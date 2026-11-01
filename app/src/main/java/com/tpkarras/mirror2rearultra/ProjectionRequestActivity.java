package com.tpkarras.mirror2rearultra;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * Asks for screen capture in the middle of a running session.
 *
 * <p>Invisible, and over in a moment when the app-op lets the system skip its
 * dialog. It lives on the main display, since the rear one has nowhere to
 * show a dialog; with the app-op there is nothing to show anyway.
 */
public class ProjectionRequestActivity extends Activity {
    private static final String TAG = "Mirror2RearConsent";
    private static final int REQUEST_CAPTURE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        if (!MirrorState.isActive() || manager == null) {
            ForegroundService.reportProjectionDenied();
            finish();
            return;
        }
        if (savedInstanceState == null) {
            try {
                startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
            } catch (RuntimeException error) {
                Log.e(TAG, "Unable to ask for screen capture", error);
                ForegroundService.reportProjectionDenied();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) {
            return;
        }
        if (resultCode == RESULT_OK && data != null && MirrorState.isActive()) {
            try {
                ContextCompat.startForegroundService(
                        this, ForegroundService.createStartIntent(this, resultCode, data, true));
            } catch (RuntimeException error) {
                Log.e(TAG, "Unable to start the projection", error);
                ForegroundService.reportProjectionDenied();
            }
        } else {
            ForegroundService.reportProjectionDenied();
        }
        finish();
    }
}
