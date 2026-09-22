package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * A frame over the app in front, for choosing the part of the screen the panel
 * shows.
 *
 * <p>Zoom and offsets could always crop the mirror, but only by trial: set a
 * percentage, go and look at the back of the phone, come back. Here the frame
 * is laid over the very app being mirrored, and what is inside it is what the
 * panel gets.
 *
 * <p>The frame is any shape. Dragged to the panel's own it snaps there and
 * fills the panel; left wider, as a 4:3 viewfinder is, it is shown whole with
 * black above and below rather than having its sides cut off.
 *
 * <p>Nothing is saved until Done. Until then the panel follows the frame, and
 * Cancel - or Back, or leaving the app - puts the old crop back.
 */
final class CropFrameOverlay {
    interface Listener {
        /** The frame moved; show it on the panel, but do not keep it yet. */
        void onFramePreview(CropFrame.Rect frame);

        /** Let go of: a moment for work too slow to do on every move. */
        void onFrameSettled();

        /** Framing is over, and either kept or thrown away. */
        void onFrameFinished(boolean keep, @Nullable CropFrame.Rect frame);
    }

    private final Context context;
    @Nullable private final WindowManager windowManager;
    @Nullable private final Display mainDisplay;
    private final Listener listener;
    @Nullable private View root;
    @Nullable private CropFrameView frameView;
    @Nullable private CropFrame crop;
    @Nullable private View card;
    @Nullable private View cardTitle;
    @Nullable private View cardHint;
    @Nullable private TextView values;
    private int rotation;
    private int screenWidth;
    private int screenHeight;
    /** Set between a finger taking hold of the frame and letting go. */
    private boolean grabbed;
    /** Whether the card is up, as opposed to faded out under a finger. */
    private boolean cardShown = true;

    CropFrameOverlay(Context context, Listener listener) {
        Context applicationContext = context.getApplicationContext();
        DisplayManager displayManager = applicationContext.getSystemService(DisplayManager.class);
        mainDisplay = displayManager == null
                ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        // Bound to the main screen for the same reason as the floating button:
        // the process lives on the panel, and a window taken from it would
        // otherwise be routed there.
        Context displayContext = mainDisplay == null
                ? applicationContext
                : applicationContext.createDisplayContext(mainDisplay)
                        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        this.context = new ContextThemeWrapper(displayContext, R.style.Theme_Mirror2RearUltra);
        this.listener = listener;
        windowManager = displayContext.getSystemService(WindowManager.class);
    }

    boolean isShowing() {
        return root != null;
    }

    /** The screen's rotation the frame was drawn for. */
    int rotation() {
        return rotation;
    }

