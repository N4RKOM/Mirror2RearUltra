package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AppProfileClassifierTest {
    @Test
    public void recognizesTheCameraFamily() {
        assertEquals(
                MirrorProfile.CAMERA_ID,
                AppProfileClassifier.classify("com.shamim.cam")
        );
    }

    @Test
    public void mapsAndVideoAppsAreLeftToTheUser() {
        assertNull(AppProfileClassifier.classify("com.google.android.apps.maps"));
        assertNull(AppProfileClassifier.classify("org.videolan.vlc"));
    }

    @Test
    public void leavesUnknownAppsOnTheManualProfile() {
        assertNull(AppProfileClassifier.classify("com.example.reader"));
        assertNull(AppProfileClassifier.classify(null));
    }
}
