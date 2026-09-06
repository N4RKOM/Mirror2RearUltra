package com.tpkarras.mirror2rearultra;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class DeviceHealthState {
    interface Listener {
        void onDeviceHealthChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        final int batteryPercent;
        final int temperatureTenthsCelsius;
        final boolean charging;
        final DeviceHealthPolicy.PauseReason pauseReason;

        Snapshot(
                int batteryPercent,
                int temperatureTenthsCelsius,
                boolean charging,
                DeviceHealthPolicy.PauseReason pauseReason
        ) {
            this.batteryPercent = batteryPercent;
            this.temperatureTenthsCelsius = temperatureTenthsCelsius;
            this.charging = charging;
            this.pauseReason = pauseReason;
        }

        boolean outputAllowed() {
            return pauseReason == DeviceHealthPolicy.PauseReason.NONE;
        }
    }

    private static final Snapshot UNKNOWN = new Snapshot(
            -1,
            -1,
            false,
            DeviceHealthPolicy.PauseReason.NONE
    );
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot snapshot = UNKNOWN;

    private DeviceHealthState() {
    }

    static Snapshot get() {
        return snapshot;
    }

    static void set(Snapshot value) {
        snapshot = value == null ? UNKNOWN : value;
        for (Listener listener : LISTENERS) {
            listener.onDeviceHealthChanged(snapshot);
        }
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }
}
