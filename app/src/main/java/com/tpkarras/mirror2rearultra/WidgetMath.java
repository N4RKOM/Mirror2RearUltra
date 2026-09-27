package com.tpkarras.mirror2rearultra;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The arithmetic behind the small widgets that work something out rather
 * than read it: the moon, a countdown, how far through the day, pressure's
 * trend, and which band a UV or air reading falls in.
 */
final class WidgetMath {
    /**
     * How far round the moon is from new, 0 up to 1: its elongation from the
     * sun over a full turn.
     *
     * <p>Meeus's short series (Astronomical Algorithms, chapter 48) rather
     * than counting mean lunations. The moon's orbit is far enough from round
     * that a mean month puts a quarter most of a day out, which read as 44%
     * lit on the evening of a first quarter.
     */
    static double moonAge(long nowMillis) {
        double julianDay = nowMillis / 86_400_000d + 2_440_587.5d;
        double centuries = (julianDay - 2_451_545d) / 36_525d;
        double elongation = Math.toRadians(297.8501921d + 445_267.1114034d * centuries);
        double sunAnomaly = Math.toRadians(357.5291092d + 35_999.0502909d * centuries);
        double moonAnomaly = Math.toRadians(134.9633964d + 477_198.8675055d * centuries);
        // The phase angle, 180 at new and 0 at full.
        double phaseAngle = 180d - Math.toDegrees(elongation)
                - 6.289d * Math.sin(moonAnomaly)
                + 2.100d * Math.sin(sunAnomaly)
                - 1.274d * Math.sin(2d * elongation - moonAnomaly)
                - 0.658d * Math.sin(2d * elongation)
                - 0.214d * Math.sin(2d * moonAnomaly)
                - 0.110d * Math.sin(elongation);
        double age = ((180d - phaseAngle) % 360d) / 360d;
        return age < 0d ? age + 1d : age;
    }

    /** How much of the moon is lit, 0 to 1. */
    static double moonIllumination(double age) {
        return (1d - Math.cos(2d * Math.PI * age)) / 2d;
    }

    /**
     * Which of the eight phases, from 0 for new round to 7 for waning
     * crescent. New, the quarters and full get a sixteenth either side, the
     * way a calendar names them on the day rather than at the instant.
     */
    static int moonPhase(double age) {
        return (int) Math.floor(age * 8d + 0.5d) % 8;
    }

    /**
     * Whole days from today to a date, by the calendar rather than by the
     * clock: tomorrow is 1 at any hour of today.
     *
     * @param targetDay days since the epoch, as the countdown stores it
     */
    static long daysUntil(long nowMillis, TimeZone zone, long targetDay) {
        return targetDay - dayNumber(nowMillis, zone);
    }

    /** Days since the epoch of the day this instant falls on, where the phone is. */
    static long dayNumber(long millis, TimeZone zone) {
        return Math.floorDiv(millis + zone.getOffset(millis), 86_400_000L);
    }

    /** The periods the progress widget can follow. */
    enum Period { DAY, WEEK, MONTH, YEAR }

    /**
     * Where the period in progress started and ends, for the progress widget.
     * A week starts on the day the phone's locale starts it.
     *
     * @return {start, end} in milliseconds
     */
    static long[] periodBounds(long nowMillis, Period period, Calendar template) {
        Calendar start = (Calendar) template.clone();
        start.setTimeInMillis(nowMillis);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        switch (period) {
            case WEEK:
                int back = (start.get(Calendar.DAY_OF_WEEK) - start.getFirstDayOfWeek() + 7) % 7;
                start.add(Calendar.DAY_OF_MONTH, -back);
                end = (Calendar) start.clone();
                end.add(Calendar.DAY_OF_MONTH, 7);
                break;
            case MONTH:
                start.set(Calendar.DAY_OF_MONTH, 1);
                end = (Calendar) start.clone();
                end.add(Calendar.MONTH, 1);
                break;
            case YEAR:
                start.set(Calendar.DAY_OF_YEAR, 1);
                end = (Calendar) start.clone();
                end.add(Calendar.YEAR, 1);
                break;
            case DAY:
            default:
                end.add(Calendar.DAY_OF_MONTH, 1);
                break;
        }
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    /** How far through, 0 to 1. */
    static double fraction(long nowMillis, long[] bounds) {
        long length = Math.max(1L, bounds[1] - bounds[0]);
        return Math.max(0d, Math.min(1d, (nowMillis - bounds[0]) / (double) length));
    }

    /**
     * Rising, falling or steady, from the change over the last few hours:
     * 1, -1 or 0. A hectopascal either way is steady - the sensor drifts that
     * much with the weather standing still.
     */
    static int pressureTrend(float nowHpa, float earlierHpa) {
        float change = nowHpa - earlierHpa;
        return change > 1f ? 1 : change < -1f ? -1 : 0;
    }

    /** Hectopascals to millimetres of mercury, as a Russian forecast gives it. */
    static float toMillimetresOfMercury(float hpa) {
        return hpa * 0.750062f;
    }

    /** Hectopascals to inches of mercury, for imperial units. */
    static float toInchesOfMercury(float hpa) {
        return hpa * 0.02953f;
    }

    /** The WHO band of a UV index: 0 low, 1 moderate, 2 high, 3 very high, 4 extreme. */
    static int uvBand(float index) {
        return index < 3f ? 0 : index < 6f ? 1 : index < 8f ? 2 : index < 11f ? 3 : 4;
    }

    /**
     * The European air quality index's band: 0 good, 1 fair, 2 moderate,
     * 3 poor, 4 very poor, 5 extremely poor.
     */
    static int airBand(int index) {
        return index <= 20 ? 0 : index <= 40 ? 1 : index <= 60 ? 2
                : index <= 80 ? 3 : index <= 100 ? 4 : 5;
    }

    private WidgetMath() {}
}
