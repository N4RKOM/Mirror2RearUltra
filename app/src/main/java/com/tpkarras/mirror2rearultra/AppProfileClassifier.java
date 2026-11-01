package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

import java.util.Locale;

final class AppProfileClassifier {
    private AppProfileClassifier() {
    }

    @Nullable
    static String classify(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return null;
        }
        String normalized = packageName.toLowerCase(Locale.ROOT);
        if (normalized.equals("com.android.camera")
                || normalized.equals("org.codeaurora.snapcam")
                || normalized.startsWith("com.google.android.googlecamera")
                || normalized.startsWith("com.shamim.cam")
                || normalized.endsWith(".camera")) {
            return MirrorProfile.CAMERA_ID;
        }
        return null;
    }
}
