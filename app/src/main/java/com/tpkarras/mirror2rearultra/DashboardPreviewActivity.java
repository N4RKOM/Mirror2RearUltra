package com.tpkarras.mirror2rearultra;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Shows the dashboard on the rear panel itself for half a minute.
 *
 * <p>The builder's preview can only ever approximate the panel: it is a view
 * on a 1440x3200 screen standing in for a 126x294 one, at a different pixel
 * density. Whether a widget is actually legible at that size is a question the
 * approximation cannot answer, so this puts the real arrangement on the real
 * panel, driven by the real {@link RearDashboardController} rather than the
 * sample values the in-app preview uses.
 *
 * <p>Modelled on {@link CalibrationPreviewActivity}, which already runs on the
 * rear display for the calibration grid.
 */
public final class DashboardPreviewActivity extends Activity
        implements RearDashboardController.Listener {

    static final long PREVIEW_DURATION_MILLIS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishPreview = this::finishAndRemoveTask;
    private RearDashboardView view;
    private FrameLayout frame;
    private RearDashboardController controller;
    private int page = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        // Dashboard-only, whatever the saved content mode: this is a look at
        // the arrangement, not at the mirrored image behind it.
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this)
                .withContentMode(RearContentMode.DASHBOARD);
        view = new RearDashboardView(this);
        view.setDashboardSettings(settings, RearContentMode.DASHBOARD);
        // Wrapped so the page's orientation can be applied the same way the
        // mirroring screen applies it - rotating needs the panel's dimensions,
        // which only the parent knows.
        frame = new FrameLayout(this);
        frame.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        view.setOnPageChangedListener(shown -> {
            page = shown;
            applyOrientation();
        });
        setContentView(frame);
        frame.post(this::applyOrientation);

        controller = new RearDashboardController(this, settings, this);
        controller.start();

        Mirror.rearScreenSwitch(true);
        handler.postDelayed(finishPreview, PREVIEW_DURATION_MILLIS);
    }

    @Override
    public void onDashboardDataChanged(RearDashboardSnapshot snapshot) {
        runOnUiThread(() -> {
            if (view != null) {
                view.setSnapshot(snapshot);
            }
        });
    }

    /** Turns the page the way the arrangement asks for, as the panel would. */
    private void applyOrientation() {
        if (frame == null || view == null || frame.getWidth() == 0 || frame.getHeight() == 0) {
            return;
        }
        DashboardWidgetLayout.Orientation orientation =
                DashboardWidgetLayout.loadPageOrientation(this, page);
        int parentWidth = frame.getWidth();
        int parentHeight = frame.getHeight();
        boolean rotate = (orientation == DashboardWidgetLayout.Orientation.LANDSCAPE
                && parentHeight > parentWidth)
                || (orientation == DashboardWidgetLayout.Orientation.PORTRAIT
                && parentWidth > parentHeight);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        params.width = rotate ? parentHeight : FrameLayout.LayoutParams.MATCH_PARENT;
        params.height = rotate ? parentWidth : FrameLayout.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.CENTER;
        view.setLayoutParams(params);
        view.setPivotX(params.width > 0 ? params.width / 2f : view.getWidth() / 2f);
        view.setPivotY(params.height > 0 ? params.height / 2f : view.getHeight() / 2f);
        view.setRotation(rotate ? 90f : 0f);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(finishPreview);
        if (controller != null) {
            controller.stop();
        }
        // Leave the panel on if a real mirroring session owns it.
        if (!MirrorState.isActive()) {
            Mirror.rearScreenSwitch(false);
        }
        super.onDestroy();
    }
}
