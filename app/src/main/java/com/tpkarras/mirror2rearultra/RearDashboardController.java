package com.tpkarras.mirror2rearultra;

import android.Manifest;
import android.app.AlarmManager;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

final class RearDashboardController implements
        SensorEventListener,
        LocationListener,
        MediaWidgetState.Listener,
        NotificationWidgetState.Listener {
    interface Listener {
        void onDashboardDataChanged(RearDashboardSnapshot snapshot);
    }

    private static final long CLOCK_REFRESH_MILLIS = 1_000L;
    private static final long WEATHER_REFRESH_MILLIS = 30 * 60_000L;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

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
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            if (!started) {
                return;
            }
            publish();
            // A running timer needs the second hand as much as the session
            // timer does; without this it would step in half-minutes.
            boolean needsSeconds = settings.showSessionTimer
                    || TimerWidgetState.isRunning(context);
            mainHandler.postDelayed(this, needsSeconds
                    ? CLOCK_REFRESH_MILLIS : 30_000L);
        }
    };

    private DashboardSettings settings;
    private boolean started;
    private int batteryPercent = -1;
    private int temperatureTenthsCelsius = -1;
    private boolean charging;
    private String weatherRequestCity = "";
    private String weatherPlace = "";
    private Integer weatherTemperatureCelsius;
    private Integer weatherCode;
    private long weatherUpdatedAt;
    private boolean weatherLoading;
    private Float headingDegrees;
    private Float speedMetersPerSecond;
    private Double altitudeMeters;
    private MirrorProfile activeProfile;
    private long systemStatsUpdatedAt;
    private String networkSummary = "";
    private int memoryPercent = -1;
    private int storagePercentFree = -1;
    private String calendarTitle = "";
    private Long calendarStartMillis;
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
        MediaWidgetState.addListener(this);
        NotificationWidgetState.addListener(this);
        configureDynamicSources();
        publish();
        mainHandler.post(clockTick);
        refreshWeatherIfNeeded(true);
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
        }
        publish();
        configureDynamicSources();
        refreshWeatherIfNeeded(cityChanged || weatherBecameVisible);
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
    }

    private void configureDynamicSources() {
        if (!started) {
            return;
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            if (settings.showCompass) {
                Sensor rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                if (rotation != null) {
                    sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
            if (DashboardWidgetLayout.isExtraEnabled(context, DashboardWidgetLayout.Widget.STEPS)
                    && hasActivityPermission()) {
                Sensor steps = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (steps != null) sensorManager.registerListener(
                        this, steps, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            if ((settings.showSpeed || settings.showAltitude) && hasLocationPermission()) {
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1_000L,
                            1f,
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
                }
            } else {
                speedMetersPerSecond = null;
                altitudeMeters = null;
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
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }
        float[] rotation = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(rotation, event.values);
        SensorManager.getOrientation(rotation, orientation);
        float degrees = (float) Math.toDegrees(orientation[0]);
        headingDegrees = (degrees + 360f) % 360f;
        publish();
    }

    private void updateSteps(int totalSinceBoot) {
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "dashboard_steps", Context.MODE_PRIVATE);
        if (!day.equals(prefs.getString("day", "")) || totalSinceBoot < prefs.getInt("base", 0)) {
            prefs.edit().putString("day", day).putInt("base", totalSinceBoot).apply();
        }
        stepsToday = Math.max(0, totalSinceBoot - prefs.getInt("base", totalSinceBoot));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
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
        boolean weatherEnabled = settings.showWeather || DashboardWidgetLayout.isExtraEnabled(
                context, DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER);
        if (!started || !weatherEnabled || city.isEmpty() || weatherLoading) {
            return;
        }
        long age = System.currentTimeMillis() - weatherUpdatedAt;
        if (!force && city.equals(weatherRequestCity) && age < WEATHER_REFRESH_MILLIS) {
            return;
        }
        weatherLoading = true;
        weatherRequestCity = city;
        publish();
        weatherExecutor.execute(() -> loadWeather(city));
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
            if (finalResult != null) {
                weatherPlace = finalResult.place;
                weatherTemperatureCelsius = finalResult.temperatureCelsius;
                weatherCode = finalResult.weatherCode;
                WeatherForecastState.set(finalResult.dates, finalResult.minimums,
                        finalResult.maximums, finalResult.codes);
            }
            publish();
            mainHandler.postDelayed(() -> refreshWeatherIfNeeded(false), WEATHER_REFRESH_MILLIS);
        });
    }

    private WeatherResult requestWeather(String city) throws IOException {
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8.name());
        String language = Locale.getDefault().getLanguage();
        JSONObject geocoding = requestJson(
                "https://geocoding-api.open-meteo.com/v1/search?name=" + encodedCity
                        + "&count=1&language=" + URLEncoder.encode(language, "UTF-8")
                        + "&format=json"
        );
        try {
            JSONArray results = geocoding.optJSONArray("results");
            if (results == null || results.length() == 0) {
                throw new IOException("City not found");
            }
            JSONObject place = results.getJSONObject(0);
            double latitude = place.getDouble("latitude");
            double longitude = place.getDouble("longitude");
            String displayName = place.optString("name", city);

            JSONObject forecast = requestJson(
                    "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                            + "&longitude=" + longitude
                            + "&current=temperature_2m,weather_code"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                            + "&forecast_days=4&timezone=auto"
            );
            JSONObject current = forecast.getJSONObject("current");
            JSONObject daily = forecast.getJSONObject("daily");
            JSONArray times = daily.getJSONArray("time");
            JSONArray minimumsJson = daily.getJSONArray("temperature_2m_min");
            JSONArray maximumsJson = daily.getJSONArray("temperature_2m_max");
            JSONArray codesJson = daily.getJSONArray("weather_code");
            int count = Math.min(4, times.length());
            String[] dates = new String[count];
            int[] minimums = new int[count];
            int[] maximums = new int[count];
            int[] codes = new int[count];
            for (int index = 0; index < count; index++) {
                dates[index] = times.getString(index);
                minimums[index] = (int) Math.round(minimumsJson.getDouble(index));
                maximums[index] = (int) Math.round(maximumsJson.getDouble(index));
                codes[index] = codesJson.getInt(index);
            }
            return new WeatherResult(
                    displayName,
                    (int) Math.round(current.getDouble("temperature_2m")),
                    current.getInt("weather_code"), dates, minimums, maximums, codes
            );
        } catch (JSONException error) {
            throw new IOException("Invalid weather data", error);
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
        refreshSystemStatsIfNeeded();
        refreshCalendar();
        AlarmManager.AlarmClockInfo nextAlarm = settings.showNextAlarm && alarmManager != null
                ? alarmManager.getNextAlarmClock()
                : null;
        MediaWidgetState.Snapshot media = MediaWidgetState.get();
        NotificationWidgetState.Snapshot notifications = NotificationWidgetState.get();
        listener.onDashboardDataChanged(new RearDashboardSnapshot(
                System.currentTimeMillis(),
                batteryPercent,
                temperatureTenthsCelsius,
                charging,
                weatherPlace,
                weatherTemperatureCelsius,
                weatherCode,
                weatherLoading,
                nextAlarm == null ? null : nextAlarm.getTriggerTime(),
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
                notifications.text
        ));
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
        long now = System.currentTimeMillis();
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(builder, now);
        android.content.ContentUris.appendId(builder, now + 24L * 60L * 60L * 1000L);
        String[] columns = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN};
        try (Cursor cursor = context.getContentResolver().query(builder.build(), columns,
                CalendarContract.Instances.END + ">?", new String[]{String.valueOf(now)},
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                calendarTitle = cursor.getString(0);
                calendarStartMillis = cursor.getLong(1);
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

        WeatherResult(String place, int temperatureCelsius, int weatherCode,
                String[] dates, int[] minimums, int[] maximums, int[] codes) {
            this.place = place;
            this.temperatureCelsius = temperatureCelsius;
            this.weatherCode = weatherCode;
            this.dates = dates;
            this.minimums = minimums;
            this.maximums = maximums;
            this.codes = codes;
        }
    }
}
