package com.tpkarras.mirror2rearultra;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Temperature and low-battery limits, with the live reading they act on.
 *
 * <p>Split out of {@link SettingsActivity}. This page owns its own
 * {@link DeviceHealthMonitor} for the live reading, started and stopped with
 * the screen so it is not polling while the user is somewhere else.
 */
public class DeviceProtectionActivity extends AppCompatActivity
        implements DeviceHealthState.Listener {

    private TextView deviceHealthStatus;
    private MaterialSwitch temperatureProtectionSwitch;
    private HyperSlider temperatureThresholdSlider;
    private TextView temperatureThresholdValue;
    private MaterialSwitch batteryProtectionSwitch;
    private HyperSlider batteryThresholdSlider;
    private TextView batteryThresholdValue;
    private ViewGroup content;

    /** Guards the listeners while the UI is being written from stored state. */
    private boolean bindingUi;
    private DeviceHealthMonitor healthMonitor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_protection);

        MaterialToolbar toolbar = findViewById(R.id.device_protection_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        deviceHealthStatus = findViewById(R.id.device_health_status);
        temperatureProtectionSwitch = findViewById(R.id.temperature_protection_switch);
        temperatureThresholdSlider = findViewById(R.id.temperature_threshold_slider);
        temperatureThresholdValue = findViewById(R.id.temperature_threshold_value);
        batteryProtectionSwitch = findViewById(R.id.battery_protection_switch);
        batteryThresholdSlider = findViewById(R.id.battery_threshold_slider);
        batteryThresholdValue = findViewById(R.id.battery_threshold_value);
        content = findViewById(R.id.device_protection_content);

        SettingsLayout.constrainContentOnWideScreens(this, content);
        bindInteractions();
        healthMonitor = new DeviceHealthMonitor(this, snapshot ->
                runOnUiThread(() -> updateDeviceHealthUi(snapshot))
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        DeviceHealthState.addListener(this);
        healthMonitor.start();
    }

    @Override
    protected void onStop() {
        DeviceHealthState.removeListener(this);
        healthMonitor.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStoredSettings();
        updateDeviceHealthUi(DeviceHealthState.get());
    }

    @Override
    public void onDeviceHealthChanged(DeviceHealthState.Snapshot snapshot) {
        runOnUiThread(() -> updateDeviceHealthUi(snapshot));
    }

    private void bindInteractions() {
        temperatureProtectionSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                MirrorSettings.setTemperatureProtectionEnabled(this, checked);
                healthMonitor.refresh();
            }
        });

        temperatureThresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            int threshold = Math.round(value);
            updateTemperatureThresholdValue(threshold);
            if (fromUser && !bindingUi) {
                MirrorSettings.setTemperatureThreshold(this, threshold);
                healthMonitor.refresh();
            }
        });

        batteryProtectionSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingUi) {
                MirrorSettings.setBatteryProtectionEnabled(this, checked);
                healthMonitor.refresh();
            }
        });

        batteryThresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            int threshold = Math.round(value);
            updateBatteryThresholdValue(threshold);
            if (fromUser && !bindingUi) {
                MirrorSettings.setBatteryThreshold(this, threshold);
                healthMonitor.refresh();
            }
        });
    }

    private void renderStoredSettings() {
        bindingUi = true;
        temperatureProtectionSwitch.setChecked(
                MirrorSettings.isTemperatureProtectionEnabled(this));
        temperatureThresholdSlider.setValue(MirrorSettings.loadTemperatureThreshold(this));
        batteryProtectionSwitch.setChecked(MirrorSettings.isBatteryProtectionEnabled(this));
        batteryThresholdSlider.setValue(MirrorSettings.loadBatteryThreshold(this));
        bindingUi = false;
        updateTemperatureThresholdValue(Math.round(temperatureThresholdSlider.getValue()));
        updateBatteryThresholdValue(Math.round(batteryThresholdSlider.getValue()));
    }

    private void updateDeviceHealthUi(DeviceHealthState.Snapshot snapshot) {
        if (snapshot.batteryPercent < 0 || snapshot.temperatureTenthsCelsius < 0) {
            deviceHealthStatus.setText(R.string.device_health_unknown);
            return;
        }
        float temperature = snapshot.temperatureTenthsCelsius / 10f;
        switch (snapshot.pauseReason) {
            case TEMPERATURE:
                deviceHealthStatus.setText(getString(
                        R.string.device_health_paused_temperature,
                        snapshot.batteryPercent,
                        temperature
                ));
                break;
            case LOW_BATTERY:
                deviceHealthStatus.setText(getString(
                        R.string.device_health_paused_battery,
                        snapshot.batteryPercent,
                        temperature
                ));
                break;
            default:
                deviceHealthStatus.setText(getString(
                        snapshot.charging
                                ? R.string.device_health_ok_charging
                                : R.string.device_health_ok,
                        snapshot.batteryPercent,
                        temperature
                ));
        }
    }

    private void updateTemperatureThresholdValue(int celsius) {
        temperatureThresholdValue.setText(getString(R.string.temperature_threshold_value, celsius));
        temperatureThresholdSlider.setContentDescription(
                getString(R.string.temperature_threshold_value, celsius)
        );
    }

    private void updateBatteryThresholdValue(int percent) {
        batteryThresholdValue.setText(getString(R.string.battery_threshold_value, percent));
        batteryThresholdSlider.setContentDescription(
                getString(R.string.battery_threshold_value, percent)
        );
    }
}
