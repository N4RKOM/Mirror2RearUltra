package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the panel lights for a notification, and what its rings say. */
public class NotificationPulseTest {
    private static final int WHITE = 0xffffffff;

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
    public void oneRingForEachUnseenNotificationUpToFive() {
        assertEquals(1, NotificationPulse.ringsFor(0));
        assertEquals(1, NotificationPulse.ringsFor(1));
        assertEquals(3, NotificationPulse.ringsFor(3));
        assertEquals(5, NotificationPulse.ringsFor(12));
    }

    @Test
    public void theWaveRunsThreeTimesAndStops() {
        assertEquals(0f, NotificationPulse.waveProgress(0L), 0f);
        assertEquals(0.5f, NotificationPulse.waveProgress(NotificationPulse.WAVE_MILLIS / 2), 0.001f);
        assertEquals(0f, NotificationPulse.waveProgress(NotificationPulse.WAVE_MILLIS), 0f);
        assertEquals(-1f, NotificationPulse.waveProgress(NotificationPulse.DURATION_MILLIS), 0f);
        assertEquals(-1f, NotificationPulse.waveProgress(-1L), 0f);
    }

    @Test
    public void theWaveLightsEachRingInTurnAndLeavesNoneOut() {
        // Three rings: the first is brightest early in the wave, the last late.
        assertEquals(1f, NotificationPulse.ringBrightness(1f / 6f, 0, 3), 0.001f);
        assertEquals(1f, NotificationPulse.ringBrightness(5f / 6f, 2, 3), 0.001f);
        assertEquals(0.3f, NotificationPulse.ringBrightness(5f / 6f, 0, 3), 0.001f);
    }

    @Test
    public void anAppsColourIsKeptButBrightenedToShowOnBlack() {
        // A dark green, as a messenger might set it.
        int glow = NotificationPulse.glowColour(0xff075e54);
        assertEquals(0xff, glow >>> 24);
        int green = (glow >> 8) & 0xff;
        assertTrue(green > 200);
        assertTrue(green > ((glow >> 16) & 0xff));
    }

    @Test
    public void noColourOrAGreyOneGlowsWhite() {
        assertEquals(WHITE, NotificationPulse.glowColour(0));
        assertEquals(WHITE, NotificationPulse.glowColour(0xff808080));
    }

    @Test
    public void anIconsColourIgnoresItsWhiteAndItsTransparentParts() {
        int blue = 0xff2aabee;
        int[] pixels = {blue, blue, WHITE, WHITE, WHITE, 0x00000000};
        assertEquals(blue, NotificationPulse.dominantColour(pixels));
        assertEquals(0, NotificationPulse.dominantColour(new int[]{WHITE, 0xff000000}));
    }
}
