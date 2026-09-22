package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;

/**
 * The frame itself: what the panel will show, drawn over the app it shows.
 *
 * <p>Everything outside the frame is dimmed and nothing is drawn inside it, so
 * the frame reads as a window onto the app - and, because the main screen is
 * what gets mirrored, the panel sees that window clean while the frame is
 * being moved.
 *
 * <p>The frame has no fixed shape. Made the panel's shape it fills the panel,
 * and it snaps onto that shape when it comes close; made any other shape it is
 * shown whole with black along two edges, which is the only way to put a whole
 * 4:3 viewfinder on a panel more than twice as tall as it is wide.
 */
public final class CropFrameView extends View {
    interface Listener {
        /** The frame moved or changed size. */
        void onFrameChanged(CropFrame.Rect frame);

        /** The finger has let go. */
        void onFrameSettled();
    }

    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_EDGE = 2;
    private static final int MODE_PINCH = 3;

    /** How near the panel's shape a frame has to be to snap onto it. */
    private static final float SNAP_TOLERANCE = 0.04f;
    /** How far one accessibility step moves or resizes the frame. */
    private static final float STEP = 0.02f;
    private static final float STEP_SCALE = 1.06f;

    private final Paint scrim = new Paint();
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawn = new RectF();
    private final int[] location = new int[2];
    private final float density;

    @Nullable private CropFrame crop;
    @Nullable private CropFrame.Rect frame;
    @Nullable private Listener listener;
    private int screenWidth;
    private int screenHeight;
    private boolean snapped;

    private int mode = MODE_NONE;
    private CropFrame.Rect startFrame;
    private float downX;
    private float downY;
    private float startSpan;
    /** Which sides the finger took hold of: -1 low edge, 1 high edge, 0 none. */
    private int grabX;
    private int grabY;

    public CropFrameView(Context context) {
        this(context, null);
    }

    public CropFrameView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        scrim.setColor(Color.argb(150, 0, 0, 0));
        border.setStyle(Paint.Style.STROKE);
        border.setColor(Color.WHITE);
        border.setStrokeWidth(1.5f * density);
        handle.setStyle(Paint.Style.STROKE);
        handle.setColor(Color.WHITE);
        handle.setStrokeWidth(4f * density);
        handle.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void bind(CropFrame crop, CropFrame.Rect frame,
              int screenWidth, int screenHeight, Listener listener) {
        this.crop = crop;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.listener = listener;
        show(crop.settle(frame));
    }

    @Nullable
    CropFrame.Rect frame() {
        return frame;
    }

    /** Puts the frame here, as near as the panel can show it. */
    void show(CropFrame.Rect wanted) {
        if (crop == null) {
            return;
        }
        frame = wanted;
        invalidate();
        if (listener != null) {
            listener.onFrameChanged(wanted);
        }
    }

    /** The frame on screen, in this view's pixels. */
    RectF frameInView() {
        RectF result = new RectF();
        if (frame == null) {
            return result;
        }
        getLocationOnScreen(location);
        result.set(
                frame.left * screenWidth - location[0],
                frame.top * screenHeight - location[1],
                frame.right * screenWidth - location[0],
                frame.bottom * screenHeight - location[1]
        );
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (frame == null) {
            return;
        }
        drawn.set(frameInView());
        canvas.save();
        canvas.clipOutRect(drawn);
        canvas.drawPaint(scrim);
        canvas.restore();
        // Both lines sit just outside the frame, so the panel - which shows
        // exactly the inside - never shows either of them.
        float outside = border.getStrokeWidth() / 2f + density;
        canvas.drawRect(drawn.left - outside, drawn.top - outside,
                drawn.right + outside, drawn.bottom + outside, border);
        float inset = handle.getStrokeWidth() / 2f + density;
        float arm = Math.min(22f * density, Math.min(drawn.width(), drawn.height()) / 3f);
        drawCorner(canvas, drawn.left - inset, drawn.top - inset, arm, arm);
        drawCorner(canvas, drawn.right + inset, drawn.top - inset, -arm, arm);
        drawCorner(canvas, drawn.left - inset, drawn.bottom + inset, arm, -arm);
        drawCorner(canvas, drawn.right + inset, drawn.bottom + inset, -arm, -arm);
        // A short bar in the middle of each side, because each side moves on
        // its own - that is what lets the frame be any shape at all.
        float bar = arm * 0.8f;
        canvas.drawLine(drawn.centerX() - bar / 2f, drawn.top - inset,
                drawn.centerX() + bar / 2f, drawn.top - inset, handle);
        canvas.drawLine(drawn.centerX() - bar / 2f, drawn.bottom + inset,
                drawn.centerX() + bar / 2f, drawn.bottom + inset, handle);
        canvas.drawLine(drawn.left - inset, drawn.centerY() - bar / 2f,
                drawn.left - inset, drawn.centerY() + bar / 2f, handle);
        canvas.drawLine(drawn.right + inset, drawn.centerY() - bar / 2f,
                drawn.right + inset, drawn.centerY() + bar / 2f, handle);
    }

