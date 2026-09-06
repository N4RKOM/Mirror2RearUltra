package com.tpkarras.mirror2rearultra;

final class RotationTransform {
    final int degrees;
    final float scaleX;
    final float scaleY;

    private RotationTransform(int degrees, float scaleX, float scaleY) {
        this.degrees = degrees;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    static RotationTransform forRotation(
            int displayRotation,
            int manualRotationDegrees,
            boolean mirrorHorizontally
    ) {
        int degrees;
        switch (displayRotation) {
            case 1:
                degrees = -90;
                break;
            case 2:
                degrees = -180;
                break;
            case 3:
                degrees = 90;
                break;
            case 0:
            default:
                degrees = 0;
                break;
        }
        degrees = normalizeDegrees(degrees + manualRotationDegrees);
        if (!mirrorHorizontally) {
            return new RotationTransform(degrees, 1f, 1f);
        }

        boolean axesSwapped = Math.abs(degrees) % 180 == 90;
        return new RotationTransform(
                degrees,
                axesSwapped ? 1f : -1f,
                axesSwapped ? -1f : 1f
        );
    }

    private static int normalizeDegrees(int value) {
        int normalized = value % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        if (normalized >= 180) {
            normalized -= 360;
        }
        return normalized;
    }

}
