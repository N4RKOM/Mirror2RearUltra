package com.tpkarras.mirror2rearultra;

import java.util.Objects;

final class DashboardSettings {
    enum Layout {
        STACKED,
        CORNERS,
        COMPACT
    }

    enum Theme {
        SYSTEM,
        LIGHT,
        DARK,
        ACCENT
    }

    static final int MIN_TEXT_SCALE = 70;
    static final int MAX_TEXT_SCALE = 160;
    static final int MIN_BACKGROUND_OPACITY = 0;
    static final int MAX_BACKGROUND_OPACITY = 100;
    static final int MAX_CITY_LENGTH = 80;
    static final int MAX_CUSTOM_TEXT_LENGTH = 60;
    static final int MIN_IMAGE_OPACITY = 10;
    static final int MAX_IMAGE_OPACITY = 100;

    final RearContentMode contentMode;
    final Layout layout;
    final Theme theme;
    final boolean showClock;
    final boolean showDate;
    final boolean showBattery;
    final boolean showTemperature;
    final boolean showWeather;
    final boolean showNextAlarm;
    final boolean showMedia;
    final boolean showCompass;
    final boolean showSpeed;
    final boolean showAltitude;
    final boolean showSessionTimer;
    final boolean showActiveProfile;
    final boolean showCustomText;
    final boolean showCustomImage;
    final String weatherCity;
    final String customText;
    final int textScalePercent;
    final int backgroundOpacityPercent;
    final int customImageOpacityPercent;

    DashboardSettings(
            RearContentMode contentMode,
            Layout layout,
            Theme theme,
            boolean showClock,
            boolean showDate,
            boolean showBattery,
            boolean showTemperature,
            boolean showWeather,
            String weatherCity,
            int textScalePercent,
            int backgroundOpacityPercent
    ) {
        this(
                contentMode,
                layout,
                theme,
                showClock,
                showDate,
                showBattery,
                showTemperature,
                showWeather,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                weatherCity,
                "",
                textScalePercent,
                backgroundOpacityPercent,
                false,
                70
        );
    }

    DashboardSettings(
            RearContentMode contentMode,
            Layout layout,
            Theme theme,
            boolean showClock,
            boolean showDate,
            boolean showBattery,
            boolean showTemperature,
            boolean showWeather,
            boolean showNextAlarm,
            boolean showMedia,
            boolean showCompass,
            boolean showSpeed,
            boolean showAltitude,
            boolean showSessionTimer,
            boolean showActiveProfile,
            boolean showCustomText,
            String weatherCity,
            String customText,
            int textScalePercent,
            int backgroundOpacityPercent
    ) {
        this(contentMode, layout, theme, showClock, showDate, showBattery, showTemperature,
                showWeather, showNextAlarm, showMedia, showCompass, showSpeed, showAltitude,
                showSessionTimer, showActiveProfile, showCustomText, weatherCity, customText,
                textScalePercent, backgroundOpacityPercent, false, 70);
    }

    DashboardSettings(
            RearContentMode contentMode, Layout layout, Theme theme,
            boolean showClock, boolean showDate, boolean showBattery,
            boolean showTemperature, boolean showWeather, boolean showNextAlarm,
            boolean showMedia, boolean showCompass, boolean showSpeed, boolean showAltitude,
            boolean showSessionTimer, boolean showActiveProfile, boolean showCustomText,
            String weatherCity, String customText, int textScalePercent,
            int backgroundOpacityPercent, boolean showCustomImage, int customImageOpacityPercent
    ) {
        this.contentMode = contentMode == null ? RearContentMode.MIRROR : contentMode;
        this.layout = layout == null ? Layout.STACKED : layout;
        this.theme = theme == null ? Theme.SYSTEM : theme;
        this.showClock = showClock;
        this.showDate = showDate;
        this.showBattery = showBattery;
        this.showTemperature = showTemperature;
        this.showWeather = showWeather;
        this.showNextAlarm = showNextAlarm;
        this.showMedia = showMedia;
        this.showCompass = showCompass;
        this.showSpeed = showSpeed;
        this.showAltitude = showAltitude;
        this.showSessionTimer = showSessionTimer;
        this.showActiveProfile = showActiveProfile;
        this.showCustomText = showCustomText;
        this.showCustomImage = showCustomImage;
        this.weatherCity = normalizeCity(weatherCity);
        this.customText = normalizeCustomText(customText);
        this.textScalePercent = clamp(textScalePercent, MIN_TEXT_SCALE, MAX_TEXT_SCALE);
        this.backgroundOpacityPercent = clamp(
                backgroundOpacityPercent,
                MIN_BACKGROUND_OPACITY,
                MAX_BACKGROUND_OPACITY
        );
        this.customImageOpacityPercent = clamp(
                customImageOpacityPercent, MIN_IMAGE_OPACITY, MAX_IMAGE_OPACITY);
    }

    static DashboardSettings defaults() {
        return new DashboardSettings(
                RearContentMode.MIRROR,
                Layout.STACKED,
                Theme.SYSTEM,
                true,
                true,
                true,
                true,
                false,
                "",
                100,
                55
        );
    }

    DashboardSettings withContentMode(RearContentMode mode) {
        return new DashboardSettings(
                mode,
                layout,
                theme,
                showClock,
                showDate,
                showBattery,
                showTemperature,
                showWeather,
                showNextAlarm,
                showMedia,
                showCompass,
                showSpeed,
                showAltitude,
                showSessionTimer,
                showActiveProfile,
                showCustomText,
                weatherCity,
                customText,
                textScalePercent,
                backgroundOpacityPercent,
                showCustomImage,
                customImageOpacityPercent
        );
    }

