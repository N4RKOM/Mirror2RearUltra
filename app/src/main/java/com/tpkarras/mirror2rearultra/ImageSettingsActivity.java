package com.tpkarras.mirror2rearultra;

import android.app.ActivityOptions;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
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

    private LinearLayout profileChips;
    private PanelPreviewView panelPreview;
    private TextView panelPreviewZoom;
    private TextView panelPreviewOffset;
    private HyperValueRow scaleModeInput;
    private HyperValueRow rotationInput;
    private MaterialSwitch mirrorSwitch;
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

    private PanelSettingsController panelSettings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_settings);
        SettingsLayout.bindCollapsingTitle(this, R.id.image_settings_toolbar,
                R.id.image_settings_scroll, R.id.image_settings_page_title);

        MaterialToolbar toolbar = findViewById(R.id.image_settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        profileChips = findViewById(R.id.profile_chips);
        panelPreview = findViewById(R.id.panel_preview);
        panelPreviewZoom = findViewById(R.id.panel_preview_zoom);
        panelPreviewOffset = findViewById(R.id.panel_preview_offset);
        panelPreview.fitToRearPanel();
        scaleModeInput = findViewById(R.id.scale_mode_input);
        rotationInput = findViewById(R.id.rotation_input);
        mirrorSwitch = findViewById(R.id.mirror_switch);
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

        // Shutter, grid and level share their bindings with the widgets tab.
        panelSettings = new PanelSettingsController(this, content, null);
        bindTabs();
        bindHelpToggle(R.id.help_image_button, R.id.help_image);
        bindHelpToggle(R.id.help_fine_button, R.id.help_fine);
        bindHelpToggle(R.id.help_all_button, R.id.help_all);

        scaleLabels = getResources().getStringArray(R.array.scale_mode_entries);
        rotationLabels = getResources().getStringArray(R.array.rotation_entries);
        qualityLabels = getResources().getStringArray(R.array.quality_mode_entries);
        scaleModeInput.setEntries(scaleLabels);
        rotationInput.setEntries(rotationLabels);
        qualityModeInput.setEntries(qualityLabels);

        SettingsLayout.constrainContentOnWideScreens(this, content);
        bindInteractions();
        MiuixRows.installChoiceRow(this, R.id.scale_mode_input, R.drawable.ic_row_scale);
        MiuixRows.installChoiceRow(this, R.id.rotation_input, R.drawable.ic_row_rotate);
        MiuixRows.installSwitchWithIcon(this, R.id.mirror_switch, R.drawable.ic_row_flip);
        MiuixRows.installChoiceRow(this, R.id.quality_mode_input, R.drawable.ic_row_quality);
        MiuixRows.installSwitchWithIcon(this, R.id.calibration_grid_switch, R.drawable.ic_row_grid);
        MiuixRows.installChoiceRow(this, R.id.dashboard_shutter_delay_input, R.drawable.ic_row_timer);
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
        panelSettings.onResume();
    }

    @Override
    protected void onPause() {
        panelSettings.onPause();
        super.onPause();
    }

    @Override
    public void onMirrorStateChanged(boolean active) {
        runOnUiThread(() -> updateMirrorState(active));
    }

    /** Image and Calibration share the page below the preview; one is shown at a time. */
    private void bindTabs() {
        MaterialButtonToggleGroup tabs = findViewById(R.id.image_settings_tabs);
        View imagePage = findViewById(R.id.image_settings_page_image);
        View calibrationPage = findViewById(R.id.image_settings_page_calibration);
        View capturePage = findViewById(R.id.image_settings_page_capture);
        tabs.check(R.id.image_settings_tab_image);
        tabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            imagePage.setVisibility(checkedId == R.id.image_settings_tab_image
                    ? View.VISIBLE : View.GONE);
            calibrationPage.setVisibility(checkedId == R.id.image_settings_tab_calibration
                    ? View.VISIBLE : View.GONE);
            capturePage.setVisibility(checkedId == R.id.image_settings_tab_capture
                    ? View.VISIBLE : View.GONE);
        });
    }

    /** A "?" next to a group header that shows or hides that group's explanation. */
    private void bindHelpToggle(int buttonId, int helpId) {
        View help = findViewById(helpId);
        findViewById(buttonId).setOnClickListener(view ->
                help.setVisibility(help.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE));
    }

    private void bindInteractions() {
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
        renderPreview(profile);
    }

    /** Shutter, grid and level are camera aids, so only the Camera profile lists them. */
    private void updateCaptureTab(MirrorProfile profile) {
        boolean camera = MirrorProfile.CAMERA_ID.equals(profile.id);
        MaterialButtonToggleGroup tabs = findViewById(R.id.image_settings_tabs);
        findViewById(R.id.image_settings_tab_capture).setVisibility(
                camera ? View.VISIBLE : View.GONE);
        if (!camera && tabs.getCheckedButtonId() == R.id.image_settings_tab_capture) {
            tabs.check(R.id.image_settings_tab_image);
        }
    }

    private void renderProfile(MirrorProfile profile) {
        bindingUi = true;
        updateCaptureTab(profile);
        scaleModeInput.setValue(scaleLabels[profile.scaleMode.ordinal()]);
        rotationInput.setValue(rotationLabels[profile.rotationDegrees / 90]);
        mirrorSwitch.setChecked(profile.mirrorHorizontally);
        // A frame overrules all three, so the sliders say so rather than
        // moving something nothing on the panel is following.
        boolean framed = profile.hasCrop();
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
        renderPreview(profile);
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
        ProfileChips.bind(this, profileChips, profiles, selectedId, this::selectProfile);
    }

    private void selectProfile(MirrorProfile profile) {
        if (bindingUi || activeProfile != null && activeProfile.id.equals(profile.id)) {
            return;
        }
        activeProfile = MirrorSettings.selectProfile(this, profile.id);
        refreshProfileOptions(activeProfile.id);
        renderProfile(activeProfile);
        content.announceForAccessibility(getString(
                R.string.profile_selected_announcement,
                profileName(activeProfile)
        ));
    }

    private void renderPreview(MirrorProfile profile) {
        panelPreview.setProfile(profile);
        panelPreviewZoom.setText(getString(R.string.panel_preview_zoom, profile.zoomPercent));
        panelPreviewOffset.setText(getString(R.string.panel_preview_offset,
                profile.horizontalOffsetPercent, profile.verticalOffsetPercent));
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
