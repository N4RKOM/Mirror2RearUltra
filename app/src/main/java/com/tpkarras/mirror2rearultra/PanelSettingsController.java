package com.tpkarras.mirror2rearultra;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Reads and writes the rear panel's own settings: what it shows, how it looks,
 * when its screen dims and goes out, what touch and the sensors do to it, and
 * the camera helpers of the mirror.
 *
 * <p>The controls live in the {@code panel_section_*} layouts and in
 * {@code activity_settings}. A host puts whichever of them it shows in its own
 * layout and hands this the root: the widgets tab folds the looks, the screen
 * and the touch sections open under their rows, and the settings tab holds the
 * content mode. A section the host does not have is simply not bound.
 *
 * <p>Everything about widgets is done in {@link DashboardBuilderActivity},
 * which opens {@link WidgetPickerActivity} from its own list.
 */
final class PanelSettingsController {

    /**
     * Drift amplitudes offered for the burn-in setting, in dp. Three was the
     * fixed value before it became a choice, so it stays the middle option.
     */
    private static final int[] BURN_IN_SHIFTS_DP = {0, 1, 3, 6};

    private final AppCompatActivity host;
    private final View root;
    /** Called after a change the host may want to show, such as its summaries. */
    @Nullable private final Runnable onChanged;

    private HyperValueRow modeInput;
    private HyperValueRow themeInput;
    private HyperValueRow burnInInput;
    private HyperValueRow idleModeInput;
    private HyperValueRow pageTransitionInput;
    private String[] pageTransitionLabels;
    private HyperValueRow fontInput;
    private View fontAddButton;
    private HyperValueRow fontRemoveInput;
    private final ActivityResultLauncher<String> fontPickerLauncher;
    private String[] fontLabels;
    private HyperSlider aodMinBrightnessSlider;
    private TextView aodMinBrightnessValue;
    private String[] burnInLabels;
    private String[] idleModeLabels;
    private HyperValueRow quietModeInput;
    private HyperValueRow quietSunInput;
    private HyperValueRow quietFromInput;
    private HyperValueRow quietToInput;
    private String[] quietModeLabels;
    private final ActivityResultLauncher<String> locationPermissionLauncher;
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

    private String[] modeLabels;
    private String[] themeLabels;
    /** Guards the listeners while the UI is being written from stored state. */
    private boolean bindingUi;

