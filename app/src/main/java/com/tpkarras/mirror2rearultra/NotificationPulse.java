package com.tpkarras.mirror2rearultra;

/**
 * When the panel stands in for the notification light this phone does not
 * have, and how its pulse runs.
 */
final class NotificationPulse {
    /** Three rings, each spreading out and fading over this long. */
    static final long RING_MILLIS = 1_400L;
    static final int RINGS = 3;
    static final long DURATION_MILLIS = RING_MILLIS * RINGS;

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

    /** How far through its own ring the pulse is, 0 to 1, or -1 once all have run. */
    static float ringProgress(long elapsedMillis) {
        if (elapsedMillis < 0L || elapsedMillis >= DURATION_MILLIS) {
            return -1f;
        }
        return (elapsedMillis % RING_MILLIS) / (float) RING_MILLIS;
    }

    private NotificationPulse() {}
}
