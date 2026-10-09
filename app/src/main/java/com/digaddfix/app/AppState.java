package com.digaddfix.app;

import android.content.Context;
import android.os.*;
import com.digaddfix.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class AppState {
    private AppState() {}
    static volatile boolean dnsRunning=false,accessibilityRunning=false;
    static volatile String dnsStatus="Browser protection is off";
    static volatile long blockedRequests=0,blockedApps=0;
    static volatile List<String> protectedBrowsers=Collections.emptyList();
    private static Rules rules;
    static final Handler MAIN=new Handler(Looper.getMainLooper());
    interface WebsiteListener { void blocked(String host,String reason); }
    static WebsiteListener websiteListener;
    static synchronized Rules rules(Context context) throws IOException {
        if(rules==null) rules=new Rules(
            new InputStreamReader(context.getAssets().open("blocked-apps.tsv"),StandardCharsets.UTF_8),
            new InputStreamReader(context.getAssets().open("blocked-domains.tsv"),StandardCharsets.UTF_8));
        return rules;
    }
    static void websiteBlocked(String host,String reason) {
        synchronized(AppState.class) { blockedRequests++; }
        MAIN.post(()->{if(websiteListener!=null) websiteListener.blocked(host,reason);});
    }
}
