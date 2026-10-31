package com.tpkarras.mirror2rearultra;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.tpkarras.mirror2rearultra.DashboardWidgetLayout.Variant;

/**
 * What each widget says in each of its three forms.
 *
 * <p>These used to be written inline in {@code RearDashboardView.createLines},
 * three hundred lines of them among the drawing code, where nothing could be
 * run without a view. Here each is a function of the reading, the form and
 * the locale, so a test can ask for every form of every widget and a new
 * widget adds one function rather than another branch of a long method.
 *
 * <p>The forms are the same throughout: {@link Variant#DEFAULT} is the
 * ordinary reading, {@link Variant#ALTERNATE} the compact one (often a bare
 * figure, or the figure over its unit) and {@link Variant#DETAILED} the
 * reading with a word. A newline starts a second line.
 */
final class WidgetText {
    /** Looks up a string resource; the view backs it with {@code Resources}. */
    interface Words {
        String get(int id, Object... arguments);
    }

    private WidgetText() {}

    private static String timeOfDay(boolean h24) {
        return h24 ? "HH:mm" : "h:mm";
    }

    static String clock(Variant variant, boolean h24, Date date, Locale locale) {
        String pattern = variant == Variant.ALTERNATE ? (h24 ? "HH\nmm" : "h\nmm")
                : variant == Variant.DETAILED ? (h24 ? "HH:mm:ss" : "h:mm:ss")
                : timeOfDay(h24);
        return new SimpleDateFormat(pattern, locale).format(date);
    }

    static String date(Variant variant, Date date, Locale locale) {
        String pattern = variant == Variant.ALTERNATE ? "dd.MM"
                : variant == Variant.DETAILED ? "dd.MM.yyyy" : "d MMM";
        return new SimpleDateFormat(pattern, locale).format(date);
    }

    static String battery(Variant variant, int percent, boolean charging, Words words) {
        String value = words.get(charging
                ? R.string.dashboard_battery_charging : R.string.dashboard_battery, percent);
        if (variant == Variant.ALTERNATE) {
            return percent + "\n%";
        }
        if (variant == Variant.DETAILED) {
            // A word either way. With only the charging one to offer, the
            // detailed form was the ordinary form whenever the phone was
            // off the charger, which is most of the time.
            return value + "\n" + words.get(charging
                    ? R.string.dashboard_variant_charging : R.string.dashboard_variant_battery);
        }
        return value;
    }

    /** @param degrees already converted to the units the panel reads in */
    static String temperature(Variant variant, float degrees, String unitName,
            Locale locale, Words words) {
        return variant == Variant.ALTERNATE ? Math.round(degrees) + "°"
                : variant == Variant.DETAILED
                ? String.format(locale, "%.1f %s", degrees, unitName)
                : words.get(R.string.dashboard_device_temperature, degrees);
    }

    static String alarm(Variant variant, boolean h24, long triggerMillis, Locale locale) {
        String time = timeOfDay(h24);
        String pattern = variant == Variant.ALTERNATE ? time.replace(":", "\n")
                : variant == Variant.DETAILED ? "dd.MM\n" + time : time;
        return new SimpleDateFormat(pattern, locale).format(new Date(triggerMillis));
    }

    static String compass(Variant variant, float heading, String direction) {
        return variant == Variant.ALTERNATE ? direction
                : variant == Variant.DETAILED ? Math.round(heading) + "°\n" + direction
                : Math.round(heading) + "° " + direction;
    }

    static String speed(Variant variant, float metersPerSecond, boolean imperial, Words words) {
        int speed = Math.round(metersPerSecond * (imperial ? 2.236936f : 3.6f));
        return variant == Variant.ALTERNATE ? String.valueOf(speed)
                : variant == Variant.DETAILED
                ? speed + "\n" + words.get(imperial
                        ? R.string.dashboard_speed_unit_imperial : R.string.dashboard_speed_unit)
                : words.get(imperial
                        ? R.string.dashboard_speed_short_imperial
                        : R.string.dashboard_speed_short, speed);
    }

