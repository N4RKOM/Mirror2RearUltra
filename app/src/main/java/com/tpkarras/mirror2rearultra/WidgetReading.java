package com.tpkarras.mirror2rearultra;

/**
 * What one widget has to say right now: its text, its icon, and whether it
 * has anything to say at all.
 *
 * <p>A widget with no reading is not the same as a widget with nothing to
 * report. The first is {@code null} from {@link WidgetSpec#read}: there is no
 * row. The second is a {@linkplain #gated gated} reading without data, and
 * whether it holds a dash or drops out is the widget's presence setting,
 * applied by the view that draws the rows.
 */
final class WidgetReading {
    final String text;
    final WidgetIcon icon;
    /** The clock-sized line; only the clock is one. */
    final boolean primary;
    /** Whether the reading is subject to the widget's presence setting. */
    final boolean gated;
    final boolean hasData;
    /** How far through its period, 0 to 1, for the progress icon; -1 for the rest. */
    final float progress;
    /** The widget wants the panel to buzz: a countdown has just run out. */
    final boolean buzz;

    /** How much the figure deserves a glance; it tints the row. */
    final Severity severity;

    private WidgetReading(String text, WidgetIcon icon, boolean primary, boolean gated,
            boolean hasData, float progress, boolean buzz) {
        this(text, icon, primary, gated, hasData, progress, buzz, Severity.NORMAL);
    }

    private WidgetReading(String text, WidgetIcon icon, boolean primary, boolean gated,
            boolean hasData, float progress, boolean buzz, Severity severity) {
        this.text = text;
        this.icon = icon;
        this.primary = primary;
        this.gated = gated;
        this.hasData = hasData;
        this.progress = progress;
        this.buzz = buzz;
        this.severity = severity;
    }

    WidgetReading withSeverity(Severity level) {
        return new WidgetReading(text, icon, primary, gated, hasData, progress, buzz, level);
    }

    /** A row that is always drawn as it stands. */
    static WidgetReading plain(String text, WidgetIcon icon) {
        return new WidgetReading(text, icon, false, false, true, -1f, false);
    }

    static WidgetReading primary(String text, WidgetIcon icon) {
        return new WidgetReading(text, icon, true, false, true, -1f, false);
    }

    /**
     * A row that drops out, or holds a dash, when there is no data.
     *
     * <p>The text of a reading without data is never shown, so callers pass an
     * empty one.
     */
    static WidgetReading gated(boolean hasData, String text, WidgetIcon icon) {
        return new WidgetReading(text, icon, false, true, hasData, -1f, false);
    }

    WidgetReading withProgress(float fraction) {
        return new WidgetReading(text, icon, primary, gated, hasData, fraction, buzz, severity);
    }

    WidgetReading withBuzz(boolean buzzes) {
        return new WidgetReading(text, icon, primary, gated, hasData, progress, buzzes, severity);
    }
}
