package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Saved dashboard arrangements.
 *
 * <p>Everything is kept in slots, and a slot is just a key prefix. Two kinds
 * use them:
 *
 * <ul>
 *   <li>a <b>profile slot</b>, named after a mirroring profile, which the
 *       profile switch applies on its own so the panel follows the profile;
 *   <li>a <b>named template</b>, which the user creates and applies by hand,
 *       and which is listed in {@link #listNamed}.
 * </ul>
 *
 * <p>Both store exactly the same thing, so a template saved by hand and one
 * attached to a profile cannot drift apart in what they remember.
 */
final class DashboardTemplateStore {
    private static final String PREFS = "dashboard_custom_template";
    private static final String SAVED = "saved";
    /** Dotted so {@link #migrateLegacyTemplate} leaves these registry keys be. */
    private static final String NAMED_INDEX = "index.named";
    private static final String NAMED_NAME_PREFIX = "index.name.";
    private static final String NAMED_SLOT_PREFIX = "T_";

    /** As many named templates as the builder can list without becoming a list screen. */
    static final int MAX_NAMED = 8;

    private DashboardTemplateStore() {}

    /** One saved template the user named. */
    static final class Named {
        final String id;
        final String name;

        Named(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static boolean exists(Context context) {
        return exists(context, MirrorSettings.loadActiveProfile(context).id);
    }

    static boolean exists(Context context, String slot) {
        SharedPreferences preferences = prefs(context);
        migrateLegacyTemplate(preferences, slot);
        return preferences.getBoolean(key(slot, SAVED), false);
    }

    static void save(Context context) {
        save(context, MirrorSettings.loadActiveProfile(context).id);
    }

    static void save(Context context, String slot) {
        String prefix = prefix(slot);
        SharedPreferences.Editor out = prefs(context).edit().putBoolean(prefix + SAVED, true);
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(context);
        out.putString(prefix + "layout", settings.layout.name());
        out.putString(prefix + "orientation", DashboardWidgetLayout.loadOrientation(context).name());
        int pageCount = DashboardWidgetLayout.loadPageCount(context);
        out.putInt(prefix + "page_count", pageCount);
        // Pages after the first carry their own arrangement and rotation.
        for (int page = 2; page <= DashboardWidgetLayout.MAX_PAGES; page++) {
            out.putString(prefix + "layout_page_" + page, DashboardWidgetLayout
                    .loadPageLayout(context, page, settings.layout).name());
            out.putString(prefix + "orientation_page_" + page,
                    DashboardWidgetLayout.loadPageOrientation(context, page).name());
        }
        List<DashboardWidgetLayout.Widget> order = DashboardWidgetLayout.loadOrder(context);
        StringBuilder serializedOrder = new StringBuilder();
        for (DashboardWidgetLayout.Widget widget : order) {
            if (serializedOrder.length() > 0) serializedOrder.append(',');
            serializedOrder.append(widget.name());
            out.putBoolean(prefix + "visible_" + widget.name(), DashboardWidgetLayout.isExtraWidget(widget)
                    ? DashboardWidgetLayout.isExtraEnabled(context, widget)
                    : settings.isWidgetVisible(widget));
            out.putString(prefix + "size_" + widget.name(), DashboardWidgetLayout.loadSize(context, widget).name());
            out.putString(prefix + "position_" + widget.name(), DashboardWidgetLayout.loadPosition(context, widget).name());
            out.putInt(prefix + "page_" + widget.name(), DashboardWidgetLayout.loadPage(context, widget));
            out.putString(prefix + "style_" + widget.name(), DashboardWidgetLayout.loadStyle(context, widget).name());
            out.putString(prefix + "presence_" + widget.name(),
                    DashboardWidgetLayout.loadPresence(context, widget).name());
            out.putString(prefix + "gap_" + widget.name(),
                    DashboardWidgetLayout.loadGap(context, widget).name());
            // The continuous size and the free position, which the three-way
            // size control and the flowed layouts never touch.
            out.putInt(prefix + "scale_" + widget.name(),
                    Math.round(DashboardWidgetLayout.scale(context, widget) * 1000f));
            out.putBoolean(prefix + "placed_" + widget.name(),
                    DashboardWidgetLayout.hasFreePosition(context, widget));
            out.putInt(prefix + "free_x_" + widget.name(),
                    Math.round(DashboardWidgetLayout.loadFreeX(context, widget) * 1000f));
            out.putInt(prefix + "free_y_" + widget.name(),
                    Math.round(DashboardWidgetLayout.loadFreeY(context, widget) * 1000f));
        }
        out.putString(prefix + "order", serializedOrder.toString()).apply();
    }

    static boolean apply(Context context) {
        return apply(context, MirrorSettings.loadActiveProfile(context).id);
    }

    static boolean apply(Context context, String slot) {
        SharedPreferences in = prefs(context);
        migrateLegacyTemplate(in, slot);
        String prefix = prefix(slot);
        if (!in.getBoolean(prefix + SAVED, false)) return false;
        ArrayList<DashboardWidgetLayout.Widget> order = new ArrayList<>();
        for (String value : in.getString(prefix + "order", "").split(",")) {
            try { order.add(DashboardWidgetLayout.Widget.valueOf(value)); }
            catch (IllegalArgumentException ignored) {}
        }
        for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.Widget.values())
            if (!order.contains(widget)) order.add(widget);
        DashboardWidgetLayout.saveOrder(context, order);
        try { DashboardWidgetLayout.saveOrientation(context, DashboardWidgetLayout.Orientation.valueOf(
                in.getString(prefix + "orientation", DashboardWidgetLayout.Orientation.AUTO.name()))); }
        catch (IllegalArgumentException ignored) {}
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(context);
        try { settings = settings.withLayout(DashboardSettings.Layout.valueOf(
                in.getString(prefix + "layout", DashboardSettings.Layout.STACKED.name()))); }
        catch (IllegalArgumentException ignored) {}
        // The count first: it pulls widgets back off pages it removes, so it has
        // to run before the per-widget pages are written.
        DashboardWidgetLayout.savePageCount(context, in.getInt(prefix + "page_count", 1));
        for (int page = 2; page <= DashboardWidgetLayout.MAX_PAGES; page++) {
            try {
                DashboardWidgetLayout.savePageLayout(context, page,
                        DashboardSettings.Layout.valueOf(in.getString(
                                prefix + "layout_page_" + page, settings.layout.name())));
            } catch (IllegalArgumentException ignored) {}
            try {
                DashboardWidgetLayout.savePageOrientation(context, page,
                        DashboardWidgetLayout.Orientation.valueOf(in.getString(
                                prefix + "orientation_page_" + page,
                                DashboardWidgetLayout.loadOrientation(context).name())));
            } catch (IllegalArgumentException ignored) {}
        }
        for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.Widget.values()) {
            if (DashboardWidgetLayout.isExtraWidget(widget)) {
                DashboardWidgetLayout.setExtraEnabled(context, widget,
                        in.getBoolean(prefix + "visible_" + widget.name(), false));
            } else {
                settings = settings.withWidget(widget, in.getBoolean(prefix + "visible_" + widget.name(), false));
            }
            try { DashboardWidgetLayout.saveSize(context, widget, DashboardWidgetLayout.Size.valueOf(
                    in.getString(prefix + "size_" + widget.name(), DashboardWidgetLayout.Size.NORMAL.name()))); }
            catch (IllegalArgumentException ignored) {}
            try { DashboardWidgetLayout.savePosition(context, widget, DashboardWidgetLayout.Position.valueOf(
                    in.getString(prefix + "position_" + widget.name(), DashboardWidgetLayout.Position.CENTER.name()))); }
            catch (IllegalArgumentException ignored) {}
            DashboardWidgetLayout.savePage(context, widget, in.getInt(prefix + "page_" + widget.name(), 1));
            try { DashboardWidgetLayout.saveStyle(context, widget, DashboardWidgetLayout.Style.valueOf(
                    in.getString(prefix + "style_" + widget.name(), DashboardWidgetLayout.Style.DEFAULT.name()))); }
            catch (IllegalArgumentException ignored) {}
            try { DashboardWidgetLayout.savePresence(context, widget, DashboardWidgetLayout.Presence.valueOf(
                    in.getString(prefix + "presence_" + widget.name(),
                            DashboardWidgetLayout.Presence.WHEN_DATA.name()))); }
            catch (IllegalArgumentException ignored) {}
            try { DashboardWidgetLayout.saveGap(context, widget, DashboardWidgetLayout.Gap.valueOf(
                    in.getString(prefix + "gap_" + widget.name(),
                            DashboardWidgetLayout.Gap.NONE.name()))); }
            catch (IllegalArgumentException ignored) {}
            int scale = in.getInt(prefix + "scale_" + widget.name(), 0);
            if (scale > 0) {
                DashboardWidgetLayout.saveScale(context, widget, scale / 1000f);
            }
            if (in.getBoolean(prefix + "placed_" + widget.name(), false)) {
                DashboardWidgetLayout.saveFreePosition(context, widget,
                        in.getInt(prefix + "free_x_" + widget.name(), 500) / 1000f,
                        in.getInt(prefix + "free_y_" + widget.name(), 500) / 1000f);
            } else {
                DashboardWidgetLayout.clearFreePosition(context, widget);
            }
        }
        MirrorSettings.saveDashboardSettings(context, settings);
        return true;
    }

    /**
     * The templates the user saved, oldest first.
     *
     * <p>The order is the order they were created in, so a template does not
     * move around the list when it is applied or renamed.
     */
    static List<Named> listNamed(Context context) {
        SharedPreferences in = prefs(context);
        List<Named> result = new ArrayList<>();
        for (String id : splitIds(in.getString(NAMED_INDEX, ""))) {
            if (!in.getBoolean(key(id, SAVED), false)) continue;
            result.add(new Named(id, in.getString(NAMED_NAME_PREFIX + id, id)));
        }
        return result;
    }

    /**
     * Saves the arrangement on screen under a new name.
     *
     * @return the new template, or null when {@link #MAX_NAMED} is already used
     */
    static Named createNamed(Context context, String name) {
        SharedPreferences in = prefs(context);
        List<String> ids = splitIds(in.getString(NAMED_INDEX, ""));
        if (ids.size() >= MAX_NAMED) {
            return null;
        }
        String id = NAMED_SLOT_PREFIX + System.currentTimeMillis();
        // Two templates saved inside the same millisecond would share a slot.
        while (ids.contains(id)) {
            id = id + "_";
        }
        ids.add(id);
        save(context, id);
        in.edit()
                .putString(NAMED_INDEX, String.join(",", ids))
                .putString(NAMED_NAME_PREFIX + id, name)
                .apply();
        return new Named(id, name);
    }

    /** Overwrites a named template with the arrangement on screen. */
    static void overwriteNamed(Context context, String id) {
        save(context, id);
    }

    static void renameNamed(Context context, String id, String name) {
        prefs(context).edit().putString(NAMED_NAME_PREFIX + id, name).apply();
    }

    static boolean applyNamed(Context context, String id) {
        return apply(context, id);
    }

    static void deleteNamed(Context context, String id) {
        SharedPreferences in = prefs(context);
        List<String> ids = splitIds(in.getString(NAMED_INDEX, ""));
        ids.remove(id);
        SharedPreferences.Editor editor = in.edit()
                .putString(NAMED_INDEX, String.join(",", ids))
                .remove(NAMED_NAME_PREFIX + id);
        String prefix = prefix(id);
        for (String key : in.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }

    static Map<String, String> exportState(Context context) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) result.put(entry.getKey(), "b:" + value);
            else if (value instanceof Integer) result.put(entry.getKey(), "i:" + value);
            else if (value instanceof String) result.put(entry.getKey(), "s:" + value);
        }
        return result;
    }

    static void importState(Context context, Map<String, String> state) {
        SharedPreferences.Editor editor = prefs(context).edit().clear();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            String value = entry.getValue();
            if (value.startsWith("b:")) editor.putBoolean(entry.getKey(), Boolean.parseBoolean(value.substring(2)));
            else if (value.startsWith("i:")) {
                try { editor.putInt(entry.getKey(), Integer.parseInt(value.substring(2))); }
                catch (NumberFormatException ignored) {}
            } else if (value.startsWith("s:")) editor.putString(entry.getKey(), value.substring(2));
        }
        editor.apply();
    }

    private static List<String> splitIds(String value) {
        List<String> result = new ArrayList<>();
        for (String id : value.split(",")) {
            if (!id.isEmpty()) result.add(id);
        }
        return result;
    }

    private static void migrateLegacyTemplate(SharedPreferences preferences, String slot) {
        if (!preferences.getBoolean(SAVED, false) || preferences.contains(key(slot, SAVED))) return;
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().contains(".")) continue;
            Object value = entry.getValue();
            String migratedKey = key(slot, entry.getKey());
            if (value instanceof Boolean) editor.putBoolean(migratedKey, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(migratedKey, (Integer) value);
            else if (value instanceof String) editor.putString(migratedKey, (String) value);
        }
        editor.remove(SAVED).apply();
    }

    private static String prefix(String slot) { return slot + "."; }
    private static String key(String slot, String name) { return prefix(slot) + name; }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