    static String altitude(Variant variant, double meters, boolean imperial, Words words) {
        long altitude = Math.round(meters * (imperial ? 3.280840d : 1d));
        return variant == Variant.ALTERNATE ? String.valueOf(altitude)
                : variant == Variant.DETAILED
                ? altitude + "\n" + words.get(imperial
                        ? R.string.dashboard_altitude_unit_imperial
                        : R.string.dashboard_altitude_unit)
                : words.get(imperial
                        ? R.string.dashboard_altitude_short_imperial
                        : R.string.dashboard_altitude_short, altitude);
    }

    /** The session timer and the timer widget read the same way. */
    static String elapsed(Variant variant, long millis) {
        if (variant == Variant.ALTERNATE) {
            // Stacked, the way the session timer's compact form reads.
            return formatElapsed(millis).replace(":", "\n");
        }
        return variant == Variant.DETAILED ? formatElapsedDetailed(millis) : formatElapsed(millis);
    }

    static String formatElapsed(long elapsedMillis) {
        long totalSeconds = Math.max(0L, elapsedMillis / 1_000L);
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    static String formatElapsedDetailed(long elapsedMillis) {
        long totalSeconds = Math.max(0L, elapsedMillis / 1_000L);
        return String.format(Locale.ROOT, "%d:%02d:%02d", totalSeconds / 3_600L,
                (totalSeconds % 3_600L) / 60L, totalSeconds % 60L);
    }

    /** Visual alternatives for text whose content is supplied by the user or system. */
    static String text(Variant variant, String value, Locale locale) {
        if (variant == Variant.ALTERNATE) {
            return value.toUpperCase(locale);
        }
        if (variant != Variant.DETAILED || value.indexOf(' ') < 0) {
            return value;
        }
        int middle = value.length() / 2;
        int before = value.lastIndexOf(' ', middle);
        int after = value.indexOf(' ', middle);
        int split = before < 0 ? after : after < 0 || middle - before <= after - middle
                ? before : after;
        return split > 0 ? value.substring(0, split) + "\n" + value.substring(split + 1) : value;
    }

    static String percent(int percent, Variant variant, String detail) {
        if (variant == Variant.ALTERNATE) return String.valueOf(percent);
        if (variant == Variant.DETAILED) return percent + "%\n" + detail;
        return percent + "%";
    }

    static String notifications(Variant variant, int count, Words words) {
        // Capped at nine rather than ninety-nine: the compact form is
        // there to hold its width on a 126 pixel panel, and a cap that
        // only bit past a hundred unread never did.
        return variant == Variant.ALTERNATE ? (count > 9 ? "9+" : String.valueOf(count))
                : variant == Variant.DETAILED
                ? count + "\n" + words.get(R.string.dashboard_variant_notifications)
                : String.valueOf(count);
    }

    /**
     * Three ways to say the same arrival, from least to most told. ALTERNATE
     * names only the app on purpose: the panel points away from its owner, and
     * "Telegram" answers "is it worth picking the phone up" without putting
     * the message in front of the room.
     */
    static String lastNotification(Variant variant, String app, String title, String text) {
        if (variant == Variant.ALTERNATE) {
            return app.isEmpty() ? title : app;
        }
        if (variant == Variant.DETAILED) {
            String head = app.isEmpty() ? title : title.isEmpty() ? app : app + " · " + title;
            return text.isEmpty() ? head : head.isEmpty() ? text : head + "\n" + text;
        }
        return title.isEmpty() ? app.isEmpty() ? text : app : title;
    }

    static String calendar(Variant variant, boolean h24, long startMillis, String title,
            Locale locale) {
        String value = new SimpleDateFormat(timeOfDay(h24), locale).format(new Date(startMillis));
        if (!title.isEmpty()) {
            value += "  " + title;
        }
        if (variant == Variant.ALTERNATE && !title.isEmpty()) {
            return title;
        }
        if (variant == Variant.DETAILED && !title.isEmpty()) {
            return value.replace("  ", "\n");
        }
        return value;
    }

    /**
     * Stacked on the thousands in the compact form, which is what compact
     * means everywhere else here. The separator is whatever the locale groups
     * with, so it is found by keeping the digits rather than by naming it.
     * Not \D, which counts only nought to nine: Persian and Arabic number in
     * their own digits, and the whole count came back as empty lines.
     */
    static String steps(Variant variant, int steps, Locale locale, Words words) {
        return variant == Variant.ALTERNATE
                ? String.format(locale, "%,d", steps).replaceAll("[^\\p{N}]", "\n")
                : variant == Variant.DETAILED
                ? steps + "\n" + words.get(R.string.dashboard_variant_steps)
                : String.valueOf(steps);
    }

    /**
     * The roll without a sign: the icon's line already leans the way to turn,
     * and a minus sign costs width on a narrow panel.
     */
    static String level(Variant variant, float rollDegrees, float pitchDegrees,
            Locale locale, Words words) {
        float roll = Math.abs(rollDegrees);
        String value = variant == Variant.ALTERNATE
                ? String.format(locale, "%.1f°", roll)
                : Math.round(roll) + "°";
        if (variant == Variant.DETAILED) {
            value += "\n" + words.get(R.string.dashboard_level_tilt, Math.round(pitchDegrees));
        }
        return value;
    }

    // Time and duration

    /** A time of day in the zone given, or the phone's own when none is. */
    static String timeOfDay(long millis, boolean h24, Locale locale, java.util.TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat(timeOfDay(h24), locale);
        if (zone != null) {
            format.setTimeZone(zone);
        }
        return format.format(new Date(millis));
    }

    /**
     * A length of time in words, "18 h 13 min". Not "18:13", which read as a
     * time of day beside the clock. Rounded up, so the last minute reads
     * 1 min and not nothing.
     */
    static String duration(long millis, Words words) {
        long minutes = Math.max(0L, (millis + 59_999L) / 60_000L);
        return minutes >= 60L
                ? words.get(R.string.dashboard_duration_hours, minutes / 60L, minutes % 60L)
                : words.get(R.string.dashboard_duration_minutes, minutes);
    }

    // Weather and media

    /**
     * The weather as a widget's variant asks for it. {@code line} is the
     * ordinary reading ("12°", a dash, an ellipsis while loading); the others
     * need the temperature and the place, and fall back to it without them.
     *
     * @param degrees the temperature already in the panel's units, or null
     */
    static String weather(Variant variant, String line, Integer degrees, String unitName,
            String place) {
        if (line.isEmpty()) {
            return line;
        }
        if (variant == Variant.ALTERNATE && degrees != null) {
            return degrees + "\n" + unitName;
        }
        if (variant == Variant.DETAILED && !place.isEmpty()) {
            return line + "\n" + place;
        }
        return line;
    }

    static String media(Variant variant, String title, String artist) {
        if (variant == Variant.ALTERNATE && !artist.isEmpty()) {
            return artist;
        }
        if (variant == Variant.DETAILED && !title.isEmpty() && !artist.isEmpty()) {
            return title + "\n" + artist;
        }
        return title.isEmpty() ? artist : title;
    }

    // Charging, sun, sound, world clock

    /**
     * Power first, since it is what says whether the charger is a fast one,
     * then how long is left. Compact stacks the two; detailed adds the current
     * and when the battery will be full, as a time of day.
     */
    static String charge(Variant variant, ChargeReading charge, long nowMillis, boolean h24,
            Locale locale, Words words) {
        if (charge == null) {
            return "";
        }
        if (charge.full) {
            return words.get(R.string.dashboard_charge_full);
        }
        float watts = charge.powerMilliwatts / 1_000f;
        String power = words.get(R.string.dashboard_charge_watts,
                watts < 10f ? String.format(locale, "%.1f", watts)
                        : String.valueOf(Math.round(watts)));
        boolean known = charge.fullInMillis > 0L;
        String left = known ? duration(charge.fullInMillis, words) : "";
        if (variant == Variant.ALTERNATE) {
            return known ? power + "\n" + left : power;
        }
        if (variant == Variant.DETAILED) {
            String amps = words.get(R.string.dashboard_charge_amps,
                    String.format(locale, "%.1f", charge.currentMilliamps / 1_000f));
            String head = power + " · " + amps;
            return known ? head + "\n" + words.get(R.string.dashboard_charge_full_at,
                    timeOfDay(nowMillis + charge.fullInMillis, h24, locale, null))
                    : head;
        }
        return known ? power + " · " + left : power;
    }

    /**
     * The next sunrise or sunset, which the icon tells apart. Compact gives
     * the day's pair, sunrise over sunset; detailed says how long until the
     * next one.
     */
    static String sun(Variant variant, SunTimes.Next sun, long nowMillis, boolean h24,
            Locale locale, Words words) {
        if (sun == null) {
            return "";
        }
        String next = timeOfDay(sun.eventMillis(), h24, locale, null);
        if (variant == Variant.ALTERNATE
                && sun.sunriseMillis != SunTimes.NONE && sun.sunsetMillis != SunTimes.NONE) {
            return timeOfDay(sun.sunriseMillis, h24, locale, null) + "\n"
                    + timeOfDay(sun.sunsetMillis, h24, locale, null);
        }
        if (variant == Variant.DETAILED) {
            String wait = duration(sun.eventMillis() - nowMillis, words);
            return next + "\n" + words.get(sun.sunriseNext
                    ? R.string.dashboard_sun_sunrise_in
                    : R.string.dashboard_sun_sunset_in, wait);
        }
        return next;
    }

    /**
     * The mode in a word. Compact gives the ring volume while the phone
     * rings, and the word otherwise, since the volume of a phone that will
     * not ring says nothing; detailed gives both.
     */
    static String sound(Variant variant, SoundModeReading sound, Words words) {
        if (sound == null) {
            return "";
        }
        int word;
        switch (sound.mode) {
            case VIBRATE: word = R.string.dashboard_sound_vibrate; break;
            case SILENT: word = R.string.dashboard_sound_silent; break;
            case DO_NOT_DISTURB: word = R.string.dashboard_sound_dnd; break;
            default: word = R.string.dashboard_sound_on; break;
        }
        String name = words.get(word);
        if (variant == Variant.ALTERNATE) {
            return sound.mode == SoundModeReading.Mode.SOUND ? sound.volumePercent + "%" : name;
        }
        if (variant == Variant.DETAILED) {
            return name + "\n" + words.get(R.string.dashboard_sound_volume, sound.volumePercent);
        }
        return name;
    }

    /**
     * The time there and where there is. Compact is the time alone; detailed
     * puts the city on its own line with how far ahead or behind it is.
     */
    static String worldClock(Variant variant, java.util.TimeZone zone, String city,
            long nowMillis, boolean h24, Locale locale, Words words) {
        String time = timeOfDay(nowMillis, h24, locale, zone);
        if (variant == Variant.ALTERNATE) {
            return time;
        }
        if (variant == Variant.DETAILED) {
            int minutes = (zone.getOffset(nowMillis)
                    - java.util.TimeZone.getDefault().getOffset(nowMillis)) / 60_000;
            return time + "\n" + words.get(R.string.dashboard_world_clock_offset,
                    city, WorldClock.differenceLabel(minutes));
        }
        return time + "  " + city;
    }

    // The small widgets

    private static final int[] UV_BANDS = {R.string.dashboard_uv_low,
            R.string.dashboard_uv_moderate, R.string.dashboard_uv_high,
            R.string.dashboard_uv_very_high, R.string.dashboard_uv_extreme};
    private static final int[] AIR_BANDS = {R.string.dashboard_air_good,
            R.string.dashboard_air_fair, R.string.dashboard_air_moderate,
            R.string.dashboard_air_poor, R.string.dashboard_air_very_poor,
            R.string.dashboard_air_extremely_poor};
    private static final int[] MOON_PHASES = {R.string.dashboard_moon_new,
            R.string.dashboard_moon_waxing_crescent, R.string.dashboard_moon_first_quarter,
            R.string.dashboard_moon_waxing_gibbous, R.string.dashboard_moon_full,
            R.string.dashboard_moon_waning_gibbous, R.string.dashboard_moon_last_quarter,
            R.string.dashboard_moon_waning_crescent};
    private static final int[] PERIOD_NAMES = {R.string.dashboard_progress_day,
            R.string.dashboard_progress_week, R.string.dashboard_progress_month,
            R.string.dashboard_progress_year};

    static String uv(Variant variant, float index, Words words) {
        String figure = String.valueOf(Math.round(index));
        String labelled = words.get(R.string.dashboard_uv_value, figure);
        return variant == Variant.ALTERNATE ? figure
                : variant == Variant.DETAILED
                ? labelled + "\n" + words.get(UV_BANDS[WidgetMath.uvBand(index)])
                : labelled;
    }

    static String air(Variant variant, int index, Words words) {
        String figure = String.valueOf(index);
        String labelled = words.get(R.string.dashboard_air_value, figure);
        return variant == Variant.ALTERNATE ? figure
                : variant == Variant.DETAILED
                ? labelled + "\n" + words.get(AIR_BANDS[WidgetMath.airBand(index)])
                : labelled;
    }

    /**
     * @param down the downstream rate as a size, "1.2 MB"
     * @param up the upstream rate as a size
     */
    static String networkSpeed(Variant variant, String down, String up, Words words) {
        return variant == Variant.ALTERNATE
                ? "↓ " + down + "\n↑ " + up
                : variant == Variant.DETAILED
                ? "↓ " + words.get(R.string.dashboard_per_second, down)
                        + "\n↑ " + words.get(R.string.dashboard_per_second, up)
                : "↓ " + words.get(R.string.dashboard_per_second, down);
    }

    static String moon(Variant variant, long nowMillis, Words words) {
        double age = WidgetMath.moonAge(nowMillis);
        String name = words.get(MOON_PHASES[WidgetMath.moonPhase(age)]);
        String lit = Math.round(WidgetMath.moonIllumination(age) * 100d) + "%";
        return variant == Variant.ALTERNATE ? lit
                : variant == Variant.DETAILED ? name + "\n" + lit
                : name;
    }

    /** @param targetDay days since the epoch, as the countdown stores it */
    static String countdown(Variant variant, long nowMillis, java.util.TimeZone zone,
            long targetDay, String label, Locale locale, Words words) {
        long days = WidgetMath.daysUntil(nowMillis, zone, targetDay);
        String count = days == 0L ? words.get(R.string.dashboard_countdown_today)
                : days > 0L ? words.get(R.string.dashboard_countdown_days, days)
                : words.get(R.string.dashboard_countdown_ago, -days);
        if (variant == Variant.ALTERNATE) {
            return days == 0L ? count : String.valueOf(Math.abs(days));
        }
        if (variant == Variant.DETAILED) {
            long midnight = targetDay * 86_400_000L;
            String when = label.isEmpty()
                    ? java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale)
                            .format(new Date(midnight - zone.getOffset(midnight)))
                    : label;
            return count + "\n" + when;
        }
        return label.isEmpty() ? count : label + " · " + count;
    }

