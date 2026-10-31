package com.tpkarras.mirror2rearultra;

import android.content.Context;

import androidx.annotation.Nullable;

import com.tpkarras.mirror2rearultra.DashboardWidgetLayout.Variant;
import com.tpkarras.mirror2rearultra.DashboardWidgetLayout.Widget;
import com.tpkarras.mirror2rearultra.WidgetSpec.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/** The registry: one spec for each widget. */
final class WidgetSpecs {
    private static final long SECOND = 1_000L;
    /** The network speed is a rate, so it is sampled at a steady pace. */
    static final long TRAFFIC_TICK_MILLIS = 2_000L;
    /**
     * How often the charging widget reads the current. The battery broadcast
     * only comes when the level or the temperature moves, which on a slow
     * charger is minutes apart.
     */
    static final long CHARGE_TICK_MILLIS = 3_000L;

    private interface Reader {
        @Nullable
        WidgetReading read(WidgetEnv env);
    }

    private interface Tick {
        long millis(Context context, boolean charging);
    }

    private static final class Simple implements WidgetSpec {
        private final Widget widget;
        private final Set<Source> sources;
        private final Tick tick;
        private final Reader reader;

        Simple(Widget widget, Set<Source> sources, Tick tick, Reader reader) {
            this.widget = widget;
            this.sources = sources;
            this.tick = tick;
            this.reader = reader;
        }

        @Override public Widget widget() { return widget; }

        @Override public Set<Source> sources() { return sources; }

        @Override
        public long tickMillis(Context context, boolean charging) {
            return tick == null ? 0L : tick.millis(context, charging);
        }

        @Override
        public WidgetReading read(WidgetEnv env) {
            return reader.read(env);
        }
    }

    // Declared before ALL: build() runs during static initialisation and reads it.
    private static final Set<Source> NONE = Collections.emptySet();
    private static final List<WidgetSpec> ALL = build();

    private WidgetSpecs() {}

    static List<WidgetSpec> all() {
        return ALL;
    }

    /**
     * Whether a widget is switched on, whichever store holds the switch: the
     * thirteen original ones live in the dashboard settings, the rest apart.
     */
    static boolean isOn(Context context, DashboardSettings settings, Widget widget) {
        return DashboardWidgetLayout.isExtraWidget(widget)
                ? DashboardWidgetLayout.isExtraEnabled(context, widget)
                : settings.isWidgetVisible(widget);
    }

    /** The union of what the widgets that are on read from the phone. */
    static Set<Source> sources(Context context, DashboardSettings settings) {
        Set<Source> result = EnumSet.noneOf(Source.class);
        for (WidgetSpec spec : ALL) {
            if (isOn(context, settings, spec.widget())) {
                result.addAll(spec.sources());
            }
        }
        return result;
    }

    /**
     * The shortest wait any widget that is on asks for, or {@code fallback}
     * when none asks for one.
     */
    static long tickMillis(Context context, DashboardSettings settings, boolean charging,
            long fallback) {
        long shortest = Long.MAX_VALUE;
        for (WidgetSpec spec : ALL) {
            if (!isOn(context, settings, spec.widget())) {
                continue;
            }
            long wanted = spec.tickMillis(context, charging);
            if (wanted > 0L) {
                shortest = Math.min(shortest, wanted);
            }
        }
        return shortest == Long.MAX_VALUE ? fallback : shortest;
    }

    private static Set<Source> of(Source source) {
        return EnumSet.of(source);
    }

    private static void add(List<WidgetSpec> list, Widget widget, Set<Source> sources,
            Tick tick, Reader reader) {
        list.add(new Simple(widget, sources, tick, reader));
    }

