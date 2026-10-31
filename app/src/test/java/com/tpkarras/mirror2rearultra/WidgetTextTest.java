package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import com.tpkarras.mirror2rearultra.DashboardWidgetLayout.Variant;

import org.junit.Test;

import java.util.Date;
import java.util.Locale;

/** What each widget says in each of its three forms. */
public class WidgetTextTest {
    /** Names the string and its arguments, so a test sees which word was asked for. */
    private static final WidgetText.Words WORDS = (id, arguments) -> {
        StringBuilder text = new StringBuilder("#").append(id);
        for (Object argument : arguments) text.append('|').append(argument);
        return text.toString();
    };
    private static final Locale EN = Locale.US;
    /** 2026-03-05 14:07:09 UTC, formatted in the zone below. */
    private static final long NOON = 1_772_719_629_000L;

    private static Date at() {
        return new Date(NOON);
    }

    @Test
    public void clockHasThreeForms() {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        try {
            assertEquals("14:07", WidgetText.clock(Variant.DEFAULT, true, at(), EN));
            assertEquals("14\n07", WidgetText.clock(Variant.ALTERNATE, true, at(), EN));
            assertEquals("14:07:09", WidgetText.clock(Variant.DETAILED, true, at(), EN));
            assertEquals("2:07", WidgetText.clock(Variant.DEFAULT, false, at(), EN));
            assertEquals("2\n07", WidgetText.clock(Variant.ALTERNATE, false, at(), EN));
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @Test
    public void dateHasThreeForms() {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        try {
            assertEquals("5 Mar", WidgetText.date(Variant.DEFAULT, at(), EN));
            assertEquals("05.03", WidgetText.date(Variant.ALTERNATE, at(), EN));
            assertEquals("05.03.2026", WidgetText.date(Variant.DETAILED, at(), EN));
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @Test
    public void batteryDetailedNamesBothStates() {
        assertEquals("#" + R.string.dashboard_battery + "|80",
                WidgetText.battery(Variant.DEFAULT, 80, false, WORDS));
        assertEquals("#" + R.string.dashboard_battery_charging + "|80",
                WidgetText.battery(Variant.DEFAULT, 80, true, WORDS));
        assertEquals("80\n%", WidgetText.battery(Variant.ALTERNATE, 80, false, WORDS));
        assertEquals("#" + R.string.dashboard_battery + "|80\n#"
                        + R.string.dashboard_variant_battery,
                WidgetText.battery(Variant.DETAILED, 80, false, WORDS));
        assertEquals("#" + R.string.dashboard_battery_charging + "|80\n#"
                        + R.string.dashboard_variant_charging,
                WidgetText.battery(Variant.DETAILED, 80, true, WORDS));
    }

    @Test
    public void temperatureRoundsAndSpellsTheUnit() {
        assertEquals("37°", WidgetText.temperature(Variant.ALTERNATE, 36.6f, "°C", EN, WORDS));
        assertEquals("36.6 °C", WidgetText.temperature(Variant.DETAILED, 36.6f, "°C", EN, WORDS));
        assertEquals("#" + R.string.dashboard_device_temperature + "|36.6",
                WidgetText.temperature(Variant.DEFAULT, 36.6f, "°C", EN, WORDS));
    }

    @Test
    public void compassStacksOnlyInTheDetailedForm() {
        assertEquals("90° E", WidgetText.compass(Variant.DEFAULT, 90.4f, "E"));
        assertEquals("E", WidgetText.compass(Variant.ALTERNATE, 90.4f, "E"));
        assertEquals("90°\nE", WidgetText.compass(Variant.DETAILED, 90.4f, "E"));
    }

    @Test
    public void speedConvertsToMilesOnRequest() {
        // 10 m/s is 36 km/h and 22 mph.
        assertEquals("36", WidgetText.speed(Variant.ALTERNATE, 10f, false, WORDS));
        assertEquals("22", WidgetText.speed(Variant.ALTERNATE, 10f, true, WORDS));
        assertEquals("36\n#" + R.string.dashboard_speed_unit,
                WidgetText.speed(Variant.DETAILED, 10f, false, WORDS));
        assertEquals("22\n#" + R.string.dashboard_speed_unit_imperial,
                WidgetText.speed(Variant.DETAILED, 10f, true, WORDS));
        assertEquals("#" + R.string.dashboard_speed_short_imperial + "|22",
                WidgetText.speed(Variant.DEFAULT, 10f, true, WORDS));
    }

    @Test
    public void altitudeConvertsToFeetOnRequest() {
        assertEquals("100", WidgetText.altitude(Variant.ALTERNATE, 100d, false, WORDS));
        assertEquals("328", WidgetText.altitude(Variant.ALTERNATE, 100d, true, WORDS));
        assertEquals("#" + R.string.dashboard_altitude_short + "|100",
                WidgetText.altitude(Variant.DEFAULT, 100d, false, WORDS));
    }

    @Test
    public void elapsedTimeStacksOrShowsHours() {
        assertEquals("01:05", WidgetText.elapsed(Variant.DEFAULT, 65_000L));
        assertEquals("01\n05", WidgetText.elapsed(Variant.ALTERNATE, 65_000L));
        assertEquals("0:01:05", WidgetText.elapsed(Variant.DETAILED, 65_000L));
        assertEquals("1:01:05", WidgetText.elapsed(Variant.DEFAULT, 3_665_000L));
        // Never negative, whatever a clock does.
        assertEquals("00:00", WidgetText.elapsed(Variant.DEFAULT, -5_000L));
    }

    @Test
    public void textUppercasesOrBreaksNearTheMiddle() {
        assertEquals("HELLO WORLD", WidgetText.text(Variant.ALTERNATE, "hello world", EN));
        assertEquals("hello\nworld", WidgetText.text(Variant.DETAILED, "hello world", EN));
        assertEquals("one two\nthree", WidgetText.text(Variant.DETAILED, "one two three", EN));
        assertEquals("single", WidgetText.text(Variant.DETAILED, "single", EN));
        assertEquals("hello world", WidgetText.text(Variant.DEFAULT, "hello world", EN));
    }

    @Test
    public void percentHasThreeForms() {
        assertEquals("42%", WidgetText.percent(42, Variant.DEFAULT, "RAM"));
        assertEquals("42", WidgetText.percent(42, Variant.ALTERNATE, "RAM"));
        assertEquals("42%\nRAM", WidgetText.percent(42, Variant.DETAILED, "RAM"));
    }

    @Test
    public void notificationCountIsCappedInTheCompactForm() {
        assertEquals("12", WidgetText.notifications(Variant.DEFAULT, 12, WORDS));
        assertEquals("9+", WidgetText.notifications(Variant.ALTERNATE, 12, WORDS));
        assertEquals("9", WidgetText.notifications(Variant.ALTERNATE, 9, WORDS));
        assertEquals("12\n#" + R.string.dashboard_variant_notifications,
                WidgetText.notifications(Variant.DETAILED, 12, WORDS));
    }

    @Test
    public void lastNotificationNamesTheAppOnlyInTheCompactForm() {
        assertEquals("Ann", WidgetText.lastNotification(Variant.DEFAULT, "Chat", "Ann", "hi"));
        assertEquals("Chat", WidgetText.lastNotification(Variant.ALTERNATE, "Chat", "Ann", "hi"));
        assertEquals("Chat · Ann\nhi",
                WidgetText.lastNotification(Variant.DETAILED, "Chat", "Ann", "hi"));
        // Whatever is missing, the rest still reads.
        assertEquals("hi", WidgetText.lastNotification(Variant.DEFAULT, "", "", "hi"));
        assertEquals("Chat", WidgetText.lastNotification(Variant.DEFAULT, "Chat", "", "hi"));
        assertEquals("Ann", WidgetText.lastNotification(Variant.ALTERNATE, "", "Ann", "hi"));
        assertEquals("Chat\nhi", WidgetText.lastNotification(Variant.DETAILED, "Chat", "", "hi"));
    }

    @Test
    public void stepsGroupDigitsByStackingThem() {
        assertEquals("4820", WidgetText.steps(Variant.DEFAULT, 4820, EN, WORDS));
        assertEquals("4\n820", WidgetText.steps(Variant.ALTERNATE, 4820, EN, WORDS));
        assertEquals("4820\n#" + R.string.dashboard_variant_steps,
                WidgetText.steps(Variant.DETAILED, 4820, EN, WORDS));
    }

    @Test
    public void stepsKeepNonLatinDigits() {
        // A pattern naming only 0-9 would have emptied every line.
        Locale arabic = new Locale.Builder().setLanguage("ar").setRegion("EG")
                .setUnicodeLocaleKeyword("nu", "arab").build();
        String stacked = WidgetText.steps(Variant.ALTERNATE, 4820, arabic, WORDS);
        assertEquals(true, stacked.replace("\n", "").length() >= 4);
    }

    @Test
    public void levelDropsTheSignAndAddsTiltWhenDetailed() {
        assertEquals("3°", WidgetText.level(Variant.DEFAULT, -3.2f, 10f, EN, WORDS));
        assertEquals("3.2°", WidgetText.level(Variant.ALTERNATE, -3.2f, 10f, EN, WORDS));
        assertEquals("3°\n#" + R.string.dashboard_level_tilt + "|10",
                WidgetText.level(Variant.DETAILED, -3.2f, 10f, EN, WORDS));
    }

    private static final long HOUR = 3_600_000L;

    @Test
    public void calendarTitleReplacesTheTimeInTheCompactForm() {
        inUtc(() -> {
            long before = NOON - HOUR;
            assertEquals("14:07  Stand-up", WidgetText.calendar(Variant.DEFAULT, true, NOON,
                    NOON + HOUR, before, "Stand-up", 0, EN, WORDS));
            assertEquals("Stand-up", WidgetText.calendar(Variant.ALTERNATE, true, NOON,
                    NOON + HOUR, before, "Stand-up", 0, EN, WORDS));
            assertEquals("14:07\nStand-up", WidgetText.calendar(Variant.DETAILED, true, NOON,
                    NOON + HOUR, before, "Stand-up", 0, EN, WORDS));
            // An event without a title is just its time, in every form.
            assertEquals("14:07", WidgetText.calendar(Variant.ALTERNATE, true, NOON,
                    NOON + HOUR, before, "", 0, EN, WORDS));
            return null;
        });
    }

    @Test
    public void calendarOngoingEventReadsUntilItsEndAndCountsTheRest() {
        inUtc(() -> {
            assertEquals(w(R.string.dashboard_calendar_until, "15:07") + "  Stand-up",
                    WidgetText.calendar(Variant.DEFAULT, true, NOON, NOON + HOUR, NOON + 1,
                            "Stand-up", 0, EN, WORDS));
            assertEquals("14:07  Stand-up " + w(R.string.dashboard_calendar_more, 2),
                    WidgetText.calendar(Variant.DEFAULT, true, NOON, NOON + HOUR, NOON - 1,
                            "Stand-up", 2, EN, WORDS));
            return null;
        });
    }

    @Test
    public void clockCanCarryTheDateOrSmallSeconds() {
        inUtc(() -> {
            assertEquals("14:07\n" + WidgetText.SMALL + "5 Mar",
                    WidgetText.clock(Variant.FOURTH, true, at(), EN, "d MMM"));
            assertEquals("14:07\n" + WidgetText.SMALL + "09",
                    WidgetText.clock(Variant.FIFTH, true, at(), EN, "d MMM"));
            return null;
        });
    }

    @Test
    public void dateCanNameTheWeekday() {
        inUtc(() -> {
            assertEquals("Thursday\n" + WidgetText.SMALL + "5 Mar",
                    WidgetText.date(Variant.FOURTH, at(), EN, "d MMM"));
            assertEquals("Mar 5", WidgetText.date(Variant.DEFAULT, at(), EN, "MMM d"));
            return null;
        });
    }

    @Test
    public void lastNotificationKeepsTheMessageToItselfUnlessAsked() {
        assertEquals("Anna · Hi",
                WidgetText.lastNotification(Variant.DETAILED, "Anna", "Hi", "secret", false));
        assertEquals("Anna · Hi\nsecret",
                WidgetText.lastNotification(Variant.DETAILED, "Anna", "Hi", "secret", true));
    }

    private static String w(int id, Object... arguments) {
        return WORDS.get(id, arguments);
    }

    private static <T> T inUtc(java.util.function.Supplier<T> body) {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        try {
            return body.get();
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @Test
    public void durationRoundsUpAndSwitchesToHours() {
        assertEquals(w(R.string.dashboard_duration_minutes, 0L), WidgetText.duration(0L, WORDS));
        assertEquals(w(R.string.dashboard_duration_minutes, 1L), WidgetText.duration(1L, WORDS));
        assertEquals(w(R.string.dashboard_duration_minutes, 59L),
                WidgetText.duration(59L * 60_000L, WORDS));
        assertEquals(w(R.string.dashboard_duration_hours, 1L, 30L),
                WidgetText.duration(90L * 60_000L, WORDS));
        assertEquals(w(R.string.dashboard_duration_minutes, 0L), WidgetText.duration(-5L, WORDS));
    }

    @Test
    public void weatherFallsBackToTheOrdinaryLine() {
        assertEquals("12°", WidgetText.weather(Variant.DEFAULT, "12°", 12, "°C", "Oslo"));
        assertEquals("12\n°C", WidgetText.weather(Variant.ALTERNATE, "12°", 12, "°C", "Oslo"));
        assertEquals("12°\nOslo", WidgetText.weather(Variant.DETAILED, "12°", 12, "°C", "Oslo"));
        // Still loading, or no place yet: the line alone.
        assertEquals("…", WidgetText.weather(Variant.ALTERNATE, "…", null, "°C", ""));
        assertEquals("12°", WidgetText.weather(Variant.DETAILED, "12°", 12, "°C", ""));
        assertEquals("", WidgetText.weather(Variant.DETAILED, "", 12, "°C", "Oslo"));
    }

    @Test
    public void mediaUsesWhicheverNameThereIs() {
        assertEquals("Song", WidgetText.media(Variant.DEFAULT, "Song", "Band"));
        assertEquals("Band", WidgetText.media(Variant.ALTERNATE, "Song", "Band"));
        assertEquals("Song\nBand", WidgetText.media(Variant.DETAILED, "Song", "Band"));
        assertEquals("Song", WidgetText.media(Variant.ALTERNATE, "Song", ""));
        assertEquals("Song", WidgetText.media(Variant.DETAILED, "Song", ""));
        assertEquals("Band", WidgetText.media(Variant.DEFAULT, "", "Band"));
    }

    @Test
    public void soundCompactShowsVolumeOnlyWhileRinging() {
        assertEquals("", WidgetText.sound(Variant.DEFAULT, null, WORDS));
        SoundModeReading ringing = new SoundModeReading(SoundModeReading.Mode.SOUND, 50);
        SoundModeReading silent = new SoundModeReading(SoundModeReading.Mode.SILENT, 50);
        assertEquals(w(R.string.dashboard_sound_on), WidgetText.sound(Variant.DEFAULT, ringing, WORDS));
        assertEquals("50%", WidgetText.sound(Variant.ALTERNATE, ringing, WORDS));
        assertEquals(w(R.string.dashboard_sound_silent),
                WidgetText.sound(Variant.ALTERNATE, silent, WORDS));
        assertEquals(w(R.string.dashboard_sound_silent) + "\n"
                        + w(R.string.dashboard_sound_volume, 50),
                WidgetText.sound(Variant.DETAILED, silent, WORDS));
    }

    @Test
    public void sunGivesTheNextEventThePairOrTheWait() {
        String[] seen = inUtc(() -> {
            SunTimes.Next next = new SunTimes.Next(NOON, NOON + 4L * 3_600_000L, true);
            long ninetyBefore = NOON - 90L * 60_000L;
            return new String[]{
                    WidgetText.sun(Variant.DEFAULT, next, ninetyBefore, true, EN, WORDS),
                    WidgetText.sun(Variant.ALTERNATE, next, ninetyBefore, true, EN, WORDS),
                    WidgetText.sun(Variant.DETAILED, next, ninetyBefore, true, EN, WORDS),
                    WidgetText.sun(Variant.ALTERNATE, new SunTimes.Next(
                            SunTimes.NONE, NOON, false), ninetyBefore, true, EN, WORDS),
                    WidgetText.sun(Variant.DEFAULT, null, ninetyBefore, true, EN, WORDS)};
        });
        assertEquals("14:07", seen[0]);
        assertEquals("14:07\n18:07", seen[1]);
        assertEquals("14:07\n" + w(R.string.dashboard_sun_sunrise_in,
                w(R.string.dashboard_duration_hours, 1L, 30L)), seen[2]);
        // No sunrise today: the compact form has no pair to give.
        assertEquals("14:07", seen[3]);
        assertEquals("", seen[4]);
    }

    @Test
    public void chargeShowsPowerThenTimeLeft() {
        ChargeReading reading = ChargeReading.of(-2_000_000, 4_000, 1_800_000L, 2_000_000, 50, false);
        String power = w(R.string.dashboard_charge_watts, "8.0");
        String left = w(R.string.dashboard_duration_minutes, 30L);
        assertEquals(power + " · " + left,
                WidgetText.charge(Variant.DEFAULT, reading, NOON, true, EN, WORDS));
        assertEquals(power + "\n" + left,
                WidgetText.charge(Variant.ALTERNATE, reading, NOON, true, EN, WORDS));
        assertEquals("", WidgetText.charge(Variant.DEFAULT, null, NOON, true, EN, WORDS));
        String detailed = inUtc(() ->
                WidgetText.charge(Variant.DETAILED, reading, NOON, true, EN, WORDS));
        assertEquals(power + " · " + w(R.string.dashboard_charge_amps, "2.0") + "\n"
                + w(R.string.dashboard_charge_full_at, "14:37"), detailed);
    }

    @Test
    public void chargeWithoutAnEstimateShowsPowerAlone() {
        ChargeReading reading = ChargeReading.of(-2_000_000, 4_000, -1L, 0, 50, false);
        String power = w(R.string.dashboard_charge_watts, "8.0");
        // No estimate and no charge counter to make one from.
        String value = WidgetText.charge(Variant.ALTERNATE, reading, NOON, true, EN, WORDS);
        assertEquals(true, value.startsWith(power));
    }

    @Test
    public void uvAndAirNameTheirBandWhenDetailed() {
        assertEquals(w(R.string.dashboard_uv_value, "5"), WidgetText.uv(Variant.DEFAULT, 4.6f, WORDS));
        assertEquals("5", WidgetText.uv(Variant.ALTERNATE, 4.6f, WORDS));
        assertEquals(w(R.string.dashboard_uv_value, "5") + "\n" + w(R.string.dashboard_uv_moderate),
                WidgetText.uv(Variant.DETAILED, 4.6f, WORDS));
        assertEquals(w(R.string.dashboard_air_value, "34") + "\n" + w(R.string.dashboard_air_fair),
                WidgetText.air(Variant.DETAILED, 34, WORDS));
        assertEquals("34", WidgetText.air(Variant.ALTERNATE, 34, WORDS));
    }

    @Test
    public void networkSpeedShowsBothDirectionsFromTheCompactForm() {
        String second = w(R.string.dashboard_per_second, "1 MB");
        assertEquals("↓ " + second, WidgetText.networkSpeed(Variant.DEFAULT, "1 MB", "2 kB", WORDS));
        assertEquals("↓ 1 MB\n↑ 2 kB",
                WidgetText.networkSpeed(Variant.ALTERNATE, "1 MB", "2 kB", WORDS));
        assertEquals("↓ " + second + "\n↑ " + w(R.string.dashboard_per_second, "2 kB"),
                WidgetText.networkSpeed(Variant.DETAILED, "1 MB", "2 kB", WORDS));
    }

    @Test
    public void moonGivesAPercentageLit() {
        String compact = WidgetText.moon(Variant.ALTERNATE, NOON, WORDS);
        assertEquals(true, compact.matches("\\d{1,3}%"));
        String detailed = WidgetText.moon(Variant.DETAILED, NOON, WORDS);
        assertEquals(true, detailed.endsWith("\n" + compact));
    }

    @Test
    public void countdownReadsDaysAheadTodayAndAgo() {
        java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");
        long today = WidgetMath.dayNumber(NOON, utc);
        assertEquals(w(R.string.dashboard_countdown_days, 3L),
                WidgetText.countdown(Variant.DEFAULT, NOON, utc, today + 3, "", EN, WORDS));
        assertEquals("Trip · " + w(R.string.dashboard_countdown_days, 3L),
                WidgetText.countdown(Variant.DEFAULT, NOON, utc, today + 3, "Trip", EN, WORDS));
        assertEquals("3", WidgetText.countdown(Variant.ALTERNATE, NOON, utc, today + 3, "", EN, WORDS));
        assertEquals(w(R.string.dashboard_countdown_days, 3L) + "\nTrip",
                WidgetText.countdown(Variant.DETAILED, NOON, utc, today + 3, "Trip", EN, WORDS));
        assertEquals(w(R.string.dashboard_countdown_today),
                WidgetText.countdown(Variant.ALTERNATE, NOON, utc, today, "", EN, WORDS));
        assertEquals(w(R.string.dashboard_countdown_ago, 2L),
                WidgetText.countdown(Variant.DEFAULT, NOON, utc, today - 2, "", EN, WORDS));
        assertEquals("2", WidgetText.countdown(Variant.ALTERNATE, NOON, utc, today - 2, "", EN, WORDS));
    }

    @Test
    public void progressSwitchesFromHoursToDaysPastTwoDays() {
        String percent = "50%";
        assertEquals(w(R.string.dashboard_progress_day) + " " + percent,
                WidgetText.progress(Variant.DEFAULT, WidgetMath.Period.DAY, 0.5f, 0L, WORDS));
        assertEquals(percent,
                WidgetText.progress(Variant.ALTERNATE, WidgetMath.Period.YEAR, 0.5f, 0L, WORDS));
        assertEquals(percent + "\n" + w(R.string.dashboard_progress_left,
                        w(R.string.dashboard_duration_hours, 3L, 0L)),
                WidgetText.progress(Variant.DETAILED, WidgetMath.Period.DAY, 0.5f,
                        3L * 3_600_000L, WORDS));
        assertEquals(percent + "\n" + w(R.string.dashboard_progress_left,
                        w(R.string.dashboard_countdown_days, 3L)),
                WidgetText.progress(Variant.DETAILED, WidgetMath.Period.WEEK, 0.5f,
                        72L * 3_600_000L, WORDS));
    }

    @Test
    public void pressureConvertsAndNamesTheTrend() {
        assertEquals(w(R.string.dashboard_pressure_hpa, 1013),
                WidgetText.pressure(Variant.DEFAULT, 1013.2f, 1, false, EN, WORDS));
        assertEquals(w(R.string.dashboard_pressure_inhg, "29.92"),
                WidgetText.pressure(Variant.DEFAULT, 1013.2f, 1, true, EN, WORDS));
        assertEquals(w(R.string.dashboard_pressure_mmhg, 760),
                WidgetText.pressure(Variant.ALTERNATE, 1013.2f, 1, false, EN, WORDS));
        assertEquals(w(R.string.dashboard_pressure_hpa, 1013) + "\n"
                        + w(R.string.dashboard_pressure_rising),
                WidgetText.pressure(Variant.DETAILED, 1013.2f, 1, false, EN, WORDS));
        assertEquals(w(R.string.dashboard_pressure_hpa, 1013) + "\n"
                        + w(R.string.dashboard_pressure_falling),
                WidgetText.pressure(Variant.DETAILED, 1013.2f, -1, false, EN, WORDS));
        assertEquals(w(R.string.dashboard_pressure_hpa, 1013) + "\n"
                        + w(R.string.dashboard_pressure_steady),
                WidgetText.pressure(Variant.DETAILED, 1013.2f, 0, false, EN, WORDS));
    }

    @Test
    public void worldClockShowsTheirTimeAndTheCity() {
        java.util.TimeZone tokyo = java.util.TimeZone.getTimeZone("Asia/Tokyo");
        String[] seen = inUtc(() -> new String[]{
                WidgetText.worldClock(Variant.DEFAULT, tokyo, "Tokyo", NOON, true, EN, WORDS),
                WidgetText.worldClock(Variant.ALTERNATE, tokyo, "Tokyo", NOON, true, EN, WORDS),
                WidgetText.worldClock(Variant.DETAILED, tokyo, "Tokyo", NOON, true, EN, WORDS)});
        assertEquals("23:07  Tokyo", seen[0]);
        assertEquals("23:07", seen[1]);
        assertEquals("23:07\n" + w(R.string.dashboard_world_clock_offset, "Tokyo",
                WorldClock.differenceLabel(540)), seen[2]);
    }

    @Test
    public void alarmDetailedAddsTheDate() {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        try {
            assertEquals("14:07", WidgetText.alarm(Variant.DEFAULT, true, NOON, EN));
            assertEquals("14\n07", WidgetText.alarm(Variant.ALTERNATE, true, NOON, EN));
            assertEquals("05.03\n14:07", WidgetText.alarm(Variant.DETAILED, true, NOON, EN));
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }
}
