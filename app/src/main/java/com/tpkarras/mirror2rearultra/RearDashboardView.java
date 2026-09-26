package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

public final class RearDashboardView extends View {
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private DashboardSettings settings = DashboardSettings.defaults();
    private RearDashboardSnapshot snapshot = new RearDashboardSnapshot(
            System.currentTimeMillis(), -1, -1, false, "", null, null, false
    );
    private RearContentMode contentMode = RearContentMode.DASHBOARD;
    private Bitmap customImage;
    private Palette currentPalette = new Palette(Color.WHITE, Color.BLACK);
    private int selectedPage = 0;
    private float touchStartX;
    private float touchStartY;
    /** The lines of the last frame, for hit testing. */
    /**
     * The smallest a widget answers to a touch at.
     *
     * <p>Material's floor for a dense control. A whole 48dp would be a
     * quarter of this panel's height, which is too much to give one line.
     */
    private static final float MINIMUM_TOUCH_TARGET_DP = 24f;
    /** Reused while measuring: this runs for every line of every frame. */
    private final Rect inkBounds = new Rect();
    private final Paint picturePaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    /** The playing app's icon, and what it was loaded for. */
    @Nullable private Bitmap appIcon;
    @Nullable private String appIconPackage;
    private int appIconSize;
    /**
     * Where each widget landed last frame, kept across the clear below.
     *
     * <p>A widget is held inside the panel by how much of it there is, and
     * the drawing needs that figure before it has drawn anything - so it
     * uses what the same widget covered the frame before.
     */
    private final Map<DashboardWidgetLayout.Widget, RectF> previousBounds = new HashMap<>();
    private final List<Line> drawnLines = new ArrayList<>();
    @Nullable private DashboardWidgetLayout.Widget selectedWidget;
    @Nullable private OnWidgetSelectedListener widgetSelectedListener;
    /** The burn-in drift in force, so recorded bounds match what is on screen. */
    private float shiftX;
    private float shiftY;
    /**
     * The rear panel's own short side and density, when this view is standing
     * in for it rather than being it. Zero on the panel itself.
     */
    private int panelShortSidePx;
    private float panelDensity;
    /** The font choice the paint is currently carrying, so it is resolved once. */
    @Nullable private DashboardWidgetLayout.Font appliedFont;
    /** The face the panel as a whole is drawn in, kept for lines that want it. */
    private Typeface panelTypeface = Typeface.DEFAULT;

    @Nullable private DashboardWidgetLayout.Widget draggedWidget;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean dragMoved;
    /** Where the dragged widget was before the finger touched it. */
    private float dragStartFractionX;
    private float dragStartFractionY;
    private boolean dragStartHadPosition;
    /**
     * The panel's own long press, for the always-on toggle.
     *
     * <p>The builder's preview had one of these once, for a carousel that no
     * longer exists, and it was removed when nothing set it. This one is the
     * panel's and has a job: a press held on the panel is the only gesture
     * there that nothing else wanted.
     */
    @Nullable private Runnable pendingLongPress;
    private boolean longPressFired;
    /** True from the moment a second finger lands until every finger is up. */
    private boolean pinching;
    @Nullable private DashboardWidgetLayout.Widget pinchedWidget;
    private float scaleAtGestureStart = 1f;
    private float spanAtGestureStart = 1f;

    /** Told what the finger did to a widget. Only the builder listens. */
    interface OnWidgetSelectedListener {
        /** Picked by a tap, so the builder may bring its card into view. */
        void onWidgetSelected(@Nullable DashboardWidgetLayout.Widget widget);

        /**
         * Taken hold of for a drag.
         *
         * <p>Separate from a tap because nothing may move on screen while a
         * finger is down on it: scrolling the card into view here slid the
         * preview out from under the finger, and the widget followed.
         */
        void onWidgetGrabbed(DashboardWidgetLayout.Widget widget);

        /** Finished being moved or resized. */
        void onWidgetChanged(DashboardWidgetLayout.Widget widget);
    }
    /** The page {@link #pageLines} last chose, for per-page layout. */
    private int currentPage = 1;
    /**
     * How often the page on screen is due to change, or zero when nothing
     * cycles. Set while the lines are being worked out and acted on once the
     * frame is drawn.
     */
    private long pageFlipPeriodMillis;
    /** How many pages the arrangement is actually showing, for the dots. */
    private int pagesOnPanel = 1;
    private final Runnable pageFlip = this::invalidate;
    /** Moves the progress along while a track is playing, and no oftener. */
    private final Runnable progressTick = this::invalidate;
    /** A call cannot wait for the next snapshot: the panel is redrawn at once. */
    private final CallWidgetState.Listener callListener = snapshot -> postInvalidate();
    /** The page swiped to, or 0 for the main page the builder names. */
    private int userPage = 0;
    /** A page a trigger holds the panel on, or 0 for none. */
    private int triggerPage = 0;
    private long lastPageInteractionMillis;
    /**
     * When the automatic cycling may start. A session opens resting on the
     * main page for one period rather than wherever the clock would put it.
     */
    private long autoPageResumeMillis = System.currentTimeMillis() + 8_000L;
    private final Runnable returnToHomePage = () -> {
        userPage = 0;
        lastPageInteractionMillis = 0L;
        autoPageResumeMillis = System.currentTimeMillis() + 8_000L;
        invalidate();
    };
    @Nullable private OnPageChangedListener pageChangedListener;

    /** Told which page is on screen, for anything that lives outside the view. */
    interface OnPageChangedListener {
        void onPageChanged(int page);
    }

    public RearDashboardView(Context context) {
        super(context);
        initialize();
    }

    public RearDashboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public RearDashboardView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        selectionPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setDashboardSettings(DashboardSettings value, RearContentMode sessionMode) {
        settings = value;
        contentMode = sessionMode;
        customImage = value.showCustomImage ? DashboardImageStore.load(getContext()) : null;
        invalidate();
    }

    void setSnapshot(RearDashboardSnapshot value) {
        snapshot = value;
        invalidate();
    }

    /**
     * Pins the view to one page instead of letting it cycle.
     *
     * <p>Zero hands it back to the timer. The builder pins it so that editing
     * page two does not mean waiting for it to come round.
     */
    void setSelectedPage(int page) {
        selectedPage = Math.max(0, page);
        invalidate();
    }

    /**
     * Holds the panel on a page a trigger asked for, or lets go with 0.
     *
     * <p>It takes the main page's place: the panel rests there, comes back to
     * it after a swipe, and does not cycle away while the trigger holds -
     * "on the charger, show the clock page" means the clock page, not the
     * clock page for eight seconds.
     */
    void setTriggerPage(int page) {
        int next = Math.max(0, page);
        if (next == triggerPage) {
            return;
        }
        triggerPage = next;
        userPage = 0;
        lastPageInteractionMillis = 0L;
        removeCallbacks(returnToHomePage);
        invalidate();
    }

    void setOnPageChangedListener(@Nullable OnPageChangedListener listener) {
        pageChangedListener = listener;
    }

    /**
     * Turns the view into something you can pick widgets in.
     *
     * <p>Only the builder's preview passes a listener. On the panel itself a
     * tap must keep meaning nothing, so the rest of this stays inert.
     */
    void setOnWidgetSelectedListener(@Nullable OnWidgetSelectedListener listener) {
        widgetSelectedListener = listener;
    }

    /**
     * Tells a preview what it is standing in for.
     *
     * <p>Text is sized from the panel's short side, but the floors and
     * ceilings around it are in dp, and dp is not the same thing on the two
     * screens: the panel reports 240dpi against the phone's 420. On the panel
     * the floor took hold and the text came out proportionally larger, so
     * widgets that stood side by side in the preview overlapped for real. With
     * the panel's own metrics the preview is a scale model of it rather than a
     * differently-proportioned drawing.
     *
     * @param shortSidePx the panel's shorter side in its own pixels, or zero
     *     for the panel itself
     */
    void setPanelMetrics(int shortSidePx, float density) {
        panelShortSidePx = shortSidePx;
        panelDensity = density;
        invalidate();
    }

    /**
     * The density to size content with: the panel's, blown up by however much
     * larger this view is than the panel.
     */
    private float contentDensity() {
        float own = getResources().getDisplayMetrics().density;
        if (panelShortSidePx <= 0 || panelDensity <= 0f) {
            return own;
        }
        float shortSide = Math.min(getWidth(), getHeight());
        if (shortSide <= 0f) {
            return own;
        }
        return panelDensity * (shortSide / panelShortSidePx);
    }

    /**
     * Gives widgets that have never been placed the place they occupy now.
     *
     * <p>Called on the way into the free layout, so it opens showing what was
     * already on screen instead of a heap in the middle. Widgets that were
     * placed before keep where they were put: switching away and back must not
     * throw an arrangement away.
     */
    void seedFreePositions() {
        for (Line line : drawnLines) {
            if (DashboardWidgetLayout.hasFreePosition(getContext(), line.widget)) {
                continue;
            }
            if (getWidth() <= 0 || getHeight() <= 0) {
                continue;
            }
            DashboardWidgetLayout.saveFreePosition(getContext(), line.widget,
                    line.bounds.centerX() / getWidth(),
                    line.bounds.centerY() / getHeight());
        }
    }

    /**
     * The face a widget asked for, or null to take the panel's.
     *
     * <p>Some of the faces on offer are small display ones carrying little
     * beyond digits, and a character they lack simply does not appear. That
     * cannot be detected from here - both hasGlyph and the measured width
     * answer for the whole system rather than for the one file - so it is left
     * visible in the preview instead of silently second-guessed.
     */
    @Nullable
    private Typeface widgetTypeface(DashboardWidgetLayout.Widget widget) {
        return PanelFonts.resolve(getContext(),
                DashboardWidgetLayout.loadWidgetFontId(getContext(), widget));
    }

    /**
     * Puts the chosen typeface on the paint, resolving it only when it changes.
     *
     * <p>Text here is drawn rather than laid out in views, so nothing arrives
     * from a theme on its own: whichever face is wanted has to be put on the
     * paint by hand.
     */
    private void applyPanelTypeface() {
        DashboardWidgetLayout.Font font = DashboardWidgetLayout.loadFont(getContext());
        if (font == appliedFont) {
            return;
        }
        appliedFont = font;
        Typeface face;
        if (font == DashboardWidgetLayout.Font.THEME) {
            face = deviceThemeTypeface();
        } else if (font == DashboardWidgetLayout.Font.PLAIN) {
            face = stockTypeface();
        } else {
            face = Typeface.DEFAULT;
        }
        panelTypeface = face == null ? Typeface.DEFAULT : face;
        textPaint.setTypeface(panelTypeface);
    }

    /**
     * Font files a theme cannot reach, in the order they are preferred.
     *
     * <p>A theme installed through Themes replaces the files under
     * /data/system/theme/fonts, which is where the sans-serif family is made
     * to point, so asking for the family by name lands on the theme's face
     * just as the default does. Only naming a file in /system, which is read
     * only and left alone, gets past it. MiSans first: it is the platform's
     * own and the panel is drawn to match the platform.
     */
    private static final String[] STOCK_FONT_FILES = {
            "/system/fonts/MiSansVF.ttf",
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
    };

    /**
     * A face from a file rather than a family.
     *
     * <p>One file carries no fallback chain, so a character it does not have
     * is drawn as a box where the family would have found it elsewhere. The
     * files chosen cover what the panel shows - digits, Latin and Cyrillic -
     * and the custom text widget is the only place anything else can arrive.
     */
    private Typeface stockTypeface() {
        for (String path : STOCK_FONT_FILES) {
            java.io.File file = new java.io.File(path);
            if (!file.canRead()) {
                continue;
            }
            try {
                Typeface face = Typeface.createFromFile(file);
                if (face != null) {
                    return face;
                }
            } catch (RuntimeException ignored) {
                // Unreadable or not a font after all; try the next.
            }
        }
        return Typeface.SANS_SERIF;
    }

    /**
     * The face the device's own theme asks for.
     *
     * <p>Read from the device-default theme rather than this app's: font
     * overlay packages change the family that theme names, and this app's
     * theme says nothing about it.
     */
    @Nullable
    private Typeface deviceThemeTypeface() {
        android.content.res.Resources.Theme theme = getContext().getResources().newTheme();
        theme.applyStyle(android.R.style.Theme_DeviceDefault, true);
        android.content.res.TypedArray attributes =
                theme.obtainStyledAttributes(new int[]{android.R.attr.fontFamily});
        String family = attributes.getString(0);
        attributes.recycle();
        return family == null || family.isEmpty()
                ? null : Typeface.create(family, Typeface.NORMAL);
    }

    /** The arrangement style in force for the page on screen. */
    private DashboardSettings.Layout currentLayout() {
        return DashboardWidgetLayout.loadPageLayout(getContext(), currentPage, settings.layout);
    }

    void setSelectedWidget(@Nullable DashboardWidgetLayout.Widget widget) {
        if (selectedWidget == widget) {
            return;
        }
        selectedWidget = widget;
        invalidate();
    }

    @Nullable DashboardWidgetLayout.Widget getSelectedWidget() {
        return selectedWidget;
    }

    /**
     * Half the height a widget covers, for holding it inside the panel.
     *
     * <p>The drawing and the drag have to agree on this. When they do not,
     * the widget stops at the edge while the finger and the stored position
     * carry on past it, and dragging back does nothing until they catch up -
     * so both ask here rather than each measuring for itself.
     *
     * <p>It is the ink, not the font's line box: the line box stands taller
     * than the glyphs in it and would stop a widget short of an edge it
     * plainly still has room to reach.
     */
    private float halfHeightOf(DashboardWidgetLayout.Widget widget, float fallback) {
        RectF box = boundsOf(widget);
        if (box == null) {
            box = previousBounds.get(widget);
        }
        return box == null ? fallback : box.height() / 2f;
    }

    /** Where a widget was drawn last frame, or null if it was not on it. */
    @Nullable
    private RectF boundsOf(DashboardWidgetLayout.Widget widget) {
        for (Line line : drawnLines) {
            if (line.widget == widget) {
                return line.bounds;
            }
        }
        return null;
    }

    /** Low wins when the range is inverted, which happens on a narrow panel. */
    private static float clampBetween(float value, float low, float high) {
        return high <= low ? low : Math.max(low, Math.min(high, value));
    }

    /**
     * The widget under a point, or null.
     *
     * <p>A point inside a widget belongs to it, and where widgets overlap the
     * topmost one takes it. A point outside them all goes to whichever comes
     * nearest, and only if it comes near enough - see {@link TouchTargets}.
     */
    @Nullable
    private DashboardWidgetLayout.Widget widgetAt(float x, float y) {
        // Sized off the density the content is drawn with rather than the
        // view's own, so the allowance grows with the drawing in a preview
        // that stands several times larger than the panel.
        float minimum = MINIMUM_TOUCH_TARGET_DP * contentDensity();
        Line nearest = null;
        float nearestMiss = Float.MAX_VALUE;
        for (int index = drawnLines.size() - 1; index >= 0; index--) {
            Line line = drawnLines.get(index);
            float miss = TouchTargets.missDistance(
                    line.bounds.left, line.bounds.top,
                    line.bounds.right, line.bounds.bottom,
                    x, y, minimum
            );
            if (miss == 0f) {
                return line.widget;
            }
            if (miss > 0f && miss < nearestMiss) {
                nearestMiss = miss;
                nearest = line;
            }
        }
        return nearest == null ? null : nearest.widget;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        // Ahead of the mode: a call is worth showing even on a panel that was
        // set to mirror the screen and nothing else. Not in the builder
        // though - a preview being edited is no place to be interrupted.
        CallWidgetState.Snapshot call = ringingCall();
        if (call != null) {
            currentPalette = resolvePalette();
            backgroundPaint.setColor(currentPalette.surface);
            canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
            panelPaint.setColor(Color.argb(
                    Math.round(settings.backgroundOpacityPercent * 2.55f),
                    Color.red(currentPalette.surface),
                    Color.green(currentPalette.surface),
                    Color.blue(currentPalette.surface)));
            textPaint.setColor(currentPalette.text);
            applyPanelTypeface();
            // Nothing from the page underneath is on screen any more, so
            // nothing from it should answer to a finger either.
            drawnLines.clear();
            drawIncomingCall(canvas, call, contentDensity());
            return;
        }
        if (!contentMode.showsDashboard()) {
            return;
        }

        Palette palette = resolvePalette();
        currentPalette = palette;
        int opacity = Math.round(settings.backgroundOpacityPercent * 2.55f);
        int surface = Color.argb(
                opacity,
                Color.red(palette.surface),
                Color.green(palette.surface),
                Color.blue(palette.surface)
        );
        if (contentMode == RearContentMode.DASHBOARD) {
            backgroundPaint.setColor(palette.surface);
            canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
            drawCustomImage(canvas);
        }
        panelPaint.setColor(surface);
        textPaint.setColor(palette.text);

        pageFlipPeriodMillis = 0L;
        applyPanelTypeface();
        float density = contentDensity();
        float shift = burnInShift(density);
        shiftX = shift;
        shiftY = -shift;
        for (Line line : drawnLines) {
            RectF remembered = previousBounds.get(line.widget);
            if (remembered == null) {
                remembered = new RectF();
                previousBounds.put(line.widget, remembered);
            }
            remembered.set(line.bounds);
        }
        drawnLines.clear();
        canvas.save();
        canvas.translate(shift, -shift);

        drawGrid(canvas);
        List<Line> lines = pageLines(createLines());
        // pageLines has just settled which page is on screen, and each page may
        // want its own arrangement: a dense trip page reads better compact than
        // the clock page beside it.
        DashboardSettings.Layout layout = DashboardWidgetLayout.loadPageLayout(
                getContext(), currentPage, settings.layout);
        if (containsFullscreenWidget(lines)) {
            drawFullscreen(canvas, lines, density);
        } else if (layout == DashboardSettings.Layout.CORNERS) {
            drawCorners(canvas, lines, density);
        } else if (layout == DashboardSettings.Layout.COMPACT) {
            drawCompact(canvas, lines, density);
        } else if (layout == DashboardSettings.Layout.FREE) {
            drawFree(canvas, lines, density);
        } else {
            drawStacked(canvas, lines, density);
        }
        drawPageDots(canvas, density);
        canvas.restore();
        drawSelection(canvas, getResources().getDisplayMetrics().density);
        schedulePageFlip();
    }

