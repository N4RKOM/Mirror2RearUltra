package com.tpkarras.mirror2rearultra;

/**
 * How near a finger has to be to count as landing on something.
 *
 * <p>The panel is 126 pixels across and a fingertip covers most of it, so a
 * widget drawn as one short line has to answer to a touch a little outside
 * itself or it could not be hit at all. What it must not do is answer to a
 * touch far outside itself, which is what the clock did: the allowance used
 * to be a share of the widget's own height, so the largest thing on the panel
 * claimed the most room around it and took touches meant for its neighbours.
 *
 * <p>The allowance is now what is missing from a minimum target and nothing
 * more, so it shrinks to nothing as a widget grows, and where two widgets
 * both answer it is the nearer one that wins rather than the later-drawn.
 */
final class TouchTargets {
    private TouchTargets() {
    }

    /**
     * How far a point is from a box, or -1 when it is out of the box's reach.
     *
     * <p>A box narrower or shorter than {@code minimumSize} reaches out to
     * that size, centred where it is. A box already that big reaches no
     * further than its own edges.
     *
     * @return 0 for a point inside the box, how far outside it while still
     *     within reach, and -1 beyond that
     */
    static float missDistance(
            float left,
            float top,
            float right,
            float bottom,
            float x,
            float y,
            float minimumSize
    ) {
        float overX = Math.max(left - x, x - right);
        float overY = Math.max(top - y, y - bottom);
        if (overX <= 0f && overY <= 0f) {
            return 0f;
        }
        float slackX = Math.max(0f, (minimumSize - (right - left)) / 2f);
        float slackY = Math.max(0f, (minimumSize - (bottom - top)) / 2f);
        if (overX > slackX || overY > slackY) {
            return -1f;
        }
        return Math.max(overX, 0f) + Math.max(overY, 0f);
    }
}
