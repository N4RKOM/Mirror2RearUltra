package com.tpkarras.mirror2rearultra;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Layout helpers shared by the settings screens.
 *
 * <p>Every settings page repeats the same wide-screen rule, so it lives here
 * once instead of being copied into each activity.
 */
final class SettingsLayout {

    /** Widest a settings column is allowed to get, in dp. */
    private static final int MAX_CONTENT_WIDTH_DP = 600;

    /** Below this width there is no room to gain from constraining anything. */
    private static final int WIDE_SCREEN_THRESHOLD_DP = 640;

    private SettingsLayout() {
    }

    /**
     * Caps the settings column and centres it on tablets and unfolded devices.
     *
     * <p>Without this a preference row stretches the full width of a large
     * screen and its trailing control ends up an arm's length from its label.
     *
     * <p>{@code content} must be a direct child of a scroll view, so that its
     * layout params are {@link FrameLayout.LayoutParams}.
     */
    static void constrainContentOnWideScreens(Activity activity, View content) {
        float density = activity.getResources().getDisplayMetrics().density;
        int availableWidthDp =
                Math.round(activity.getResources().getDisplayMetrics().widthPixels / density);
        if (availableWidthDp <= WIDE_SCREEN_THRESHOLD_DP) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) content.getLayoutParams();
        params.width = Math.round(MAX_CONTENT_WIDTH_DP * density);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        content.setLayoutParams(params);
    }
}
