package com.tpkarras.mirror2rearultra;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.IOException;
import android.widget.Toast;
import java.util.List;

/**
 * The rear panel as a whole: what it shows, how it looks, when its screen
 * dims and goes out, and what touch and the sensors do to it.
 *
 * <p>Everything about widgets is done in {@link DashboardBuilderActivity},
 * which opens {@link WidgetPickerActivity} from its own list, so this screen
 * leads only to the builder.
 *
 * <p>It used to carry a layout choice, a page-cycling switch and the
 * background image as well, all the builder's business - the layout here was
 * page one's layout under another name - and the units, which belong with
 * the widget data in the picker. They live there now, and nothing is set in
 * two places.
 */
public class DashboardSettingsActivity extends AppCompatActivity {

    /**
     * Drift amplitudes offered for the burn-in setting, in dp. Three was the
     * fixed value before it became a choice, so it stays the middle option.
     */
    private static final int[] BURN_IN_SHIFTS_DP = {0, 1, 3, 6};

    private HyperValueRow modeInput;
    private HyperValueRow themeInput;
    private HyperValueRow burnInInput;
    private HyperValueRow idleModeInput;
    private HyperValueRow fontInput;
    private View fontAddButton;
    private HyperValueRow fontRemoveInput;
    private ActivityResultLauncher<String> fontPickerLauncher;
    private String[] fontLabels;
    private HyperSlider aodMinBrightnessSlider;
    private TextView aodMinBrightnessValue;
    private String[] burnInLabels;
    private String[] idleModeLabels;
    private MaterialSwitch pocketLockSwitch;
    private MaterialSwitch shutterOnTapSwitch;
    private HyperValueRow shutterDelayInput;
    private MaterialSwitch mirrorGridSwitch;
    private MaterialSwitch mirrorLevelSwitch;
    private MaterialSwitch notificationLightSwitch;
    private String[] shutterDelayLabels;
    private MaterialSwitch tapVariantSwitch;
    private TextView shutterAccessStatus;
    private View shutterAccessButton;
    private MaterialSwitch faceDownSwitch;
    private HyperSlider textScaleSlider;
    private HyperSlider backgroundOpacitySlider;
    private TextView textScaleValue;
    private TextView backgroundOpacityValue;
    private TextView restartNotice;
    private ViewGroup content;

