package com.tpkarras.mirror2rearultra;

import android.app.NotificationManager;
import android.media.AudioManager;

/** Whether the phone will ring, buzz or keep quiet, for the sound mode widget. */
final class SoundModeReading {
    enum Mode { SOUND, VIBRATE, SILENT, DO_NOT_DISTURB }

    final Mode mode;
    /** The ring volume, 0 to 100. */
    final int volumePercent;

    SoundModeReading(Mode mode, int volumePercent) {
        this.mode = mode;
        this.volumePercent = volumePercent;
    }

    /**
     * Do not disturb wins over the ringer: with it on, a phone set to ring
     * still stays quiet for almost everyone, and that is what the panel is
     * asked about. HyperOS's own silent switch is do not disturb underneath.
     */
    static Mode modeOf(int ringerMode, int interruptionFilter) {
        if (interruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
                || interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
                || interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
            return Mode.DO_NOT_DISTURB;
        }
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return Mode.SILENT;
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) return Mode.VIBRATE;
        return Mode.SOUND;
    }

    static int percentOf(int volume, int maximum) {
        return maximum <= 0 ? 0 : Math.round(Math.max(0, Math.min(volume, maximum)) * 100f / maximum);
    }
}
