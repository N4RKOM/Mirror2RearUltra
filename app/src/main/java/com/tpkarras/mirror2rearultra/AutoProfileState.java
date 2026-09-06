package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class AutoProfileState {
    interface Listener {
        void onAutoProfileChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        @Nullable
        final String profileId;
        @Nullable
        final String packageName;

        Snapshot(@Nullable String profileId, @Nullable String packageName) {
            this.profileId = profileId;
            this.packageName = packageName;
        }
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot snapshot = new Snapshot(null, null);

    private AutoProfileState() {
    }

    static Snapshot get() {
        return snapshot;
    }

    static void set(@Nullable String profileId, @Nullable String packageName) {
        Snapshot next = new Snapshot(profileId, packageName);
        snapshot = next;
        for (Listener listener : LISTENERS) {
            listener.onAutoProfileChanged(next);
        }
    }

    static void clear() {
        set(null, null);
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }
}
