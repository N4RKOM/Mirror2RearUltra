package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * Turns the ambient light reading into a fraction of the chosen brightness.
 *
 * <p>It scales rather than replaces. The brightness slider keeps meaning what
 * it meant - the most the panel will ever give - and this only takes it down
 * indoors and at night, which is the complaint: a panel set for daylight is
 * blinding in a dark room.
 *
 * <p>Two things make the sensor unusable raw. It is noisy, so the value is
 * smoothed rather than followed; and applying a brightness spawns a root
 * shell, so a reading that moves the result by a percent or two is not worth
 * acting on. Both are handled here so the caller can treat every callback as
 * something worth doing.
 */
final class AutoBrightnessSensor implements SensorEventListener {
    interface Listener {
        void onAmbientFactorChanged(float factor);
    }

    /**
     * How dark the panel is allowed to get, as a share of the chosen
     * brightness. Below roughly this it stops being readable at arm's length,
     * and the AOD floor is a separate setting for when it should go dimmer.
     */
    private static final float MIN_FACTOR = 0.15f;
    /** Daylight indoors. Above this the panel gets everything it was given. */
    private static final float FULL_LUX = 1_000f;
    /** Weight of a new reading; the rest is what the sensor said before. */
    private static final float SMOOTHING = 0.18f;
    /** A change worth a root shell. */
    private static final float MIN_STEP = 0.04f;
    private static final long MIN_INTERVAL_MILLIS = 1_500L;

    private final SensorManager sensorManager;
    private final Sensor lightSensor;
    private final Listener listener;

    private float smoothedLux = -1f;
    private float reportedFactor = -1f;
    private long reportedAt;
    private boolean running;

    AutoBrightnessSensor(Context context, Listener listener) {
        this.listener = listener;
        sensorManager = context.getSystemService(SensorManager.class);
        lightSensor = sensorManager == null
                ? null : sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
    }

    /** Whether this device has anything to read. */
    boolean isAvailable() {
        return lightSensor != null;
    }

    void start() {
        if (running || lightSensor == null) {
            return;
        }
        running = true;
        smoothedLux = -1f;
        reportedFactor = -1f;
        reportedAt = 0L;
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void stop() {
        if (!running) {
            return;
        }
        running = false;
        sensorManager.unregisterListener(this);
    }

    /** The factor as it stands, for a caller reapplying brightness on its own. */
    float currentFactor() {
        return reportedFactor < 0f ? 1f : reportedFactor;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values == null || event.values.length == 0) {
            return;
        }
        float lux = Math.max(0f, event.values[0]);
        smoothedLux = smoothedLux < 0f ? lux : smoothedLux + (lux - smoothedLux) * SMOOTHING;
        float factor = factorFor(smoothedLux);
        long now = SystemClock.elapsedRealtime();
        boolean first = reportedFactor < 0f;
        if (!first && (Math.abs(factor - reportedFactor) < MIN_STEP
                || now - reportedAt < MIN_INTERVAL_MILLIS)) {
            return;
        }
        reportedFactor = factor;
        reportedAt = now;
        listener.onAmbientFactorChanged(factor);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Logarithmic, because that is how the eye reads a room: the step from a
     * dark room to a lit one matters, the step from bright to brighter does
     * not.
     */
    private static float factorFor(float lux) {
        double share = Math.log10(lux + 1d) / Math.log10(FULL_LUX + 1d);
        return (float) Math.max(MIN_FACTOR, Math.min(1d, share));
    }
}
