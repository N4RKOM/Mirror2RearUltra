package com.tpkarras.mirror2rearultra;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.Map;

/**
 * The first-run checklist: the Rear Unlock module (required for the rear screen on HyperOS 4),
 * the permissions the features ask for, and root, which is optional.
 *
 * <p>It does not block anything. Each row reports how it stands and, when it is not met,
 * opens the place where it is fixed; the rows are rebuilt every time the screen returns from
 * a system page, so a permission just granted shows as granted. Whatever is left undone stays
 * visible on the home screen as a banner.
 */
public class OnboardingActivity extends AppCompatActivity implements DeviceCapabilityState.Listener {
    private static final String PREFS = "onboarding";
    private static final String KEY_SHOWN = "shown";

    private LinearLayout moduleGroup;
    private LinearLayout accessGroup;
    private LinearLayout widgetsGroup;
    private LinearLayout rootGroup;
    private View moduleSteps;
    private com.google.android.material.button.MaterialButton doneButton;
    private ActivityResultLauncher<String[]> runtimeLauncher;

    /** Whether the checklist has been put in front of the user once. */
    static boolean wasShown(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SHOWN, false);
    }

    static void markShown(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SHOWN, true).apply();
    }

    static Intent intent(Context context) {
        return new Intent(context, OnboardingActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Seeing it once is enough: leaving it half done must not bring it back at every launch.
        markShown(this);
        setContentView(R.layout.activity_onboarding);
        MaterialToolbar toolbar = findViewById(R.id.onboarding_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        moduleGroup = findViewById(R.id.onboarding_module_group);
        accessGroup = findViewById(R.id.onboarding_access_group);
        widgetsGroup = findViewById(R.id.onboarding_widgets_group);
        rootGroup = findViewById(R.id.onboarding_root_group);
        moduleSteps = findViewById(R.id.onboarding_module_steps);
        doneButton = findViewById(R.id.onboarding_done_button);
        SettingsLayout.constrainContentOnWideScreens(this, findViewById(R.id.onboarding_content));
        doneButton.setOnClickListener(view -> finish());
        runtimeLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), this::onRuntimeResult);
    }

    @Override
    protected void onStart() {
        super.onStart();
        DeviceCapabilityState.addListener(this);
        DeviceCapabilityState.Snapshot snapshot = DeviceCapabilityState.get();
        if (snapshot.rootAvailable == null && !snapshot.probing) {
            // The root prompt belongs to this screen: it says what root is for.
            probeRoot();
        }
    }

    @Override
    protected void onStop() {
        DeviceCapabilityState.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onDeviceCapabilityChanged(DeviceCapabilityState.Snapshot snapshot) {
        runOnUiThread(this::render);
    }

    private void render() {
        renderModule();
        renderAccess();
        renderWidgets();
        renderRoot();
    }

    // --- Module ---------------------------------------------------------------------------

    private void renderModule() {
        RearUnlockModule.State state = RearUnlockModule.state(this);
        moduleGroup.removeAllViews();
        boolean loaded = state == RearUnlockModule.State.LOADED;
        int summary;
        int status;
        switch (state) {
            case LOADED:
                summary = R.string.onboarding_module_loaded;
                status = R.string.onboarding_module_state_loaded;
                break;
            case OUTDATED:
                summary = R.string.onboarding_module_outdated;
                status = R.string.onboarding_module_state_outdated;
                break;
            case NOT_LOADED:
                summary = R.string.onboarding_module_not_loaded;
                status = R.string.onboarding_module_state_waiting;
                break;
            default:
                summary = R.string.onboarding_module_not_installed;
                status = R.string.onboarding_module_state_missing;
                break;
        }
        addRow(moduleGroup, OnboardingRows.row(this, getString(R.string.onboarding_module_title),
                getString(summary), R.drawable.ic_row_protection, getString(status),
                loaded ? OnboardingRows.TONE_GOOD : OnboardingRows.TONE_BAD, null));
        if (!loaded) {
            if (state != RearUnlockModule.State.NOT_LOADED) {
                addRow(moduleGroup, OnboardingRows.row(this,
                        getString(R.string.onboarding_module_download), null,
                        R.drawable.ic_row_add, "", OnboardingRows.TONE_QUIET,
                        this::openReleases));
            }
            addRow(moduleGroup, OnboardingRows.row(this,
                    getString(R.string.onboarding_module_recheck), null,
                    R.drawable.ic_row_flip, "", OnboardingRows.TONE_QUIET, this::render));
        }
        moduleSteps.setVisibility(loaded ? View.GONE : View.VISIBLE);
        doneButton.setText(loaded
                ? R.string.onboarding_done : R.string.onboarding_continue_without_module);
    }

    private void openReleases() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RearUnlockModule.RELEASES_URL)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, RearUnlockModule.RELEASES_URL, Toast.LENGTH_LONG).show();
        }
    }

    // --- Permissions ----------------------------------------------------------------------

    private void renderAccess() {
        accessGroup.removeAllViews();
        addPermission(accessGroup, R.string.onboarding_overlay_title,
                R.string.onboarding_overlay_summary, R.drawable.ic_row_screen,
                Settings.canDrawOverlays(this), () -> openSystemPage(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()))));
        addPermission(accessGroup, R.string.onboarding_usage_title,
                R.string.onboarding_usage_summary, R.drawable.ic_row_profiles,
                ForegroundAppMonitor.hasUsageAccess(this), () -> openSystemPage(
                        new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                                Uri.parse("package:" + getPackageName()))));
        addPermission(accessGroup, R.string.onboarding_listener_title,
                R.string.onboarding_listener_summary, R.drawable.ic_row_content,
                MediaWidgetState.hasNotificationAccess(this), this::openListenerSettings);
        if (Build.VERSION.SDK_INT >= 33) {
            addRuntimePermission(accessGroup, R.string.onboarding_post_title,
                    R.string.onboarding_post_summary, R.drawable.ic_row_about,
                    new String[] {Manifest.permission.POST_NOTIFICATIONS});
        }
    }

    private void renderWidgets() {
        widgetsGroup.removeAllViews();
        addRuntimePermission(widgetsGroup, R.string.onboarding_calendar_title,
                R.string.onboarding_calendar_summary, R.drawable.ic_row_templates,
                new String[] {Manifest.permission.READ_CALENDAR});
        addRuntimePermission(widgetsGroup, R.string.onboarding_location_title,
                R.string.onboarding_location_summary, R.drawable.ic_row_level,
                new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
        addRuntimePermission(widgetsGroup, R.string.onboarding_activity_title,
                R.string.onboarding_activity_summary, R.drawable.ic_row_auto_brightness,
                new String[] {Manifest.permission.ACTIVITY_RECOGNITION});
        if (Build.VERSION.SDK_INT >= 31) {
            addRuntimePermission(widgetsGroup, R.string.onboarding_bluetooth_title,
                    R.string.onboarding_bluetooth_summary, R.drawable.ic_row_quality,
                    new String[] {Manifest.permission.BLUETOOTH_CONNECT});
        }
    }

    /** A runtime permission: granted when the first of [permissions] is, asked together. */
    private void addRuntimePermission(ViewGroup group, int title, int summary, int icon,
                                      String[] permissions) {
        boolean granted = ContextCompat.checkSelfPermission(this, permissions[0])
                == PackageManager.PERMISSION_GRANTED;
        addPermission(group, title, summary, icon, granted, () -> runtimeLauncher.launch(permissions));
    }

    private void addPermission(ViewGroup group, int title, int summary, int icon,
                               boolean granted, Runnable grant) {
        addRow(group, OnboardingRows.row(this, getString(title), getString(summary), icon,
                getString(granted
                        ? R.string.onboarding_state_allowed : R.string.onboarding_state_not_allowed),
                granted ? OnboardingRows.TONE_GOOD : OnboardingRows.TONE_BAD,
                granted ? null : grant));
    }

    private void onRuntimeResult(Map<String, Boolean> result) {
        // A permission the system will no longer ask for can only be given from the app's page.
        for (Map.Entry<String, Boolean> entry : result.entrySet()) {
            if (!entry.getValue()
                    && !ActivityCompat.shouldShowRequestPermissionRationale(this, entry.getKey())) {
                openSystemPage(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
                break;
            }
        }
        render();
    }

    private void openListenerSettings() {
        ComponentName service = new ComponentName(this, MediaNotificationListenerService.class);
        Intent details = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        service.flattenToString());
        try {
            startActivity(details);
        } catch (RuntimeException unavailable) {
            openSystemPage(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    private void openSystemPage(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // --- Root -----------------------------------------------------------------------------

    private void renderRoot() {
        rootGroup.removeAllViews();
        DeviceCapabilityState.Snapshot snapshot = DeviceCapabilityState.get();
        boolean root = Boolean.TRUE.equals(snapshot.rootAvailable);
        int rootStatus = snapshot.probing ? R.string.onboarding_root_checking
                : snapshot.rootAvailable == null ? R.string.capability_unknown
                : root ? R.string.onboarding_root_available : R.string.onboarding_root_unavailable;
        addRow(rootGroup, OnboardingRows.row(this, getString(R.string.onboarding_root_title),
                getString(R.string.onboarding_root_summary), R.drawable.ic_row_touch,
                getString(rootStatus),
                root ? OnboardingRows.TONE_GOOD : OnboardingRows.TONE_QUIET,
                snapshot.probing ? null : this::probeRoot));
        boolean backlight = Boolean.TRUE.equals(snapshot.rearBrightnessAvailable);
        addRow(rootGroup, OnboardingRows.row(this, getString(R.string.onboarding_backlight_title),
                getString(R.string.onboarding_backlight_summary), R.drawable.ic_row_brightness,
                getString(backlight
                        ? R.string.onboarding_root_available : R.string.onboarding_backlight_needs_root),
                backlight ? OnboardingRows.TONE_GOOD : OnboardingRows.TONE_QUIET, null));
        boolean silent = ProjectionConsent.isSilent(this);
        addRow(rootGroup, OnboardingRows.row(this, getString(R.string.onboarding_silent_title),
                getString(R.string.onboarding_silent_summary), R.drawable.ic_row_camera,
                getString(silent ? R.string.onboarding_state_on : R.string.onboarding_state_off),
                silent ? OnboardingRows.TONE_GOOD : OnboardingRows.TONE_QUIET,
                silent ? null : this::grantSilentCapture));
    }

    private void probeRoot() {
        RearBrightnessController.probeCapabilities((root, rearBrightness) -> {
            // DeviceCapabilityState publishes the result to this screen.
        });
    }

    private void grantSilentCapture() {
        Context app = getApplicationContext();
        new Thread(() -> {
            boolean granted = ProjectionConsent.grantWithRoot(app);
            runOnUiThread(() -> {
                if (!granted) {
                    Toast.makeText(this, R.string.silent_capture_failed, Toast.LENGTH_SHORT).show();
                }
                render();
            });
        }, "silent-capture-grant").start();
    }

    // --- Rows -----------------------------------------------------------------------------

    private void addRow(ViewGroup group, View row) {
        if (group.getChildCount() > 0) {
            View divider = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getResources().getDimensionPixelSize(R.dimen.divider_height));
            params.setMarginStart(getResources().getDimensionPixelSize(R.dimen.spacing_md)
                    + (int) (40 * getResources().getDisplayMetrics().density));
            divider.setLayoutParams(params);
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.hyper_divider));
            group.addView(divider);
        }
        group.addView(row);
    }
}
