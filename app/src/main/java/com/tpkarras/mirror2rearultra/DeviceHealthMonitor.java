package com.tpkarras.mirror2rearultra;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.core.content.ContextCompat;

final class DeviceHealthMonitor {
    interface Listener {
        void onHealthChanged(DeviceHealthState.Snapshot snapshot);
    }

    private final Context context;
    private final Listener listener;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update(intent);
        }
    };
    private DeviceHealthPolicy.PauseReason previousReason = DeviceHealthPolicy.PauseReason.NONE;
    private boolean registered;

    DeviceHealthMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (registered) {
            refresh();
            return;
        }
        Intent sticky = ContextCompat.registerReceiver(
                context,
                receiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        registered = true;
        if (sticky != null) {
            update(sticky);
        }
    }

    void refresh() {
        Intent sticky = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
            update(sticky);
        }
    }

    void stop() {
        if (registered) {
            context.unregisterReceiver(receiver);
            registered = false;
        }
    }

    private void update(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int batteryPercent = level < 0 || scale <= 0 ? -1 : Math.round(level * 100f / scale);
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        DeviceHealthPolicy.PauseReason reason = DeviceHealthPolicy.evaluate(
                temperature,
                batteryPercent,
                charging,
                MirrorSettings.isTemperatureProtectionEnabled(context),
                MirrorSettings.loadTemperatureThreshold(context),
                MirrorSettings.isBatteryProtectionEnabled(context),
                MirrorSettings.loadBatteryThreshold(context),
                previousReason
        );
        previousReason = reason;
        DeviceHealthState.Snapshot snapshot = new DeviceHealthState.Snapshot(
                batteryPercent,
                temperature,
                charging,
                reason
        );
        DeviceHealthState.set(snapshot);
        listener.onHealthChanged(snapshot);
    }
}
