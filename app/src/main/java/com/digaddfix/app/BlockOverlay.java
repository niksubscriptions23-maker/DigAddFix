package com.digaddfix.app;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.digaddfix.core.OverlayClock;

/** Accessibility overlay, not an Activity or a SYSTEM_ALERT_WINDOW permission. */
final class BlockOverlay {
    private final AccessibilityService service;
    private final WindowManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final OverlayClock clock=new OverlayClock();
    private View view;
    private TextView countdown;
    BlockOverlay(AccessibilityService service) {
        this.service=service;manager=(WindowManager)service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }
    private int dp(int n) {return Math.round(n*service.getResources().getDisplayMetrics().density);}
    void show(String key,String title,String label,String reason) {
        if(!clock.start(key,SystemClock.elapsedRealtime())) return;
        handler.removeCallbacksAndMessages(null);removeView();
        FrameLayout root=new FrameLayout(service);root.setBackgroundColor(Color.rgb(16,28,34));
        root.setClickable(true);root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout content=new LinearLayout(service);content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);content.setPadding(dp(28),dp(48),dp(28),dp(48));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        text(content,"PAUSE & RESET",14,Color.rgb(102,229,181));
        text(content,title,32,Color.WHITE);
        text(content,label,22,Color.rgb(220,235,232));
        text(content,reason,17,Color.rgb(166,190,192));
        text(content,"Take one slow breath.\nYour attention belongs to you.",18,Color.WHITE);
        countdown=text(content,"Returning to Home in 5 seconds",16,Color.rgb(102,229,181));
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|
            WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.OPAQUE);
        params.gravity=Gravity.FILL;
        if(Build.VERSION.SDK_INT>=28) params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if(Build.VERSION.SDK_INT>=30) params.setFitInsetsTypes(0);
        try {manager.addView(root,params);view=root;}
        catch(RuntimeException e) {clock.clear();return;}
        handler.post(tick);
        handler.postDelayed(this::close,OverlayClock.DURATION_MS);
    }
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if(view==null) return;
            int seconds=clock.seconds(SystemClock.elapsedRealtime());
            countdown.setText("This pause closes in "+seconds+" second"+(seconds==1?"":"s"));
            if(seconds>0) handler.postDelayed(this,200);
        }
    };
    private TextView text(LinearLayout parent,String value,int size,int color) {
        TextView text=new TextView(service);text.setText(value);text.setTextSize(size);
        text.setTextColor(color);text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(12);p.bottomMargin=dp(12);parent.addView(text,p);return text;
    }
    private void removeView() {if(view!=null) {try {manager.removeViewImmediate(view);} catch(RuntimeException ignored) {}view=null;}}
    void close() {handler.removeCallbacksAndMessages(null);removeView();clock.clear();}
}
