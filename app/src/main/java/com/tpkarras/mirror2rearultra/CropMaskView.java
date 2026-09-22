package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Black where a frame does not reach.
 *
 * <p>A frame the shape of the panel covers it entirely and this draws nothing.
 * A wider one - a whole 4:3 viewfinder, say - lands as a band across the
 * middle, and the mirrored image would otherwise carry on past it: the texture
 * holds the whole screen, and scaling only decides how much of it shows. This
 * covers the rest, so the panel shows the frame and nothing else.
 */
public final class CropMaskView extends View {
    private final Paint black = new Paint();
    private float visibleWidth;
    private float visibleHeight;

    public CropMaskView(Context context) {
        this(context, null);
    }

    public CropMaskView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        black.setColor(Color.BLACK);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The frame's landing size, centred on the panel. */
    void setVisibleSize(float width, float height) {
        if (visibleWidth == width && visibleHeight == height) {
            return;
        }
        visibleWidth = width;
        visibleHeight = height;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0 || visibleWidth <= 0 || visibleHeight <= 0) {
            return;
        }
        float left = (width - Math.min(width, visibleWidth)) / 2f;
        float top = (height - Math.min(height, visibleHeight)) / 2f;
        if (left > 0.5f) {
            canvas.drawRect(0f, 0f, left, height, black);
            canvas.drawRect(width - left, 0f, width, height, black);
        }
        if (top > 0.5f) {
            canvas.drawRect(0f, 0f, width, top, black);
            canvas.drawRect(0f, height - top, width, height, black);
        }
    }
}
