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

    final String id;
    final String customName;
    final ScaleMode scaleMode;
    final int rotationDegrees;
    final boolean mirrorHorizontally;
    final int brightnessPercent;
    final int zoomPercent;
    final int horizontalOffsetPercent;
    final int verticalOffsetPercent;

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

    MirrorProfile resetCalibration() {
        return copy(scaleMode, rotationDegrees, mirrorHorizontally, brightnessPercent,
                100, 0, 0);
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
                verticalOffsetPercent
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
                newVerticalOffsetPercent
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