    private static List<WidgetSpec> build() {
        List<WidgetSpec> s = new ArrayList<>();

        add(s, Widget.CLOCK, NONE,
                // Seconds on the face want a frame a second.
                (context, charging) -> DashboardWidgetLayout.loadVariant(context, Widget.CLOCK)
                        == Variant.DETAILED ? SECOND : 0L,
                env -> WidgetReading.primary(WidgetText.clock(env.variant(Widget.CLOCK), env.h24,
                        new java.util.Date(env.now()), env.locale), WidgetIcon.NONE));

        add(s, Widget.DATE, NONE, null, env -> WidgetReading.plain(
                WidgetText.date(env.variant(Widget.DATE), new java.util.Date(env.now()),
                        env.locale), WidgetIcon.NONE));

        add(s, Widget.BATTERY, NONE, null, env -> env.snapshot.batteryPercent < 0 ? null
                : WidgetReading.plain(WidgetText.battery(env.variant(Widget.BATTERY),
                        env.snapshot.batteryPercent, env.snapshot.charging, env.words),
                env.snapshot.charging ? WidgetIcon.BATTERY_CHARGING : WidgetIcon.BATTERY));

        add(s, Widget.TEMPERATURE, NONE, null, env ->
                env.snapshot.temperatureTenthsCelsius < 0 ? null
                        : WidgetReading.plain(WidgetText.temperature(env.variant(Widget.TEMPERATURE),
                        env.degrees(env.snapshot.temperatureTenthsCelsius / 10f),
                        env.degreeName(), env.locale, env.words), WidgetIcon.TEMPERATURE));

        add(s, Widget.WEATHER, of(Source.WEATHER), null, env -> {
            String weather = env.weather(Widget.WEATHER);
            return weather.isEmpty() ? null : WidgetReading.plain(weather,
                    WidgetIcon.forWeather(env.snapshot.weatherCode));
        });

        add(s, Widget.NEXT_ALARM, NONE, null, env -> {
            Long alarm = env.snapshot.nextAlarmMillis;
            return WidgetReading.gated(alarm != null, alarm == null ? ""
                    : WidgetText.alarm(env.variant(Widget.NEXT_ALARM), env.h24, alarm,
                    env.locale), WidgetIcon.ALARM);
        });

        add(s, Widget.MEDIA, NONE, null, env -> WidgetReading.gated(env.hasMedia(),
                env.media(Widget.MEDIA), WidgetIcon.MEDIA));

        add(s, Widget.COMPASS, of(Source.HEADING), null, env -> {
            Float heading = env.snapshot.headingDegrees;
            return WidgetReading.gated(heading != null, heading == null ? ""
                    : WidgetText.compass(env.variant(Widget.COMPASS), heading,
                    WidgetEnv.cardinal(heading)), WidgetIcon.COMPASS);
        });

        add(s, Widget.SPEED, of(Source.LOCATION), null, env -> {
            Float speed = env.snapshot.speedMetersPerSecond;
            return WidgetReading.gated(speed != null, speed == null ? ""
                    : WidgetText.speed(env.variant(Widget.SPEED), speed, env.imperial,
                    env.words), WidgetIcon.SPEED);
        });

        add(s, Widget.ALTITUDE, of(Source.LOCATION), null, env -> {
            Double altitude = env.snapshot.altitudeMeters;
            return WidgetReading.gated(altitude != null, altitude == null ? ""
                    : WidgetText.altitude(env.variant(Widget.ALTITUDE), altitude, env.imperial,
                    env.words), WidgetIcon.ALTITUDE);
        });

        add(s, Widget.SESSION_TIMER, NONE, (context, charging) -> SECOND, env ->
                WidgetReading.plain(WidgetText.elapsed(env.variant(Widget.SESSION_TIMER),
                        env.snapshot.sessionElapsedMillis), WidgetIcon.TIMER));

        add(s, Widget.ACTIVE_PROFILE, NONE, null, env ->
                env.snapshot.activeProfileName.isEmpty() ? null
                        : WidgetReading.plain(WidgetText.text(env.variant(Widget.ACTIVE_PROFILE),
                        env.snapshot.activeProfileName, env.locale), WidgetIcon.PROFILE));

        add(s, Widget.CUSTOM_TEXT, NONE, null, env -> env.settings.customText.isEmpty() ? null
                : WidgetReading.plain(WidgetText.text(env.variant(Widget.CUSTOM_TEXT),
                env.settings.customText, env.locale), WidgetIcon.TEXT));

        add(s, Widget.NETWORK, of(Source.CONNECTIVITY), null, env -> WidgetReading.gated(
                !env.snapshot.networkSummary.isEmpty(),
                WidgetText.text(env.variant(Widget.NETWORK), env.snapshot.networkSummary,
                        env.locale), WidgetIcon.NETWORK));

        add(s, Widget.MEMORY, NONE, null, env -> WidgetReading.gated(
                env.snapshot.memoryPercent >= 0,
                WidgetText.percent(env.snapshot.memoryPercent, env.variant(Widget.MEMORY),
                        env.words.get(R.string.dashboard_variant_memory)), WidgetIcon.MEMORY));

        add(s, Widget.STORAGE, NONE, null, env -> WidgetReading.gated(
                env.snapshot.storagePercentFree >= 0,
                WidgetText.percent(env.snapshot.storagePercentFree, env.variant(Widget.STORAGE),
                        env.words.get(R.string.dashboard_variant_storage_free)),
                WidgetIcon.STORAGE));

        add(s, Widget.NOTIFICATIONS, NONE, null, env -> {
            // Nothing unread is the empty case here, not a missing reading.
            int count = NotificationWidgetState.getCount();
            return WidgetReading.gated(count > 0, WidgetText.notifications(
                    env.variant(Widget.NOTIFICATIONS), count, env.words),
                    WidgetIcon.NOTIFICATIONS);
        });

        add(s, Widget.CALENDAR, NONE, null, env -> {
            Long start = env.snapshot.calendarStartMillis;
            return WidgetReading.gated(start != null, start == null ? ""
                    : WidgetText.calendar(env.variant(Widget.CALENDAR), env.h24, start,
                    env.snapshot.calendarTitle, env.locale), WidgetIcon.CALENDAR);
        });

        add(s, Widget.STEPS, of(Source.STEPS), null, env -> WidgetReading.gated(
                env.snapshot.stepsToday >= 0,
                WidgetText.steps(env.variant(Widget.STEPS), env.snapshot.stepsToday, env.locale,
                        env.words), WidgetIcon.STEPS));

        add(s, Widget.FULLSCREEN_WEATHER, of(Source.WEATHER), null, env -> {
            String value = env.weather(Widget.FULLSCREEN_WEATHER);
            return WidgetReading.gated(true, value.isEmpty() ? "—" : value,
                    WidgetIcon.forWeather(env.snapshot.weatherCode));
        });

        add(s, Widget.FULLSCREEN_MEDIA, NONE, null, env -> WidgetReading.gated(true,
                env.hasMedia() ? env.media(Widget.FULLSCREEN_MEDIA) : "—", WidgetIcon.MEDIA));

        add(s, Widget.LAST_NOTIFICATION, NONE, null, env -> {
            String value = WidgetText.lastNotification(env.variant(Widget.LAST_NOTIFICATION),
                    env.snapshot.notificationApp, env.snapshot.notificationTitle,
                    env.snapshot.notificationText);
            return WidgetReading.gated(!value.isEmpty(), value, WidgetIcon.MESSAGE);
        });

        add(s, Widget.TIMER, NONE,
                (context, charging) -> TimerWidgetState.isRunning(context) ? SECOND : 0L,
                env -> {
                    long duration = DashboardWidgetLayout.timerMinutes(env.context) * 60_000L;
                    boolean finished = TimerWidgetState.finishIfElapsed(env.context, duration);
                    long shown = duration > 0
                            ? Math.max(0L, duration - TimerWidgetState.elapsedMillis(env.context))
                            : TimerWidgetState.elapsedMillis(env.context);
                    // A stopped countdown at zero has finished; a stopped stopwatch at
                    // zero has not started. Both are worth a row, so this one is
                    // always drawn rather than dropping out when there is no reading.
                    return WidgetReading.gated(true,
                            WidgetText.elapsed(env.variant(Widget.TIMER), shown), WidgetIcon.TIMER)
                            .withBuzz(finished);
                });

        add(s, Widget.CHARGING, NONE,
                (context, charging) -> charging ? CHARGE_TICK_MILLIS : 0L,
                env -> WidgetReading.gated(env.snapshot.charge != null,
                        WidgetText.charge(env.variant(Widget.CHARGING), env.snapshot.charge,
                                env.now(), env.h24, env.locale, env.words), WidgetIcon.CHARGE));

        add(s, Widget.SUN, of(Source.WEATHER), null, env -> {
            SunTimes.Next sun = env.snapshot.sun;
            return WidgetReading.gated(sun != null,
                    WidgetText.sun(env.variant(Widget.SUN), sun, env.now(), env.h24,
                            env.locale, env.words),
                    sun == null || sun.sunriseNext ? WidgetIcon.SUNRISE : WidgetIcon.SUNSET);
        });

        add(s, Widget.LEVEL, of(Source.GRAVITY), null, env -> {
            LevelReading level = env.snapshot.level;
            return WidgetReading.gated(level != null, level == null ? ""
                    : WidgetText.level(env.variant(Widget.LEVEL), level.rollDegrees,
                    level.pitchDegrees, env.locale, env.words), WidgetIcon.LEVEL);
        });

        add(s, Widget.SOUND_MODE, of(Source.SOUND), null, env -> {
            SoundModeReading sound = env.snapshot.sound;
            // Ringing is the ordinary state and says nothing, so like the
            // other widgets with nothing to report it drops out unless set to
            // stay: the row is there to warn that a call will not be heard.
            boolean worthShowing = sound != null
                    && (sound.mode != SoundModeReading.Mode.SOUND
                    || DashboardWidgetLayout.loadPresence(env.context, Widget.SOUND_MODE)
                    == DashboardWidgetLayout.Presence.ALWAYS);
            return WidgetReading.gated(worthShowing,
                    WidgetText.sound(env.variant(Widget.SOUND_MODE), sound, env.words),
                    WidgetIcon.forSound(sound));
        });

        add(s, Widget.WORLD_CLOCK, NONE, null, env -> {
            String zoneId = DashboardWidgetLayout.worldClockZone(env.context);
            return WidgetReading.plain(WidgetText.worldClock(env.variant(Widget.WORLD_CLOCK),
                    TimeZone.getTimeZone(zoneId), env.cityName(zoneId), env.now(), env.h24,
                    env.locale, env.words), WidgetIcon.GLOBE);
        });

        // Drawn as a picture by the view; the text is only what a page too
        // full to hold the code would show in its place.
        add(s, Widget.QR_CODE, NONE, null, env -> WidgetReading.plain("QR", WidgetIcon.NONE));

        add(s, Widget.UV_INDEX, of(Source.WEATHER), null, env -> {
            Float uv = env.snapshot.extra.uvIndex;
            return WidgetReading.gated(uv != null, uv == null ? ""
                    : WidgetText.uv(env.variant(Widget.UV_INDEX), uv, env.words), WidgetIcon.UV);
        });

        add(s, Widget.AIR_QUALITY, of(Source.WEATHER), null, env -> {
            Integer air = env.snapshot.extra.airQuality;
            return WidgetReading.gated(air != null, air == null ? ""
                    : WidgetText.air(env.variant(Widget.AIR_QUALITY), air, env.words),
                    WidgetIcon.AIR);
        });

        add(s, Widget.NETWORK_SPEED, NONE, (context, charging) -> TRAFFIC_TICK_MILLIS, env -> {
            ExtraReadings extra = env.snapshot.extra;
            boolean hasData = extra.downBytesPerSecond >= 0L;
            return WidgetReading.gated(hasData, !hasData ? ""
                    : WidgetText.networkSpeed(env.variant(Widget.NETWORK_SPEED),
                    android.text.format.Formatter.formatShortFileSize(
                            env.context, extra.downBytesPerSecond),
                    android.text.format.Formatter.formatShortFileSize(
                            env.context, extra.upBytesPerSecond), env.words),
                    WidgetIcon.NETWORK_SPEED);
        });

        add(s, Widget.MOON, NONE, null, env -> WidgetReading.plain(
                WidgetText.moon(env.variant(Widget.MOON), env.now(), env.words), WidgetIcon.MOON));

        add(s, Widget.COUNTDOWN, NONE, null, env -> {
            long target = DashboardWidgetLayout.countdownDay(env.context);
            return WidgetReading.gated(target >= 0L, target < 0L ? ""
                    : WidgetText.countdown(env.variant(Widget.COUNTDOWN), env.now(),
                    TimeZone.getDefault(), target,
                    DashboardWidgetLayout.countdownLabel(env.context), env.locale, env.words),
                    WidgetIcon.COUNTDOWN);
        });

        add(s, Widget.PROGRESS, NONE, null, env -> {
            WidgetMath.Period period = DashboardWidgetLayout.progressPeriod(env.context);
            long[] bounds = WidgetMath.periodBounds(env.now(), period,
                    java.util.Calendar.getInstance(env.locale));
            float fraction = (float) WidgetMath.fraction(env.now(), bounds);
            return WidgetReading.plain(WidgetText.progress(env.variant(Widget.PROGRESS), period,
                    fraction, Math.max(0L, bounds[1] - env.now()), env.words),
                    WidgetIcon.PROGRESS).withProgress(fraction);
        });

        add(s, Widget.PRESSURE, of(Source.BAROMETER), null, env -> {
            ExtraReadings extra = env.snapshot.extra;
            Float hpa = extra.pressureHpa;
            return WidgetReading.gated(hpa != null, hpa == null ? ""
                    : WidgetText.pressure(env.variant(Widget.PRESSURE), hpa, extra.pressureTrend,
                    env.imperial, env.locale, env.words), WidgetIcon.PRESSURE);
        });

        return Collections.unmodifiableList(s);
    }
}
