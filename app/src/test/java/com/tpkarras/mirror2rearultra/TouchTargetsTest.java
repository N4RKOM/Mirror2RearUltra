package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The clock used to take touches meant for the line under it. */
public class TouchTargetsTest {
    /** 24dp on the panel, where the density is 1.5. */
    private static final float MINIMUM = 36f;

    /** A clock: taller than the minimum, so it reaches no further than itself. */
    private static float clock(float x, float y) {
        return TouchTargets.missDistance(10f, 100f, 116f, 149f, x, y, MINIMUM);
    }

    /** One short line, twenty-one pixels tall, sitting below the clock. */
    private static float line(float x, float y) {
        return TouchTargets.missDistance(10f, 160f, 116f, 181f, x, y, MINIMUM);
    }

    @Test
    public void insideIsAHit() {
        assertEquals(0f, clock(60f, 120f), 0f);
        assertEquals(0f, line(60f, 170f), 0f);
    }

    @Test
    public void aWidgetBiggerThanTheMinimumReachesNoFurtherThanItself() {
        assertEquals(-1f, clock(60f, 150f), 0f);
        assertEquals(-1f, clock(60f, 99f), 0f);
        assertEquals(-1f, clock(117f, 120f), 0f);
    }

    @Test
    public void aSmallWidgetReachesOutToTheMinimum() {
        // Twenty-one tall against a minimum of thirty-six: seven and a half
        // either side.
        assertTrue(line(60f, 153f) > 0f);
        assertEquals(-1f, line(60f, 152f), 0f);
        assertTrue(line(60f, 188f) > 0f);
        assertEquals(-1f, line(60f, 189f), 0f);
    }

    @Test
    public void theNearerWidgetMissesByLess() {
        // A point in the gap: closer to the line than to the clock, and the
        // clock does not answer for it at all.
        float y = 155f;
        assertEquals(-1f, clock(60f, y), 0f);
        assertTrue(line(60f, y) > 0f);
    }

    @Test
    public void missGrowsWithDistance() {
        assertTrue(line(60f, 155f) > line(60f, 158f));
    }

    @Test
    public void anEmptyBoxStillAnswersWithinTheMinimum() {
        // Nothing has been drawn for it yet, so it is a point: it should still
        // be reachable, and only just.
        assertEquals(0f, TouchTargets.missDistance(60f, 60f, 60f, 60f, 60f, 60f, MINIMUM), 0f);
        assertTrue(TouchTargets.missDistance(60f, 60f, 60f, 60f, 60f, 77f, MINIMUM) > 0f);
        assertEquals(-1f,
                TouchTargets.missDistance(60f, 60f, 60f, 60f, 60f, 79f, MINIMUM), 0f);
    }
}
