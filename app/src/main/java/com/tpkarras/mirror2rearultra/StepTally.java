package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

/**
 * Today's steps from a counter that only counts since the phone started.
 *
 * <p>The sensor's total starts again from nought at every boot. Measuring
 * from a base taken at midnight, a restart in the afternoon threw the morning
 * away: the base was reset to the new, small total and the count began again
 * from nothing. The steps counted so far today are carried across the restart
 * instead, and the new total adds to them.
 */
final class StepTally {
    final String day;
    /** The sensor's total when today began, or when the counter last started over. */
    final int base;
    /** Steps counted today before the counter last started over. */
    final int carried;
    /** The total at the last reading, to tell a restart from a long walk. */
    final int lastTotal;
    final int today;

    StepTally(String day, int base, int carried, int lastTotal, int today) {
        this.day = day;
        this.base = base;
        this.carried = carried;
        this.lastTotal = lastTotal;
        this.today = today;
    }

    /**
     * @param previous what was stored after the last reading, or null for none
     * @param day today's date as a string
     * @param total the sensor's count since the phone started
     */
    static StepTally update(@Nullable StepTally previous, String day, int total) {
        if (previous == null || !previous.day.equals(day)) {
            return new StepTally(day, total, 0, total, 0);
        }
        if (total < previous.lastTotal) {
            // The counter started over: what it counted before is in `today`.
            return new StepTally(day, 0, previous.today, total, previous.today + total);
        }
        int walked = Math.max(0, total - previous.base);
        return new StepTally(day, previous.base, previous.carried, total,
                previous.carried + walked);
    }
}
