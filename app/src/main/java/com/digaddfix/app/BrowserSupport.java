package com.digaddfix.app;

import android.content.Context;
import android.content.pm.PackageManager;
import com.digaddfix.core.Browsers;
import java.util.*;

final class BrowserSupport {
    private BrowserSupport() {}
    static List<String> installed(Context context) {
        List<String> packages=new ArrayList<>();
        for(Browsers.Browser browser:Browsers.all()) {
            try {
                if(context.getPackageManager().getApplicationInfo(browser.packageName,0).enabled)
                    packages.add(browser.packageName);
            } catch(PackageManager.NameNotFoundException ignored) {}
        }
        return Browsers.installedScope(packages);
    }
    static String labels(Collection<String> packages) {
        List<String> names=new ArrayList<>();
        for(String name:packages) {
            Browsers.Browser browser=Browsers.find(name);
            if(browser!=null) names.add(browser.label);
        }
        return android.text.TextUtils.join(", ",names);
    }
}
