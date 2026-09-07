package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class DashboardWidgetLayout {
    enum Widget { CLOCK, DATE, BATTERY, TEMPERATURE, WEATHER, NEXT_ALARM, MEDIA,
        COMPASS, SPEED, ALTITUDE, SESSION_TIMER, ACTIVE_PROFILE, CUSTOM_TEXT,
        NETWORK, MEMORY, STORAGE, NOTIFICATIONS, CALENDAR, STEPS,
        FULLSCREEN_WEATHER, FULLSCREEN_MEDIA }
    enum Size { SMALL, NORMAL, LARGE }

    /**
     * Whether a widget stays on the panel when it has nothing to report.
     *
     * <p>Ten widgets used to draw a literal dash in that case while five others
     * simply disappeared - no rule, just how each was written. With room for
     * about five lines, switching on media, the alarm, the compass, speed and
     * altitude with nothing playing and no fix filled the whole panel with
     * dashes. {@link #WHEN_DATA} is the default for that reason; {@link
     * #ALWAYS} restores the dash for anyone who wants the row to hold its
     * place.
     */
    enum Presence { WHEN_DATA, ALWAYS }

    /** Extra space above a widget, for breaking a dense column into blocks. */
    enum Gap { NONE, SMALL, LARGE }
    enum Position { LEFT, CENTER, RIGHT }
    enum Orientation { AUTO, LANDSCAPE, PORTRAIT }
    enum Style { DEFAULT, ACCENT, MUTED }
    enum IdleMode { TIMEOUT_15, TIMEOUT_30, ALWAYS_ON }
    /** Widget-specific presentation: normal, compact alternative, or detailed. */
    enum Variant { DEFAULT, ALTERNATE, DETAILED }

    private static final String PREFS = "dashboard_widget_layout";
    private static final String ORDER = "order";
    private static final String SIZE_PREFIX = "size_";
    private static final String POSITION_PREFIX = "position_";
    private static final String ORIENTATION = "orientation";
    private static final String HIDDEN = "hidden";
    private static final String PAGE_PREFIX = "page_";
    private static final String STYLE_PREFIX = "style_";
    private static final String VARIANT_PREFIX = "variant_";
    private static final String ROTATION_PREFIX = "rotation_";
    private static final String ICON_HIDDEN_PREFIX = "icon_hidden_";
    private static final String EXTRA_ENABLED = "extra_enabled";
    private static final String PAGE_COUNT = "page_count";
    private static final String PRESENCE_PREFIX = "presence_";
    private static final String GAP_PREFIX = "gap_";
    private static final String BURN_IN_SHIFT = "burn_in_shift";
    private static final String GRID_SNAP = "grid_snap";
    private static final String SCALE_PREFIX = "scale_";
    private static final String FREE_X_PREFIX = "free_x_";
    private static final String FREE_Y_PREFIX = "free_y_";
    private static final String PAGE_LAYOUT_PREFIX = "layout_page_";
    private static final String PAGE_ORIENTATION_PREFIX = "orientation_page_";
    private static final String IDLE_MODE = "idle_mode";
    private static final String AOD_MIN_BRIGHTNESS = "aod_min_brightness";
    private static final String AUTO_PAGE_SWITCH = "auto_page_switch";

    private DashboardWidgetLayout() {}

    static List<Widget> loadOrder(Context context) {
        String saved = prefs(context).getString(ORDER, "");
        List<Widget> result = new ArrayList<>();
        Set<Widget> seen = new HashSet<>();
        for (String value : saved.split(",")) {
            try { Widget widget = Widget.valueOf(value); if (seen.add(widget)) result.add(widget); }
            catch (IllegalArgumentException ignored) {}
        }
        for (Widget widget : Widget.values()) if (seen.add(widget)) result.add(widget);
        return result;
    }

    static void saveOrder(Context context, List<Widget> widgets) {
        StringBuilder value = new StringBuilder();
        for (Widget widget : widgets) {
            if (value.length() > 0) value.append(',');
            value.append(widget.name());
        }
        prefs(context).edit().putString(ORDER, value.toString()).apply();
    }

    /**
     * The three-way control's reading of the widget's size.
     *
     * <p>Size is a continuous factor underneath - a pinch in the preview can
     * land anywhere - so this reports whichever of the three steps the factor
     * is nearest to. The control then shows something true rather than
     * snapping back to a value the widget no longer has.
     */
    static Size loadSize(Context context, Widget widget) {
        float factor = scale(context, widget);
        Size nearest = Size.NORMAL;
        float best = Float.MAX_VALUE;
        for (Size size : Size.values()) {
            float distance = Math.abs(factorOf(size) - factor);
            if (distance < best) {
                best = distance;
                nearest = size;
            }
        }
        return nearest;
    }

    static void saveSize(Context context, Widget widget, Size size) {
        prefs(context).edit()
                .putString(SIZE_PREFIX + widget.name(), size.name())
                .putInt(SCALE_PREFIX + widget.name(), Math.round(factorOf(size) * 1000f))
                .apply();
    }

    /** Smallest and largest a widget may be scaled to by hand. */
    static final float MIN_SCALE = 0.4f;
    static final float MAX_SCALE = 2.2f;

    static void saveScale(Context context, Widget widget, float factor) {
        float clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, factor));
        prefs(context).edit()
                .putInt(SCALE_PREFIX + widget.name(), Math.round(clamped * 1000f))
                .apply();
    }

    static float scale(Context context, Widget widget) {
        int stored = prefs(context).getInt(SCALE_PREFIX + widget.name(), 0);
        if (stored > 0) {
            return Math.max(MIN_SCALE, Math.min(MAX_SCALE, stored / 1000f));
        }
        // Nothing stored: an arrangement made before the size was continuous,
        // which kept only the three-way choice.
        try {
            return factorOf(Size.valueOf(prefs(context)
                    .getString(SIZE_PREFIX + widget.name(), Size.NORMAL.name())));
        } catch (IllegalArgumentException error) {
            return 1f;
        }
    }

    private static float factorOf(Size size) {
        return size == Size.SMALL ? 0.8f : size == Size.LARGE ? 1.25f : 1f;
    }

    /**
     * Where a widget sits in the free arrangement, as a fraction of the panel.
     *
     * <p>Fractions rather than pixels: the builder's preview and the panel are
     * different sizes at different densities, and a widget placed by finger in
     * one has to land in the same place in the other.
     */
    static boolean hasFreePosition(Context context, Widget widget) {
        return prefs(context).contains(FREE_X_PREFIX + widget.name());
    }

    static float loadFreeX(Context context, Widget widget) {
        return clampFraction(prefs(context).getInt(FREE_X_PREFIX + widget.name(), 500) / 1000f);
    }

    static float loadFreeY(Context context, Widget widget) {
        return clampFraction(prefs(context).getInt(FREE_Y_PREFIX + widget.name(), 500) / 1000f);
    }

    static void saveFreePosition(Context context, Widget widget, float x, float y) {
        prefs(context).edit()
                .putInt(FREE_X_PREFIX + widget.name(), Math.round(clampFraction(x) * 1000f))
                .putInt(FREE_Y_PREFIX + widget.name(), Math.round(clampFraction(y) * 1000f))
                .apply();
    }

    /** Forgets a widget's place, so the free layout falls back to the column. */
    static void clearFreePosition(Context context, Widget widget) {
        prefs(context).edit()
                .remove(FREE_X_PREFIX + widget.name())
                .remove(FREE_Y_PREFIX + widget.name())
                .apply();
    }

    /**
     * How many cells the free layout's grid divides the panel's short side
     * into. Square cells, so the grid does not change shape with the panel's
     * orientation.
     */
    static final int GRID_DIVISIONS = 8;

    static boolean isGridSnapEnabled(Context context) {
        return prefs(context).getBoolean(GRID_SNAP, false);
    }

    static void setGridSnapEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(GRID_SNAP, enabled).apply();
    }

    private static float clampFraction(float value) {
        return Math.max(0.02f, Math.min(0.98f, value));
    }

    static Position loadPosition(Context context, Widget widget) {
        try { return Position.valueOf(prefs(context).getString(
                POSITION_PREFIX + widget.name(), Position.CENTER.name())); }
        catch (IllegalArgumentException error) { return Position.CENTER; }
    }

    static void savePosition(Context context, Widget widget, Position position) {
        prefs(context).edit().putString(POSITION_PREFIX + widget.name(), position.name()).apply();
    }

    static Orientation loadOrientation(Context context) {
        try { return Orientation.valueOf(prefs(context).getString(ORIENTATION, Orientation.AUTO.name())); }
        catch (IllegalArgumentException error) { return Orientation.AUTO; }
    }

    static void saveOrientation(Context context, Orientation orientation) {
        prefs(context).edit().putString(ORIENTATION, orientation.name()).apply();
    }

    static boolean isVisible(Context context, Widget widget) {
        return !prefs(context).getStringSet(HIDDEN, Collections.emptySet()).contains(widget.name());
    }

    static void setVisible(Context context, Widget widget, boolean visible) {
        Set<String> hidden = new HashSet<>(prefs(context).getStringSet(HIDDEN, Collections.emptySet()));
        if (visible) hidden.remove(widget.name()); else hidden.add(widget.name());
        prefs(context).edit().putStringSet(HIDDEN, hidden).apply();
    }

    /** Most pages the panel is allowed to cycle through. */
    static final int MAX_PAGES = 3;

    /**
     * How many pages the arrangement uses.
     *
     * <p>Pages were only ever implicit: the renderer took the highest page any
     * widget sat on and cycled through that many. Nothing stated how many
     * pages existed, so the builder offered three per widget whether or not
     * the user wanted more than one screen. Storing the count makes it a
     * setting the user chooses, and lets the per-widget page control disappear
     * entirely when there is only one page.
     *
     * <p>An arrangement made before the count existed infers it from the pages
     * its widgets already sit on, so nothing moves on upgrade.
     */
    static int loadPageCount(Context context) {
        int stored = prefs(context).getInt(PAGE_COUNT, 0);
        if (stored >= 1) {
            return Math.min(MAX_PAGES, stored);
        }
        int inferred = 1;
        for (Widget widget : Widget.values()) {
            inferred = Math.max(inferred, loadPage(context, widget));
        }
        return inferred;
    }

    static void savePageCount(Context context, int count) {
        int clamped = Math.max(1, Math.min(MAX_PAGES, count));
        prefs(context).edit().putInt(PAGE_COUNT, clamped).apply();
        // A widget left on a page that no longer exists would drop out of the
        // rotation without saying so, so pull it back into range.
        for (Widget widget : Widget.values()) {
            if (loadPage(context, widget) > clamped) {
                savePage(context, widget, clamped);
            }
        }
    }

    static int loadPage(Context context, Widget widget) {
        return Math.max(1, Math.min(3, prefs(context).getInt(PAGE_PREFIX + widget.name(), 1)));
    }

    static void savePage(Context context, Widget widget, int page) {
        int target = Math.max(1, Math.min(3, page));
        prefs(context).edit().putInt(PAGE_PREFIX + widget.name(), target).apply();
        if (isWidgetEnabled(context, widget)) {
            enforceExclusivePage(context, widget, target);
        }
    }

    /** Widgets that can have nothing to show, and so can hold a dash. */
    static boolean canBeEmpty(Widget widget) {
        switch (widget) {
            case NEXT_ALARM:
            case MEDIA:
            case COMPASS:
            case SPEED:
            case ALTITUDE:
            case NETWORK:
            case MEMORY:
            case STORAGE:
            case NOTIFICATIONS:
            case CALENDAR:
            case STEPS:
            case FULLSCREEN_WEATHER:
            case FULLSCREEN_MEDIA:
                return true;
            default:
                // The clock, the date and the session timer always have a
                // value; battery, temperature, weather, the profile name and
                // the custom text already drop out on their own.
                return false;
        }
    }

    static Presence loadPresence(Context context, Widget widget) {
        try {
            return Presence.valueOf(prefs(context).getString(
                    PRESENCE_PREFIX + widget.name(), Presence.WHEN_DATA.name()));
        } catch (IllegalArgumentException error) {
            return Presence.WHEN_DATA;
        }
    }

    static void savePresence(Context context, Widget widget, Presence presence) {
        prefs(context).edit().putString(PRESENCE_PREFIX + widget.name(), presence.name()).apply();
    }

    static Gap loadGap(Context context, Widget widget) {
        try {
            return Gap.valueOf(prefs(context).getString(
                    GAP_PREFIX + widget.name(), Gap.NONE.name()));
        } catch (IllegalArgumentException error) {
            return Gap.NONE;
        }
    }

    static void saveGap(Context context, Widget widget, Gap gap) {
        prefs(context).edit().putString(GAP_PREFIX + widget.name(), gap.name()).apply();
    }

    /** Extra gaps to insert above a widget, in multiples of the row gap. */
    static float gapMultiplier(Context context, Widget widget) {
        Gap gap = loadGap(context, widget);
        return gap == Gap.SMALL ? 1f : gap == Gap.LARGE ? 2.5f : 0f;
    }

    /**
     * How far the whole arrangement drifts to spare the OLED panel.
     *
     * <p>The drift already existed but was fixed at 3dp either way. On a panel
     * that can be lit for hours it is worth being able to widen, or to switch
     * off when the extra movement is more distracting than the burn-in risk.
     */
    static int loadBurnInShiftDp(Context context) {
        return Math.max(0, Math.min(6, prefs(context).getInt(BURN_IN_SHIFT, 3)));
    }

    static void saveBurnInShiftDp(Context context, int dp) {
        prefs(context).edit().putInt(BURN_IN_SHIFT, Math.max(0, Math.min(6, dp))).apply();
    }

    /**
     * The arrangement style for one page.
     *
     * <p>Page one has no key of its own: it is the setting already shown on the
     * rear-panel screen, so an arrangement made before pages existed keeps
     * working and there is only ever one place the first page is stored. Pages
     * two and three are overrides and start out following page one.
     */
    static DashboardSettings.Layout loadPageLayout(
            Context context, int page, DashboardSettings.Layout firstPage) {
        if (page <= 1) {
            return firstPage;
        }
        try {
            return DashboardSettings.Layout.valueOf(prefs(context)
                    .getString(PAGE_LAYOUT_PREFIX + page, firstPage.name()));
        } catch (IllegalArgumentException error) {
            return firstPage;
        }
    }

    /** Pages after the first only; page one is saved with the panel settings. */
    static void savePageLayout(Context context, int page, DashboardSettings.Layout layout) {
        if (page <= 1) {
            return;
        }
        prefs(context).edit().putString(PAGE_LAYOUT_PREFIX + page, layout.name()).apply();
    }

    /** Which way round a page is drawn. Page one is the panel-wide setting. */
    static Orientation loadPageOrientation(Context context, int page) {
        Orientation firstPage = loadOrientation(context);
        if (page <= 1) {
            return firstPage;
        }
        try {
            return Orientation.valueOf(prefs(context)
                    .getString(PAGE_ORIENTATION_PREFIX + page, firstPage.name()));
        } catch (IllegalArgumentException error) {
            return firstPage;
        }
    }

    static void savePageOrientation(Context context, int page, Orientation orientation) {
        if (page <= 1) {
            saveOrientation(context, orientation);
            return;
        }
        prefs(context).edit().putString(PAGE_ORIENTATION_PREFIX + page, orientation.name()).apply();
    }

    static Style loadStyle(Context context, Widget widget) {
        try { return Style.valueOf(prefs(context).getString(
                STYLE_PREFIX + widget.name(), Style.DEFAULT.name())); }
        catch (IllegalArgumentException error) { return Style.DEFAULT; }
    }

    static void saveStyle(Context context, Widget widget, Style style) {
        prefs(context).edit().putString(STYLE_PREFIX + widget.name(), style.name()).apply();
    }

    static IdleMode loadIdleMode(Context context) {
        try {
            return IdleMode.valueOf(prefs(context).getString(
                    IDLE_MODE, IdleMode.ALWAYS_ON.name()));
        } catch (IllegalArgumentException error) {
            return IdleMode.ALWAYS_ON;
        }
    }

    static void saveIdleMode(Context context, IdleMode mode) {
        prefs(context).edit().putString(IDLE_MODE, mode.name()).apply();
    }

    static int loadAodMinBrightnessPercent(Context context) {
        return Math.max(1, Math.min(30,
                prefs(context).getInt(AOD_MIN_BRIGHTNESS, 8)));
    }

    static void saveAodMinBrightnessPercent(Context context, int percent) {
        prefs(context).edit().putInt(AOD_MIN_BRIGHTNESS,
                Math.max(1, Math.min(30, percent))).apply();
    }

    static boolean isAutoPageSwitchEnabled(Context context) {
        return prefs(context).getBoolean(AUTO_PAGE_SWITCH, false);
    }

    static void setAutoPageSwitchEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(AUTO_PAGE_SWITCH, enabled).apply();
    }

    static boolean supportsVariant(Widget widget) {
        return true;
    }

    static Variant loadVariant(Context context, Widget widget) {
        try { return Variant.valueOf(prefs(context).getString(
                VARIANT_PREFIX + widget.name(), Variant.DEFAULT.name())); }
        catch (IllegalArgumentException error) { return Variant.DEFAULT; }
    }

    static void saveVariant(Context context, Widget widget, Variant variant) {
        prefs(context).edit().putString(VARIANT_PREFIX + widget.name(), variant.name()).apply();
    }

    static int loadRotation(Context context, Widget widget) {
        int rotation = prefs(context).getInt(ROTATION_PREFIX + widget.name(), 0);
        return ((rotation % 360) + 360) % 360;
    }

    static void saveRotation(Context context, Widget widget, int rotation) {
        int normalized = ((rotation % 360) + 360) % 360;
        prefs(context).edit().putInt(ROTATION_PREFIX + widget.name(), normalized).apply();
    }

    static boolean isIconHidden(Context context, Widget widget) {
        return prefs(context).getBoolean(ICON_HIDDEN_PREFIX + widget.name(), false);
    }

    static void setIconHidden(Context context, Widget widget, boolean hidden) {
        prefs(context).edit().putBoolean(ICON_HIDDEN_PREFIX + widget.name(), hidden).apply();
    }

    static boolean hasMultiplePages(Context context) {
        for (Widget widget : Widget.values()) if (loadPage(context, widget) > 1) return true;
        return false;
    }

    /**
     * Whether a widget is currently shown on the rear panel.
     *
     * <p>Enablement lives in two places: the thirteen original widgets are
     * fields on {@link DashboardSettings}, the six added later are a name set
     * in this class's own preferences, and both are gated by the hidden flag.
     * The settings screen and the builder used to reimplement that rule
     * separately, so they could disagree about what was on. Both now read it
     * here.
     */
    static boolean isWidgetEnabled(Context context, Widget widget) {
        boolean enabled = isExtraWidget(widget)
                ? isExtraEnabled(context, widget)
                : MirrorSettings.loadDashboardSettings(context).isWidgetVisible(widget);
        return enabled && isVisible(context, widget);
    }

    /**
     * Turns a widget on or off, whichever store holds it.
     *
     * <p>Switching one on also clears the hidden flag, so a widget the user
     * just enabled appears even if something had removed it from the layout.
     */
    static void setWidgetEnabled(Context context, Widget widget, boolean enabled) {
        setVisible(context, widget, true);
        if (isExtraWidget(widget)) {
            setExtraEnabled(context, widget, enabled);
            // The rear display listens to the settings store, so re-saving is
            // what publishes a change that lives outside it.
            MirrorSettings.saveDashboardSettings(
                    context, MirrorSettings.loadDashboardSettings(context));
        } else {
            MirrorSettings.saveDashboardSettings(context,
                    MirrorSettings.loadDashboardSettings(context).withWidget(widget, enabled));
        }
        if (enabled) {
            enforceExclusivePage(context, widget, loadPage(context, widget));
        }
    }

    static boolean isExtraWidget(Widget widget) {
        return widget == Widget.NETWORK || widget == Widget.MEMORY || widget == Widget.STORAGE
                || widget == Widget.NOTIFICATIONS || widget == Widget.CALENDAR
                || widget == Widget.STEPS || widget == Widget.FULLSCREEN_WEATHER
                || widget == Widget.FULLSCREEN_MEDIA;
    }

    static boolean isFullscreenWidget(Widget widget) {
        return widget == Widget.FULLSCREEN_WEATHER || widget == Widget.FULLSCREEN_MEDIA;
    }

    private static void enforceExclusivePage(Context context, Widget changed, int requestedPage) {
        int target = requestedPage;
        if (isFullscreenWidget(changed)) {
            for (int candidate = 1; candidate <= MAX_PAGES; candidate++) {
                boolean occupied = false;
                for (Widget widget : Widget.values()) {
                    if (widget != changed && isWidgetEnabled(context, widget)
                            && loadPage(context, widget) == candidate) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) {
                    target = candidate;
                    break;
                }
            }
            savePageCount(context, Math.max(loadPageCount(context), target));
            prefs(context).edit().putInt(PAGE_PREFIX + changed.name(), target).apply();
            // The page count grows only when somebody is actually moved. It
            // used to grow either way, so switching a full-screen widget on
            // added an empty second page nobody had asked for.
            int fallback = firstSharablePage(context, changed);
            for (Widget widget : Widget.values()) {
                if (widget != changed && isWidgetEnabled(context, widget)
                        && loadPage(context, widget) == target) {
                    savePageCount(context, Math.max(loadPageCount(context), fallback));
                    prefs(context).edit().putInt(PAGE_PREFIX + widget.name(), fallback).apply();
                }
            }
        } else {
            for (Widget widget : Widget.values()) {
                if (isFullscreenWidget(widget) && isWidgetEnabled(context, widget)
                        && loadPage(context, widget) == target) {
                    int fallback = firstSharablePage(context, null);
                    savePageCount(context, Math.max(loadPageCount(context), fallback));
                    prefs(context).edit().putInt(PAGE_PREFIX + changed.name(), fallback).apply();
                    break;
                }
            }
        }
    }

    /**
     * The lowest page no full-screen widget has taken for itself.
     *
     * <p>Displaced widgets used to be sent to page 1 or 2 whichever the other
     * was, without looking at who was already there, so with a full-screen
     * widget on each of those pages an ordinary widget landed on one of them
     * and was never drawn again - while the builder went on listing it with
     * all of its controls.
     *
     * @param except a widget to ignore, for when it is the one being placed
     */
    private static int firstSharablePage(Context context, @Nullable Widget except) {
        for (int page = 1; page <= MAX_PAGES; page++) {
            boolean owned = false;
            for (Widget widget : Widget.values()) {
                if (widget != except && isFullscreenWidget(widget)
                        && isWidgetEnabled(context, widget)
                        && loadPage(context, widget) == page) {
                    owned = true;
                    break;
                }
            }
            if (!owned) return page;
        }
        return MAX_PAGES;
    }

    static boolean isExtraEnabled(Context context, Widget widget) {
        return prefs(context).getStringSet(EXTRA_ENABLED, Collections.emptySet()).contains(widget.name());
    }

    static void setExtraEnabled(Context context, Widget widget, boolean enabled) {
        Set<String> values = new HashSet<>(prefs(context).getStringSet(EXTRA_ENABLED, Collections.emptySet()));
        if (enabled) values.add(widget.name()); else values.remove(widget.name());
        prefs(context).edit().putStringSet(EXTRA_ENABLED, values).apply();
    }

    static void reset(Context context) { prefs(context).edit().clear().apply(); }

    static Map<String, String> exportState(Context context) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Integer) result.put(entry.getKey(), "i:" + value);
            else if (value instanceof String) result.put(entry.getKey(), "s:" + value);
            else if (value instanceof Set) {
                @SuppressWarnings("unchecked") Set<String> values = (Set<String>) value;
                result.put(entry.getKey(), "t:" + String.join(",", values));
            }
        }
        return result;
    }

    static void importState(Context context, Map<String, String> state) {
        SharedPreferences.Editor editor = prefs(context).edit().clear();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            String value = entry.getValue();
            if (value.startsWith("i:")) {
                try { editor.putInt(entry.getKey(), Integer.parseInt(value.substring(2))); }
                catch (NumberFormatException ignored) {}
            } else if (value.startsWith("s:")) editor.putString(entry.getKey(), value.substring(2));
            else if (value.startsWith("t:")) {
                Set<String> values = new HashSet<>();
                if (value.length() > 2) Collections.addAll(values, value.substring(2).split(","));
                editor.putStringSet(entry.getKey(), values);
            }
        }
        editor.apply();
    }

    static <T extends Item> void sort(Context context, List<T> items) {
        List<Widget> order = loadOrder(context);
        EnumMap<Widget, Integer> positions = new EnumMap<>(Widget.class);
        for (int index = 0; index < order.size(); index++) positions.put(order.get(index), index);
        Collections.sort(items, Comparator.comparingInt(item -> positions.get(item.widget())));
    }

    interface Item { Widget widget(); }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
