package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The rectangle of the main screen the panel shows, and what the panel has to
 * do with it.
 *
 * <p>The numbers are the Mi 11 Ultra's: a 126 x 294 panel and a main screen of
 * 1080 x 2400 as the phone reports it. The panel's texture is a 294 x 294
 * square centred across the panel, and the screen is letterboxed into it, so
 * the screen occupies 132.3 x 294 of that square.
 */
public class CropFrameTest {
    private static final int PANEL_WIDTH = 126;
    private static final int PANEL_HEIGHT = 294;
    private static final int SCREEN_WIDTH = 1080;
    private static final int SCREEN_HEIGHT = 2400;
    private static final int SIDE = 294;
    private static final float CONTENT_WIDTH = SIDE * SCREEN_WIDTH / (float) SCREEN_HEIGHT;
    private static final float PIXEL = 1f / SCREEN_HEIGHT;

    private static MirrorProfile profile(
            MirrorProfile.ScaleMode mode, int rotation, boolean mirror) {
        return new MirrorProfile(MirrorProfile.Id.CAMERA, mode, rotation, mirror, 100);
    }

    private static CropFrame portrait(MirrorProfile profile) {
        return new CropFrame(PANEL_WIDTH, PANEL_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 0, profile);
    }

    /** Where a point of the screen lands on the panel under this projection. */
    private static float[] onPanel(CropFrame.Projection projection, float u, float v) {
        float x = (u - 0.5f) * CONTENT_WIDTH;
        float y = (v - 0.5f) * SIDE;
        double radians = Math.toRadians(projection.degrees);
        float cos = (float) Math.round(Math.cos(radians));
        float sin = (float) Math.round(Math.sin(radians));
        float turnedX = cos * x - sin * y;
        float turnedY = sin * x + cos * y;
        return new float[] {
                (PANEL_WIDTH - SIDE) / 2f + SIDE / 2f
                        + turnedX * projection.scaleX + projection.translateX,
                SIDE / 2f + turnedY * projection.scaleY + projection.translateY
        };
    }

    // ---- The rectangle a profile's zoom and offsets amount to ----

