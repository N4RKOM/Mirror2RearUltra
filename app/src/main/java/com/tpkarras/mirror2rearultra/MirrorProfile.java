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
    @androidx.annotation.Nullable
    final Crop crop;

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
                zoomPercent, horizontalOffsetPercent, verticalOffsetPercent, null);
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
            @androidx.annotation.Nullable Crop crop
    ) {
        this.crop = crop != null && crop.isUsable() ? crop : null;
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

    /** The frame, or none: with one set the zoom and the offsets stand aside. */
    MirrorProfile withCrop(@androidx.annotation.Nullable Crop value) {
        return new MirrorProfile(id, customName, scaleMode, rotationDegrees, mirrorHorizontally,
                brightnessPercent, zoomPercent, horizontalOffsetPercent, verticalOffsetPercent,
                value);
    }

    /** Back to the whole screen: the frame goes with the numbers. */
    MirrorProfile resetCalibration() {
        return new MirrorProfile(id, customName, scaleMode, rotationDegrees, mirrorHorizontally,
                brightnessPercent, 100, 0, 0, null);
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
                crop
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
                crop
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
