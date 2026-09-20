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
    /** Between an encoded pixel and the light it becomes. */
    private static final double SRGB_GAMMA = 2.2;
    /**
     * The dimmest the panel is worth setting to.
     *
     * <p>Not a hardware limit - the backlight takes smaller numbers happily -
     * but below about a tenth of full output this panel gives too little light
     * to read, and the stock rear-panel app pins its own slider to the same
     * figure and pulls anything lower up to it. Asking for less than this is
     * answered by veiling the picture instead; see {@link #veilAlpha}.
     */
    static final float MINIMUM_SHARE = 0.1f;

    private PerceptualBrightness() {
    }

    /**
     * What to set the panel to for a slider position.
     *
     * <p>The curve is laid over the range the panel is worth using rather than
     * over all of it, so the bottom of the slider lands on the dimmest useful
     * light instead of on a number that emits almost nothing.
     */
    static float toLinear(int percent) {
        return MINIMUM_SHARE + (1f - MINIMUM_SHARE) * curve(percent);
    }

    /** The slider position that sets the panel to a given share of full output. */
    static int toPercent(float linear) {
        float share = (constrain(linear, MINIMUM_SHARE, 1f) - MINIMUM_SHARE)
                / (1f - MINIMUM_SHARE);
        float value = share * RANGE;
        float position = value <= 1f
                ? (float) Math.sqrt(value) * R
                : A * (float) Math.log(value - B) + C;
        return Math.round(constrain(position, 0f, 1f) * 100f);
    }

    /**
     * The share of full output a position asks for, floor or no floor.
     *
     * <p>AOD lives below anything the panel will show on its own, so it asks
     * in these terms and lets {@link #veilAlpha} make up the difference.
     */
    static float toRequestedShare(int percent) {
        return curve(percent);
    }

    /** The curve itself, over the whole of it: nought to one. */
    private static float curve(int percent) {
        float position = constrain(percent / 100f, 0f, 1f);
        float value = position <= R
                ? square(position / R)
                : (float) Math.exp((position - C) / A) + B;
        return constrain(value, 0f, RANGE) / RANGE;
    }

    /**
     * How much an overlay must hide when the panel cannot get dim enough.
     *
     * <p>A backlight has a finite number of steps and its lowest one is still
     * brighter than the dimmest settings ask for. What is left below that
     * floor has to come out of the picture instead, and an overlay takes its
     * bite out of encoded pixels while the eye reads the light behind them -
     * so the share it has to remove is not the share of light that is wanted.
     *
     * @param wanted share of full output asked for
     * @param panel  share the panel is actually set to give
     * @return the alpha of a black overlay, or zero when the panel can do it
     */
    static float veilAlpha(float wanted, float panel) {
        if (panel <= 0f || wanted >= panel) {
            return 0f;
        }
        return 1f - (float) Math.pow(Math.max(wanted, 0f) / panel, 1 / SRGB_GAMMA);
    }

    private static float square(float value) {
        return value * value;
    }

    private static float constrain(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
