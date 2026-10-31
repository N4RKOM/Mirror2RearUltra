package com.tpkarras.mirror2rearultra;

import android.content.Context;

/**
 * What the app knows about each widget as an item to be chosen: its name and
 * which group it is listed under.
 *
 * <p>The widgets tab, the builder and the add-widget gallery all name a widget
 * and none of them should keep its own copy of the list.
 */
final class WidgetCatalog {
    private WidgetCatalog() { }

    /** The headings widgets are listed under, in the order they are shown. */
    enum Group {
        TIME(R.string.dashboard_group_time),
        DEVICE(R.string.dashboard_group_device),
        SENSORS(R.string.dashboard_group_sensors),
        MEDIA(R.string.dashboard_group_media),
        OTHER(R.string.dashboard_group_other);

        final int title;

        Group(int title) {
            this.title = title;
        }
    }

    static Group groupOf(DashboardWidgetLayout.Widget widget) {
        switch (widget) {
            case CLOCK: case DATE: case WORLD_CLOCK: case COUNTDOWN: case PROGRESS:
            case NEXT_ALARM: case SESSION_TIMER: case TIMER: case CALENDAR:
                return Group.TIME;
            case BATTERY: case CHARGING: case SOUND_MODE: case TEMPERATURE: case MEMORY:
            case STORAGE: case NETWORK: case NETWORK_SPEED:
                return Group.DEVICE;
            case WEATHER: case FULLSCREEN_WEATHER: case SUN: case UV_INDEX: case AIR_QUALITY:
            case MOON: case COMPASS: case LEVEL: case SPEED: case ALTITUDE: case PRESSURE:
            case STEPS:
                return Group.SENSORS;
            case MEDIA: case FULLSCREEN_MEDIA: case NOTIFICATIONS: case LAST_NOTIFICATION:
                return Group.MEDIA;
            default:
                return Group.OTHER;
        }
    }

    /** The drawing shown for a widget on its gallery card. */
    static int icon(DashboardWidgetLayout.Widget widget) {
        switch (widget) {
            case CLOCK: return R.drawable.ic_widget_clock;
            case DATE: return R.drawable.ic_widget_date;
            case BATTERY: return R.drawable.ic_widget_battery;
            case TEMPERATURE: return R.drawable.ic_widget_temperature;
            case WEATHER: return R.drawable.ic_widget_weather;
            case NEXT_ALARM: return R.drawable.ic_widget_next_alarm;
            case MEDIA: return R.drawable.ic_widget_media;
            case COMPASS: return R.drawable.ic_widget_compass;
            case SPEED: return R.drawable.ic_widget_speed;
            case ALTITUDE: return R.drawable.ic_widget_altitude;
            case SESSION_TIMER: return R.drawable.ic_widget_session_timer;
            case ACTIVE_PROFILE: return R.drawable.ic_widget_active_profile;
            case CUSTOM_TEXT: return R.drawable.ic_widget_custom_text;
            case NETWORK: return R.drawable.ic_widget_network;
            case MEMORY: return R.drawable.ic_widget_memory;
            case STORAGE: return R.drawable.ic_widget_storage;
            case NOTIFICATIONS: return R.drawable.ic_widget_notifications;
            case CALENDAR: return R.drawable.ic_widget_calendar;
            case STEPS: return R.drawable.ic_widget_steps;
            case FULLSCREEN_WEATHER: return R.drawable.ic_widget_fullscreen_weather;
            case FULLSCREEN_MEDIA: return R.drawable.ic_widget_fullscreen_media;
            case LAST_NOTIFICATION: return R.drawable.ic_widget_last_notification;
            case TIMER: return R.drawable.ic_widget_timer;
            case CHARGING: return R.drawable.ic_widget_charging;
            case SUN: return R.drawable.ic_widget_sun;
            case LEVEL: return R.drawable.ic_widget_level;
            case SOUND_MODE: return R.drawable.ic_widget_sound_mode;
            case WORLD_CLOCK: return R.drawable.ic_widget_world_clock;
            case QR_CODE: return R.drawable.ic_widget_qr_code;
            case UV_INDEX: return R.drawable.ic_widget_uv_index;
            case AIR_QUALITY: return R.drawable.ic_widget_air_quality;
            case NETWORK_SPEED: return R.drawable.ic_widget_network_speed;
            case MOON: return R.drawable.ic_widget_moon;
            case COUNTDOWN: return R.drawable.ic_widget_countdown;
            case PROGRESS: return R.drawable.ic_widget_progress;
            case PRESSURE: return R.drawable.ic_widget_pressure;
            default: return R.drawable.ic_group_other;
        }
    }

    static String label(Context context, DashboardWidgetLayout.Widget widget) {
        int[] labels = {R.string.dashboard_widget_clock, R.string.dashboard_widget_date,
                R.string.dashboard_widget_battery, R.string.dashboard_widget_temperature,
                R.string.dashboard_widget_weather, R.string.dashboard_widget_next_alarm,
                R.string.dashboard_widget_media, R.string.dashboard_widget_compass,
                R.string.dashboard_widget_speed, R.string.dashboard_widget_altitude,
                R.string.dashboard_widget_session_timer, R.string.dashboard_widget_active_profile,
                R.string.dashboard_widget_custom_text, R.string.dashboard_widget_network,
                R.string.dashboard_widget_memory, R.string.dashboard_widget_storage,
                R.string.dashboard_widget_notifications, R.string.dashboard_widget_calendar,
                R.string.dashboard_widget_steps, R.string.dashboard_widget_fullscreen_weather,
                R.string.dashboard_widget_fullscreen_media,
                R.string.dashboard_widget_last_notification,
                R.string.dashboard_widget_timer, R.string.dashboard_widget_charging,
                R.string.dashboard_widget_sun, R.string.dashboard_widget_level,
                R.string.dashboard_widget_sound_mode, R.string.dashboard_widget_world_clock,
                R.string.dashboard_widget_qr_code, R.string.dashboard_widget_uv,
                R.string.dashboard_widget_air, R.string.dashboard_widget_net_speed,
                R.string.dashboard_widget_moon, R.string.dashboard_widget_countdown,
                R.string.dashboard_widget_progress, R.string.dashboard_widget_pressure};
        // Indexed by ordinal, so a widget added without a label here would
        // take the whole screen down rather than show a blank row.
        int index = widget.ordinal();
        return context.getString(
                index < labels.length ? labels[index] : R.string.dashboard_widgets_title);
    }

    /** "Page 2", or "Page 2 · Road" once it has a name. */
    static String pageLabel(Context context, int page) {
        String name = DashboardWidgetLayout.loadPageName(context, page);
        return name.isEmpty()
                ? context.getString(R.string.dashboard_builder_page_section, page)
                : context.getString(R.string.dashboard_page_label_named, page, name);
    }
}
