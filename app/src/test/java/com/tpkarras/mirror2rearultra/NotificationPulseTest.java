package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the panel lights for a notification, and how the pulse runs. */
public class NotificationPulseTest {
    @Test
    public void lightsWithTheMainScreenOffAndNothingInTheWay() {
        assertTrue(NotificationPulse.shouldPulse(true, false, false, false, false, false));
    }

    @Test
    public void staysDarkWhenSwitchedOff() {
        assertFalse(NotificationPulse.shouldPulse(false, false, false, false, false, false));
    }

    @Test
    public void staysDarkWithTheMainScreenOn() {
        // The notification is in front of its owner already.
        assertFalse(NotificationPulse.shouldPulse(true, true, false, false, false, false));
    }

    @Test
    public void staysDarkWhereItWouldNotBeSeenOrWouldBeInTheWay() {
        assertFalse(NotificationPulse.shouldPulse(true, false, true, false, false, false));
        assertFalse(NotificationPulse.shouldPulse(true, false, false, true, false, false));
        assertFalse(NotificationPulse.shouldPulse(true, false, false, false, true, false));
        assertFalse(NotificationPulse.shouldPulse(true, false, false, false, false, true));
    }

    @Test
    public void eachRingRunsFromNothingToAlmostDone() {
        assertEquals(0f, NotificationPulse.ringProgress(0L), 0f);
        assertEquals(0.5f, NotificationPulse.ringProgress(NotificationPulse.RING_MILLIS / 2), 0.001f);
        assertEquals(0f, NotificationPulse.ringProgress(NotificationPulse.RING_MILLIS), 0f);
    }

    @Test
    public void itEndsAfterItsRings() {
        assertEquals(-1f, NotificationPulse.ringProgress(NotificationPulse.DURATION_MILLIS), 0f);
        assertEquals(-1f, NotificationPulse.ringProgress(-1L), 0f);
    }
}
