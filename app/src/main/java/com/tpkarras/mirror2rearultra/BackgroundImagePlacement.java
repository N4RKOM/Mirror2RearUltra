package com.tpkarras.mirror2rearultra;

/**
 * How the picture behind the widgets sits on the panel: the same five choices
 * the mirrored image has on the image and calibration screen.
 *
 * <p>Scaling, an extra turn in quarters, a zoom on top of it, and a shift of
 * the centre as a share of the panel. Kept apart from the drawing so that the
 * arithmetic can be checked without a canvas.
 */
final class BackgroundImagePlacement {
    static final int MIN_ZOOM = 100;
    static final int MAX_ZOOM = 200;
    static final int MIN_OFFSET = -50;
    static final int MAX_OFFSET = 50;

    /** Cover the panel, as the picture always did before it could be set. */
    static final BackgroundImagePlacement DEFAULT = new BackgroundImagePlacement(
            MirrorProfile.ScaleMode.FILL, 0, MIN_ZOOM, 0, 0);

    final MirrorProfile.ScaleMode scaleMode;
    final int rotationDegrees;
    final int zoomPercent;
    final int horizontalOffsetPercent;
    final int verticalOffsetPercent;

    BackgroundImagePlacement(
            MirrorProfile.ScaleMode scaleMode,
            int rotationDegrees,
            int zoomPercent,
            int horizontalOffsetPercent,
            int verticalOffsetPercent
    ) {
        this.scaleMode = scaleMode == null ? MirrorProfile.ScaleMode.FILL : scaleMode;
        this.rotationDegrees = Math.floorMod(Math.round(rotationDegrees / 90f) * 90, 360);
        this.zoomPercent = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomPercent));
        this.horizontalOffsetPercent = Math.max(MIN_OFFSET,
                Math.min(MAX_OFFSET, horizontalOffsetPercent));
        this.verticalOffsetPercent = Math.max(MIN_OFFSET,
                Math.min(MAX_OFFSET, verticalOffsetPercent));
    }

    BackgroundImagePlacement withScaleMode(MirrorProfile.ScaleMode value) {
        return new BackgroundImagePlacement(value, rotationDegrees, zoomPercent,
                horizontalOffsetPercent, verticalOffsetPercent);
    }

    BackgroundImagePlacement withRotationDegrees(int value) {
        return new BackgroundImagePlacement(scaleMode, value, zoomPercent,
                horizontalOffsetPercent, verticalOffsetPercent);
    }

    BackgroundImagePlacement withZoomPercent(int value) {
        return new BackgroundImagePlacement(scaleMode, rotationDegrees, value,
                horizontalOffsetPercent, verticalOffsetPercent);
    }

    BackgroundImagePlacement withHorizontalOffsetPercent(int value) {
        return new BackgroundImagePlacement(scaleMode, rotationDegrees, zoomPercent,
                value, verticalOffsetPercent);
    }

    BackgroundImagePlacement withVerticalOffsetPercent(int value) {
        return new BackgroundImagePlacement(scaleMode, rotationDegrees, zoomPercent,
                horizontalOffsetPercent, value);
    }

    /**
     * The factors to scale the picture by along its own width and height,
     * after it has been turned about its centre.
     *
     * <p>A quarter turn swaps which side of the picture faces which side of
     * the panel, so Fit and Fill are worked out on the turned picture, and
     * Stretch matches each of the picture's own sides to the panel side it now
     * faces. Zoom multiplies whichever was chosen.
     *
     * @return {width factor, height factor}
     */
    float[] scales(int pictureWidth, int pictureHeight, float panelWidth, float panelHeight) {
        boolean sideways = rotationDegrees == 90 || rotationDegrees == 270;
        float turnedWidth = sideways ? pictureHeight : pictureWidth;
        float turnedHeight = sideways ? pictureWidth : pictureHeight;
        float acrossFactor = panelWidth / turnedWidth;
        float downFactor = panelHeight / turnedHeight;
        float zoom = zoomPercent / 100f;
        switch (scaleMode) {
            case STRETCH:
                // The picture's own width runs along the panel's height when it
                // lies on its side.
                return sideways
                        ? new float[]{downFactor * zoom, acrossFactor * zoom}
                        : new float[]{acrossFactor * zoom, downFactor * zoom};
            case FIT: {
                float uniform = Math.min(acrossFactor, downFactor) * zoom;
                return new float[]{uniform, uniform};
            }
            case FILL:
            default: {
                float uniform = Math.max(acrossFactor, downFactor) * zoom;
                return new float[]{uniform, uniform};
            }
        }
    }

    float offsetX(float panelWidth) {
        return panelWidth * horizontalOffsetPercent / 100f;
    }

    float offsetY(float panelHeight) {
        return panelHeight * verticalOffsetPercent / 100f;
    }
}
