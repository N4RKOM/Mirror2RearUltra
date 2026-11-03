package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The widget arrangement the app starts with, and goes back to on a reset.
 *
 * <p>It lives in {@code assets/default_dashboard.xml}: the stored widget
 * layout as it was taken from a phone, plus which of the always-available
 * widgets are on, the arrangement of the page, and optionally what the panel
 * shows and how opaque its background is. Nothing here is special
 * once it is applied - it is written into the same stores everything else
 * edits, so it can be changed from the builder like any other arrangement.
 *
 * <p>To change what the app starts with, replace the asset.
 */
final class DefaultDashboard {
    private static final String TAG = "DefaultDashboard";
    static final String ASSET = "default_dashboard.xml";
    private static final String FORMAT = "Mirror2RearUltra default dashboard";
    /** Kept apart from the layout, which a reset or an import clears. */
    private static final String PREFS = "dashboard_defaults";
    private static final String SEEDED = "seeded";

    static final class Data {
        final Map<String, String> layout;
        final DashboardSettings.Layout arrangement;
        final Map<DashboardWidgetLayout.Widget, Boolean> visible;
        /** What the panel shows, or null when the asset leaves it as it is. */
        @Nullable final RearContentMode contentMode;
        /** The panel's background opacity, or null when the asset leaves it as it is. */
        @Nullable final Integer backgroundOpacity;

        Data(Map<String, String> layout, DashboardSettings.Layout arrangement,
                Map<DashboardWidgetLayout.Widget, Boolean> visible,
                @Nullable RearContentMode contentMode, @Nullable Integer backgroundOpacity) {
            this.layout = Collections.unmodifiableMap(layout);
            this.arrangement = arrangement;
            this.visible = Collections.unmodifiableMap(visible);
            this.contentMode = contentMode;
            this.backgroundOpacity = backgroundOpacity;
        }
    }

    private DefaultDashboard() {
    }

    /** Parses the asset's text; kept free of Android so it can be tested. */
    static Data read(InputStream input) throws IOException {
        Properties properties = new Properties();
        try {
            properties.loadFromXML(input);
        } catch (IllegalArgumentException error) {
            throw new IOException("Not a properties file", error);
        }
        if (!FORMAT.equals(properties.getProperty("format"))) {
            throw new IOException("Not a default dashboard");
        }
        int count;
        try {
            count = Integer.parseInt(properties.getProperty("layout.count", ""));
        } catch (NumberFormatException error) {
            throw new IOException("No layout", error);
        }
        Map<String, String> layout = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = properties.getProperty("layout." + index + ".key");
            String value = properties.getProperty("layout." + index + ".value");
            if (key == null || value == null
                    || DashboardWidgetLayout.decodeStateValue(value) == null) {
                throw new IOException("Damaged layout entry " + index);
            }
            layout.put(key, value);
        }
        DashboardSettings.Layout arrangement;
        Map<DashboardWidgetLayout.Widget, Boolean> visible = new LinkedHashMap<>();
        RearContentMode contentMode = null;
        Integer backgroundOpacity = null;
        try {
            arrangement = DashboardSettings.Layout.valueOf(
                    properties.getProperty("settings.layout", ""));
            String mode = properties.getProperty("settings.mode");
            if (mode != null) {
                contentMode = RearContentMode.valueOf(mode);
            }
            String opacity = properties.getProperty("settings.backgroundOpacity");
            if (opacity != null) {
                backgroundOpacity = Integer.valueOf(opacity);
            }
            for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.Widget.values()) {
                String flag = properties.getProperty("settings.visible." + widget.name());
                if (flag != null) {
                    visible.put(widget, Boolean.parseBoolean(flag));
                }
            }
        } catch (IllegalArgumentException error) {
            throw new IOException("Unknown arrangement", error);
        }
        return new Data(layout, arrangement, visible, contentMode, backgroundOpacity);
    }

    /**
     * Replaces the arrangement with the default one.
     *
     * @return false when the asset could not be read, with nothing changed
     */
    static boolean apply(Context context) {
        Data data;
        try (InputStream input = context.getAssets().open(ASSET)) {
            data = read(input);
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Default dashboard unreadable", error);
            return false;
        }
        DashboardWidgetLayout.importState(context, data.layout);
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(context)
                .withLayout(data.arrangement);
        if (data.contentMode != null) {
            settings = settings.withContentMode(data.contentMode);
        }
        if (data.backgroundOpacity != null) {
            settings = settings.withBackgroundOpacity(data.backgroundOpacity);
        }
        for (Map.Entry<DashboardWidgetLayout.Widget, Boolean> entry : data.visible.entrySet()) {
            settings = settings.withWidget(entry.getKey(), entry.getValue());
        }
        MirrorSettings.saveDashboardSettings(context, settings);
        return true;
    }

    /**
     * Applies the default once, on the first run of an install that has never
     * been arranged.
     *
     * <p>An install that already holds an arrangement - an update, or a phone
     * restored from a backup - is left exactly as it is. Either way the check
     * is made once, so a later reset or import is never answered with the
     * default behind the user's back.
     */
    static void seedOnFirstRun(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.getBoolean(SEEDED, false)) {
            return;
        }
        boolean fresh = !DashboardWidgetLayout.hasStoredState(context)
                && !MirrorSettings.hasDashboardSettings(context);
        if (fresh) {
            apply(context);
        }
        preferences.edit().putBoolean(SEEDED, true).apply();
    }
}
