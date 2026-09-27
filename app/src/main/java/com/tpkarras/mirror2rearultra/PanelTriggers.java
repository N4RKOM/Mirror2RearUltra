package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.Set;

/**
 * Reasons other than the foreground app for the panel to change.
 *
 * <p>A profile answers "which app am I in". These answer "what is going on
 * around the phone": it is night, or it is on the charger. Each names either
 * a saved template, which replaces the arrangement while the condition holds,
 * or a page of the arrangement in use, which the panel then holds on.
 *
 * <p>The more specific and the more recently done wins: a Bluetooth device
 * connecting, then the charger, then the Wi-Fi network, then the clock.
 * Getting into the car and its kit connecting says more about the next hour
 * than being at home, and plugging in is something someone just did, where a
 * network in range and the night window are things that were true already.
 */
final class PanelTriggers {
    private static final String PREFS = "panel_triggers";
    private static final String CHARGING_SLOT = "charging_slot";
    private static final String BLUETOOTH_SLOT = "bluetooth_slot";
    /** The device's address, or empty for any device at all. */
    private static final String BLUETOOTH_DEVICE = "bluetooth_device";
    /** The device's name as it was when chosen, to label the choice by. */
    private static final String BLUETOOTH_NAME = "bluetooth_name";
    private static final String WIFI_SLOT = "wifi_slot";
    /** The network's name, or empty for any Wi-Fi at all. */
    private static final String WIFI_NETWORK = "wifi_network";
    private static final String TIME_SLOT = "time_slot";
    private static final String TIME_FROM = "time_from";
    private static final String TIME_TO = "time_to";
    /** A slot naming a page rather than a template: "page:3". */
    private static final String PAGE_SLOT_PREFIX = "page:";
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
    static String bluetoothSlot(Context context) {
        return slot(context, BLUETOOTH_SLOT);
    }

    static void setBluetoothSlot(Context context, @Nullable String slot) {
        setSlot(context, BLUETOOTH_SLOT, slot);
    }

    /** The address of the device the trigger waits for, or empty for any. */
    static String bluetoothDevice(Context context) {
        return prefs(context).getString(BLUETOOTH_DEVICE, "");
    }

    static String bluetoothDeviceName(Context context) {
        return prefs(context).getString(BLUETOOTH_NAME, "");
    }

    static void setBluetoothDevice(Context context, String address, String name) {
        prefs(context).edit()
                .putString(BLUETOOTH_DEVICE, address)
                .putString(BLUETOOTH_NAME, name)
                .apply();
    }

    @Nullable
    static String wifiSlot(Context context) {
        return slot(context, WIFI_SLOT);
    }

    static void setWifiSlot(Context context, @Nullable String slot) {
        setSlot(context, WIFI_SLOT, slot);
    }

    /** The network the trigger waits for, or empty for any. */
    static String wifiNetwork(Context context) {
        return prefs(context).getString(WIFI_NETWORK, "");
    }

    static void setWifiNetwork(Context context, String network) {
        prefs(context).edit().putString(WIFI_NETWORK, network).apply();
    }

    /**
     * Whether a connected device is the one waited for.
     *
     * @param connected addresses of every device connected now
     */
    static boolean bluetoothMatches(String wanted, Set<String> connected) {
        return wanted.isEmpty() ? !connected.isEmpty() : connected.contains(wanted);
    }

    /**
     * Whether the phone is on the network waited for.
     *
     * @param network the connected network's name, or null when there is none
     *     or when the system will not say which it is
     */
    static boolean wifiMatches(String wanted, boolean onWifi, @Nullable String network) {
        return onWifi && (wanted.isEmpty() || wanted.equals(network));
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
        return activeSlot(context, charging, false, false, nowMillis);
    }

    /**
     * @param bluetoothMatched the device waited for is connected, from
     *     {@link #bluetoothMatches}
     * @param wifiMatched the phone is on the network waited for, from
     *     {@link #wifiMatches}
     */
    @Nullable
    static String activeSlot(Context context, boolean charging, boolean bluetoothMatched,
            boolean wifiMatched, long nowMillis) {
        return pick(new String[]{
                bluetoothMatched ? usableOrNull(context, bluetoothSlot(context)) : null,
                charging ? usableOrNull(context, chargingSlot(context)) : null,
                wifiMatched ? usableOrNull(context, wifiSlot(context)) : null,
                withinWindow(context, nowMillis)
                        ? usableOrNull(context, timeSlot(context)) : null,
        });
    }

    /** The first slot that holds, in the order they outrank each other. */
    @Nullable
    static String pick(String[] inOrder) {
        for (String slot : inOrder) {
            if (slot != null) return slot;
        }
        return null;
    }

    @Nullable
    private static String usableOrNull(Context context, @Nullable String slot) {
        return usable(context, slot) ? slot : null;
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

    /** The slot that stands for a page of the current arrangement. */
    static String pageSlot(int page) {
        return PAGE_SLOT_PREFIX + page;
    }

    /** The page a slot names, or 0 when it names a template or nothing. */
    static int pageOf(@Nullable String slot) {
        if (slot == null || !slot.startsWith(PAGE_SLOT_PREFIX)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(slot.substring(PAGE_SLOT_PREFIX.length())));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    /**
     * Keeps page slots on their page when pages are moved or deleted, and
     * turns a slot off when its page is gone - a trigger quietly moving to
     * whichever page slid into the place would show something nobody chose.
     */
    static void remapPages(Context context, int[] map) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String key : new String[]{CHARGING_SLOT, BLUETOOTH_SLOT, WIFI_SLOT, TIME_SLOT}) {
            int page = pageOf(slot(context, key));
            if (page <= 0) {
                continue;
            }
            int target = page < map.length ? map[page] : 0;
            editor.putString(key, target > 0 ? pageSlot(target) : "");
        }
        editor.apply();
    }

    private static boolean usable(Context context, @Nullable String slot) {
        if (slot == null) {
            return false;
        }
        int page = pageOf(slot);
        if (page > 0) {
            return page <= DashboardWidgetLayout.loadPageCount(context);
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
