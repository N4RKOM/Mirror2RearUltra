package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

/**
 * The part of the main screen the panel shows, as a rectangle on that screen.
 *
 * <p>Two jobs. It turns a frame into the way the mirrored image has to be
 * turned, scaled and slid for that frame to land on the panel - the frame is
 * shown whole, keeping its shape, so a frame that is not the panel's shape
 * leaves black along two edges rather than losing its sides. And it turns a
 * profile's older zoom and offsets into the rectangle they amount to, which is
 * where the frame starts when a profile has never been framed.
 *
 * <p>Every step copies {@code Mirror.applyRotationTransform}: the screen is
 * letterboxed into a square texture centred across the panel, turned, scaled
 * about the texture's centre, and slid. If that method changes, this has to
 * change with it.
 *
 * <p>Rectangles are in fractions of the main screen as it is turned at the
 * moment, 0 to 1 across and down.
 */
final class CropFrame {
    static final int MIN_ZOOM = 100;
    static final int MAX_ZOOM = 200;
    static final int MAX_OFFSET = 50;
    /**
     * How far the image may be blown up.
     *
     * <p>The same limit the zoom slider has always had: past twice its size
     * the capture has no more detail to give, and the panel shows the screen's
     * own pixels rather than what is on it.
     */
    static final float MAX_MAGNIFICATION = 2f;
    /** A frame smaller than this along either side is not worth showing. */
    static final float MIN_SPAN = 0.08f;

    /** A rectangle on the main screen, in fractions of it. */
    static final class Rect {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Rect(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }

        float centreX() {
            return (left + right) / 2f;
        }

        float centreY() {
            return (top + bottom) / 2f;
        }

        static Rect around(float centreX, float centreY, float width, float height) {
            return new Rect(centreX - width / 2f, centreY - height / 2f,
                    centreX + width / 2f, centreY + height / 2f);
        }
    }

    /** Zoom and offsets in the profile's own units. */
    static final class Calibration {
        final int zoomPercent;
        final int offsetXPercent;
        final int offsetYPercent;

        Calibration(int zoomPercent, int offsetXPercent, int offsetYPercent) {
            this.zoomPercent = zoomPercent;
            this.offsetXPercent = offsetXPercent;
            this.offsetYPercent = offsetYPercent;
        }
    }

    /**
     * What the panel does with a frame: how to draw the image, and how much of
     * the panel the frame covers once it is drawn.
     */
    static final class Projection {
        final float degrees;
        final float scaleX;
        final float scaleY;
        /** In the texture view's own pixels, applied after the scaling. */
        final float translateX;
        final float translateY;
        /** The frame's landing size, centred on the panel. */
        final float visibleWidth;
        final float visibleHeight;
        /** How far the image is blown up, as the capture buffer counts it. */
        final int magnificationPercent;

        Projection(float degrees, float scaleX, float scaleY, float translateX, float translateY,
                   float visibleWidth, float visibleHeight, int magnificationPercent) {
            this.degrees = degrees;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.translateX = translateX;
            this.translateY = translateY;
            this.visibleWidth = visibleWidth;
            this.visibleHeight = visibleHeight;
            this.magnificationPercent = magnificationPercent;
        }
    }

    private final int panelWidth;
    private final int panelHeight;
    private final int side;
    /** Where the texture's centre sits on the panel before any offset. */
    private final float originX;
    private final float originY;
    /** The mirrored screen's box inside the texture, before the transform. */
    private final float contentWidth;
    private final float contentHeight;
    private final int cos;
    private final int sin;
    private final boolean axesSwapped;
    /** Scale per unit of zoom, sign included for the mirror. */
    private final float unitScaleX;
    private final float unitScaleY;
    private final float degrees;

    /**
     * @param screenWidth the main screen's width as it is turned now
     * @param screenHeight the main screen's height as it is turned now
     * @param displayRotation the main screen's rotation, 0 to 3
     */
    CropFrame(
            int panelWidth,
            int panelHeight,
            int screenWidth,
            int screenHeight,
            int displayRotation,
            MirrorProfile profile
    ) {
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        // The texture is square, as long as the panel's long side, laid out
        // across the top of the panel and centred from side to side.
        side = Math.max(panelWidth, panelHeight);
        originX = panelWidth / 2f;
        originY = side / 2f;
        // The virtual display fits the screen into that square keeping its
        // shape, the way it is turned now.
        float screenAspect = screenWidth / (float) Math.max(1, screenHeight);
        if (screenAspect <= 1f) {
            contentWidth = side * screenAspect;
            contentHeight = side;
        } else {
            contentWidth = side;
            contentHeight = side / screenAspect;
        }
        RotationTransform transform = RotationTransform.forRotation(
                displayRotation, profile.rotationDegrees, profile.mirrorHorizontally);
        degrees = transform.degrees;
        int quarter = Math.floorMod(transform.degrees / 90, 4);
        cos = quarter == 0 ? 1 : quarter == 2 ? -1 : 0;
        sin = quarter == 1 ? 1 : quarter == 3 ? -1 : 0;
        axesSwapped = quarter % 2 == 1;
        ProjectionGeometry.Scale scale = ProjectionGeometry.calculateScale(
                profile.scaleMode,
                panelWidth,
                panelHeight,
                side,
                screenWidth,
                screenHeight,
                profile.rotationDegrees
        );
        unitScaleX = transform.scaleX * scale.x;
        unitScaleY = transform.scaleY * scale.y;
    }

