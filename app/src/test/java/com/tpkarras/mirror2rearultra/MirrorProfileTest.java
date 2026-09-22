package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MirrorProfileTest {
    @Test
    public void normalizesRotationAndBrightness() {
        MirrorProfile profile = new MirrorProfile(
                MirrorProfile.Id.CAMERA,
                MirrorProfile.ScaleMode.FILL,
                455,
                true,
                140
        );

        assertEquals(90, profile.rotationDegrees);
        assertEquals(100, profile.brightnessPercent);
    }

    @Test
    public void clampsLowBrightness() {
        MirrorProfile profile = new MirrorProfile(
                MirrorProfile.Id.VIDEO,
                MirrorProfile.ScaleMode.FIT,
                0,
                false,
                0
        );

        assertEquals(10, profile.brightnessPercent);
    }

    @Test
    public void aFrameBelongsToTheWayTheScreenWasHeld() {
        // Sideways an app is laid out afresh, so the two frames are kept
        // apart rather than one standing in for the other.
        MirrorProfile profile = new MirrorProfile(
                MirrorProfile.Id.CAMERA, MirrorProfile.ScaleMode.FILL, 0, false, 100)
                .withCrop(new MirrorProfile.Crop(0f, 0.15f, 1f, 0.75f, 0))
                .withCrop(new MirrorProfile.Crop(0.2f, 0f, 0.8f, 1f, 1));

        assertEquals(0.15f, profile.cropFor(0).top, 1e-4f);
        assertEquals(0.15f, profile.cropFor(2).top, 1e-4f);
        assertEquals(0.2f, profile.cropFor(1).left, 1e-4f);
        assertEquals(0.2f, profile.cropFor(3).left, 1e-4f);
        assertTrue(profile.hasCrop());
        assertNull(profile.resetCalibration().cropFor(0));
        assertNull(profile.resetCalibration().cropFor(1));
        assertFalse(profile.resetCalibration().hasCrop());
    }

    @Test
    public void aFrameOnlyOneWayRoundLeavesTheOtherWayUnframed() {
        MirrorProfile profile = new MirrorProfile(
                MirrorProfile.Id.CAMERA, MirrorProfile.ScaleMode.FILL, 0, false, 100)
                .withCrop(new MirrorProfile.Crop(0f, 0.15f, 1f, 0.75f, 0));

        assertNull(profile.cropFor(1));
        // The rest of the profile rides along with the frame.
        assertEquals(MirrorProfile.ScaleMode.FILL, profile.scaleMode);
    }

    @Test
    public void clampsAndResetsCalibration() {
        MirrorProfile profile = new MirrorProfile(
                MirrorProfile.Id.CAMERA,
                MirrorProfile.ScaleMode.FILL,
                0,
                false,
                100,
                250,
                -80,
                90
        );

        assertEquals(200, profile.zoomPercent);
        assertEquals(-50, profile.horizontalOffsetPercent);
        assertEquals(50, profile.verticalOffsetPercent);

        MirrorProfile reset = profile.resetCalibration();
        assertEquals(100, reset.zoomPercent);
        assertEquals(0, reset.horizontalOffsetPercent);
        assertEquals(0, reset.verticalOffsetPercent);
    }

    @Test
    public void customProfileKeepsIdentityAcrossEdits() {
        MirrorProfile profile = new MirrorProfile(
                "CUSTOM_123",
                "Passenger",
                MirrorProfile.ScaleMode.FILL,
                0,
                false,
                75,
                100,
                0,
                0
        );

        MirrorProfile edited = profile.withZoomPercent(135).withCustomName("Passenger screen");

        assertEquals("CUSTOM_123", edited.id);
        assertEquals("Passenger screen", edited.customName);
        assertEquals(135, edited.zoomPercent);
    }
}
