package com.tpkarras.mirror2rearultra;

/** The arithmetic of the countdown before a shot taken from the panel. */
final class ShutterCountdown {
    /** The lengths offered, in seconds; nought takes the shot at once. */
    static final int[] CHOICES = {0, 3, 5, 10};

    /**
     * The figure on the panel: rounded up, so a three second count reads
     * 3, 2, 1 and the shot comes as the 1 goes, rather than showing a 0 that
     * lingers for a second before anything happens.
     */
    static int secondsShown(long remainingMillis) {
        return remainingMillis <= 0L ? 0 : (int) ((remainingMillis + 999L) / 1_000L);
    }

    /** How long until the next whole second, when the figure changes. */
    static long untilNextSecond(long remainingMillis) {
        long part = remainingMillis % 1_000L;
        return part == 0L ? 1_000L : part;
    }

    /**
     * Degrees to turn the figure so it reads the right way up for the person
     * being photographed, the same turn the dashboard's pages take.
     *
     * @param quarterTurn from {@link PanelPostureSensor}, or its unknown value
     */
    static float rotationFor(int quarterTurn) {
        return quarterTurn == 1 ? 90f : quarterTurn == 3 ? 270f : 0f;
    }

    /** One of {@link #CHOICES}, so a stored value from elsewhere cannot run a minute. */
    static int normalized(int seconds) {
        for (int choice : CHOICES) {
            if (choice == seconds) return seconds;
        }
        return 0;
    }

    private ShutterCountdown() {}
}
