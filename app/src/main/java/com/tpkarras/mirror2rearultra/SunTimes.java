package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Sunrise and sunset for the weather city, and which of them comes next.
 *
 * <p>The forecast gives them as the city's own wall-clock time, with the
 * city's offset from UTC alongside. Turned into instants here, so the panel
 * shows them in the phone's time and a city in another zone still reads right.
 */
final class SunTimes {
    /** No such event: missing from the forecast, or a polar day or night. */
    static final long NONE = -1L;

    /** The day the next event falls on. */
    static final class Next {
        final long sunriseMillis;
        final long sunsetMillis;
        final boolean sunriseNext;

        Next(long sunriseMillis, long sunsetMillis, boolean sunriseNext) {
            this.sunriseMillis = sunriseMillis;
            this.sunsetMillis = sunsetMillis;
            this.sunriseNext = sunriseNext;
        }

        long eventMillis() {
            return sunriseNext ? sunriseMillis : sunsetMillis;
        }
    }

    /**
     * @param localTimes as Open-Meteo sends them, {@code 2026-09-27T06:45}
     * @param utcOffsetSeconds the city's offset, from the same response
     */
    static long[] toInstants(String[] localTimes, int utcOffsetSeconds) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        long[] instants = new long[localTimes.length];
        for (int index = 0; index < localTimes.length; index++) {
            String value = localTimes[index];
            try {
                instants[index] = value == null || value.isEmpty()
                        ? NONE
                        : format.parse(value).getTime() - utcOffsetSeconds * 1_000L;
            } catch (ParseException error) {
                instants[index] = NONE;
            }
        }
        return instants;
    }

    /**
     * The first sunrise or sunset still ahead, with the other half of its day.
     *
     * <p>After sunset that is tomorrow's sunrise, and the pair shown with it
     * is tomorrow's: today's are both behind by then.
     */
    @Nullable
    static Next next(long nowMillis, long[] sunrises, long[] sunsets) {
        int days = Math.min(sunrises.length, sunsets.length);
        for (int day = 0; day < days; day++) {
            if (sunrises[day] != NONE && nowMillis < sunrises[day]) {
                return new Next(sunrises[day], sunsets[day], true);
            }
            if (sunsets[day] != NONE && nowMillis < sunsets[day]) {
                return new Next(sunrises[day], sunsets[day], false);
            }
        }
        return null;
    }

    private SunTimes() {}
}
