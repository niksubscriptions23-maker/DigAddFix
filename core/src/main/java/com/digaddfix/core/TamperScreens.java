package com.digaddfix.core;

import java.util.*;

/** Exact package boundaries and narrow native screen matching; never webpage text. */
public final class TamperScreens {
    private TamperScreens() {}
    private static final Set<String> INSTALLERS=new HashSet<>(Arrays.asList(
        "com.android.packageinstaller","com.google.android.packageinstaller",
        "com.android.permissioncontroller","com.google.android.permissioncontroller",
        "com.miui.packageinstaller","com.samsung.android.packageinstaller"));
    private static final Set<String> STORES=new HashSet<>(Arrays.asList(
        "com.android.vending","com.sec.android.app.samsungapps","com.xiaomi.mipicks","org.fdroid.fdroid"));
    private static final Set<String> SETTINGS_SCREENS=new HashSet<>(Arrays.asList(
        "VpnSettingsActivity","AccessibilitySettingsActivity","AccessibilityDetailsSettingsActivity",
        "ApplicationSettingsActivity","ManageApplicationsActivity","AppStorageSettingsActivity",
        "InstalledAppDetailsActivity","DevelopmentSettingsActivity","DateTimeSettingsActivity",
        "ManageExternalSourcesActivity","DeviceAdminSettingsActivity","UserSettingsActivity"));
    public static String reason(String pkg,String activity) {
        if(pkg==null) return "";
        if(STORES.contains(pkg)) return "App store access is paused while protection is locked. Unlock in DigAddFix to install or update apps.";
        String cls=activity==null?"":activity;
        if(INSTALLERS.contains(pkg)) {
            // PermissionController also hosts essential runtime consent. Never block that entire package.
            if(cls.contains("GrantPermissions") || cls.contains(".permission.")) return "";
            for(String name:Arrays.asList("InstallStart","InstallLaunch","InstallInstalling","PackageInstallerActivity","UninstallerActivity","UninstallActivity"))
                if(cls.endsWith("."+name)) return "Installation and removal require unlocking protection in DigAddFix.";
        }
        if(pkg.equals("com.android.settings") && cls.startsWith("com.android.settings.")) {
            for(String screen:SETTINGS_SCREENS) if(cls.endsWith("$"+screen) || cls.endsWith("."+screen))
                return "This settings screen can change protection. Unlock in DigAddFix first.";
            if(cls.endsWith(".PrivateDnsModeDialog") || cls.endsWith(".VpnSettings") || cls.endsWith(".InstalledAppDetails"))
                return "This settings screen can change protection. Unlock in DigAddFix first.";
        }
        if((pkg.equals("com.brave.browser") || pkg.equals("com.android.chrome")) && (cls.equals("org.chromium.chrome.browser.settings.SettingsActivity") ||
            cls.equals("com.google.android.apps.chrome.MainPreferences")))
            return "Browser settings are paused while protection is locked. Unlock in DigAddFix first.";
        return "";
    }
    public static boolean nativeSettingsNode(String pkg,String id) {
        if(!"org.mozilla.firefox".equals(pkg)) return false;
        return "android:id/widget_frame".equals(id) || "org.mozilla.firefox:id/switchWidget".equals(id) ||
            "org.mozilla.firefox:id/add_ons_list".equals(id) || "org.mozilla.firefox:id/addonSettingsEngineView".equals(id);
    }
    public static boolean internalSettings(String address) {
        if(address==null || address.length()>4096) return false;
        String value=address.trim().toLowerCase(Locale.ROOT);
        for(String base:Arrays.asList("chrome://settings","chrome://flags","brave://settings","brave://flags",
            "about:config","about:addons","about:preferences"))
            if(value.equals(base) || value.startsWith(base+"/") || value.startsWith(base+"?") || value.startsWith(base+"#")) return true;
        return false;
    }
}
