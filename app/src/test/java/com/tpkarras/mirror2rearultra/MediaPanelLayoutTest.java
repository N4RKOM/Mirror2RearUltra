package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.tpkarras.mirror2rearultra.DashboardWidgetLayout.Variant;
import com.tpkarras.mirror2rearultra.MediaPanelLayout.Action;

import org.junit.Test;

/** What a finger on the full-screen player means, look by look. */
public class MediaPanelLayoutTest {
    private static final float W = 294f;
    private static final float H = 126f;

    private static Action at(Variant variant, float x, float y) {
        return MediaPanelLayout.hit(variant, x, y, W, H);
    }

    @Test
    public void defaultLookSplitsTheWholePanelIntoThirds() {
        for (float y : new float[]{2f, 60f, 124f}) {
            assertEquals(Action.PREVIOUS, at(Variant.DEFAULT, 10f, y));
            assertEquals(Action.PLAY_PAUSE, at(Variant.DEFAULT, 147f, y));
            assertEquals(Action.NEXT, at(Variant.DEFAULT, 280f, y));
        }
    }

    @Test
    public void wordsAboveTheControlsAreNotAControl() {
        assertNull(at(Variant.ALTERNATE, 147f, 20f));
        assertNull(at(Variant.FIFTH, 147f, 20f));
    }

    @Test
    public void coverLookReachesTheWholeScreen() {
        assertEquals(Action.PREVIOUS, at(Variant.FOURTH, 5f, 2f));
        assertEquals(Action.PLAY_PAUSE, at(Variant.FOURTH, 147f, 120f));
        assertEquals(Action.NEXT, at(Variant.FOURTH, 290f, 60f));
    }

    @Test
    public void theCoverItselfPlaysAndPauses() {
        assertEquals(Action.PLAY_PAUSE, at(Variant.DETAILED, 20f, 10f));
        assertEquals(Action.PLAY_PAUSE, at(Variant.DETAILED, 100f, 120f));
        assertEquals(Action.PREVIOUS, at(Variant.DETAILED, 140f, 100f));
        assertEquals(Action.PLAY_PAUSE, at(Variant.DETAILED, 210f, 100f));
        assertEquals(Action.NEXT, at(Variant.DETAILED, 280f, 100f));
        assertNull(at(Variant.DETAILED, 210f, 10f));
    }

    @Test
    public void zonesTouchSoNoSpotIsDead() {
        for (Variant variant : new Variant[]{Variant.DEFAULT, Variant.ALTERNATE,
                Variant.DETAILED, Variant.FOURTH, Variant.FIFTH}) {
            float[] previous = MediaPanelLayout.zone(variant, Action.PREVIOUS);
            float[] play = MediaPanelLayout.zone(variant, Action.PLAY_PAUSE);
            float[] next = MediaPanelLayout.zone(variant, Action.NEXT);
            assertEquals(previous[2], play[0], 0f);
            assertEquals(play[2], next[0], 0f);
            assertEquals(1f, next[2], 0f);
            assertEquals(1f, play[3], 0f);
            assertArrayEquals(new float[]{previous[1], previous[1]},
                    new float[]{play[1], next[1]}, 0f);
        }
    }

    @Test
    public void everyZoneIsBiggerThanTheOldFortyEightPercentStrip() {
        // The old controls answered in the lower 48% of the panel, in thirds.
        for (Variant variant : new Variant[]{Variant.DEFAULT, Variant.ALTERNATE,
                Variant.FOURTH, Variant.FIFTH}) {
            for (Action action : Action.values()) {
                float[] zone = MediaPanelLayout.zone(variant, action);
                float area = (zone[2] - zone[0]) * (zone[3] - zone[1]);
                org.junit.Assert.assertTrue(variant + " " + action,
                        area >= (1f / 3f) * 0.48f - 1e-4f);
            }
        }
    }

    @Test
    public void emptyPanelAnswersNothing() {
        assertNull(MediaPanelLayout.hit(Variant.DEFAULT, 1f, 1f, 0f, 0f));
    }
}
