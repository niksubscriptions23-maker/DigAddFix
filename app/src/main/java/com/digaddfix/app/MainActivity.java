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
    private TextView status,appStatus,counts,resolver,protectionStatus;
    private ProtectionUi protectionUi;
    private Button toggle;
    private boolean visible;
    private LinearLayout page;
    private final Map<String,TextView> browserRows=new LinkedHashMap<>();
    private int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        protectionUi=new ProtectionUi(this);
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
        text(page,"Choose Brave, Chrome or Firefox. Make room for a calmer digital routine.",16,MUTED,false);

        LinearLayout browser=card();
        text(browser,"Browser protection",21,TEXT,true);
        status=text(browser,"Browser protection is off",15,MUTED,false);
        text(browser,"Family DNS covers installed stable versions of Brave, Chrome and Firefox. Other apps keep their normal networking.",15,MUTED,false);
        for(Browsers.Browser supported:Browsers.all())
            browserRows.put(supported.packageName,text(browser,supported.label+" · checking installation",15,TEXT,false));
        toggle=button(browser,"Enable browser protection",v->toggleDns());
        button(browser,"Open a supported browser",v->chooseBrowser("https://www.google.com"));
        button(browser,"Setup and coverage",v->showSetup());

        LinearLayout apps=card();
        text(apps,"App blocks & five-second pause",21,TEXT,true);
        appStatus=text(apps,"Accessibility is not enabled",15,MUTED,false);
        text(apps,"A full-screen pause appears when a supported app or website is blocked. It closes after five seconds; the block remains.",15,MUTED,false);
        Switch enabled=new Switch(this);enabled.setText("Block listed apps and other known browsers");enabled.setTextColor(TEXT);
        enabled.setChecked(getSharedPreferences("settings",0).getBoolean("app_blocks",true));
        enabled.setOnCheckedChangeListener((button,on)->{
            if(!on && Protection.locked(this)) {
                button.setChecked(true);message("Protection is locked","Release the lock with your PIN or recovery code before disabling app blocks.");return;
            }
            getSharedPreferences("settings",0).edit().putBoolean("app_blocks",on).apply();
        });
        apps.addView(enabled);
        button(apps,"Enable app blocks and overlays",v->new AlertDialog.Builder(this)
            .setTitle("Allow DigAddFix accessibility access")
            .setMessage(getString(R.string.accessibility_description)+"\n\nWebsite overlays depend on the browser exposing its current address. Domain filtering works independently.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Open Android settings",(dialog,which)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show());
        button(apps,"Review starter blocklists",v->showRules());

        LinearLayout protection=card();
        text(protection,"Protect your setup",21,TEXT,true);
        protectionStatus=text(protection,"Protection lock is off",15,MUTED,false);
        text(protection,"Use a PIN and recovery code to guard controls and known bypass screens. Android installation and uninstall restrictions require this app to be the full device owner on Android 11 or newer.",15,MUTED,false);
        button(protection,"Set PIN and lock protection",v->protectionUi.setup());
        button(protection,"Release lock with PIN",v->protectionUi.unlock(false));
        button(protection,"Use recovery code",v->protectionUi.unlock(true));
        button(protection,"Repair active protection",v->protectionUi.repair());
        if(new ManagedProtection(this).capable() && (getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)
            button(protection,"Remove test management after unlock",v->protectionUi.removeTestManagement());
        button(protection,"Protection coverage",v->message("Protection coverage",
            "Personal guard interrupts known app stores, installer/removal activities, Android bypass settings and recognized browser settings. Detection varies by Android/browser version. Unrecognized screens, background installs, ADB, safe mode and accessibility removal can bypass it. It is best effort.\n\n"+
            "Full device owner on Android 11+ adds OS restrictions on app installations, VPN/private-DNS configuration, extra users and switching users. It protects DigAddFix and supported browser packages from uninstall, force-stop and clearing data, and configures always-on VPN with global lockdown OFF. Browser settings still require correct initial setup; independent encrypted DNS, proxies and extensions are not guaranteed blocked.\n\n"+
            "Managed restrictions also stop updates until you release the lock. Recovery restores the app's previous managed policies. Factory reset, emergency calling, lock screens, ordinary Wi-Fi/Bluetooth and runtime permission consent are not intentionally blocked. Existing secondary profiles and rooted/recovery environments are outside coverage. No provisioning or reset is performed by this app."));

        LinearLayout checks=card();
        text(checks,"Check your protection",21,TEXT,true);
        counts=text(checks,"",15,MUTED,false);
        resolver=text(checks,"Resolver not checked in this session",15,MUTED,false);
        button(checks,"Check family resolver",v->checkResolver());
        button(checks,"Test a blocked website",v->chooseBrowser("https://digaddfix-blocked.test"));
        button(checks,"Verify Google SafeSearch",v->chooseBrowser("https://www.google.com/safesearch"));
        text(checks,"The test website is a harmless local rule. Check that you see the five-second overlay and that another app still has Internet access.",14,MUTED,false);

        text(page,"No account. No ads. No browsing history stored.",14,ACCENT,false);
        text(page,"Starter lists cover selected apps and domains. Webpage text and media are not scanned in this version.",14,MUTED,false);
    }
    private void toggleDns() {
        if(AppState.dnsRunning) {
            if(Protection.locked(this)) {message("Protection is locked","Release the lock with your PIN or recovery code before stopping browser protection.");return;}
            startService(new Intent(this,BrowserDnsService.class).setAction(BrowserDnsService.STOP));return;
        }
        if(BrowserSupport.installed(this).isEmpty()) {
            message("A supported browser is needed","Install or enable Brave, Chrome or Firefox, then enable browser protection.");return;
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
    private void startDns() {startForegroundService(new Intent(this,BrowserDnsService.class).setAction(BrowserDnsService.START));}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request==11 && result==RESULT_OK) startDns();
    }
    private void chooseBrowser(String url) {
        List<String> installed=BrowserSupport.installed(this);
        if(installed.isEmpty()) {message("A supported browser is needed","Install or enable Brave, Chrome or Firefox to open this link.");return;}
        if(installed.size()==1) {openBrowser(installed.get(0),url);return;}
        String[] names=new String[installed.size()];
        for(int i=0;i<names.length;i++) names[i]=Browsers.find(installed.get(i)).label;
        new AlertDialog.Builder(this).setTitle("Choose browser").setItems(names,(dialog,which)->openBrowser(installed.get(which),url)).show();
    }
    private void openBrowser(String packageName,String url) {
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(packageName));}
        catch(ActivityNotFoundException e) {message("Browser unavailable","This browser could not open the link. Check that it is enabled.");}
    }
    private void showSetup() {
        message("Set up browser protection",
            "1. Enable browser protection and allow Android's VPN request. All installed supported stable browsers are included; Brave is not required.\n\n"+
            "2. Use system DNS in Brave and Chrome. If Firefox uses DNS over HTTPS, turn that off. Independent encrypted DNS or browser extensions/proxies can bypass this filter. Settings vary by browser version.\n\n"+
            "3. Choose Google as the search engine in each browser. DigAddFix maps supported Google Search hosts to Google's SafeSearch endpoint. Verify Filter is locked separately in Brave, Chrome and Firefox. Other engines are not enforced in v0.2.\n\n"+
            "4. Enable DigAddFix accessibility for app blocks and five-second overlays. The browser's committed address must be exposed for website overlays. Other known browsers on the list, including beta/nightly channels, are blocked while app blocking is enabled.\n\n"+
            "5. Optional: set DigAddFix as always-on in Android VPN settings. Keep Block connections without VPN OFF so other apps retain Internet access.\n\n"+
            "Coverage: system DNS queries from installed Brave, Chrome and Firefox stable packages, including their background lookups. Other apps and their embedded webviews are excluded from family DNS. The VPN refreshes for supported browser installation/removal/enable changes while active. If all supported browsers are removed, enable protection again after installing one. Unlisted browsers are not automatically classified or blocked. Another VPN, custom DNS, cached/direct addresses and permission removal can bypass this layer. Text/image scanning is deferred.");
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
                else if(Dns.providerBlocked(testReply)) result="Family resolver passed · verify routing in each installed supported browser";
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
            status.setText(AppState.dnsStatus);toggle.setText(AppState.dnsRunning?"Stop browser protection":"Enable browser protection");
            List<String> installed=BrowserSupport.installed(MainActivity.this);
            for(Browsers.Browser browser:Browsers.all()) {
                String state=!installed.contains(browser.packageName)?"not installed or disabled":
                    AppState.dnsRunning && AppState.protectedBrowsers.contains(browser.packageName)?"DNS filter active":"DNS filter off";
                browserRows.get(browser.packageName).setText(browser.label+" · "+state);
            }
            boolean blocks=getSharedPreferences("settings",0).getBoolean("app_blocks",true);
            appStatus.setText(!AppState.accessibilityRunning?"Accessibility is not enabled":
                blocks?"Listed apps and other browsers are blocked · overlays enabled":"App blocking is off · website overlays enabled");
            counts.setText("This session: "+AppState.blockedApps+" app interrupts · "+AppState.blockedRequests+" blocked DNS requests");
            protectionStatus.setText(Protection.status(MainActivity.this));
            handler.postDelayed(this,1000);
        }
    };
    @Override protected void onResume() {
        super.onResume();visible=true;
        if(AppState.dnsRunning) startService(new Intent(this,BrowserDnsService.class).setAction(BrowserDnsService.START));
        else Protection.resumeDns(this);
        handler.post(refresh);
    }
    @Override protected void onPause() {visible=false;handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy() {protectionUi.close();super.onDestroy();}
}
