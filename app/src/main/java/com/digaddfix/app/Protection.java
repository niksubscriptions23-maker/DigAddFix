package com.digaddfix.app;

import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.provider.Settings;
import com.digaddfix.core.*;
import java.util.Arrays;

final class Protection {
    private Protection() {}
    static volatile String lastError="";
    private static SharedPreferences prefs(Context context) {return context.getSharedPreferences("protection",0);}
    static boolean locked(Context context) {return prefs(context).getBoolean("locked",false) || prefs(context).contains("managed_baseline");}
    static String prerequisites(Context context) {
        if(!AppState.accessibilityRunning) return "Enable accessibility before locking protection.";
        if(!AppState.dnsRunning || VpnService.prepare(context)!=null) return "Enable browser protection before locking protection.";
        if(!context.getSharedPreferences("settings",0).getBoolean("app_blocks",true)) return "Enable the app-block switch before locking protection.";
        return "";
    }
    static synchronized void enable(Context context,String pinRecord,String recoveryRecord) throws Exception {
        if(locked(context)) throw new IllegalStateException("Protection is already locked");
        String issue=prerequisites(context);if(!issue.isEmpty()) throw new IllegalStateException(issue);
        if(!prefs(context).edit().putString("pin",pinRecord).putString("recovery",recoveryRecord)
            .putBoolean("locked",true).putInt("failures",0).putLong("remaining",0).commit()) throw new IllegalStateException("Could not save protection credentials");
        context.getSharedPreferences("settings",0).edit().putBoolean("app_blocks",true).putBoolean("dns_requested",true).commit();
        lastError="";
        try {new ManagedProtection(context).apply();}
        catch(Exception e) {lastError="Managed policies need attention. "+safeMessage(e);throw e;}
    }
    private static int boot(Context context) {return Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    private static long checkpoint(Context context) {
        SharedPreferences p=prefs(context);long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();int boot=boot(context);
        long remaining=RetryGate.remaining(p.getLong("remaining",0),p.getLong("wall",wall),p.getLong("elapsed",elapsed),p.getInt("boot",-1),wall,elapsed,boot);
        if(!p.edit().putLong("remaining",remaining).putLong("wall",wall).putLong("elapsed",elapsed).putInt("boot",boot).commit())
            throw new IllegalStateException("Could not save unlock retry state");
        return remaining;
    }
    static synchronized void unlock(Context context,char[] input,boolean recovery) throws Exception {
        try {
            if(!locked(context)) return;
            long remaining=checkpoint(context);
            if(remaining>0) throw new IllegalStateException("Wait "+((remaining+999)/1000)+" seconds before another attempt.");
            SharedPreferences p=prefs(context);int failures=Math.min(16,p.getInt("failures",0)+1);
            // Count before hashing: killing/reopening the process cannot grant free attempts.
            if(!p.edit().putInt("failures",failures).putLong("remaining",RetryGate.delay(failures)).commit()) throw new IllegalStateException("Could not save unlock attempt");
            if(!Credential.verify(input,p.getString(recovery?"recovery":"pin",""))) throw new IllegalStateException("The credential was not accepted.");
            p.edit().putInt("failures",0).putLong("remaining",0).commit();
            new ManagedProtection(context).release();
            if(!p.edit().clear().commit()) throw new IllegalStateException("Could not finish unlocking protection");
            lastError="";
        } finally {Arrays.fill(input,'\0');}
    }
    static String status(Context context) {
        if(!locked(context)) return "Protection lock is off";
        ManagedProtection managed=new ManagedProtection(context);
        String mode=managed.verified()?"Managed policies confirmed":"Personal guard · best effort";
        if(prefs(context).contains("managed_baseline") && !managed.verified()) mode="Managed policies incomplete · repair or unlock";
        if(!AppState.dnsRunning || !AppState.accessibilityRunning) mode+="\nProtection interrupted · open DigAddFix to restore access";
        if(!lastError.isEmpty()) mode+="\n"+lastError;
        return mode;
    }
    static synchronized void repair(Context context) {
        if(!locked(context)) return;
        context.getSharedPreferences("settings",0).edit().putBoolean("app_blocks",true).apply();
        try {new ManagedProtection(context).apply();lastError="";}
        catch(Exception e) {lastError=safeMessage(e);}
    }
    static void resumeDns(Context context) {
        if(!locked(context) || AppState.dnsRunning || VpnService.prepare(context)!=null || BrowserSupport.installed(context).isEmpty()) return;
        try {context.startForegroundService(new Intent(context,BrowserDnsService.class).setAction(BrowserDnsService.START));}
        catch(RuntimeException e) {lastError="Android prevented restart. Open DigAddFix to restore browser protection.";}
    }
    static String safeMessage(Exception e) {return e.getMessage()==null?"Protection could not complete the requested change.":e.getMessage();}
}