    static String progress(Variant variant, WidgetMath.Period period, float fraction,
            long millisLeft, Words words) {
        String percent = Math.round(fraction * 100f) + "%";
        if (variant == Variant.ALTERNATE) {
            return percent;
        }
        if (variant == Variant.DETAILED) {
            // Days past two of them: "2345 h" says nothing about a year.
            String remaining = millisLeft > 48L * 3_600_000L
                    ? words.get(R.string.dashboard_countdown_days,
                            (millisLeft + 86_399_999L) / 86_400_000L)
                    : duration(millisLeft, words);
            return percent + "\n" + words.get(R.string.dashboard_progress_left, remaining);
        }
        return words.get(PERIOD_NAMES[period.ordinal()]) + " " + percent;
    }

    static String pressure(Variant variant, float hpa, int trend, boolean imperial,
            Locale locale, Words words) {
        String main = imperial
                ? words.get(R.string.dashboard_pressure_inhg,
                        String.format(locale, "%.2f", WidgetMath.toInchesOfMercury(hpa)))
                : words.get(R.string.dashboard_pressure_hpa, Math.round(hpa));
        if (variant == Variant.ALTERNATE) {
            return words.get(R.string.dashboard_pressure_mmhg,
                    Math.round(WidgetMath.toMillimetresOfMercury(hpa)));
        }
        if (variant == Variant.DETAILED) {
            return main + "\n" + words.get(trend > 0 ? R.string.dashboard_pressure_rising
                    : trend < 0 ? R.string.dashboard_pressure_falling
                    : R.string.dashboard_pressure_steady);
        }
        return main;
    }
}
