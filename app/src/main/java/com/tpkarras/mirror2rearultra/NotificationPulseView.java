package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * The panel as a notification light: the app's icon, with rings spreading
 * out from it the way an LED pulses.
 *
 * <p>Over a near-black cover, so it reads on a panel dimmed for the night
 * and stands clear of the widgets under it. It runs for a few seconds and
 * leaves the widgets as they were; the counter and the newest notification
 * are theirs to keep showing.
 */
public final class NotificationPulseView extends View {
    private final Paint cover = new Paint();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private Drawable icon;
    private long startedAt;
    private boolean running;

    public NotificationPulseView(Context context) {
        this(context, null);
    }

    public NotificationPulseView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        cover.setColor(Color.argb(225, 0, 0, 0));
        ring.setColor(Color.WHITE);
        ring.setStyle(Paint.Style.STROKE);
        disc.setColor(Color.argb(60, 255, 255, 255));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Starts again from the beginning if one is already running, with the newer app's icon. */
    void start(String packageName) {
        try {
            icon = getContext().getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException gone) {
            icon = null;
        }
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!running) {
            return;
        }
        float progress = NotificationPulse.ringProgress(SystemClock.uptimeMillis() - startedAt);
        if (progress < 0f) {
            stop();
            return;
        }
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), cover);
        float side = Math.min(getWidth(), getHeight());
        float centreX = getWidth() / 2f;
        float centreY = getHeight() / 2f;
        float inner = side * 0.26f;
        float outer = side * 0.49f;
        // Eased out, so each ring leaves quickly and slows as it fades.
        float eased = 1f - (1f - progress) * (1f - progress);
        ring.setStrokeWidth(Math.max(1.5f, side * 0.035f * (1f - progress)));
        ring.setAlpha(Math.round(255 * (1f - progress)));
        canvas.drawCircle(centreX, centreY, inner + (outer - inner) * eased, ring);
        canvas.drawCircle(centreX, centreY, inner, disc);
        Drawable shown = icon;
        if (shown != null) {
            int half = Math.round(side * 0.20f);
            shown.setBounds(Math.round(centreX) - half, Math.round(centreY) - half,
                    Math.round(centreX) + half, Math.round(centreY) + half);
            shown.draw(canvas);
        }
        postInvalidateOnAnimation();
    }
}