    /**
     * A dot for each page, the one on screen filled in.
     *
     * <p>The panel turns pages on a swipe and on a timer, and said nothing
     * about how many there were or which one this was - so a page that
     * happened to be empty looked like a fault, and a swipe that did nothing
     * looked the same as one that had reached the end.
     *
     * <p>They sit in the margin the layouts leave below them, and they are
     * drawn inside the burn-in shift so they wander with everything else
     * rather than marking the same pixels all day.
     */
    private void drawPageDots(Canvas canvas, float density) {
        if (pagesOnPanel <= 1 || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float radius = Math.max(1f, density * 0.9f);
        float step = radius * 3.4f;
        float y = getHeight() - Math.max(3f, density * 3.2f) - radius;
        float x = (getWidth() - step * (pagesOnPanel - 1)) / 2f;
        iconPaint.setStyle(Paint.Style.FILL);
        for (int page = 1; page <= pagesOnPanel; page++) {
            iconPaint.setColor(page == currentPage ? currentPalette.text : mutedColour());
            canvas.drawCircle(x, y, page == currentPage ? radius : radius * 0.78f, iconPaint);
            x += step;
        }
        iconPaint.setStyle(Paint.Style.STROKE);
    }

    /** Outlines the picked widget, so the preview says what is being edited. */
    private void drawSelection(Canvas canvas, float density) {
        if (selectedWidget == null || widgetSelectedListener == null) {
            return;
        }
        for (Line line : drawnLines) {
            if (line.widget != selectedWidget) {
                continue;
            }
            float padding = 3f * density;
            RectF box = new RectF(line.bounds);
            box.inset(-padding, -padding);
            selectionPaint.setColor(MaterialColors.getColor(this,
                    androidx.appcompat.R.attr.colorPrimary, currentPalette.text));
            selectionPaint.setStrokeWidth(Math.max(1f, 1.2f * density));
            canvas.drawRoundRect(box, 6f * density, 6f * density, selectionPaint);
            return;
        }
    }

    /**
     * Notes that something on screen cycles, and how fast.
     *
     * <p>Both the page rotation and the overflow rotation can be running at
     * once, so the shorter of the two wins: a redraw serves them both.
     */
    private void requestPageFlip(long periodMillis) {
        if (selectedPage > 0) {
            return;
        }
        pageFlipPeriodMillis = pageFlipPeriodMillis == 0L
                ? periodMillis
                : Math.min(pageFlipPeriodMillis, periodMillis);
    }

    /** Wakes the view when the next page is due, and not before. */
    private void schedulePageFlip() {
        removeCallbacks(pageFlip);
        if (pageFlipPeriodMillis <= 0L) {
            return;
        }
        long period = pageFlipPeriodMillis;
        long delay = period - (System.currentTimeMillis() % period);
        postDelayed(pageFlip, Math.max(250L, delay));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CallWidgetState.addListener(callListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        CallWidgetState.removeListener(callListener);
        cancelPendingLongPress();
        removeCallbacks(pageFlip);
        removeCallbacks(progressTick);
        removeCallbacks(returnToHomePage);
        super.onDetachedFromWindow();
    }

    private void drawCustomImage(Canvas canvas) {
        if (customImage == null) return;
        float scale = Math.max(getWidth() / (float) customImage.getWidth(),
                getHeight() / (float) customImage.getHeight());
        float width = customImage.getWidth() * scale;
        float height = customImage.getHeight() * scale;
        RectF destination = new RectF((getWidth() - width) / 2f, (getHeight() - height) / 2f,
                (getWidth() + width) / 2f, (getHeight() + height) / 2f);
        imagePaint.setAlpha(Math.round(settings.customImageOpacityPercent * 2.55f));
        canvas.drawBitmap(customImage, null, destination, imagePaint);
    }

    /**
     * Adds a widget's line, or drops it when it has nothing to report.
     *
     * <p>These widgets used to draw a dash whenever their data was missing,
     * while battery, temperature, weather, the profile name and the custom
     * text quietly disappeared instead. On a panel that holds about five lines,
     * a column of dashes is the whole panel, so the default is now to drop the
     * row and let the rest move up. Setting the widget to appear always brings
     * the dash back.
     */
    private void addLine(List<Line> lines, DashboardWidgetLayout.Widget widget,
            boolean hasData, String value, Icon icon) {
        if (!hasData && DashboardWidgetLayout.loadPresence(getContext(), widget)
                == DashboardWidgetLayout.Presence.WHEN_DATA) {
            return;
        }
        lines.add(new Line(widget, hasData ? value : "—", false, icon));
    }

    private List<Line> createLines() {
        List<Line> lines = new ArrayList<>();
        Date date = new Date(snapshot.timestampMillis);
        Locale locale = Locale.getDefault();
        if (settings.showClock) {
            DashboardWidgetLayout.Variant variant = DashboardWidgetLayout.loadVariant(
                    getContext(), DashboardWidgetLayout.Widget.CLOCK);
            String pattern;
            if (variant == DashboardWidgetLayout.Variant.ALTERNATE) {
                pattern = android.text.format.DateFormat.is24HourFormat(getContext())
                        ? "HH\nmm" : "h\nmm";
            } else if (variant == DashboardWidgetLayout.Variant.DETAILED) {
                pattern = android.text.format.DateFormat.is24HourFormat(getContext())
                        ? "HH:mm:ss" : "h:mm:ss";
            } else {
                pattern = android.text.format.DateFormat.is24HourFormat(getContext())
                        ? "HH:mm" : "h:mm";
            }
            lines.add(new Line(DashboardWidgetLayout.Widget.CLOCK,
                    new SimpleDateFormat(pattern, locale).format(date),
                    true,
                    Icon.NONE
            ));
        }
        if (settings.showDate) {
            DashboardWidgetLayout.Variant variant = DashboardWidgetLayout.loadVariant(
                    getContext(), DashboardWidgetLayout.Widget.DATE);
            String pattern = variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? "dd.MM" : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? "dd.MM.yyyy" : "d MMM";
            lines.add(new Line(DashboardWidgetLayout.Widget.DATE,
                    new SimpleDateFormat(pattern, locale).format(date),
                    false,
                    Icon.NONE
            ));
        }
        if (settings.showBattery && snapshot.batteryPercent >= 0) {
            String value = getResources().getString(
                    snapshot.charging
                            ? R.string.dashboard_battery_charging
                            : R.string.dashboard_battery,
                    snapshot.batteryPercent
            );
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.BATTERY);
            if (variant == DashboardWidgetLayout.Variant.ALTERNATE) {
                value = snapshot.batteryPercent + "\n%";
            } else if (variant == DashboardWidgetLayout.Variant.DETAILED) {
                // A word either way. With only the charging one to offer, the
                // detailed form was the ordinary form whenever the phone was
                // off the charger, which is most of the time.
                value += "\n" + getResources().getString(snapshot.charging
                        ? R.string.dashboard_variant_charging
                        : R.string.dashboard_variant_battery);
            }
            lines.add(new Line(DashboardWidgetLayout.Widget.BATTERY, value, false,
                    snapshot.charging ? Icon.BATTERY_CHARGING : Icon.BATTERY));
        }
        if (settings.showTemperature && snapshot.temperatureTenthsCelsius >= 0) {
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.TEMPERATURE);
            float degrees = inDegrees(snapshot.temperatureTenthsCelsius / 10f);
            String value = variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? Math.round(degrees) + "°"
                    : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? String.format(locale, "%.1f %s", degrees, degreeName())
                    : getResources().getString(R.string.dashboard_device_temperature, degrees);
            lines.add(new Line(DashboardWidgetLayout.Widget.TEMPERATURE, value, false, Icon.TEMPERATURE));
        }
        if (settings.showWeather) {
            String weather = weatherVariant(DashboardWidgetLayout.Widget.WEATHER);
            if (!weather.isEmpty()) {
                lines.add(new Line(DashboardWidgetLayout.Widget.WEATHER, weather, false, weatherIcon(snapshot.weatherCode)));
            }
        }
        if (settings.showNextAlarm) {
            boolean hasData = snapshot.nextAlarmMillis != null;
            String value = "";
            if (hasData) {
                DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.NEXT_ALARM);
                String timePattern = android.text.format.DateFormat.is24HourFormat(getContext())
                        ? "HH:mm" : "h:mm";
                String pattern = variant == DashboardWidgetLayout.Variant.ALTERNATE
                        ? timePattern.replace(":", "\n")
                        : variant == DashboardWidgetLayout.Variant.DETAILED
                        ? "dd.MM\n" + timePattern : timePattern;
                value = new SimpleDateFormat(pattern, locale).format(new Date(snapshot.nextAlarmMillis));
            }
            addLine(lines, DashboardWidgetLayout.Widget.NEXT_ALARM, hasData, value, Icon.ALARM);
        }
        if (settings.showMedia) {
            boolean hasData = !snapshot.mediaTitle.isEmpty() || !snapshot.mediaArtist.isEmpty();
            addLine(lines, DashboardWidgetLayout.Widget.MEDIA, hasData,
                    mediaVariant(DashboardWidgetLayout.Widget.MEDIA), Icon.MEDIA);
        }
        if (settings.showCompass) {
            boolean hasData = snapshot.headingDegrees != null;
            String value = "";
            if (hasData) {
                String direction = cardinalDirection(snapshot.headingDegrees);
                DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.COMPASS);
                value = variant == DashboardWidgetLayout.Variant.ALTERNATE ? direction
                        : variant == DashboardWidgetLayout.Variant.DETAILED
                        ? Math.round(snapshot.headingDegrees) + "°\n" + direction
                        : Math.round(snapshot.headingDegrees) + "° " + direction;
            }
            addLine(lines, DashboardWidgetLayout.Widget.COMPASS, hasData, value, Icon.COMPASS);
        }
        if (settings.showSpeed) {
            boolean hasData = snapshot.speedMetersPerSecond != null;
            boolean imperial = imperialUnits();
            int speed = !hasData ? 0 : Math.round(snapshot.speedMetersPerSecond
                    * (imperial ? 2.236936f : 3.6f));
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.SPEED);
            String value = !hasData ? "" : variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? String.valueOf(speed) : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? speed + "\n" + getResources().getString(imperial
                            ? R.string.dashboard_speed_unit_imperial
                            : R.string.dashboard_speed_unit)
                    : getResources().getString(imperial
                            ? R.string.dashboard_speed_short_imperial
                            : R.string.dashboard_speed_short, speed);
            addLine(lines, DashboardWidgetLayout.Widget.SPEED, hasData, value, Icon.SPEED);
        }
        if (settings.showAltitude) {
            boolean hasData = snapshot.altitudeMeters != null;
            boolean imperial = imperialUnits();
            long altitude = !hasData ? 0L : Math.round(snapshot.altitudeMeters
                    * (imperial ? 3.280840d : 1d));
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.ALTITUDE);
            String value = !hasData ? "" : variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? String.valueOf(altitude) : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? altitude + "\n" + getResources().getString(imperial
                            ? R.string.dashboard_altitude_unit_imperial
                            : R.string.dashboard_altitude_unit)
                    : getResources().getString(imperial
                            ? R.string.dashboard_altitude_short_imperial
                            : R.string.dashboard_altitude_short, altitude);
            addLine(lines, DashboardWidgetLayout.Widget.ALTITUDE, hasData, value, Icon.ALTITUDE);
        }
        if (settings.showSessionTimer) {
            String value = formatElapsed(snapshot.sessionElapsedMillis);
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.SESSION_TIMER);
            if (variant == DashboardWidgetLayout.Variant.ALTERNATE) value = value.replace(":", "\n");
            else if (variant == DashboardWidgetLayout.Variant.DETAILED) value = formatElapsedDetailed(snapshot.sessionElapsedMillis);
            lines.add(new Line(DashboardWidgetLayout.Widget.SESSION_TIMER, value, false, Icon.TIMER));
        }
        if (settings.showActiveProfile && !snapshot.activeProfileName.isEmpty()) {
            lines.add(new Line(DashboardWidgetLayout.Widget.ACTIVE_PROFILE,
                    textVariant(DashboardWidgetLayout.Widget.ACTIVE_PROFILE, snapshot.activeProfileName),
                    false, Icon.PROFILE));
        }
        if (settings.showCustomText && !settings.customText.isEmpty()) {
            lines.add(new Line(DashboardWidgetLayout.Widget.CUSTOM_TEXT,
                    textVariant(DashboardWidgetLayout.Widget.CUSTOM_TEXT, settings.customText),
                    false, Icon.TEXT));
        }
        if (DashboardWidgetLayout.isExtraEnabled(getContext(), DashboardWidgetLayout.Widget.NETWORK)) {
            addLine(lines, DashboardWidgetLayout.Widget.NETWORK,
                    !snapshot.networkSummary.isEmpty(),
                    textVariant(DashboardWidgetLayout.Widget.NETWORK, snapshot.networkSummary), Icon.NETWORK);
        }
        if (DashboardWidgetLayout.isExtraEnabled(getContext(), DashboardWidgetLayout.Widget.MEMORY)) {
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.MEMORY);
            String value = percentVariant(snapshot.memoryPercent, variant,
                    getResources().getString(R.string.dashboard_variant_memory));
            addLine(lines, DashboardWidgetLayout.Widget.MEMORY,
                    snapshot.memoryPercent >= 0, value, Icon.MEMORY);
        }
        if (DashboardWidgetLayout.isExtraEnabled(getContext(), DashboardWidgetLayout.Widget.STORAGE)) {
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.STORAGE);
            String value = percentVariant(snapshot.storagePercentFree, variant,
                    getResources().getString(R.string.dashboard_variant_storage_free));
            addLine(lines, DashboardWidgetLayout.Widget.STORAGE,
                    snapshot.storagePercentFree >= 0, value, Icon.STORAGE);
        }
        if (DashboardWidgetLayout.isExtraEnabled(getContext(), DashboardWidgetLayout.Widget.NOTIFICATIONS)) {
            // Nothing unread is the empty case here, not a missing reading.
            int count = NotificationWidgetState.getCount();
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.NOTIFICATIONS);
            // Capped at nine rather than ninety-nine: the compact form is
            // there to hold its width on a 126 pixel panel, and a cap that
            // only bit past a hundred unread never did.
            String value = variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? (count > 9 ? "9+" : String.valueOf(count))
                    : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? count + "\n" + getResources().getString(R.string.dashboard_variant_notifications)
                    : String.valueOf(count);
            addLine(lines, DashboardWidgetLayout.Widget.NOTIFICATIONS,
                    count > 0, value, Icon.NOTIFICATIONS);
        }
        if (DashboardWidgetLayout.isExtraEnabled(
                getContext(), DashboardWidgetLayout.Widget.TIMER)) {
            long duration = DashboardWidgetLayout.timerMinutes(getContext()) * 60_000L;
            if (TimerWidgetState.finishIfElapsed(getContext(), duration)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            long shown = duration > 0
                    ? Math.max(0L, duration - TimerWidgetState.elapsedMillis(getContext()))
                    : TimerWidgetState.elapsedMillis(getContext());
            String value = formatElapsed(shown);
            DashboardWidgetLayout.Variant variant =
                    variantOf(DashboardWidgetLayout.Widget.TIMER);
            if (variant == DashboardWidgetLayout.Variant.ALTERNATE) {
                // Stacked, the way the session timer's compact form reads.
                value = value.replace(":", "\n");
            } else if (variant == DashboardWidgetLayout.Variant.DETAILED) {
                value = formatElapsedDetailed(shown);
            }
            // A stopped countdown at zero has finished; a stopped stopwatch at
            // zero has not started. Both are worth a row, so this one is
            // always drawn rather than dropping out when there is no reading.
            addLine(lines, DashboardWidgetLayout.Widget.TIMER, true, value, Icon.TIMER);
        }
        if (DashboardWidgetLayout.isExtraEnabled(
                getContext(), DashboardWidgetLayout.Widget.LAST_NOTIFICATION)) {
            // Three ways to say the same arrival, from least to most told.
            // ALTERNATE names only the app on purpose: the panel points away
            // from its owner, and "Telegram" answers "is it worth picking the
            // phone up" without putting the message in front of the room.
            DashboardWidgetLayout.Variant variant =
                    variantOf(DashboardWidgetLayout.Widget.LAST_NOTIFICATION);
            String app = snapshot.notificationApp;
            String title = snapshot.notificationTitle;
            String text = snapshot.notificationText;
            String value;
            if (variant == DashboardWidgetLayout.Variant.ALTERNATE) {
                value = app.isEmpty() ? title : app;
            } else if (variant == DashboardWidgetLayout.Variant.DETAILED) {
                String head = app.isEmpty() ? title
                        : title.isEmpty() ? app : app + " · " + title;
                value = text.isEmpty() ? head : head.isEmpty() ? text : head + "\n" + text;
            } else {
                value = title.isEmpty() ? app.isEmpty() ? text : app : title;
            }
            addLine(lines, DashboardWidgetLayout.Widget.LAST_NOTIFICATION,
                    !value.isEmpty(), value, Icon.MESSAGE);
        }
        if (DashboardWidgetLayout.isExtraEnabled(getContext(), DashboardWidgetLayout.Widget.CALENDAR)) {
            boolean hasData = snapshot.calendarStartMillis != null;
            String value = "";
            if (hasData) {
                value = new SimpleDateFormat(
                        android.text.format.DateFormat.is24HourFormat(getContext())
                                ? "HH:mm" : "h:mm",
                        locale).format(new Date(snapshot.calendarStartMillis));
                if (!snapshot.calendarTitle.isEmpty()) {
                    value += "  " + snapshot.calendarTitle;
                }
                DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.CALENDAR);
                if (variant == DashboardWidgetLayout.Variant.ALTERNATE
                        && !snapshot.calendarTitle.isEmpty()) {
                    value = snapshot.calendarTitle;
                } else if (variant == DashboardWidgetLayout.Variant.DETAILED
                        && !snapshot.calendarTitle.isEmpty()) {
                    value = value.replace("  ", "\n");
                }
            }
            addLine(lines, DashboardWidgetLayout.Widget.CALENDAR, hasData, value, Icon.CALENDAR);
        }
        if (DashboardWidgetLayout.isExtraEnabled(getContext(), DashboardWidgetLayout.Widget.STEPS)) {
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.STEPS);
            // Stacked on the thousands, which is what compact means
            // everywhere else here - the battery's figure over its sign, the
            // clock's hours over its minutes. Grouping the digits, as this
            // did, made the compact form the widest of the three.
            //
            // The separator is whatever the locale groups with, so it is found
            // by keeping the digits rather than by naming it. Not \D, which
            // counts only nought to nine: Persian and Arabic number in their
            // own digits, and the whole count came back as empty lines.
            String value = variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? String.format(locale, "%,d", snapshot.stepsToday)
                            .replaceAll("[^\\p{N}]", "\n")
                    : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? snapshot.stepsToday + "\n" + getResources().getString(R.string.dashboard_variant_steps)
                    : String.valueOf(snapshot.stepsToday);
            addLine(lines, DashboardWidgetLayout.Widget.STEPS,
                    snapshot.stepsToday >= 0, value, Icon.STEPS);
        }
        if (DashboardWidgetLayout.isExtraEnabled(
                getContext(), DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER)) {
            String value = weatherVariant(DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER);
            addLine(lines, DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER,
                    true, value.isEmpty() ? "—" : value, weatherIcon(snapshot.weatherCode));
        }
        if (DashboardWidgetLayout.isExtraEnabled(
                getContext(), DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA)) {
            boolean hasMedia = !snapshot.mediaTitle.isEmpty() || !snapshot.mediaArtist.isEmpty();
            addLine(lines, DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA, true,
                    hasMedia ? mediaVariant(DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA) : "—",
                    Icon.MEDIA);
        }
        DashboardWidgetLayout.sort(getContext(), lines);
        lines.removeIf(line -> !DashboardWidgetLayout.isVisible(getContext(), line.widget));
        return lines;
    }

    private List<Line> pageLines(List<Line> source) {
        int maxPage = 1;
        for (Line line : source) maxPage = Math.max(maxPage,
                DashboardWidgetLayout.loadPage(getContext(), line.widget));
        pagesOnPanel = maxPage;
        // The wall clock rather than the snapshot: the snapshot only arrives
        // every thirty seconds unless the session timer is on, so a page that
        // turned with it sat still for half a minute at a time whatever the
        // help text promised.
        long now = System.currentTimeMillis();
        int page;
        if (selectedPage > 0) {
            // Pinned from outside - the builder. It is clamped to the pages the
            // arrangement declares rather than to the pages that happen to hold
            // a widget, so an empty page can still be looked at while it is
            // being filled. The panel's own cycling below stays on the pages
            // that have something to show, so it never sits on a blank screen.
            page = Math.min(selectedPage,
                    Math.max(1, DashboardWidgetLayout.loadPageCount(getContext())));
        } else if (maxPage <= 1) {
            setCurrentPage(1);
            return paginateLines(source);
        } else {
            boolean auto = DashboardWidgetLayout.isAutoPageSwitchEnabled(getContext());
            int home = Math.min(maxPage, triggerPage > 0
                    ? triggerPage : DashboardWidgetLayout.loadHomePage(getContext()));
            int resting = userPage > 0 ? Math.min(userPage, maxPage) : home;
            if (lastPageInteractionMillis > 0L) {
                page = resting;
            } else if (auto && triggerPage == 0 && now >= autoPageResumeMillis) {
                // Counted on from the main page, so cycling moves on from the
                // page the panel was resting on. The steps stay on the wall
                // clock's eight-second grid that the flip is scheduled on.
                // Only pages with something on them and not left out of the
                // cycle are turned to.
                boolean[] inCycle = new boolean[maxPage + 1];
                for (Line line : source) {
                    int linePage = DashboardWidgetLayout.loadPage(getContext(), line.widget);
                    if (linePage <= maxPage) inCycle[linePage] = true;
                }
                for (int candidate = 1; candidate <= maxPage; candidate++) {
                    inCycle[candidate] &= DashboardWidgetLayout.isPageInCycle(getContext(), candidate);
                }
                long steps = now / 8_000L - autoPageResumeMillis / 8_000L;
                page = DashboardPages.autoPage(home, steps, inCycle);
                requestPageFlip(8_000L);
            } else {
                page = resting;
            }
        }
        setCurrentPage(page);
        List<Line> result = new ArrayList<>();
        for (Line line : source) {
            if (DashboardWidgetLayout.loadPage(getContext(), line.widget) == page) result.add(line);
        }
        return paginateLines(result);
    }

    /**
     * Records the page being drawn and tells whoever asked.
     *
     * <p>The callback is posted rather than delivered here: it lands during
     * {@code onDraw}, and its listeners rotate and re-measure the view.
     */
    private void setCurrentPage(int page) {
        if (currentPage == page) {
            return;
        }
        currentPage = page;
        OnPageChangedListener listener = pageChangedListener;
        if (listener != null) {
            post(() -> listener.onPageChanged(page));
        }
    }

    /**
     * Turns finger movement into a selection, a drag, or a page turn.
     *
     * <p>On the panel itself only the page turn exists: a tap there must keep
     * meaning nothing. In the builder's preview the same view also picks a
     * widget, and in the free layout drags it, which is the only place a
     * position can be chosen directly rather than out of three alignments.
     */
    /**
     * Turns finger movement into a selection, a drag, a pinch, or a page turn.
     *
     * <p>On the panel itself only the page turn exists: a tap there must keep
     * meaning nothing. In the builder's preview the same view also picks a
     * widget, and in the free layout moves and resizes it.
     *
     * <p>The pinch is tracked here rather than by {@link
     * android.view.ScaleGestureDetector}, which only starts once the fingers
     * have moved a threshold apart. That threshold cost the gesture both ends:
     * the widget went on following the first finger until the detector woke
     * up, and a pinch inwards had already spent much of its travel by the time
     * the reference span was taken, so it could barely shrink anything.
     */
    @Override public boolean onTouchEvent(MotionEvent event) {
        boolean interactive = widgetSelectedListener != null;
        // The call card has nothing to press. Letting a touch through would
        // reach the page underneath it - cycling a widget nobody can see, or
        // on a long press turning AOD on or off.
        if (ringingCall() != null) {
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                touchStartX = event.getX();
                touchStartY = event.getY();
                dragMoved = false;
                draggedWidget = null;
                pinching = false;
                pinchedWidget = null;
                cancelPendingLongPress();
                longPressFired = false;
                if (interactive && currentLayout() == DashboardSettings.Layout.FREE) {
                    DashboardWidgetLayout.Widget hit = widgetAt(event.getX(), event.getY());
                    if (hit != null) {
                        beginDrag(hit, event.getX(), event.getY());
                    }
                } else if (!interactive) {
                    float downX = event.getX();
                    float downY = event.getY();
                    pendingLongPress = () -> {
                        pendingLongPress = null;
                        longPressFired = true;
                        if (widgetAt(downX, downY) == DashboardWidgetLayout.Widget.TIMER) {
                            TimerWidgetState.reset(getContext());
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            invalidate();
                            return;
                        }
                        toggleAlwaysOnFromPanel();
                    };
                    postDelayed(pendingLongPress, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (!interactive || event.getPointerCount() < 2) {
                    return true;
                }
                // A second finger means a pinch, not a drag. Whatever the one
                // finger dragged on the way here is put back: a pinch should
                // resize a widget, not shove it across the panel first.
                if (draggedWidget != null) {
                    restoreDragStart();
                }
                // Whatever the fingers are actually around, falling back to
                // the picked widget when they are around nothing: pinching on
                // a widget should resize that one, not the last one tapped.
                DashboardWidgetLayout.Widget target = widgetAt(
                        (event.getX(0) + event.getX(1)) / 2f,
                        (event.getY(0) + event.getY(1)) / 2f);
                if (target == null) {
                    target = selectedWidget;
                }
                if (target == null) {
                    return true;
                }
                draggedWidget = null;
                pinchedWidget = target;
                pinching = true;
                setSelectedWidget(target);
                widgetSelectedListener.onWidgetGrabbed(target);
                scaleAtGestureStart = DashboardWidgetLayout.scale(getContext(), target);
                spanAtGestureStart = Math.max(1f, spanOf(event));
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pinching) {
                    if (pinchedWidget != null && event.getPointerCount() >= 2) {
                        DashboardWidgetLayout.saveScale(getContext(), pinchedWidget,
                                scaleAtGestureStart * (spanOf(event) / spanAtGestureStart));
                        invalidate();
                    }
                    return true;
                }
                if (pendingLongPress != null && Math.hypot(event.getX() - touchStartX,
                        event.getY() - touchStartY) > ViewConfiguration.get(getContext())
                        .getScaledTouchSlop()) {
                    cancelPendingLongPress();
                }
                if (draggedWidget == null) {
                    return true;
                }
                if (Math.hypot(event.getX() - touchStartX, event.getY() - touchStartY) > 4f) {
                    dragMoved = true;
                }
                moveDragTo(event.getX(), event.getY());
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                // One finger left: stop resizing, but do not let what remains
                // of the gesture be read as a drag or a tap.
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                cancelPendingLongPress();
                if (longPressFired) {
                    longPressFired = false;
                    return true;
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (pinching) {
                    DashboardWidgetLayout.Widget resized = pinchedWidget;
                    pinching = false;
                    pinchedWidget = null;
                    if (resized != null) {
                        widgetSelectedListener.onWidgetChanged(resized);
                    }
                    return true;
                }
                if (draggedWidget != null) {
                    DashboardWidgetLayout.Widget moved = draggedWidget;
                    draggedWidget = null;
                    if (dragMoved) {
                        widgetSelectedListener.onWidgetChanged(moved);
                        return true;
                    }
                }
                float distance = event.getX() - touchStartX;
                boolean swipe = Math.abs(distance) >= Math.max(18f, getWidth() * 0.18f);
                if (interactive && !swipe) {
                    DashboardWidgetLayout.Widget hit = widgetAt(event.getX(), event.getY());
                    setSelectedWidget(hit);
                    widgetSelectedListener.onWidgetSelected(hit);
                    return true;
                }
                if (!interactive && !swipe && handleFullscreenTap(event.getX(), event.getY())) {
                    markPageInteraction();
                    return true;
                }
                if (!interactive && !swipe && cycleVariantAt(event.getX(), event.getY())) {
                    markPageInteraction();
                    return true;
                }
                if (swipe) {
                    int maxPage = 1;
                    for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.Widget.values())
                        maxPage = Math.max(maxPage, DashboardWidgetLayout.loadPage(getContext(), widget));
                    if (interactive) {
                        // The builder walks the pages it declares, empty ones
                        // included, and follows along through the page listener.
                        maxPage = Math.max(1, DashboardWidgetLayout.loadPageCount(getContext()));
                    }
                    int current = selectedPage > 0 ? selectedPage : currentPage;
                    int next = distance < 0 ? current % maxPage + 1
                            : (current + maxPage - 2) % maxPage + 1;
                    if (interactive) {
                        selectedPage = next;
                    } else {
                        userPage = next;
                        markPageInteraction();
                    }
                    invalidate();
                }
                return true;
            }
            default:
                return true;
        }
    }

    /** Distance between the first two fingers. */
    private static float spanOf(MotionEvent event) {
        return (float) Math.hypot(
                event.getX(0) - event.getX(1),
                event.getY(0) - event.getY(1));
    }

    /**
     * Drops whatever gesture was under way.
     *
     * <p>Called when the panel stops taking touches part-way through one. The
     * finger's release then never arrives, so nothing cancels the long press
     * that the press had armed - and on this panel a long press turns AOD on
     * or off, which is a settings change nobody asked for.
     */
    void cancelTouchInProgress() {
        cancelPendingLongPress();
        longPressFired = false;
        draggedWidget = null;
        pinching = false;
        pinchedWidget = null;
    }

    private void cancelPendingLongPress() {
        if (pendingLongPress != null) {
            removeCallbacks(pendingLongPress);
            pendingLongPress = null;
        }
    }

    /**
     * A tap on a widget steps its display style on by one.
     *
     * <p>The styles were reachable only from the builder, which means putting
     * the phone down, picking it up and turning it over to ask the clock for
     * seconds. They are a small, reversible, per-widget thing - exactly what a
     * tap on the thing itself should do.
     */
    private boolean cycleVariantAt(float x, float y) {
        DashboardWidgetLayout.Widget hit = widgetAt(x, y);
        if (hit == null || !DashboardWidgetLayout.supportsVariant(hit)) {
            return false;
        }
        // The timer is a control, not a readout: a tap on it should do the
        // thing it is for rather than restyle it.
        if (hit == DashboardWidgetLayout.Widget.TIMER) {
            TimerWidgetState.toggle(getContext());
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            // The panel redraws every second only while something is counting,
            // so the session has to be told the answer changed.
            MirrorSettings.saveDashboardSettings(getContext(),
                    MirrorSettings.loadDashboardSettings(getContext()));
            invalidate();
            return true;
        }
        DashboardWidgetLayout.Variant[] all = DashboardWidgetLayout.Variant.values();
        DashboardWidgetLayout.Variant next =
                all[(variantOf(hit).ordinal() + 1) % all.length];
        DashboardWidgetLayout.saveVariant(getContext(), hit, next);
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        invalidate();
        return true;
    }

    /**
     * A press held on the panel turns always-on on, or back off.
     *
     * <p>Saving the dashboard settings unchanged is what publishes it: the
     * timeout lives outside them, and the session reconfigures its idle timer
     * when they are saved. Without that the panel would keep counting down
     * against the mode it started with.
     */
    private void toggleAlwaysOnFromPanel() {
        DashboardWidgetLayout.IdleMode now = DashboardWidgetLayout.toggleAlwaysOn(getContext());
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        MirrorSettings.saveDashboardSettings(getContext(),
                MirrorSettings.loadDashboardSettings(getContext()));
        markPageInteraction();
        announceForAccessibility(getResources().getString(
                now == DashboardWidgetLayout.IdleMode.ALWAYS_ON
                        ? R.string.dashboard_idle_always_on
                        : R.string.dashboard_idle_mode_title));
        invalidate();
    }

    private void beginDrag(DashboardWidgetLayout.Widget widget, float x, float y) {
        draggedWidget = widget;
        setSelectedWidget(widget);
        widgetSelectedListener.onWidgetGrabbed(widget);
        dragStartHadPosition = DashboardWidgetLayout.hasFreePosition(getContext(), widget);
        dragStartFractionX = DashboardWidgetLayout.loadFreeX(getContext(), widget);
        dragStartFractionY = DashboardWidgetLayout.loadFreeY(getContext(), widget);
        // Keep the grab point under the finger rather than snapping the
        // widget's middle to it.
        dragOffsetX = dragStartHadPosition ? dragStartFractionX * getWidth() - x : 0f;
        dragOffsetY = dragStartHadPosition ? dragStartFractionY * getHeight() - y : 0f;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    /** Undoes whatever a drag did, for a gesture that turned out to be a pinch. */
    private void restoreDragStart() {
        if (draggedWidget == null) {
            return;
        }
        if (dragStartHadPosition) {
            DashboardWidgetLayout.saveFreePosition(
                    getContext(), draggedWidget, dragStartFractionX, dragStartFractionY);
        } else {
            DashboardWidgetLayout.clearFreePosition(getContext(), draggedWidget);
        }
        invalidate();
    }

    /**
     * The grid the free layout snaps to, in view pixels.
     *
     * <p>Taken from the short side so the cells stay square whichever way
     * round the panel is.
     */
    private float gridStep() {
        return Math.max(1f, Math.min(getWidth(), getHeight())
                / (float) DashboardWidgetLayout.GRID_DIVISIONS);
    }

    /** The nearest cell centre to a point, on one axis. */
    private float snapToGrid(float value, float extent) {
        float step = gridStep();
        int cell = Math.round(value / step - 0.5f);
        int last = Math.max(0, (int) Math.floor(extent / step) - 1);
        cell = Math.max(0, Math.min(last, cell));
        return (cell + 0.5f) * step;
    }

    /**
     * Puts every widget on the nearest grid cell.
     *
     * <p>Widgets that were never placed are given the place they occupy now
     * first, so switching snapping on tidies the arrangement that is on screen
     * rather than gathering everything into one corner.
     */
    void snapAllToGrid() {
        seedFreePositions();
        for (Line line : drawnLines) {
            if (getWidth() <= 0 || getHeight() <= 0) {
                continue;
            }
            float x = DashboardWidgetLayout.loadFreeX(getContext(), line.widget) * getWidth();
            float y = DashboardWidgetLayout.loadFreeY(getContext(), line.widget) * getHeight();
            DashboardWidgetLayout.saveFreePosition(getContext(), line.widget,
                    snapToGrid(x, getWidth()) / getWidth(),
                    snapToGrid(y, getHeight()) / getHeight());
        }
        invalidate();
    }

    /** The cell centres, drawn faintly so there is something to aim at. */
    private void drawGrid(Canvas canvas) {
        if (widgetSelectedListener == null
                || currentLayout() != DashboardSettings.Layout.FREE
                || !DashboardWidgetLayout.isGridSnapEnabled(getContext())) {
            return;
        }
        float step = gridStep();
        float radius = Math.max(1f, step * 0.045f);
        selectionPaint.setStyle(Paint.Style.FILL);
        selectionPaint.setColor(Color.argb(70, Color.red(currentPalette.text),
                Color.green(currentPalette.text), Color.blue(currentPalette.text)));
        for (float x = step / 2f; x < getWidth(); x += step) {
            for (float y = step / 2f; y < getHeight(); y += step) {
                canvas.drawCircle(x, y, radius, selectionPaint);
            }
        }
        selectionPaint.setStyle(Paint.Style.STROKE);
    }

    private void moveDragTo(float x, float y) {
        // Held to the same range the drawing is held to. Storing a point the
        // panel cannot show left a dead zone at each edge: the widget stopped,
        // the stored position carried on, and dragging back did nothing until
        // it caught up.
        float edge = Math.min(6f * getResources().getDisplayMetrics().density, getWidth() * 0.04f);
        RectF box = boundsOf(draggedWidget);
        float halfWidth = box == null ? 0f : box.width() / 2f;
        float halfHeight = halfHeightOf(draggedWidget, 0f);
        float placedX = clampBetween(x + dragOffsetX, edge + halfWidth, getWidth() - edge - halfWidth);
        float placedY = clampBetween(y + dragOffsetY, edge + halfHeight, getHeight() - edge - halfHeight);
        if (DashboardWidgetLayout.isGridSnapEnabled(getContext())) {
            placedX = snapToGrid(placedX, getWidth());
            placedY = snapToGrid(placedY, getHeight());
        }
        DashboardWidgetLayout.saveFreePosition(getContext(), draggedWidget,
                placedX / Math.max(1, getWidth()), placedY / Math.max(1, getHeight()));
        invalidate();
    }

    /**
     * How many of these lines the panel can actually hold at once.
     *
     * <p>It used to be five, whatever the panel, the text size or the sizes
     * the widgets had been given. The panel holds seven or eight at the normal
     * size, so a sixth widget was pushed into a rotation nobody asked for -
     * invisible in the preview, and belonging to no page the builder lists, so
     * there was no way to get at it. This measures the lines the same way
     * {@link #drawStacked} lays them out, so what fits is shown.
     */
    private int lineCapacity(List<Line> source) {
        float density = contentDensity();
        float scale = settings.textScalePercent / 100f;
        float shortSide = Math.min(getWidth(), getHeight());
        float normalSize = clamp(shortSide * 0.105f * scale, 12f * density, 36f * density);
        float clockSize = clamp(shortSide * 0.24f * scale, 28f * density, 76f * density);
        float gap = Math.min(8f * density, getHeight() * 0.025f);
        float available = getHeight() - 2f * Math.min(12f * density, getWidth() * 0.06f);
        float used = 0f;
        int fits = 0;
        for (Line line : source) {
            float height = (line.primary ? clockSize * 1.12f : normalSize * 1.35f) * line.scale;
            float before = fits == 0 ? 0f
                    : gap * (1f + DashboardWidgetLayout.gapMultiplier(getContext(), line.widget));
            if (fits > 0 && used + before + height > available) {
                break;
            }
            used += before + height;
            fits++;
        }
        return Math.max(1, fits);
    }

    /**
     * How many widgets are on the page but cannot be shown at once, or zero.
     *
     * <p>The panel falls back to cycling them, which is better than dropping
     * them but says nothing about why the panel keeps changing. The builder
     * asks so that it can.
     */
    int overflowCount() {
        if (currentLayout() == DashboardSettings.Layout.FREE) {
            return 0;
        }
        List<Line> lines = createLines();
        int maxPage = 1;
        for (Line line : lines) maxPage = Math.max(maxPage,
                DashboardWidgetLayout.loadPage(getContext(), line.widget));
        if (maxPage > 1) {
            List<Line> onPage = new ArrayList<>();
            int page = selectedPage > 0 ? Math.min(selectedPage, maxPage) : currentPage;
            for (Line line : lines) {
                if (DashboardWidgetLayout.loadPage(getContext(), line.widget) == page) {
                    onPage.add(line);
                }
            }
            lines = onPage;
        }
        return Math.max(0, lines.size() - lineCapacity(lines));
    }

    private List<Line> paginateLines(List<Line> source) {
        // Nothing to overflow in the free layout: the widgets are where they
        // were put, they do not stack, and splitting a hand-made arrangement
        // across a rotation only hides half of it.
        if (currentLayout() == DashboardSettings.Layout.FREE) {
            return source;
        }
        int capacity = lineCapacity(source);
        if (source.size() <= capacity) {
            return source;
        }
        // The clock used to be pinned to every page so the time was always
        // up. It read as the same widget appearing twice, and it spent a row
        // on each page saying what the previous page had already said, so
        // every page is now simply a share of the lines.
        List<Line> result = new ArrayList<>(capacity);
        int start = 0;
        int pageSize = capacity;
        int secondaryCount = source.size() - start;
        int pageCount = (secondaryCount + pageSize - 1) / pageSize;
        // Spread the rows over the pages rather than filling the first and
        // leaving the rest: six rows used to show five and then a single line
        // adrift in the middle of an empty panel. Six now shows three and three.
        int base = secondaryCount / pageCount;
        int remainder = secondaryCount % pageCount;
        int page = (int) ((System.currentTimeMillis() / 6_000L) % pageCount);
        requestPageFlip(6_000L);
        int from = start + page * base + Math.min(page, remainder);
        int to = Math.min(source.size(), from + base + (page < remainder ? 1 : 0));
        result.addAll(source.subList(from, to));
        return result;
    }

    private static String cardinalDirection(float degrees) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = Math.round(degrees / 45f) % directions.length;
        return directions[index];
    }

    private DashboardWidgetLayout.Variant variantOf(DashboardWidgetLayout.Widget widget) {
        return DashboardWidgetLayout.loadVariant(getContext(), widget);
    }

    private void markPageInteraction() {
        lastPageInteractionMillis = System.currentTimeMillis();
        removeCallbacks(returnToHomePage);
        postDelayed(returnToHomePage, 30_000L);
    }

    private boolean handleFullscreenTap(float x, float y) {
        if (y < getHeight() * 0.52f
                || !DashboardWidgetLayout.isWidgetEnabled(
                getContext(), DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA)
                || DashboardWidgetLayout.loadPage(
                getContext(), DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA) != currentPage) {
            return false;
        }
        if (x < getWidth() / 3f) return MediaNotificationListenerService.previous();
        if (x > getWidth() * 2f / 3f) return MediaNotificationListenerService.next();
        return MediaNotificationListenerService.playPause();
    }

    private void drawFullscreen(Canvas canvas, List<Line> lines, float density) {
        boolean weather = false;
        boolean media = false;
        for (Line line : lines) {
            weather |= line.widget == DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER;
            media |= line.widget == DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA;
        }
        if (weather) {
            drawFullscreenWeather(canvas, density);
        } else if (media) {
            drawFullscreenMedia(canvas, density);
        } else {
            drawStacked(canvas, lines, density);
        }
    }

    private boolean containsFullscreenWidget(List<Line> lines) {
        for (Line line : lines) {
            if (DashboardWidgetLayout.isFullscreenWidget(line.widget)) return true;
        }
        return false;
    }

    /** The rounded card both full-screen widgets stand on. */
    private RectF fullscreenPanel(Canvas canvas, float density) {
        float pad = Math.min(10f * density, getWidth() * 0.06f);
        RectF panel = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
        canvas.drawRoundRect(panel, 20f * density, 20f * density, panelPaint);
        return panel;
    }

    /** The text colour, softened, for the parts that are not the reading. */
    private int mutedColour() {
        int colour = currentPalette.text;
        return Color.argb(150, Color.red(colour), Color.green(colour), Color.blue(colour));
    }

    /**
     * Today beside the days ahead, or above them on a panel standing upright.
     *
     * <p>It used to stack every row down the panel whichever way round it was,
     * which on a landscape page spent the width on nothing and left the rows
     * almost touching. The reading that matters now takes a block of its own,
     * with the condition drawn beside the number rather than only in the
     * forecast, and a rule between the two so the eye knows which is which.
     */
    private void drawFullscreenWeather(Canvas canvas, float density) {
        RectF panel = fullscreenPanel(canvas, density);
        WeatherForecastState.Snapshot forecast = WeatherForecastState.get();
        int days = Math.min(4, forecast.dates.length);
        boolean wide = panel.width() >= panel.height() * 1.35f;
        float share = days == 0 ? 1f : wide ? 0.36f : 0.40f;
        RectF now = wide
                ? new RectF(panel.left, panel.top,
                        panel.left + panel.width() * share, panel.bottom)
                : new RectF(panel.left, panel.top,
                        panel.right, panel.top + panel.height() * share);
        drawWeatherNow(canvas, now, density);
        if (days == 0) {
            textPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }
        RectF ahead = wide
                ? new RectF(now.right, panel.top, panel.right, panel.bottom)
                : new RectF(panel.left, now.bottom, panel.right, panel.bottom);
        iconPaint.setColor(mutedColour());
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(Math.max(1f, density * 0.7f));
        if (wide) {
            canvas.drawLine(ahead.left, ahead.top + ahead.height() * 0.18f,
                    ahead.left, ahead.bottom - ahead.height() * 0.18f, iconPaint);
        } else {
            canvas.drawLine(ahead.left + ahead.width() * 0.12f, ahead.top,
                    ahead.right - ahead.width() * 0.12f, ahead.top, iconPaint);
        }
        // Whether any day has a chance of rain to report decides the height
        // every column is laid out in, not just the ones that have one: sized
        // each on its own, a column with the extra line shrank while the one
        // beside it did not, and the two stopped lining up.
        boolean rainAnyDay = false;
        for (int index = 0; index < days; index++) {
            rainAnyDay |= !rainChance(forecast, index).isEmpty();
        }
        for (int index = 0; index < days; index++) {
            RectF cell = wide
                    ? new RectF(ahead.left + ahead.width() * index / days, ahead.top,
                            ahead.left + ahead.width() * (index + 1) / days, ahead.bottom)
                    : new RectF(ahead.left, ahead.top + ahead.height() * index / days,
                            ahead.right, ahead.top + ahead.height() * (index + 1) / days);
            if (wide) {
                drawForecastColumn(canvas, cell, forecast, index, density, rainAnyDay);
            } else {
                drawForecastRow(canvas, cell, forecast, index, density);
            }
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    /** The condition, the temperature and the place, centred in what is left. */
    private void drawWeatherNow(Canvas canvas, RectF area, float density) {
        String temperature = snapshot.weatherTemperatureCelsius == null
                ? "—" : inDegrees(snapshot.weatherTemperatureCelsius) + "°";
        String place = snapshot.weatherPlace;
        float iconSize = clamp(Math.min(area.width(), area.height()) * 0.26f,
                11f * density, 30f * density);
        float temperatureSize = clamp(Math.min(area.width() * 0.44f, area.height() * 0.36f),
                15f * density, 50f * density);
        float placeSize = clamp(temperatureSize * 0.30f, 7f * density, 14f * density);
        float gap = iconSize * 0.22f;

        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(temperatureSize);
        textPaint.getTextBounds(temperature, 0, temperature.length(), inkBounds);
        float temperatureTop = inkBounds.top;
        float temperatureHeight = inkBounds.height();
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(placeSize);
        float placeTop = 0f;
        float placeHeight = 0f;
        if (!place.isEmpty()) {
            textPaint.getTextBounds(place, 0, place.length(), inkBounds);
            placeTop = inkBounds.top;
            placeHeight = inkBounds.height();
        }
        float total = iconSize + gap + temperatureHeight
                + (place.isEmpty() ? 0f : gap * 0.6f + placeHeight);

        float centreX = area.centerX();
        float y = area.centerY() - total / 2f;
        textPaint.setColor(currentPalette.text);
        drawIcon(canvas, weatherIcon(snapshot.weatherCode), new RectF(
                centreX - iconSize / 2f, y, centreX + iconSize / 2f, y + iconSize));
        y += iconSize + gap;

        // Placed by the ink rather than by the line box: the box stands clear
        // of the glyphs, so gaps measured from it read half again as wide.
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(temperatureSize);
        canvas.drawText(temperature, centreX, y - temperatureTop, textPaint);
        textPaint.setFakeBoldText(false);
        y += temperatureHeight;

        if (!place.isEmpty()) {
            y += gap * 0.6f;
            textPaint.setTextSize(placeSize);
            textPaint.setColor(mutedColour());
            CharSequence fitted = TextUtils.ellipsize(place, textPaint,
                    area.width() * 0.92f, TextUtils.TruncateAt.END);
            canvas.drawText(fitted.toString(), centreX, y - placeTop, textPaint);
            textPaint.setColor(currentPalette.text);
        }
    }

    /** One day of the forecast, stacked, for a panel lying on its side. */
    private void drawForecastColumn(Canvas canvas, RectF cell,
            WeatherForecastState.Snapshot forecast, int index, float density,
            boolean roomForRain) {
        float labelSize = clamp(cell.height() * 0.15f, 7f * density, 13f * density);
        float iconSize = clamp(Math.min(cell.width() * 0.52f, cell.height() * 0.26f),
                9f * density, 24f * density);
        float valueSize = clamp(cell.height() * 0.17f, 8f * density, 15f * density);
        float gap = iconSize * 0.2f;
        String rain = rainChance(forecast, index);

        textPaint.setTextSize(labelSize);
        float labelHeight = textPaint.descent() - textPaint.ascent();
        textPaint.setTextSize(valueSize);
        float valueHeight = textPaint.descent() - textPaint.ascent();
        float total = labelHeight + gap + iconSize + gap + valueHeight * 2f
                + (roomForRain ? labelHeight : 0f);
        // The four rows already all but filled the cell, so a fifth has to be
        // made room for rather than simply drawn - it fell off the bottom
        // edge otherwise. Font metrics go up and down with the size, so the
        // heights measured above can be squeezed by the same fraction.
        float room = cell.height() * 0.96f;
        if (total > room) {
            float squeeze = room / total;
            labelSize *= squeeze;
            iconSize *= squeeze;
            valueSize *= squeeze;
            gap *= squeeze;
            labelHeight *= squeeze;
            valueHeight *= squeeze;
            total = room;
        }

        float centreX = cell.centerX();
        float y = cell.centerY() - total / 2f;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(labelSize);
        textPaint.setColor(mutedColour());
        canvas.drawText(dayName(forecast.dates[index]), centreX, y - textPaint.ascent(), textPaint);
        y += labelHeight + gap;

        textPaint.setColor(currentPalette.text);
        drawIcon(canvas, weatherIcon(forecast.codes[index]), new RectF(
                centreX - iconSize / 2f, y, centreX + iconSize / 2f, y + iconSize));
        y += iconSize + gap;

        textPaint.setTextSize(valueSize);
        canvas.drawText(inDegrees(forecast.maximums[index]) + "°", centreX,
                y - textPaint.ascent(), textPaint);
        y += valueHeight;
        textPaint.setColor(mutedColour());
        canvas.drawText(inDegrees(forecast.minimums[index]) + "°", centreX,
                y - textPaint.ascent(), textPaint);
        if (!rain.isEmpty()) {
            y += valueHeight;
            textPaint.setTextSize(labelSize);
            canvas.drawText(rain, centreX, y - textPaint.ascent(), textPaint);
        }
        textPaint.setColor(currentPalette.text);
    }

    /**
     * The chance of rain, where it is worth saying.
     *
     * <p>Under a fifth it is noise on a panel this size, and the row it would
     * take is better left to the temperatures. A day drawn with a drizzle
     * icon came in at twenty-nine per cent, so the cut cannot sit much above
     * that or it would contradict the picture beside it. Not every place and
     * season has the figure at all.
     */
    private String rainChance(WeatherForecastState.Snapshot forecast, int index) {
        if (index >= forecast.rainChances.length) {
            return "";
        }
        int chance = forecast.rainChances[index];
        return chance < 20 ? "" : chance + "%";
    }

    /** One day of the forecast, in a line, for a panel standing upright. */
    private void drawForecastRow(Canvas canvas, RectF cell,
            WeatherForecastState.Snapshot forecast, int index, float density) {
        float size = clamp(cell.height() * 0.42f, 7f * density, 15f * density);
        float iconSize = clamp(Math.min(cell.width() * 0.2f, cell.height() * 0.7f),
                9f * density, 22f * density);
        float pad = cell.width() * 0.06f;
        float baseline = cell.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;

        textPaint.setTextSize(size);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(mutedColour());
        String day = dayName(forecast.dates[index]);
        canvas.drawText(day, cell.left + pad, baseline, textPaint);
        String rain = rainChance(forecast, index);
        if (!rain.isEmpty()) {
            float from = cell.left + pad + textPaint.measureText(day) + size * 0.45f;
            float until = cell.centerX() - iconSize * 0.6f;
            if (until - from >= textPaint.measureText(rain)) {
                canvas.drawText(rain, from, baseline, textPaint);
            }
        }

        textPaint.setColor(currentPalette.text);
        drawIcon(canvas, weatherIcon(forecast.codes[index]), new RectF(
                cell.centerX() - iconSize / 2f, cell.centerY() - iconSize / 2f,
                cell.centerX() + iconSize / 2f, cell.centerY() + iconSize / 2f));

        textPaint.setTextAlign(Paint.Align.RIGHT);
        String maximum = inDegrees(forecast.maximums[index]) + "°";
        canvas.drawText(maximum, cell.right - pad, baseline, textPaint);
        textPaint.setColor(mutedColour());
        canvas.drawText(inDegrees(forecast.minimums[index]) + "°",
                cell.right - pad - textPaint.measureText(maximum) - size * 0.35f,
                baseline, textPaint);
        textPaint.setColor(currentPalette.text);
    }

    /** The weekday a forecast entry falls on, or the raw date if it will not parse. */
    private String dayName(String isoDate) {
        try {
            return new SimpleDateFormat("EEE", Locale.getDefault()).format(
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate));
        } catch (Exception ignored) {
            return isoDate;
        }
    }

    /**
     * What is playing, and the three controls for it.
     *
     * <p>The title used to be set at a size the panel could not hold and then
     * cut off mid-word - "Nothing playing" itself did not fit - so it is
     * brought down to a size that fits before it is cut at all. The skip
     * controls were the characters for a double chevron, which the faces this
     * panel can offer draw thin or not at all; they are shapes now, like the
     * play mark beside them, and they go quiet when there is no session for
     * them to talk to.
     */
    /**
     * What is playing, whose it is, and the three controls for it.
     *
     * <p>Laid out as a card rather than as a column: the app's own icon on the
     * left, the track and the artist beside it, and the controls below a rule.
     * The title used to be set at a size the panel could not hold and then cut
     * off mid-word - "Nothing playing" itself did not fit - so it is brought
     * down to a size that fits before it is cut at all. The skip controls were
     * the characters for a double chevron, which the faces this panel can
     * offer draw thin or not at all; they are shapes now, like the play mark
     * beside them, and all three go quiet when there is no session to talk to.
     */
    private void drawFullscreenMedia(Canvas canvas, float density) {
        RectF panel = fullscreenPanel(canvas, density);
        boolean live = !snapshot.mediaTitle.isEmpty() || !snapshot.mediaArtist.isEmpty();
        float controlSize = clamp(Math.min(panel.width() * 0.14f, panel.height() * 0.3f),
                11f * density, 30f * density);
        float controlsCentre = panel.bottom - controlSize * 0.58f;
        float rule = controlsCentre - controlSize * 0.72f;

        // The rule above the controls doubles as the track's own progress: on
        // a panel this short there is no room for a bar of its own, and a line
        // that fills as the track plays says the same thing in the same place.
        MediaWidgetState.Snapshot media = MediaWidgetState.get();
        float ruleLeft = panel.left + panel.width() * 0.06f;
        float ruleRight = panel.right - panel.width() * 0.06f;
        iconPaint.setColor(mutedColour());
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(Math.max(1f, density * 0.7f));
        canvas.drawLine(ruleLeft, rule, ruleRight, rule, iconPaint);
        if (live && media.durationMillis > 0L) {
            float through = clamp(media.positionNow() / (float) media.durationMillis, 0f, 1f);
            iconPaint.setColor(currentPalette.text);
            iconPaint.setStrokeWidth(Math.max(1f, density * 1.4f));
            canvas.drawLine(ruleLeft, rule,
                    ruleLeft + (ruleRight - ruleLeft) * through, rule, iconPaint);
            if (media.playing) {
                // Nothing else on the panel moves this often, so the redraw is
                // asked for only while there is something to move.
                removeCallbacks(progressTick);
                postDelayed(progressTick, 1_000L);
            }
        }

        RectF above = new RectF(panel.left, panel.top, panel.right, rule);
        if (live) {
            drawNowPlaying(canvas, above, density);
        } else {
            String nothing = getResources().getString(R.string.dashboard_media_nothing_playing);
            float available = above.width() * 0.88f;
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(mutedColour());
            textPaint.setTextSize(fitTextSize(nothing, available,
                    clamp(above.height() * 0.4f, 10f * density, 22f * density), 8f * density));
            canvas.drawText(TextUtils.ellipsize(nothing, textPaint, available,
                            TextUtils.TruncateAt.END).toString(), above.centerX(),
                    above.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
            textPaint.setColor(currentPalette.text);
        }

        int colour = live ? currentPalette.text : mutedColour();
        drawSkip(canvas, panel.left + panel.width() / 6f, controlsCentre, controlSize,
                false, colour);
        drawTransportState(canvas, panel.centerX(), controlsCentre, controlSize,
                snapshot.mediaPlaying, colour);
        drawSkip(canvas, panel.right - panel.width() / 6f, controlsCentre, controlSize,
                true, colour);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    /** The icon on the left, the words beside it, a rule between the two. */
    private void drawNowPlaying(Canvas canvas, RectF area, float density) {
        // The track's own picture where the player offers one; failing that,
        // the icon of whoever is playing, which at least says where it is from.
        MediaWidgetState.Snapshot media = MediaWidgetState.get();
        drawIconAndLines(canvas, area, media.artwork, media.packageName,
                snapshot.mediaTitle.isEmpty() ? snapshot.mediaArtist : snapshot.mediaTitle,
                snapshot.mediaTitle.isEmpty() ? "" : snapshot.mediaArtist,
                density);
    }

    /**
     * A picture on the left, two lines beside it, a rule between the two.
     *
     * <p>Shared by the media card and the call card, which are the same
     * arrangement holding different news - and which drifted apart the moment
     * they were written twice.
     *
     * @param picture what to show, already a bitmap, or null to fall back on
     *     the icon of {@code packageName}. A picture is given corners; an app
     *     icon arrives with its own shape already cut.
     */
    private void drawIconAndLines(Canvas canvas, RectF area, @Nullable Bitmap picture,
            String packageName, String title, String subtitle, float density) {
        float iconSize = clamp(Math.min(area.width() * 0.22f, area.height() * 0.76f),
                13f * density, 38f * density);
        Bitmap icon = picture;
        boolean artwork = icon != null;
        if (icon == null) {
            icon = appIcon(packageName, Math.round(iconSize));
        }
        String artist = subtitle;
        float margin = area.width() * 0.05f;
        float left = area.left + margin;
        float textLeft = left;
        if (icon != null) {
            drawPicture(canvas, icon, new RectF(left, area.centerY() - iconSize / 2f,
                    left + iconSize, area.centerY() + iconSize / 2f), artwork);
            float divider = left + iconSize * 1.42f;
            iconPaint.setColor(mutedColour());
            iconPaint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(divider, area.top + area.height() * 0.18f,
                    divider, area.bottom - area.height() * 0.18f, iconPaint);
            textLeft = divider + iconSize * 0.42f;
        }
        float available = Math.max(1f, area.right - margin - textLeft);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setFakeBoldText(true);
        float titleSize = fitTextSize(title, available,
                clamp(Math.min(area.height() * 0.42f, area.width() * 0.13f),
                        11f * density, 30f * density), 8f * density);
        textPaint.setTextSize(titleSize);
        textPaint.getTextBounds(title, 0, title.length(), inkBounds);
        float titleTop = inkBounds.top;
        float titleHeight = inkBounds.height();
        textPaint.setFakeBoldText(false);

        // Given the same chance to fit as the title, and a lower floor: it is
        // the second line, so it may go smaller before it is cut.
        float artistSize = artist.isEmpty() ? 0f : fitTextSize(artist, available,
                clamp(titleSize * 0.66f, 7f * density, 20f * density), 6f * density);
        float artistTop = 0f;
        float artistHeight = 0f;
        if (!artist.isEmpty()) {
            textPaint.setTextSize(artistSize);
            textPaint.getTextBounds(artist, 0, artist.length(), inkBounds);
            artistTop = inkBounds.top;
            artistHeight = inkBounds.height();
        }
        float gap = titleSize * 0.42f;
        float y = area.centerY()
                - (titleHeight + (artist.isEmpty() ? 0f : gap + artistHeight)) / 2f;

        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(titleSize);
        canvas.drawText(TextUtils.ellipsize(title, textPaint, available,
                TextUtils.TruncateAt.END).toString(), textLeft, y - titleTop, textPaint);
        textPaint.setFakeBoldText(false);
        if (!artist.isEmpty()) {
            y += titleHeight + gap;
            textPaint.setTextSize(artistSize);
            textPaint.setColor(mutedColour());
            canvas.drawText(TextUtils.ellipsize(artist, textPaint, available,
                    TextUtils.TruncateAt.END).toString(), textLeft, y - artistTop, textPaint);
            textPaint.setColor(currentPalette.text);
        }
    }

    /**
     * The call to put on the panel, or null when there is none to show.
     *
     * <p>Null in the builder as well as when nothing is ringing: the preview
     * is where an arrangement is being edited, and a call arriving there
     * would take the editing surface away rather than tell anyone anything.
     */
    @Nullable
    private CallWidgetState.Snapshot ringingCall() {
        if (widgetSelectedListener != null) {
            return null;
        }
        CallWidgetState.Snapshot call = CallWidgetState.get();
        return call.ringing ? call : null;
    }

    /**
     * Whoever is ringing, across the whole panel.
     *
     * <p>It pre-empts the page that was showing and the mirrored image alike:
     * the one question a panel facing away from its owner is well placed to
     * answer is whether this call is worth turning the phone over for.
     */
    private void drawIncomingCall(Canvas canvas, CallWidgetState.Snapshot call, float density) {
        RectF panel = fullscreenPanel(canvas, density);
        String caller = call.caller.isEmpty()
                ? getResources().getString(R.string.dashboard_call_incoming) : call.caller;
        String label = call.caller.isEmpty() ? "" : call.label;
        drawIconAndLines(canvas, panel, null, call.packageName, caller, label, density);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * A square of picture in the box given.
     *
     * <p>An app icon arrives with its own shape already cut; a piece of album
     * art is a bare square, so it is given corners to match rather than left
     * as the one hard rectangle on a panel of rounded ones.
     */
    private void drawPicture(Canvas canvas, Bitmap picture, RectF box, boolean round) {
        if (!round) {
            canvas.drawBitmap(picture, null, box, null);
            return;
        }
        Matrix fit = new Matrix();
        fit.setRectToRect(new RectF(0f, 0f, picture.getWidth(), picture.getHeight()), box,
                Matrix.ScaleToFit.CENTER);
        BitmapShader shader = new BitmapShader(picture, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        shader.setLocalMatrix(fit);
        picturePaint.setShader(shader);
        float radius = box.width() * 0.17f;
        canvas.drawRoundRect(box, radius, radius, picturePaint);
        picturePaint.setShader(null);
    }

    /**
     * The playing app's icon, drawn once into a bitmap and kept until it
     * changes.
     *
     * <p>Loading it asks the package manager to open somebody else's
     * resources, which is far too much to do sixty times a second. A package
     * that has no icon to give is remembered as such, so the asking stops.
     */
    @Nullable
    private Bitmap appIcon(String packageName, int size) {
        if (packageName == null || packageName.isEmpty() || size <= 0) {
            return null;
        }
        if (packageName.equals(appIconPackage) && appIconSize == size) {
            return appIcon;
        }
        appIconPackage = packageName;
        appIconSize = size;
        appIcon = null;
        try {
            Drawable drawable = getContext().getPackageManager().getApplicationIcon(packageName);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(new Canvas(bitmap));
            appIcon = bitmap;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Nothing to show for it; the words stand on their own.
        }
        return appIcon;
    }

    /**
     * The largest size at or under {@code wanted} that fits, down to a floor.
     *
     * <p>Cutting a title off is a last resort, not the first thing to try:
     * most of them fit whole once the size gives a little.
     */
    private float fitTextSize(String value, float available, float wanted, float floor) {
        float size = wanted;
        textPaint.setTextSize(size);
        while (size > floor && textPaint.measureText(value) > available) {
            size = Math.max(floor, size - Math.max(0.5f, size * 0.06f));
            textPaint.setTextSize(size);
        }
        return size;
    }

    /**
     * Play or pause, drawn rather than typed.
     *
     * <p>The glyph was a fixed triangle whatever the session was doing, and a
     * pause pair is exactly the sort of character the small faces on offer
     * here turn into a box.
     */
    private void drawTransportState(Canvas canvas, float centreX, float centreY,
            float size, boolean playing, int colour) {
        float height = size * 0.62f;
        float top = centreY - height / 2f;
        iconPaint.setColor(colour);
        iconPaint.setStyle(Paint.Style.FILL);
        if (playing) {
            float bar = height * 0.28f;
            float gap = bar * 0.85f;
            canvas.drawRect(centreX - gap / 2f - bar, top, centreX - gap / 2f,
                    top + height, iconPaint);
            canvas.drawRect(centreX + gap / 2f, top, centreX + gap / 2f + bar,
                    top + height, iconPaint);
        } else {
            float half = height * 0.46f;
            Path triangle = new Path();
            triangle.moveTo(centreX - half, top);
            triangle.lineTo(centreX + half, top + height / 2f);
            triangle.lineTo(centreX - half, top + height);
            triangle.close();
            canvas.drawPath(triangle, iconPaint);
        }
        // The shared paint goes back to how the rest of the view expects it.
        iconPaint.setStyle(Paint.Style.STROKE);
    }

    /** Skip back or on: two marks pointing the way, to match the play mark. */
    private void drawSkip(Canvas canvas, float centreX, float centreY, float size,
            boolean forward, int colour) {
        float height = size * 0.5f;
        float half = height * 0.46f;
        iconPaint.setColor(colour);
        iconPaint.setStyle(Paint.Style.FILL);
        for (int mark = 0; mark < 2; mark++) {
            float shift = (mark == 0 ? -1f : 1f) * half * 1.15f;
            float tip = forward ? centreX + shift + half : centreX + shift - half;
            float back = forward ? centreX + shift - half : centreX + shift + half;
            Path triangle = new Path();
            triangle.moveTo(back, centreY - height / 2f);
            triangle.lineTo(tip, centreY);
            triangle.lineTo(back, centreY + height / 2f);
            triangle.close();
            canvas.drawPath(triangle, iconPaint);
        }
        iconPaint.setStyle(Paint.Style.STROKE);
    }

    private boolean imperialUnits() {
        return DashboardWidgetLayout.isImperialUnits(getContext());
    }

    /** A reading in whichever degrees the panel has been told to show. */
    private int inDegrees(int celsius) {
        return imperialUnits() ? Math.round(celsius * 9f / 5f + 32f) : celsius;
    }

    private float inDegrees(float celsius) {
        return imperialUnits() ? celsius * 9f / 5f + 32f : celsius;
    }

    /** What to call them, for the one reading that spells the unit out. */
    private String degreeName() {
        return imperialUnits() ? "°F" : "°C";
    }

    /**
     * The weather as a widget's variant asks for it.
     *
     * <p>Shared with the full-screen weather widget, which read no variant at
     * all: all three of its choices drew the same line, while the row
     * offering them said otherwise.
     */
    private String weatherVariant(DashboardWidgetLayout.Widget widget) {
        String weather = weatherLine();
        if (weather.isEmpty()) {
            return weather;
        }
        DashboardWidgetLayout.Variant variant = variantOf(widget);
        if (variant == DashboardWidgetLayout.Variant.ALTERNATE
                && snapshot.weatherTemperatureCelsius != null) {
            return inDegrees(snapshot.weatherTemperatureCelsius) + "\n" + degreeName();
        }
        if (variant == DashboardWidgetLayout.Variant.DETAILED
                && !snapshot.weatherPlace.isEmpty()) {
            return weather + "\n" + snapshot.weatherPlace;
        }
        return weather;
    }

    /**
     * What is playing, as a widget's variant asks for it.
     *
     * <p>Shared with the full-screen media widget for the same reason. That
     * one also showed the title and nothing else, so a station that names
     * only its artist left it blank.
     */
    private String mediaVariant(DashboardWidgetLayout.Widget widget) {
        String title = snapshot.mediaTitle;
        String artist = snapshot.mediaArtist;
        DashboardWidgetLayout.Variant variant = variantOf(widget);
        if (variant == DashboardWidgetLayout.Variant.ALTERNATE && !artist.isEmpty()) {
            return artist;
        }
        if (variant == DashboardWidgetLayout.Variant.DETAILED
                && !title.isEmpty() && !artist.isEmpty()) {
            return title + "\n" + artist;
        }
        return title.isEmpty() ? artist : title;
    }

    /** Visual alternatives for text whose content is supplied by the user or system. */
    private String textVariant(DashboardWidgetLayout.Widget widget, String value) {
        DashboardWidgetLayout.Variant variant = variantOf(widget);
        if (variant == DashboardWidgetLayout.Variant.ALTERNATE) {
            return value.toUpperCase(Locale.getDefault());
        }
        if (variant != DashboardWidgetLayout.Variant.DETAILED || value.indexOf(' ') < 0) {
            return value;
        }
        int middle = value.length() / 2;
        int before = value.lastIndexOf(' ', middle);
        int after = value.indexOf(' ', middle);
        int split = before < 0 ? after : after < 0 || middle - before <= after - middle
                ? before : after;
        return split > 0 ? value.substring(0, split) + "\n" + value.substring(split + 1) : value;
    }

    private static String percentVariant(int percent, DashboardWidgetLayout.Variant variant,
            String detail) {
        if (variant == DashboardWidgetLayout.Variant.ALTERNATE) return String.valueOf(percent);
        if (variant == DashboardWidgetLayout.Variant.DETAILED) return percent + "%\n" + detail;
        return percent + "%";
    }

    private static String formatElapsed(long elapsedMillis) {
        long totalSeconds = Math.max(0L, elapsedMillis / 1_000L);
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static String formatElapsedDetailed(long elapsedMillis) {
        long totalSeconds = Math.max(0L, elapsedMillis / 1_000L);
        return String.format(Locale.ROOT, "%d:%02d:%02d", totalSeconds / 3_600L,
                (totalSeconds % 3_600L) / 60L, totalSeconds % 60L);
    }

    private String weatherLine() {
        if (snapshot.weatherTemperatureCelsius != null && snapshot.weatherCode != null) {
            return getResources().getString(
                    R.string.dashboard_weather_short,
                    inDegrees(snapshot.weatherTemperatureCelsius)
            );
        }
        if (snapshot.weatherLoading) {
            return "…";
        }
        return "—";
    }

    private static Icon weatherIcon(@Nullable Integer codeValue) {
        if (codeValue == null) {
            return Icon.WEATHER_CLOUD;
        }
        int code = codeValue;
        if (code == 0) {
            return Icon.WEATHER_CLEAR;
        }
        if (code <= 3) {
            return Icon.WEATHER_CLOUD;
        }
        if (code == 45 || code == 48) {
            return Icon.WEATHER_FOG;
        }
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return Icon.WEATHER_RAIN;
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return Icon.WEATHER_SNOW;
        }
        if (code >= 95) {
            return Icon.WEATHER_STORM;
        }
        return Icon.WEATHER_CLOUD;
    }

    /**
     * Draws each widget where it was put.
     *
     * <p>The other layouts flow their widgets and work out the sizes, so there
     * is nothing in them a finger could move. Here every widget carries its own
     * place as a fraction of the panel, which is what makes dragging in the
     * preview mean anything on a panel of a different size and density.
     *
     * <p>A widget that has never been placed falls back to an even column, so
     * switching to this layout shows the same widgets in the same order rather
     * than a heap in the middle.
     */
    private void drawFree(Canvas canvas, List<Line> lines, float density) {
        if (lines.isEmpty()) {
            return;
        }
        float scale = settings.textScalePercent / 100f;
        float shortSide = Math.min(getWidth(), getHeight());
        float normalSize = clamp(shortSide * 0.105f * scale, 12f * density, 36f * density);
        float clockSize = clamp(shortSide * 0.24f * scale, 28f * density, 76f * density);
        float padding = Math.min(6f * density, getWidth() * 0.04f);
        int opacity = Math.round(settings.backgroundOpacityPercent * 2.55f);
        if (opacity > 0) {
            canvas.drawRoundRect(
                    new RectF(padding, padding, getWidth() - padding, getHeight() - padding),
                    20f * density, 20f * density, panelPaint);
        }
        float[][] fallback = freePlacement(lines, clockSize, normalSize, padding);
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            float size = (line.primary ? clockSize : normalSize) * line.scale;
            textPaint.setTextSize(size);
            textPaint.setFakeBoldText(line.primary);
            float x;
            float y;
            if (DashboardWidgetLayout.hasFreePosition(getContext(), line.widget)) {
                x = DashboardWidgetLayout.loadFreeX(getContext(), line.widget) * getWidth();
                y = DashboardWidgetLayout.loadFreeY(getContext(), line.widget) * getHeight();
            } else {
                x = fallback[index][0];
                y = fallback[index][1];
            }
            // Always centred on the stored point, whatever alignment the
            // widget carries from a flowed layout. Everything else here
            // already treats that point as the widget's middle - the drag
            // stores it, the drag holds it inside the panel by half a width,
            // the fallback placement hands back the middle of a free box -
            // so honouring an edge alignment drew the widget half its width
            // away from where it had been dropped. The builder leaves the
            // alignment row off a free page for the same reason.
            // The whole panel, not the room left between the anchor and the
            // nearer edge. Measuring from the anchor made the widget shrink as
            // it was dragged towards an edge - down to a fifth of its size,
            // where there was nothing left to put a finger on - and made
            // growing it near an edge do nothing at all.
            float available = Math.max(1f, getWidth() - padding * 2f);
            // The stored point is the middle of the widget; text is drawn from
            // its baseline, so the two have to be reconciled here.
            float half = halfHeightOf(line.widget,
                    (textPaint.descent() - textPaint.ascent()) / 2f);
            float clampedY = Math.max(padding + half,
                    Math.min(getHeight() - padding - half, y));
            float baseline = clampedY - (textPaint.ascent() + textPaint.descent()) / 2f;
            drawLine(canvas, line, x, baseline, available, Alignment.CENTER, true);
        }
    }

    /**
     * Where the widgets that have never been dragged should go.
     *
     * <p>They used to be spread down the column by their place in the list,
     * which ignores the ones already dragged somewhere. Switching a widget on
     * therefore dropped it onto an arrangement someone had made by hand, and
     * it stayed there until they moved it.
     *
     * <p>With nothing placed by hand the even spread is still what this
     * returns - it reads better than a corner. Once anything has a place of
     * its own, the rest take the first free box, searched across the panel
     * rather than down it: this panel is 294 by 126, so on a page whose rows
     * are full the room that is left is beside them, not below.
     */
    private float[][] freePlacement(List<Line> lines, float clockSize, float normalSize,
            float padding) {
        int count = lines.size();
        float[][] result = new float[count][2];
        boolean anyPlaced = false;
        boolean anyLoose = false;
        for (int index = 0; index < count; index++) {
            boolean placed = DashboardWidgetLayout.hasFreePosition(
                    getContext(), lines.get(index).widget);
            anyPlaced |= placed;
            anyLoose |= !placed;
            result[index][0] = getWidth() / 2f;
            result[index][1] = getHeight() * (index + 1f) / (count + 1f);
        }
        // The usual case, and the one a drag spends every frame in: everything
        // has a place, so nothing needs measuring to find it one.
        if (!anyPlaced || !anyLoose) {
            return result;
        }
        float[] widths = new float[count];
        float[] heights = new float[count];
        for (int index = 0; index < count; index++) {
            widths[index] = measuredWidth(lines.get(index), clockSize, normalSize);
            heights[index] = textPaint.descent() - textPaint.ascent();
        }
        List<RectF> taken = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            if (DashboardWidgetLayout.hasFreePosition(getContext(), lines.get(index).widget)) {
                taken.add(boxAt(
                        DashboardWidgetLayout.loadFreeX(getContext(), lines.get(index).widget)
                                * getWidth(),
                        DashboardWidgetLayout.loadFreeY(getContext(), lines.get(index).widget)
                                * getHeight(),
                        widths[index], heights[index]));
            }
        }
        for (int index = 0; index < count; index++) {
            if (DashboardWidgetLayout.hasFreePosition(getContext(), lines.get(index).widget)) {
                continue;
            }
            RectF spot = firstFreeBox(taken, widths[index], heights[index], padding);
            if (spot != null) {
                result[index][0] = spot.centerX();
                result[index][1] = spot.centerY();
            }
            // Nothing free leaves the even-spread value, and the overflow note
            // already says the panel has run out of room.
            taken.add(boxAt(result[index][0], result[index][1], widths[index], heights[index]));
        }
        return result;
    }

    /** What drawLine will take up, near enough to keep two widgets apart. */
    private float measuredWidth(Line line, float clockSize, float normalSize) {
        textPaint.setTextSize((line.primary ? clockSize : normalSize) * line.scale);
        textPaint.setFakeBoldText(line.primary);
        if (line.text.indexOf('\n') >= 0) {
            textPaint.setTextSize(textPaint.getTextSize() * 0.58f);
        }
        float icon = line.icon == Icon.NONE ? 0f : textPaint.getTextSize() * 1.10f;
        float text = 0f;
        for (String part : line.text.split("\\n", -1)) {
            text = Math.max(text, textPaint.measureText(part));
        }
        return icon + text;
    }

    private static RectF boxAt(float centreX, float centreY, float width, float height) {
        return new RectF(centreX - width / 2f, centreY - height / 2f,
                centreX + width / 2f, centreY + height / 2f);
    }

    /** Scans the panel row by row for somewhere this box fits untouched. */
    @Nullable
    private RectF firstFreeBox(List<RectF> taken, float width, float height, float padding) {
        float stepY = Math.max(1f, height * 0.34f);
        float stepX = Math.max(1f, width * 0.34f);
        for (float centreY = padding + height / 2f;
                centreY <= getHeight() - padding - height / 2f; centreY += stepY) {
            for (float centreX = padding + width / 2f;
                    centreX <= getWidth() - padding - width / 2f; centreX += stepX) {
                RectF candidate = boxAt(centreX, centreY, width, height);
                boolean clear = true;
                for (RectF other : taken) {
                    if (RectF.intersects(candidate, other)) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private void drawStacked(Canvas canvas, List<Line> lines, float density) {
        if (lines.isEmpty()) {
            return;
        }
        if (getWidth() > getHeight() * 1.4f) {
            drawLandscapeStacked(canvas, lines, density);
            return;
        }
        float scale = settings.textScalePercent / 100f;
        float shortSide = Math.min(getWidth(), getHeight());
        float normalSize = clamp(shortSide * 0.105f * scale, 12f * density, 36f * density);
        float clockSize = clamp(shortSide * 0.24f * scale, 28f * density, 76f * density);
        float gap = Math.min(8f * density, getHeight() * 0.025f);
        float total = 0f;
        for (Line line : lines) {
            total += (line.primary ? clockSize * 1.12f : normalSize * 1.35f) * line.scale;
        }
        total += Math.max(0, lines.size() - 1) * gap;
        // Extra space asked for above individual widgets, so a dense column can
        // be read as blocks. The first line never gets one: it would only push
        // the whole arrangement off centre.
        float[] extraGaps = new float[lines.size()];
        for (int index = 1; index < lines.size(); index++) {
            extraGaps[index] = gap
                    * DashboardWidgetLayout.gapMultiplier(getContext(), lines.get(index).widget);
            total += extraGaps[index];
        }
        float y = (getHeight() - total) / 2f;
        float panelPadding = Math.min(12f * density, getWidth() * 0.06f);
        float contentPadding = Math.min(12f * density, getWidth() * 0.04f);
        RectF panel = new RectF(
                panelPadding,
                Math.max(panelPadding, y - panelPadding),
                getWidth() - panelPadding,
                Math.min(getHeight() - panelPadding, y + total + panelPadding)
        );
        canvas.drawRoundRect(panel, 20f * density, 20f * density, panelPaint);
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            float size = (line.primary ? clockSize : normalSize) * line.scale;
            textPaint.setTextSize(size);
            textPaint.setFakeBoldText(line.primary);
            y += extraGaps[index] + size;
            drawLine(
                    canvas,
                    line,
                    anchorFor(line, panel.left + contentPadding, panel.right - contentPadding),
                    y,
                    panel.width() - contentPadding * 2f,
                    alignmentFor(line)
            );
            y += (line.primary ? size * 0.12f : size * 0.35f) + gap;
        }
    }

    private void drawLandscapeStacked(Canvas canvas, List<Line> lines, float density) {
        float scale = settings.textScalePercent / 100f;
        float padding = 8f * density;
        RectF panel = new RectF(padding, padding, getWidth() - padding, getHeight() - padding);
        canvas.drawRoundRect(panel, getHeight() / 2f, getHeight() / 2f, panelPaint);

        int firstSecondary = lines.get(0).primary ? 1 : 0;
        if (firstSecondary == 1) {
            textPaint.setTextSize(clamp(getHeight() * 0.34f * scale, 20f * density, 52f * density)
                    * lines.get(0).scale);
            textPaint.setFakeBoldText(true);
            float baseline = getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
            drawLine(
                    canvas,
                    lines.get(0),
                    getWidth() * 0.22f,
                    baseline,
                    getWidth() * 0.38f,
                    Alignment.CENTER
            );
        }

        int count = lines.size() - firstSecondary;
        if (count <= 0) {
            return;
        }
        float startX = firstSecondary == 1 ? getWidth() * 0.43f : padding * 2f;
        float availableWidth = getWidth() - startX - padding * 2f;
        int columns = Math.min(2, count);
        int rows = (count + columns - 1) / columns;
        float cellWidth = availableWidth / columns;
        float cellHeight = (getHeight() - padding * 2f) / rows;
        textPaint.setFakeBoldText(false);
        for (int index = 0; index < count; index++) {
            Line line = lines.get(index + firstSecondary);
            textPaint.setTextSize(clamp(getHeight() * 0.14f * scale, 9f * density, 23f * density)
                    * line.scale);
            int column = index % columns;
            int row = index / columns;
            float x = startX + cellWidth * (column + 0.5f);
            float centerY = padding + cellHeight * (row + 0.5f);
            float baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f;
            drawLine(
                    canvas,
                    line,
                    x,
                    baseline,
                    cellWidth - padding,
                    Alignment.CENTER
            );
        }
    }

    private void drawCorners(Canvas canvas, List<Line> lines, float density) {
        if (lines.isEmpty()) {
            return;
        }
        if (getWidth() > getHeight() * 1.4f) {
            drawLandscapeCorners(canvas, lines, density);
            return;
        }
        float scale = settings.textScalePercent / 100f;
        float shortSide = Math.min(getWidth(), getHeight());
        float size = clamp(shortSide * 0.095f * scale, 11f * density, 30f * density);
        float primarySize = clamp(shortSide * 0.19f * scale, 24f * density, 60f * density);
        float padding = Math.min(12f * density, shortSide * 0.06f);
        canvas.drawRoundRect(
                new RectF(padding, padding, getWidth() - padding, getHeight() - padding),
                20f * density,
                20f * density,
                panelPaint
        );
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            boolean bottom = index >= 2;
            textPaint.setTextSize((line.primary ? primarySize : size) * line.scale);
            textPaint.setFakeBoldText(line.primary);
            float x = anchorFor(line, padding * 2f, getWidth() - padding * 2f);
            float y = bottom
                    ? getHeight() - padding * 2f - (lines.size() - 1 - index) / 2f * size * 1.4f
                    : padding * 2f + textPaint.getTextSize();
            drawLine(
                    canvas,
                    line,
                    x,
                    y,
                    getWidth() * 0.46f,
                    alignmentFor(line)
            );
        }
    }

    private void drawLandscapeCorners(Canvas canvas, List<Line> lines, float density) {
        float scale = settings.textScalePercent / 100f;
        float padding = Math.min(10f * density, getHeight() * 0.06f);
        RectF panel = new RectF(padding, padding, getWidth() - padding, getHeight() - padding);
        canvas.drawRoundRect(panel, getHeight() / 2f, getHeight() / 2f, panelPaint);

        int secondaryStart = lines.get(0).primary ? 1 : 0;
        if (secondaryStart == 1) {
            Line primary = lines.get(0);
            textPaint.setTextSize(clamp(getHeight() * 0.31f * scale, 16f * density, 48f * density)
                    * primary.scale);
            textPaint.setFakeBoldText(true);
            float baseline = getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
            drawLine(canvas, primary, getWidth() * 0.25f, baseline,
                    getWidth() * 0.42f - padding, Alignment.CENTER);
        }

        int count = lines.size() - secondaryStart;
        if (count <= 0) return;
        float left = secondaryStart == 1 ? getWidth() * 0.48f : panel.left + padding;
        float availableWidth = panel.right - padding - left;
        float cellHeight = panel.height() / count;
        for (int index = 0; index < count; index++) {
            Line line = lines.get(index + secondaryStart);
            textPaint.setTextSize(clamp(getHeight() * 0.13f * scale, 7f * density, 20f * density)
                    * line.scale);
            textPaint.setFakeBoldText(false);
            float centerY = panel.top + cellHeight * (index + 0.5f);
            float baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f;
            drawLine(canvas, line, left + availableWidth / 2f, baseline,
                    availableWidth, Alignment.CENTER);
        }
    }

    private void drawCompact(Canvas canvas, List<Line> lines, float density) {
        if (lines.isEmpty()) {
            return;
        }
        float scale = settings.textScalePercent / 100f;
        float shortSide = Math.min(getWidth(), getHeight());
        float primarySize = clamp(shortSide * 0.19f * scale, 22f * density, 58f * density);
        float normalSize = clamp(shortSide * 0.085f * scale, 10f * density, 28f * density);
        float padding = 10f * density;
        canvas.drawRoundRect(
                new RectF(padding, padding, getWidth() - padding, getHeight() - padding),
                getHeight() / 2f,
                getHeight() / 2f,
                panelPaint
        );
        boolean landscape = getWidth() > getHeight() * 1.4f;
        if (landscape) {
            float cellWidth = (getWidth() - padding * 4f) / lines.size();
            for (int index = 0; index < lines.size(); index++) {
                Line line = lines.get(index);
                textPaint.setTextSize((line.primary ? primarySize : normalSize) * line.scale);
                textPaint.setFakeBoldText(line.primary);
                float x = padding * 2f + cellWidth * (index + 0.5f);
                float baseline = getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
                drawLine(
                        canvas,
                        line,
                        anchorFor(line, x - cellWidth / 2f, x + cellWidth / 2f),
                        baseline,
                        cellWidth - padding,
                        alignmentFor(line)
                );
            }
        } else {
            float lineHeight = Math.min(
                    primarySize * 1.15f,
                    (getHeight() - padding * 4f) / lines.size()
            );
            float y = (getHeight() - lineHeight * lines.size()) / 2f;
            for (Line line : lines) {
                textPaint.setTextSize((line.primary ? primarySize : normalSize) * line.scale);
                textPaint.setFakeBoldText(line.primary);
                y += lineHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
                drawLine(
                        canvas,
                        line,
                        anchorFor(line, padding * 2f, getWidth() - padding * 2f),
                        y,
                        getWidth() - padding * 4f,
                        alignmentFor(line)
                );
                y += lineHeight / 2f + (textPaint.ascent() + textPaint.descent()) / 2f;
            }
        }
    }

    private void drawLine(
            Canvas canvas,
            Line line,
            float anchorX,
            float baseline,
            float width,
            Alignment alignment
    ) {
        drawLine(canvas, line, anchorX, baseline, width, alignment, false);
    }

    /**
     * @param keepInsidePanel slide the line back inside the panel rather than
     *     letting it hang off the edge. The flowed layouts place their own
     *     lines and never need it; the free one lets a finger put a widget
     *     wherever it likes, including half off the side.
     */
    private void drawLine(
            Canvas canvas,
            Line line,
            float anchorX,
            float baseline,
            float width,
            Alignment alignment,
            boolean keepInsidePanel
    ) {
        int canvasState = canvas.save();
        if (line.rotation != 0) {
            canvas.rotate(line.rotation, anchorX, baseline);
        }
        if (line.style == DashboardWidgetLayout.Style.ACCENT) {
            textPaint.setColor(MaterialColors.getColor(this,
                    androidx.appcompat.R.attr.colorPrimary, currentPalette.text));
        } else if (line.style == DashboardWidgetLayout.Style.MUTED) {
            textPaint.setColor(Color.argb(170, Color.red(currentPalette.text),
                    Color.green(currentPalette.text), Color.blue(currentPalette.text)));
        } else {
            textPaint.setColor(currentPalette.text);
        }
        // The widget's own face where it named one, and back to the panel's
        // for the next line: the paint is shared by every line drawn.
        textPaint.setTypeface(line.typeface != null ? line.typeface : panelTypeface);
        boolean multiline = line.text.indexOf('\n') >= 0;
        if (multiline) textPaint.setTextSize(textPaint.getTextSize() * 0.58f);
        fitTextToWidth(line, width);
        float iconSize = line.icon == Icon.NONE ? 0f : textPaint.getTextSize() * 0.82f;
        float gap = line.icon == Icon.NONE ? 0f : textPaint.getTextSize() * 0.28f;
        float textSpace = Math.max(1f, width - iconSize - gap);
        String[] parts = line.text.split("\\n", -1);
        CharSequence[] fittedParts = new CharSequence[parts.length];
        float textWidth = 0f;
        for (int index = 0; index < parts.length; index++) {
            fittedParts[index] = TextUtils.ellipsize(parts[index], textPaint, textSpace,
                    TextUtils.TruncateAt.END);
            textWidth = Math.max(textWidth, textPaint.measureText(
                    fittedParts[index], 0, fittedParts[index].length()));
        }
        float totalWidth = iconSize + gap + textWidth;
        float startX;
        if (alignment == Alignment.LEFT) {
            startX = anchorX;
        } else if (alignment == Alignment.RIGHT) {
            startX = anchorX - totalWidth;
        } else {
            startX = anchorX - totalWidth / 2f;
        }
        if (keepInsidePanel) {
            float edge = Math.min(6f * getResources().getDisplayMetrics().density,
                    getWidth() * 0.04f);
            float highest = Math.max(edge, getWidth() - edge - totalWidth);
            startX = Math.max(edge, Math.min(startX, highest));
        }
        if (line.icon != Icon.NONE) {
            float centerY = baseline + (textPaint.ascent() + textPaint.descent()) / 2f;
            drawIcon(canvas, line.icon, new RectF(
                    startX,
                    centerY - iconSize / 2f,
                    startX + iconSize,
                    centerY + iconSize / 2f
            ));
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
        float lineHeight = textPaint.descent() - textPaint.ascent();
        float firstBaseline = baseline - (parts.length - 1) * lineHeight * 0.52f;
        for (int index = 0; index < fittedParts.length; index++) {
            float partWidth = textPaint.measureText(fittedParts[index], 0,
                    fittedParts[index].length());
            float textX = startX + iconSize + gap;
            if (alignment == Alignment.CENTER) textX += (textWidth - partWidth) / 2f;
            else if (alignment == Alignment.RIGHT) textX += textWidth - partWidth;
            canvas.drawText(fittedParts[index].toString(), textX,
                    firstBaseline + index * lineHeight * 1.04f, textPaint);
        }
        // A font's line box stands well clear of the glyphs inside it - the
        // clock's is half again the height of its digits - and this rectangle
        // is three things at once: what a finger has to hit, what the
        // selection draws around, and what holds a dragged widget inside the
        // panel. Measured off the ascent it made the clock answer to touches
        // well above and below itself, drew a box around empty space, and
        // stopped short of the edges it looked like it could still reach. So
        // it follows the ink instead.
        float inkTop = Float.MAX_VALUE;
        float inkBottom = -Float.MAX_VALUE;
        boolean anyInk = false;
        for (int index = 0; index < fittedParts.length; index++) {
            int length = fittedParts[index].length();
            if (length == 0) {
                continue;
            }
            textPaint.getTextBounds(fittedParts[index], 0, length, inkBounds);
            if (inkBounds.isEmpty()) {
                continue;
            }
            anyInk = true;
            float partBaseline = firstBaseline + index * lineHeight * 1.04f;
            inkTop = Math.min(inkTop, partBaseline + inkBounds.top);
            inkBottom = Math.max(inkBottom, partBaseline + inkBounds.bottom);
        }
        if (line.icon != Icon.NONE) {
            float iconCentreY = baseline + (textPaint.ascent() + textPaint.descent()) / 2f;
            inkTop = Math.min(inkTop, iconCentreY - iconSize / 2f);
            inkBottom = Math.max(inkBottom, iconCentreY + iconSize / 2f);
            anyInk = true;
        }
        if (!anyInk) {
            // Nothing was drawn: an empty line, or one ellipsised to nothing.
            inkTop = firstBaseline + textPaint.ascent();
            inkBottom = firstBaseline + (parts.length - 1) * lineHeight * 1.04f
                    + textPaint.descent();
        }
        RectF localBounds = new RectF(startX, inkTop, startX + totalWidth, inkBottom);
        if (line.rotation != 0) {
            Matrix rotation = new Matrix();
            rotation.setRotate(line.rotation, anchorX, baseline);
            rotation.mapRect(localBounds);
        }
        localBounds.offset(shiftX, shiftY);
        line.bounds.set(localBounds);
        drawnLines.add(line);
        canvas.restoreToCount(canvasState);
    }

    private void fitTextToWidth(Line line, float width) {
        float originalSize = textPaint.getTextSize();
        float iconSpace = line.icon == Icon.NONE ? 0f : originalSize * 1.10f;
        float desired = 0f;
        for (String part : line.text.split("\\n", -1)) {
            desired = Math.max(desired, textPaint.measureText(part));
        }
        desired += iconSpace;
        if (desired <= width || desired <= 0f) return;
        // The rear panel is only 126 px wide on the target device. A hard 40% floor
        // still forces large user-selected text to ellipsize instead of fitting it.
        float minimumFactor = 0.20f;
        // Leave a small safety margin: glyph hinting can make the final bold text
        // fractionally wider than measureText(), which otherwise triggers an ellipsis.
        float factor = Math.max(minimumFactor, width / desired * 0.94f);
        textPaint.setTextSize(originalSize * factor);
    }

    private Alignment alignmentFor(Line line) {
        return line.position == DashboardWidgetLayout.Position.LEFT ? Alignment.LEFT
                : line.position == DashboardWidgetLayout.Position.RIGHT ? Alignment.RIGHT
                : Alignment.CENTER;
    }

    private float anchorFor(Line line, float left, float right) {
        return line.position == DashboardWidgetLayout.Position.LEFT ? left
                : line.position == DashboardWidgetLayout.Position.RIGHT ? right
                : (left + right) / 2f;
    }

    private void drawIcon(Canvas canvas, Icon icon, RectF bounds) {
        iconPaint.setColor(textPaint.getColor());
        iconPaint.setStrokeWidth(Math.max(1f, bounds.width() * 0.09f));
        iconPaint.setStyle(Paint.Style.STROKE);
        float inset = iconPaint.getStrokeWidth();
        RectF body = new RectF(bounds);
        body.inset(inset, inset);
        switch (icon) {
            case BATTERY:
            case BATTERY_CHARGING:
                drawBatteryIcon(canvas, body, icon == Icon.BATTERY_CHARGING);
                break;
            case TEMPERATURE:
                float cx = body.centerX();
                float bulbRadius = body.width() * 0.22f;
                canvas.drawCircle(cx, body.bottom - bulbRadius, bulbRadius, iconPaint);
                canvas.drawLine(cx, body.top, cx, body.bottom - bulbRadius * 1.8f, iconPaint);
                canvas.drawRoundRect(
                        cx - bulbRadius * 0.45f,
                        body.top,
                        cx + bulbRadius * 0.45f,
                        body.bottom - bulbRadius,
                        bulbRadius,
                        bulbRadius,
                        iconPaint
                );
                break;
            case WEATHER_CLEAR:
                drawSun(canvas, body);
                break;
            case WEATHER_RAIN:
                drawCloud(canvas, body);
                canvas.drawLine(body.left + body.width() * 0.28f, body.top + body.height() * 0.78f,
                        body.left + body.width() * 0.20f, body.bottom, iconPaint);
                canvas.drawLine(body.left + body.width() * 0.62f, body.top + body.height() * 0.78f,
                        body.left + body.width() * 0.54f, body.bottom, iconPaint);
                break;
            case WEATHER_SNOW:
                drawCloud(canvas, body);
                canvas.drawCircle(body.left + body.width() * 0.30f, body.top + body.height() * 0.90f,
                        iconPaint.getStrokeWidth(), iconPaint);
                canvas.drawCircle(body.left + body.width() * 0.65f, body.top + body.height() * 0.90f,
                        iconPaint.getStrokeWidth(), iconPaint);
                break;
            case WEATHER_FOG:
                canvas.drawLine(body.left, body.centerY() - body.height() * 0.16f,
                        body.right, body.centerY() - body.height() * 0.16f, iconPaint);
                canvas.drawLine(body.left + body.width() * 0.12f, body.centerY() + body.height() * 0.16f,
                        body.right - body.width() * 0.12f, body.centerY() + body.height() * 0.16f,
                        iconPaint);
                break;
            case WEATHER_STORM:
                drawCloud(canvas, body);
                Path bolt = new Path();
                bolt.moveTo(body.centerX(), body.centerY());
                bolt.lineTo(body.centerX() - body.width() * 0.12f,
                        body.top + body.height() * 0.76f);
                bolt.lineTo(body.centerX() + body.width() * 0.02f,
                        body.top + body.height() * 0.76f);
                bolt.lineTo(body.centerX() - body.width() * 0.08f, body.bottom);
                canvas.drawPath(bolt, iconPaint);
                break;
            case WEATHER_CLOUD:
                drawCloud(canvas, body);
                break;
            case ALARM:
                drawAlarm(canvas, body);
                break;
            case MEDIA:
                drawMedia(canvas, body);
                break;
            case COMPASS:
                drawCompass(canvas, body);
                break;
            case SPEED:
                drawSpeed(canvas, body);
                break;
            case ALTITUDE:
                drawAltitude(canvas, body);
                break;
            case TIMER:
                drawTimer(canvas, body);
                break;
            case PROFILE:
                drawProfile(canvas, body);
                break;
            case TEXT:
                drawTextIcon(canvas, body);
                break;
            case NETWORK:
                canvas.drawArc(body.left, body.top + body.height() * .15f, body.right,
                        body.bottom + body.height() * .45f, 210, 120, false, iconPaint);
                canvas.drawArc(body.left + body.width() * .22f, body.top + body.height() * .42f,
                        body.right - body.width() * .22f, body.bottom + body.height() * .22f,
                        210, 120, false, iconPaint);
                canvas.drawCircle(body.centerX(), body.bottom - body.height() * .08f,
                        iconPaint.getStrokeWidth() * 1.25f, iconPaint);
                break;
            case MEMORY:
                canvas.drawRoundRect(body, body.width() * .12f, body.width() * .12f, iconPaint);
                canvas.drawRect(body.left + body.width() * .28f, body.top + body.height() * .28f,
                        body.right - body.width() * .28f, body.bottom - body.height() * .28f, iconPaint);
                break;
            case STORAGE:
                canvas.drawRoundRect(body, body.width() * .12f, body.width() * .12f, iconPaint);
                canvas.drawLine(body.left + body.width() * .18f, body.bottom - body.height() * .25f,
                        body.right - body.width() * .18f, body.bottom - body.height() * .25f, iconPaint);
                break;
            case NOTIFICATIONS:
                Path bell = new Path();
                bell.moveTo(body.left + body.width() * .22f, body.bottom - body.height() * .28f);
                bell.quadTo(body.centerX(), body.top, body.right - body.width() * .22f,
                        body.bottom - body.height() * .28f);
                canvas.drawPath(bell, iconPaint);
                canvas.drawLine(body.left + body.width() * .16f, body.bottom - body.height() * .22f,
                        body.right - body.width() * .16f, body.bottom - body.height() * .22f, iconPaint);
                break;
            case MESSAGE:
                // A bubble with a tail, to tell it apart from the bell the
                // counter already uses.
                canvas.drawRoundRect(new RectF(body.left, body.top,
                                body.right, body.bottom - body.height() * .22f),
                        body.width() * .18f, body.width() * .18f, iconPaint);
                Path tail = new Path();
                tail.moveTo(body.left + body.width() * .28f, body.bottom - body.height() * .24f);
                tail.lineTo(body.left + body.width() * .28f, body.bottom);
                tail.lineTo(body.left + body.width() * .52f, body.bottom - body.height() * .24f);
                canvas.drawPath(tail, iconPaint);
                break;
            case CALENDAR:
                canvas.drawRoundRect(body, body.width() * .1f, body.width() * .1f, iconPaint);
                canvas.drawLine(body.left, body.top + body.height() * .3f,
                        body.right, body.top + body.height() * .3f, iconPaint);
                canvas.drawCircle(body.centerX(), body.centerY() + body.height() * .12f,
                        iconPaint.getStrokeWidth(), iconPaint);
                break;
            case STEPS:
                canvas.drawOval(new RectF(body.left + body.width() * .08f, body.centerY(),
                        body.centerX(), body.bottom), iconPaint);
                canvas.drawOval(new RectF(body.centerX(), body.top,
                        body.right - body.width() * .08f, body.centerY()), iconPaint);
                break;
            default:
                break;
        }
    }

    private void drawBatteryIcon(Canvas canvas, RectF body, boolean charging) {
        float terminal = body.width() * 0.12f;
        RectF caseBounds = new RectF(body.left, body.top, body.right - terminal, body.bottom);
        canvas.drawRoundRect(caseBounds, body.width() * 0.10f, body.width() * 0.10f, iconPaint);
        canvas.drawLine(
                caseBounds.right + iconPaint.getStrokeWidth(),
                body.centerY() - body.height() * 0.18f,
                body.right,
                body.centerY() + body.height() * 0.18f,
                iconPaint
        );
        if (snapshot.batteryPercent >= 0) {
            float fraction = Math.max(0f, Math.min(1f, snapshot.batteryPercent / 100f));
            RectF fill = new RectF(caseBounds);
            fill.inset(iconPaint.getStrokeWidth() * 1.8f, iconPaint.getStrokeWidth() * 1.8f);
            fill.right = fill.left + fill.width() * fraction;
            Paint.Style previous = iconPaint.getStyle();
            iconPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(fill, body.width() * 0.04f, body.width() * 0.04f, iconPaint);
            iconPaint.setStyle(previous);
        }
        if (charging) {
            Path bolt = new Path();
            bolt.moveTo(caseBounds.centerX() + body.width() * 0.05f, caseBounds.top + body.height() * 0.12f);
            bolt.lineTo(caseBounds.centerX() - body.width() * 0.12f, caseBounds.centerY());
            bolt.lineTo(caseBounds.centerX(), caseBounds.centerY());
            bolt.lineTo(caseBounds.centerX() - body.width() * 0.05f, caseBounds.bottom - body.height() * 0.12f);
            canvas.drawPath(bolt, iconPaint);
        }
    }

    private void drawSun(Canvas canvas, RectF body) {
        float radius = body.width() * 0.23f;
        canvas.drawCircle(body.centerX(), body.centerY(), radius, iconPaint);
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI * index / 4d;
            float inner = radius * 1.45f;
            float outer = radius * 1.95f;
            canvas.drawLine(
                    body.centerX() + (float) Math.cos(angle) * inner,
                    body.centerY() + (float) Math.sin(angle) * inner,
                    body.centerX() + (float) Math.cos(angle) * outer,
                    body.centerY() + (float) Math.sin(angle) * outer,
                    iconPaint
            );
        }
    }

    private void drawCloud(Canvas canvas, RectF body) {
        float y = body.centerY() + body.height() * 0.12f;
        canvas.drawArc(
                body.left + body.width() * 0.08f,
                y - body.height() * 0.26f,
                body.left + body.width() * 0.52f,
                y + body.height() * 0.18f,
                175f,
                190f,
                false,
                iconPaint
        );
        canvas.drawArc(
                body.left + body.width() * 0.30f,
                body.top + body.height() * 0.10f,
                body.right - body.width() * 0.08f,
                y + body.height() * 0.18f,
                190f,
                175f,
                false,
                iconPaint
        );
        canvas.drawLine(body.left + body.width() * 0.14f, y + body.height() * 0.16f,
                body.right - body.width() * 0.12f, y + body.height() * 0.16f, iconPaint);
    }

    private void drawAlarm(Canvas canvas, RectF body) {
        float radius = body.width() * 0.34f;
        canvas.drawCircle(body.centerX(), body.centerY(), radius, iconPaint);
        canvas.drawLine(body.centerX(), body.centerY(), body.centerX(),
                body.centerY() - radius * 0.62f, iconPaint);
        canvas.drawLine(body.centerX(), body.centerY(), body.centerX() + radius * 0.48f,
                body.centerY(), iconPaint);
        canvas.drawLine(body.left + body.width() * 0.12f, body.top + body.height() * 0.22f,
                body.left + body.width() * 0.28f, body.top + body.height() * 0.06f, iconPaint);
        canvas.drawLine(body.right - body.width() * 0.12f, body.top + body.height() * 0.22f,
                body.right - body.width() * 0.28f, body.top + body.height() * 0.06f, iconPaint);
    }

    private void drawMedia(Canvas canvas, RectF body) {
        float stemX = body.left + body.width() * 0.62f;
        canvas.drawLine(stemX, body.top + body.height() * 0.12f,
                stemX, body.bottom - body.height() * 0.23f, iconPaint);
        canvas.drawLine(stemX, body.top + body.height() * 0.12f,
                body.right - body.width() * 0.10f, body.top + body.height() * 0.24f, iconPaint);
        canvas.drawCircle(body.left + body.width() * 0.42f, body.bottom - body.height() * 0.18f,
                body.width() * 0.18f, iconPaint);
    }

    private void drawCompass(Canvas canvas, RectF body) {
        canvas.drawCircle(body.centerX(), body.centerY(), body.width() * 0.42f, iconPaint);
        Path needle = new Path();
        needle.moveTo(body.centerX(), body.top + body.height() * 0.10f);
        needle.lineTo(body.centerX() + body.width() * 0.13f, body.centerY());
        needle.lineTo(body.centerX(), body.bottom - body.height() * 0.10f);
        needle.lineTo(body.centerX() - body.width() * 0.13f, body.centerY());
        needle.close();
        canvas.drawPath(needle, iconPaint);
    }

    private void drawSpeed(Canvas canvas, RectF body) {
        canvas.drawArc(body, 200f, 140f, false, iconPaint);
        canvas.drawLine(body.centerX(), body.centerY(), body.right - body.width() * 0.18f,
                body.top + body.height() * 0.28f, iconPaint);
        canvas.drawCircle(body.centerX(), body.centerY(), iconPaint.getStrokeWidth(), iconPaint);
    }

    private void drawAltitude(Canvas canvas, RectF body) {
        Path mountain = new Path();
        mountain.moveTo(body.left, body.bottom - body.height() * 0.12f);
        mountain.lineTo(body.left + body.width() * 0.42f, body.top + body.height() * 0.12f);
        mountain.lineTo(body.left + body.width() * 0.60f, body.centerY());
        mountain.lineTo(body.left + body.width() * 0.72f, body.top + body.height() * 0.30f);
        mountain.lineTo(body.right, body.bottom - body.height() * 0.12f);
        canvas.drawPath(mountain, iconPaint);
    }

    private void drawTimer(Canvas canvas, RectF body) {
        canvas.drawCircle(body.centerX(), body.centerY() + body.height() * 0.05f,
                body.width() * 0.36f, iconPaint);
        canvas.drawLine(body.centerX(), body.top, body.centerX(), body.top + body.height() * 0.15f,
                iconPaint);
        canvas.drawLine(body.centerX() - body.width() * 0.14f, body.top,
                body.centerX() + body.width() * 0.14f, body.top, iconPaint);
        canvas.drawLine(body.centerX(), body.centerY(), body.centerX() + body.width() * 0.18f,
                body.centerY() + body.height() * 0.12f, iconPaint);
    }

    private void drawProfile(Canvas canvas, RectF body) {
        canvas.drawCircle(body.centerX(), body.top + body.height() * 0.30f,
                body.width() * 0.19f, iconPaint);
        canvas.drawArc(body.left + body.width() * 0.18f, body.centerY(),
                body.right - body.width() * 0.18f, body.bottom,
                190f, 160f, false, iconPaint);
    }

    private void drawTextIcon(Canvas canvas, RectF body) {
        canvas.drawLine(body.left + body.width() * 0.15f, body.top + body.height() * 0.16f,
                body.right - body.width() * 0.15f, body.top + body.height() * 0.16f, iconPaint);
        canvas.drawLine(body.centerX(), body.top + body.height() * 0.16f,
                body.centerX(), body.bottom - body.height() * 0.12f, iconPaint);
        canvas.drawLine(body.left + body.width() * 0.30f, body.bottom - body.height() * 0.12f,
                body.right - body.width() * 0.30f, body.bottom - body.height() * 0.12f, iconPaint);
    }

    private Palette resolvePalette() {
        DashboardSettings.Theme theme = settings.theme;
        if (theme == DashboardSettings.Theme.SYSTEM) {
            boolean night = (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            theme = night ? DashboardSettings.Theme.DARK : DashboardSettings.Theme.LIGHT;
        }
        if (theme == DashboardSettings.Theme.LIGHT) {
            return new Palette(Color.BLACK, Color.WHITE);
        }
        if (theme == DashboardSettings.Theme.ACCENT) {
            int accent = MaterialColors.getColor(
                    this,
                    androidx.appcompat.R.attr.colorPrimary,
                    Color.WHITE
            );
            return new Palette(accent, Color.BLACK);
        }
        return new Palette(Color.WHITE, Color.BLACK);
    }

    private float burnInShift(float density) {
        int amplitudeDp = DashboardWidgetLayout.loadBurnInShiftDp(getContext());
        if (amplitudeDp <= 0) {
            return 0f;
        }
        long step = snapshot.timestampMillis / (5 * 60_000L);
        // Five positions spanning plus and minus the chosen amplitude.
        return ((step % 5L) - 2L) * (amplitudeDp / 2f) * density;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private final class Line implements DashboardWidgetLayout.Item {
        final DashboardWidgetLayout.Widget widget;
        final String text;
        final boolean primary;
        final Icon icon;
        final float scale;
        final DashboardWidgetLayout.Position position;
        final DashboardWidgetLayout.Style style;
        /** The face this widget asked for, or null to take the panel's. */
        @Nullable final Typeface typeface;
        final int rotation;
        /**
         * Where this line landed in the last frame, in view coordinates.
         *
         * <p>The renderer used to draw and forget, so there was nothing for a
         * finger to hit: picking a widget meant finding its card in a list.
         * Every layout draws through {@link #drawLine}, so recording it there
         * covers all of them at once.
         */
        final RectF bounds = new RectF();

        Line(DashboardWidgetLayout.Widget widget, String text, boolean primary, Icon icon) {
            this.widget = widget;
            this.text = text;
            this.primary = primary;
            this.icon = DashboardWidgetLayout.isIconHidden(getContext(), widget)
                    ? Icon.NONE : icon;
            this.scale = DashboardWidgetLayout.scale(getContext(), widget);
            this.position = DashboardWidgetLayout.loadPosition(getContext(), widget);
            this.style = DashboardWidgetLayout.loadStyle(getContext(), widget);
            this.typeface = widgetTypeface(widget);
            this.rotation = DashboardWidgetLayout.loadRotation(getContext(), widget);
        }

        @Override public DashboardWidgetLayout.Widget widget() { return widget; }
    }

    private enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private enum Icon {
        NONE,
        BATTERY,
        BATTERY_CHARGING,
        TEMPERATURE,
        WEATHER_CLEAR,
        WEATHER_CLOUD,
        WEATHER_FOG,
        WEATHER_RAIN,
        WEATHER_SNOW,
        WEATHER_STORM,
        ALARM,
        MEDIA,
        COMPASS,
        SPEED,
        ALTITUDE,
        TIMER,
        PROFILE,
        TEXT
        ,NETWORK, MEMORY, STORAGE, NOTIFICATIONS, MESSAGE, CALENDAR, STEPS
    }

    private static final class Palette {
        final int text;
        final int surface;

        Palette(int text, int surface) {
            this.text = text;
            this.surface = surface;
        }
    }
}
