package com.digaddfix.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.os.SystemClock;
import android.view.accessibility.*;
import com.digaddfix.core.*;
import java.io.IOException;
import java.util.*;

public final class BlockAccessibilityService extends AccessibilityService implements AppState.WebsiteListener {
    private BlockOverlay overlay;
    private Rules rules;
    private String foreground="";
    private long lastAppAction;
    private String lastApp="";
    private final Map<String,PendingBlock> pending=new LinkedHashMap<>();
    private static final class PendingBlock {
        final String reason;final long time;
        PendingBlock(String reason,long time) {this.reason=reason;this.time=time;}
    }
    @Override protected void onServiceConnected() {
        try {rules=AppState.rules(this);}
        catch(IOException e) {disableSelf();return;}
        overlay=new BlockOverlay(this);AppState.websiteListener=this;AppState.accessibilityRunning=true;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(rules==null || event.getPackageName()==null) return;
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(keyguard.isKeyguardLocked()) return;
        String name=event.getPackageName().toString();
        // Ignore events from our own overlay/UI without changing the real foreground.
        if(name.equals(getPackageName())) return;
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) foreground=name;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root!=null && root.getPackageName()!=null) foreground=root.getPackageName().toString();
        if(getSharedPreferences("settings",0).getBoolean("app_blocks",true)) {
            Rules.Rule rule=rules.app(foreground);
            long now=SystemClock.elapsedRealtime();
            if(rule!=null) {
                if(!lastApp.equals(foreground) || now-lastAppAction>1500) {
                    lastApp=foreground;lastAppAction=now;AppState.blockedApps++;
                    performGlobalAction(GLOBAL_ACTION_HOME);
                    overlay.show("app:"+rule.key,"App blocked",rule.label,rule.reason);
                }
                return;
            }
        }
        if(AppState.dnsRunning && AppState.protectedBrowsers.contains(foreground)) inspectBrowser(root,foreground);
    }
    @Override public void blocked(String host,String reason) {
        pending.put(host,new PendingBlock(reason,SystemClock.elapsedRealtime()));
        while(pending.size()>32) pending.remove(pending.keySet().iterator().next());
        if(AppState.dnsRunning && AppState.protectedBrowsers.contains(foreground)) {
            AccessibilityNodeInfo root=getRootInActiveWindow();
            if(root!=null && foreground.contentEquals(root.getPackageName()==null?"":root.getPackageName())) inspectBrowser(root,foreground);
        }
    }
    private void inspectBrowser(AccessibilityNodeInfo root,String packageName) {
        if(root==null) return;
        Browsers.Browser browser=Browsers.find(packageName);
        if(browser==null) return;
        String host=addressHost(root,browser,0,new int[]{0});
        if(host.isEmpty()) return;
        long now=SystemClock.elapsedRealtime();
        Iterator<Map.Entry<String,PendingBlock>> it=pending.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String,PendingBlock> entry=it.next();
            if(now-entry.getValue().time>30000) {it.remove();continue;}
            if(Rules.relatedHost(host,entry.getKey())) {
                String reason=entry.getValue().reason;it.remove();interruptWebsite(packageName,host,reason);return;
            }
        }
        Rules.Rule local=rules.domain(host);
        if(local!=null) interruptWebsite(packageName,host,local.reason);
    }
    private void interruptWebsite(String packageName,String host,String reason) {
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(keyguard.isKeyguardLocked()) return;
        performGlobalAction(GLOBAL_ACTION_HOME);
        overlay.show("site:"+packageName+":"+host,"Website blocked",host,reason);
    }
    private String addressHost(AccessibilityNodeInfo node,Browsers.Browser browser,int depth,int[] visited) {
        if(node==null || depth>16 || ++visited[0]>300) return "";
        String id=node.getViewIdResourceName();
        // Read only the address node, never arbitrary body text, messages or input fields.
        if(browser.isAddressNode(id)) {
            if(node.isFocused()) return ""; // Do not interrupt a partially typed address.
            CharSequence text=node.getText();
            String host=Rules.hostFromAddress(text==null?"":text.toString());
            if(!host.isEmpty()) return host;
        }
        for(int i=0;i<node.getChildCount();i++) {
            String host=addressHost(node.getChild(i),browser,depth+1,visited);
            if(!host.isEmpty()) return host;
        }
        return "";
    }
    @Override public void onInterrupt() {if(overlay!=null) overlay.close();}
    @Override public void onDestroy() {
        if(AppState.websiteListener==this) AppState.websiteListener=null;
        AppState.accessibilityRunning=false;pending.clear();
        if(overlay!=null) overlay.close();super.onDestroy();
    }
}
