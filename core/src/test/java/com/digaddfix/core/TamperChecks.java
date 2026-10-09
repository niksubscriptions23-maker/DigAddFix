package com.digaddfix.core;

import java.util.*;

/** Independent PBKDF2 vector, hostile records, retry clocks and negative screen boundaries. */
final class TamperChecks {
    private static int checks;
    private static void check(boolean ok,String name) {checks++;if(!ok) throw new AssertionError(name);}
    static int run() throws Exception {
        check(Credential.validPin("482759"),"nontrivial PIN accepted");
        for(String pin:Arrays.asList("","12345","1234567890123","111111","123456","987654","１２３４５６","48 759","48275a"))
            check(!Credential.validPin(pin),"weak or invalid PIN rejected: "+pin);
        // Expected value generated independently using Python hashlib.pbkdf2_hmac.
        String vector="p1$600000$000102030405060708090a0b0c0d0e0f$941d4d55daa12ee42f9e30e04efcdecdc7520bc9764cf8e0ed31ec541e919fdc";
        check(Credential.verify("482759".toCharArray(),vector),"independent PBKDF2-SHA256 600000 vector");
        check(!Credential.verify("482758".toCharArray(),vector),"wrong credential rejected");
        for(String record:Arrays.asList("",vector+"$extra",vector.replace("600000","000001"),vector.replace("600000","999999"),
            vector.replace("p1$","p2$"),vector.replace("00010203","xxxxxxxx"),vector.substring(0,vector.length()-1)))
            check(!Credential.verify("482759".toCharArray(),record),"malformed or unbounded hash record rejected");
        String first=Credential.create("482759".toCharArray()),second=Credential.create("482759".toCharArray());
        check(!first.equals(second),"random salts differ for identical PINs");
        check(Credential.verify("482759".toCharArray(),first),"newly created record verifies");
        String recovery=Credential.recoveryCode(),clean=Credential.normalizeRecovery(recovery);
        check(clean.length()==32 && recovery.length()==39,"128-bit recovery code grouping");
        check(clean.equals(Credential.normalizeRecovery(recovery.toLowerCase(Locale.ROOT))),"recovery input normalization");
        check(Credential.normalizeRecovery("1234").isEmpty() && Credential.normalizeRecovery(clean+"x").isEmpty(),"incomplete recovery rejected");
        String recoveryRecord=Credential.create(clean.toCharArray());
        check(Credential.verify(clean.toCharArray(),recoveryRecord),"recovery hash roundtrip");
        check(!Credential.verify(new char[65],first),"oversized credential rejected before derivation");
        check(RetryGate.delay(4)==0 && RetryGate.delay(5)==30000 && RetryGate.delay(6)==60000,"retry threshold and escalation");
        check(RetryGate.delay(100)==RetryGate.MAX_WAIT,"retry maximum is bounded");
        check(RetryGate.remaining(30000,100000,50000,3,110000,60000,3)==20000,"same boot uses elapsed clock");
        check(RetryGate.remaining(30000,100000,50000,3,90000,60000,3)==20000,"wall rollback cannot lengthen active monotonic wait");
        check(RetryGate.remaining(30000,100000,50000,3,9999999,51000,3)==29000,"wall jump cannot skip active monotonic wait");
        check(RetryGate.remaining(30000,100000,50000,3,110000,1000,4)==20000,"reboot accounts for wall time");
        long rollback=RetryGate.remaining(30000,100000,50000,3,90000,1000,4);
        check(rollback==30000,"reboot with wall rollback does not shorten wait");
        check(RetryGate.remaining(rollback,90000,1000,4,91000,2000,4)==29000,"rollback checkpoint still permits eventual recovery");
        check(RetryGate.remaining(30000,100000,50000,3,140000,90000,3)==0,"retry expires");
        for(String pkg:Arrays.asList("com.android.vending","org.fdroid.fdroid","com.sec.android.app.samsungapps"))
            check(!TamperScreens.reason(pkg,"android.widget.FrameLayout").isEmpty(),"known store interrupted");
        check(TamperScreens.reason("com.android.vending.clone","InstallStart").isEmpty(),"store package lookalike allowed");
        for(String pkg:Arrays.asList("com.android.packageinstaller","com.google.android.permissioncontroller")) {
            check(!TamperScreens.reason(pkg,"com.android.packageinstaller.InstallStart").isEmpty(),"installer activity interrupted");
            check(TamperScreens.reason(pkg,"com.android.permissioncontroller.permission.ui.GrantPermissionsActivity").isEmpty(),"runtime permission consent allowed");
            check(TamperScreens.reason(pkg,"android.app.AlertDialog").isEmpty(),"unrecognized installer screen not blanket-blocked");
        }
        for(String cls:Arrays.asList("com.android.settings.Settings$VpnSettingsActivity","com.android.settings.Settings$AccessibilitySettingsActivity","com.android.settings.Settings$DevelopmentSettingsActivity"))
            check(!TamperScreens.reason("com.android.settings",cls).isEmpty(),"known bypass settings interrupted");
        for(String cls:Arrays.asList("com.android.settings.Settings$WifiSettingsActivity","com.android.settings.Settings$BluetoothSettingsActivity","com.android.settings.Settings$DisplaySettingsActivity","com.android.settings.Settings","com.android.settings.SubSettings"))
            check(TamperScreens.reason("com.android.settings",cls).isEmpty(),"ordinary or unknown settings kept available");
        check(TamperScreens.reason("com.example.app","com.android.settings.Settings$VpnSettingsActivity").isEmpty(),"settings class cannot classify arbitrary app");
        for(String pkg:Arrays.asList("com.brave.browser","com.android.chrome")) check(!TamperScreens.reason(pkg,"org.chromium.chrome.browser.settings.SettingsActivity").isEmpty(),"native Chromium settings classifier is package bounded");
        for(String id:Arrays.asList("android:id/widget_frame","org.mozilla.firefox:id/switchWidget","org.mozilla.firefox:id/add_ons_list","org.mozilla.firefox:id/addonSettingsEngineView"))
            check(TamperScreens.nativeSettingsNode("org.mozilla.firefox",id),"Firefox preference/add-on native ID");
        check(!TamperScreens.nativeSettingsNode("org.mozilla.firefox","org.mozilla.firefox:id/recycler_view"),"ordinary Firefox lists are not settings");
        check(!TamperScreens.nativeSettingsNode("org.mozilla.firefox_beta","android:id/widget_frame"),"native settings IDs require exact supported package");
        check(!TamperScreens.nativeSettingsNode("org.mozilla.firefox","webpage:widget_frame"),"page text cannot trigger native settings rule");
        for(String url:Arrays.asList("chrome://settings","BRAVE://settings/security","about:config","about:addons?x","chrome://flags#x"))
            check(TamperScreens.internalSettings(url),"internal settings URL recognized");
        for(String url:Arrays.asList("https://example.org/about:config","about:configuration","chrome://settings.evil","chrome://newtab","about:blank",""))
            check(!TamperScreens.internalSettings(url),"ordinary/lookalike URL allowed");
        return checks;
    }
}
