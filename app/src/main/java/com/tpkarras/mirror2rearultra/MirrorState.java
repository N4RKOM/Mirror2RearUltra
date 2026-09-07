package com.tpkarras.mirror2rearultra;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
    /**
     * Counts the runs of the session, so an activity from a finished one can
     * tell that it no longer owns the state before clearing it. Switching
     * tiles stops one session and starts the next in a single click, and the
     * outgoing activity is destroyed after the new one has been armed.
     */
    private static final java.util.concurrent.atomic.AtomicInteger GENERATION =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    /**
     * How long a session that has been armed has to actually open before it is
     * given up on. Long enough for the shade to collapse and a cold process to
     * start, short enough that a tile is not left lit for any noticeable time.
     */
    private static final long LAUNCH_TIMEOUT_MILLIS = 3_000L;
    private static final Handler LAUNCH_HANDLER = new Handler(Looper.getMainLooper());
    /** Main thread only: armed from a tile's click, cleared from the same thread. */
    private static Runnable pendingLaunchCheck;

    private MirrorState() {
    }

    static boolean isActive() {
        return ACTIVE.get();
    }

    /** Which run of the session is current. */
    static int generation() {
        return GENERATION.get();
    }

    static boolean isDashboardOnly() {
        return ACTIVE.get() && DASHBOARD_ONLY.get();
    }

    static void setDashboardOnly(Context context, boolean dashboardOnly) {
        DASHBOARD_ONLY.set(dashboardOnly);
        requestTileRefresh(context);
    }

    /**
     * Switches the session on and waits to see it open.
     *
     * <p>A tile marks the session started and then asks for the activity that
     * runs it. When that request is quietly dropped - a background launch the
     * system refuses, a locked device, a cancelled pending intent - nothing
     * used to notice: the tile stayed lit over a panel with nothing on it, and
     * the next press switched off a session that had never existed, so it took
     * two presses to start one. If the activity does not arrive, this puts the
     * state back where it was.
     */
    static void armSession(Context context, boolean dashboardOnly) {
        setDashboardOnly(context, dashboardOnly);
        setActive(context, true);
        cancelLaunchCheck();
        Context application = context.getApplicationContext();
        int armedGeneration = GENERATION.get();
        Runnable check = new Runnable() {
            @Override
            public void run() {
                pendingLaunchCheck = null;
                // Only this run of the session: a later one has its own.
                if (GENERATION.get() != armedGeneration || !ACTIVE.get()) {
                    return;
                }
                Log.w(TAG, "Nothing opened the armed session; switching it back off");
                setActive(application, false);
            }
        };
        pendingLaunchCheck = check;
        LAUNCH_HANDLER.postDelayed(check, LAUNCH_TIMEOUT_MILLIS);
    }

    /** Reports that the activity a tile asked for has arrived. */
    static void confirmLaunch() {
        cancelLaunchCheck();
    }

    private static void cancelLaunchCheck() {
        if (pendingLaunchCheck != null) {
            LAUNCH_HANDLER.removeCallbacks(pendingLaunchCheck);
            pendingLaunchCheck = null;
        }
    }

    static void setActive(Context context, boolean active) {
        if (!active) {
            DASHBOARD_ONLY.set(false);
            // Whoever switched it off has settled the question the wait was
            // asking, including the tiles' own error handling.
            cancelLaunchCheck();
        }
        boolean changed = ACTIVE.getAndSet(active) != active;
        if (active && changed) {
            GENERATION.incrementAndGet();
        }
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
