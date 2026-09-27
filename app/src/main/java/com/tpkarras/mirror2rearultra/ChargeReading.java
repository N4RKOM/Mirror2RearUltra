package com.tpkarras.mirror2rearultra;

/**
 * What the charger is doing, for the charging widget.
 *
 * <p>Pure arithmetic over what {@link android.os.BatteryManager} reports, so
 * it can be tested off the device.
 */
final class ChargeReading {
    /** Unknown: the system gave no estimate and there was nothing to work one out from. */
    static final long UNKNOWN = -1L;

    /**
     * Below this the current is noise, or a charger that cannot keep up with
     * the phone, and dividing by it gives an estimate of days.
     */
    private static final int MIN_ESTIMATE_MILLIAMPS = 50;

    /** Into the battery, however the kernel signs it. */
    final int currentMilliamps;
    /** Current times the battery's voltage: what reaches the cell, not what the charger draws. */
    final int powerMilliwatts;
    /** How long until full, or {@link #UNKNOWN}. */
    final long fullInMillis;
    final boolean full;

    ChargeReading(int currentMilliamps, int powerMilliwatts, long fullInMillis, boolean full) {
        this.currentMilliamps = currentMilliamps;
        this.powerMilliwatts = powerMilliwatts;
        this.fullInMillis = fullInMillis;
        this.full = full;
    }

    /**
     * @param currentMicroamps {@code BATTERY_PROPERTY_CURRENT_NOW}. This phone
     *     reports charging as negative; others report it as positive, so only
     *     the size is used.
     * @param systemEstimateMillis {@code computeChargeTimeRemaining()}, which
     *     is -1 whenever the system has not made up its mind
     * @param chargeCounterMicroampHours {@code BATTERY_PROPERTY_CHARGE_COUNTER},
     *     what is in the battery now
     */
    static ChargeReading of(int currentMicroamps, int voltageMillivolts,
            long systemEstimateMillis, int chargeCounterMicroampHours, int percent,
            boolean full) {
        long microamps = Math.abs((long) currentMicroamps);
        int milliamps = (int) (microamps / 1_000L);
        int milliwatts = voltageMillivolts > 0
                ? (int) (microamps * voltageMillivolts / 1_000_000L) : 0;
        long fullIn;
        if (full) {
            fullIn = 0L;
        } else if (systemEstimateMillis > 0L) {
            fullIn = systemEstimateMillis;
        } else {
            fullIn = estimateMillis(microamps, chargeCounterMicroampHours, percent);
        }
        return new ChargeReading(milliamps, milliwatts, fullIn, full);
    }

    /**
     * What is missing, divided by what is going in.
     *
     * <p>The capacity comes from the counter and the percentage together,
     * since the design capacity is not offered and an aged battery holds less
     * than it anyway. Charging slows over the last fifth, so near the top this
     * comes out short; it corrects itself as the current falls.
     */
    static long estimateMillis(long currentMicroamps, int chargeCounterMicroampHours, int percent) {
        if (currentMicroamps < MIN_ESTIMATE_MILLIAMPS * 1_000L
                || chargeCounterMicroampHours <= 0 || percent <= 0 || percent >= 100) {
            return UNKNOWN;
        }
        long capacity = chargeCounterMicroampHours * 100L / percent;
        long missing = Math.max(0L, capacity - chargeCounterMicroampHours);
        return missing * 3_600_000L / currentMicroamps;
    }
}
