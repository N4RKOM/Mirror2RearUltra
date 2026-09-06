package com.tpkarras.mirror2rearultra;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

public final class CalibrationPreviewActivity extends Activity {
    static final long PREVIEW_DURATION_MILLIS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishPreview = this::finishAndRemoveTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        CalibrationGridView pattern = new CalibrationGridView(this);
        pattern.setPreviewMode(true);
        setContentView(pattern);
        Mirror.rearScreenSwitch(true);
        handler.postDelayed(finishPreview, PREVIEW_DURATION_MILLIS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(finishPreview);
        if (!MirrorState.isActive()) {
            Mirror.rearScreenSwitch(false);
        }
        super.onDestroy();
    }
}
