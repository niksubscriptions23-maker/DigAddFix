package com.digaddfix.app;

import android.content.*;

public final class ProtectionBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        if(!Protection.locked(context)) return;
        Protection.repair(context);Protection.resumeDns(context);
    }
}
