package com.tpkarras.mirror2rearultra;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Automatic profile switching and automatic rear-output visibility.
 *
 * <p>Split out of {@link SettingsActivity}. Both switches depend on usage
 * access, so the permission prompt and the running state belong next to them
 * rather than buried in the middle of a long settings page.
 */
public class AutomationActivity extends AppCompatActivity implements AutoProfileState.Listener {

    private MaterialSwitch autoProfileSwitch;
    private MaterialSwitch autoVisibilitySwitch;
    private TextView autoProfileStatus;
    private View usageAccessButton;
    private TextView overlayAccessStatus;
    private View overlayAccessButton;
    private ViewGroup content;

    /** Guards the listeners while the UI is being written from stored state. */
    private boolean bindingUi;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_automation);

        MaterialToolbar toolbar = findViewById(R.id.automation_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        autoProfileSwitch = findViewById(R.id.auto_profile_switch);
        autoVisibilitySwitch = findViewById(R.id.auto_visibility_switch);
        autoProfileStatus = findViewById(R.id.auto_profile_status);
        usageAccessButton = findViewById(R.id.usage_access_button);
        overlayAccessStatus = findViewById(R.id.overlay_access_status);
        overlayAccessButton = findViewById(R.id.overlay_access_button);
        content = findViewById(R.id.automation_content);

        SettingsLayout.constrainContentOnWideScreens(this, content);
        bindInteractions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        AutoProfileState.addListener(this);
    }

    @Override
    protected void onStop() {
        AutoProfileState.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindingUi = true;
        autoProfileSwitch.setChecked(MirrorSettings.isAutoProfileEnabled(this));
        autoVisibilitySwitch.setChecked(MirrorSettings.isAutoVisibilityEnabled(this));
        bindingUi = false;
        // Returning from the system usage-access screen is the common way back
        // here, so re-arm the monitor if the permission has just been granted.
        if (MirrorSettings.isAutoProfileEnabled(this)
                && ForegroundAppMonitor.hasUsageAccess(this)) {
            MirrorSettings.setAutoProfileEnabled(this, true);
        }
        updateAutoProfileUi(AutoProfileState.get());
        updateOverlayAccessUi();
    }

    @Override
    public void onAutoProfileChanged(AutoProfileState.Snapshot snapshot) {
        runOnUiThread(() -> updateAutoProfileUi(snapshot));
    }

    private void bindInteractions() {
        autoProfileSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) {
                return;
            }
            MirrorSettings.setAutoProfileEnabled(this, checked);
            updateAutoProfileUi(AutoProfileState.get());
        });

        autoVisibilitySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) {
                return;
            }
            MirrorSettings.setAutoVisibilityEnabled(this, checked);
            updateAutoProfileUi(AutoProfileState.get());
        });

        usageAccessButton.setOnClickListener(view -> openUsageAccessSettings());
        overlayAccessButton.setOnClickListener(view -> openOverlayAccessSettings());
    }

    private void updateAutoProfileUi(AutoProfileState.Snapshot snapshot) {
        boolean enabled = MirrorSettings.isAutoProfileEnabled(this)
                || MirrorSettings.isAutoVisibilityEnabled(this);
        boolean permitted = ForegroundAppMonitor.hasUsageAccess(this);
        usageAccessButton.setVisibility(enabled && !permitted ? View.VISIBLE : View.GONE);
        if (!enabled) {
            autoProfileStatus.setText(R.string.auto_profile_disabled);
            return;
        }
        if (!permitted) {
            autoProfileStatus.setText(R.string.auto_profile_permission_required);
            return;
        }
        if (snapshot.packageName == null) {
            autoProfileStatus.setText(R.string.auto_profile_waiting);
            return;
        }
        String appName = applicationName(snapshot.packageName);
        if (snapshot.profileId == null) {
            autoProfileStatus.setText(getString(R.string.auto_profile_manual_for_app, appName));
        } else {
            autoProfileStatus.setText(getString(
                    R.string.auto_profile_current,
                    MirrorSettings.profileDisplayName(
                            this, MirrorSettings.loadProfile(this, snapshot.profileId)),
                    appName
            ));
        }
    }

    private String applicationName(String packageName) {
        PackageManager manager = getPackageManager();
        try {
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            return String.valueOf(manager.getApplicationLabel(info));
        } catch (PackageManager.NameNotFoundException error) {
            return packageName;
        }
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void updateOverlayAccessUi() {
        boolean permitted = Settings.canDrawOverlays(this);
        overlayAccessStatus.setText(permitted
                ? R.string.overlay_access_granted : R.string.overlay_access_required);
        overlayAccessButton.setVisibility(permitted ? View.GONE : View.VISIBLE);
    }

    private void openOverlayAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }
}
