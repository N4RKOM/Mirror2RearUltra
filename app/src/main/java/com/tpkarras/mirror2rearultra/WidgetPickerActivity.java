package com.tpkarras.mirror2rearultra;

import android.Manifest;
import android.os.Build;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.EnumMap;
import java.util.Map;

/**
 * Chooses which widgets the rear panel shows.
 *
 * <p>The single place a widget is switched on or off. It used to be two: a
 * switch per widget on the dashboard settings page and another inside every
 * builder card, which forced the builder to list all nineteen widgets whether
 * or not they were in use. The builder now lists only what is on, and what is
 * off waits here.
 *
 * <p>Reached from both the dashboard settings page and the builder.
 */
public class WidgetPickerActivity extends AppCompatActivity implements MediaWidgetState.Listener {

    /**
     * Pairs each switch with the widget it controls, in the order the screen
     * lists them. A widget needs one row here and one row in the layout.
     */
    private static final class WidgetRow {
        final int viewId;
        final DashboardWidgetLayout.Widget widget;

        WidgetRow(int viewId, DashboardWidgetLayout.Widget widget) {
            this.viewId = viewId;
            this.widget = widget;
        }
    }

    private static final WidgetRow[] WIDGET_ROWS = {
            // Time
            new WidgetRow(R.id.dashboard_clock_switch, DashboardWidgetLayout.Widget.CLOCK),
            new WidgetRow(R.id.dashboard_date_switch, DashboardWidgetLayout.Widget.DATE),
            new WidgetRow(R.id.dashboard_next_alarm_switch, DashboardWidgetLayout.Widget.NEXT_ALARM),
            new WidgetRow(R.id.dashboard_session_timer_switch,
                    DashboardWidgetLayout.Widget.SESSION_TIMER),
            new WidgetRow(R.id.dashboard_calendar_switch, DashboardWidgetLayout.Widget.CALENDAR),
            // Device
            new WidgetRow(R.id.dashboard_battery_switch, DashboardWidgetLayout.Widget.BATTERY),
            new WidgetRow(R.id.dashboard_temperature_switch,
                    DashboardWidgetLayout.Widget.TEMPERATURE),
            new WidgetRow(R.id.dashboard_memory_switch, DashboardWidgetLayout.Widget.MEMORY),
            new WidgetRow(R.id.dashboard_storage_switch, DashboardWidgetLayout.Widget.STORAGE),
            new WidgetRow(R.id.dashboard_network_switch, DashboardWidgetLayout.Widget.NETWORK),
            // Weather and sensors
            new WidgetRow(R.id.dashboard_weather_switch, DashboardWidgetLayout.Widget.WEATHER),
            new WidgetRow(R.id.dashboard_fullscreen_weather_switch,
                    DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER),
            new WidgetRow(R.id.dashboard_compass_switch, DashboardWidgetLayout.Widget.COMPASS),
            new WidgetRow(R.id.dashboard_speed_switch, DashboardWidgetLayout.Widget.SPEED),
            new WidgetRow(R.id.dashboard_altitude_switch, DashboardWidgetLayout.Widget.ALTITUDE),
            new WidgetRow(R.id.dashboard_steps_switch, DashboardWidgetLayout.Widget.STEPS),
            // Media and notifications
            new WidgetRow(R.id.dashboard_media_switch, DashboardWidgetLayout.Widget.MEDIA),
            new WidgetRow(R.id.dashboard_fullscreen_media_switch,
                    DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA),
            new WidgetRow(R.id.dashboard_notifications_switch,
                    DashboardWidgetLayout.Widget.NOTIFICATIONS),
            // Other
            new WidgetRow(R.id.dashboard_active_profile_switch,
                    DashboardWidgetLayout.Widget.ACTIVE_PROFILE),
            new WidgetRow(R.id.dashboard_custom_text_switch,
                    DashboardWidgetLayout.Widget.CUSTOM_TEXT),
    };

    private final Map<DashboardWidgetLayout.Widget, MaterialSwitch> widgetSwitches =
            new EnumMap<>(DashboardWidgetLayout.Widget.class);

