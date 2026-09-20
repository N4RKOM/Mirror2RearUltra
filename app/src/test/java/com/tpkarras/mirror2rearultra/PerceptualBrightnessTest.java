package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PerceptualBrightnessTest {
    @Test
    public void stopsAtTheDimmestUsefulLightRatherThanAtNothing() {
        assertEquals(PerceptualBrightness.MINIMUM_SHARE,
                PerceptualBrightness.toLinear(0), 0.0001f);
        assertEquals(1f, PerceptualBrightness.toLinear(100), 0.0001f);
    }

    @Test
    public void halfTheTravelCoversTheBottomTwelfth() {
        // Where the squared half meets the exponential one, which is the whole
        // point of the curve: the dark end gets half the slider. Measured over
        // what the panel can show, so a twelfth of that, above its floor.
        float floor = PerceptualBrightness.MINIMUM_SHARE;
        assertEquals(1f / 12f, PerceptualBrightness.toRequestedShare(50), 0.0001f);
        assertEquals(floor + (1f - floor) / 12f,
                PerceptualBrightness.toLinear(50), 0.0001f);
    }

    @Test
    public void joinsWithoutAStep() {
        float below = PerceptualBrightness.toLinear(49);
        float above = PerceptualBrightness.toLinear(51);
        assertTrue(below < PerceptualBrightness.toLinear(50));
        assertTrue(PerceptualBrightness.toLinear(50) < above);
        assertTrue(above - below < 0.01f);
    }

    @Test
    public void risesAllTheWay() {
        for (int percent = 1; percent <= 100; percent++) {
            assertTrue("fell at " + percent,
                    PerceptualBrightness.toLinear(percent)
                            > PerceptualBrightness.toLinear(percent - 1));
        }
    }

    @Test
    public void aodAsksForLessThanThePanelWillGive() {
        // The reason AOD needs a veil at all: every setting the AOD slider
        // offers is below the panel's floor.
        for (int percent = 1; percent <= 30; percent++) {
            assertTrue("reachable at " + percent,
                    PerceptualBrightness.toRequestedShare(percent)
                            < PerceptualBrightness.MINIMUM_SHARE);
        }
    }

    @Test
    public void positionAndOutputAgree() {
        // What a migration and the sensor's factor both rely on.
        for (int percent = 0; percent <= 100; percent++) {
            assertEquals(percent,
                    PerceptualBrightness.toPercent(PerceptualBrightness.toLinear(percent)), 1);
        }
    }

    @Test
    public void needsNoVeilWhenThePanelCanGoThatDim() {
        // The panel's own dimmest step, on a backlight of 255.
        float floor = PerceptualBrightness.MINIMUM_SHARE;
        assertEquals(0f, PerceptualBrightness.veilAlpha(floor, floor), 0f);
        assertEquals(0f, PerceptualBrightness.veilAlpha(1f, floor), 0f);
        // Anything the slider can ask for is at or above the floor by design.
        assertEquals(0f, PerceptualBrightness.veilAlpha(
                PerceptualBrightness.toLinear(1), floor), 0f);
    }

    @Test
    public void veilsOnlyWhatIsLeftBelowTheFloor() {
        float floor = PerceptualBrightness.MINIMUM_SHARE;
        // What AOD asks for is below anything the panel shows, so the overlay
        // takes the remainder - in encoded terms rather than in light.
        float alpha = PerceptualBrightness.veilAlpha(
                PerceptualBrightness.toRequestedShare(21),
                PerceptualBrightness.toLinear(21));
        assertEquals(0.605f, alpha, 0.005f);
        // Darker settings need more of it, and never more than all of it.
        assertTrue(PerceptualBrightness.veilAlpha(
                PerceptualBrightness.toRequestedShare(1),
                PerceptualBrightness.toLinear(1)) > alpha);
        assertEquals(1f, PerceptualBrightness.veilAlpha(0f, floor), 0.0001f);
    }

    @Test
    public void asksForNoVeilWithoutAKnownFloor() {
        assertEquals(0f, PerceptualBrightness.veilAlpha(0.001f, 0f), 0f);
    }

    @Test
    public void convertsTheOldDefaults() {
        assertEquals(95, PerceptualBrightness.toPercent(0.80f));
        assertEquals(93, PerceptualBrightness.toPercent(0.70f));
        assertEquals(100, PerceptualBrightness.toPercent(1.00f));
    }
}
