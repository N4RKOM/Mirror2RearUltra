package com.tpkarras.mirror2rearultra;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class DisplayActivity extends AppCompatActivity {
    static final String EXTRA_DASHBOARD_ONLY = "dashboard_only";
    private static final String TAG = "Mirror2RearConsent";
    private static final String XIAOMI_REAR_SCREEN_PACKAGE = "com.xiaomi.misubscreenui";

    private MediaProjectionManager mediaProjectionManager;
    private ActivityResultLauncher<Intent> projectionPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectionPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> onProjectionPermissionResult(result.getResultCode(), result.getData())
        );

        if (!MirrorState.isActive()) {
            finish();
            return;
        }

        if (!isPackageInstalled(XIAOMI_REAR_SCREEN_PACKAGE)) {
            showFatalError(R.string.incompatible);
            return;
        }

        DisplayManager displayManager = getSystemService(DisplayManager.class);
        int rearDisplayId = findRearDisplayId(displayManager);
        if (rearDisplayId == Display.INVALID_DISPLAY) {
            showFatalError(R.string.rear_display_missing);
            return;
        }

        boolean dashboardOnly = getIntent().getBooleanExtra(EXTRA_DASHBOARD_ONLY, false);
        if (dashboardOnly || !MirrorSettings.loadDashboardSettings(this).contentMode.usesProjection()) {
            launchRearActivity(rearDisplayId, false, dashboardOnly);
            return;
        }

        mediaProjectionManager = getSystemService(MediaProjectionManager.class);
        if (mediaProjectionManager == null) {
            showFatalError(R.string.projection_failed);
            return;
        }

        projectionPermissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
    }

    @SuppressWarnings("deprecation")
    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void onProjectionPermissionResult(int resultCode, @Nullable Intent resultData) {
        if (!MirrorState.isActive()) {
            finish();
            return;
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            MirrorState.setActive(this, false);
            finish();
            return;
        }

        int rearDisplayId = findRearDisplayId(getSystemService(DisplayManager.class));
        if (rearDisplayId == Display.INVALID_DISPLAY) {
            showFatalError(R.string.rear_display_missing);
            return;
        }

        try {
            ContextCompat.startForegroundService(
                    this,
                    ForegroundService.createStartIntent(this, resultCode, resultData)
            );

            launchRearActivity(rearDisplayId, true, false);
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to start mirroring on display " + rearDisplayId, error);
            stopService(ForegroundService.createStopIntent(this));
            showFatalError(R.string.projection_failed);
        }
    }

    private void launchRearActivity(int rearDisplayId, boolean hasProjection, boolean dashboardOnly) {
        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(rearDisplayId);
            Intent mirrorIntent = new Intent(this, Mirror.class)
                    .putExtra(Mirror.EXTRA_SESSION_HAS_PROJECTION, hasProjection)
                    .putExtra(Mirror.EXTRA_DASHBOARD_ONLY, dashboardOnly)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(mirrorIntent, options.toBundle());
            finish();
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to start rear content on display " + rearDisplayId, error);
            if (hasProjection) {
                stopService(ForegroundService.createStopIntent(this));
            }
            showFatalError(R.string.projection_failed);
        }
    }

    static int findRearDisplayId(@Nullable DisplayManager displayManager) {
        if (displayManager == null) {
            return Display.INVALID_DISPLAY;
        }

        Display best = chooseSmallestNonDefaultDisplay(
                displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        );
        if (best == null) {
            best = chooseSmallestNonDefaultDisplay(displayManager.getDisplays());
        }
        return best == null ? Display.INVALID_DISPLAY : best.getDisplayId();
    }

    @Nullable
    private static Display chooseSmallestNonDefaultDisplay(Display[] displays) {
        Display best = null;
        long bestArea = Long.MAX_VALUE;
        for (Display display : displays) {
            if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
                continue;
            }
            Display.Mode mode = display.getMode();
            long area = (long) mode.getPhysicalWidth() * mode.getPhysicalHeight();
            if (area < bestArea) {
                best = display;
                bestArea = area;
            }
        }
        return best;
    }

    private void showFatalError(int messageResource) {
        MirrorState.setActive(this, false);
        new AlertDialog.Builder(this)
                .setMessage(messageResource)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }
}
