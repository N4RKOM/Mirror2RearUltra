package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

final class RearDashboardSnapshot {
    final long timestampMillis;
    final int batteryPercent;
    final int temperatureTenthsCelsius;
    final boolean charging;
    final String weatherPlace;
    final Integer weatherTemperatureCelsius;
    final Integer weatherCode;
    final boolean weatherLoading;
    final Long nextAlarmMillis;
    final String mediaTitle;
    final String mediaArtist;
    final boolean mediaPlaying;
    final Float headingDegrees;
    final Float speedMetersPerSecond;
    final Double altitudeMeters;
    final long sessionElapsedMillis;
    final String activeProfileName;
    final String networkSummary;
    final int memoryPercent;
    final int storagePercentFree;
    final String calendarTitle;
    final Long calendarStartMillis;
    final int stepsToday;
    /** The newest notification worth reading, empty when there is none. */
    final String notificationApp;
    final String notificationTitle;
    final String notificationText;
    /** Null unless the phone is on a charger. */
    @Nullable final ChargeReading charge;
    /** Null until the weather city's forecast has come in. */
    @Nullable final SunTimes.Next sun;
    /** Null while the widget is off, or the phone lies too flat to have a horizon. */
    @Nullable final LevelReading level;
    /** Null while the widget is off. */
    @Nullable final SoundModeReading sound;

    RearDashboardSnapshot(
            long timestampMillis,
            int batteryPercent,
            int temperatureTenthsCelsius,
            boolean charging,
            String weatherPlace,
            Integer weatherTemperatureCelsius,
            Integer weatherCode,
            boolean weatherLoading
    ) {
        this(
                timestampMillis,
                batteryPercent,
                temperatureTenthsCelsius,
                charging,
                weatherPlace,
                weatherTemperatureCelsius,
                weatherCode,
                weatherLoading,
                null,
                "",
                "",
                false,
                null,
                null,
                null,
                0L,
                "", "", -1, -1, "", null, -1, "", "", "", null, null, null, null
        );
    }

    RearDashboardSnapshot(
            long timestampMillis,
            int batteryPercent,
            int temperatureTenthsCelsius,
            boolean charging,
            String weatherPlace,
            Integer weatherTemperatureCelsius,
            Integer weatherCode,
            boolean weatherLoading,
            Long nextAlarmMillis,
            String mediaTitle,
            String mediaArtist,
            boolean mediaPlaying,
            Float headingDegrees,
            Float speedMetersPerSecond,
            Double altitudeMeters,
            long sessionElapsedMillis,
            String activeProfileName
    ) {
        this(timestampMillis, batteryPercent, temperatureTenthsCelsius, charging, weatherPlace,
                weatherTemperatureCelsius, weatherCode, weatherLoading, nextAlarmMillis,
                mediaTitle, mediaArtist, mediaPlaying, headingDegrees, speedMetersPerSecond,
                altitudeMeters,
                sessionElapsedMillis, activeProfileName, "", -1, -1, "", null, -1,
                "", "", "", null, null, null, null);
    }

    RearDashboardSnapshot(
            long timestampMillis, int batteryPercent, int temperatureTenthsCelsius,
            boolean charging, String weatherPlace, Integer weatherTemperatureCelsius,
            Integer weatherCode, boolean weatherLoading, Long nextAlarmMillis,
            String mediaTitle, String mediaArtist, boolean mediaPlaying, Float headingDegrees,
            Float speedMetersPerSecond, Double altitudeMeters, long sessionElapsedMillis,
            String activeProfileName, String networkSummary, int memoryPercent,
            int storagePercentFree, String calendarTitle, Long calendarStartMillis,
            int stepsToday, String notificationApp, String notificationTitle,
            String notificationText, @Nullable ChargeReading charge,
            @Nullable SunTimes.Next sun, @Nullable LevelReading level,
            @Nullable SoundModeReading sound
    ) {
        this.timestampMillis = timestampMillis;
        this.batteryPercent = batteryPercent;
        this.temperatureTenthsCelsius = temperatureTenthsCelsius;
        this.charging = charging;
        this.weatherPlace = weatherPlace;
        this.weatherTemperatureCelsius = weatherTemperatureCelsius;
        this.weatherCode = weatherCode;
        this.weatherLoading = weatherLoading;
        this.nextAlarmMillis = nextAlarmMillis;
        this.mediaTitle = mediaTitle == null ? "" : mediaTitle;
        this.mediaArtist = mediaArtist == null ? "" : mediaArtist;
        this.mediaPlaying = mediaPlaying;
        this.headingDegrees = headingDegrees;
        this.speedMetersPerSecond = speedMetersPerSecond;
        this.altitudeMeters = altitudeMeters;
        this.sessionElapsedMillis = Math.max(0L, sessionElapsedMillis);
        this.activeProfileName = activeProfileName == null ? "" : activeProfileName;
        this.networkSummary = networkSummary == null ? "" : networkSummary;
        this.memoryPercent = memoryPercent;
        this.storagePercentFree = storagePercentFree;
        this.calendarTitle = calendarTitle == null ? "" : calendarTitle;
        this.calendarStartMillis = calendarStartMillis;
        this.stepsToday = stepsToday;
        this.notificationApp = notificationApp == null ? "" : notificationApp;
        this.notificationTitle = notificationTitle == null ? "" : notificationTitle;
        this.notificationText = notificationText == null ? "" : notificationText;
        this.charge = charge;
        this.sun = sun;
        this.level = level;
        this.sound = sound;
    }
}