    private TextInputLayout weatherCityContainer;
    private TextInputEditText weatherCityInput;
    private TextInputLayout customTextContainer;
    private TextInputEditText customTextInput;
    private TextView locationAccessStatus;
    private TextView mediaAccessStatus;
    private TextView mediaNowPlaying;
    private View mediaPreviousButton;
    private View mediaPlayPauseButton;
    private View mediaNextButton;
    private View locationAccessButton;
    private View mediaAccessButton;
    private ViewGroup content;

    /** Guards the listeners while the UI is being written from stored state. */
    private boolean bindingUi;
    private ActivityResultLauncher<String> locationPermissionLauncher;
    /** Calendar and step counting each need their own runtime permission. */
    private ActivityResultLauncher<String> widgetPermissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> updatePermissionUi()
        );
        widgetPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { });
        setContentView(R.layout.activity_widget_picker);

        MaterialToolbar toolbar = findViewById(R.id.widget_picker_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        for (WidgetRow row : WIDGET_ROWS) {
            widgetSwitches.put(row.widget, findViewById(row.viewId));
        }
        weatherCityContainer = findViewById(R.id.dashboard_weather_city_container);
        weatherCityInput = findViewById(R.id.dashboard_weather_city_input);
        customTextContainer = findViewById(R.id.dashboard_custom_text_container);
        customTextInput = findViewById(R.id.dashboard_custom_text_input);
        locationAccessStatus = findViewById(R.id.dashboard_location_access_status);
        mediaAccessStatus = findViewById(R.id.dashboard_media_access_status);
        mediaNowPlaying = findViewById(R.id.dashboard_media_now_playing);
        mediaPreviousButton = findViewById(R.id.dashboard_media_previous_button);
        mediaPlayPauseButton = findViewById(R.id.dashboard_media_play_pause_button);
        mediaNextButton = findViewById(R.id.dashboard_media_next_button);
        locationAccessButton = findViewById(R.id.dashboard_location_access_button);
        mediaAccessButton = findViewById(R.id.dashboard_media_access_button);
        content = findViewById(R.id.widget_picker_content);

        SettingsLayout.constrainContentOnWideScreens(this, content);
        bindInteractions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        MediaWidgetState.addListener(this);
        updateMediaControls(MediaWidgetState.get());
    }

    @Override
    protected void onStop() {
        MediaWidgetState.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        updatePermissionUi();
    }

    @Override
    protected void onPause() {
        saveTexts();
        super.onPause();
    }

    @Override
    public void onMediaWidgetChanged(MediaWidgetState.Snapshot snapshot) {
        runOnUiThread(() -> updateMediaControls(snapshot));
    }

    private void bindInteractions() {
        for (Map.Entry<DashboardWidgetLayout.Widget, MaterialSwitch> entry
                : widgetSwitches.entrySet()) {
            DashboardWidgetLayout.Widget widget = entry.getKey();
            entry.getValue().setOnCheckedChangeListener((button, checked) -> {
                if (bindingUi) {
                    return;
                }
                DashboardWidgetLayout.setWidgetEnabled(this, widget, checked);
                onWidgetToggled(widget, checked);
            });
        }

        mediaPreviousButton.setOnClickListener(view -> MediaNotificationListenerService.previous());
        mediaPlayPauseButton.setOnClickListener(view -> MediaNotificationListenerService.playPause());
        mediaNextButton.setOnClickListener(view -> MediaNotificationListenerService.next());

        weatherCityInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                saveTexts();
            }
        });
        weatherCityInput.setOnEditorActionListener((view, actionId, event) -> {
            saveTexts();
            weatherCityInput.clearFocus();
            return false;
        });
        customTextInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                saveTexts();
            }
        });
        customTextInput.setOnEditorActionListener((view, actionId, event) -> {
            saveTexts();
            customTextInput.clearFocus();
            return false;
        });

        locationAccessButton.setOnClickListener(view -> locationPermissionLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
        ));
        mediaAccessButton.setOnClickListener(view -> openMediaAccessSettings());
    }

    /** Extra work for the widgets that carry a value or need a permission. */
    private void onWidgetToggled(DashboardWidgetLayout.Widget widget, boolean checked) {
        switch (widget) {
            case WEATHER:
            case FULLSCREEN_WEATHER:
                weatherCityContainer.setEnabled(
                        isWidgetOn(DashboardWidgetLayout.Widget.WEATHER)
                                || isWidgetOn(DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER));
                break;
            case CUSTOM_TEXT:
                customTextContainer.setEnabled(checked);
                break;
            case MEDIA:
            case FULLSCREEN_MEDIA:
                updatePermissionUi();
                break;
            case SPEED:
            case ALTITUDE:
                requestLocationIfNeeded(checked);
                updatePermissionUi();
                break;
            case CALENDAR:
                if (checked) {
                    widgetPermissionLauncher.launch(Manifest.permission.READ_CALENDAR);
                }
                break;
            case STEPS:
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    widgetPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
                }
                break;
            default:
                break;
        }
    }

    private void render() {
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this);
        bindingUi = true;
        for (Map.Entry<DashboardWidgetLayout.Widget, MaterialSwitch> entry
                : widgetSwitches.entrySet()) {
            entry.getValue().setChecked(
                    DashboardWidgetLayout.isWidgetEnabled(this, entry.getKey()));
        }
        weatherCityInput.setText(settings.weatherCity);
        weatherCityContainer.setEnabled(isWidgetOn(DashboardWidgetLayout.Widget.WEATHER)
                || isWidgetOn(DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER));
        customTextInput.setText(settings.customText);
        customTextContainer.setEnabled(isWidgetOn(DashboardWidgetLayout.Widget.CUSTOM_TEXT));
        bindingUi = false;
    }

    /**
     * Writes the two text values. Widget flags are not touched here: each
     * switch saves its own widget the moment it is toggled.
     */
    private void saveTexts() {
        String city = weatherCityInput.getText() == null
                ? "" : weatherCityInput.getText().toString();
        String text = customTextInput.getText() == null
                ? "" : customTextInput.getText().toString();
        DashboardSettings current = MirrorSettings.loadDashboardSettings(this);
        if (current.weatherCity.equals(city) && current.customText.equals(text)) {
            return;
        }
        MirrorSettings.saveDashboardSettings(this, current.withTexts(city, text));
    }

    private boolean isWidgetOn(DashboardWidgetLayout.Widget widget) {
        MaterialSwitch control = widgetSwitches.get(widget);
        return control != null && control.isChecked();
    }

    private void requestLocationIfNeeded(boolean enabled) {
        if (bindingUi || !enabled || hasLocationAccess()) {
            return;
        }
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void updatePermissionUi() {
        if (locationAccessStatus == null || mediaAccessStatus == null) {
            return;
        }
        boolean locationRequired = isWidgetOn(DashboardWidgetLayout.Widget.SPEED)
                || isWidgetOn(DashboardWidgetLayout.Widget.ALTITUDE);
        boolean locationGranted = hasLocationAccess();
        locationAccessStatus.setText(locationGranted
                ? R.string.dashboard_location_access_granted
                : locationRequired
                ? R.string.dashboard_location_access_required
                : R.string.dashboard_location_access_optional);
        locationAccessButton.setVisibility(
                locationRequired && !locationGranted ? View.VISIBLE : View.GONE
        );

        boolean mediaGranted = MediaWidgetState.hasNotificationAccess(this);
        mediaAccessStatus.setText(mediaGranted
                ? R.string.dashboard_media_access_granted
                : isWidgetOn(DashboardWidgetLayout.Widget.MEDIA)
                        || isWidgetOn(DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA)
                ? R.string.dashboard_media_access_required
                : R.string.dashboard_media_access_optional);
        mediaAccessButton.setVisibility(
                (isWidgetOn(DashboardWidgetLayout.Widget.MEDIA)
                        || isWidgetOn(DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA)) && !mediaGranted
                        ? View.VISIBLE : View.GONE
        );
    }

    private void updateMediaControls(MediaWidgetState.Snapshot snapshot) {
        boolean available = snapshot != null && snapshot.hasMedia();
        String title = available ? (snapshot.title.isEmpty() ? snapshot.artist : snapshot.title)
                : getString(R.string.dashboard_media_nothing_playing);
        mediaNowPlaying.setText(title);
        mediaPreviousButton.setEnabled(available);
        mediaPlayPauseButton.setEnabled(available);
        mediaNextButton.setEnabled(available);
    }

    private boolean hasLocationAccess() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openMediaAccessSettings() {
        ComponentName service = new ComponentName(this, MediaNotificationListenerService.class);
        Intent details = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        service.flattenToString());
        try {
            startActivity(details);
        } catch (RuntimeException unavailable) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }
}
