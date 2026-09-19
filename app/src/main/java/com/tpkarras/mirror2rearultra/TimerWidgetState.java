package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * A stopwatch, or a countdown, depending on whether it was given a length.
 *
 * <p>One widget rather than two. The difference between the two is a number
 * chosen once in the builder, and everything else - the running, the pausing,
 * the resetting, the drawing - would have been written twice for it.
 *
 * <p>Kept in preferences rather than in memory because the panel is not
 * always up. A session ends when the phone is put down, and a timer that
 * forgot itself every time that happened would be no use to anyone cooking.
 */
final class TimerWidgetState {
    private static final String PREFS = "panel_timer";
    private static final String RUNNING = "running";
    private static final String ACCUMULATED = "accumulated_millis";
    private static final String STARTED_AT = "run_started_realtime";

    private TimerWidgetState() {
    }

    static boolean isRunning(Context context) {
        return prefs(context).getBoolean(RUNNING, false);
    }

    /** How long it has counted, running or not. */
    static long elapsedMillis(Context context) {
        SharedPreferences in = prefs(context);
        long accumulated = in.getLong(ACCUMULATED, 0L);
        if (!in.getBoolean(RUNNING, false)) {
            return Math.max(0L, accumulated);
        }
        // A reboot puts elapsedRealtime back near zero while a run is still
        // recorded as open, which would otherwise read as a negative age.
        long since = SystemClock.elapsedRealtime() - in.getLong(STARTED_AT, 0L);
        return Math.max(0L, accumulated + Math.max(0L, since));
    }

    /** Starts it, or pauses it where it stands. */
    static void toggle(Context context) {
        SharedPreferences in = prefs(context);
        if (in.getBoolean(RUNNING, false)) {
            in.edit()
                    .putBoolean(RUNNING, false)
                    .putLong(ACCUMULATED, elapsedMillis(context))
                    .apply();
            return;
        }
        in.edit()
                .putBoolean(RUNNING, true)
                .putLong(STARTED_AT, SystemClock.elapsedRealtime())
                .apply();
    }

    static void reset(Context context) {
        prefs(context).edit()
                .putBoolean(RUNNING, false)
                .putLong(ACCUMULATED, 0L)
                .apply();
    }

    /**
     * Stops a countdown that has run out, so it holds at zero.
     *
     * @return true when this call is what stopped it, for a single buzz
     */
    static boolean finishIfElapsed(Context context, long durationMillis) {
        if (durationMillis <= 0L || !isRunning(context)) {
            return false;
        }
        if (elapsedMillis(context) < durationMillis) {
            return false;
        }
        prefs(context).edit()
                .putBoolean(RUNNING, false)
                .putLong(ACCUMULATED, durationMillis)
                .apply();
        return true;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
