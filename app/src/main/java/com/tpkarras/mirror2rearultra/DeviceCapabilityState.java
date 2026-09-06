package com.tpkarras.mirror2rearultra;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class DeviceCapabilityState {
    interface Listener {
        void onDeviceCapabilityChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        final boolean probing;
        final Boolean rootAvailable;
        final Boolean rearBrightnessAvailable;
        final boolean hardwareBrightnessActive;

        Snapshot(
                boolean probing,
                Boolean rootAvailable,
                Boolean rearBrightnessAvailable,
                boolean hardwareBrightnessActive
        ) {
            this.probing = probing;
            this.rootAvailable = rootAvailable;
            this.rearBrightnessAvailable = rearBrightnessAvailable;
            this.hardwareBrightnessActive = hardwareBrightnessActive;
        }
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot snapshot = new Snapshot(false, null, null, false);

    private DeviceCapabilityState() {
    }

    static Snapshot get() {
        return snapshot;
    }

    static void setProbing() {
        publish(new Snapshot(true, snapshot.rootAvailable, snapshot.rearBrightnessAvailable,
                snapshot.hardwareBrightnessActive));
    }

    static void setCapabilities(boolean rootAvailable, boolean rearBrightnessAvailable) {
        publish(new Snapshot(false, rootAvailable, rearBrightnessAvailable,
                snapshot.hardwareBrightnessActive && rearBrightnessAvailable));
    }

    static void setHardwareBrightnessActive(boolean active) {
        publish(new Snapshot(snapshot.probing, snapshot.rootAvailable,
                snapshot.rearBrightnessAvailable, active));
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void publish(Snapshot value) {
        snapshot = value;
        for (Listener listener : LISTENERS) {
            listener.onDeviceCapabilityChanged(value);
        }
    }
}
