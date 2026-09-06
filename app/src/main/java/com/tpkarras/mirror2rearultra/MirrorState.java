package com.tpkarras.mirror2rearultra;

import android.content.ComponentName;
import android.content.Context;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

final class MirrorState {
    interface Listener {
        void onMirrorStateChanged(boolean active);
    }

    private static final String TAG = "Mirror2RearState";
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();

    private MirrorState() {
    }

    static boolean isActive() {
        return ACTIVE.get();
    }

    static void setActive(Context context, boolean active) {
        boolean changed = ACTIVE.getAndSet(active) != active;
        if (changed) {
            for (Listener listener : LISTENERS) {
                listener.onMirrorStateChanged(active);
            }
        }

        try {
            TileService.requestListeningState(
                    context.getApplicationContext(),
                    new ComponentName(context, QuickTileService.class)
            );
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to request a Quick Settings tile refresh", error);
        }
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }
}