    DashboardSettings withLayout(Layout value) {
        return copy(value, showClock, showDate, showBattery, showTemperature, showWeather,
                showNextAlarm, showMedia, showCompass, showSpeed, showAltitude,
                showSessionTimer, showActiveProfile, showCustomText);
    }

    /**
     * The weather city and the custom text, which belong to the two widgets
     * that carry a value. Kept as one wither because both are edited on the
     * widget picker, away from the screen that writes the rest of these fields.
     */
    DashboardSettings withTexts(String city, String text) {
        return new DashboardSettings(contentMode, layout, theme, showClock, showDate, showBattery,
                showTemperature, showWeather, showNextAlarm, showMedia, showCompass, showSpeed,
                showAltitude, showSessionTimer, showActiveProfile, showCustomText, city, text,
                textScalePercent, backgroundOpacityPercent, showCustomImage,
                customImageOpacityPercent);
    }

    DashboardSettings withWidget(DashboardWidgetLayout.Widget widget, boolean visible) {
        return copy(layout,
                widget == DashboardWidgetLayout.Widget.CLOCK ? visible : showClock,
                widget == DashboardWidgetLayout.Widget.DATE ? visible : showDate,
                widget == DashboardWidgetLayout.Widget.BATTERY ? visible : showBattery,
                widget == DashboardWidgetLayout.Widget.TEMPERATURE ? visible : showTemperature,
                widget == DashboardWidgetLayout.Widget.WEATHER ? visible : showWeather,
                widget == DashboardWidgetLayout.Widget.NEXT_ALARM ? visible : showNextAlarm,
                widget == DashboardWidgetLayout.Widget.MEDIA ? visible : showMedia,
                widget == DashboardWidgetLayout.Widget.COMPASS ? visible : showCompass,
                widget == DashboardWidgetLayout.Widget.SPEED ? visible : showSpeed,
                widget == DashboardWidgetLayout.Widget.ALTITUDE ? visible : showAltitude,
                widget == DashboardWidgetLayout.Widget.SESSION_TIMER ? visible : showSessionTimer,
                widget == DashboardWidgetLayout.Widget.ACTIVE_PROFILE ? visible : showActiveProfile,
                widget == DashboardWidgetLayout.Widget.CUSTOM_TEXT ? visible : showCustomText);
    }

    boolean isWidgetVisible(DashboardWidgetLayout.Widget widget) {
        switch (widget) {
            case CLOCK: return showClock; case DATE: return showDate; case BATTERY: return showBattery;
            case TEMPERATURE: return showTemperature; case WEATHER: return showWeather;
            case NEXT_ALARM: return showNextAlarm; case MEDIA: return showMedia;
            case COMPASS: return showCompass; case SPEED: return showSpeed; case ALTITUDE: return showAltitude;
            case SESSION_TIMER: return showSessionTimer; case ACTIVE_PROFILE: return showActiveProfile;
            case CUSTOM_TEXT: return showCustomText; default: return false;
        }
    }

    private DashboardSettings copy(Layout newLayout, boolean clock, boolean date, boolean battery,
            boolean temperature, boolean weather, boolean nextAlarm, boolean media,
            boolean compass, boolean speed, boolean altitude, boolean sessionTimer,
            boolean activeProfile, boolean customTextEnabled) {
        return new DashboardSettings(contentMode, newLayout, theme, clock, date, battery,
                temperature, weather, nextAlarm, media, compass, speed, altitude, sessionTimer,
                activeProfile, customTextEnabled, weatherCity, customText, textScalePercent,
                backgroundOpacityPercent, showCustomImage, customImageOpacityPercent);
    }

    private static String normalizeCity(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= MAX_CITY_LENGTH
                ? normalized
                : normalized.substring(0, MAX_CITY_LENGTH).trim();
    }

    private static String normalizeCustomText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= MAX_CUSTOM_TEXT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_CUSTOM_TEXT_LENGTH).trim();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DashboardSettings)) {
            return false;
        }
        DashboardSettings that = (DashboardSettings) other;
        return showClock == that.showClock
                && showDate == that.showDate
                && showBattery == that.showBattery
                && showTemperature == that.showTemperature
                && showWeather == that.showWeather
                && showNextAlarm == that.showNextAlarm
                && showMedia == that.showMedia
                && showCompass == that.showCompass
                && showSpeed == that.showSpeed
                && showAltitude == that.showAltitude
                && showSessionTimer == that.showSessionTimer
                && showActiveProfile == that.showActiveProfile
                && showCustomText == that.showCustomText
                && showCustomImage == that.showCustomImage
                && textScalePercent == that.textScalePercent
                && backgroundOpacityPercent == that.backgroundOpacityPercent
                && customImageOpacityPercent == that.customImageOpacityPercent
                && contentMode == that.contentMode
                && layout == that.layout
                && theme == that.theme
                && weatherCity.equals(that.weatherCity)
                && customText.equals(that.customText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                contentMode,
                layout,
                theme,
                showClock,
                showDate,
                showBattery,
                showTemperature,
                showWeather,
                showNextAlarm,
                showMedia,
                showCompass,
                showSpeed,
                showAltitude,
                showSessionTimer,
                showActiveProfile,
                showCustomText,
                weatherCity,
                customText,
                textScalePercent,
                backgroundOpacityPercent,
                showCustomImage,
                customImageOpacityPercent
        );
    }
}
