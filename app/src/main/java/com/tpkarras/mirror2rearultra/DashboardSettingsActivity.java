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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.Toast;
import java.util.List;

/**
 * The rear panel itself: what it shows, the background image, and appearance.
 *
 * <p>Choosing widgets is {@link WidgetPickerActivity} and arranging them is
 * {@link DashboardBuilderActivity}; this screen only leads to them, in that
 * order, because that is the order the two jobs are done in. The long widget
 * lists that used to live here moved to the picker, which is now the only
 * place a widget is switched on or off.
 */
public class DashboardSettingsActivity extends AppCompatActivity {

    /**
     * Drift amplitudes offered for the burn-in setting, in dp. Three was the
     * fixed value before it became a choice, so it stays the middle option.
     */
    private static final int[] BURN_IN_SHIFTS_DP = {0, 1, 3, 6};

    private HyperValueRow modeInput;
    private HyperValueRow layoutInput;
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
    private MaterialSwitch customImageSwitch;
    private MaterialSwitch autoPagesSwitch;
    private MaterialSwitch imperialUnitsSwitch;
    private MaterialSwitch pocketLockSwitch;
    private MaterialSwitch shutterOnTapSwitch;
    private TextView shutterAccessStatus;
    private View shutterAccessButton;
    private MaterialSwitch faceDownSwitch;
    private TextView customImageStatus;
    private View customImageChooseButton;
    private View customImageRemoveButton;
    private HyperSlider customImageOpacitySlider;
    private TextView customImageOpacityValue;
    private HyperSlider textScaleSlider;
    private HyperSlider backgroundOpacitySlider;
    private TextView textScaleValue;
    private TextView backgroundOpacityValue;
    private TextView restartNotice;
    private ViewGroup content;

