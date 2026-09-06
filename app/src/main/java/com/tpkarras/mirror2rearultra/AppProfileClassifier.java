package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Set;

final class AppProfileClassifier {
    private static final Set<String> NAVIGATION_PACKAGES = Set.of(
            "com.google.android.apps.maps",
            "com.waze",
            "ru.dublgis.dgismobile",
            "ru.yandex.yandexnavi",
            "com.yandex.navikit",
            "com.here.app.maps"
    );
    private static final Set<String> VIDEO_PACKAGES = Set.of(
            "com.google.android.youtube",
            "org.videolan.vlc",
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.plexapp.android"
    );

    private AppProfileClassifier() {
    }

    @Nullable
    static String classify(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return null;
        }
        String normalized = packageName.toLowerCase(Locale.ROOT);
        if (NAVIGATION_PACKAGES.contains(normalized)
                || normalized.startsWith("com.tomtom.")
                || normalized.startsWith("com.sygic.")) {
            return MirrorProfile.NAVIGATION_ID;
        }
        if (VIDEO_PACKAGES.contains(normalized)) {
            return MirrorProfile.VIDEO_ID;
        }
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
