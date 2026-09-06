package com.tpkarras.mirror2rearultra;

final class ProjectionGeometry {
    static final class Scale {
        final float x;
        final float y;

        Scale(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private ProjectionGeometry() {
    }

    static Scale calculateScale(
            MirrorProfile.ScaleMode mode,
            int viewportWidth,
            int viewportHeight,
            int surfaceSide,
            int sourceWidth,
            int sourceHeight,
            int manualRotationDegrees
    ) {
        if (viewportWidth <= 0
                || viewportHeight <= 0
                || surfaceSide <= 0
                || sourceWidth <= 0
                || sourceHeight <= 0) {
            return new Scale(1f, 1f);
        }

        float shortSide = Math.min(sourceWidth, sourceHeight);
        float longSide = Math.max(sourceWidth, sourceHeight);
        float contentWidth = surfaceSide * shortSide / longSide;
        float contentHeight = surfaceSide;
        if (manualRotationDegrees % 180 != 0) {
            float swap = contentWidth;
            contentWidth = contentHeight;
            contentHeight = swap;
        }

        float scaleX = viewportWidth / contentWidth;
        float scaleY = viewportHeight / contentHeight;
        switch (mode) {
            case FIT:
                float fit = Math.min(scaleX, scaleY);
                return new Scale(fit, fit);
            case STRETCH:
                return new Scale(scaleX, scaleY);
            case FILL:
            default:
                float fill = Math.max(scaleX, scaleY);
                return new Scale(fill, fill);
        }
    }

    static float calculateTranslation(int viewportSize, int offsetPercent) {
        if (viewportSize <= 0) {
            return 0f;
        }
        int clampedOffset = Math.max(-50, Math.min(50, offsetPercent));
        return viewportSize * clampedOffset / 100f;
    }
}