    private String[] modeLabels;
    private String[] layoutLabels;
    private String[] themeLabels;
    /** Guards the listeners while the UI is being written from stored state. */
    private boolean bindingUi;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Any file: a font picked from a download folder is often served with
        // a generic type, and filtering by font/* hides it.
        fontPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::importFont);
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::importImage);
        setContentView(R.layout.activity_dashboard_settings);

        MaterialToolbar toolbar = findViewById(R.id.dashboard_settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        modeInput = findViewById(R.id.dashboard_mode_input);
        layoutInput = findViewById(R.id.dashboard_layout_input);
        themeInput = findViewById(R.id.dashboard_theme_input);
        customImageSwitch = findViewById(R.id.dashboard_custom_image_switch);
        autoPagesSwitch = findViewById(R.id.dashboard_auto_pages_switch);
        imperialUnitsSwitch = findViewById(R.id.dashboard_imperial_units_switch);
        pocketLockSwitch = findViewById(R.id.dashboard_pocket_lock_switch);
        shutterOnTapSwitch = findViewById(R.id.dashboard_shutter_switch);
        shutterAccessStatus = findViewById(R.id.dashboard_shutter_access_status);
        shutterAccessButton = findViewById(R.id.dashboard_shutter_access_button);
        faceDownSwitch = findViewById(R.id.dashboard_face_down_switch);
        customImageStatus = findViewById(R.id.dashboard_custom_image_status);
        customImageChooseButton = findViewById(R.id.dashboard_custom_image_choose_button);
        customImageRemoveButton = findViewById(R.id.dashboard_custom_image_remove_button);
        customImageOpacitySlider = findViewById(R.id.dashboard_custom_image_opacity_slider);
        customImageOpacityValue = findViewById(R.id.dashboard_custom_image_opacity_value);
        textScaleSlider = findViewById(R.id.dashboard_text_scale_slider);
        backgroundOpacitySlider = findViewById(R.id.dashboard_background_opacity_slider);
        textScaleValue = findViewById(R.id.dashboard_text_scale_value);
        backgroundOpacityValue = findViewById(R.id.dashboard_background_opacity_value);
        restartNotice = findViewById(R.id.dashboard_restart_notice);
        content = findViewById(R.id.dashboard_settings_content);

        modeLabels = getResources().getStringArray(R.array.dashboard_mode_entries);
        layoutLabels = getResources().getStringArray(R.array.dashboard_layout_entries);
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
        layoutInput.setEntries(layoutLabels);
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

    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindInteractions() {
        modeInput.setOnItemSelectedListener(position -> {
            saveFromUi();
            restartNotice.setVisibility(MirrorState.isActive() ? View.VISIBLE : View.GONE);
        });
        layoutInput.setOnItemSelectedListener(position -> saveFromUi());
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
        shutterOnTapSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setShutterOnTapEnabled(this, checked);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        faceDownSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setFaceDownOffEnabled(this, checked);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        imperialUnitsSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setImperialUnits(this, checked);
                MirrorSettings.saveDashboardSettings(this,
                        MirrorSettings.loadDashboardSettings(this));
            }
        });
        autoPagesSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                DashboardWidgetLayout.setAutoPageSwitchEnabled(this, checked);
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

        customImageSwitch.setOnCheckedChangeListener((button, checked) -> saveIfReady());
        customImageChooseButton.setOnClickListener(view -> imagePickerLauncher.launch("image/*"));
        customImageRemoveButton.setOnClickListener(view -> {
            DashboardImageStore.remove(this);
            customImageSwitch.setChecked(false);
            updateImageUi();
            saveFromUi();
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
        customImageOpacitySlider.addOnChangeListener((slider, value, fromUser) -> {
            updateCustomImageOpacityValue(Math.round(value));
            if (fromUser) {
                saveIfReady();
            }
        });

        findViewById(R.id.dashboard_widgets_button).setOnClickListener(view ->
                startActivity(new Intent(this, WidgetPickerActivity.class)));
        findViewById(R.id.dashboard_builder_button).setOnClickListener(view ->
                startActivity(new Intent(this, DashboardBuilderActivity.class)));
        findViewById(R.id.dashboard_done_button).setOnClickListener(view -> {
            saveFromUi();
            finish();
        });
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
        layoutInput.setValue(labelAt(layoutLabels, settings.layout.ordinal()));
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
        autoPagesSwitch.setChecked(DashboardWidgetLayout.isAutoPageSwitchEnabled(this));
        imperialUnitsSwitch.setChecked(DashboardWidgetLayout.isImperialUnits(this));
        pocketLockSwitch.setChecked(DashboardWidgetLayout.isPocketLockEnabled(this));
        shutterOnTapSwitch.setChecked(DashboardWidgetLayout.isShutterOnTapEnabled(this));
        // Switched on in the system's own settings, so it is read afresh here
        // rather than remembered: this screen is where people come back to.
        boolean access = PanelShutterService.isEnabled(this);
        shutterAccessStatus.setText(access
                ? R.string.shutter_service_on : R.string.shutter_service_off);
        faceDownSwitch.setChecked(DashboardWidgetLayout.isFaceDownOffEnabled(this));
        int aodMinBrightness = DashboardWidgetLayout.loadAodMinBrightnessPercent(this);
        aodMinBrightnessSlider.setValue(aodMinBrightness);
        updateAodMinBrightnessValue(aodMinBrightness);
        customImageSwitch.setChecked(settings.showCustomImage && DashboardImageStore.exists(this));
        textScaleSlider.setValue(settings.textScalePercent);
        backgroundOpacitySlider.setValue(settings.backgroundOpacityPercent);
        customImageOpacitySlider.setValue(settings.customImageOpacityPercent);
        updateTextScaleValue(settings.textScalePercent);
        updateBackgroundOpacityValue(settings.backgroundOpacityPercent);
        updateCustomImageOpacityValue(settings.customImageOpacityPercent);
        updateImageUi();
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
        DashboardSettings.Layout layout = valueAt(
                DashboardSettings.Layout.values(),
                layoutInput.getSelectedIndex(),
                DashboardSettings.Layout.STACKED
        );
        DashboardSettings.Theme theme = valueAt(
                DashboardSettings.Theme.values(),
                themeInput.getSelectedIndex(),
                DashboardSettings.Theme.SYSTEM
        );
        // Widget flags and the two widget texts are carried over from storage,
        // not re-derived from this screen: the picker owns them, and rebuilding
        // them here would overwrite whatever it just saved.
        DashboardSettings current = MirrorSettings.loadDashboardSettings(this);
        MirrorSettings.saveDashboardSettings(this, new DashboardSettings(
                mode,
                layout,
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
                customImageSwitch.isChecked() && DashboardImageStore.exists(this),
                Math.round(customImageOpacitySlider.getValue())
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

    private void updateCustomImageOpacityValue(int percent) {
        customImageOpacityValue.setText(
                getString(R.string.dashboard_custom_image_opacity_value, percent));
        customImageOpacitySlider.setContentDescription(
                getString(R.string.dashboard_custom_image_opacity_value, percent)
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

    private void updateImageUi() {
        boolean exists = DashboardImageStore.exists(this);
        customImageStatus.setText(exists ? R.string.dashboard_custom_image_selected
                : R.string.dashboard_custom_image_not_selected);
        customImageRemoveButton.setVisibility(exists ? View.VISIBLE : View.GONE);
        customImageSwitch.setEnabled(exists);
    }

    private void importImage(Uri uri) {
        if (uri == null) {
            return;
        }
        customImageStatus.setText(R.string.dashboard_custom_image_loading);
        imageExecutor.execute(() -> {
            try {
                DashboardImageStore.importFromUri(getApplicationContext(), uri);
                runOnUiThread(() -> {
                    customImageSwitch.setEnabled(true);
                    customImageSwitch.setChecked(true);
                    updateImageUi();
                    saveFromUi();
                });
            } catch (IOException error) {
                runOnUiThread(() ->
                        customImageStatus.setText(R.string.dashboard_custom_image_error));
            }
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
