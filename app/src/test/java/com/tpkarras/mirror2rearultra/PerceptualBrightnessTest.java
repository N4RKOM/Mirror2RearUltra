package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PerceptualBrightnessTest {
    @Test
    public void spansTheWholeRange() {
        assertEquals(0f, PerceptualBrightness.toLinear(0), 0.0001f);
        assertEquals(1f, PerceptualBrightness.toLinear(100), 0.0001f);
    }

    @Test
    public void halfTheTravelCoversTheBottomTwelfth() {
        // Where the squared half meets the exponential one, which is the whole
        // point of the curve: the dark end gets half the slider.
        assertEquals(1f / 12f, PerceptualBrightness.toLinear(50), 0.0001f);
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
    public void positionAndOutputAgree() {
        // What a migration and the sensor's factor both rely on.
        for (int percent = 0; percent <= 100; percent++) {
            assertEquals(percent,
                    PerceptualBrightness.toPercent(PerceptualBrightness.toLinear(percent)), 1);
        }
    }

    @Test
    public void convertsTheOldDefaults() {
        assertEquals(96, PerceptualBrightness.toPercent(0.80f));
        assertEquals(93, PerceptualBrightness.toPercent(0.70f));
        assertEquals(100, PerceptualBrightness.toPercent(1.00f));
    }
}
