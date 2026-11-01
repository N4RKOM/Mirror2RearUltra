package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * A miniature of the rear panel showing where a profile's rotation, mirroring,
 * zoom and offsets put the picture.
 *
 * <p>It draws a stand-in scene (two halves, a crosshair and an "R") rather
 * than the live screen, so it works while mirroring is off. The offsets use
 * the same percent-of-panel meaning as {@link ProjectionGeometry}; the scale
 * mode is not modelled, the scene already fills the panel.
 */
public final class PanelPreviewView extends View {
    /** Width over height used until the real rear display is known. */
    private static final float DEFAULT_ASPECT = 4f / 3f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panel = new RectF();
    private final Path clip = new Path();
    private final float radius;

    private float aspect = DEFAULT_ASPECT;
    private int rotationDegrees;
    private boolean mirrored;
    private int zoomPercent = 100;
    private int horizontalOffsetPercent;
    private int verticalOffsetPercent;
    private int brightnessPercent = 100;

    public PanelPreviewView(Context context) {
        this(context, null);
    }

    public PanelPreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        radius = 14 * density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setContentDescription(context.getString(R.string.panel_preview_description));
    }

    /** Width over height of the rear panel, in the orientation it is held. */
    void setAspect(float widthOverHeight) {
        if (widthOverHeight > 0f && widthOverHeight != aspect) {
            aspect = widthOverHeight;
            requestLayout();
            invalidate();
        }
    }

    /** Takes the rear panel's real proportions when the panel can be found. */
    void fitToRearPanel() {
        DisplayManager manager = getContext().getSystemService(DisplayManager.class);
        int rearDisplayId = DisplayActivity.findRearDisplayId(manager);
        Display rear = rearDisplayId == Display.INVALID_DISPLAY
                ? null : manager.getDisplay(rearDisplayId);
        if (rear == null) {
            return;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        rear.getRealMetrics(metrics);
        if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            setAspect((float) metrics.widthPixels / metrics.heightPixels);
        }
    }

    void setProfile(MirrorProfile profile) {
        rotationDegrees = profile.rotationDegrees;
        mirrored = profile.mirrorHorizontally;
        zoomPercent = profile.zoomPercent;
        horizontalOffsetPercent = profile.horizontalOffsetPercent;
        verticalOffsetPercent = profile.verticalOffsetPercent;
        brightnessPercent = profile.brightnessPercent;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = MeasureSpec.getSize(heightSpec);
        int width = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED || height <= 0) {
            height = Math.round(width / aspect);
        }
        int fitted = Math.round(height * aspect);
        if (fitted > width && width > 0) {
            fitted = width;
            height = Math.round(width / aspect);
        }
        setMeasuredDimension(fitted, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float half = stroke.getStrokeWidth();
        panel.set(half, half, width - half, height - half);
        clip.reset();
        clip.addRoundRect(panel, radius, radius, Path.Direction.CW);

        fill.setColor(ContextCompat.getColor(getContext(), R.color.black));
        canvas.drawPath(clip, fill);

        canvas.save();
        canvas.clipPath(clip);
        float cx = width / 2f;
        float cy = height / 2f;
        canvas.translate(
                ProjectionGeometry.calculateTranslation(Math.round(width), horizontalOffsetPercent),
                ProjectionGeometry.calculateTranslation(Math.round(height), verticalOffsetPercent));
        float zoom = zoomPercent / 100f;
        canvas.scale(mirrored ? -zoom : zoom, zoom, cx, cy);
        canvas.rotate(rotationDegrees, cx, cy);
        drawScene(canvas, width, height);
        canvas.restore();

        // Brightness dims the whole panel, like the real backlight does.
        int dim = Math.round(255 * (1f - Math.max(10, Math.min(100, brightnessPercent)) / 100f)
                * 0.8f);
        fill.setColor(dim << 24);
        canvas.drawPath(clip, fill);

        stroke.setColor(ContextCompat.getColor(getContext(), R.color.hyper_outline_subtle));
        canvas.drawPath(clip, stroke);
    }

    private void drawScene(Canvas canvas, float width, float height) {
        fill.setColor(ContextCompat.getColor(getContext(), R.color.hyper_accent));
        canvas.drawRect(0, 0, width / 2f, height, fill);
        fill.setColor(ContextCompat.getColor(getContext(), R.color.hyper_success));
        canvas.drawRect(width / 2f, 0, width, height, fill);

        stroke.setColor(0x99FFFFFF);
        canvas.drawLine(width / 2f, 0, width / 2f, height, stroke);
        canvas.drawLine(0, height / 2f, width, height / 2f, stroke);

        // Not symmetric either way, so a rotation or a flip is readable.
        text.setColor(0xFFFFFFFF);
        text.setTextSize(Math.min(width, height) * 0.4f);
        float baseline = height * 0.5f - (text.ascent() + text.descent()) / 2f;
        canvas.drawText("R", width * 0.25f, baseline, text);
    }
}
