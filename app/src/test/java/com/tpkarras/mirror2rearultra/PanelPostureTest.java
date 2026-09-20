package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Which way up the phone is, from what the accelerometer reports.
 *
 * <p>The axis points out of the main screen, X to the right and Y to the top
 * as the screen is read. Whichever axis reads positive is the one pointing up
 * against gravity.
 */
public class PanelPostureTest {
    private static final int UNKNOWN = PanelPostureSensor.TURN_UNKNOWN;

    @Test
    public void uprightIsNoTurnAtAll() {
        assertEquals(0, PanelPostureSensor.turnOf(0f, 9.8f, UNKNOWN));
    }

    @Test
    public void leaningOnItsLeftEdgeIsAQuarterTurnClockwise() {
        // The left side is then the one pointing up.
        assertEquals(1, PanelPostureSensor.turnOf(-9.8f, 0f, UNKNOWN));
    }

    @Test
    public void upsideDownIsTwo() {
        assertEquals(2, PanelPostureSensor.turnOf(0f, -9.8f, UNKNOWN));
    }

    @Test
    public void leaningOnItsRightEdgeIsThree() {
        assertEquals(3, PanelPostureSensor.turnOf(9.8f, 0f, UNKNOWN));
    }

    @Test
    public void lyingFlatKeepsWhateverWasLastKnown() {
        // Flat on a table both axes read nothing, and there is no answer to
        // give - so the page holds still rather than spinning.
        assertEquals(3, PanelPostureSensor.turnOf(0.1f, -0.2f, 3));
        assertEquals(UNKNOWN, PanelPostureSensor.turnOf(0.1f, -0.2f, UNKNOWN));
    }

    @Test
    public void aLeanTooSlightToBeSureOfChangesNothing() {
        assertEquals(1, PanelPostureSensor.turnOf(5.5f, 0f, 1));
    }

    @Test
    public void theStrongerLeanDecidesAtTheCorners() {
        assertEquals(0, PanelPostureSensor.turnOf(6.5f, 9.0f, UNKNOWN));
        assertEquals(3, PanelPostureSensor.turnOf(9.0f, 6.5f, UNKNOWN));
    }
}
