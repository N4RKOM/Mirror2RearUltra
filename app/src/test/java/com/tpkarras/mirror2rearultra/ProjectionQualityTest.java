package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProjectionQualityTest {
    @Test
    public void sharpUsesNativeResolutionAtDefaultZoom() {
        assertEquals(294, ProjectionQuality.SHARP.bufferDimension(294, 100));
    }

    @Test
    public void sharpUsesOneAndHalfResolutionFrom105Through150Percent() {
        assertEquals(441, ProjectionQuality.SHARP.bufferDimension(294, 105));
        assertEquals(441, ProjectionQuality.SHARP.bufferDimension(294, 150));
    }

    @Test
    public void sharpUsesDoubleResolutionFrom155Percent() {
        assertEquals(588, ProjectionQuality.SHARP.bufferDimension(294, 155));
        assertEquals(588, ProjectionQuality.SHARP.bufferDimension(294, 200));
    }

    @Test
    public void economyMatchesTheViewResolutionAtEveryZoom() {
        assertEquals(294, ProjectionQuality.ECONOMY.bufferDimension(294, 100));
        assertEquals(294, ProjectionQuality.ECONOMY.bufferDimension(294, 200));
    }

    @Test
    public void dimensionsNeverDropBelowOnePixel() {
        assertEquals(2, ProjectionQuality.SHARP.bufferDimension(0, 155));
        assertEquals(1, ProjectionQuality.ECONOMY.bufferDimension(-10, 200));
    }
}
