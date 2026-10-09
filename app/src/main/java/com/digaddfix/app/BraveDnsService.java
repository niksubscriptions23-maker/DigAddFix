package com.digaddfix.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.VpnService;
import android.os.*;
import android.system.*;
import com.digaddfix.core.*;
import java.io.*;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

public final class BraveDnsService extends VpnService {
    public static final String START="com.digaddfix.START_DNS",STOP="com.digaddfix.STOP_DNS";
    private static final String CHANNEL="browser_protection";
    private ParcelFileDescriptor tunnel;
    private FileOutputStream output;
    private volatile boolean stopped=true;
    private volatile int generation=0;
    private Thread reader;
    private ThreadPoolExecutor workers;
    private byte[] dns4,dns6;
    private FilterEngine engine;
    private TcpDns tcp;
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && STOP.equals(intent.getAction())) {
            getSharedPreferences("settings",0).edit().putBoolean("dns_requested",false).apply();shutdown("Browser protection is off");stopSelf();return START_NOT_STICKY;
        }
        if(!stopped) return START_STICKY;
        try {
            if(prepare(this)!=null) throw new IOException("VPN permission is required");
            getPackageManager().getPackageInfo(Rules.BRAVE_PACKAGE,0);
            if(Build.VERSION.SDK_INT>=29 && isLockdownEnabled()) throw new IOException("Turn off Block connections without VPN so other apps retain Internet access");
            dns4=InetAddress.getByName("10.111.0.1").getAddress();
            dns6=InetAddress.getByName("fd7f:da:f1::1").getAddress();
            Builder builder=new Builder().setSession("DigAddFix · Brave DNS").setMtu(32767)
                .addAddress("10.111.0.2",32).addAddress("fd7f:da:f1::2",128)
                .addDnsServer("10.111.0.1").addDnsServer("fd7f:da:f1::1")
                .addRoute("10.111.0.1",32).addRoute("fd7f:da:f1::1",128)
                .addAllowedApplication(Rules.BRAVE_PACKAGE).setBlocking(false);
            builder.setConfigureIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE));
            // No default routes, no allowBypass(), no global DNS changes, and no empty app list.
            engine=new FilterEngine(AppState.rules(this),new FamilyDoh(this));
            tunnel=builder.establish();
            if(tunnel==null) throw new IOException("Android did not establish the Brave DNS interface");
            startNotification();
            output=new FileOutputStream(tunnel.getFileDescriptor());stopped=false;generation++;
            workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64));
            tcp=new TcpDns(this::writePacket,(query,done)->submit(query,done));
            AppState.dnsRunning=true;AppState.dnsStatus="Brave DNS is active · verify filtering in Brave";
            getSharedPreferences("settings",0).edit().putBoolean("dns_requested",true).apply();
            final ParcelFileDescriptor sessionTunnel=tunnel;
            final int sessionGeneration=generation;
            reader=new Thread(()->readPackets(sessionTunnel,sessionGeneration),"brave-dns-reader");reader.start();
            return START_STICKY;
        } catch(PackageManager.NameNotFoundException e) { shutdown("Install Brave stable to enable browser protection"); }
        catch(Exception e) { shutdown(e.getMessage()==null?"Browser protection could not start":e.getMessage()); }
        stopSelf();return START_NOT_STICKY;
    }
    private void startNotification() {
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Browser protection",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("DigAddFix").setContentText("Brave DNS filtering · other apps excluded")
            .setContentIntent(open).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34) startForeground(7,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        else startForeground(7,notification);
    }
    private void readPackets(ParcelFileDescriptor sessionTunnel,int sessionGeneration) {
        byte[] buffer=new byte[32767];
        try {
            while(!stopped && generation==sessionGeneration) {
                StructPollfd poll=new StructPollfd();poll.fd=sessionTunnel.getFileDescriptor();poll.events=(short)OsConstants.POLLIN;
                Os.poll(new StructPollfd[]{poll},1000);
                if(stopped || generation!=sessionGeneration) break;
                if((poll.revents&OsConstants.POLLIN)==0) continue;
                int size=Os.read(poll.fd,buffer,0,buffer.length);
                if(size<=0) continue;
                try {
                    final IpPacket packet=IpPacket.parse(buffer,size);
                    if(packet.destinationPort!=53 || !(Arrays.equals(packet.destination,dns4)||Arrays.equals(packet.destination,dns6))) continue;
                    if(packet.protocol==6) tcp.receive(packet,SystemClock.elapsedRealtime());
                    else submit(packet.payload,answer->{
                        if(answer==null) return;
                        try {writePacket(packet.reply(Dns.forUdp(packet.payload,answer)));} catch(IllegalArgumentException ignored) {}
                    });
                } catch(IllegalArgumentException ignored) { /* Malformed packets are dropped, never forwarded unfiltered. */ }
            }
        } catch(Exception e) {
            if(!stopped && generation==sessionGeneration) AppState.MAIN.post(()->{
                if(generation==sessionGeneration) {shutdown("Browser protection interrupted · restart it");stopSelf();}
            });
        }
    }
    private void submit(final byte[] query,final TcpDns.Completion done) {
        final int sessionGeneration=generation;
        final FilterEngine sessionEngine=engine;
        try {
            workers.execute(()->{
                try {
                    FilterEngine.Result result=sessionEngine.resolve(query);
                    if(stopped || generation!=sessionGeneration) return;
                    if(result.blocked) AppState.websiteBlocked(result.host,result.reason);
                    AppState.dnsStatus=result.failed?"Brave DNS active · family resolver unavailable":"Brave DNS active · page text is not scanned";
                    done.finish(result.response);
                } catch(IllegalArgumentException e) {done.finish(null);}
            });
        } catch(RejectedExecutionException e) {
            try {done.finish(Dns.error(query,Dns.SERVFAIL));} catch(IllegalArgumentException ignored) {done.finish(null);}
        }
    }
    private synchronized void writePacket(byte[] packet) {
        if(stopped || output==null) return;
        try {output.write(packet);} catch(IOException ignored) { /* Reader/service lifecycle reports interface failure. */ }
    }
    private synchronized void shutdown(String status) {
        stopped=true;generation++;AppState.dnsRunning=false;AppState.dnsStatus=status;
        if(workers!=null) workers.shutdownNow();
        if(tunnel!=null) {try {tunnel.close();} catch(IOException ignored) {}tunnel=null;}
        output=null;
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
    @Override public void onRevoke() {
        getSharedPreferences("settings",0).edit().putBoolean("dns_requested",false).apply();
        shutdown("VPN permission was removed · browser protection is off");stopSelf();
    }
    @Override public void onDestroy() {
        shutdown(AppState.dnsRunning?"Browser protection stopped":AppState.dnsStatus);super.onDestroy();
    }
}
