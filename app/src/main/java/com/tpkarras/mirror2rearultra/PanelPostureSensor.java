package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * Which way the panel is facing, and whether anything is in front of the phone.
 *
 * <p>Two things the panel could not tell before. One: it is face down on a
 * table, showing the table - the accelerometer's Z axis points out of the
 * screen, so the panel is the side underneath whenever that axis reads
 * positive. Two: it is in a pocket, where every touch is the lining of the
 * pocket rather than a finger.
 *
 * <p>Proximity alone would not do for the pocket. A phone lying screen down on
 * a desk reads "near" just as a pocket does, and that is precisely when the
 * panel is up and worth touching - so the reading only counts while the phone
 * is off the flat, which is how it sits in a pocket and is not how it sits on
 * a table.
 *
 * <p>Both readings have to hold still before they are believed. The panel
 * going dark because the phone was picked up at an angle for half a second
 * would be worse than it never going dark at all.
 */
final class PanelPostureSensor implements SensorEventListener {
    interface Listener {
        /**
         * @param quarterTurn which way up the phone is being held, counted in
         *     quarter turns clockwise from upright, or {@link #TURN_UNKNOWN}
         *     while it is lying too flat to tell
         */
        void onPostureChanged(boolean pocketed, boolean panelDown, int quarterTurn);
    }

    /** Flat on a table: gravity says which way is down but not which way is round. */
    static final int TURN_UNKNOWN = -1;

    /** Gravity, near enough, with room for the hand that is holding it. */
    private static final float FACE_DOWN_ENTER = 7.5f;
    private static final float FACE_DOWN_LEAVE = 5.5f;
    /** Above this on either side the phone is flat, and so is not in a pocket. */
    private static final float FLAT = 6.5f;
    /**
     * How far from upright the phone has to lean before it counts as turned.
     *
     * <p>Generous on purpose. Nothing is worse than a panel that spins while
     * it is being read, and holding still is always a safe answer: the last
     * turn is kept until a new one is unmistakable.
     */
    private static final float TURNED = 6f;
    /** How long a reading has to hold before it is acted on. */
    private static final long SETTLE_MILLIS = 900L;
    /**
     * The same, for which way up the phone is - and much shorter.
     *
     * <p>The other two readings are worth being slow about: a panel that goes
     * dark or stops taking touches by mistake is a nuisance to undo. A page
     * turned the wrong way is neither - it is obvious the moment it happens
     * and it rights itself as soon as the phone settles.
     */
    private static final long TURN_SETTLE_MILLIS = 250L;
    /** Every reading moves the average by this much, which steadies the hand. */
    private static final float SMOOTHING = 0.25f;

    private final SensorManager sensors;
    private final Sensor accelerometer;
    private final Sensor proximity;
    private final Listener listener;

    private Float smoothedX;
    private Float smoothedY;
    private Float smoothedZ;
    private boolean covered;
    private boolean pocketed;
    private boolean panelDown;
    private int quarterTurn = TURN_UNKNOWN;
    private boolean candidatePocketed;
    private boolean candidatePanelDown;
    private int candidateTurn = TURN_UNKNOWN;
    private long candidateSince;
    private long turnSince;
    private boolean running;
    /** Cleared on every start, so the first settled reading is always told. */
    private boolean reported;

    PanelPostureSensor(Context context, Listener listener) {
        this.listener = listener;
        this.sensors = context.getSystemService(SensorManager.class);
        this.accelerometer = sensors == null ? null
                : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.proximity = sensors == null ? null
                : sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    /** @return whether there is anything to listen to */
    boolean start() {
        if (running || sensors == null || accelerometer == null) {
            return running;
        }
        // Faster than the other sensors here: the smoothing needs several
        // readings before it crosses a threshold, and at one every fifth of
        // a second that wait was longer than the settling that follows it.
        sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (proximity != null) {
            sensors.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);
        }
        running = true;
        reported = false;
        return true;
    }

