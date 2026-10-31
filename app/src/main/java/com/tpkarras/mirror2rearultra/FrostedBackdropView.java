package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Frosted glass behind a floating bar: draws the part of the list that lies
 * under the bar's pill, blurred and tinted, clipped to the pill.
 *
 * <p>Sits between the scrolling list and the bar in one frame layout, covers
 * all of it and takes no touches. The bar reports where its pill is; the list
 * is redrawn into a render node whenever it scrolls or changes size, which is
 * cheap for a pill that small.
 */
@RequiresApi(31)
public final class FrostedBackdropView extends View {
    private static final float BLUR_RADIUS_DP = 18f;

    private final RenderNode node = new RenderNode("frosted");
    private final RectF pill = new RectF();
    private final Path clip = new Path();
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawn = new RectF();
    private float shift;
    private int ground;
    private View source;
    private boolean registered;

    private final ViewTreeObserver.OnScrollChangedListener onScroll = this::invalidate;
    private final ViewTreeObserver.OnGlobalLayoutListener onLayout = this::invalidate;

    public FrostedBackdropView(Context context) {
        this(context, null);
    }

    public FrostedBackdropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float radius = BLUR_RADIUS_DP * getResources().getDisplayMetrics().density;
        node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
    }

    /**
     * The list to blur, the page colour it sits on (the list itself is see-through, and
     * blurring see-through pixels would show the sharp list behind), the veil laid over the
     * blur and the hairline round the pill.
     */
    public void bind(View source, int groundColor, int tintColor, int outlineColor) {
        this.source = source;
        ground = groundColor;
        tint.setColor(tintColor);
        outline.setColor(outlineColor);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(getResources().getDisplayMetrics().density);
        invalidate();
    }

    /** Where the pill is, in window coordinates. */
    public void setPill(float left, float top, float right, float bottom) {
        int[] origin = new int[2];
        getLocationInWindow(origin);
        pill.set(left - origin[0], top - origin[1], right - origin[0], bottom - origin[1]);
        invalidate();
    }

    /** How far the bar has slid down while it hides, so the glass moves with it. */
    public void setShift(float dy) {
        shift = dy;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewTreeObserver observer = getViewTreeObserver();
        observer.addOnScrollChangedListener(onScroll);
        observer.addOnGlobalLayoutListener(onLayout);
        registered = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (registered) {
            ViewTreeObserver observer = getViewTreeObserver();
            observer.removeOnScrollChangedListener(onScroll);
            observer.removeOnGlobalLayoutListener(onLayout);
            registered = false;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (source == null || pill.isEmpty() || !canvas.isHardwareAccelerated()) {
            return;
        }
        RectF pill = drawn;
        pill.set(this.pill);
        pill.offset(0, shift);
        int width = Math.round(pill.width());
        int height = Math.round(pill.height());
        node.setPosition(0, 0, width, height);
        Canvas recording = node.beginRecording(width, height);
        // The pill's patch of the list: its place in the list's frame, plus the scroll the
        // list applies only when its parent draws it.
        recording.drawColor(ground);
        recording.translate(-pill.left + source.getLeft() - source.getScrollX(),
                -pill.top + source.getTop() - source.getScrollY());
        source.draw(recording);
        node.endRecording();

        float radius = Math.min(pill.width(), pill.height()) / 2f;
        clip.rewind();
        clip.addRoundRect(pill, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(clip);
        canvas.translate(pill.left, pill.top);
        canvas.drawRenderNode(node);
        canvas.translate(-pill.left, -pill.top);
        canvas.drawPath(clip, tint);
        canvas.drawPath(clip, outline);
        canvas.restoreToCount(saved);
    }
}
