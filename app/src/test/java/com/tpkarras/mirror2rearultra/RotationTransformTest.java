package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RotationTransformTest {
    @Test
    public void portraitRotationMirrorsHorizontally() {
        RotationTransform transform = RotationTransform.forRotation(0, 0, true);

        assertEquals(0, transform.degrees);
        assertEquals(-1f, transform.scaleX, 0f);
        assertEquals(1f, transform.scaleY, 0f);
    }

    @Test
    public void landscapeRotationsKeepOppositeDirections() {
        RotationTransform clockwise = RotationTransform.forRotation(1, 0, true);
        RotationTransform counterClockwise = RotationTransform.forRotation(3, 0, true);

        assertEquals(-90, clockwise.degrees);
        assertEquals(90, counterClockwise.degrees);
    }

    @Test
    public void upsideDownRotationKeepsHorizontalMirror() {
        RotationTransform transform = RotationTransform.forRotation(2, 0, true);

        assertEquals(-180, transform.degrees);
        assertEquals(-1f, transform.scaleX, 0f);
        assertEquals(1f, transform.scaleY, 0f);
    }

    @Test
    public void manualRotationCombinesWithDisplayRotation() {
        RotationTransform transform = RotationTransform.forRotation(1, 90, false);

        assertEquals(0, transform.degrees);
        assertEquals(1f, transform.scaleX, 0f);
        assertEquals(1f, transform.scaleY, 0f);
    }
}
