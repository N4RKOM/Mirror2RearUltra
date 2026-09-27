package com.tpkarras.mirror2rearultra;

import android.graphics.Color;

/**
 * When the panel stands in for the notification light this phone does not
 * have, and what its rings say.
 *
 * <p>The rings carry two things. How many there are is how many notifications
 * came while the main screen was off, so a glance tells one message from a
 * pile of them. Their colour is the app's own, so WhatsApp's green or
 * Telegram's blue is known before the icon in the middle is made out.
 */
final class NotificationPulse {
    /** One wave out through the rings takes this long, and runs this many times. */
    static final long WAVE_MILLIS = 1_400L;
    static final int WAVES = 3;
    static final long DURATION_MILLIS = WAVE_MILLIS * WAVES;
    /** More than this would not fit between the icon and the edge, nor be counted at a glance. */
    static final int MAX_RINGS = 5;

    /**
     * Only with the main screen off: with it on, the notification is in
     * front of its owner already. And never where nobody would see it or it
     * would be in the way - a pocket, the panel against the table, a call
     * ringing on it, or a camera being framed on it.
     */
    static boolean shouldPulse(boolean enabled, boolean mainScreenOn, boolean pocketed,
            boolean panelFacingDown, boolean callRinging, boolean imageShown) {
        return enabled && !mainScreenOn && !pocketed && !panelFacingDown
                && !callRinging && !imageShown;
    }

    /** How many rings to draw for this many notifications not yet seen. */
    static int ringsFor(int unseen) {
        return Math.max(1, Math.min(MAX_RINGS, unseen));
    }

    /** How far through its current wave the pulse is, 0 to 1, or -1 once all have run. */
    static float waveProgress(long elapsedMillis) {
        if (elapsedMillis < 0L || elapsedMillis >= DURATION_MILLIS) {
            return -1f;
        }
        return (elapsedMillis % WAVE_MILLIS) / (float) WAVE_MILLIS;
    }

    /**
     * How bright one ring is as the wave passes, 0.3 to 1.
     *
     * <p>Never out entirely: every ring stays there to be counted, and the
     * wave only brightens each in turn, from the icon outwards.
     */
    static float ringBrightness(float wave, int ring, int rings) {
        float centre = (ring + 0.5f) / rings;
        float distance = Math.abs(wave - centre) * rings;
        return 0.3f + 0.7f * Math.max(0f, 1f - distance);
    }

    /**
     * The app's colour, made fit to glow on black.
     *
     * <p>Too dark and it would not show on the panel; too grey and it would
     * say nothing about which app it is. Those come out white.
     */
    static int glowColour(int colour) {
        int alpha = colour >>> 24;
        int red = (colour >> 16) & 0xff;
        int green = (colour >> 8) & 0xff;
        int blue = colour & 0xff;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        if (colour == 0 || alpha < 128 || max == 0 || (max - min) / (float) max < 0.25f) {
            return Color.WHITE;
        }
        // Brightened by scaling all three together, which keeps the hue and
        // how strong it is.
        float scale = Math.max(1f, 0.85f * 255f / max);
        return 0xff000000
                | Math.min(255, Math.round(red * scale)) << 16
                | Math.min(255, Math.round(green * scale)) << 8
                | Math.min(255, Math.round(blue * scale));
    }

    /**
     * The colour an icon mostly is, for an app that gave its notification
     * none: every strongly coloured pixel counted, weighted by how strong it
     * is, so a white or grey background does not wash it out.
     *
     * @param pixels ARGB, as a bitmap gives them
     * @return 0 when the icon has no colour to speak of
     */
    static int dominantColour(int[] pixels) {
        double red = 0d;
        double green = 0d;
        double blue = 0d;
        double weight = 0d;
        for (int pixel : pixels) {
            if ((pixel >>> 24) < 128) continue;
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            if (max == 0) continue;
            double strength = (max - min) / (double) max;
            if (strength < 0.25d) continue;
            red += r * strength;
            green += g * strength;
            blue += b * strength;
            weight += strength;
        }
        if (weight == 0d) {
            return 0;
        }
        return 0xff000000 | (int) Math.round(red / weight) << 16
                | (int) Math.round(green / weight) << 8 | (int) Math.round(blue / weight);
    }

    private NotificationPulse() {}
}