    void stop() {
        if (!running) {
            return;
        }
        sensors.unregisterListener(this);
        running = false;
        smoothedX = null;
        smoothedY = null;
        smoothedZ = null;
        covered = false;
        candidateSince = 0L;
        turnSince = 0L;
        // Nothing is reported on the way out. Stopping is not a reading, and
        // a session that stops and starts - which is what happens when the
        // panel goes dark - would otherwise be told the phone had been picked
        // up and put down again on every cycle.
        pocketed = false;
        panelDown = false;
        quarterTurn = TURN_UNKNOWN;
        candidatePocketed = false;
        candidatePanelDown = false;
        candidateTurn = TURN_UNKNOWN;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            // Some report centimetres and some report only nought or their
            // maximum, so anything under half the range counts as covered.
            covered = event.values[0] < Math.max(1f, event.sensor.getMaximumRange() / 2f);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            smoothedX = smooth(smoothedX, event.values[0]);
            smoothedY = smooth(smoothedY, event.values[1]);
            smoothedZ = smooth(smoothedZ, event.values[2]);
        } else {
            return;
        }
        settle();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private static Float smooth(Float average, float reading) {
        return average == null ? reading : average + (reading - average) * SMOOTHING;
    }

    /**
     * Which way up the phone is, in quarter turns clockwise from upright.
     *
     * <p>Whichever way the phone leans, that side is down and the opposite one
     * is up. Lying flat it leans no way at all, so the last answer stands.
     */
    static int turnOf(float x, float y, int previous) {
        if (Math.abs(y) > TURNED && Math.abs(y) > Math.abs(x)) {
            return y > 0f ? 0 : 2;
        }
        if (Math.abs(x) > TURNED) {
            // Leaning onto its left edge puts the left side up, which is one
            // quarter turn clockwise.
            return x < 0f ? 1 : 3;
        }
        return previous;
    }

    private void settle() {
        if (smoothedZ == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        noteCandidates(now);
        // The first word after a start is always sent, even when it says
        // nothing has changed: whoever is listening was told nothing on the
        // way out, and would otherwise hold the last posture for ever.
        if (!reported) {
            if (now - candidateSince < SETTLE_MILLIS
                    || now - turnSince < TURN_SETTLE_MILLIS) {
                return;
            }
            pocketed = candidatePocketed;
            panelDown = candidatePanelDown;
            quarterTurn = candidateTurn;
            reported = true;
            listener.onPostureChanged(pocketed, panelDown, quarterTurn);
            return;
        }
        // Afterwards each half keeps its own clock, so the turn is not held up
        // behind readings that are deliberately slow.
        boolean postureReady = (candidatePocketed != pocketed || candidatePanelDown != panelDown)
                && now - candidateSince >= SETTLE_MILLIS;
        boolean turnReady = candidateTurn != quarterTurn
                && now - turnSince >= TURN_SETTLE_MILLIS;
        if (!postureReady && !turnReady) {
            return;
        }
        if (postureReady) {
            pocketed = candidatePocketed;
            panelDown = candidatePanelDown;
        }
        if (turnReady) {
            quarterTurn = candidateTurn;
        }
        listener.onPostureChanged(pocketed, panelDown, quarterTurn);
    }

    private void noteCandidates(long now) {
        float z = smoothedZ;
        // Hysteresis, so a phone resting near the threshold does not flicker.
        boolean nextPanelDown = panelDown ? z > FACE_DOWN_LEAVE : z > FACE_DOWN_ENTER;
        boolean nextPocketed = covered && Math.abs(z) < FLAT;
        if (nextPocketed != candidatePocketed || nextPanelDown != candidatePanelDown) {
            candidatePocketed = nextPocketed;
            candidatePanelDown = nextPanelDown;
            candidateSince = now;
        }
        int nextTurn = smoothedX == null || smoothedY == null
                ? quarterTurn : turnOf(smoothedX, smoothedY, quarterTurn);
        if (nextTurn != candidateTurn) {
            candidateTurn = nextTurn;
            turnSince = now;
        }
    }
}
