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
    private static final AtomicBoolean DASHBOARD_ONLY = new AtomicBoolean(false);
    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();

    private MirrorState() {
    }

    static boolean isActive() {
        return ACTIVE.get();
    }

    static boolean isDashboardOnly() {
        return ACTIVE.get() && DASHBOARD_ONLY.get();
    }

    static void setDashboardOnly(Context context, boolean dashboardOnly) {
        DASHBOARD_ONLY.set(dashboardOnly);
        requestTileRefresh(context);
    }

    static void setActive(Context context, boolean active) {
        if (!active) {
            DASHBOARD_ONLY.set(false);
        }
        boolean changed = ACTIVE.getAndSet(active) != active;
        if (changed) {
            for (Listener listener : LISTENERS) {
                listener.onMirrorStateChanged(active);
            }
        }

        requestTileRefresh(context);
    }

    private static void requestTileRefresh(Context context) {
        try {
            TileService.requestListeningState(
                    context.getApplicationContext(),
                    new ComponentName(context, QuickTileService.class)
            );
            TileService.requestListeningState(
                    context.getApplicationContext(),
                    new ComponentName(context, DashboardTileService.class)
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
