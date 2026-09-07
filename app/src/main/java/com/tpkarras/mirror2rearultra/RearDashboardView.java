package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    @Nullable private DashboardWidgetLayout.Widget draggedWidget;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean dragMoved;
    /** Where the dragged widget was before the finger touched it. */
    private float dragStartFractionX;
    private float dragStartFractionY;
    private boolean dragStartHadPosition;
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
    private final Runnable pageFlip = this::invalidate;
    private int userPage = 1;
    private long lastPageInteractionMillis;
    private long autoPageResumeMillis;
    private final Runnable returnToFirstPage = () -> {
        userPage = 1;
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

    /** The widget under a point, or null. Topmost first, so later lines win. */
    @Nullable
    private DashboardWidgetLayout.Widget widgetAt(float x, float y) {
        for (int index = drawnLines.size() - 1; index >= 0; index--) {
            Line line = drawnLines.get(index);
            // A 126px-wide panel makes for small targets, so the strip is
            // widened to something a finger can actually land on.
            float slack = Math.max(10f * getResources().getDisplayMetrics().density * 0.5f,
                    line.bounds.height() * 0.4f);
            if (x >= line.bounds.left - slack && x <= line.bounds.right + slack
                    && y >= line.bounds.top - slack && y <= line.bounds.bottom + slack) {
                return line.widget;
            }
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!contentMode.showsDashboard() || getWidth() <= 0 || getHeight() <= 0) {
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
        float density = contentDensity();
        float shift = burnInShift(density);
        shiftX = shift;
        shiftY = -shift;
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
        canvas.restore();
        drawSelection(canvas, getResources().getDisplayMetrics().density);
        schedulePageFlip();
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
    protected void onDetachedFromWindow() {
        removeCallbacks(pageFlip);
        removeCallbacks(returnToFirstPage);
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
            } else if (variant == DashboardWidgetLayout.Variant.DETAILED && snapshot.charging) {
                value += "\n" + getResources().getString(R.string.dashboard_variant_charging);
            }
            lines.add(new Line(DashboardWidgetLayout.Widget.BATTERY, value, false,
                    snapshot.charging ? Icon.BATTERY_CHARGING : Icon.BATTERY));
        }
        if (settings.showTemperature && snapshot.temperatureTenthsCelsius >= 0) {
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.TEMPERATURE);
            String value = variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? Math.round(snapshot.temperatureTenthsCelsius / 10f) + "°"
                    : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? String.format(locale, "%.1f °C", snapshot.temperatureTenthsCelsius / 10f)
                    : getResources().getString(R.string.dashboard_device_temperature,
                            snapshot.temperatureTenthsCelsius / 10f);
            lines.add(new Line(DashboardWidgetLayout.Widget.TEMPERATURE, value, false, Icon.TEMPERATURE));
        }
        if (settings.showWeather) {
            String weather = weatherLine();
            if (!weather.isEmpty()) {
                DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.WEATHER);
                if (variant == DashboardWidgetLayout.Variant.ALTERNATE
                        && snapshot.weatherTemperatureCelsius != null) {
                    weather = snapshot.weatherTemperatureCelsius + "\n°C";
                } else if (variant == DashboardWidgetLayout.Variant.DETAILED
                        && !snapshot.weatherPlace.isEmpty()) {
                    weather += "\n" + snapshot.weatherPlace;
                }
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
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.MEDIA);
            String value = !snapshot.mediaTitle.isEmpty() ? snapshot.mediaTitle : snapshot.mediaArtist;
            if (variant == DashboardWidgetLayout.Variant.ALTERNATE && !snapshot.mediaArtist.isEmpty()) {
                value = snapshot.mediaArtist;
            } else if (variant == DashboardWidgetLayout.Variant.DETAILED
                    && !snapshot.mediaTitle.isEmpty() && !snapshot.mediaArtist.isEmpty()) {
                value = snapshot.mediaTitle + "\n" + snapshot.mediaArtist;
            }
            addLine(lines, DashboardWidgetLayout.Widget.MEDIA, hasData, value, Icon.MEDIA);
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
            int speed = hasData ? Math.round(snapshot.speedMetersPerSecond * 3.6f) : 0;
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.SPEED);
            String value = !hasData ? "" : variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? String.valueOf(speed) : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? speed + "\n" + getResources().getString(R.string.dashboard_speed_unit)
                    : getResources().getString(R.string.dashboard_speed_short, speed);
            addLine(lines, DashboardWidgetLayout.Widget.SPEED, hasData, value, Icon.SPEED);
        }
        if (settings.showAltitude) {
            boolean hasData = snapshot.altitudeMeters != null;
            long altitude = hasData ? Math.round(snapshot.altitudeMeters) : 0L;
            DashboardWidgetLayout.Variant variant = variantOf(DashboardWidgetLayout.Widget.ALTITUDE);
            String value = !hasData ? "" : variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? String.valueOf(altitude) : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? altitude + "\n" + getResources().getString(R.string.dashboard_altitude_unit)
                    : getResources().getString(R.string.dashboard_altitude_short, altitude);
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
            String value = variant == DashboardWidgetLayout.Variant.ALTERNATE && count > 99
                    ? "99+" : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? count + "\n" + getResources().getString(R.string.dashboard_variant_notifications)
                    : String.valueOf(count);
            addLine(lines, DashboardWidgetLayout.Widget.NOTIFICATIONS,
                    count > 0, value, Icon.NOTIFICATIONS);
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
            String value = variant == DashboardWidgetLayout.Variant.ALTERNATE
                    ? String.format(locale, "%,d", snapshot.stepsToday)
                    : variant == DashboardWidgetLayout.Variant.DETAILED
                    ? snapshot.stepsToday + "\n" + getResources().getString(R.string.dashboard_variant_steps)
                    : String.valueOf(snapshot.stepsToday);
            addLine(lines, DashboardWidgetLayout.Widget.STEPS,
                    snapshot.stepsToday >= 0, value, Icon.STEPS);
        }
        if (DashboardWidgetLayout.isExtraEnabled(
                getContext(), DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER)) {
            String value = weatherLine();
            addLine(lines, DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER,
                    true, value.isEmpty() ? "—" : value, weatherIcon(snapshot.weatherCode));
        }
        if (DashboardWidgetLayout.isExtraEnabled(
                getContext(), DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA)) {
            boolean hasMedia = !snapshot.mediaTitle.isEmpty() || !snapshot.mediaArtist.isEmpty();
            addLine(lines, DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA, true,
                    hasMedia ? snapshot.mediaTitle : "—", Icon.MEDIA);
        }
        DashboardWidgetLayout.sort(getContext(), lines);
        lines.removeIf(line -> !DashboardWidgetLayout.isVisible(getContext(), line.widget));
        return lines;
    }

    private List<Line> pageLines(List<Line> source) {
        int maxPage = 1;
        for (Line line : source) maxPage = Math.max(maxPage,
                DashboardWidgetLayout.loadPage(getContext(), line.widget));
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
            if (lastPageInteractionMillis > 0L) {
                page = Math.min(userPage, maxPage);
            } else if (auto && now >= autoPageResumeMillis) {
                page = (int) ((now / 8_000L) % maxPage) + 1;
                requestPageFlip(8_000L);
            } else {
                page = Math.min(userPage, maxPage);
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
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                touchStartX = event.getX();
                touchStartY = event.getY();
                dragMoved = false;
                draggedWidget = null;
                pinching = false;
                pinchedWidget = null;
                if (interactive && currentLayout() == DashboardSettings.Layout.FREE) {
                    DashboardWidgetLayout.Widget hit = widgetAt(event.getX(), event.getY());
                    if (hit != null) {
                        beginDrag(hit, event.getX(), event.getY());
                    }
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
                if (swipe) {
                    int maxPage = 1;
                    for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.Widget.values())
                        maxPage = Math.max(maxPage, DashboardWidgetLayout.loadPage(getContext(), widget));
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
        float halfHeight = box == null ? 0f : box.height() / 2f;
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
        removeCallbacks(returnToFirstPage);
        postDelayed(returnToFirstPage, 30_000L);
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

    private void drawFullscreenWeather(Canvas canvas, float density) {
        float pad = Math.min(10f * density, getWidth() * 0.06f);
        RectF panel = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
        canvas.drawRoundRect(panel, 20f * density, 20f * density, panelPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(clamp(Math.min(getWidth(), getHeight()) * 0.18f,
                20f * density, 58f * density));
        String current = snapshot.weatherTemperatureCelsius == null
                ? "—" : snapshot.weatherTemperatureCelsius + "°";
        canvas.drawText(current, panel.centerX(), panel.top + panel.height() * 0.28f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(clamp(panel.height() * 0.08f, 9f * density, 20f * density));
        canvas.drawText(snapshot.weatherPlace, panel.centerX(), panel.top + panel.height() * 0.42f,
                textPaint);

        WeatherForecastState.Snapshot forecast = WeatherForecastState.get();
        int count = Math.min(4, forecast.dates.length);
        if (count == 0) return;
        float cell = panel.width() / count;
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat output = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int index = 0; index < count; index++) {
            float x = panel.left + cell * (index + 0.5f);
            String day = forecast.dates[index];
            try { day = output.format(input.parse(day)); } catch (Exception ignored) {}
            textPaint.setTextSize(clamp(panel.height() * 0.07f, 8f * density, 18f * density));
            canvas.drawText(day, x, panel.top + panel.height() * 0.60f, textPaint);
            drawIcon(canvas, weatherIcon(forecast.codes[index]), new RectF(
                    x - cell * 0.13f, panel.top + panel.height() * 0.64f,
                    x + cell * 0.13f, panel.top + panel.height() * 0.78f));
            textPaint.setTextSize(clamp(panel.height() * 0.075f, 8f * density, 19f * density));
            canvas.drawText(forecast.maximums[index] + "°/" + forecast.minimums[index] + "°",
                    x, panel.top + panel.height() * 0.91f, textPaint);
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawFullscreenMedia(Canvas canvas, float density) {
        float pad = Math.min(10f * density, getWidth() * 0.06f);
        RectF panel = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
        canvas.drawRoundRect(panel, 20f * density, 20f * density, panelPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(clamp(panel.height() * 0.16f, 14f * density, 38f * density));
        String mediaTitle = snapshot.mediaTitle.isEmpty()
                ? getResources().getString(R.string.dashboard_media_nothing_playing)
                : snapshot.mediaTitle;
        CharSequence title = TextUtils.ellipsize(mediaTitle, textPaint,
                panel.width() * 0.84f, TextUtils.TruncateAt.END);
        canvas.drawText(title.toString(), panel.centerX(), panel.top + panel.height() * 0.32f,
                textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(clamp(panel.height() * 0.10f, 10f * density, 24f * density));
        CharSequence artist = TextUtils.ellipsize(snapshot.mediaArtist, textPaint,
                panel.width() * 0.80f, TextUtils.TruncateAt.END);
        canvas.drawText(artist.toString(), panel.centerX(), panel.top + panel.height() * 0.48f,
                textPaint);
        textPaint.setTextSize(clamp(panel.height() * 0.18f, 18f * density, 42f * density));
        canvas.drawText("‹‹", panel.left + panel.width() / 6f,
                panel.top + panel.height() * 0.78f, textPaint);
        canvas.drawText("▶", panel.centerX(), panel.top + panel.height() * 0.78f, textPaint);
        canvas.drawText("››", panel.right - panel.width() / 6f,
                panel.top + panel.height() * 0.78f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
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
                    snapshot.weatherTemperatureCelsius
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
                x = getWidth() / 2f;
                y = getHeight() * (index + 1f) / (lines.size() + 1f);
            }
            Alignment alignment = alignmentFor(line);
            // The whole panel, not the room left between the anchor and the
            // nearer edge. Measuring from the anchor made the widget shrink as
            // it was dragged towards an edge - down to a fifth of its size,
            // where there was nothing left to put a finger on - and made
            // growing it near an edge do nothing at all.
            float available = Math.max(1f, getWidth() - padding * 2f);
            // The stored point is the middle of the widget; text is drawn from
            // its baseline, so the two have to be reconciled here.
            float half = (textPaint.descent() - textPaint.ascent()) / 2f;
            float clampedY = Math.max(padding + half,
                    Math.min(getHeight() - padding - half, y));
            float baseline = clampedY - (textPaint.ascent() + textPaint.descent()) / 2f;
            drawLine(canvas, line, x, baseline, available, alignment, true);
        }
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
        RectF localBounds = new RectF(
                startX,
                firstBaseline + textPaint.ascent(),
                startX + totalWidth,
                firstBaseline + (parts.length - 1) * lineHeight * 1.04f
                        + textPaint.descent()
        );
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
        ,NETWORK, MEMORY, STORAGE, NOTIFICATIONS, CALENDAR, STEPS
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
