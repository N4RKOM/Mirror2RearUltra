package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

/**
 * How far off level the phone is held, for lining up a shot on the main camera.
 *
 * <p>Axes as the sensors give them: X to the right and Y to the top of the
 * main screen, Z out of it. Gravity reads as the direction that is up.
 */
final class LevelReading {
    /**
     * Closer to flat than this and there is no horizon to hold: a phone on its
     * back could be turned any way round and still read the same.
     */
    private static final double FLAT_DEGREES = 65d;

    /**
     * Degrees to turn a line drawn on the panel so that it lies level, from
     * the nearest square: the same reading serves a phone held upright and one
     * held on its side.
     *
     * <p>Positive is clockwise in the panel's own drawing, which is what
     * {@link android.graphics.Canvas#rotate} takes. The panel is looked at from
     * behind, so a phone turned clockwise as its owner sees it is turned the
     * other way as the panel sees it, and the line has to turn clockwise to
     * make up for that.
     */
    final float rollDegrees;
    /** How far the phone leans towards or away from its subject, either way. */
    final float pitchDegrees;

    LevelReading(float rollDegrees, float pitchDegrees) {
        this.rollDegrees = rollDegrees;
        this.pitchDegrees = pitchDegrees;
    }

    /** Level to the nearest degree, which is what the figure shows as nought. */
    boolean isLevel() {
        return Math.abs(rollDegrees) < 0.5f;
    }

    @Nullable
    static LevelReading fromGravity(float x, float y, float z) {
        double across = Math.hypot(x, y);
        double pitch = Math.toDegrees(Math.atan2(Math.abs(z), across));
        if (across <= 0d || pitch > FLAT_DEGREES) {
            return null;
        }
        // Clockwise as the main screen is read: tipping the top to the right
        // turns gravity towards negative X.
        double turn = Math.toDegrees(Math.atan2(-x, y));
        double roll = turn - 90d * Math.round(turn / 90d);
        return new LevelReading((float) roll, (float) pitch);
    }
}
