package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.Calendar;

/**
 * Reasons other than the foreground app for the panel to change.
 *
 * <p>A profile answers "which app am I in". These answer "what is going on
 * around the phone": it is night, or it is on the charger. Both name a saved
 * template, and while the condition holds the panel shows it.
 *
 * <p>Charging wins over the clock. Putting a phone on the charger is
 * something someone just did, and the night window is something that was
 * true already; the more recent intent should be the one on screen.
 */
final class PanelTriggers {
    private static final String PREFS = "panel_triggers";
    private static final String CHARGING_SLOT = "charging_slot";
    private static final String TIME_SLOT = "time_slot";
    private static final String TIME_FROM = "time_from";
    private static final String TIME_TO = "time_to";
    /** Minutes past midnight; the usual night anyone would pick. */
    private static final int DEFAULT_FROM = 22 * 60;
    private static final int DEFAULT_TO = 7 * 60;

    private PanelTriggers() {
    }

    @Nullable
    static String chargingSlot(Context context) {
        return slot(context, CHARGING_SLOT);
    }

    static void setChargingSlot(Context context, @Nullable String slot) {
        setSlot(context, CHARGING_SLOT, slot);
    }

    @Nullable
    static String timeSlot(Context context) {
        return slot(context, TIME_SLOT);
    }

    static void setTimeSlot(Context context, @Nullable String slot) {
        setSlot(context, TIME_SLOT, slot);
    }

    static int fromMinutes(Context context) {
        return clampMinutes(prefs(context).getInt(TIME_FROM, DEFAULT_FROM));
    }

    static int toMinutes(Context context) {
        return clampMinutes(prefs(context).getInt(TIME_TO, DEFAULT_TO));
    }

    static void setWindow(Context context, int fromMinutes, int toMinutes) {
        prefs(context).edit()
                .putInt(TIME_FROM, clampMinutes(fromMinutes))
                .putInt(TIME_TO, clampMinutes(toMinutes))
                .apply();
    }

    /**
     * Which template the surroundings ask for, or null for none.
     *
     * <p>A slot naming a template that has since been deleted counts as none,
     * rather than leaving the panel pinned to something that cannot be
     * applied.
     */
    @Nullable
    static String activeSlot(Context context, boolean charging, long nowMillis) {
        String charged = chargingSlot(context);
        if (charging && usable(context, charged)) {
            return charged;
        }
        String timed = timeSlot(context);
        if (usable(context, timed) && withinWindow(context, nowMillis)) {
            return timed;
        }
        return null;
    }

    static boolean withinWindow(Context context, long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        int now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        return within(now, fromMinutes(context), toMinutes(context));
    }

    /**
     * Whether a minute of the day falls in the window.
     *
     * <p>A night runs past midnight, so when the end is the smaller number the
     * window is the outside of the range rather than the inside. Equal ends
     * are no window at all rather than the whole day: someone who has not
     * chosen yet should not have the panel taken over.
     */
    static boolean within(int nowMinutes, int from, int to) {
        if (from == to) {
            return false;
        }
        return from < to
                ? nowMinutes >= from && nowMinutes < to
                : nowMinutes >= from || nowMinutes < to;
    }

    /** How long until the window opens or closes, for scheduling one check. */
    static long millisUntilNextEdge(Context context, long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        int now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        int seconds = calendar.get(Calendar.SECOND);
        int from = fromMinutes(context);
        int to = toMinutes(context);
        long soonest = Long.MAX_VALUE;
        for (int edge : new int[]{from, to}) {
            int minutes = edge - now;
            if (minutes <= 0) {
                minutes += 24 * 60;
            }
            soonest = Math.min(soonest, minutes * 60_000L - seconds * 1_000L);
        }
        // Never zero: a check that schedules itself for now would spin.
        return Math.max(30_000L, soonest);
    }

    private static boolean usable(Context context, @Nullable String slot) {
        if (slot == null) {
            return false;
        }
        for (DashboardTemplateStore.Named named : DashboardTemplateStore.listNamed(context)) {
            if (named.id.equals(slot)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String slot(Context context, String key) {
        String value = prefs(context).getString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static void setSlot(Context context, String key, @Nullable String slot) {
        prefs(context).edit().putString(key, slot == null ? "" : slot).apply();
    }

    private static int clampMinutes(int value) {
        return Math.max(0, Math.min(24 * 60 - 1, value));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
