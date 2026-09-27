package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The barometer's readings over the last few hours, for the pressure
 * widget's trend: a rising or falling glass is the forecast a barometer
 * gives, and one reading cannot say which way it is going.
 *
 * <p>Samples are {time in milliseconds, tenths of a hectopascal}, stored as
 * "time:value" pairs separated by commas.
 */
final class PressureHistory {
    /** The trend compares with about three hours ago, the forecaster's measure. */
    private static final long LOOK_BACK_MILLIS = 3 * 3_600_000L;
    /** A sample this far from three hours ago still stands in for it. */
    private static final long LEEWAY_MILLIS = 45 * 60_000L;
    private static final long KEEP_MILLIS = 4 * 3_600_000L;

    static List<long[]> parse(String stored) {
        List<long[]> samples = new ArrayList<>();
        if (stored == null || stored.isEmpty()) {
            return samples;
        }
        for (String pair : stored.split(",")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) continue;
            try {
                samples.add(new long[]{Long.parseLong(pair.substring(0, colon)),
                        Long.parseLong(pair.substring(colon + 1))});
            } catch (NumberFormatException skipped) {
                // A damaged sample is dropped; the rest still serve.
            }
        }
        return samples;
    }

    static String format(List<long[]> samples) {
        StringBuilder text = new StringBuilder();
        for (long[] sample : samples) {
            if (text.length() > 0) text.append(',');
            text.append(sample[0]).append(':').append(sample[1]);
        }
        return text.toString();
    }

    /** Only what the trend could still use. */
    static List<long[]> trimmed(List<long[]> samples, long nowMillis) {
        List<long[]> kept = new ArrayList<>();
        for (long[] sample : samples) {
            if (nowMillis - sample[0] <= KEEP_MILLIS && sample[0] <= nowMillis) {
                kept.add(sample);
            }
        }
        return kept;
    }

    /**
     * The reading nearest three hours ago, in hectopascals, or null when none
     * came close enough - the first hours after the widget is switched on.
     */
    @Nullable
    static Float threeHoursBefore(List<long[]> samples, long nowMillis) {
        long target = nowMillis - LOOK_BACK_MILLIS;
        long[] best = null;
        for (long[] sample : samples) {
            long distance = Math.abs(sample[0] - target);
            if (distance <= LEEWAY_MILLIS && (best == null || distance < Math.abs(best[0] - target))) {
                best = sample;
            }
        }
        return best == null ? null : best[1] / 10f;
    }

    private PressureHistory() {}
}
