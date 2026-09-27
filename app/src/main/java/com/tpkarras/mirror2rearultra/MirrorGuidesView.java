package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Framing aids over a mirrored camera: a rule-of-thirds grid and a level.
 *
 * <p>They are drawn on the panel only, over the copy of the viewfinder, so
 * they never reach the photo.
 *
 * <p>The level is a line that lies on the real horizon whichever way the phone
 * is held, between two short marks that keep to the phone's own sides. Held
 * square, the line meets the marks and both turn the yellow a camera app uses
 * for the same thing. The line turns by the whole angle rather than from the
 * nearest square, so it goes smoothly through 45 degrees; only the marks move
 * over to the other pair of sides there.
 *
 * <p>The gravity sensor runs only while the view can be seen with the level
 * switched on.
 */
public final class MirrorGuidesView extends View implements SensorEventListener {
    /** Yellow, as camera apps colour a level that is true. */
    private static final int LEVEL_COLOUR = Color.rgb(255, 202, 40);
    private static final long REFRESH_MILLIS = 33L;
    private static final float SMOOTHING = 0.3f;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marks = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private final SensorManager sensors;
    private boolean gridOn;
    private boolean levelOn;
    private boolean listening;
    private float visibleWidth;
    private float visibleHeight;
    @Nullable private float[] gravity;
    @Nullable private LevelReading level;
    private long drawnAt;

    public MirrorGuidesView(Context context) {
        this(context, null);
    }

    public MirrorGuidesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        sensors = context.getSystemService(SensorManager.class);
        float density = getResources().getDisplayMetrics().density;
        grid.setColor(Color.argb(110, 255, 255, 255));
        grid.setStrokeWidth(Math.max(1f, density * 0.5f));
        line.setStrokeWidth(Math.max(1.5f, density * 1.1f));
        line.setStrokeCap(Paint.Cap.ROUND);
        marks.setStrokeWidth(Math.max(1.5f, density * 1.1f));
        marks.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void configure(boolean showGrid, boolean showLevel) {
        if (gridOn == showGrid && levelOn == showLevel) {
            return;
        }
        gridOn = showGrid;
        levelOn = showLevel;
        updateSensor();
        invalidate();
    }

    boolean hasAnything() {
        return gridOn || levelOn;
    }

    /** The image's size where a frame leaves black bars round it; nought for the whole panel. */
    void setVisibleSize(float width, float height) {
        if (visibleWidth == width && visibleHeight == height) {
            return;
        }
        visibleWidth = width;
        visibleHeight = height;
        invalidate();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        updateSensor();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        listen(false);
    }

    private void updateSensor() {
        listen(levelOn && isAttachedToWindow() && isShown());
    }

    private void listen(boolean wanted) {
        if (wanted == listening || sensors == null) {
            return;
        }
        listening = wanted;
        if (!wanted) {
            sensors.unregisterListener(this);
            gravity = null;
            level = null;
            return;
        }
        Sensor down = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (down == null) {
            down = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        if (down != null) {
            sensors.registerListener(this, down, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values = event.values;
        if (gravity == null) {
            gravity = new float[]{values[0], values[1], values[2]};
        } else {
            for (int axis = 0; axis < 3; axis++) {
                gravity[axis] += (values[axis] - gravity[axis]) * SMOOTHING;
            }
        }
        long now = SystemClock.uptimeMillis();
        if (now - drawnAt < REFRESH_MILLIS) {
            return;
        }
        LevelReading reading = LevelReading.fromGravity(gravity[0], gravity[1], gravity[2]);
        boolean unchanged = reading == null ? level == null
                : level != null && Math.abs(reading.turnDegrees - level.turnDegrees) < 0.1f;
        if (unchanged) {
            return;
        }
        level = reading;
        drawnAt = now;
        invalidate();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }
        float imageWidth = visibleWidth > 0f ? Math.min(width, visibleWidth) : width;
        float imageHeight = visibleHeight > 0f ? Math.min(height, visibleHeight) : height;
        float left = (width - imageWidth) / 2f;
        float top = (height - imageHeight) / 2f;
        if (gridOn) {
            for (int third = 1; third <= 2; third++) {
                float x = left + imageWidth * third / 3f;
                float y = top + imageHeight * third / 3f;
                canvas.drawLine(x, top, x, top + imageHeight, grid);
                canvas.drawLine(left, y, left + imageWidth, y, grid);
            }
        }
        LevelReading reading = level;
        if (!levelOn || reading == null) {
            return;
        }
        boolean levelled = reading.isLevel();
        line.setColor(levelled ? LEVEL_COLOUR : Color.WHITE);
        marks.setColor(levelled ? LEVEL_COLOUR : Color.argb(170, 255, 255, 255));
        float centreX = width / 2f;
        float centreY = height / 2f;
        // The phone's side the horizon is nearest to, as the panel draws it.
        float square = reading.turnDegrees - reading.rollDegrees;
        double squareRadians = Math.toRadians(square);
        // Across the image along that side, whichever it is.
        float span = (float) (Math.abs(Math.cos(squareRadians)) * imageWidth
                + Math.abs(Math.sin(squareRadians)) * imageHeight);
        float half = span * 0.28f;
        float mark = span * 0.08f;
        int state = canvas.save();
        canvas.rotate(square, centreX, centreY);
        canvas.drawLine(centreX - half - mark, centreY, centreX - half, centreY, marks);
        canvas.drawLine(centreX + half, centreY, centreX + half + mark, centreY, marks);
        canvas.restoreToCount(state);
        state = canvas.save();
        canvas.rotate(reading.turnDegrees, centreX, centreY);
        canvas.drawLine(centreX - half, centreY, centreX + half, centreY, line);
        canvas.restoreToCount(state);
    }
}
