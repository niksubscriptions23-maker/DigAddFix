package com.digaddfix.core;

import java.util.*;

/** Stable packages supported by DNS filtering and narrowly scoped address readers. */
public final class Browsers {
    private Browsers() {}
    public static final class Browser {
        public final String packageName,label;
        private final Set<String> addressIds;
        Browser(String packageName,String label,String... ids) {
            this.packageName=packageName;this.label=label;
            Set<String> names=new HashSet<>();
            for(String id:ids) names.add(packageName+":id/"+id);
            addressIds=Collections.unmodifiableSet(names);
        }
        public boolean isAddressNode(String resourceId) {return addressIds.contains(resourceId);}
    }
    private static final List<Browser> ALL=Collections.unmodifiableList(Arrays.asList(
        new Browser("com.brave.browser","Brave","url_bar","location_bar_edit_text"),
        new Browser("com.android.chrome","Chrome","url_bar","location_bar_edit_text"),
        new Browser("org.mozilla.firefox","Firefox","mozac_browser_toolbar_url_view")
    ));
    public static List<Browser> all() {return ALL;}
    public static Browser find(String packageName) {
        for(Browser browser:ALL) if(browser.packageName.equals(packageName)) return browser;
        return null;
    }
    /** Exact intersection only: empty means do not establish a VPN. */
    public static List<String> installedScope(Collection<String> installedPackages) {
        List<String> out=new ArrayList<>();
        for(Browser browser:ALL) if(installedPackages.contains(browser.packageName)) out.add(browser.packageName);
        return Collections.unmodifiableList(out);
    }
}