    private String[] modeLabels;
    private String[] themeLabels;
    /** Guards the listeners while the UI is being written from stored state. */
    private boolean bindingUi;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Any file: a font picked from a download folder is often served with
        // a generic type, and filtering by font/* hides it.
        fontPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::importFont);
        setContentView(R.layout.activity_dashboard_settings);

        MaterialToolbar toolbar = findViewById(R.id.dashboard_settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        modeInput = findViewById(R.id.dashboard_mode_input);
        themeInput = findViewById(R.id.dashboard_theme_input);
        pocketLockSwitch = findViewById(R.id.dashboard_pocket_lock_switch);
        shutterOnTapSwitch = findViewById(R.id.dashboard_shutter_switch);
        shutterDelayInput = findViewById(R.id.dashboard_shutter_delay_input);
        mirrorGridSwitch = findViewById(R.id.dashboard_mirror_grid_switch);
        mirrorLevelSwitch = findViewById(R.id.dashboard_mirror_level_switch);
        notificationLightSwitch = findViewById(R.id.dashboard_notification_light_switch);
        shutterDelayLabels = new String[ShutterCountdown.CHOICES.length];
        for (int index = 0; index < ShutterCountdown.CHOICES.length; index++) {
            int seconds = ShutterCountdown.CHOICES[index];
            shutterDelayLabels[index] = seconds == 0
                    ? getString(R.string.shutter_countdown_off)
                    : getString(R.string.shutter_countdown_seconds, seconds);
        }
        shutterDelayInput.setEntries(shutterDelayLabels);
        tapVariantSwitch = findViewById(R.id.dashboard_tap_variant_switch);
        shutterAccessStatus = findViewById(R.id.dashboard_shutter_access_status);
        shutterAccessButton = findViewById(R.id.dashboard_shutter_access_button);
        faceDownSwitch = findViewById(R.id.dashboard_face_down_switch);
        textScaleSlider = findViewById(R.id.dashboard_text_scale_slider);
        backgroundOpacitySlider = findViewById(R.id.dashboard_background_opacity_slider);
        textScaleValue = findViewById(R.id.dashboard_text_scale_value);
        backgroundOpacityValue = findViewById(R.id.dashboard_background_opacity_value);
        restartNotice = findViewById(R.id.dashboard_restart_notice);
        content = findViewById(R.id.dashboard_settings_content);

        modeLabels = getResources().getStringArray(R.array.dashboard_mode_entries);
        themeLabels = getResources().getStringArray(R.array.dashboard_theme_entries);
        burnInInput = findViewById(R.id.dashboard_burn_in_input);
        idleModeInput = findViewById(R.id.dashboard_idle_mode_input);
        fontInput = findViewById(R.id.dashboard_font_input);
        fontAddButton = findViewById(R.id.dashboard_font_add_button);
        fontRemoveInput = findViewById(R.id.dashboard_font_remove_input);
        fontAddButton.setOnClickListener(view -> fontPickerLauncher.launch("*/*"));
        aodMinBrightnessSlider = findViewById(R.id.dashboard_aod_min_brightness_slider);
        aodMinBrightnessValue = findViewById(R.id.dashboard_aod_min_brightness_value);
        burnInLabels = new String[]{
                getString(R.string.dashboard_burn_in_off),
                getString(R.string.dashboard_burn_in_small),
                getString(R.string.dashboard_burn_in_normal),
                getString(R.string.dashboard_burn_in_large),
        };
        idleModeLabels = new String[]{
                getString(R.string.dashboard_idle_15_seconds),
                getString(R.string.dashboard_idle_30_seconds),
                getString(R.string.dashboard_idle_always_on),
        };
        modeInput.setEntries(modeLabels);
        themeInput.setEntries(themeLabels);
        burnInInput.setEntries(burnInLabels);
        idleModeInput.setEntries(idleModeLabels);
        fontLabels = getResources().getStringArray(R.array.dashboard_font_entries);
        fontInput.setEntries(fontLabels);

        render(MirrorSettings.loadDashboardSettings(this));
        bindInteractions();
        SettingsLayout.constrainContentOnWideScreens(this, content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A backup import or the picker can change stored settings while this
        // screen is stopped, so the values are re-read rather than trusted.
        render(MirrorSettings.loadDashboardSettings(this));
    }

    @Override
    protected void onPause() {
        saveFromUi();
        super.onPause();
    }

    private void bindInteractions() {
        modeInput.setOnItemSelectedListener(position -> {
            saveFromUi();
            restartNotice.setVisibility(MirrorState.isActive() ? View.VISIBLE : View.GONE);
        });
        themeInput.setOnItemSelectedListener(position -> saveFromUi());
        burnInInput.setOnItemSelectedListener(position -> {
            if (position >= 0 && position < BURN_IN_SHIFTS_DP.length) {
                DashboardWidgetLayout.saveBurnInShiftDp(this, BURN_IN_SHIFTS_DP[position]);
                // Lives outside DashboardSettings, so re-saving is what tells
                // the running panel to pick the change up.
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        fontInput.setOnItemSelectedListener(position -> {
            if (position >= 0 && position < DashboardWidgetLayout.Font.values().length) {
                DashboardWidgetLayout.saveFont(this,
                        DashboardWidgetLayout.Font.values()[position]);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        idleModeInput.setOnItemSelectedListener(position -> {
            if (position >= 0 && position < DashboardWidgetLayout.IdleMode.values().length) {
                DashboardWidgetLayout.saveIdleMode(this,
                        DashboardWidgetLayout.IdleMode.values()[position]);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        pocketLockSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setPocketLockEnabled(this, checked);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        shutterAccessButton.setOnClickListener(button -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (ActivityNotFoundException missing) {
                // Nowhere to send them; the row above still says how it stands.
            }
        });
        tapVariantSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setTapCyclesVariantEnabled(this, checked);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        shutterOnTapSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setShutterOnTapEnabled(this, checked);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
                shutterDelayInput.setEnabled(checked);
            }
        });
        // Read when a notification arrives, so there is nothing to tell the
        // running panel.
        notificationLightSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setNotificationLightEnabled(this, checked);
            }
        });
        mirrorGridSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setMirrorGridEnabled(this, checked);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        mirrorLevelSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setMirrorLevelEnabled(this, checked);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        // Read at the moment of the tap, so there is nothing to tell the
        // running panel.
        shutterDelayInput.setOnItemSelectedListener(position -> {
            if (position >= 0 && position < ShutterCountdown.CHOICES.length) {
                DashboardWidgetLayout.setShutterDelaySeconds(this,
                        ShutterCountdown.CHOICES[position]);
            }
        });
        faceDownSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setFaceDownOffEnabled(this, checked);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        aodMinBrightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            int percent = Math.round(value);
            updateAodMinBrightnessValue(percent);
            if (fromUser) {
                DashboardWidgetLayout.saveAodMinBrightnessPercent(this, percent);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });

        textScaleSlider.addOnChangeListener((slider, value, fromUser) -> {
            updateTextScaleValue(Math.round(value));
            if (fromUser) {
                saveIfReady();
            }
        });
        backgroundOpacitySlider.addOnChangeListener((slider, value, fromUser) -> {
            updateBackgroundOpacityValue(Math.round(value));
            if (fromUser) {
                saveIfReady();
            }
        });
        findViewById(R.id.dashboard_builder_button).setOnClickListener(view ->
                startActivity(new Intent(this, DashboardBuilderActivity.class)));
    }

    /**
     * The label for an enum value, without trusting the array to match it.
     *
     * <p>These arrays are indexed by ordinal, so adding a value to one of the
     * enums and forgetting the string array took the whole screen down with an
     * index out of bounds. A missing label is a defect worth fixing, but not
     * one worth a crash on the app's main settings page.
     */
    private String labelAt(String[] labels, int index) {
        return index >= 0 && index < labels.length ? labels[index] : "";
    }

    private void render(DashboardSettings settings) {
        bindingUi = true;
        modeInput.setValue(labelAt(modeLabels, settings.contentMode.ordinal()));
        themeInput.setValue(labelAt(themeLabels, settings.theme.ordinal()));
        int shiftDp = DashboardWidgetLayout.loadBurnInShiftDp(this);
        int burnInIndex = 2;
        for (int index = 0; index < BURN_IN_SHIFTS_DP.length; index++) {
            if (BURN_IN_SHIFTS_DP[index] == shiftDp) {
                burnInIndex = index;
                break;
            }
        }
        burnInInput.setValue(burnInLabels[burnInIndex]);
        DashboardWidgetLayout.IdleMode idleMode = DashboardWidgetLayout.loadIdleMode(this);
        idleModeInput.setValue(labelAt(idleModeLabels, idleMode.ordinal()));
        fontInput.setValue(labelAt(fontLabels, DashboardWidgetLayout.loadFont(this).ordinal()));
        renderOwnFonts();
        pocketLockSwitch.setChecked(DashboardWidgetLayout.isPocketLockEnabled(this));
        shutterOnTapSwitch.setChecked(DashboardWidgetLayout.isShutterOnTapEnabled(this));
        int delay = DashboardWidgetLayout.shutterDelaySeconds(this);
        for (int index = 0; index < ShutterCountdown.CHOICES.length; index++) {
            if (ShutterCountdown.CHOICES[index] == delay) {
                shutterDelayInput.setValue(shutterDelayLabels[index]);
            }
        }
        shutterDelayInput.setEnabled(shutterOnTapSwitch.isChecked());
        mirrorGridSwitch.setChecked(DashboardWidgetLayout.isMirrorGridEnabled(this));
        mirrorLevelSwitch.setChecked(DashboardWidgetLayout.isMirrorLevelEnabled(this));
        notificationLightSwitch.setChecked(DashboardWidgetLayout.isNotificationLightEnabled(this));
        tapVariantSwitch.setChecked(DashboardWidgetLayout.isTapCyclesVariantEnabled(this));
        // Switched on in the system's own settings, so it is read afresh here
        // rather than remembered: this screen is where people come back to.
        boolean access = PanelShutterService.isEnabled(this);
        shutterAccessStatus.setText(access
                ? R.string.shutter_service_on : R.string.shutter_service_off);
        faceDownSwitch.setChecked(DashboardWidgetLayout.isFaceDownOffEnabled(this));
        int aodMinBrightness = DashboardWidgetLayout.loadAodMinBrightnessPercent(this);
        aodMinBrightnessSlider.setValue(aodMinBrightness);
        updateAodMinBrightnessValue(aodMinBrightness);
        textScaleSlider.setValue(settings.textScalePercent);
        backgroundOpacitySlider.setValue(settings.backgroundOpacityPercent);
        updateTextScaleValue(settings.textScalePercent);
        updateBackgroundOpacityValue(settings.backgroundOpacityPercent);
        restartNotice.setVisibility(View.GONE);
        bindingUi = false;
    }

    private void saveIfReady() {
        if (!bindingUi) {
            saveFromUi();
        }
    }

    private void saveFromUi() {
        if (bindingUi) {
            return;
        }
        RearContentMode mode = valueAt(
                RearContentMode.values(),
                modeInput.getSelectedIndex(),
                RearContentMode.MIRROR
        );
        DashboardSettings.Theme theme = valueAt(
                DashboardSettings.Theme.values(),
                themeInput.getSelectedIndex(),
                DashboardSettings.Theme.SYSTEM
        );
        // Widget flags and the two widget texts are carried over from storage,
        // not re-derived from this screen: the picker owns them, and rebuilding
        // them here would overwrite whatever it just saved. So are the layout,
        // which is page one's, and the background image, both set in the
        // builder.
        DashboardSettings current = MirrorSettings.loadDashboardSettings(this);
        MirrorSettings.saveDashboardSettings(this, new DashboardSettings(
                mode,
                current.layout,
                theme,
                current.showClock,
                current.showDate,
                current.showBattery,
                current.showTemperature,
                current.showWeather,
                current.showNextAlarm,
                current.showMedia,
                current.showCompass,
                current.showSpeed,
                current.showAltitude,
                current.showSessionTimer,
                current.showActiveProfile,
                current.showCustomText,
                current.weatherCity,
                current.customText,
                Math.round(textScaleSlider.getValue()),
                Math.round(backgroundOpacitySlider.getValue()),
                current.showCustomImage,
                current.customImageOpacityPercent
        ));
    }

    private void updateTextScaleValue(int percent) {
        textScaleValue.setText(getString(R.string.dashboard_text_scale_value, percent));
        textScaleSlider.setContentDescription(
                getString(R.string.dashboard_text_scale_value, percent)
        );
    }

    private void updateAodMinBrightnessValue(int percent) {
        String value = getString(R.string.dashboard_aod_min_brightness_value, percent);
        aodMinBrightnessValue.setText(value);
        aodMinBrightnessSlider.setContentDescription(value);
    }

    private void updateBackgroundOpacityValue(int percent) {
        backgroundOpacityValue.setText(
                getString(R.string.dashboard_background_opacity_value, percent)
        );
        backgroundOpacitySlider.setContentDescription(
                getString(R.string.dashboard_background_opacity_value, percent)
        );
    }

    /**
     * Copies a picked font in and says how it went.
     *
     * <p>Kept here beside the panel's own font choice rather than in the
     * builder: a font is added once and then chosen many times, and the
     * choosing happens per widget.
     */
    private void importFont(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            String name = PanelFontStore.importFromUri(this, uri);
            renderOwnFonts();
            Toast.makeText(this, getString(R.string.dashboard_font_added, name),
                    Toast.LENGTH_SHORT).show();
        } catch (IOException error) {
            Toast.makeText(this, R.string.dashboard_font_add_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** The removal row exists only while there is something to remove. */
    private void renderOwnFonts() {
        List<PanelFontStore.Entry> entries = PanelFontStore.list(this);
        if (entries.isEmpty()) {
            fontRemoveInput.setVisibility(View.GONE);
            return;
        }
        String[] labels = new String[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            labels[index] = entries.get(index).label;
        }
        fontRemoveInput.setVisibility(View.VISIBLE);
        fontRemoveInput.setEntries(labels);
        fontRemoveInput.setValue(getString(R.string.dashboard_font_remove_choose));
        fontRemoveInput.setOnItemSelectedListener(position -> {
            if (position < 0 || position >= entries.size()) {
                return;
            }
            PanelFontStore.Entry entry = entries.get(position);
            PanelFontStore.remove(this, entry.id);
            PanelFonts.forget(entry.id);
            renderOwnFonts();
            // Widgets still naming it fall back to the panel's face on their
            // own, so nothing else has to be rewritten.
            MirrorSettings.saveDashboardSettings(this,
                    MirrorSettings.loadDashboardSettings(this));
            Toast.makeText(this, getString(R.string.dashboard_font_removed, entry.label),
                    Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Reads {@code values[index]}, falling back when the index is out of range.
     * A value row reports -1 until something is selected, so every read of a
     * choice has to survive that.
     */
    private static <T> T valueAt(T[] values, int index, T fallback) {
        return index >= 0 && index < values.length ? values[index] : fallback;
    }
}
