package com.tpkarras.mirror2rearultra;

final class MirrorProfile {
    static final String CAMERA_ID = "CAMERA";
    static final String NAVIGATION_ID = "NAVIGATION";
    static final String VIDEO_ID = "VIDEO";
    static final String CUSTOM_PREFIX = "CUSTOM_";

    enum Id {
        CAMERA,
        NAVIGATION,
        VIDEO
    }

    enum ScaleMode {
        FIT,
        FILL,
        STRETCH
    }

    /**
     * The part of the main screen the panel shows, chosen with a frame.
     *
     * <p>Sides are fractions of the screen as it was turned when the frame was
     * drawn, which is what {@link #rotation} records. Whatever is inside goes
     * on the panel whole, keeping its shape, with black where the panel is
     * wider or taller than the frame - so a viewfinder can be shown entire
     * rather than trimmed to the panel's own proportions.
     *
     * <p>A crop answers the same question as the zoom and the offsets and
     * answers it better, so while one is set those are left alone.
     */
    static final class Crop {
        final float left;
        final float top;
        final float right;
        final float bottom;
        final int rotation;

        Crop(float left, float top, float right, float bottom, int rotation) {
            this.left = clampFraction(left);
            this.top = clampFraction(top);
            this.right = clampFraction(right);
            this.bottom = clampFraction(bottom);
            this.rotation = Math.floorMod(rotation, 4);
        }

        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }

        boolean isUsable() {
            return width() > 0.01f && height() > 0.01f;
        }

        private static float clampFraction(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }

    final String id;
    final String customName;
    final ScaleMode scaleMode;
    final int rotationDegrees;
    final boolean mirrorHorizontally;
    final int brightnessPercent;
    final int zoomPercent;
    final int horizontalOffsetPercent;
    final int verticalOffsetPercent;
    /**
     * The frame for a screen held upright, and the one for a screen on its
     * side.
     *
     * <p>Two, because an app laid out sideways is a different picture and not
     * the same one turned: the viewfinder changes shape and the buttons move
     * to the edge that is now long. A frame taken from one and used on the
     * other would cut a part of the screen nobody chose.
     */
    @androidx.annotation.Nullable
    final Crop crop;
    @androidx.annotation.Nullable
    final Crop landscapeCrop;

    MirrorProfile(
            Id id,
            ScaleMode scaleMode,
            int rotationDegrees,
            boolean mirrorHorizontally,
            int brightnessPercent
    ) {
        this(id.name(), null, scaleMode, rotationDegrees, mirrorHorizontally,
                brightnessPercent, 100, 0, 0);
    }

    MirrorProfile(
            Id id,
            ScaleMode scaleMode,
            int rotationDegrees,
            boolean mirrorHorizontally,
            int brightnessPercent,
            int zoomPercent,
            int horizontalOffsetPercent,
            int verticalOffsetPercent
    ) {
        this(id.name(), null, scaleMode, rotationDegrees, mirrorHorizontally,
                brightnessPercent, zoomPercent, horizontalOffsetPercent, verticalOffsetPercent);
    }

    MirrorProfile(
            String id,
            String customName,
            ScaleMode scaleMode,
            int rotationDegrees,
            boolean mirrorHorizontally,
            int brightnessPercent,
            int zoomPercent,
            int horizontalOffsetPercent,
            int verticalOffsetPercent
    ) {
        this(id, customName, scaleMode, rotationDegrees, mirrorHorizontally, brightnessPercent,
                zoomPercent, horizontalOffsetPercent, verticalOffsetPercent, null, null);
    }

    MirrorProfile(
            String id,
            String customName,
            ScaleMode scaleMode,
            int rotationDegrees,
            boolean mirrorHorizontally,
            int brightnessPercent,
            int zoomPercent,
            int horizontalOffsetPercent,
            int verticalOffsetPercent,
            @androidx.annotation.Nullable Crop crop,
            @androidx.annotation.Nullable Crop landscapeCrop
    ) {
        this.crop = crop != null && crop.isUsable() ? crop : null;
        this.landscapeCrop = landscapeCrop != null && landscapeCrop.isUsable()
                ? landscapeCrop : null;
        this.id = normalizeId(id);
        this.customName = customName == null ? null : customName.trim();
        this.scaleMode = scaleMode;
        this.rotationDegrees = normalizeRotation(rotationDegrees);
        this.mirrorHorizontally = mirrorHorizontally;
        this.brightnessPercent = Math.max(10, Math.min(100, brightnessPercent));
        this.zoomPercent = Math.max(100, Math.min(200, zoomPercent));
        this.horizontalOffsetPercent = clampOffset(horizontalOffsetPercent);
        this.verticalOffsetPercent = clampOffset(verticalOffsetPercent);
    }

