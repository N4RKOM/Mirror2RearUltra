package com.tpkarras.mirror2rearultra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The second time zone widget's choices and arithmetic.
 *
 * <p>A short list of cities rather than all six hundred zone names: one or
 * two per offset, which is what the panel can use and a list a finger can
 * get through. The names are shown in the phone's language by ICU.
 */
final class WorldClock {
    static final String DEFAULT_ZONE = "Europe/London";

    static final String[] ZONES = {
            "Pacific/Honolulu", "America/Anchorage", "America/Los_Angeles",
            "America/Denver", "America/Phoenix", "America/Chicago", "America/Mexico_City",
            "America/New_York", "America/Toronto", "America/Bogota", "America/Lima",
            "America/Halifax", "America/Santiago", "America/St_Johns", "America/Sao_Paulo",
            "America/Argentina/Buenos_Aires", "Atlantic/Azores", "Atlantic/Reykjavik",
            "UTC", "Europe/London", "Europe/Lisbon", "Africa/Lagos", "Europe/Paris",
            "Europe/Berlin", "Europe/Madrid", "Europe/Rome", "Europe/Warsaw", "Europe/Prague",
            "Europe/Kaliningrad", "Europe/Kyiv", "Europe/Athens", "Europe/Helsinki",
            "Africa/Cairo", "Africa/Johannesburg", "Asia/Jerusalem", "Europe/Istanbul",
            "Europe/Minsk", "Europe/Moscow", "Asia/Riyadh", "Africa/Nairobi", "Asia/Tehran",
            "Asia/Dubai", "Europe/Samara", "Asia/Tbilisi", "Asia/Yerevan", "Asia/Baku",
            "Asia/Kabul", "Asia/Yekaterinburg", "Asia/Tashkent", "Asia/Karachi",
            "Asia/Kolkata", "Asia/Kathmandu", "Asia/Almaty", "Asia/Bishkek", "Asia/Omsk",
            "Asia/Dhaka", "Asia/Yangon", "Asia/Novosibirsk", "Asia/Krasnoyarsk",
            "Asia/Bangkok", "Asia/Jakarta", "Asia/Irkutsk", "Asia/Shanghai",
            "Asia/Hong_Kong", "Asia/Singapore", "Asia/Taipei", "Australia/Perth",
            "Asia/Yakutsk", "Asia/Seoul", "Asia/Tokyo", "Australia/Darwin",
            "Australia/Adelaide", "Asia/Vladivostok", "Australia/Brisbane", "Australia/Sydney",
            "Asia/Magadan", "Asia/Kamchatka", "Pacific/Auckland",
    };

    /**
     * The zones this phone knows, west to east by their offset at {@code nowMillis}.
     *
     * <p>A name the phone's time zone data lacks comes back as GMT, so it is
     * dropped rather than offered as a city that shows the wrong time.
     */
    static List<String> sortedZones(long nowMillis) {
        List<String> zones = new ArrayList<>();
        for (String id : ZONES) {
            if (id.equals("UTC") || !TimeZone.getTimeZone(id).getID().equals("GMT")) {
                zones.add(id);
            }
        }
        zones.sort(Comparator.comparingInt(
                (String id) -> TimeZone.getTimeZone(id).getOffset(nowMillis)));
        return zones;
    }

    /** "+6", "-2:30", "0": the hours between two offsets, for the detailed form. */
    static String differenceLabel(int minutes) {
        if (minutes == 0) return "0";
        int size = Math.abs(minutes);
        String sign = minutes > 0 ? "+" : "−";
        return size % 60 == 0
                ? sign + size / 60
                : String.format(Locale.ROOT, "%s%d:%02d", sign, size / 60, size % 60);
    }

    /** "UTC+5:30" and the like, for telling apart the list's cities. */
    static String utcLabel(int offsetMinutes) {
        return offsetMinutes == 0 ? "UTC" : "UTC" + differenceLabel(offsetMinutes);
    }

    /** The last part of the zone's name, when ICU has no city for it. */
    static String fallbackName(String zoneId) {
        return zoneId.substring(zoneId.lastIndexOf('/') + 1).replace('_', ' ');
    }

    private WorldClock() {}
}
