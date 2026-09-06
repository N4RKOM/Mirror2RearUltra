package com.tpkarras.mirror2rearultra;

import android.content.Intent;
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
    private String[] burnInLabels;
    private MaterialSwitch customImageSwitch;
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
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::importImage);
        setContentView(R.layout.activity_dashboard_settings);

        MaterialToolbar toolbar = findViewById(R.id.dashboard_settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        modeInput = findViewById(R.id.dashboard_mode_input);
        layoutInput = findViewById(R.id.dashboard_layout_input);
        themeInput = findViewById(R.id.dashboard_theme_input);
        customImageSwitch = findViewById(R.id.dashboard_custom_image_switch);
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
        burnInLabels = new String[]{
                getString(R.string.dashboard_burn_in_off),
                getString(R.string.dashboard_burn_in_small),
                getString(R.string.dashboard_burn_in_normal),
                getString(R.string.dashboard_burn_in_large),
        };
        modeInput.setEntries(modeLabels);
        layoutInput.setEntries(layoutLabels);
        themeInput.setEntries(themeLabels);
        burnInInput.setEntries(burnInLabels);

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

    private void render(DashboardSettings settings) {
        bindingUi = true;
        modeInput.setValue(modeLabels[settings.contentMode.ordinal()]);
        layoutInput.setValue(layoutLabels[settings.layout.ordinal()]);
        themeInput.setValue(themeLabels[settings.theme.ordinal()]);
        int shiftDp = DashboardWidgetLayout.loadBurnInShiftDp(this);
        int burnInIndex = 2;
        for (int index = 0; index < BURN_IN_SHIFTS_DP.length; index++) {
            if (BURN_IN_SHIFTS_DP[index] == shiftDp) {
                burnInIndex = index;
                break;
            }
        }
        burnInInput.setValue(burnInLabels[burnInIndex]);
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
