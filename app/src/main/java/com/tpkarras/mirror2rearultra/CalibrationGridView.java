package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public final class CalibrationGridView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean previewMode;

    public CalibrationGridView(Context context) {
        this(context, null);
    }

    public CalibrationGridView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setPreviewMode(boolean previewMode) {
        this.previewMode = previewMode;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (previewMode) {
            drawPreviewBackground(canvas, width, height);
        }
        drawGrid(canvas, width, height);
    }

    private void drawPreviewBackground(Canvas canvas, float width, float height) {
        canvas.drawColor(Color.BLACK);
        int[] colors = {
                Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN,
                Color.MAGENTA, Color.RED, Color.BLUE
        };
        float barHeight = height * 0.18f;
        float barWidth = width / colors.length;
        for (int index = 0; index < colors.length; index++) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(colors[index]);
            canvas.drawRect(index * barWidth, 0f, (index + 1) * barWidth, barHeight, paint);
        }

        int[] grays = {255, 204, 153, 102, 51, 0};
        float grayTop = height * 0.84f;
        float grayWidth = width / grays.length;
        for (int index = 0; index < grays.length; index++) {
            int gray = grays[index];
            paint.setColor(Color.rgb(gray, gray, gray));
            canvas.drawRect(index * grayWidth, grayTop, (index + 1) * grayWidth, height, paint);
        }
    }

    private void drawGrid(Canvas canvas, float width, float height) {
        float density = getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, density));
        paint.setColor(Color.argb(170, 255, 255, 255));
        for (int column = 1; column < 4; column++) {
            float x = width * column / 4f;
            canvas.drawLine(x, 0f, x, height, paint);
        }
        for (int row = 1; row < 8; row++) {
            float y = height * row / 8f;
            canvas.drawLine(0f, y, width, y, paint);
        }

        paint.setStrokeWidth(Math.max(2f, density * 1.5f));
        paint.setColor(Color.CYAN);
        float inset = Math.max(3f * density, Math.min(width, height) * 0.05f);
        canvas.drawRect(new RectF(inset, inset, width - inset, height - inset), paint);

        paint.setColor(Color.WHITE);
        float cross = Math.min(width, height) * 0.12f;
        float centerX = width / 2f;
        float centerY = height / 2f;
        canvas.drawLine(centerX - cross, centerY, centerX + cross, centerY, paint);
        canvas.drawLine(centerX, centerY - cross, centerX, centerY + cross, paint);

        paint.setColor(Color.YELLOW);
        float corner = Math.min(width, height) * 0.08f;
        canvas.drawLine(0f, 0f, corner, corner, paint);
        canvas.drawLine(width, 0f, width - corner, corner, paint);
        canvas.drawLine(0f, height, corner, height - corner, paint);
        canvas.drawLine(width, height, width - corner, height - corner, paint);
    }
}
