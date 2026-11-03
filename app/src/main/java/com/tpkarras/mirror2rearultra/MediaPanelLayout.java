package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

/**
 * Where the three media controls sit on the full-screen player, for each of its
 * looks.
 *
 * <p>One table answers both questions - where to draw a control and what a
 * finger on a given spot means - so the two cannot drift apart: the zone drawn
 * is the zone that answers. The zones touch, with no dead strip between them,
 * and are given as fractions of the panel, so the same table serves a panel
 * lying down and one standing up.
 */
final class MediaPanelLayout {
    enum Action { PREVIOUS, PLAY_PAUSE, NEXT }

    /** The cover of the cover look: its own square, a tap on which plays or pauses. */
    static final float COVER_RIGHT = 126f / 294f;

    private static final float[] THIRDS = {0f, 1f / 3f, 2f / 3f, 1f};
    private static final float[] SIDES_SMALLER = {0f, 0.30f, 0.70f, 1f};
    private static final float[] BESIDE_COVER = {COVER_RIGHT, 178f / 294f, 242f / 294f, 1f};

    private MediaPanelLayout() {}

    /** Where the controls start, as a fraction of the height. */
    static float controlsTop(DashboardWidgetLayout.Variant variant) {
        switch (variant) {
            case ALTERNATE: return 0.35f;
            case DETAILED: return 0.40f;
            case FOURTH: return 0f;
            case FIFTH: return 0.30f;
            default: return 0f;
        }
    }

    private static float[] columns(DashboardWidgetLayout.Variant variant) {
        switch (variant) {
            case DETAILED: return BESIDE_COVER;
            case FOURTH:
            case FIFTH: return SIDES_SMALLER;
            default: return THIRDS;
        }
    }

    /** The zone of {@code action} as {left, top, right, bottom}, in fractions of the panel. */
    static float[] zone(DashboardWidgetLayout.Variant variant, Action action) {
        float[] edges = columns(variant);
        int column = action.ordinal();
        return new float[]{edges[column], controlsTop(variant), edges[column + 1], 1f};
    }

    /**
     * What a touch at ({@code x}, {@code y}) in a panel {@code width} by
     * {@code height} asks for, or null when it lands on something that is not a
     * control.
     */
    @Nullable
    static Action hit(DashboardWidgetLayout.Variant variant, float x, float y,
            float width, float height) {
        if (width <= 0f || height <= 0f) {
            return null;
        }
        float fx = x / width;
        float fy = y / height;
        if (variant == DashboardWidgetLayout.Variant.DETAILED && fx < COVER_RIGHT) {
            return Action.PLAY_PAUSE;
        }
        if (fy < controlsTop(variant)) {
            return null;
        }
        float[] edges = columns(variant);
        if (fx < edges[1]) return Action.PREVIOUS;
        if (fx < edges[2]) return Action.PLAY_PAUSE;
        return Action.NEXT;
    }
}
