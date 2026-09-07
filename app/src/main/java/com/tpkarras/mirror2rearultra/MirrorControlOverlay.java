package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

/** Movable main-screen control shown while an app assigned to a profile is in front. */
final class MirrorControlOverlay {
    interface Listener { void onToggleRequested(); }

    private static final String PREFS = "mirror_control_overlay";
    private static final String X = "x";
    private static final String Y = "y";

    private final Context context;
    private final WindowManager windowManager;
    private final Listener listener;
    private final ControlView view;
    private final WindowManager.LayoutParams params;
    private boolean attached;

    MirrorControlOverlay(Context context, Listener listener) {
        Context applicationContext = context.getApplicationContext();
        DisplayManager displayManager = applicationContext.getSystemService(DisplayManager.class);
        Display mainDisplay = displayManager == null
                ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Context displayContext = mainDisplay == null
                ? applicationContext : applicationContext.createDisplayContext(mainDisplay);
        // The owning activity lives on the rear display. A WindowManager taken
        // from that process context can therefore be routed to the 126x294
        // panel by HyperOS. Bind the overlay window explicitly to display 0.
        if (mainDisplay != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            displayContext = displayContext.createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        this.context = displayContext;
        this.listener = listener;
        windowManager = this.context.getSystemService(WindowManager.class);
        view = new ControlView(this.context);
        int size = dp(56);
        params = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        params.x = preferences.getInt(X, dp(12));
        params.y = preferences.getInt(Y, dp(180));
        view.setOnTouchListener(new DragTouchListener());
    }

    void show(boolean visible, boolean mirroring) {
        view.setMirroring(mirroring);
        if (!visible || !Settings.canDrawOverlays(context) || windowManager == null) {
            hide();
            return;
        }
        if (!attached) {
            try {
                windowManager.addView(view, params);
                attached = true;
            } catch (RuntimeException ignored) {
                attached = false;
            }
        }
    }

    void hide() {
        if (!attached || windowManager == null) return;
        try { windowManager.removeView(view); }
        catch (RuntimeException ignored) { }
        attached = false;
    }

    void close() { hide(); }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private float downRawX;
        private float downRawY;
        private int startX;
        private int startY;
        private boolean moved;

        @Override public boolean onTouch(View target, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (Math.hypot(dx, dy) > dp(5)) moved = true;
                    if (moved && attached) {
                        params.x = Math.max(0, startX + Math.round(dx));
                        params.y = Math.max(0, startY + Math.round(dy));
                        try { windowManager.updateViewLayout(view, params); }
                        catch (RuntimeException ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putInt(X, params.x).putInt(Y, params.y).apply();
                    } else {
                        target.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                        listener.onToggleRequested();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return false;
            }
        }
    }

    private static final class ControlView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean mirroring;

        ControlView(Context context) {
            super(context);
            setClickable(true);
            setElevation(12f * context.getResources().getDisplayMetrics().density);
            icon.setStyle(Paint.Style.STROKE);
            icon.setStrokeCap(Paint.Cap.ROUND);
            icon.setStrokeJoin(Paint.Join.ROUND);
        }

        void setMirroring(boolean value) {
            mirroring = value;
            setContentDescription(getResources().getString(value
                    ? R.string.overlay_stop_mirroring : R.string.overlay_start_mirroring));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float radius = Math.min(getWidth(), getHeight()) / 2f;
            fill.setColor(mirroring ? Color.rgb(214, 58, 58) : Color.rgb(52, 109, 241));
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius - density, fill);
            icon.setColor(Color.WHITE);
            icon.setStrokeWidth(2.4f * density);
            RectF screen = new RectF(15f * density, 17f * density,
                    41f * density, 36f * density);
            canvas.drawRoundRect(screen, 3f * density, 3f * density, icon);
            canvas.drawLine(23f * density, 41f * density, 33f * density, 41f * density, icon);
            canvas.drawLine(28f * density, 36f * density, 28f * density, 41f * density, icon);
            if (mirroring) {
                icon.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(24f * density, 23f * density, 32f * density,
                        31f * density, 1.5f * density, 1.5f * density, icon);
                icon.setStyle(Paint.Style.STROKE);
            } else {
                android.graphics.Path play = new android.graphics.Path();
                play.moveTo(25f * density, 22f * density);
                play.lineTo(34f * density, 27f * density);
                play.lineTo(25f * density, 32f * density);
                play.close();
                icon.setStyle(Paint.Style.FILL);
                canvas.drawPath(play, icon);
                icon.setStyle(Paint.Style.STROKE);
            }
        }
    }
}
