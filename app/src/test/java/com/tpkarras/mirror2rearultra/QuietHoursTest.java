package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/** The sun against almanac times, and the hours around midnight. */
public class QuietHoursTest {

    private static final TimeZone LONDON = TimeZone.getTimeZone("Europe/London");
    private static final TimeZone NEW_YORK = TimeZone.getTimeZone("America/New_York");
    private static final TimeZone MOSCOW = TimeZone.getTimeZone("Europe/Moscow");
    private static final double[] LONDON_PLACE = {51.5074, -0.1278};
    private static final double[] NEW_YORK_PLACE = {40.7128, -74.0060};
    private static final double[] TROMSO_PLACE = {69.6492, 18.9553};
    /** Two minutes either way: the equation is good to about one. */
    private static final long SLACK = 2 * 60_000L;

    private static long at(TimeZone zone, int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    private static void assertNear(long expected, long actual) {
        assertTrue("off by " + (actual - expected) / 1000 + " s",
                Math.abs(actual - expected) <= SLACK);
    }

    @Test
    public void londonMidsummerMatchesTheAlmanac() {
        QuietHours.Sun sun = QuietHours.sunOn(LONDON_PLACE[0], LONDON_PLACE[1],
                at(LONDON, 2024, 6, 21, 12, 0));
        assertNear(at(LONDON, 2024, 6, 21, 4, 43), sun.riseMillis);
        assertNear(at(LONDON, 2024, 6, 21, 21, 21), sun.setMillis);
    }

    @Test
    public void newYorkMidwinterMatchesTheAlmanac() {
        QuietHours.Sun sun = QuietHours.sunOn(NEW_YORK_PLACE[0], NEW_YORK_PLACE[1],
                at(NEW_YORK, 2024, 12, 21, 12, 0));
        assertNear(at(NEW_YORK, 2024, 12, 21, 7, 17), sun.riseMillis);
        assertNear(at(NEW_YORK, 2024, 12, 21, 16, 32), sun.setMillis);
    }

    @Test
    public void farNorthHasPolarNightAndMidnightSun() {
        QuietHours.Sun winter = QuietHours.sunOn(TROMSO_PLACE[0], TROMSO_PLACE[1],
                at(MOSCOW, 2024, 12, 21, 12, 0));
        assertTrue(winter.alwaysDown);
        QuietHours.Sun summer = QuietHours.sunOn(TROMSO_PLACE[0], TROMSO_PLACE[1],
                at(MOSCOW, 2024, 6, 21, 12, 0));
        assertTrue(summer.alwaysUp);

        QuietHours.Verdict night = QuietHours.evaluate(QuietHours.Mode.NIGHT, 0, 0,
                TROMSO_PLACE, at(MOSCOW, 2024, 12, 21, 12, 0), MOSCOW);
        assertTrue(night.quiet);
        assertEquals(at(MOSCOW, 2024, 12, 22, 0, 0), night.nextChangeMillis);
        assertFalse(QuietHours.evaluate(QuietHours.Mode.NIGHT, 0, 0,
                TROMSO_PLACE, at(MOSCOW, 2024, 6, 21, 12, 0), MOSCOW).quiet);
    }

    @Test
    public void nightRunsFromSunsetToTheNextSunrise() {
        QuietHours.Verdict noon = QuietHours.evaluate(QuietHours.Mode.NIGHT, 0, 0,
                LONDON_PLACE, at(LONDON, 2024, 6, 21, 12, 0), LONDON);
        assertFalse(noon.quiet);
        assertNear(at(LONDON, 2024, 6, 21, 21, 21), noon.nextChangeMillis);

        QuietHours.Verdict late = QuietHours.evaluate(QuietHours.Mode.NIGHT, 0, 0,
                LONDON_PLACE, at(LONDON, 2024, 6, 21, 23, 0), LONDON);
        assertTrue(late.quiet);
        assertNear(at(LONDON, 2024, 6, 22, 4, 43), late.nextChangeMillis);

        QuietHours.Verdict early = QuietHours.evaluate(QuietHours.Mode.NIGHT, 0, 0,
                LONDON_PLACE, at(LONDON, 2024, 6, 22, 2, 0), LONDON);
        assertTrue(early.quiet);
        assertNear(at(LONDON, 2024, 6, 22, 4, 43), early.nextChangeMillis);
    }

    @Test
    public void unknownPlaceNeverFallsDark() {
        QuietHours.Verdict verdict = QuietHours.evaluate(QuietHours.Mode.NIGHT, 0, 0,
                null, at(LONDON, 2024, 6, 21, 23, 0), LONDON);
        assertFalse(verdict.quiet);
        assertEquals(QuietHours.NEVER, verdict.nextChangeMillis);
    }

    @Test
    public void scheduleRunsPastMidnightAndNamesItsNextEdge() {
        int from = 23 * 60;
        int to = 7 * 60;
        QuietHours.Verdict before = QuietHours.evaluate(QuietHours.Mode.SCHEDULE, from, to,
                null, at(MOSCOW, 2026, 9, 27, 22, 59), MOSCOW);
        assertFalse(before.quiet);
        assertEquals(at(MOSCOW, 2026, 9, 27, 23, 0), before.nextChangeMillis);

        QuietHours.Verdict inside = QuietHours.evaluate(QuietHours.Mode.SCHEDULE, from, to,
                null, at(MOSCOW, 2026, 9, 27, 23, 30), MOSCOW);
        assertTrue(inside.quiet);
        assertEquals(at(MOSCOW, 2026, 9, 28, 7, 0), inside.nextChangeMillis);

        QuietHours.Verdict morning = QuietHours.evaluate(QuietHours.Mode.SCHEDULE, from, to,
                null, at(MOSCOW, 2026, 9, 28, 7, 0), MOSCOW);
        assertFalse(morning.quiet);
        assertEquals(at(MOSCOW, 2026, 9, 28, 23, 0), morning.nextChangeMillis);
    }

    @Test
    public void offAndEqualEndsAreNoQuietHours() {
        long now = at(MOSCOW, 2026, 9, 27, 23, 30);
        assertFalse(QuietHours.evaluate(QuietHours.Mode.OFF, 23 * 60, 7 * 60,
                LONDON_PLACE, now, MOSCOW).quiet);
        QuietHours.Verdict same = QuietHours.evaluate(QuietHours.Mode.SCHEDULE, 60, 60,
                null, now, MOSCOW);
        assertFalse(same.quiet);
        assertEquals(QuietHours.NEVER, same.nextChangeMillis);
    }
}
