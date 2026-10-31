package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.view.Display;

import androidx.annotation.Nullable;

/**
 * What a preview of the rear panel needs from the phone: the panel's own
 * shape and density, and a snapshot of readings to draw it with.
 *
 * <p>Shared by the builder and the widgets tab, so the two draw the same
 * panel from the same numbers.
 */
final class PreviewSupport {
    private PreviewSupport() { }

    /**
     * Fills the preview from live state where the app already has it.
     *
     * <p>Battery, temperature, charging, the current track, the clock, the
     * profile name, the network, memory and storage are real. Weather,
     * heading, speed, altitude, the next event, today's steps, the charger,
     * the sun and the level stand in: those come from sensors, a network
     * fetch and a content query that a preview does not start. The charger
     * stands in even when unplugged, for the same reason as the counters
     * below.
     *
     * <p>The system counters are stood in for rather than left at "no
     * reading": a widget with no reading is dropped, so a page made of them
     * would look empty here while the panel showed it.
     */
    static RearDashboardSnapshot snapshot(Context context) {
        MediaWidgetState.Snapshot media = MediaWidgetState.get();
        boolean playing = media != null && media.hasMedia();
        // Read the battery straight from the sticky broadcast rather than from
        // DeviceHealthState: that is filled by DeviceHealthMonitor, which only
        // runs while the protection screen or the mirroring service is up, so
        // in a preview it is almost always unknown.
        Intent battery = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPercent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : 72;
        int temperature = battery == null
                ? 365 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 365);
        int status = battery == null
                ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        SystemStats stats = SystemStats.read(context);
        NotificationWidgetState.Snapshot notification = NotificationWidgetState.get();
        return new RearDashboardSnapshot(
                System.currentTimeMillis(),
                batteryPercent,
                temperature,
                charging,
                "",
                18,
                1,
                false,
                System.currentTimeMillis() + 3_600_000L,
                playing ? media.title : context.getString(R.string.dashboard_builder_sample_track),
                playing ? media.artist : "",
                media != null && media.playing,
                45f,
                8.3f,
                120.0,
                754_000L,
                MirrorSettings.profileDisplayName(context, MirrorSettings.loadActiveProfile(context)),
                stats.networkSummary,
                stats.memoryPercent,
                stats.storagePercentFree,
                context.getString(R.string.dashboard_builder_sample_event),
                System.currentTimeMillis() + 5_400_000L,
                4_820,
                notification.hasContent() ? notification.app
                        : context.getString(R.string.dashboard_builder_sample_notification_app),
                notification.hasContent() ? notification.title
                        : context.getString(R.string.dashboard_builder_sample_notification),
                notification.hasContent() ? notification.text : "",
                new ChargeReading(2_100, 8_200, 52 * 60_000L, false),
                new SunTimes.Next(System.currentTimeMillis() - 4 * 3_600_000L,
                        System.currentTimeMillis() + 135 * 60_000L, false),
                new LevelReading(2.4f, 2.4f, 6f),
                // Vibrate rather than ringing: a ringing phone drops the row
                // unless it is set to stay, and the preview would be empty.
                new SoundModeReading(SoundModeReading.Mode.VIBRATE, 60),
                new ExtraReadings(4.6f, 34, 1_250_000L, 42_000L, 1013.2f, 1));
    }

    /**
     * Density of the rear panel, or zero when it cannot be read.
     *
     * <p>Its own, not the calling screen's: the preview sizes its text against
     * the panel's dp, which is a different size from the phone's.
     */
    static float panelDensity(Context context) {
        Display display = rearDisplay(context);
        if (display == null) {
            return 0f;
        }
        // The display's own metrics rather than a context's: a display context
        // reports the density the screen was configured with, which is the
        // phone's, and sizing the preview against that made it a drawing of
        // the wrong panel.
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        display.getRealMetrics(metrics);
        return metrics.density;
    }

    /** Physical size of the rear panel, or null when it cannot be read. */
    @Nullable
    static Point panelSize(Context context) {
        Display display = rearDisplay(context);
        if (display == null || display.getMode() == null) {
            return null;
        }
        int width = display.getMode().getPhysicalWidth();
        int height = display.getMode().getPhysicalHeight();
        return width > 0 && height > 0 ? new Point(width, height) : null;
    }

    @Nullable
    private static Display rearDisplay(Context context) {
        DisplayManager manager = context.getSystemService(DisplayManager.class);
        int rearDisplayId = DisplayActivity.findRearDisplayId(manager);
        if (manager == null || rearDisplayId == Display.INVALID_DISPLAY) {
            return null;
        }
        return manager.getDisplay(rearDisplayId);
    }
}
