package com.digaddfix.app;

import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.os.*;
import com.digaddfix.core.Browsers;
import org.json.*;
import java.util.*;

/** Uses full device-owner capabilities only. A work profile or legacy admin is insufficient. */
@android.annotation.TargetApi(30) // Every policy entry point checks capable() before invoking API 30 methods.
final class ManagedProtection {
    private static final String[] RESTRICTIONS={UserManager.DISALLOW_INSTALL_APPS,UserManager.DISALLOW_CONFIG_VPN,
        UserManager.DISALLOW_CONFIG_PRIVATE_DNS,UserManager.DISALLOW_ADD_USER,UserManager.DISALLOW_USER_SWITCH};
    private final Context context;
    private final DevicePolicyManager policy;
    private final ComponentName admin;
    ManagedProtection(Context context) {
        this.context=context.getApplicationContext();policy=context.getSystemService(DevicePolicyManager.class);
        admin=new ComponentName(context,ProtectionAdminReceiver.class);
    }
    boolean capable() {return Build.VERSION.SDK_INT>=30 && policy!=null && policy.isDeviceOwnerApp(context.getPackageName());}
    private List<String> targets() {
        List<String> targets=new ArrayList<>();targets.add(context.getPackageName());
        for(Browsers.Browser browser:Browsers.all()) targets.add(browser.packageName);return targets;
    }
    private SharedPreferences prefs() {return context.getSharedPreferences("protection",0);}
    void apply() throws Exception {
        if(!capable()) {
            if(prefs().contains("managed_baseline")) throw new IllegalStateException("Device-owner access is unavailable; managed protection needs repair");
            return;
        }
        if(!prefs().contains("managed_baseline")) {
            String vpn=policy.getAlwaysOnVpnPackage(admin);
            if(vpn!=null && (!vpn.equals(context.getPackageName()) || policy.isAlwaysOnVpnLockdownEnabled(admin)))
                throw new IllegalStateException("Remove the conflicting always-on VPN or global lockdown before locking protection");
            JSONObject baseline=new JSONObject();
            baseline.put("vpn",vpn==null?JSONObject.NULL:vpn);
            JSONObject restrictions=new JSONObject();Bundle owned=policy.getUserRestrictions(admin);
            for(String key:RESTRICTIONS) restrictions.put(key,owned.getBoolean(key));baseline.put("restrictions",restrictions);
            JSONObject uninstall=new JSONObject();for(String pkg:targets()) uninstall.put(pkg,policy.isUninstallBlocked(admin,pkg));
            baseline.put("uninstall",uninstall);
            baseline.put("control",new JSONArray(policy.getUserControlDisabledPackages(admin)));
            if(!prefs().edit().putString("managed_baseline",baseline.toString()).commit()) throw new IllegalStateException("Could not save managed recovery state");
        }
        // Configure the owner VPN BEFORE restricting VPN configuration. No device-wide lockdown.
        policy.setAlwaysOnVpnPackage(admin,context.getPackageName(),false);
        for(String pkg:targets()) policy.setUninstallBlocked(admin,pkg,true);
        LinkedHashSet<String> controls=new LinkedHashSet<>(policy.getUserControlDisabledPackages(admin));controls.addAll(targets());
        policy.setUserControlDisabledPackages(admin,new ArrayList<>(controls));
        for(String key:RESTRICTIONS) policy.addUserRestriction(admin,key);
        if(!verified()) throw new IllegalStateException("Android has not confirmed every managed policy; unlock or retry repair");
    }
    boolean verified() {
        if(!capable() || !prefs().contains("managed_baseline")) return false;
        try {
            if(!context.getPackageName().equals(policy.getAlwaysOnVpnPackage(admin)) || policy.isAlwaysOnVpnLockdownEnabled(admin)) return false;
            Bundle restrictions=policy.getUserRestrictions(admin);
            for(String key:RESTRICTIONS) if(!restrictions.getBoolean(key)) return false;
            for(String pkg:targets()) if(!policy.isUninstallBlocked(admin,pkg)) return false;
            return policy.getUserControlDisabledPackages(admin).containsAll(targets());
        } catch(RuntimeException e) {return false;}
    }
    void release() throws Exception {
        String saved=prefs().getString("managed_baseline",null);if(saved==null) return;
        if(!capable()) throw new IllegalStateException("Device-owner access is required to restore managed policies");
        JSONObject baseline=new JSONObject(saved),restrictions=baseline.getJSONObject("restrictions");
        // Retain the journal until EVERY restoration succeeds, so a failed release can be retried.
        for(String key:RESTRICTIONS) {
            if(restrictions.getBoolean(key)) policy.addUserRestriction(admin,key);else policy.clearUserRestriction(admin,key);
        }
        JSONObject uninstall=baseline.getJSONObject("uninstall");
        for(String pkg:targets()) policy.setUninstallBlocked(admin,pkg,uninstall.getBoolean(pkg));
        JSONArray prior=baseline.getJSONArray("control");List<String> controls=new ArrayList<>();
        for(int i=0;i<prior.length();i++) controls.add(prior.getString(i));policy.setUserControlDisabledPackages(admin,controls);
        String vpn=baseline.isNull("vpn")?null:baseline.getString("vpn");
        policy.setAlwaysOnVpnPackage(admin,vpn,false);
        Bundle restored=policy.getUserRestrictions(admin);
        for(String key:RESTRICTIONS) if(restored.getBoolean(key)!=restrictions.getBoolean(key)) throw new IllegalStateException("Android has not confirmed policy release; retry unlock");
        for(String pkg:targets()) if(policy.isUninstallBlocked(admin,pkg)!=uninstall.getBoolean(pkg)) throw new IllegalStateException("Uninstall policy release not confirmed");
        if(!new HashSet<>(policy.getUserControlDisabledPackages(admin)).equals(new HashSet<>(controls)) ||
            !Objects.equals(vpn,policy.getAlwaysOnVpnPackage(admin))) throw new IllegalStateException("Managed policy release not confirmed");
        if(!prefs().edit().remove("managed_baseline").commit()) throw new IllegalStateException("Could not finish managed recovery state");
    }
    @SuppressWarnings("deprecation")
    void removeTestOwner() {
        if(Protection.locked(context)) throw new IllegalStateException("Release protection with your PIN or recovery code first");
        if(!capable() || (context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)
            throw new IllegalStateException("This action is available only on a device-owner debug test build");
        // Android documents this API for testing only. Production deprovisioning is a separate workflow.
        policy.clearDeviceOwnerApp(context.getPackageName());
        if(policy.isDeviceOwnerApp(context.getPackageName())) throw new IllegalStateException("Android did not remove test device management");
    }
}
