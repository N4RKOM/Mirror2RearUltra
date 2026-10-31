package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One scale of warning for every widget with a limit. */
public class SeverityTest {
    @Test
    public void uvAndAirRiseWithTheirBands() {
        assertEquals(Severity.NORMAL, Severity.forUvBand(1));
        assertEquals(Severity.NOTICE, Severity.forUvBand(2));
        assertEquals(Severity.ALERT, Severity.forUvBand(4));
        assertEquals(Severity.NORMAL, Severity.forAirBand(1));
        assertEquals(Severity.NOTICE, Severity.forAirBand(2));
        assertEquals(Severity.ALERT, Severity.forAirBand(3));
    }

    @Test
    public void memoryAndStorageWarnFromOppositeEnds() {
        assertEquals(Severity.NORMAL, Severity.forUsedPercent(84));
        assertEquals(Severity.NOTICE, Severity.forUsedPercent(85));
        assertEquals(Severity.ALERT, Severity.forUsedPercent(93));
        assertEquals(Severity.NORMAL, Severity.forFreePercent(16));
        assertEquals(Severity.NOTICE, Severity.forFreePercent(15));
        assertEquals(Severity.ALERT, Severity.forFreePercent(7));
    }

    @Test
    public void phoneHeatWarnsFromFortyDegrees() {
        assertEquals(Severity.NORMAL, Severity.forPhoneCelsius(39.9f));
        assertEquals(Severity.NOTICE, Severity.forPhoneCelsius(40f));
        assertEquals(Severity.ALERT, Severity.forPhoneCelsius(45f));
    }

    /** The moon, the progress ring, the world clock and the timer always have a row. */
    @Test
    public void rowsThatAlwaysSayWhatTheyAreAreNotSubjectToThePresenceSetting() {
        assertFalse(WidgetReading.plain("x", WidgetIcon.MOON).gated);
        assertFalse(WidgetReading.plain("x", WidgetIcon.GLOBE).gated);
        // The timer is gated only in form: it always has data, so it is never dropped.
        WidgetReading timer = WidgetReading.gated(true, "0:00", WidgetIcon.TIMER);
        assertTrue(timer.hasData);
    }
}
