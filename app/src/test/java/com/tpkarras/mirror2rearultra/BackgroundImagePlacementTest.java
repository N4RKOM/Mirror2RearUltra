package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BackgroundImagePlacementTest {
    private static BackgroundImagePlacement placement(
            MirrorProfile.ScaleMode mode, int rotation, int zoom) {
        return new BackgroundImagePlacement(mode, rotation, zoom, 0, 0);
    }

    @Test
    public void theDefaultCoversThePanelAsThePictureAlwaysDid() {
        // 200x100 on a 100x100 panel: the short side decides, so 1.0.
        float[] scales = BackgroundImagePlacement.DEFAULT.scales(200, 100, 100, 100);

        assertArrayEquals(new float[]{1f, 1f}, scales, 0.0001f);
    }

    @Test
    public void fitKeepsTheWholePictureAndFillCoversThePanel() {
        float[] fit = placement(MirrorProfile.ScaleMode.FIT, 0, 100).scales(200, 100, 100, 100);
        float[] fill = placement(MirrorProfile.ScaleMode.FILL, 0, 100).scales(200, 100, 100, 100);

        assertArrayEquals(new float[]{0.5f, 0.5f}, fit, 0.0001f);
        assertArrayEquals(new float[]{1f, 1f}, fill, 0.0001f);
    }

    @Test
    public void stretchMatchesEachSideToThePanel() {
        float[] scales = placement(MirrorProfile.ScaleMode.STRETCH, 0, 100)
                .scales(200, 100, 100, 300);

        assertArrayEquals(new float[]{0.5f, 3f}, scales, 0.0001f);
    }

    @Test
    public void aQuarterTurnWorksOutFitOnThePictureLyingOnItsSide() {
        // 200x100 turned is 100x200 on a 100x200 panel: no scaling at all.
        float[] scales = placement(MirrorProfile.ScaleMode.FIT, 90, 100)
                .scales(200, 100, 100, 200);

        assertArrayEquals(new float[]{1f, 1f}, scales, 0.0001f);
    }

    @Test
    public void stretchOnItsSideGivesThePicturesWidthToThePanelsHeight() {
        float[] scales = placement(MirrorProfile.ScaleMode.STRETCH, 270, 100)
                .scales(200, 100, 100, 400);

        // Its width (200) runs along the panel's height (400); its height
        // (100) along the panel's width (100).
        assertArrayEquals(new float[]{2f, 1f}, scales, 0.0001f);
    }

    @Test
    public void zoomMultipliesWhateverTheModeChose() {
        float[] scales = placement(MirrorProfile.ScaleMode.FIT, 0, 150).scales(100, 100, 100, 100);

        assertArrayEquals(new float[]{1.5f, 1.5f}, scales, 0.0001f);
    }

    @Test
    public void offsetsAreSharesOfThePanel() {
        BackgroundImagePlacement shifted = new BackgroundImagePlacement(
                MirrorProfile.ScaleMode.FILL, 0, 100, 25, -10);

        assertEquals(50f, shifted.offsetX(200), 0.0001f);
        assertEquals(-30f, shifted.offsetY(300), 0.0001f);
    }

    @Test
    public void valuesOutsideTheRangesAreBroughtInside() {
        BackgroundImagePlacement placement = new BackgroundImagePlacement(
                null, 450, 10, 200, -200);

        assertEquals(MirrorProfile.ScaleMode.FILL, placement.scaleMode);
        assertEquals(90, placement.rotationDegrees);
        assertEquals(BackgroundImagePlacement.MIN_ZOOM, placement.zoomPercent);
        assertEquals(BackgroundImagePlacement.MAX_OFFSET, placement.horizontalOffsetPercent);
        assertEquals(BackgroundImagePlacement.MIN_OFFSET, placement.verticalOffsetPercent);
    }
}
