package com.tpkarras.mirror2rearultra;

import android.app.ActivityOptions;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

/**
 * Everything about the projected image, in one place.
 *
 * <p>Split out of {@link SettingsActivity}, where these controls sat spread
 * through a long page. The Image group is scoped to the profile named by the
 * picker at the top. The Calibration group deliberately mixes scopes:
 * projection quality and the calibration grid are app-wide, while zoom, the
 * offsets and the reset belong to the selected profile. They live together
 * because they are the tools you reach for in one sitting while lining the
 * rear panel up.
 */
public class ImageSettingsActivity extends AppCompatActivity implements MirrorState.Listener {

    private HyperValueRow profileInput;
    private HyperValueRow scaleModeInput;
    private HyperValueRow rotationInput;
    private MaterialSwitch mirrorSwitch;
    private HyperSlider brightnessSlider;
    private MaterialSwitch autoBrightnessSwitch;
    private TextView brightnessValue;
    private HyperValueRow qualityModeInput;
    private MaterialSwitch calibrationGridSwitch;
    private View calibrationPreviewButton;
    private View framedNotice;
    private HyperSlider zoomSlider;
    private HyperSlider horizontalOffsetSlider;
    private HyperSlider verticalOffsetSlider;
    private TextView zoomValue;
    private TextView horizontalOffsetValue;
    private TextView verticalOffsetValue;
    private ViewGroup content;

