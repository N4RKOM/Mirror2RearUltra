package com.tpkarras.mirror2rearultra;

import android.content.Context;

import androidx.annotation.Nullable;

import com.tpkarras.mirror2rearultra.DashboardWidgetLayout.Widget;

import java.util.Set;

/**
 * Everything the panel needs to know about one widget, in one place.
 *
 * <p>Before this a widget was spread over five: the checks that decided which
 * sensors to listen to and how often to tick lived in the controller, the text
 * in the view, and each had its own list of which widgets they meant. Adding
 * one meant finding every list. Now a spec says what the widget reads from
 * the phone ({@link #sources}), how often it needs a fresh frame ({@link
 * #tickMillis}) and what it says ({@link #read}), and the controller and the
 * view ask the registry in {@link WidgetSpecs}.
 */
interface WidgetSpec {
    /** Where a widget's data comes from; the controller listens to the union of what is on. */
    enum Source {
        /** GPS fixes, for speed and altitude. */
        LOCATION,
        /** The rotation vector, for the compass. */
        HEADING,
        /** Gravity, for the level. */
        GRAVITY,
        /** The barometer. */
        BAROMETER,
        /** The step counter. */
        STEPS,
        /** The ringer, do-not-disturb and volume broadcasts. */
        SOUND,
        /** Connection changes. */
        CONNECTIVITY,
        /** The forecast service. */
        WEATHER
    }

    Widget widget();

    Set<Source> sources();

    /**
     * How soon this widget wants another frame while it is on, in
     * milliseconds, or 0 when the ordinary pace will do.
     *
     * @param charging whether the phone is on the charger
     */
    long tickMillis(Context context, boolean charging);

    /** The widget's row, or null when it should not have one. */
    @Nullable
    WidgetReading read(WidgetEnv env);
}
