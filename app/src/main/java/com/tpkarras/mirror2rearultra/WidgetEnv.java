package com.tpkarras.mirror2rearultra;

import android.content.Context;

import com.tpkarras.mirror2rearultra.DashboardWidgetLayout.Variant;
import com.tpkarras.mirror2rearultra.DashboardWidgetLayout.Widget;

import java.util.Locale;
import java.util.Map;

/**
 * Everything a {@link WidgetSpec} may look at to make its reading: the
 * snapshot, the settings, and the phone's way of writing times and units.
 *
 * <p>Built once per frame by the view, so a spec asks the locale and the
 * clock format once rather than once each.
 */
final class WidgetEnv {
    final Context context;
    final RearDashboardSnapshot snapshot;
    final DashboardSettings settings;
    final Locale locale = Locale.getDefault();
    final boolean h24;
    final boolean imperial;
    final WidgetText.Words words;
    /** Zone ids to city names in the panel's language, which ICU looks up slowly. */
    private final Map<String, String> cityNames;

    WidgetEnv(Context context, RearDashboardSnapshot snapshot, DashboardSettings settings,
            WidgetText.Words words, Map<String, String> cityNames) {
        this.context = context;
        this.snapshot = snapshot;
        this.settings = settings;
        this.words = words;
        this.cityNames = cityNames;
        this.h24 = android.text.format.DateFormat.is24HourFormat(context);
        this.imperial = DashboardWidgetLayout.isImperialUnits(context);
    }

    long now() {
        return snapshot.timestampMillis;
    }

    Variant variant(Widget widget) {
        return DashboardWidgetLayout.loadVariant(context, widget);
    }

    /** A reading in whichever degrees the panel has been told to show. */
    int degrees(int celsius) {
        return imperial ? Math.round(celsius * 9f / 5f + 32f) : celsius;
    }

    float degrees(float celsius) {
        return imperial ? celsius * 9f / 5f + 32f : celsius;
    }

    /** What to call them, for the one reading that spells the unit out. */
    String degreeName() {
        return imperial ? "°F" : "°C";
    }

    static String cardinal(float degrees) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = Math.round(degrees / 45f) % directions.length;
        return directions[index];
    }

    /**
     * The weather's ordinary line: the temperature, an ellipsis while it is
     * being fetched, a dash when it never was. A tilde marks a reading the
     * fetch has failed to renew for a long while.
     */
    String weatherLine() {
        if (snapshot.weatherTemperatureCelsius != null && snapshot.weatherCode != null) {
            String reading = words.get(R.string.dashboard_weather_short,
                    degrees(snapshot.weatherTemperatureCelsius));
            return snapshot.extra.weatherStale ? "~" + reading : reading;
        }
        if (snapshot.weatherLoading) {
            return "…";
        }
        return "—";
    }

    /**
     * The weather as a widget's variant asks for it. Shared with the
     * full-screen weather widget, which read no variant at all: all three of
     * its choices drew the same line, while the row offering them said
     * otherwise.
     */
    String weather(Widget widget) {
        return WidgetText.weather(variant(widget), weatherLine(),
                snapshot.weatherTemperatureCelsius == null ? null
                        : degrees(snapshot.weatherTemperatureCelsius),
                degreeName(), snapshot.weatherPlace);
    }

    boolean hasMedia() {
        return !snapshot.mediaTitle.isEmpty() || !snapshot.mediaArtist.isEmpty();
    }

    /**
     * What is playing, as a widget's variant asks for it. Shared with the
     * full-screen media widget for the same reason: that one also showed the
     * title and nothing else, so a station that names only its artist left it
     * blank.
     */
    String media(Widget widget) {
        return WidgetText.media(variant(widget), snapshot.mediaTitle, snapshot.mediaArtist);
    }

    String cityName(String zoneId) {
        String key = locale.toLanguageTag() + "|" + zoneId;
        String cached = cityNames.get(key);
        if (cached != null) {
            return cached;
        }
        String name = null;
        try {
            name = android.icu.text.TimeZoneNames.getInstance(locale)
                    .getExemplarLocationName(zoneId);
        } catch (RuntimeException ignored) {
            // Falls back to the zone's own name below.
        }
        if (name == null || name.isEmpty()) {
            name = WorldClock.fallbackName(zoneId);
        }
        cityNames.put(key, name);
        return name;
    }
}
