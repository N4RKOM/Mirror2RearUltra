package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

/** The icons a widget row can carry; drawn as shapes by the panel view. */
enum WidgetIcon {
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
    ,CHARGE, SUNRISE, SUNSET, LEVEL
    ,SOUND_ON, VIBRATE, SOUND_OFF, DO_NOT_DISTURB, GLOBE
    ,UV, AIR, NETWORK_SPEED, MOON, COUNTDOWN, PROGRESS, PRESSURE;

    /** The weather icon for a WMO weather code; a cloud when there is none. */
    static WidgetIcon forWeather(@Nullable Integer codeValue) {
        if (codeValue == null) {
            return WEATHER_CLOUD;
        }
        int code = codeValue;
        if (code == 0) {
            return WEATHER_CLEAR;
        }
        if (code <= 3) {
            return WEATHER_CLOUD;
        }
        if (code == 45 || code == 48) {
            return WEATHER_FOG;
        }
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return WEATHER_RAIN;
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return WEATHER_SNOW;
        }
        if (code >= 95) {
            return WEATHER_STORM;
        }
        return WEATHER_CLOUD;
    }

    static WidgetIcon forSound(@Nullable SoundModeReading sound) {
        if (sound == null) return SOUND_ON;
        switch (sound.mode) {
            case VIBRATE: return VIBRATE;
            case SILENT: return SOUND_OFF;
            case DO_NOT_DISTURB: return DO_NOT_DISTURB;
            default: return SOUND_ON;
        }
    }
}
