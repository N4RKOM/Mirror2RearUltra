package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * The panel as a notification light: the app's icon inside rings of its
 * colour, one ring for each notification that came while the screen was off.
 *
 * <p>The rings stay put, so they can be counted, and a wave of brightness
 * runs out through them from the icon, the way an LED pulses. Over a
 * near-black cover, so it reads on a panel dimmed for the night and stands
 * clear of the widgets under it. It runs for a few seconds and leaves the
 * widgets as they were.
 */
public final class NotificationPulseView extends View {
    private final Paint cover = new Paint();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private Drawable icon;
    private int glow = Color.WHITE;
    private int rings = 1;
    private long startedAt;
    private boolean running;

    public NotificationPulseView(Context context) {
        this(context, null);
    }

    public NotificationPulseView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        cover.setColor(Color.argb(225, 0, 0, 0));
        ring.setStyle(Paint.Style.STROKE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Starts again from the beginning if one is already running, with the
     * newer app's icon and colour.
     *
     * @param colour the notification's own, or 0 to take it from the icon
     * @param unseen how many have come since the screen was last looked at
     */
    void start(String packageName, int colour, int unseen) {
        try {
            icon = getContext().getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException gone) {
            icon = null;
        }
        if (colour == 0 && icon != null) {
            colour = NotificationPulse.dominantColour(sample(icon));
        }
        glow = NotificationPulse.glowColour(colour);
        rings = NotificationPulse.ringsFor(unseen);
        startedAt = SystemClock.uptimeMillis();
        running = true;
        setVisibility(VISIBLE);
        invalidate();
    }

    void stop() {
        running = false;
        icon = null;
        setVisibility(GONE);
    }

    boolean isRunning() {
        return running;
    }

    /** The icon's pixels at a size small enough to count quickly. */
    private static int[] sample(Drawable drawable) {
        int side = 24;
        Bitmap bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, side, side);
        drawable.draw(canvas);
        int[] pixels = new int[side * side];
        bitmap.getPixels(pixels, 0, side, 0, 0, side, side);
        bitmap.recycle();
        return pixels;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!running) {
            return;
        }
        float wave = NotificationPulse.waveProgress(SystemClock.uptimeMillis() - startedAt);
        if (wave < 0f) {
            stop();
            return;
        }
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), cover);
        float side = Math.min(getWidth(), getHeight());
        float centreX = getWidth() / 2f;
        float centreY = getHeight() / 2f;
        float inner = side * 0.27f;
        float outer = side * 0.48f;
        float spacing = (outer - inner) / rings;
        ring.setStrokeWidth(Math.max(1.5f, Math.min(side * 0.03f, spacing * 0.45f)));
        ring.setColor(glow);
        for (int index = 0; index < rings; index++) {
            ring.setAlpha(Math.round(255 * NotificationPulse.ringBrightness(wave, index, rings)));
            canvas.drawCircle(centreX, centreY, inner + spacing * (index + 0.5f), ring);
        }
        disc.setColor(glow);
        disc.setAlpha(55);
        canvas.drawCircle(centreX, centreY, inner * 0.92f, disc);
        Drawable shown = icon;
        if (shown != null) {
            int half = Math.round(side * 0.19f);
            shown.setBounds(Math.round(centreX) - half, Math.round(centreY) - half,
                    Math.round(centreX) + half, Math.round(centreY) + half);
            shown.draw(canvas);
        }
        postInvalidateOnAnimation();
    }
}
