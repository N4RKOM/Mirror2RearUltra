package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The window is the one piece of trigger logic that is easy to get wrong. */
public class PanelTriggersTest {

    private static int at(int hour, int minute) {
        return hour * 60 + minute;
    }

    @Test
    public void daytimeWindowHoldsBetweenItsEnds() {
        int from = at(9, 0);
        int to = at(17, 30);
        assertFalse(PanelTriggers.within(at(8, 59), from, to));
        assertTrue(PanelTriggers.within(at(9, 0), from, to));
        assertTrue(PanelTriggers.within(at(17, 29), from, to));
        assertFalse(PanelTriggers.within(at(17, 30), from, to));
    }

    @Test
    public void nightWindowRunsPastMidnight() {
        int from = at(22, 0);
        int to = at(7, 0);
        assertTrue(PanelTriggers.within(at(22, 0), from, to));
        assertTrue(PanelTriggers.within(at(23, 59), from, to));
        assertTrue(PanelTriggers.within(at(0, 0), from, to));
        assertTrue(PanelTriggers.within(at(3, 39), from, to));
        assertTrue(PanelTriggers.within(at(6, 59), from, to));
        assertFalse(PanelTriggers.within(at(7, 0), from, to));
        assertFalse(PanelTriggers.within(at(12, 0), from, to));
        assertFalse(PanelTriggers.within(at(21, 59), from, to));
    }

    @Test
    public void equalEndsAreNoWindowRatherThanTheWholeDay() {
        int both = at(8, 0);
        assertFalse(PanelTriggers.within(at(8, 0), both, both));
        assertFalse(PanelTriggers.within(at(20, 0), both, both));
    }
}
