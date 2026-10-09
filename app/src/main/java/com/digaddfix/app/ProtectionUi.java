package com.digaddfix.app;

import android.app.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.digaddfix.core.Credential;
import java.util.*;
import java.util.concurrent.*;

/** Hash off the UI thread; credentials are excluded from screenshots, saved state and autofill. */
final class ProtectionUi {
    private final Activity activity;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    ProtectionUi(Activity activity) {this.activity=activity;}
    void close() {worker.shutdown();}
    private boolean alive() {return !activity.isFinishing() && !activity.isDestroyed();}
    private LinearLayout form() {LinearLayout f=new LinearLayout(activity);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(36,16,36,16);return f;}
    private EditText input(LinearLayout form,String hint,boolean pin) {
        EditText e=new EditText(activity);e.setHint(hint);e.setSingleLine(true);e.setSaveEnabled(false);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);e.setFilterTouchesWhenObscured(true);
        e.setInputType(pin?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(pin?12:64)});form.addView(e);return e;
    }
    private void secure(AlertDialog dialog) {dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getButton(-1).setFilterTouchesWhenObscured(true);}
    void setup() {
        if(Protection.locked(activity)) {info("Protection is already locked",Protection.status(activity));return;}
        String issue=Protection.prerequisites(activity);if(!issue.isEmpty()) {info("Complete setup first",issue);return;}
        LinearLayout form=form();EditText pin=input(form,"New PIN (6–12 digits)",true),confirm=input(form,"Repeat PIN",true);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Set protection PIN")
            .setMessage((new ManagedProtection(activity).capable()?"Device-owner mode: Android will restrict installations (including updates), VPN/private-DNS changes, extra users, uninstall, force-stop and data clearing for protected packages.":
                "Personal guard locks DigAddFix controls and interrupts known installer, store and bypass-settings screens. Accessibility can be removed; this mode cannot guarantee installation or uninstall prevention.")+
                "\n\nSet system DNS in each browser first. Keep the recovery code outside this phone. A trusted person can hold your PIN. Unlocking releases restrictions while existing filters keep running.")
            .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Create recovery code",null).create();
        dialog.setOnShowListener(ignored->{secure(dialog);dialog.getButton(-1).setOnClickListener(v->{
            String value=pin.getText().toString();
            if(!Credential.validPin(value)) {pin.setError("Use 6–12 digits; avoid repeated or sequential digits.");return;}
            if(!value.equals(confirm.getText().toString())) {confirm.setError("PINs do not match.");return;}
            char[] secret=value.toCharArray();pin.getText().clear();confirm.getText().clear();dialog.getButton(-1).setEnabled(false);
            worker.execute(()->{
                String code=Credential.recoveryCode(),pinRecord,recoveryRecord;char[] recovery=Credential.normalizeRecovery(code).toCharArray();
                try {pinRecord=Credential.create(secret);recoveryRecord=Credential.create(recovery);}
                catch(Exception e) {activity.runOnUiThread(()->{if(alive()) {dialog.dismiss();info("Setup failed",Protection.safeMessage(e));}});return;}
                finally {Arrays.fill(secret,'\0');Arrays.fill(recovery,'\0');}
                final String p=pinRecord,r=recoveryRecord;
                activity.runOnUiThread(()->{if(alive() && dialog.isShowing()) {dialog.dismiss();confirmRecovery(code,p,r);}});
            });
        });});dialog.show();
    }
    private void confirmRecovery(String code,String pinRecord,String recoveryRecord) {
        LinearLayout form=form();TextView shown=new TextView(activity);shown.setText(code);shown.setTextSize(20);shown.setPadding(0,8,0,16);form.addView(shown);
        EditText proof=input(form,"Last 8 characters (without hyphens)",false);
        CheckBox saved=new CheckBox(activity);saved.setText("I saved the complete code outside this phone");saved.setFilterTouchesWhenObscured(true);form.addView(saved);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Save your recovery code")
            .setMessage("This code is shown once. It releases protection if you lose your PIN. Write down all 32 characters, then confirm the last eight.")
            .setView(form).setNegativeButton("Cancel setup",null).setPositiveButton("Lock protection",null).create();
        dialog.setOnShowListener(ignored->{secure(dialog);dialog.getButton(-1).setOnClickListener(v->{
            if(!saved.isChecked() || !Credential.normalizeRecovery(code).substring(24).equalsIgnoreCase(proof.getText().toString().replace("-","").replace(" ",""))) {
                proof.setError("Save the full code and confirm its last eight characters.");return;
            }
            proof.getText().clear();dialog.getButton(-1).setEnabled(false);
            worker.execute(()->{
                String result;
                try {Protection.enable(activity,pinRecord,recoveryRecord);result="Protection is locked.\n\n"+Protection.status(activity);}
                catch(Exception e) {result=Protection.safeMessage(e)+"\n\n"+Protection.status(activity)+"\nUse your PIN or recovery code to release a partial lock.";}
                final String message=result;activity.runOnUiThread(()->{if(alive()) {dialog.dismiss();info("Protection lock",message);}});
            });
        });});dialog.show();
    }
    void unlock(boolean recovery) {
        if(!Protection.locked(activity)) {info("Protection is off","There is no lock to release.");return;}
        LinearLayout form=form();EditText edit=input(form,recovery?"Complete recovery code":"Protection PIN",!recovery);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(recovery?"Recover protection access":"Release protection lock")
            .setMessage("This releases managed restrictions and guarded controls. Existing filters remain active until you change them. Five incorrect attempts begin a retry delay.")
            .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Unlock",null).create();
        dialog.setOnShowListener(ignored->{secure(dialog);dialog.getButton(-1).setOnClickListener(v->{
            String value=edit.getText().toString();if(recovery) value=Credential.normalizeRecovery(value);
            char[] secret=value.toCharArray();edit.getText().clear();dialog.getButton(-1).setEnabled(false);
            worker.execute(()->{
                String result;try {Protection.unlock(activity,secret,recovery);result="Lock released. Existing filters remain enabled.";}
                catch(Exception e) {result=Protection.safeMessage(e);}
                final String message=result;activity.runOnUiThread(()->{if(alive()) {dialog.dismiss();info("Protection lock",message);}});
            });
        });});dialog.show();
    }
    void repair() {worker.execute(()->{Protection.repair(activity);activity.runOnUiThread(()->{if(alive()) {Protection.resumeDns(activity);info("Protection status",Protection.status(activity));}});});}
    void removeTestManagement() {
        if(Protection.locked(activity)) {info("Protection is locked","Release the lock with your PIN or recovery code first.");return;}
        new AlertDialog.Builder(activity).setTitle("Remove test device management?")
            .setMessage("This debug-only action removes DigAddFix's device-owner role after its lock has been released. It does not reset the phone. Android documents this API for testing only; it may not clear unrelated policies. Existing filters can continue voluntarily. Production deprovisioning is not implemented.")
            .setNegativeButton("Cancel",null).setPositiveButton("Remove test management",(d,w)->worker.execute(()->{
                String result;try {new ManagedProtection(activity).removeTestOwner();result="Test management removed. DigAddFix is now voluntary protection.";}
                catch(Exception e) {result=Protection.safeMessage(e);}final String message=result;
                activity.runOnUiThread(()->{if(alive()) info("Test management",message);});
            })).show();
    }
    void info(String title,String message) {new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("Done",null).show();}
}
