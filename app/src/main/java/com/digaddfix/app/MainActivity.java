package com.digaddfix.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.digaddfix.core.*;
import java.util.*;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG=0xff101c22,CARD=0xff1c2e35,TEXT=0xffe8f1ef,MUTED=0xffaac2c4,ACCENT=0xff66e5b5;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,appStatus,counts,resolver;
    private Button toggle;
    private boolean visible;
    private LinearLayout page;
    private int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(30),dp(22),dp(28));
        scroll.addView(page);setContentView(scroll);
        // Android 15 enforces edge-to-edge; keep controls clear of system bars.
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.requestApplyInsets();
        text(page,"DIGADDFIX",13,ACCENT,true);
        text(page,"Make room\nfor your focus.",32,TEXT,true);
        text(page,"Brave stays your browser. You choose a calmer digital routine.",16,MUTED,false);

        LinearLayout browser=card();
        text(browser,"Browser protection",21,TEXT,true);
        status=text(browser,"Browser protection is off",15,MUTED,false);
        text(browser,"Only Brave stable uses the family DNS filter. Other apps keep their normal networking.",15,MUTED,false);
        toggle=button(browser,"Enable Brave protection",v->toggleDns());
        button(browser,"Open Brave",v->openBrave("https://www.google.com"));
        button(browser,"Setup and coverage",v->showSetup());

        LinearLayout apps=card();
        text(apps,"App blocks & five-second pause",21,TEXT,true);
        appStatus=text(apps,"Accessibility is not enabled",15,MUTED,false);
        text(apps,"A full-screen pause appears when a supported app or website is blocked. It closes after five seconds; the block remains.",15,MUTED,false);
        Switch enabled=new Switch(this);enabled.setText("Block apps on the starter list");enabled.setTextColor(TEXT);
        enabled.setChecked(getSharedPreferences("settings",0).getBoolean("app_blocks",true));
        enabled.setOnCheckedChangeListener((button,on)->getSharedPreferences("settings",0).edit().putBoolean("app_blocks",on).apply());
        apps.addView(enabled);
        button(apps,"Enable app blocks and overlays",v->new AlertDialog.Builder(this)
            .setTitle("Allow DigAddFix accessibility access")
            .setMessage(getString(R.string.accessibility_description)+"\n\nWebsite overlays depend on Brave exposing its current address. Domain filtering works independently.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Open Android settings",(dialog,which)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show());
        button(apps,"Review starter blocklists",v->showRules());

        LinearLayout checks=card();
        text(checks,"Check your protection",21,TEXT,true);
        counts=text(checks,"",15,MUTED,false);
        resolver=text(checks,"Resolver not checked in this session",15,MUTED,false);
        button(checks,"Check family resolver",v->checkResolver());
        button(checks,"Test a blocked website in Brave",v->openBrave("https://digaddfix-blocked.test"));
        button(checks,"Verify Google SafeSearch in Brave",v->openBrave("https://www.google.com/safesearch"));
        text(checks,"The test website is a harmless local rule. Check that you see the five-second overlay and that another app still has Internet access.",14,MUTED,false);

        text(page,"No account. No ads. No browsing history stored.",14,ACCENT,false);
        text(page,"Starter lists cover selected apps and domains. Webpage text and media are not scanned in this version.",14,MUTED,false);
    }
    private void toggleDns() {
        if(AppState.dnsRunning) {startService(new Intent(this,BraveDnsService.class).setAction(BraveDnsService.STOP));return;}
        try {getPackageManager().getPackageInfo(Rules.BRAVE_PACKAGE,0);}
        catch(PackageManager.NameNotFoundException e) {
            message("Brave is needed","Install Brave stable, then enable browser protection.");return;
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);
            return;
        }
        requestVpn();
    }
    private void requestVpn() {
        Intent consent=android.net.VpnService.prepare(this);
        if(consent!=null) startActivityForResult(consent,11);
        else startDns();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==12) requestVpn(); // VPN can run even if notification permission is declined.
    }
    private void startDns() {startForegroundService(new Intent(this,BraveDnsService.class).setAction(BraveDnsService.START));}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request==11 && result==RESULT_OK) startDns();
    }
    private void openBrave(String url) {
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(Rules.BRAVE_PACKAGE));}
        catch(ActivityNotFoundException e) {message("Brave is needed","Install Brave stable to open this link.");}
    }
    private void showSetup() {
        message("Set up Brave protection",
            "1. Enable Brave protection and allow Android's VPN request.\n\n"+
            "2. In Brave's privacy settings, use system DNS rather than a separate Secure DNS provider. The setting name varies by Brave version. Custom encrypted DNS can bypass domain rules.\n\n"+
            "3. Choose Google as Brave's search engine. DigAddFix maps supported Google Search hosts to Google's SafeSearch endpoint. Verify Filter is locked on the SafeSearch page. Brave Search and other engines are not enforced in v0.1.\n\n"+
            "4. Enable DigAddFix accessibility for app blocks and five-second overlays. Full Brave addresses must be exposed for website overlays.\n\n"+
            "5. Optional: set DigAddFix as always-on in Android VPN settings. Keep Block connections without VPN OFF so other apps retain Internet access.\n\n"+
            "Coverage: Brave stable's system DNS queries, including browser background lookups. Other apps and their embedded webviews are excluded. Another VPN, custom DNS, cached/direct addresses, VPN removal, and some browser modes can bypass this layer. Text/image scanning is deferred.");
    }
    private void showRules() {
        try {
            Rules rules=AppState.rules(this);StringBuilder text=new StringBuilder("APPS\n");
            for(Rules.Rule rule:rules.apps()) {
                boolean installed;
                try {getPackageManager().getPackageInfo(rule.key,0);installed=true;} catch(PackageManager.NameNotFoundException e) {installed=false;}
                text.append(rule.label).append(installed?" · installed":"").append("\n").append(rule.reason).append("\n\n");
            }
            text.append("WEBSITES (also their subdomains)\n");
            for(Rules.Rule rule:rules.domains()) text.append(rule.key).append("\n").append(rule.reason).append("\n\n");
            text.append("These are starter lists, not all games or all unsafe sites. Your final lists can replace the bundled assets.");
            message("Starter policy",text.toString());
        } catch(Exception e) {message("Policy error","The bundled lists could not be loaded. Protection cannot start.");}
    }
    private void checkResolver() {
        resolver.setText("Checking the family resolver…");
        java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
        worker.execute(()->{
            String result;
            try {
                FamilyDoh transport=new FamilyDoh(this);
                byte[] safe=Dns.query("example.org",Dns.A,701),test=Dns.query("nudity.testcategory.com",Dns.A,702);
                byte[] safeReply=transport.exchange(safe),testReply=transport.exchange(test);
                Dns.validateReply(safe,safeReply);Dns.validateReply(test,testReply);
                if((safeReply[3]&15)!=0 || Dns.addresses(safeReply,Dns.A).isEmpty() || Dns.providerBlocked(safeReply))
                    result="Resolver check failed: ordinary lookup unavailable";
                else if(Dns.providerBlocked(testReply)) result="Family resolver passed · verify actual Brave routing with the browser tests";
                else result="Resolver check inconclusive: adult test response was not the expected sinkhole";
            } catch(Exception e) {result="Family resolver unavailable · retry when connected";}
            final String message=result;handler.post(()->{if(!isFinishing() && !isDestroyed()) resolver.setText(message);});
            worker.shutdown();
        });
    }
    private void message(String title,String content) {new AlertDialog.Builder(this).setTitle(title).setMessage(content).setPositiveButton("Done",null).show();}
    private LinearLayout card() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(18));
        GradientDrawable background=new GradientDrawable();background.setColor(CARD);background.setCornerRadius(dp(18));box.setBackground(background);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(18);page.addView(box,p);return box;
    }
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold) {
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);
        if(bold) view.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(view,p);return view;
    }
    private Button button(LinearLayout parent,String value,View.OnClickListener click) {
        Button button=new Button(this);button.setText(value);button.setAllCaps(false);button.setTextColor(BG);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));button.setOnClickListener(click);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);parent.addView(button,p);return button;
    }
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if(!visible) return;
            status.setText(AppState.dnsStatus);toggle.setText(AppState.dnsRunning?"Stop Brave protection":"Enable Brave protection");
            appStatus.setText(AppState.accessibilityRunning?"App blocks and overlays are enabled":"Accessibility is not enabled");
            counts.setText("This session: "+AppState.blockedApps+" app interrupts · "+AppState.blockedRequests+" blocked DNS requests");
            handler.postDelayed(this,1000);
        }
    };
    @Override protected void onResume() {super.onResume();visible=true;handler.post(refresh);}
    @Override protected void onPause() {visible=false;handler.removeCallbacks(refresh);super.onPause();}
}
