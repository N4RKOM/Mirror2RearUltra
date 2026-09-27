package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** The small widgets' arithmetic. */
public class WidgetMathTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void aKnownFullMoonIsFull() {
        // 25 January 2024, 17:54 UTC.
        double age = WidgetMath.moonAge(1_706_205_240_000L);
        assertEquals(4, WidgetMath.moonPhase(age));
        assertTrue(WidgetMath.moonIllumination(age) > 0.99d);
    }

    @Test
    public void aKnownNewMoonIsNew() {
        // 11 January 2024, 11:57 UTC.
        double age = WidgetMath.moonAge(1_704_974_220_000L);
        assertEquals(0, WidgetMath.moonPhase(age));
        assertTrue(WidgetMath.moonIllumination(age) < 0.01d);
    }

    @Test
    public void aKnownFirstQuarterIsHalfLit() {
        // 18 January 2024, 03:53 UTC.
        double age = WidgetMath.moonAge(1_705_549_980_000L);
        assertEquals(2, WidgetMath.moonPhase(age));
        assertEquals(0.5d, WidgetMath.moonIllumination(age), 0.03d);
    }

    @Test
    public void daysCountByTheCalendarNotTheClock() {
        long noon = 1_790_510_400_000L; // 27 September 2026, 12:00 UTC
        long today = WidgetMath.dayNumber(noon, UTC);
        assertEquals(0L, WidgetMath.daysUntil(noon, UTC, today));
        // At 23:00 tomorrow is still a day away, not an hour.
        assertEquals(1L, WidgetMath.daysUntil(noon + 11 * 3_600_000L, UTC, today + 1));
        assertEquals(12L, WidgetMath.daysUntil(noon, UTC, today + 12));
        assertEquals(-3L, WidgetMath.daysUntil(noon, UTC, today - 3));
    }

    @Test
    public void theDayRunsFromMidnightToMidnight() {
        Calendar calendar = Calendar.getInstance(UTC, Locale.UK);
        long noon = 1_790_510_400_000L;
        long[] day = WidgetMath.periodBounds(noon, WidgetMath.Period.DAY, calendar);
        assertEquals(24 * 3_600_000L, day[1] - day[0]);
        assertEquals(0.5d, WidgetMath.fraction(noon, day), 0.0001d);
    }

    @Test
    public void theWeekStartsWhereTheLocaleStartsIt() {
        // 27 September 2026 is a Sunday: the last day of a British week.
        Calendar monday = Calendar.getInstance(UTC, Locale.UK);
        long[] week = WidgetMath.periodBounds(1_790_510_400_000L, WidgetMath.Period.WEEK, monday);
        assertTrue(WidgetMath.fraction(1_790_510_400_000L, week) > 0.9d);
        // And the first of an American one.
        Calendar sunday = Calendar.getInstance(UTC, Locale.US);
        week = WidgetMath.periodBounds(1_790_510_400_000L, WidgetMath.Period.WEEK, sunday);
        assertTrue(WidgetMath.fraction(1_790_510_400_000L, week) < 0.1d);
    }

    @Test
    public void theYearIsAYear() {
        Calendar calendar = Calendar.getInstance(UTC, Locale.UK);
        long[] year = WidgetMath.periodBounds(1_790_510_400_000L, WidgetMath.Period.YEAR, calendar);
        assertEquals(365L * 86_400_000L, year[1] - year[0]);
        // Late September is about three quarters through.
        assertEquals(0.74d, WidgetMath.fraction(1_790_510_400_000L, year), 0.01d);
    }

    @Test
    public void pressureWithinAHectopascalIsSteady() {
        assertEquals(0, WidgetMath.pressureTrend(1013.4f, 1012.9f));
        assertEquals(1, WidgetMath.pressureTrend(1015f, 1012f));
        assertEquals(-1, WidgetMath.pressureTrend(1008f, 1012f));
    }

    @Test
    public void pressureConvertsToOtherUnits() {
        assertEquals(760f, WidgetMath.toMillimetresOfMercury(1013.25f), 0.1f);
        assertEquals(29.92f, WidgetMath.toInchesOfMercury(1013.25f), 0.01f);
    }

    @Test
    public void uvAndAirFallIntoTheirBands() {
        assertEquals(0, WidgetMath.uvBand(2.9f));
        assertEquals(1, WidgetMath.uvBand(3f));
        assertEquals(4, WidgetMath.uvBand(11f));
        assertEquals(0, WidgetMath.airBand(20));
        assertEquals(2, WidgetMath.airBand(55));
        assertEquals(5, WidgetMath.airBand(140));
    }
}