    @Test
    public void unzoomedFillShowsTheWholeHeightAndTrimsTheSides() {
        CropFrame.Rect frame = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, false))
                .frameOf(new CropFrame.Calibration(100, 0, 0));
        assertEquals(0f, frame.top, PIXEL);
        assertEquals(1f, frame.bottom, PIXEL);
        // The panel is a little narrower than the screen for its height.
        float width = (126f / 294f) / (1080f / 2400f);
        assertEquals((1f - width) / 2f, frame.left, PIXEL);
        assertEquals(1f - (1f - width) / 2f, frame.right, PIXEL);
    }

    @Test
    public void aPositiveVerticalOffsetShowsHigherUpTheScreen() {
        // The camera profile as it was tuned by hand on the phone: 165 % and
        // +20 %. The image slides down, so the panel looks further up.
        CropFrame.Rect frame = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, false))
                .frameOf(new CropFrame.Calibration(165, 0, 20));
        assertEquals(182f / 2400f, frame.top, 2 * PIXEL);
        assertEquals(1636f / 2400f, frame.bottom, 2 * PIXEL);
    }

    // ---- What the panel does with a frame ----

    @Test
    public void aPanelShapedFrameFillsThePanelWithNothingToSpare() {
        CropFrame crop = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, false));
        // The viewfinder's height, in the panel's own proportions.
        float height = (1794f - 354f) / 2400f;
        CropFrame.Rect frame = CropFrame.Rect.around(
                0.5f, (354f + 1794f) / 2f / 2400f, height * crop.panelShapeAspect(), height);
        CropFrame.Projection projection = crop.projectionFor(frame);
        assertEquals(PANEL_WIDTH, projection.visibleWidth, 0.5f);
        assertEquals(PANEL_HEIGHT, projection.visibleHeight, 0.5f);
        assertTrue(crop.fillsPanel(frame));
        // 60 % of the screen's height across the whole panel: blown up by
        // two thirds, which is what the capture has to be sharp enough for.
        assertEquals(167, projection.magnificationPercent);
        // Each corner of the frame lands on a corner of the panel.
        float[] topLeft = onPanel(projection, frame.left, frame.top);
        float[] bottomRight = onPanel(projection, frame.right, frame.bottom);
        assertEquals(0f, topLeft[0], 0.5f);
        assertEquals(0f, topLeft[1], 0.5f);
        assertEquals(PANEL_WIDTH, bottomRight[0], 0.5f);
        assertEquals(PANEL_HEIGHT, bottomRight[1], 0.5f);
    }

    @Test
    public void aWholeFourByThreeViewfinderKeepsItsShapeAndLeavesBlack() {
        // The frame the phone actually needs: Google Camera's whole 4:3
        // viewfinder, the full width of the screen.
        CropFrame crop = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, false));
        CropFrame.Rect frame = new CropFrame.Rect(0f, 354f / 2400f, 1f, 1794f / 2400f);
        CropFrame.Projection projection = crop.projectionFor(frame);
        assertEquals(PANEL_WIDTH, projection.visibleWidth, 0.5f);
        // Four to three, as wide as the panel: 126 x 168, so 63 rows of black
        // above and below.
        assertEquals(168f, projection.visibleHeight, 1f);
        assertFalse(crop.fillsPanel(frame));
        // Nothing is blown up here - the screen is being shrunk, if anything.
        assertTrue(projection.magnificationPercent <= 100);
        float[] topLeft = onPanel(projection, frame.left, frame.top);
        float[] bottomRight = onPanel(projection, frame.right, frame.bottom);
        assertEquals(0f, topLeft[0], 0.5f);
        assertEquals((PANEL_HEIGHT - 168f) / 2f, topLeft[1], 1f);
        assertEquals(PANEL_WIDTH, bottomRight[0], 0.5f);
        assertEquals(PANEL_HEIGHT - (PANEL_HEIGHT - 168f) / 2f, bottomRight[1], 1f);
    }

    @Test
    public void mirroringTurnsTheFrameOverWithoutMovingIt() {
        CropFrame crop = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, true));
        CropFrame.Rect frame = new CropFrame.Rect(0.1f, 0.3f, 0.6f, 0.7f);
        CropFrame.Projection projection = crop.projectionFor(frame);
        // The frame's left edge comes out on the panel's right.
        float[] left = onPanel(projection, frame.left, frame.centreY());
        float[] right = onPanel(projection, frame.right, frame.centreY());
        assertTrue(left[0] > right[0]);
        assertEquals(PANEL_WIDTH / 2f, (left[0] + right[0]) / 2f, 0.5f);
    }

    @Test
    public void everyFrameLandsWhereItsProjectionSaysForEveryTurn() {
        for (MirrorProfile.ScaleMode mode : MirrorProfile.ScaleMode.values()) {
            for (int rotation = 0; rotation < 360; rotation += 90) {
                for (boolean mirror : new boolean[] {false, true}) {
                    for (int display = 0; display < 4; display++) {
                        landsOnThePanel(mode, rotation, mirror, display);
                    }
                }
            }
        }
    }

    private static void landsOnThePanel(
            MirrorProfile.ScaleMode mode, int rotation, boolean mirror, int display) {
        boolean landscape = display % 2 == 1;
        int screenWidth = landscape ? SCREEN_HEIGHT : SCREEN_WIDTH;
        int screenHeight = landscape ? SCREEN_WIDTH : SCREEN_HEIGHT;
        CropFrame crop = new CropFrame(PANEL_WIDTH, PANEL_HEIGHT,
                screenWidth, screenHeight, display, profile(mode, rotation, mirror));
        String where = mode + " " + rotation + (mirror ? " mirrored" : "") + " display " + display;
        for (CropFrame.Rect frame : new CropFrame.Rect[] {
                new CropFrame.Rect(0f, 0f, 1f, 1f),
                new CropFrame.Rect(0.1f, 0.15f, 0.9f, 0.75f),
                new CropFrame.Rect(0.4f, 0.4f, 0.6f, 0.95f),
        }) {
            CropFrame.Projection projection = crop.projectionFor(frame);
            // Whatever the turn, the frame lands centred on the panel, and one
            // of its sides touches the panel's edges exactly.
            assertTrue(where, projection.visibleWidth <= PANEL_WIDTH + 0.5f);
            assertTrue(where, projection.visibleHeight <= PANEL_HEIGHT + 0.5f);
            assertTrue(where, projection.visibleWidth > PANEL_WIDTH - 0.5f
                    || projection.visibleHeight > PANEL_HEIGHT - 0.5f);
            float centreLeft = (PANEL_WIDTH - projection.visibleWidth) / 2f;
            float centreTop = (PANEL_HEIGHT - projection.visibleHeight) / 2f;
            float[] corners = spread(projection, frame, screenWidth, screenHeight);
            assertEquals(where, centreLeft, corners[0], 1f);
            assertEquals(where, centreTop, corners[1], 1f);
            assertEquals(where, PANEL_WIDTH - centreLeft, corners[2], 1f);
            assertEquals(where, PANEL_HEIGHT - centreTop, corners[3], 1f);
        }
    }

    /** The box the frame's four corners land in, as left, top, right, bottom. */
    private static float[] spread(CropFrame.Projection projection, CropFrame.Rect frame,
                                  int screenWidth, int screenHeight) {
        float contentWidth = screenWidth <= screenHeight
                ? SIDE * screenWidth / (float) screenHeight : SIDE;
        float contentHeight = screenWidth <= screenHeight
                ? SIDE : SIDE * screenHeight / (float) screenWidth;
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (float u : new float[] {frame.left, frame.right}) {
            for (float v : new float[] {frame.top, frame.bottom}) {
                float x = (u - 0.5f) * contentWidth;
                float y = (v - 0.5f) * contentHeight;
                double radians = Math.toRadians(projection.degrees);
                float cos = (float) Math.round(Math.cos(radians));
                float sin = (float) Math.round(Math.sin(radians));
                float panelX = (PANEL_WIDTH - SIDE) / 2f + SIDE / 2f
                        + (cos * x - sin * y) * projection.scaleX + projection.translateX;
                float panelY = SIDE / 2f
                        + (sin * x + cos * y) * projection.scaleY + projection.translateY;
                left = Math.min(left, panelX);
                right = Math.max(right, panelX);
                top = Math.min(top, panelY);
                bottom = Math.max(bottom, panelY);
            }
        }
        return new float[] {left, top, right, bottom};
    }

    // ---- What the frame may and may not be ----

    @Test
    public void aFrameTooSmallToBeSharpIsGrownBackToTheLimit() {
        CropFrame crop = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, false));
        CropFrame.Rect tiny = CropFrame.Rect.around(0.5f, 0.5f, 0.1f, 0.25f);
        CropFrame.Rect settled = crop.settle(tiny);
        assertEquals(CropFrame.MAX_MAGNIFICATION, crop.magnification(settled), 0.02f);
    }

    @Test
    public void aFrameDraggedOffTheEdgeStopsAtIt() {
        CropFrame crop = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, false));
        CropFrame.Rect settled = crop.settle(CropFrame.Rect.around(0.5f, -3f, 0.5f, 0.6f));
        assertEquals(0f, settled.top, 0.001f);
        settled = crop.settle(CropFrame.Rect.around(4f, 0.5f, 0.5f, 0.6f));
        assertEquals(1f, settled.right, 0.001f);
    }

    @Test
    public void theWholeScreenIsAllowedAndFitsInsideThePanel() {
        CropFrame crop = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, false));
        CropFrame.Rect whole = crop.settle(crop.wholeScreen());
        assertEquals(0f, whole.left, 0.001f);
        assertEquals(1f, whole.bottom, 0.001f);
        CropFrame.Projection projection = crop.projectionFor(whole);
        assertEquals(PANEL_WIDTH, projection.visibleWidth, 0.5f);
        assertTrue(projection.visibleHeight < PANEL_HEIGHT);
    }

    @Test
    public void aFrameNearlyThePanelsShapeSnapsOntoIt() {
        CropFrame crop = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, false));
        float aspect = crop.panelShapeAspect();
        CropFrame.Rect close = CropFrame.Rect.around(0.5f, 0.5f, 0.5f, 0.5f / aspect * 1.02f);
        assertTrue(crop.fillsPanel(crop.snapToPanelShape(close, 0.05f)));
        CropFrame.Rect wide = CropFrame.Rect.around(0.5f, 0.5f, 0.9f, 0.4f);
        assertFalse(crop.fillsPanel(crop.snapToPanelShape(wide, 0.05f)));
    }

    @Test
    public void thePanelShapeInsideAWideFrameKeepsItsCentre() {
        CropFrame crop = portrait(profile(MirrorProfile.ScaleMode.FILL, 0, false));
        CropFrame.Rect wide = new CropFrame.Rect(0f, 0.2f, 1f, 0.8f);
        CropFrame.Rect shaped = crop.toPanelShape(wide);
        assertTrue(crop.fillsPanel(shaped));
        assertEquals(wide.centreX(), shaped.centreX(), 0.001f);
        assertEquals(wide.centreY(), shaped.centreY(), 0.001f);
        assertTrue(shaped.width() <= wide.width() + 0.001f);
        assertTrue(shaped.height() <= wide.height() + 0.001f);
    }
}