    private void drawCorner(Canvas canvas, float x, float y, float armX, float armY) {
        canvas.drawLine(x, y, x + armX, y, handle);
        canvas.drawLine(x, y, x, y + armY, handle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (crop == null || frame == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginSingle(event.getX(), event.getY(), true);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2) {
                    beginPinch(event);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                follow(event);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() == 2) {
                    // One finger stays down: carry on moving from where it is.
                    int remaining = event.getActionIndex() == 0 ? 1 : 0;
                    beginSingle(event.getX(remaining), event.getY(remaining), false);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = MODE_NONE;
                if (listener != null) {
                    listener.onFrameSettled();
                }
                return true;
            default:
                return false;
        }
    }

    private void beginSingle(float x, float y, boolean mayGrabEdge) {
        startFrame = frame;
        downX = x;
        downY = y;
        grabX = 0;
        grabY = 0;
        mode = MODE_MOVE;
        if (!mayGrabEdge) {
            return;
        }
        RectF box = frameInView();
        float reach = 36f * density;
        // Near one side: that side alone moves. Near two: the corner does, and
        // both sides move together.
        if (Math.abs(x - box.left) <= reach && y > box.top - reach && y < box.bottom + reach) {
            grabX = -1;
        } else if (Math.abs(x - box.right) <= reach && y > box.top - reach && y < box.bottom + reach) {
            grabX = 1;
        }
        if (Math.abs(y - box.top) <= reach && x > box.left - reach && x < box.right + reach) {
            grabY = -1;
        } else if (Math.abs(y - box.bottom) <= reach && x > box.left - reach && x < box.right + reach) {
            grabY = 1;
        }
        if (grabX != 0 || grabY != 0) {
            mode = MODE_EDGE;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private void beginPinch(MotionEvent event) {
        startFrame = frame;
        downX = (event.getX(0) + event.getX(1)) / 2f;
        downY = (event.getY(0) + event.getY(1)) / 2f;
        startSpan = Math.max(1f, (float) Math.hypot(
                event.getX(0) - event.getX(1), event.getY(0) - event.getY(1)));
        mode = MODE_PINCH;
    }

    private void follow(MotionEvent event) {
        if (startFrame == null) {
            return;
        }
        switch (mode) {
            case MODE_MOVE: {
                float dx = (event.getX() - downX) / screenWidth;
                float dy = (event.getY() - downY) / screenHeight;
                request(new CropFrame.Rect(startFrame.left + dx, startFrame.top + dy,
                        startFrame.right + dx, startFrame.bottom + dy), false);
                break;
            }
            case MODE_EDGE: {
                float dx = (event.getX() - downX) / screenWidth;
                float dy = (event.getY() - downY) / screenHeight;
                float left = startFrame.left + (grabX < 0 ? dx : 0f);
                float right = startFrame.right + (grabX > 0 ? dx : 0f);
                float top = startFrame.top + (grabY < 0 ? dy : 0f);
                float bottom = startFrame.bottom + (grabY > 0 ? dy : 0f);
                // Pushed past the opposite side, the frame stops rather than
                // turning itself inside out.
                request(new CropFrame.Rect(Math.min(left, right - CropFrame.MIN_SPAN),
                        Math.min(top, bottom - CropFrame.MIN_SPAN),
                        Math.max(right, left + CropFrame.MIN_SPAN),
                        Math.max(bottom, top + CropFrame.MIN_SPAN)), true);
                break;
            }
            case MODE_PINCH: {
                if (event.getPointerCount() < 2) {
                    break;
                }
                float span = (float) Math.hypot(
                        event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
                float midX = (event.getX(0) + event.getX(1)) / 2f;
                float midY = (event.getY(0) + event.getY(1)) / 2f;
                float grow = span / startSpan;
                request(CropFrame.Rect.around(
                        startFrame.centreX() + (midX - downX) / screenWidth,
                        startFrame.centreY() + (midY - downY) / screenHeight,
                        startFrame.width() * grow,
                        startFrame.height() * grow), false);
                break;
            }
            default:
                break;
        }
    }

    /**
     * @param resizing whether the shape may have changed, and so whether the
     *     frame should be let snap onto the panel's own
     */
    private void request(CropFrame.Rect wanted, boolean resizing) {
        if (crop == null) {
            return;
        }
        CropFrame.Rect settled = crop.settle(
                resizing ? crop.snapToPanelShape(wanted, SNAP_TOLERANCE) : wanted);
        boolean fills = crop.fillsPanel(settled);
        if (resizing && fills != snapped) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        snapped = fills;
        show(settled);
    }

    // ---- Accessibility: the same moves, a step at a time ----

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                R.id.crop_frame_action_larger, getResources().getString(R.string.crop_frame_larger)));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                R.id.crop_frame_action_smaller, getResources().getString(R.string.crop_frame_smaller)));
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
        if (crop == null || frame == null) {
            return super.performAccessibilityAction(action, arguments);
        }
        CropFrame.Rect current = frame;
        if (action == android.R.id.accessibilityActionScrollUp) {
            moveBy(current, 0f, -STEP);
        } else if (action == android.R.id.accessibilityActionScrollDown) {
            moveBy(current, 0f, STEP);
        } else if (action == android.R.id.accessibilityActionScrollLeft) {
            moveBy(current, -STEP, 0f);
        } else if (action == android.R.id.accessibilityActionScrollRight) {
            moveBy(current, STEP, 0f);
        } else if (action == R.id.crop_frame_action_larger
                || action == R.id.crop_frame_action_smaller) {
            float grow = action == R.id.crop_frame_action_larger ? STEP_SCALE : 1f / STEP_SCALE;
            request(CropFrame.Rect.around(current.centreX(), current.centreY(),
                    current.width() * grow, current.height() * grow), false);
        } else {
            return super.performAccessibilityAction(action, arguments);
        }
        if (listener != null) {
            listener.onFrameSettled();
        }
        return true;
    }

    private void moveBy(CropFrame.Rect current, float dx, float dy) {
        request(new CropFrame.Rect(current.left + dx, current.top + dy,
                current.right + dx, current.bottom + dy), false);
    }
}
