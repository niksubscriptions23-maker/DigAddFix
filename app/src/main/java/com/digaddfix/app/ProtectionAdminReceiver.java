package com.digaddfix.app;

import android.app.admin.DeviceAdminReceiver;
import android.content.*;

/** System binds this component during independently performed device-owner provisioning. */
public final class ProtectionAdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context context,Intent intent) {Protection.repair(context);}
    @Override public CharSequence onDisableRequested(Context context,Intent intent) {
        return "Release the protection lock in DigAddFix before removing management.";
    }
}
