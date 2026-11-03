package com.tpkarras.mirror2rearultra;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.net.ConnectivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

final class RearDashboardController implements
        SensorEventListener,
        LocationListener,
        MediaWidgetState.Listener,
        NotificationWidgetState.Listener {
    interface Listener {
        void onDashboardDataChanged(RearDashboardSnapshot snapshot);
    }

    private static final long WEATHER_REFRESH_MILLIS = 30 * 60_000L;
    /** First retry after a failed fetch; doubles per failure up to the normal period. */
    private static final long WEATHER_RETRY_MILLIS = 2 * 60_000L;
    /** A reading older than this is shown as approximate: the fetch keeps failing. */
    private static final long WEATHER_STALE_MILLIS = 90 * 60_000L;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    /**
     * How often the charging widget reads the current. The battery broadcast
     * only comes when the level or the temperature moves, which on a slow
     * charger is minutes apart.
     */
    private static final long CHARGE_REFRESH_MILLIS = WidgetSpecs.CHARGE_TICK_MILLIS;
    /** The network speed is a rate, so it is sampled at a steady pace. */
    private static final long TRAFFIC_REFRESH_MILLIS = WidgetSpecs.TRAFFIC_TICK_MILLIS;
    /**
     * The level redraws at most this often. The sensor reports far faster,
     * and every report redraws the whole panel.
     */
    private static final long LEVEL_REFRESH_MILLIS = 66L;
    /** Each reading moves the level this far towards itself, to steady the hand. */
    private static final float LEVEL_SMOOTHING = 0.3f;

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService weatherExecutor = Executors.newSingleThreadExecutor();
    private final SensorManager sensorManager;
    private final LocationManager locationManager;
    private final AlarmManager alarmManager;
    private final long sessionStartedAt = SystemClock.elapsedRealtime();
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
            readBattery(intent);
            publish();
        }
    };
    private final BroadcastReceiver soundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
            readSound();
            publish();
        }
    };
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            if (!started) {
                return;
            }
            publish();
            // Each widget that is on says how soon it wants a frame - a
            // running timer needs the second hand as much as the session
            // timer does - and the panel ticks at the shortest.
            mainHandler.postDelayed(this,
                    WidgetSpecs.tickMillis(context, settings, charging, 30_000L));
        }
    };

    private DashboardSettings settings;
    private boolean started;
    /** The panel is out: nothing is read or redrawn until {@link #resume}. */
    private boolean paused;
    /** A quarter of an hour, in the microseconds a sensor is asked for. */
    private static final int PAUSED_BAROMETER_MICROS = 15 * 60 * 1_000_000;
    private int batteryPercent = -1;
    private int temperatureTenthsCelsius = -1;
    private boolean charging;
    private boolean batteryFull;
    private int voltageMillivolts;
    private ChargeReading chargeReading;
    private long chargeUpdatedAt;
    /** Averaged over the last few reads: the current jumps by half from one to the next. */
    private float smoothedMicroamps = Float.NaN;
    private float[] gravity;
    private LevelReading levelReading;
    private long levelPublishedAt;
    private SoundModeReading soundReading;
    private Float uvIndex;
    private Integer airQuality;
    private long trafficSampledAt;
    private long lastRxBytes = -1L;
    private long lastTxBytes = -1L;
    private long downBytesPerSecond = -1L;
    private long upBytesPerSecond = -1L;
    private Float pressureHpa;
    private int pressureTrend;
    private boolean soundReceiverRegistered;
    private String weatherRequestCity = "";
    private String weatherPlace = "";
    private Integer weatherTemperatureCelsius;
    private Integer weatherCode;
    private long weatherUpdatedAt;
    /** When a fetch last succeeded, as opposed to merely finished; 0 before the first. */
    private long weatherSucceededAt;
    private int weatherFailures;
    private final Runnable weatherRefresh = () -> refreshWeatherIfNeeded(true);
    private boolean weatherLoading;
    private ConnectivityManager.NetworkCallback networkCallback;
    private float smoothedDown = -1f;
    private float smoothedUp = -1f;
    private Float headingDegrees;
    private Float speedMetersPerSecond;
    private Double altitudeMeters;
    /** When the last fix arrived, on the elapsed-realtime clock; 0 before any. */
    private long locationAtMillis;
    private static final long LOCATION_LOST_MILLIS = 12_000L;
    private MirrorProfile activeProfile;
    private long systemStatsUpdatedAt;
    private String networkSummary = "";
    private int memoryPercent = -1;
    private int storagePercentFree = -1;
    private static final long ALARM_CACHE_MILLIS = 30_000L;
    private long alarmReadAt;
    private Long nextAlarmMillis;
    /** The barometer's history, read from the preferences once and kept. */
    private java.util.List<long[]> pressureSamples;
    private String calendarTitle = "";
    private Long calendarStartMillis;
    private long calendarEndMillis = -1L;
    private int calendarMore;
    private long calendarUpdatedAt;
    private int stepsToday = -1;

    RearDashboardController(Context context, DashboardSettings settings, Listener listener) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.listener = listener;
        sensorManager = this.context.getSystemService(SensorManager.class);
        locationManager = this.context.getSystemService(LocationManager.class);
        alarmManager = this.context.getSystemService(AlarmManager.class);
        activeProfile = MirrorSettings.loadActiveProfile(this.context);
    }

    void start() {
        if (started) {
            return;
        }
        started = true;
        registerBattery();
        MediaWidgetState.addListener(this);
        NotificationWidgetState.addListener(this);
        configureDynamicSources();
        publish();
        mainHandler.post(clockTick);
        refreshWeatherIfNeeded(true);
    }

    private void registerBattery() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent sticky;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sticky = context.registerReceiver(
                    batteryReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            sticky = context.registerReceiver(batteryReceiver, filter);
        }
        readBattery(sticky);
    }

    /**
     * Stops reading and redrawing while nobody can see the panel.
     *
     * <p>A panel the idle timer has put out used to go on ticking, listening
     * to its sensors, holding a GPS fix and asking for the weather, all for a
     * picture that was black. Everything is let go here and taken up again by
     * {@link #resume}, which brings the numbers up to date before the panel
     * is lit. The two sensors that keep a record while nobody looks - the step
     * counter, which is a total since boot and so loses nothing, and the
     * barometer, whose trend needs a sample every quarter of an hour - are
     * kept, the barometer at that rate.
     */
    void pause() {
        if (!started || paused) {
            return;
        }
        paused = true;
        mainHandler.removeCallbacks(clockTick);
        mainHandler.removeCallbacks(weatherRefresh);
        try {
            context.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
            // Already gone.
        }
        listenForSound(false);
        listenForNetwork(false);
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            Set<WidgetSpec.Source> needs = WidgetSpecs.sources(context, settings);
            if (needs.contains(WidgetSpec.Source.BAROMETER)) {
                Sensor barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
                if (barometer != null) {
                    sensorManager.registerListener(this, barometer, PAUSED_BAROMETER_MICROS);
                }
            }
            if (needs.contains(WidgetSpec.Source.STEPS) && hasActivityPermission()) {
                Sensor steps = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (steps != null) {
                    sensorManager.registerListener(
                            this, steps, SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        }
    }

    /** Takes everything up again and draws a current frame, before the panel is lit. */
    void resume() {
        if (!started || !paused) {
            return;
        }
        paused = false;
        registerBattery();
        configureDynamicSources();
        publish();
        mainHandler.post(clockTick);
        // The refresh timer was dropped with the rest: fetch if the reading
        // has gone stale meanwhile, otherwise wait out what is left of it.
        mainHandler.removeCallbacks(weatherRefresh);
        long age = System.currentTimeMillis() - weatherUpdatedAt;
        refreshWeatherIfNeeded(false);
        if (!weatherLoading && age < WEATHER_REFRESH_MILLIS) {
            mainHandler.postDelayed(weatherRefresh, WEATHER_REFRESH_MILLIS - age);
        }
    }

    void updateSettings(DashboardSettings updatedSettings) {
        boolean cityChanged = !settings.weatherCity.equals(updatedSettings.weatherCity);
        boolean weatherBecameVisible = !settings.showWeather && updatedSettings.showWeather;
        settings = updatedSettings;
        if (cityChanged) {
            weatherLoading = false;
            weatherRequestCity = "";
            weatherPlace = "";
            weatherTemperatureCelsius = null;
            weatherCode = null;
            weatherUpdatedAt = 0L;
            uvIndex = null;
            airQuality = null;
        }
        // A UV or air widget switched on after the last fetch has nothing to
        // show until the next, half an hour off. Fetched again at once - but
        // not more than once a minute, since this runs on every settings save
        // and the air service may simply have nothing for this place.
        boolean missingReadings = System.currentTimeMillis() - weatherUpdatedAt > 60_000L
                && ((uvIndex == null && DashboardWidgetLayout.isExtraEnabled(
                        context, DashboardWidgetLayout.Widget.UV_INDEX))
                || (airQuality == null && DashboardWidgetLayout.isExtraEnabled(
                        context, DashboardWidgetLayout.Widget.AIR_QUALITY)));
        publish();
        configureDynamicSources();
        refreshWeatherIfNeeded(cityChanged || weatherBecameVisible || missingReadings);
    }

    void setActiveProfile(MirrorProfile profile) {
        activeProfile = profile;
        publish();
    }

    void stop() {
        if (!started) {
            return;
        }
        started = false;
        mainHandler.removeCallbacksAndMessages(null);
        MediaWidgetState.removeListener(this);
        NotificationWidgetState.removeListener(this);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        try {
            context.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was already removed by the system.
        }
        listenForSound(false);
        listenForNetwork(false);
        weatherExecutor.shutdownNow();
    }

    private void readBattery(Intent intent) {
        if (intent == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        batteryPercent = level < 0 || scale <= 0 ? -1 : Math.round(level * 100f / scale);
        temperatureTenthsCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        batteryFull = status == BatteryManager.BATTERY_STATUS_FULL;
        voltageMillivolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        if (!charging) {
            smoothedMicroamps = Float.NaN;
        }
    }

    private void refreshCharge() {
        if (!charging || !DashboardWidgetLayout.isExtraEnabled(
                context, DashboardWidgetLayout.Widget.CHARGING)) {
            chargeReading = null;
            smoothedMicroamps = Float.NaN;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (chargeReading != null && now - chargeUpdatedAt < CHARGE_REFRESH_MILLIS) {
            return;
        }
        chargeUpdatedAt = now;
        BatteryManager battery = context.getSystemService(BatteryManager.class);
        if (battery == null) {
            chargeReading = null;
            return;
        }
        int microamps = Math.abs(battery.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
        smoothedMicroamps = Float.isNaN(smoothedMicroamps)
                ? microamps : smoothedMicroamps * 0.6f + microamps * 0.4f;
        chargeReading = ChargeReading.of(Math.round(smoothedMicroamps), voltageMillivolts,
                battery.computeChargeTimeRemaining(),
                battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
                batteryPercent, batteryFull);
    }

    /**
     * Follows the ringer, do not disturb and the ring volume while the sound
     * widget is on. All three are announced, so nothing has to be polled.
     */
    private void listenForSound(boolean listen) {
        if (listen == soundReceiverRegistered) {
            return;
        }
        soundReceiverRegistered = listen;
        if (!listen) {
            soundReading = null;
            try {
                context.unregisterReceiver(soundReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already gone.
            }
            return;
        }
        IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        // Not in the SDK, but sent by every build of Android since 4.0; the
        // widget's volume figure would otherwise only catch up on the next
        // ringer change.
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        ContextCompat.registerReceiver(context, soundReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        readSound();
    }

    /**
     * Redraws the network row when the connection changes. It was read every
     * thirty seconds, so leaving the Wi-Fi showed the old name for half a
     * minute.
     */
    private void listenForNetwork(boolean listen) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity == null) {
            return;
        }
        if (!listen) {
            if (networkCallback != null) {
                try {
                    connectivity.unregisterNetworkCallback(networkCallback);
                } catch (IllegalArgumentException alreadyGone) {
                    // Not registered any more.
                }
                networkCallback = null;
            }
            return;
        }
        if (networkCallback != null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            private void changed() {
                mainHandler.post(() -> {
                    if (!started) {
                        return;
                    }
                    systemStatsUpdatedAt = 0L;
                    publish();
                });
            }

            @Override
            public void onAvailable(@NonNull android.net.Network network) {
                changed();
            }

            @Override
            public void onLost(@NonNull android.net.Network network) {
                changed();
            }

            @Override
            public void onCapabilitiesChanged(@NonNull android.net.Network network,
                    @NonNull android.net.NetworkCapabilities capabilities) {
                changed();
            }
        };
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException unavailable) {
            networkCallback = null;
        }
    }

    private void readSound() {
        AudioManager audio = context.getSystemService(AudioManager.class);
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (audio == null || notifications == null) {
            soundReading = null;
            return;
        }
        soundReading = new SoundModeReading(
                SoundModeReading.modeOf(audio.getRingerMode(),
                        notifications.getCurrentInterruptionFilter()),
                SoundModeReading.percentOf(audio.getStreamVolume(AudioManager.STREAM_RING),
                        audio.getStreamMaxVolume(AudioManager.STREAM_RING)));
    }

    private void configureDynamicSources() {
        if (!started || paused) {
            return;
        }
        Set<WidgetSpec.Source> needs = WidgetSpecs.sources(context, settings);
        listenForSound(needs.contains(WidgetSpec.Source.SOUND));
        listenForNetwork(needs.contains(WidgetSpec.Source.CONNECTIVITY));
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            if (needs.contains(WidgetSpec.Source.HEADING)) {
                Sensor rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                if (rotation != null) {
                    sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
            if (needs.contains(WidgetSpec.Source.GRAVITY)) {
                // Gravity alone, with the hand's shake already taken out;
                // the raw accelerometer where a phone has no such sensor.
                Sensor down = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
                if (down == null) {
                    down = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                }
                if (down != null) {
                    sensorManager.registerListener(this, down, SensorManager.SENSOR_DELAY_UI);
                }
            } else {
                gravity = null;
                levelReading = null;
            }
            if (needs.contains(WidgetSpec.Source.BAROMETER)) {
                Sensor barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
                if (barometer != null) {
                    sensorManager.registerListener(
                            this, barometer, SensorManager.SENSOR_DELAY_NORMAL);
                }
            } else {
                pressureHpa = null;
            }
            if (needs.contains(WidgetSpec.Source.STEPS) && hasActivityPermission()) {
                Sensor steps = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (steps != null) sensorManager.registerListener(
                        this, steps, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            if (needs.contains(WidgetSpec.Source.LOCATION) && hasLocationPermission()) {
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1_000L,
                            // Nought, so a phone standing still keeps reporting that it
                            // is: otherwise a fix gone quiet and a stop look the same.
                            0f,
                            this,
                            Looper.getMainLooper()
                    );
                    Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (last != null) {
                        onLocationChanged(last);
                    }
                } catch (SecurityException | IllegalArgumentException ignored) {
                    speedMetersPerSecond = null;
                    altitudeMeters = null;
                    locationAtMillis = 0L;
                }
            } else {
                speedMetersPerSecond = null;
                altitudeMeters = null;
                locationAtMillis = 0L;
            }
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasActivityPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            updateSteps(Math.round(event.values[0]));
            publish();
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY
                || event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            updateLevel(event.values);
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            updatePressure(event.values[0]);
            return;
        }
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }
        float[] rotation = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(rotation, event.values);
        SensorManager.getOrientation(rotation, orientation);
        float degrees = (float) Math.toDegrees(orientation[0]);
        // Lying screen down, the panel is the face in view and the system compass
        // reads from there: its figure is ours turned half way round (measured
        // on the device, 311 against 124 and 50 against 230 after a quarter turn).
        // rotation[8] is how far the screen faces up; vertical is left as it is.
        if (rotation[8] < -0.5f) {
            degrees += 180f;
        }
        headingDegrees = (degrees + 360f) % 360f;
        publish();
    }

    private void updateLevel(float[] values) {
        if (gravity == null) {
            gravity = new float[]{values[0], values[1], values[2]};
        } else {
            for (int axis = 0; axis < 3; axis++) {
                gravity[axis] += (values[axis] - gravity[axis]) * LEVEL_SMOOTHING;
            }
        }
        long now = SystemClock.elapsedRealtime();
        if (now - levelPublishedAt < LEVEL_REFRESH_MILLIS) {
            return;
        }
        LevelReading reading = LevelReading.fromGravity(gravity[0], gravity[1], gravity[2]);
        // A tenth of a degree is nothing on a panel this size, and a phone
        // held still is what the widget is for: no reason to redraw for it.
        boolean unchanged = reading == null ? levelReading == null
                : levelReading != null
                && Math.abs(reading.turnDegrees - levelReading.turnDegrees) < 0.1f
                && Math.abs(reading.pitchDegrees - levelReading.pitchDegrees) < 0.5f;
        if (unchanged) {
            return;
        }
        levelReading = reading;
        levelPublishedAt = now;
        publish();
    }

    /**
     * Keeps the barometer's reading, and a sample every quarter of an hour
     * for the trend, which needs to know what it read three hours ago - kept
     * in the preferences, since a panel session rarely lasts that long.
     */
    private void updatePressure(float hpa) {
        float smoothed = pressureHpa == null ? hpa : pressureHpa + (hpa - pressureHpa) * 0.1f;
        long now = System.currentTimeMillis();
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "dashboard_pressure", Context.MODE_PRIVATE);
        // The sensor reports many times a second; the preferences were being
        // parsed on every report to look at a list that changes every 15 min.
        if (pressureSamples == null) {
            pressureSamples = PressureHistory.parse(prefs.getString("samples", ""));
        }
        java.util.List<long[]> samples = pressureSamples;
        if (samples.isEmpty() || now - samples.get(samples.size() - 1)[0] >= 15 * 60_000L) {
            samples.add(new long[]{now, Math.round(smoothed * 10f)});
            samples = PressureHistory.trimmed(samples, now);
            pressureSamples = samples;
            prefs.edit().putString("samples", PressureHistory.format(samples)).apply();
        }
        Float earlier = PressureHistory.threeHoursBefore(samples, now);
        int trend = earlier == null ? 0 : WidgetMath.pressureTrend(smoothed, earlier);
        boolean changed = pressureHpa == null || Math.round(pressureHpa) != Math.round(smoothed)
                || trend != pressureTrend;
        pressureHpa = smoothed;
        pressureTrend = trend;
        if (changed) {
            publish();
        }
    }

    /** Bytes a second each way since the last sample, from the counters for the whole phone. */
    private void refreshTraffic() {
        if (!DashboardWidgetLayout.isExtraEnabled(
                context, DashboardWidgetLayout.Widget.NETWORK_SPEED)) {
            lastRxBytes = -1L;
            downBytesPerSecond = -1L;
            upBytesPerSecond = -1L;
            smoothedDown = -1f;
            smoothedUp = -1f;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (lastRxBytes >= 0L && now - trafficSampledAt < 1_000L) {
            return;
        }
        long rx = android.net.TrafficStats.getTotalRxBytes();
        long tx = android.net.TrafficStats.getTotalTxBytes();
        if (rx < 0L || tx < 0L) {
            downBytesPerSecond = -1L;
            upBytesPerSecond = -1L;
            return;
        }
        if (lastRxBytes >= 0L && now > trafficSampledAt) {
            long elapsed = now - trafficSampledAt;
            // Averaged with the last sample: a transfer arrives in bursts, and
            // the raw figure jumped between megabytes and nothing every two seconds.
            float down = Math.max(0L, (rx - lastRxBytes) * 1_000L / elapsed);
            float up = Math.max(0L, (tx - lastTxBytes) * 1_000L / elapsed);
            smoothedDown = smoothedDown < 0f ? down : smoothedDown * 0.5f + down * 0.5f;
            smoothedUp = smoothedUp < 0f ? up : smoothedUp * 0.5f + up * 0.5f;
            downBytesPerSecond = Math.round(smoothedDown);
            upBytesPerSecond = Math.round(smoothedUp);
        }
        lastRxBytes = rx;
        lastTxBytes = tx;
        trafficSampledAt = now;
    }

    private void updateSteps(int totalSinceBoot) {
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "dashboard_steps", Context.MODE_PRIVATE);
        StepTally previous = prefs.contains("day")
                ? new StepTally(prefs.getString("day", ""), prefs.getInt("base", 0),
                        prefs.getInt("carried", 0), prefs.getInt("last", 0),
                        prefs.getInt("today", 0))
                : null;
        StepTally tally = StepTally.update(previous, day, totalSinceBoot);
        if (previous == null || tally.base != previous.base || tally.carried != previous.carried
                || tally.today != previous.today || !tally.day.equals(previous.day)) {
            prefs.edit().putString("day", tally.day).putInt("base", tally.base)
                    .putInt("carried", tally.carried).putInt("last", tally.lastTotal)
                    .putInt("today", tally.today).apply();
        }
        stepsToday = tally.today;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        locationAtMillis = location.getElapsedRealtimeNanos() / 1_000_000L;
        speedMetersPerSecond = location.hasSpeed() ? location.getSpeed() : null;
        altitudeMeters = location.hasAltitude() ? location.getAltitude() : null;
        publish();
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        speedMetersPerSecond = null;
        altitudeMeters = null;
        publish();
    }

    @Override
    public void onMediaWidgetChanged(MediaWidgetState.Snapshot snapshot) {
        mainHandler.post(this::publish);
    }

    /**
     * Redraws as soon as something arrives.
     *
     * <p>Without this the panel would catch up on its own schedule, which is
     * every thirty seconds unless the session timer happens to be on.
     */
    @Override
    public void onNotificationWidgetChanged(NotificationWidgetState.Snapshot snapshot) {
        mainHandler.post(this::publish);
    }

    private void refreshWeatherIfNeeded(boolean force) {
        String city = settings.weatherCity;
        boolean weatherEnabled = WidgetSpecs.sources(context, settings)
                .contains(WidgetSpec.Source.WEATHER);
        if (!started || paused || !weatherEnabled || weatherLoading) {
            return;
        }
        long age = System.currentTimeMillis() - weatherUpdatedAt;
        if (!force && city.equals(weatherRequestCity) && age < WEATHER_REFRESH_MILLIS) {
            return;
        }
        weatherLoading = true;
        weatherRequestCity = city;
        publish();
        try {
            weatherExecutor.execute(() -> loadWeather(city));
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            // stop() has shut the executor down; nothing is waiting on the answer.
            weatherLoading = false;
        }
    }

    private void loadWeather(String city) {
        WeatherResult result = null;
        try {
            result = requestWeather(city);
        } catch (IOException | RuntimeException ignored) {
            // The previous successful result remains visible when the network is unavailable.
        }
        WeatherResult finalResult = result;
        mainHandler.post(() -> {
            if (!started || !city.equals(weatherRequestCity)) {
                return;
            }
            weatherLoading = false;
            weatherUpdatedAt = System.currentTimeMillis();
            long delay = WEATHER_REFRESH_MILLIS;
            if (finalResult != null) {
                weatherFailures = 0;
                weatherSucceededAt = weatherUpdatedAt;
                weatherPlace = finalResult.place;
                weatherTemperatureCelsius = finalResult.temperatureCelsius;
                weatherCode = finalResult.weatherCode;
                WeatherForecastState.set(finalResult.dates, finalResult.minimums,
                        finalResult.maximums, finalResult.codes,
                        finalResult.rainChances);
                WeatherForecastState.setSun(finalResult.sunrises, finalResult.sunsets);
                uvIndex = finalResult.uvIndex;
                airQuality = finalResult.airQuality;
            } else {
                // Half an hour of an old reading is too long to wait after a
                // dropped connection; try again soon, backing off while the
                // service stays out of reach.
                weatherFailures++;
                delay = Math.min(WEATHER_REFRESH_MILLIS,
                        WEATHER_RETRY_MILLIS << Math.min(weatherFailures - 1, 4));
            }
            publish();
            // A panel that is out has no timer; resume() arms it again.
            if (!paused) {
                mainHandler.removeCallbacks(weatherRefresh);
                mainHandler.postDelayed(weatherRefresh, delay);
            }
        });
    }

    /** Where the phone last was, or null when it has never said. */
    @Nullable
    private double[] devicePlace() {
        QuietHours.refreshPlaceFromDevice(context);
        return QuietHours.place(context);
    }

    /** The cached place's coordinates, or null for an entry that does not read. */
    private static double[] parseCoordinates(String[] cached) {
        try {
            return new double[]{Double.parseDouble(cached[0]), Double.parseDouble(cached[1])};
        } catch (NumberFormatException damaged) {
            return null;
        }
    }

    private WeatherResult requestWeather(String city) throws IOException {
        String language = Locale.getDefault().getLanguage();
        String cacheKey = language + "|" + city;
        android.content.SharedPreferences placeCache = context.getSharedPreferences(
                "dashboard_weather_place", Context.MODE_PRIVATE);
        // The city does not move: asked once per city and language, not on
        // every refresh.
        String[] cached = placeCache.getString(cacheKey, "").split(";", 3);
        try {
            double latitude;
            double longitude;
            String displayName;
            // No city named: the weather where the phone is, by the last
            // position it knew. It used to leave every weather widget empty
            // until a city was typed in, with nothing to say why.
            double[] known = city.isEmpty() ? devicePlace()
                    : cached.length == 3 ? parseCoordinates(cached) : null;
            if (city.isEmpty() && known == null) {
                throw new IOException("No city and no known position");
            }
            if (known != null) {
                latitude = known[0];
                longitude = known[1];
                displayName = city.isEmpty() ? "" : cached[2];
            } else {
                String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8.name());
                JSONObject geocoding = requestJson(
                        "https://geocoding-api.open-meteo.com/v1/search?name=" + encodedCity
                                + "&count=1&language=" + URLEncoder.encode(language, "UTF-8")
                                + "&format=json"
                );
                JSONArray results = geocoding.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    throw new IOException("City not found");
                }
                JSONObject place = results.getJSONObject(0);
                latitude = place.getDouble("latitude");
                longitude = place.getDouble("longitude");
                displayName = place.optString("name", city);
                placeCache.edit().putString(cacheKey,
                        latitude + ";" + longitude + ";" + displayName).apply();
            }
            if (!city.isEmpty()) {
                // Where night falls for the quiet hours, if the phone will not say.
                QuietHours.offerWeatherPlace(context, latitude, longitude);
            }

            JSONObject forecast = requestJson(
                    "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                            + "&longitude=" + longitude
                            + "&current=temperature_2m,weather_code,uv_index"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                            + ",precipitation_probability_max,sunrise,sunset"
                            + "&forecast_days=4&timezone=auto"
            );
            JSONObject current = forecast.getJSONObject("current");
            JSONObject daily = forecast.getJSONObject("daily");
            JSONArray times = daily.getJSONArray("time");
            JSONArray minimumsJson = daily.getJSONArray("temperature_2m_min");
            JSONArray maximumsJson = daily.getJSONArray("temperature_2m_max");
            JSONArray codesJson = daily.getJSONArray("weather_code");
            // Not every place and season has this to give, so it is optional.
            JSONArray rainJson = daily.optJSONArray("precipitation_probability_max");
            // Optional too: past the polar circles a day can have neither.
            JSONArray sunriseJson = daily.optJSONArray("sunrise");
            JSONArray sunsetJson = daily.optJSONArray("sunset");
            int count = Math.min(4, times.length());
            String[] dates = new String[count];
            int[] minimums = new int[count];
            int[] maximums = new int[count];
            int[] codes = new int[count];
            int[] rainChances = new int[count];
            String[] sunriseTimes = new String[count];
            String[] sunsetTimes = new String[count];
            for (int index = 0; index < count; index++) {
                dates[index] = times.getString(index);
                minimums[index] = (int) Math.round(minimumsJson.getDouble(index));
                maximums[index] = (int) Math.round(maximumsJson.getDouble(index));
                codes[index] = codesJson.getInt(index);
                rainChances[index] = rainJson == null ? -1 : rainJson.optInt(index, -1);
                sunriseTimes[index] = sunriseJson == null ? "" : sunriseJson.optString(index, "");
                sunsetTimes[index] = sunsetJson == null ? "" : sunsetJson.optString(index, "");
            }
            int offsetSeconds = forecast.optInt("utc_offset_seconds", 0);
            Float uvIndex = current.has("uv_index") && !current.isNull("uv_index")
                    ? (float) current.getDouble("uv_index") : null;
            Integer airQuality = DashboardWidgetLayout.isExtraEnabled(
                    context, DashboardWidgetLayout.Widget.AIR_QUALITY)
                    ? requestAirQuality(latitude, longitude) : null;
            return new WeatherResult(
                    displayName,
                    (int) Math.round(current.getDouble("temperature_2m")),
                    current.getInt("weather_code"), dates, minimums, maximums, codes,
                    rainChances, SunTimes.toInstants(sunriseTimes, offsetSeconds),
                    SunTimes.toInstants(sunsetTimes, offsetSeconds), uvIndex, airQuality
            );
        } catch (JSONException error) {
            throw new IOException("Invalid weather data", error);
        }
    }

    /**
     * The European air quality index where the weather city is, or null.
     *
     * <p>A separate service of the same provider, and optional: the weather
     * is not lost for want of it.
     */
    private static Integer requestAirQuality(double latitude, double longitude) {
        try {
            JSONObject air = requestJson(
                    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude="
                            + latitude + "&longitude=" + longitude
                            + "&current=european_aqi&timezone=auto");
            JSONObject current = air.optJSONObject("current");
            return current == null || current.isNull("european_aqi")
                    ? null : (int) Math.round(current.getDouble("european_aqi"));
        } catch (IOException | JSONException | RuntimeException unavailable) {
            return null;
        }
    }

    private static JSONObject requestJson(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mirror2RearUltra/1.8");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Weather HTTP " + responseCode);
            }
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_RESPONSE_BYTES) {
                        throw new IOException("Weather response too large");
                    }
                    output.write(buffer, 0, read);
                }
                return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (org.json.JSONException error) {
            throw new IOException("Invalid weather response", error);
        } finally {
            connection.disconnect();
        }
    }

    private void publish() {
        if (paused) {
            return;
        }
        refreshSystemStatsIfNeeded();
        refreshCalendar();
        refreshCharge();
        refreshTraffic();
        long now = System.currentTimeMillis();
        SunTimes.Next sun = DashboardWidgetLayout.isExtraEnabled(
                context, DashboardWidgetLayout.Widget.SUN)
                ? SunTimes.next(now, WeatherForecastState.sunrises(),
                        WeatherForecastState.sunsets())
                : null;
        Long nextAlarm = nextAlarmTime();
        // A fix that has gone quiet is not a phone standing still: the speed
        // it last read would otherwise stay on the panel for good.
        boolean lost = locationAtMillis != 0L
                && SystemClock.elapsedRealtime() - locationAtMillis > LOCATION_LOST_MILLIS;
        MediaWidgetState.Snapshot media = MediaWidgetState.get();
        NotificationWidgetState.Snapshot notifications = NotificationWidgetState.get();
        listener.onDashboardDataChanged(new RearDashboardSnapshot(
                now,
                batteryPercent,
                temperatureTenthsCelsius,
                charging,
                weatherPlace,
                weatherTemperatureCelsius,
                weatherCode,
                weatherLoading,
                nextAlarm,
                media.title,
                media.artist,
                media.playing,
                headingDegrees,
                speedMetersPerSecond,
                altitudeMeters,
                SystemClock.elapsedRealtime() - sessionStartedAt,
                MirrorSettings.profileDisplayName(context, activeProfile),
                networkSummary,
                memoryPercent,
                storagePercentFree,
                calendarTitle,
                calendarStartMillis,
                stepsToday,
                notifications.app,
                notifications.title,
                notifications.text,
                chargeReading,
                sun,
                levelReading,
                soundReading,
                new ExtraReadings(uvIndex, airQuality, downBytesPerSecond, upBytesPerSecond,
                        pressureHpa, pressureTrend,
                        weatherSucceededAt > 0L
                                && now - weatherSucceededAt > WEATHER_STALE_MILLIS,
                        calendarEndMillis, calendarMore, lost)
        ));
    }

    /**
     * When the next alarm rings, asked of the system at most twice a minute:
     * the clock may tick every second, and the alarm does not move that fast.
     */
    @Nullable
    private Long nextAlarmTime() {
        if (!settings.showNextAlarm || alarmManager == null) {
            return null;
        }
        long elapsed = SystemClock.elapsedRealtime();
        if (elapsed - alarmReadAt >= ALARM_CACHE_MILLIS || alarmReadAt == 0L) {
            alarmReadAt = elapsed;
            AlarmManager.AlarmClockInfo info = alarmManager.getNextAlarmClock();
            nextAlarmMillis = info == null ? null : info.getTriggerTime();
        }
        return nextAlarmMillis;
    }

    private void refreshCalendar() {
        if (!DashboardWidgetLayout.isExtraEnabled(context, DashboardWidgetLayout.Widget.CALENDAR)
                || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            calendarTitle = "";
            calendarStartMillis = null;
            return;
        }
        long elapsed = SystemClock.elapsedRealtime();
        if (elapsed - calendarUpdatedAt < 60_000L) return;
        calendarUpdatedAt = elapsed;
        calendarTitle = "";
        calendarStartMillis = null;
        calendarEndMillis = -1L;
        calendarMore = 0;
        long now = System.currentTimeMillis();
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(builder, now);
        android.content.ContentUris.appendId(builder, now + 24L * 60L * 60L * 1000L);
        String[] columns = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END};
        try (Cursor cursor = context.getContentResolver().query(builder.build(), columns,
                CalendarContract.Instances.END + ">?", new String[]{String.valueOf(now)},
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                calendarTitle = cursor.getString(0);
                calendarStartMillis = cursor.getLong(1);
                calendarEndMillis = cursor.getLong(2);
                calendarMore = Math.max(0, cursor.getCount() - 1);
            }
        } catch (SecurityException ignored) {
            calendarTitle = "";
            calendarStartMillis = null;
        }
    }

    private void refreshSystemStatsIfNeeded() {
        long now = SystemClock.elapsedRealtime();
        if (now - systemStatsUpdatedAt < 30_000L) return;
        systemStatsUpdatedAt = now;
        SystemStats stats = SystemStats.read(context);
        networkSummary = stats.networkSummary;
        memoryPercent = stats.memoryPercent;
        storagePercentFree = stats.storagePercentFree;
    }

    private static final class WeatherResult {
        final String place;
        final int temperatureCelsius;
        final int weatherCode;
        final String[] dates;
        final int[] minimums;
        final int[] maximums;
        final int[] codes;
        final int[] rainChances;
        final long[] sunrises;
        final long[] sunsets;
        final Float uvIndex;
        final Integer airQuality;

        WeatherResult(String place, int temperatureCelsius, int weatherCode,
                String[] dates, int[] minimums, int[] maximums, int[] codes,
                int[] rainChances, long[] sunrises, long[] sunsets,
                Float uvIndex, Integer airQuality) {
            this.place = place;
            this.temperatureCelsius = temperatureCelsius;
            this.weatherCode = weatherCode;
            this.dates = dates;
            this.minimums = minimums;
            this.maximums = maximums;
            this.codes = codes;
            this.rainChances = rainChances;
            this.sunrises = sunrises;
            this.sunsets = sunsets;
            this.uvIndex = uvIndex;
            this.airQuality = airQuality;
        }
    }
}
