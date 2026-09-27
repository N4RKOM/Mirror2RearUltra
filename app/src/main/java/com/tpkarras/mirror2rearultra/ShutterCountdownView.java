package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * The seconds before a shot, over the mirrored image.
 *
 * <p>A figure in a dark disc, so it reads over any picture, and a ring round
 * it that runs down with the time: the figure says how many seconds, the ring
 * how far into this one. Only drawing; {@link Mirror} keeps the time and takes
 * the shot.
 */
public final class ShutterCountdownView extends View {
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint figure = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF arc = new RectF();
    private long endsAt;
    private boolean running;

    public ShutterCountdownView(Context context) {
        this(context, null);
    }

    public ShutterCountdownView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        disc.setColor(Color.argb(150, 0, 0, 0));
        ring.setColor(Color.WHITE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        figure.setColor(Color.WHITE);
        figure.setTextAlign(Paint.Align.CENTER);
        figure.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        figure.setFontFeatureSettings("tnum");
    }

    /** @param endsAtUptime in {@link SystemClock#uptimeMillis} time */
    void start(long endsAtUptime) {
        endsAt = endsAtUptime;
        running = true;
        setVisibility(VISIBLE);
        invalidate();
    }

    void stop() {
        running = false;
        setVisibility(GONE);
    }

    boolean isRunning() {
        return running;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!running) {
            return;
        }
        long remaining = Math.max(0L, endsAt - SystemClock.uptimeMillis());
        float side = Math.min(getWidth(), getHeight());
        float centreX = getWidth() / 2f;
        float centreY = getHeight() / 2f;
        float radius = side * 0.40f;
        canvas.drawCircle(centreX, centreY, radius, disc);
        float stroke = Math.max(2f, side * 0.045f);
        ring.setStrokeWidth(stroke);
        float inner = radius - stroke;
        arc.set(centreX - inner, centreY - inner, centreX + inner, centreY + inner);
        // What is left of the current second, so the ring empties once a second.
        float share = ShutterCountdown.secondsShown(remaining) == 0 ? 0f
                : (remaining % 1_000L == 0L ? 1_000L : remaining % 1_000L) / 1_000f;
        canvas.drawArc(arc, -90f, 360f * share, false, ring);
        figure.setTextSize(side * 0.46f);
        Paint.FontMetrics metrics = figure.getFontMetrics();
        float baseline = centreY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(String.valueOf(ShutterCountdown.secondsShown(remaining)),
                centreX, baseline, figure);
        postInvalidateOnAnimation();
    }
}
