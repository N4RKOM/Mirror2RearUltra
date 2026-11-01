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

    /** For a panel that is taller than wide, the way it is built into the phone. */
    static RotationTransform forRotation(
            int displayRotation,
            int manualRotationDegrees,
            boolean mirrorHorizontally
    ) {
        return forRotation(displayRotation, manualRotationDegrees, mirrorHorizontally, false);
    }

    /**
     * @param widePanel the panel is wider than tall in its own frame, which
     *        means it is built in sideways: held upright it is a vertical
     *        strip, so the picture has to be turned a quarter to fill it
     */
    static RotationTransform forRotation(
            int displayRotation,
            int manualRotationDegrees,
            boolean mirrorHorizontally,
            boolean widePanel
    ) {
        int degrees;
        switch (displayRotation) {
            case 1:
                degrees = widePanel ? 0 : -90;
                break;
            case 2:
                degrees = widePanel ? 90 : -180;
                break;
            case 3:
                degrees = widePanel ? 180 : 90;
                break;
            case 0:
            default:
                degrees = widePanel ? -90 : 0;
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

    /**
     * Turn of the picture as the panel sees it, for sizing: the capture is a
     * portrait picture when the screen is held upright and a landscape one
     * when it is on its side, so a sideways hold counts as a quarter turn.
     */
    static int contentQuarterTurnDegrees(
            int displayRotation, int manualRotationDegrees, boolean widePanel) {
        if (!widePanel) {
            return manualRotationDegrees;
        }
        return forRotation(displayRotation, manualRotationDegrees, false, true).degrees
                + (displayRotation % 2 == 1 ? 90 : 0);
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
