package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProjectionGeometryTest {
    @Test
    public void fillKeepsCurrentPortraitCropOnMi11Ultra() {
        ProjectionGeometry.Scale scale = ProjectionGeometry.calculateScale(
                MirrorProfile.ScaleMode.FILL,
                126,
                294,
                294,
                1440,
                3200,
                0
        );

        assertEquals(1f, scale.x, 0.001f);
        assertEquals(1f, scale.y, 0.001f);
    }

    @Test
    public void fitPreservesWholePortraitFrame() {
        ProjectionGeometry.Scale scale = ProjectionGeometry.calculateScale(
                MirrorProfile.ScaleMode.FIT,
                126,
                294,
                294,
                1440,
                3200,
                0
        );

        assertEquals(0.952f, scale.x, 0.001f);
        assertEquals(scale.x, scale.y, 0f);
    }

    @Test
    public void stretchUsesIndependentAxes() {
        ProjectionGeometry.Scale scale = ProjectionGeometry.calculateScale(
                MirrorProfile.ScaleMode.STRETCH,
                126,
                294,
                294,
                1440,
                3200,
                0
        );

        assertEquals(0.952f, scale.x, 0.001f);
        assertEquals(1f, scale.y, 0.001f);
    }

    @Test
    public void manualQuarterTurnFitsLandscapeFrame() {
        ProjectionGeometry.Scale scale = ProjectionGeometry.calculateScale(
                MirrorProfile.ScaleMode.FIT,
                126,
                294,
                294,
                1440,
                3200,
                90
        );

        assertEquals(0.429f, scale.x, 0.001f);
        assertEquals(scale.x, scale.y, 0f);
    }

    @Test
    public void translationUsesViewportPercentageAndClamps() {
        assertEquals(63f, ProjectionGeometry.calculateTranslation(126, 50), 0f);
        assertEquals(-147f, ProjectionGeometry.calculateTranslation(294, -80), 0f);
        assertEquals(0f, ProjectionGeometry.calculateTranslation(0, 50), 0f);
    }
}