    private MirrorProfile activeProfile;
    /** Guards the listeners while the UI is being written from stored state. */
    private boolean bindingUi;
    private String[] scaleLabels;
    private String[] rotationLabels;
    private String[] qualityLabels;
    private List<MirrorProfile> profiles;
    private String[] profileLabels;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_settings);

        MaterialToolbar toolbar = findViewById(R.id.image_settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        profileInput = findViewById(R.id.profile_input);
        scaleModeInput = findViewById(R.id.scale_mode_input);
        rotationInput = findViewById(R.id.rotation_input);
        mirrorSwitch = findViewById(R.id.mirror_switch);
        brightnessSlider = findViewById(R.id.brightness_slider);
        autoBrightnessSwitch = findViewById(R.id.auto_brightness_switch);
        // No sensor, no promise: the row would do nothing on a device without
        // one, and there would be no way to tell from looking at it.
        boolean hasLightSensor = new AutoBrightnessSensor(this, factor -> { }).isAvailable();
        autoBrightnessSwitch.setVisibility(hasLightSensor ? View.VISIBLE : View.GONE);
        brightnessValue = findViewById(R.id.brightness_value);
        qualityModeInput = findViewById(R.id.quality_mode_input);
        calibrationGridSwitch = findViewById(R.id.calibration_grid_switch);
        calibrationPreviewButton = findViewById(R.id.calibration_preview_button);
        zoomSlider = findViewById(R.id.zoom_slider);
        horizontalOffsetSlider = findViewById(R.id.horizontal_offset_slider);
        verticalOffsetSlider = findViewById(R.id.vertical_offset_slider);
        framedNotice = findViewById(R.id.calibration_framed_notice);
        zoomValue = findViewById(R.id.zoom_value);
        horizontalOffsetValue = findViewById(R.id.horizontal_offset_value);
        verticalOffsetValue = findViewById(R.id.vertical_offset_value);
        content = findViewById(R.id.image_settings_content);

        scaleLabels = getResources().getStringArray(R.array.scale_mode_entries);
        rotationLabels = getResources().getStringArray(R.array.rotation_entries);
        qualityLabels = getResources().getStringArray(R.array.quality_mode_entries);
        scaleModeInput.setEntries(scaleLabels);
        rotationInput.setEntries(rotationLabels);
        qualityModeInput.setEntries(qualityLabels);

        SettingsLayout.constrainContentOnWideScreens(this, content);
        bindInteractions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        MirrorState.addListener(this);
        updateMirrorState(MirrorState.isActive());
    }

    @Override
    protected void onStop() {
        MirrorState.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The profile can be changed from the hub or the profile manager while
        // this screen is stopped, so rebind rather than trusting cached state.
        activeProfile = MirrorSettings.loadActiveProfile(this);
        refreshProfileOptions(activeProfile.id);
        renderProfile(activeProfile);
        renderGlobalSettings();
    }

    @Override
    public void onMirrorStateChanged(boolean active) {
        runOnUiThread(() -> updateMirrorState(active));
    }

    private void bindInteractions() {
        profileInput.setOnItemSelectedListener(position -> {
            if (bindingUi || position < 0 || position >= profiles.size()) {
                return;
            }
            activeProfile = MirrorSettings.selectProfile(this, profiles.get(position).id);
            renderProfile(activeProfile);
            content.announceForAccessibility(getString(
                    R.string.profile_selected_announcement,
                    profileName(activeProfile)
            ));
        });

        scaleModeInput.setOnItemSelectedListener(position -> {
            if (bindingUi || activeProfile == null) {
                return;
            }
            MirrorProfile.ScaleMode[] modes = MirrorProfile.ScaleMode.values();
            if (position >= 0 && position < modes.length) {
                save(activeProfile.withScaleMode(modes[position]));
            }
        });

        rotationInput.setOnItemSelectedListener(position -> {
            if (!bindingUi && activeProfile != null && position >= 0) {
                save(activeProfile.withRotationDegrees(position * 90));
            }
        });

        mirrorSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi && activeProfile != null) {
                save(activeProfile.withMirrorHorizontally(checked));
            }
        });

        autoBrightnessSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) {
                return;
            }
            DashboardWidgetLayout.setAutoBrightnessEnabled(this, checked);
            // The session watches the profile store, not this one, so a save
            // is what tells a running panel that the switch moved.
            MirrorSettings.saveDashboardSettings(this,
                    MirrorSettings.loadDashboardSettings(this));
        });

        brightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            int percent = Math.round(value);
            updateBrightnessValue(percent);
            if (fromUser && !bindingUi && activeProfile != null) {
                save(activeProfile.withBrightnessPercent(percent));
            }
        });

        zoomSlider.addOnChangeListener((slider, value, fromUser) -> {
            int percent = Math.round(value);
            updateZoomValue(percent);
            if (fromUser && !bindingUi && activeProfile != null) {
                save(activeProfile.withZoomPercent(percent));
            }
        });

        horizontalOffsetSlider.addOnChangeListener((slider, value, fromUser) -> {
            int percent = Math.round(value);
            updateHorizontalOffsetValue(percent);
            if (fromUser && !bindingUi && activeProfile != null) {
                save(activeProfile.withHorizontalOffsetPercent(percent));
            }
        });

        verticalOffsetSlider.addOnChangeListener((slider, value, fromUser) -> {
            int percent = Math.round(value);
            updateVerticalOffsetValue(percent);
            if (fromUser && !bindingUi && activeProfile != null) {
                save(activeProfile.withVerticalOffsetPercent(percent));
            }
        });

        // Applies to every profile, unlike the rows above it.
        qualityModeInput.setOnItemSelectedListener(position -> {
            ProjectionQuality[] modes = ProjectionQuality.values();
            if (position >= 0 && position < modes.length) {
                MirrorSettings.setProjectionQuality(this, modes[position]);
            }
        });

        calibrationGridSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                MirrorSettings.setCalibrationGridEnabled(this, checked);
            }
        });

        calibrationPreviewButton.setOnClickListener(view -> startCalibrationPreview());

        findViewById(R.id.reset_calibration_button).setOnClickListener(view -> {
            if (activeProfile == null) {
                return;
            }
            save(activeProfile.resetCalibration());
            renderProfile(activeProfile);
            content.announceForAccessibility(
                    getString(R.string.calibration_reset_announcement)
            );
        });
    }

    private void save(MirrorProfile profile) {
        activeProfile = profile;
        MirrorSettings.saveProfile(this, profile);
    }

    private void renderProfile(MirrorProfile profile) {
        bindingUi = true;
        profileInput.setValue(profileName(profile));
        scaleModeInput.setValue(scaleLabels[profile.scaleMode.ordinal()]);
        rotationInput.setValue(rotationLabels[profile.rotationDegrees / 90]);
        mirrorSwitch.setChecked(profile.mirrorHorizontally);
        brightnessSlider.setValue(profile.brightnessPercent);
        updateBrightnessValue(profile.brightnessPercent);
        autoBrightnessSwitch.setChecked(DashboardWidgetLayout.isAutoBrightnessEnabled(this));
        // A frame overrules all three, so the sliders say so rather than
        // moving something nothing on the panel is following.
        boolean framed = profile.crop != null;
        zoomSlider.setEnabled(!framed);
        horizontalOffsetSlider.setEnabled(!framed);
        verticalOffsetSlider.setEnabled(!framed);
        framedNotice.setVisibility(framed ? View.VISIBLE : View.GONE);
        zoomSlider.setValue(profile.zoomPercent);
        horizontalOffsetSlider.setValue(profile.horizontalOffsetPercent);
        verticalOffsetSlider.setValue(profile.verticalOffsetPercent);
        updateZoomValue(profile.zoomPercent);
        updateHorizontalOffsetValue(profile.horizontalOffsetPercent);
        updateVerticalOffsetValue(profile.verticalOffsetPercent);
        bindingUi = false;
    }

    /** The two rows on this page that are not tied to the selected profile. */
    private void renderGlobalSettings() {
        bindingUi = true;
        qualityModeInput.setValue(
                qualityLabels[MirrorSettings.loadProjectionQuality(this).ordinal()]);
        calibrationGridSwitch.setChecked(MirrorSettings.isCalibrationGridEnabled(this));
        bindingUi = false;
    }

    /**
     * The test pattern takes over the rear panel, so it cannot run while
     * mirroring already owns it.
     */
    private void updateMirrorState(boolean active) {
        calibrationPreviewButton.setEnabled(!active);
        if (calibrationPreviewButton instanceof TextView) {
            ((TextView) calibrationPreviewButton).setText(active
                    ? R.string.calibration_preview_active
                    : R.string.calibration_preview);
        }
    }

    private void startCalibrationPreview() {
        DisplayManager manager = getSystemService(DisplayManager.class);
        int rearDisplayId = DisplayActivity.findRearDisplayId(manager);
        if (rearDisplayId == Display.INVALID_DISPLAY) {
            content.announceForAccessibility(getString(R.string.rear_display_missing));
            return;
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(rearDisplayId);
        Intent intent = new Intent(this, CalibrationPreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent, options.toBundle());
    }

    private void refreshProfileOptions(String selectedId) {
        profiles = MirrorSettings.loadProfiles(this);
        profileLabels = new String[profiles.size()];
        int selectedIndex = 0;
        for (int index = 0; index < profiles.size(); index++) {
            MirrorProfile profile = profiles.get(index);
            profileLabels[index] = profileName(profile);
            if (profile.id.equals(selectedId)) {
                selectedIndex = index;
            }
        }
        profileInput.setEntries(profileLabels);
        profileInput.setValue(profileLabels[selectedIndex]);
    }

    private void updateBrightnessValue(int percent) {
        String text = getString(R.string.brightness_value, percent);
        brightnessValue.setText(text);
        brightnessSlider.setContentDescription(text);
    }

    private void updateZoomValue(int percent) {
        String text = getString(R.string.zoom_value, percent);
        zoomValue.setText(text);
        zoomSlider.setContentDescription(text);
    }

    private void updateHorizontalOffsetValue(int percent) {
        String text = getString(R.string.horizontal_offset_value, percent);
        horizontalOffsetValue.setText(text);
        horizontalOffsetSlider.setContentDescription(text);
    }

    private void updateVerticalOffsetValue(int percent) {
        String text = getString(R.string.vertical_offset_value, percent);
        verticalOffsetValue.setText(text);
        verticalOffsetSlider.setContentDescription(text);
    }

    private String profileName(MirrorProfile profile) {
        return MirrorSettings.profileDisplayName(this, profile);
    }
}
