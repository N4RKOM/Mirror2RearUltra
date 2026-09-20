package com.tpkarras.mirror2rearultra;

/**
 * The curve between what a brightness slider says and what the panel emits.
 *
 * <p>A backlight value is a share of the panel's light output, and the eye
 * does not read light output evenly: the step from 1% to 2% is plainly
 * visible, the step from 90% to 100% is barely there. A slider that hands its
 * position straight to the hardware therefore spends most of its travel on
 * differences nobody can see, and puts the whole range that matters at night -
 * everything under a twentieth of full output - in the last sliver before the
 * bottom stop, where this app could not reach it at all, because the slider
 * stops at ten.
 *
 * <p>So the slider's position is read as a perceived brightness and converted
 * here. The curve is the hybrid log-gamma one Android uses for the main
 * screen, and the stock rear-panel app uses for this very panel: squared below
 * the midpoint, exponential above it, joined so that both halves meet without
 * a kink. Half the travel now covers the bottom twelfth of the output, which
 * is where a panel on a bedside table lives.
 */
final class PerceptualBrightness {
    private static final float A = 0.17883277f;
    private static final float B = 0.28466892f;
    private static final float C = 0.5599107f;
    /** Where the squared half gives way to the exponential one. */
    private static final float R = 0.5f;
    /** The curve is defined over a range of twelve, and normalised from it. */
    private static final float RANGE = 12f;

    private PerceptualBrightness() {
    }

    /** What share of the panel's full output a slider position asks for. */
    static float toLinear(int percent) {
        float position = constrain(percent / 100f, 0f, 1f);
        float value = position <= R
                ? square(position / R)
                : (float) Math.exp((position - C) / A) + B;
        return constrain(value, 0f, RANGE) / RANGE;
    }

    /** The slider position that asks for a given share of full output. */
    static int toPercent(float linear) {
        float value = constrain(linear, 0f, 1f) * RANGE;
        float position = value <= 1f
                ? (float) Math.sqrt(value) * R
                : A * (float) Math.log(value - B) + C;
        return Math.round(constrain(position, 0f, 1f) * 100f);
    }

    private static float square(float value) {
        return value * value;
    }

    private static float constrain(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
