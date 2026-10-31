package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

/**
 * The readings the small widgets take from outside: the forecast's UV and
 * air, the network's traffic, the barometer. Null or -1 where there is none.
 *
 * <p>Kept together rather than as more arguments to the snapshot, which had
 * grown to thirty.
 */
final class ExtraReadings {
    static final ExtraReadings NONE = new ExtraReadings(null, null, -1L, -1L, null, 0);

    @Nullable final Float uvIndex;
    /** The European air quality index. */
    @Nullable final Integer airQuality;
    final long downBytesPerSecond;
    final long upBytesPerSecond;
    @Nullable final Float pressureHpa;
    /** 1 rising, -1 falling, 0 steady or not yet known. */
    final int pressureTrend;
    /** The weather has not been fetched for a long while, so its figures are old. */
    final boolean weatherStale;
    /** When the event shown ends, and how many follow it; -1 and 0 without one. */
    final long calendarEndMillis;
    final int calendarMore;
    /** A satellite fix was had and has gone quiet: not the same as standing still. */
    final boolean locationLost;

    ExtraReadings(@Nullable Float uvIndex, @Nullable Integer airQuality,
            long downBytesPerSecond, long upBytesPerSecond,
            @Nullable Float pressureHpa, int pressureTrend) {
        this(uvIndex, airQuality, downBytesPerSecond, upBytesPerSecond, pressureHpa,
                pressureTrend, false);
    }

    ExtraReadings(@Nullable Float uvIndex, @Nullable Integer airQuality,
            long downBytesPerSecond, long upBytesPerSecond,
            @Nullable Float pressureHpa, int pressureTrend, boolean weatherStale) {
        this(uvIndex, airQuality, downBytesPerSecond, upBytesPerSecond, pressureHpa,
                pressureTrend, weatherStale, -1L, 0, false);
    }

    ExtraReadings(@Nullable Float uvIndex, @Nullable Integer airQuality,
            long downBytesPerSecond, long upBytesPerSecond,
            @Nullable Float pressureHpa, int pressureTrend, boolean weatherStale,
            long calendarEndMillis, int calendarMore, boolean locationLost) {
        this.calendarEndMillis = calendarEndMillis;
        this.calendarMore = calendarMore;
        this.locationLost = locationLost;
        this.weatherStale = weatherStale;
        this.uvIndex = uvIndex;
        this.airQuality = airQuality;
        this.downBytesPerSecond = downBytesPerSecond;
        this.upBytesPerSecond = upBytesPerSecond;
        this.pressureHpa = pressureHpa;
        this.pressureTrend = pressureTrend;
    }
}