    MirrorProfile withScaleMode(ScaleMode value) {
        return copy(value, rotationDegrees, mirrorHorizontally, brightnessPercent,
                zoomPercent, horizontalOffsetPercent, verticalOffsetPercent);
    }

    MirrorProfile withRotationDegrees(int value) {
        return copy(scaleMode, value, mirrorHorizontally, brightnessPercent,
                zoomPercent, horizontalOffsetPercent, verticalOffsetPercent);
    }

    MirrorProfile withMirrorHorizontally(boolean value) {
        return copy(scaleMode, rotationDegrees, value, brightnessPercent,
                zoomPercent, horizontalOffsetPercent, verticalOffsetPercent);
    }

    MirrorProfile withBrightnessPercent(int value) {
        return copy(scaleMode, rotationDegrees, mirrorHorizontally, value,
                zoomPercent, horizontalOffsetPercent, verticalOffsetPercent);
    }

    MirrorProfile withZoomPercent(int value) {
        return copy(scaleMode, rotationDegrees, mirrorHorizontally, brightnessPercent,
                value, horizontalOffsetPercent, verticalOffsetPercent);
    }

    MirrorProfile withHorizontalOffsetPercent(int value) {
        return copy(scaleMode, rotationDegrees, mirrorHorizontally, brightnessPercent,
                zoomPercent, value, verticalOffsetPercent);
    }

    MirrorProfile withVerticalOffsetPercent(int value) {
        return copy(scaleMode, rotationDegrees, mirrorHorizontally, brightnessPercent,
                zoomPercent, horizontalOffsetPercent, value);
    }

    /**
     * The frame for the way the screen was held when it was drawn.
     *
     * <p>It replaces the frame for that way round and leaves the other alone,
     * so a phone framed upright and then framed sideways keeps both.
     */
    MirrorProfile withCrop(Crop value) {
        boolean upright = value.rotation % 2 == 0;
        return new MirrorProfile(id, customName, scaleMode, rotationDegrees, mirrorHorizontally,
                brightnessPercent, zoomPercent, horizontalOffsetPercent, verticalOffsetPercent,
                upright ? value : crop, upright ? landscapeCrop : value);
    }

    /** The frame to use with the screen turned this way, or none. */
    @androidx.annotation.Nullable
    Crop cropFor(int rotation) {
        return rotation % 2 == 0 ? crop : landscapeCrop;
    }

    /** Whether a frame decides this profile's crop either way round. */
    boolean hasCrop() {
        return crop != null || landscapeCrop != null;
    }

    /** Back to the whole screen: both frames go with the numbers. */
    MirrorProfile resetCalibration() {
        return new MirrorProfile(id, customName, scaleMode, rotationDegrees, mirrorHorizontally,
                brightnessPercent, 100, 0, 0, null, null);
    }

    MirrorProfile withCustomName(String value) {
        return new MirrorProfile(
                id,
                value,
                scaleMode,
                rotationDegrees,
                mirrorHorizontally,
                brightnessPercent,
                zoomPercent,
                horizontalOffsetPercent,
                verticalOffsetPercent,
                crop,
                landscapeCrop
        );
    }

    boolean isCustom() {
        return id.startsWith(CUSTOM_PREFIX);
    }

    private MirrorProfile copy(
            ScaleMode newScaleMode,
            int newRotationDegrees,
            boolean newMirrorHorizontally,
            int newBrightnessPercent,
            int newZoomPercent,
            int newHorizontalOffsetPercent,
            int newVerticalOffsetPercent
    ) {
        return new MirrorProfile(
                id,
                customName,
                newScaleMode,
                newRotationDegrees,
                newMirrorHorizontally,
                newBrightnessPercent,
                newZoomPercent,
                newHorizontalOffsetPercent,
                newVerticalOffsetPercent,
                crop,
                landscapeCrop
        );
    }

    private static int normalizeRotation(int value) {
        int normalized = value % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        return normalized - normalized % 90;
    }

    private static int clampOffset(int value) {
        return Math.max(-50, Math.min(50, value));
    }

    private static String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return CAMERA_ID;
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
