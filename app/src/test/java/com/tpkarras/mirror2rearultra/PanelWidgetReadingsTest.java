package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The arithmetic behind the charging, sunrise and horizon widgets. */
public class PanelWidgetReadingsTest {
    private static final float G = 9.81f;

    // Charging

    @Test
    public void chargingCurrentCountsWhicheverWayItIsSigned() {
        // As this phone reported it on a computer's USB port: -189 mA at 3.864 V.
        ChargeReading negative = ChargeReading.of(-189_000, 3_864, -1L, 1_431_530, 37, false);
        ChargeReading positive = ChargeReading.of(189_000, 3_864, -1L, 1_431_530, 37, false);
        assertEquals(189, negative.currentMilliamps);
        assertEquals(730, negative.powerMilliwatts);
        assertEquals(negative.powerMilliwatts, positive.powerMilliwatts);
    }

    @Test
    public void theSystemsOwnEstimateWins() {
        ChargeReading reading = ChargeReading.of(-2_000_000, 4_000, 1_800_000L, 2_000_000, 50, false);
        assertEquals(1_800_000L, reading.fullInMillis);
    }

    @Test
    public void withoutOneTheGapIsDividedByTheCurrent() {
        // Half of 4000 mAh missing at 2 A is an hour.
        ChargeReading reading = ChargeReading.of(-2_000_000, 4_000, -1L, 2_000_000, 50, false);
        assertEquals(3_600_000L, reading.fullInMillis);
    }

    @Test
    public void aTrickleGivesNoEstimateRatherThanDays() {
        assertEquals(ChargeReading.UNKNOWN, ChargeReading.estimateMillis(20_000L, 2_000_000, 50));
    }

    @Test
    public void aFullBatteryHasNothingLeftToGo() {
        ChargeReading reading = ChargeReading.of(0, 4_400, -1L, 3_869_000, 100, true);
        assertTrue(reading.full);
        assertEquals(0L, reading.fullInMillis);
    }

    // Sunrise and sunset

    @Test
    public void localTimesBecomeInstantsThroughTheCitysOffset() {
        // 06:45 at UTC+5 is 01:45 UTC.
        long[] instants = SunTimes.toInstants(new String[]{"2026-09-27T06:45"}, 5 * 3_600);
        assertEquals(1_790_473_500_000L, instants[0]);
    }

    @Test
    public void anUnreadableTimeIsNoEvent() {
        long[] instants = SunTimes.toInstants(new String[]{"", null, "never"}, 0);
        assertEquals(SunTimes.NONE, instants[0]);
        assertEquals(SunTimes.NONE, instants[1]);
        assertEquals(SunTimes.NONE, instants[2]);
    }

    @Test
    public void beforeDawnTheSunriseIsNext() {
        SunTimes.Next next = SunTimes.next(50L, new long[]{100L, 1_100L}, new long[]{600L, 1_600L});
        assertNotNull(next);
        assertTrue(next.sunriseNext);
        assertEquals(100L, next.eventMillis());
    }

    @Test
    public void inDaylightTheSunsetIsNext() {
        SunTimes.Next next = SunTimes.next(300L, new long[]{100L, 1_100L}, new long[]{600L, 1_600L});
        assertNotNull(next);
        assertFalse(next.sunriseNext);
        assertEquals(600L, next.eventMillis());
    }

    @Test
    public void afterSunsetTomorrowsPairIsShown() {
        SunTimes.Next next = SunTimes.next(700L, new long[]{100L, 1_100L}, new long[]{600L, 1_600L});
        assertNotNull(next);
        assertTrue(next.sunriseNext);
        assertEquals(1_100L, next.sunriseMillis);
        assertEquals(1_600L, next.sunsetMillis);
    }

    @Test
    public void aPolarDaySkipsToTheSunsetThatExists() {
        long none = SunTimes.NONE;
        SunTimes.Next next = SunTimes.next(50L, new long[]{none}, new long[]{600L});
        assertNotNull(next);
        assertFalse(next.sunriseNext);
    }

    @Test
    public void pastTheLastDayOfTheForecastThereIsNothing() {
        assertNull(SunTimes.next(2_000L, new long[]{100L}, new long[]{600L}));
    }

    // Horizon

    @Test
    public void uprightIsLevel() {
        LevelReading reading = LevelReading.fromGravity(0f, G, 0f);
        assertNotNull(reading);
        assertEquals(0f, reading.rollDegrees, 0.01f);
        assertTrue(reading.isLevel());
    }

    @Test
    public void tippedClockwiseTheLineTurnsClockwiseOnThePanel() {
        // Turned 10 degrees clockwise as the main screen is read, gravity
        // leans towards negative X.
        double turn = Math.toRadians(10d);
        LevelReading reading = LevelReading.fromGravity(
                (float) (-G * Math.sin(turn)), (float) (G * Math.cos(turn)), 0f);
        assertNotNull(reading);
        assertEquals(10f, reading.rollDegrees, 0.01f);
        assertFalse(reading.isLevel());
    }

    @Test
    public void onItsSideItIsMeasuredFromTheSide() {
        // Landscape with the left edge down, then 3 degrees further round.
        double turn = Math.toRadians(-93d);
        LevelReading reading = LevelReading.fromGravity(
                (float) (-G * Math.sin(turn)), (float) (G * Math.cos(turn)), 0f);
        assertNotNull(reading);
        assertEquals(-3f, reading.rollDegrees, 0.01f);
        assertEquals(-93f, reading.turnDegrees, 0.01f);
    }

    @Test
    public void theLineTurnsSmoothlyThroughFortyFiveDegrees() {
        // The figure changes which square it counts from at 45 degrees; the
        // line's own angle must not jump there, or the icon flips a quarter
        // turn on a page drawn on its side.
        double before = Math.toRadians(44.5d);
        double after = Math.toRadians(45.5d);
        LevelReading first = LevelReading.fromGravity(
                (float) (-G * Math.sin(before)), (float) (G * Math.cos(before)), 0f);
        LevelReading second = LevelReading.fromGravity(
                (float) (-G * Math.sin(after)), (float) (G * Math.cos(after)), 0f);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(1f, second.turnDegrees - first.turnDegrees, 0.01f);
        assertEquals(44.5f, Math.abs(second.rollDegrees), 0.01f);
    }

    @Test
    public void leaningBackIsTiltNotRoll() {
        double lean = Math.toRadians(20d);
        LevelReading reading = LevelReading.fromGravity(
                0f, (float) (G * Math.cos(lean)), (float) (G * Math.sin(lean)));
        assertNotNull(reading);
        assertEquals(0f, reading.rollDegrees, 0.01f);
        assertEquals(20f, reading.pitchDegrees, 0.01f);
    }

    @Test
    public void lyingFlatHasNoHorizon() {
        assertNull(LevelReading.fromGravity(0.3f, 0.2f, G));
    }
}