    /** The part of the screen the panel shows with this calibration. */
    Rect frameOf(Calibration calibration) {
        float zoom = calibration.zoomPercent / 100f;
        float shiftX = Math.round(ProjectionGeometry.calculateTranslation(
                panelWidth, calibration.offsetXPercent));
        float shiftY = Math.round(ProjectionGeometry.calculateTranslation(
                panelHeight, calibration.offsetYPercent));
        float[] first = toScreen(0f, 0f, zoom, shiftX, shiftY);
        float[] second = toScreen(panelWidth, panelHeight, zoom, shiftX, shiftY);
        return new Rect(
                Math.min(first[0], second[0]),
                Math.min(first[1], second[1]),
                Math.max(first[0], second[0]),
                Math.max(first[1], second[1])
        );
    }

    /** How the image has to be drawn for this frame to land on the panel. */
    Projection projectionFor(Rect frame) {
        float width = Math.max(1e-4f, frame.width()) * contentWidth;
        float height = Math.max(1e-4f, frame.height()) * contentHeight;
        float turnedWidth = axesSwapped ? height : width;
        float turnedHeight = axesSwapped ? width : height;
        // Whole and in shape: the smaller of the two fits, and the panel keeps
        // black along the other pair of edges.
        float fit = Math.min(panelWidth / turnedWidth, panelHeight / turnedHeight);
        float centreX = (frame.centreX() - 0.5f) * contentWidth;
        float centreY = (frame.centreY() - 0.5f) * contentHeight;
        float turnedCentreX = cos * centreX - sin * centreY;
        float turnedCentreY = sin * centreX + cos * centreY;
        float scaleX = unitSign(unitScaleX) * fit;
        float scaleY = unitSign(unitScaleY) * fit;
        return new Projection(
                degrees,
                scaleX,
                scaleY,
                // The texture's own middle is already across the panel; only
                // the frame's distance from the screen's middle has to be
                // taken out, and the panel's middle found down the texture.
                -turnedCentreX * scaleX,
                panelHeight / 2f - originY - turnedCentreY * scaleY,
                turnedWidth * fit,
                turnedHeight * fit,
                Math.round(fit * 100f)
        );
    }

    /** How far the image is blown up to show this frame. */
    float magnification(Rect frame) {
        return projectionFor(frame).magnificationPercent / 100f;
    }

    /**
     * The width-to-height a frame needs to fill the panel exactly.
     *
     * <p>In screen fractions rather than pixels, which is how frames are kept.
     */
    float panelShapeAspect() {
        float shape = axesSwapped
                ? panelHeight / (float) panelWidth
                : panelWidth / (float) panelHeight;
        return shape * contentHeight / contentWidth;
    }

    /** The largest panel-shaped frame that fits inside this one. */
    Rect toPanelShape(Rect frame) {
        float aspect = panelShapeAspect();
        float width = Math.min(frame.width(), frame.height() * aspect);
        return Rect.around(frame.centreX(), frame.centreY(), width, width / aspect);
    }

    /** The whole screen, which is where a frame starts from. */
    Rect wholeScreen() {
        return new Rect(0f, 0f, 1f, 1f);
    }

    /**
     * The nearest frame the panel can actually show: on the screen, not too
     * small, and not blown up past what the capture has detail for.
     */
    Rect settle(Rect wanted) {
        float width = Math.max(MIN_SPAN, Math.min(1f, wanted.width()));
        float height = Math.max(MIN_SPAN, Math.min(1f, wanted.height()));
        Rect sized = Rect.around(wanted.centreX(), wanted.centreY(), width, height);
        float magnified = magnification(sized);
        if (magnified > MAX_MAGNIFICATION) {
            float grow = magnified / MAX_MAGNIFICATION;
            width = Math.min(1f, width * grow);
            height = Math.min(1f, height * grow);
        }
        float centreX = Math.max(width / 2f, Math.min(1f - width / 2f, wanted.centreX()));
        float centreY = Math.max(height / 2f, Math.min(1f - height / 2f, wanted.centreY()));
        return Rect.around(centreX, centreY, width, height);
    }

    /**
     * A frame close to the panel's shape, snapped exactly onto it.
     *
     * <p>Filling the panel is the one size worth hitting exactly, and by hand
     * it is a pixel away in either direction.
     */
    Rect snapToPanelShape(Rect frame, float tolerance) {
        float aspect = frame.width() / Math.max(1e-4f, frame.height());
        float wanted = panelShapeAspect();
        if (Math.abs(aspect - wanted) > tolerance * wanted) {
            return frame;
        }
        return Rect.around(frame.centreX(), frame.centreY(),
                frame.width(), frame.width() / wanted);
    }

    /** Whether this frame fills the panel, leaving no black edges. */
    boolean fillsPanel(Rect frame) {
        Projection projection = projectionFor(frame);
        return projection.visibleWidth > panelWidth - 1f
                && projection.visibleHeight > panelHeight - 1f;
    }

    private static float unitSign(float value) {
        return value < 0f ? -1f : 1f;
    }

    private float[] toScreen(float panelX, float panelY, float zoom, float shiftX, float shiftY) {
        float turnedX = (panelX - originX - shiftX) / (unitScaleX * zoom);
        float turnedY = (panelY - originY - shiftY) / (unitScaleY * zoom);
        // The inverse of a quarter turn is the same turn the other way.
        float x = cos * turnedX + sin * turnedY;
        float y = -sin * turnedX + cos * turnedY;
        return new float[] {x / contentWidth + 0.5f, y / contentHeight + 0.5f};
    }
}