    /**
     * Lays the frame over the screen, starting from the profile's own crop -
     * or, for a profile that has never been framed, from the part of the
     * screen its zoom and offsets amount to.
     *
     * @return false when there is no way to draw over other apps
     */
    boolean show(MirrorProfile profile, int panelWidth, int panelHeight) {
        if (root != null) {
            return true;
        }
        if (windowManager == null || mainDisplay == null || !Settings.canDrawOverlays(context)) {
            return false;
        }
        // The whole screen, bars included: that is what the mirror captures,
        // so that is what the frame has to be measured against.
        Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
        rotation = mainDisplay.getRotation();
        screenWidth = bounds.width();
        screenHeight = bounds.height();
        crop = new CropFrame(panelWidth, panelHeight, screenWidth, screenHeight, rotation, profile);

        View view = LayoutInflater.from(context).inflate(R.layout.overlay_crop_frame, null);
        frameView = view.findViewById(R.id.crop_frame);
        addCard((FrameLayout) view);
        values = view.findViewById(R.id.crop_frame_values);
        view.findViewById(R.id.crop_frame_done).setOnClickListener(button ->
                finish(true, frameView == null ? null : frameView.frame()));
        view.findViewById(R.id.crop_frame_cancel).setOnClickListener(button -> finish(false, null));
        view.findViewById(R.id.crop_frame_whole).setOnClickListener(button ->
                replaceFrame(crop.wholeScreen()));
        view.findViewById(R.id.crop_frame_fill).setOnClickListener(button -> {
            if (frameView != null && frameView.frame() != null) {
                replaceFrame(crop.toPanelShape(frameView.frame()));
            }
        });
        view.setOnKeyListener((target, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                finish(false, null);
            }
            return true;
        });
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            placeCard(insets);
            return insets;
        });
        frameView.bind(crop, startingFrame(profile), screenWidth, screenHeight,
                new CropFrameView.Listener() {
                    @Override
                    public void onFrameChanged(CropFrame.Rect frame) {
                        describe(frame);
                        // Hidden on the first move rather than on the touch:
                        // a tap that changes nothing should not blink it.
                        if (grabbed) {
                            showCard(false);
                        }
                        listener.onFramePreview(frame);
                    }

                    @Override
                    public void onFrameGrabbed() {
                        grabbed = true;
                    }

                    @Override
                    public void onFrameSettled() {
                        grabbed = false;
                        placeCard();
                        showCard(true);
                        listener.onFrameSettled();
                    }
                });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setFitInsetsTypes(0);
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setTitle(context.getString(R.string.crop_frame_title));
        try {
            windowManager.addView(view, params);
        } catch (RuntimeException error) {
            return false;
        }
        root = view;
        view.requestFocus();
        view.post(this::placeCard);
        return true;
    }

    /**
     * Puts the card on the screen, laid out for the way the screen is held.
     *
     * <p>Upright it is a band across the bottom. Sideways it is a column down
     * the right edge, where a camera keeps its own buttons: across the bottom
     * it would lie over the viewfinder, which is the one thing being framed.
     */
    private void addCard(FrameLayout parent) {
        boolean wide = screenWidth > screenHeight;
        View added = LayoutInflater.from(context).inflate(
                wide ? R.layout.overlay_crop_card_wide : R.layout.overlay_crop_card,
                parent, false);
        int margin = context.getResources().getDimensionPixelSize(R.dimen.spacing_md);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) added.getLayoutParams();
        params.gravity = wide
                ? Gravity.END | Gravity.CENTER_VERTICAL
                : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.leftMargin = margin;
        params.rightMargin = margin;
        parent.addView(added, params);
        card = added;
        cardTitle = added.findViewById(R.id.crop_frame_title);
        cardHint = added.findViewById(R.id.crop_frame_hint);
    }

    /**
     * Takes the card away while the frame is being dragged, and brings it
     * back when the finger lifts.
     *
     * <p>The panel is showing this screen: a card over the frame is a card on
     * the panel, and the moment it matters least is the moment the frame is
     * being aimed.
     */
    private void showCard(boolean visible) {
        // Asked on every move of the frame, so the state is checked rather
        // than the animation restarted: a fade begun afresh sixty times a
        // second never finishes, and the card hung half way.
        if (card == null || cardShown == visible) {
            return;
        }
        cardShown = visible;
        View shown = card;
        shown.animate().cancel();
        if (visible) {
            shown.setVisibility(View.VISIBLE);
            shown.animate().alpha(1f).setDuration(120L).start();
        } else {
            shown.animate().alpha(0f).setDuration(120L)
                    .withEndAction(() -> shown.setVisibility(View.INVISIBLE)).start();
        }
    }

    /** Drops the frame and puts back whatever the profile had before. */
    void cancel() {
        finish(false, null);
    }

    /** Takes the frame away and says whether to keep what it shows. */
    void finish(boolean keep, @Nullable CropFrame.Rect frame) {
        if (root == null) {
            return;
        }
        View view = root;
        root = null;
        frameView = null;
        crop = null;
        card = null;
        cardTitle = null;
        cardHint = null;
        values = null;
        if (windowManager != null) {
            try {
                windowManager.removeView(view);
            } catch (RuntimeException ignored) {
                // Already gone with the window's display.
            }
        }
        listener.onFrameFinished(keep, frame);
    }

    private CropFrame.Rect startingFrame(MirrorProfile profile) {
        MirrorProfile.Crop saved = profile.cropFor(rotation);
        if (saved != null) {
            return new CropFrame.Rect(saved.left, saved.top, saved.right, saved.bottom);
        }
        // Never framed this way round: start from the part of the screen the
        // profile shows as it stands.
        return crop.frameOf(new CropFrame.Calibration(profile.zoomPercent,
                profile.horizontalOffsetPercent, profile.verticalOffsetPercent));
    }

    private void replaceFrame(CropFrame.Rect frame) {
        if (frameView == null || crop == null) {
            return;
        }
        frameView.show(crop.settle(frame));
        placeCard();
        listener.onFrameSettled();
    }

    /** The frame's size, and the size it lands at on the panel. */
    private void describe(CropFrame.Rect frame) {
        if (crop == null) {
            return;
        }
        CropFrame.Projection projection = crop.projectionFor(frame);
        String text = context.getString(R.string.crop_frame_values,
                Math.round(frame.width() * screenWidth),
                Math.round(frame.height() * screenHeight),
                Math.round(projection.visibleWidth),
                Math.round(projection.visibleHeight));
        if (values != null) {
            values.setText(text);
        }
        if (frameView != null) {
            frameView.setContentDescription(text);
        }
    }

    private void placeCard() {
        if (root != null) {
            WindowInsets insets = root.getRootWindowInsets();
            if (insets != null) {
                placeCard(insets);
            }
        }
    }

    /**
     * Keeps the card out of the frame: at the bottom where there is room, at
     * the top when the frame reaches down past it and leaves more room above.
     * Inside the frame it is on the panel as well, since the panel is showing
     * this very screen.
     *
     * <p>A frame that leaves room nowhere - the whole screen, say - gets the
     * card stripped to its buttons and the reading, which is a third of the
     * height and so a third of the intrusion. The words it drops have been
     * read by then: they say what to drag.
     */
    private void placeCard(WindowInsets insets) {
        if (root == null || card == null || frameView == null) {
            return;
        }
        android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int margin = context.getResources().getDimensionPixelSize(R.dimen.spacing_md);
        if (screenWidth > screenHeight) {
            // The column keeps its side; only the bars move it.
            FrameLayout.LayoutParams wide = (FrameLayout.LayoutParams) card.getLayoutParams();
            if (wide.rightMargin != bars.right + margin) {
                wide.rightMargin = bars.right + margin;
                card.setLayoutParams(wide);
            }
            return;
        }
        RectF frame = frameView.frameInView();
        int height = root.getHeight();
        int cardHeight = card.getHeight();
        float roomBelow = height - bars.bottom - frame.bottom;
        float roomAbove = frame.top - bars.top;
        boolean fits = Math.max(roomBelow, roomAbove) >= cardHeight + 2 * margin;
        int words = fits ? View.VISIBLE : View.GONE;
        if (cardTitle != null && cardTitle.getVisibility() != words) {
            cardTitle.setVisibility(words);
            cardHint.setVisibility(words);
        }
        boolean top = roomBelow < cardHeight + 2 * margin && roomAbove > roomBelow;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
        int gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        int topMargin = top ? bars.top + margin : 0;
        int bottomMargin = top ? 0 : bars.bottom + margin;
        if (params.gravity == gravity && params.topMargin == topMargin
                && params.bottomMargin == bottomMargin) {
            return;
        }
        params.gravity = gravity;
        params.topMargin = topMargin;
        params.bottomMargin = bottomMargin;
        card.setLayoutParams(params);
    }
}