    /** Must be built while the host is being created: it registers result launchers. */
    PanelSettingsController(AppCompatActivity host, View root, @Nullable Runnable onChanged) {
        this.host = host;
        this.root = root;
        this.onChanged = onChanged;
        // Any file: a font picked from a download folder is often served with
        // a generic type, and filtering by font/* hides it.
        fontPickerLauncher = host.registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::importFont);
        locationPermissionLauncher = host.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        QuietHours.refreshPlaceFromDevice(host);
                        tellPanel();
                    }
                    renderQuietHours();
                });

        modeInput = root.findViewById(R.id.dashboard_mode_input);
        themeInput = root.findViewById(R.id.dashboard_theme_input);
        pocketLockSwitch = root.findViewById(R.id.dashboard_pocket_lock_switch);
        shutterOnTapSwitch = root.findViewById(R.id.dashboard_shutter_switch);
        shutterDelayInput = root.findViewById(R.id.dashboard_shutter_delay_input);
        mirrorGridSwitch = root.findViewById(R.id.dashboard_mirror_grid_switch);
        mirrorLevelSwitch = root.findViewById(R.id.dashboard_mirror_level_switch);
        notificationLightSwitch = root.findViewById(R.id.dashboard_notification_light_switch);
        tapVariantSwitch = root.findViewById(R.id.dashboard_tap_variant_switch);
        shutterAccessStatus = root.findViewById(R.id.dashboard_shutter_access_status);
        shutterAccessButton = root.findViewById(R.id.dashboard_shutter_access_button);
        faceDownSwitch = root.findViewById(R.id.dashboard_face_down_switch);
        textScaleSlider = root.findViewById(R.id.dashboard_text_scale_slider);
        backgroundOpacitySlider = root.findViewById(R.id.dashboard_background_opacity_slider);
        textScaleValue = root.findViewById(R.id.dashboard_text_scale_value);
        backgroundOpacityValue = root.findViewById(R.id.dashboard_background_opacity_value);
        restartNotice = root.findViewById(R.id.dashboard_restart_notice);
        burnInInput = root.findViewById(R.id.dashboard_burn_in_input);
        idleModeInput = root.findViewById(R.id.dashboard_idle_mode_input);
        pageTransitionInput = root.findViewById(R.id.dashboard_page_transition_input);
        fontInput = root.findViewById(R.id.dashboard_font_input);
        fontAddButton = root.findViewById(R.id.dashboard_font_add_button);
        fontRemoveInput = root.findViewById(R.id.dashboard_font_remove_input);
        aodMinBrightnessSlider = root.findViewById(R.id.dashboard_aod_min_brightness_slider);
        aodMinBrightnessValue = root.findViewById(R.id.dashboard_aod_min_brightness_value);
        quietModeInput = root.findViewById(R.id.dashboard_quiet_mode_input);
        quietSunInput = root.findViewById(R.id.dashboard_quiet_sun_input);
        quietFromInput = root.findViewById(R.id.dashboard_quiet_from_input);
        quietToInput = root.findViewById(R.id.dashboard_quiet_to_input);

        if (modeInput != null) {
            modeLabels = host.getResources().getStringArray(R.array.dashboard_mode_entries);
            modeInput.setEntries(modeLabels);
        }
        if (themeInput != null) {
            themeLabels = host.getResources().getStringArray(R.array.dashboard_theme_entries);
            themeInput.setEntries(themeLabels);
            fontAddButton.setOnClickListener(view -> fontPickerLauncher.launch("*/*"));
            fontLabels = host.getResources().getStringArray(R.array.dashboard_font_entries);
            fontInput.setEntries(fontLabels);
        }
        if (idleModeInput != null) {
            burnInLabels = new String[]{
                    host.getString(R.string.dashboard_burn_in_off),
                    host.getString(R.string.dashboard_burn_in_small),
                    host.getString(R.string.dashboard_burn_in_normal),
                    host.getString(R.string.dashboard_burn_in_large),
            };
            idleModeLabels = new String[]{
                    host.getString(R.string.dashboard_idle_15_seconds),
                    host.getString(R.string.dashboard_idle_30_seconds),
                    host.getString(R.string.dashboard_idle_always_on),
            };
            burnInInput.setEntries(burnInLabels);
            idleModeInput.setEntries(idleModeLabels);
            // Says when night falls; there is nothing on it to choose.
            quietSunInput.setReadOnly(true);
            quietModeLabels = new String[]{
                    host.getString(R.string.dashboard_quiet_never),
                    host.getString(R.string.dashboard_quiet_night),
                    host.getString(R.string.dashboard_quiet_schedule),
            };
            quietModeInput.setEntries(quietModeLabels);
        }
        if (pageTransitionInput != null) {
            pageTransitionLabels = new String[]{
                    host.getString(R.string.dashboard_page_transition_off),
                    host.getString(R.string.dashboard_page_transition_slide),
                    host.getString(R.string.dashboard_page_transition_parallax),
                    host.getString(R.string.dashboard_page_transition_fade),
                    host.getString(R.string.dashboard_page_transition_vertical),
                    host.getString(R.string.dashboard_page_transition_zoom),
            };
            pageTransitionInput.setEntries(pageTransitionLabels);
        }
        if (shutterDelayInput != null) {
            shutterDelayLabels = new String[ShutterCountdown.CHOICES.length];
            for (int index = 0; index < ShutterCountdown.CHOICES.length; index++) {
                int seconds = ShutterCountdown.CHOICES[index];
                shutterDelayLabels[index] = seconds == 0
                        ? host.getString(R.string.shutter_countdown_off)
                        : host.getString(R.string.shutter_countdown_seconds, seconds);
            }
            shutterDelayInput.setEntries(shutterDelayLabels);
        }

        render(MirrorSettings.loadDashboardSettings(host));
        bindInteractions();
        MiuixRows.installSwitches(host,
                R.id.dashboard_notification_light_switch,
                R.id.dashboard_tap_variant_switch,
                R.id.dashboard_pocket_lock_switch,
                R.id.dashboard_face_down_switch);
        MiuixRows.installSwitchWithIcon(host, R.id.dashboard_mirror_grid_switch, R.drawable.ic_row_grid);
        MiuixRows.installSwitchWithIcon(host, R.id.dashboard_mirror_level_switch, R.drawable.ic_row_level);
        MiuixRows.installExpandableSwitch(host, R.id.dashboard_shutter_switch, R.drawable.ic_row_camera,
                R.id.dashboard_shutter_access_status, R.id.dashboard_shutter_access_button,
                R.id.dashboard_shutter_delay_input);
    }

    void onResume() {
        // A backup import or the picker can change stored settings while the
        // screen is stopped, so the values are re-read rather than trusted.
        render(MirrorSettings.loadDashboardSettings(host));
    }

    void onPause() {
        saveFromUi();
    }

    private void changed() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private void bindInteractions() {
        if (modeInput != null) {
            modeInput.setOnItemSelectedListener(position -> {
                saveFromUi();
                restartNotice.setVisibility(MirrorState.isActive() ? View.VISIBLE : View.GONE);
            });
        }
        if (themeInput != null) {
            themeInput.setOnItemSelectedListener(position -> saveFromUi());
            fontInput.setOnItemSelectedListener(position -> {
                if (position >= 0 && position < DashboardWidgetLayout.Font.values().length) {
                    DashboardWidgetLayout.saveFont(host,
                            DashboardWidgetLayout.Font.values()[position]);
                    tellPanel();
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
        }
        if (idleModeInput != null) {
            burnInInput.setOnItemSelectedListener(position -> {
                if (position >= 0 && position < BURN_IN_SHIFTS_DP.length) {
                    DashboardWidgetLayout.saveBurnInShiftDp(host, BURN_IN_SHIFTS_DP[position]);
                    // Lives outside DashboardSettings, so re-saving is what tells
                    // the running panel to pick the change up.
                    tellPanel();
                }
            });
            idleModeInput.setOnItemSelectedListener(position -> {
                if (position >= 0 && position < DashboardWidgetLayout.IdleMode.values().length) {
                    DashboardWidgetLayout.saveIdleMode(host,
                            DashboardWidgetLayout.IdleMode.values()[position]);
                    tellPanel();
                    renderQuietHours();
                }
            });
            quietModeInput.setOnItemSelectedListener(position -> {
                if (position < 0 || position >= QuietHours.Mode.values().length) {
                    return;
                }
                QuietHours.Mode mode = QuietHours.Mode.values()[position];
                DashboardWidgetLayout.saveQuietMode(host, mode);
                if (mode == QuietHours.Mode.NIGHT) {
                    findPlace();
                }
                renderQuietHours();
                tellPanel();
            });
            quietFromInput.setOnClickListener(view -> pickQuietTime(true));
            quietToInput.setOnClickListener(view -> pickQuietTime(false));
            // Read when a notification arrives, so there is nothing to tell the
            // running panel.
            notificationLightSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!bindingUi) {
                    DashboardWidgetLayout.setNotificationLightEnabled(host, checked);
                    changed();
                }
            });
            aodMinBrightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
                int percent = Math.round(value);
                updateAodMinBrightnessValue(percent);
                if (fromUser) {
                    DashboardWidgetLayout.saveAodMinBrightnessPercent(host, percent);
                    tellPanel();
                }
            });
        }
        if (pageTransitionInput != null) {
            pageTransitionInput.setOnItemSelectedListener(position -> {
                if (position >= 0 && position < DashboardWidgetLayout.PageTransition.values().length) {
                    DashboardWidgetLayout.savePageTransition(host,
                            DashboardWidgetLayout.PageTransition.values()[position]);
                    tellPanel();
                }
            });
        }
        if (tapVariantSwitch != null) {
            pocketLockSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!bindingUi) {
                    DashboardWidgetLayout.setPocketLockEnabled(host, checked);
                    tellPanel();
                }
            });
            tapVariantSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!bindingUi) {
                    DashboardWidgetLayout.setTapCyclesVariantEnabled(host, checked);
                    tellPanel();
                }
            });
            faceDownSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!bindingUi) {
                    DashboardWidgetLayout.setFaceDownOffEnabled(host, checked);
                    tellPanel();
                }
            });
        }
        if (shutterOnTapSwitch != null) {
            shutterAccessButton.setOnClickListener(button -> {
                try {
                    host.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (ActivityNotFoundException missing) {
                    // Nowhere to send them; the row above still says how it stands.
                }
            });
            shutterOnTapSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!bindingUi) {
                    DashboardWidgetLayout.setShutterOnTapEnabled(host, checked);
                    tellPanel();
                    shutterDelayInput.setEnabled(checked);
                }
            });
            mirrorGridSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!bindingUi) {
                    DashboardWidgetLayout.setMirrorGridEnabled(host, checked);
                    tellPanel();
                }
            });
            mirrorLevelSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!bindingUi) {
                    DashboardWidgetLayout.setMirrorLevelEnabled(host, checked);
                    tellPanel();
                }
            });
            // Read at the moment of the tap, so there is nothing to tell the
            // running panel.
            shutterDelayInput.setOnItemSelectedListener(position -> {
                if (position >= 0 && position < ShutterCountdown.CHOICES.length) {
                    DashboardWidgetLayout.setShutterDelaySeconds(host,
                            ShutterCountdown.CHOICES[position]);
                }
            });
        }
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
        if (modeInput != null) {
            modeInput.setValue(labelAt(modeLabels, settings.contentMode.ordinal()));
            restartNotice.setVisibility(View.GONE);
        }
        if (themeInput != null) {
            themeInput.setValue(labelAt(themeLabels, settings.theme.ordinal()));
            fontInput.setValue(labelAt(fontLabels,
                    DashboardWidgetLayout.loadFont(host).ordinal()));
            renderOwnFonts();
            textScaleSlider.setValue(settings.textScalePercent);
            backgroundOpacitySlider.setValue(settings.backgroundOpacityPercent);
            updateTextScaleValue(settings.textScalePercent);
            updateBackgroundOpacityValue(settings.backgroundOpacityPercent);
        }
        if (idleModeInput != null) {
            int shiftDp = DashboardWidgetLayout.loadBurnInShiftDp(host);
            int burnInIndex = 2;
            for (int index = 0; index < BURN_IN_SHIFTS_DP.length; index++) {
                if (BURN_IN_SHIFTS_DP[index] == shiftDp) {
                    burnInIndex = index;
                    break;
                }
            }
            burnInInput.setValue(burnInLabels[burnInIndex]);
            DashboardWidgetLayout.IdleMode idleMode = DashboardWidgetLayout.loadIdleMode(host);
            idleModeInput.setValue(labelAt(idleModeLabels, idleMode.ordinal()));
            renderQuietHours();
            notificationLightSwitch.setChecked(
                    DashboardWidgetLayout.isNotificationLightEnabled(host));
            int aodMinBrightness = DashboardWidgetLayout.loadAodMinBrightnessPercent(host);
            aodMinBrightnessSlider.setValue(aodMinBrightness);
            updateAodMinBrightnessValue(aodMinBrightness);
        }
        if (pageTransitionInput != null) {
            pageTransitionInput.setValue(labelAt(pageTransitionLabels,
                    DashboardWidgetLayout.loadPageTransition(host).ordinal()));
        }
        if (tapVariantSwitch != null) {
            pocketLockSwitch.setChecked(DashboardWidgetLayout.isPocketLockEnabled(host));
            tapVariantSwitch.setChecked(DashboardWidgetLayout.isTapCyclesVariantEnabled(host));
            faceDownSwitch.setChecked(DashboardWidgetLayout.isFaceDownOffEnabled(host));
        }
        if (shutterOnTapSwitch != null) {
            shutterOnTapSwitch.setChecked(DashboardWidgetLayout.isShutterOnTapEnabled(host));
            int delay = DashboardWidgetLayout.shutterDelaySeconds(host);
            for (int index = 0; index < ShutterCountdown.CHOICES.length; index++) {
                if (ShutterCountdown.CHOICES[index] == delay) {
                    shutterDelayInput.setValue(shutterDelayLabels[index]);
                }
            }
            shutterDelayInput.setEnabled(shutterOnTapSwitch.isChecked());
            mirrorGridSwitch.setChecked(DashboardWidgetLayout.isMirrorGridEnabled(host));
            mirrorLevelSwitch.setChecked(DashboardWidgetLayout.isMirrorLevelEnabled(host));
            // Switched on in the system's own settings, so it is read afresh
            // here rather than remembered: this is where people come back to.
            boolean access = PanelShutterService.isEnabled(host);
            shutterAccessStatus.setText(access
                    ? R.string.shutter_service_on : R.string.shutter_service_off);
        }
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
        // Widget flags and the two widget texts are carried over from storage,
        // not re-derived from here: the picker owns them, and rebuilding them
        // would overwrite whatever it just saved. So are the layout, which is
        // page one's, and the background image, both set in the builder. So is
        // whatever part of this the host does not show.
        DashboardSettings current = MirrorSettings.loadDashboardSettings(host);
        RearContentMode mode = modeInput == null ? current.contentMode : valueAt(
                RearContentMode.values(),
                modeInput.getSelectedIndex(),
                RearContentMode.MIRROR
        );
        DashboardSettings.Theme theme = themeInput == null ? current.theme : valueAt(
                DashboardSettings.Theme.values(),
                themeInput.getSelectedIndex(),
                DashboardSettings.Theme.SYSTEM
        );
        int textScale = textScaleSlider == null ? current.textScalePercent
                : Math.round(textScaleSlider.getValue());
        int backgroundOpacity = backgroundOpacitySlider == null ? current.backgroundOpacityPercent
                : Math.round(backgroundOpacitySlider.getValue());
        MirrorSettings.saveDashboardSettings(host, new DashboardSettings(
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
                textScale,
                backgroundOpacity,
                current.showCustomImage,
                current.customImageOpacityPercent
        ));
        changed();
    }

    private void updateTextScaleValue(int percent) {
        textScaleValue.setText(host.getString(R.string.dashboard_text_scale_value, percent));
        textScaleSlider.setContentDescription(
                host.getString(R.string.dashboard_text_scale_value, percent)
        );
    }

    /**
     * The AOD rows - quiet hours, AOD brightness and the burn-in drift -
     * only while the timer is AOD, and of the quiet hours only the rows for
     * the way chosen. The drift keeps its stored value while hidden.
     */
    private void renderQuietHours() {
        if (quietModeInput == null) {
            return;
        }
        QuietHours.Mode mode = DashboardWidgetLayout.loadQuietMode(host);
        quietModeInput.setValue(labelAt(quietModeLabels, mode.ordinal()));
        boolean aod = DashboardWidgetLayout.loadIdleMode(host)
                == DashboardWidgetLayout.IdleMode.ALWAYS_ON;
        boolean night = aod && mode == QuietHours.Mode.NIGHT;
        boolean scheduled = aod && mode == QuietHours.Mode.SCHEDULE;
        quietModeInput.setVisibility(aod ? View.VISIBLE : View.GONE);
        aodMinBrightnessValue.setVisibility(aod ? View.VISIBLE : View.GONE);
        aodMinBrightnessSlider.setVisibility(aod ? View.VISIBLE : View.GONE);
        burnInInput.setVisibility(aod ? View.VISIBLE : View.GONE);
        quietSunInput.setVisibility(night ? View.VISIBLE : View.GONE);
        quietFromInput.setVisibility(scheduled ? View.VISIBLE : View.GONE);
        quietToInput.setVisibility(scheduled ? View.VISIBLE : View.GONE);
        quietFromInput.setValue(clockLabel(DashboardWidgetLayout.quietFromMinutes(host)));
        quietToInput.setValue(clockLabel(DashboardWidgetLayout.quietToMinutes(host)));
        quietSunInput.setValue(sunLabel());
    }

    /** Today's sunset and sunrise where the phone is, or what is missing. */
    private String sunLabel() {
        double[] place = QuietHours.place(host);
        if (place == null) {
            return host.getString(R.string.dashboard_quiet_sun_unknown);
        }
        QuietHours.Sun sun = QuietHours.today(place, System.currentTimeMillis(),
                TimeZone.getDefault());
        if (sun.alwaysDown) {
            return host.getString(R.string.dashboard_quiet_polar_night);
        }
        if (sun.alwaysUp) {
            return host.getString(R.string.dashboard_quiet_polar_day);
        }
        java.text.DateFormat format = android.text.format.DateFormat.getTimeFormat(host);
        return host.getString(R.string.dashboard_quiet_sun_value,
                format.format(sun.setMillis), format.format(sun.riseMillis));
    }

    /**
     * Where the sun is worked out for: the phone's last known position, with
     * the permission for it asked each time sunset is chosen. The weather
     * city stands in until then; it is saved whenever the forecast is fetched.
     */
    private void findPlace() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(host,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (QuietHours.refreshPlaceFromDevice(host)) {
                tellPanel();
            }
            return;
        }
        locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private void pickQuietTime(boolean start) {
        int current = start ? DashboardWidgetLayout.quietFromMinutes(host)
                : DashboardWidgetLayout.quietToMinutes(host);
        new android.app.TimePickerDialog(host, (view, hour, minute) -> {
            int picked = hour * 60 + minute;
            DashboardWidgetLayout.setQuietWindow(host,
                    start ? picked : DashboardWidgetLayout.quietFromMinutes(host),
                    start ? DashboardWidgetLayout.quietToMinutes(host) : picked);
            renderQuietHours();
            tellPanel();
        }, current / 60, current % 60,
                android.text.format.DateFormat.is24HourFormat(host)).show();
    }

    /** A time of day written the way the phone writes it. */
    private String clockLabel(int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, minutes / 60);
        calendar.set(Calendar.MINUTE, minutes % 60);
        return android.text.format.DateFormat.getTimeFormat(host).format(calendar.getTime());
    }

    /**
     * Settings kept outside DashboardSettings (the quiet hours, the drift, the
     * flags) reach a running panel only through a save, so re-saving is what
     * tells it to look again. The host hears of it too.
     */
    private void tellPanel() {
        MirrorSettings.saveDashboardSettings(host, MirrorSettings.loadDashboardSettings(host));
        changed();
    }

    private void updateAodMinBrightnessValue(int percent) {
        String value = host.getString(R.string.dashboard_aod_min_brightness_value, percent);
        aodMinBrightnessValue.setText(value);
        aodMinBrightnessSlider.setContentDescription(value);
    }

    private void updateBackgroundOpacityValue(int percent) {
        backgroundOpacityValue.setText(
                host.getString(R.string.dashboard_background_opacity_value, percent)
        );
        backgroundOpacitySlider.setContentDescription(
                host.getString(R.string.dashboard_background_opacity_value, percent)
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
            String name = PanelFontStore.importFromUri(host, uri);
            renderOwnFonts();
            Toast.makeText(host, host.getString(R.string.dashboard_font_added, name),
                    Toast.LENGTH_SHORT).show();
        } catch (IOException error) {
            Toast.makeText(host, R.string.dashboard_font_add_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** The removal row exists only while there is something to remove. */
    private void renderOwnFonts() {
        if (fontRemoveInput == null) {
            return;
        }
        List<PanelFontStore.Entry> entries = PanelFontStore.list(host);
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
        fontRemoveInput.setValue(host.getString(R.string.dashboard_font_remove_choose));
        fontRemoveInput.setOnItemSelectedListener(position -> {
            if (position < 0 || position >= entries.size()) {
                return;
            }
            PanelFontStore.Entry entry = entries.get(position);
            PanelFontStore.remove(host, entry.id);
            PanelFonts.forget(entry.id);
            renderOwnFonts();
            // Widgets still naming it fall back to the panel's face on their
            // own, so nothing else has to be rewritten.
            tellPanel();
            Toast.makeText(host, host.getString(R.string.dashboard_font_removed, entry.label),
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
