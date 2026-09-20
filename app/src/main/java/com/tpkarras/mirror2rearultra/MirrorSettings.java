package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class MirrorSettings {
    interface Listener {
        void onMirrorSettingsChanged(MirrorProfile profile);
    }

    private static final String PREFERENCES = "mirror_profiles";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";
    private static final String KEY_AUTO_PROFILE = "auto_profile";
    private static final String KEY_AUTO_VISIBILITY = "auto_visibility";
    private static final String KEY_PROJECTION_QUALITY = "projection_quality";
    private static final String KEY_CALIBRATION_GRID = "calibration_grid";
    private static final String KEY_TEMPERATURE_PROTECTION = "temperature_protection";
    private static final String KEY_TEMPERATURE_THRESHOLD = "temperature_threshold";
    private static final String KEY_BATTERY_PROTECTION = "battery_protection";
    private static final String KEY_BATTERY_THRESHOLD = "battery_threshold";
    private static final String KEY_DASHBOARD_MODE = "dashboard_mode";
    private static final String KEY_DASHBOARD_LAYOUT = "dashboard_layout";
    private static final String KEY_DASHBOARD_THEME = "dashboard_theme";
    private static final String KEY_DASHBOARD_CLOCK = "dashboard_clock";
    private static final String KEY_DASHBOARD_DATE = "dashboard_date";
    private static final String KEY_DASHBOARD_BATTERY = "dashboard_battery";
    private static final String KEY_DASHBOARD_TEMPERATURE = "dashboard_temperature";
    private static final String KEY_DASHBOARD_WEATHER = "dashboard_weather";
    private static final String KEY_DASHBOARD_NEXT_ALARM = "dashboard_next_alarm";
    private static final String KEY_DASHBOARD_MEDIA = "dashboard_media";
    private static final String KEY_DASHBOARD_COMPASS = "dashboard_compass";
    private static final String KEY_DASHBOARD_SPEED = "dashboard_speed";
    private static final String KEY_DASHBOARD_ALTITUDE = "dashboard_altitude";
    private static final String KEY_DASHBOARD_SESSION_TIMER = "dashboard_session_timer";
    private static final String KEY_DASHBOARD_ACTIVE_PROFILE = "dashboard_active_profile";
    private static final String KEY_DASHBOARD_CUSTOM_TEXT_ENABLED = "dashboard_custom_text_enabled";
    private static final String KEY_DASHBOARD_WEATHER_CITY = "dashboard_weather_city";
    private static final String KEY_DASHBOARD_CUSTOM_TEXT = "dashboard_custom_text";
    private static final String KEY_DASHBOARD_TEXT_SCALE = "dashboard_text_scale";
    private static final String KEY_DASHBOARD_BACKGROUND_OPACITY = "dashboard_background_opacity";
    private static final String KEY_DASHBOARD_CUSTOM_IMAGE = "dashboard_custom_image";
    private static final String KEY_DASHBOARD_CUSTOM_IMAGE_OPACITY = "dashboard_custom_image_opacity";
    private static final String KEY_CUSTOM_PROFILES = "custom_profiles";
    private static final String KEY_ASSIGNMENTS_INITIALIZED = "assignments_initialized";
    private static final String KEY_BRIGHTNESS_SCALE = "brightness_scale";
    /** Slider positions stopped being a share of output and became a perceived one. */
    private static final int BRIGHTNESS_SCALE_PERCEPTUAL = 2;
    private static final String APP_ASSIGNMENT_PREFIX = "app_assignment_";
    /** Main-thread or not, the conversion below is wanted exactly once. */
    private static volatile boolean brightnessScaleChecked;
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();

    private MirrorSettings() {
    }

    static MirrorProfile loadActiveProfile(Context context) {
        SharedPreferences preferences = preferences(context);
        String id = preferences.getString(KEY_ACTIVE_PROFILE, MirrorProfile.CAMERA_ID);
        if (!profileExists(preferences, id)) {
            id = MirrorProfile.CAMERA_ID;
        }
        return loadProfile(preferences, id);
    }

    static MirrorProfile selectProfile(Context context, String id) {
        SharedPreferences preferences = preferences(context);
        String selectedId = profileExists(preferences, id) ? id : MirrorProfile.CAMERA_ID;
        String previousId = preferences.getString(KEY_ACTIVE_PROFILE, MirrorProfile.CAMERA_ID);
        boolean switched = !selectedId.equals(previousId);
        if (switched && profileExists(preferences, previousId)) {
            // Keep what the outgoing profile was showing before the incoming
            // one overwrites it. There is one live arrangement and a snapshot
            // per profile, so without this an afternoon of rearranging the
            // panel under one profile was thrown away by the next switch,
            // with nothing to say it had happened. A profile that had no
            // snapshot gets one here: from now on it remembers its own panel
            // rather than inheriting whatever the last profile left behind.
            DashboardTemplateStore.save(context, previousId);
        }
        preferences.edit().putString(KEY_ACTIVE_PROFILE, selectedId).apply();
        if (switched) {
            // Dashboard arrangements are stored per profile but had to be
            // restored by hand, so switching to the driving profile left the
            // music layout on the panel. Applying it here covers every way the
            // profile can change, including the automatic app-based switch.
            // Does nothing for a profile that has never had one saved.
            DashboardTemplateStore.apply(context, selectedId);
        }
        MirrorProfile profile = loadProfile(preferences, selectedId);
        notifyListeners(profile);
        return profile;
    }

    static MirrorProfile selectProfile(Context context, MirrorProfile.Id id) {
        return selectProfile(context, id.name());
    }

    static void saveProfile(Context context, MirrorProfile profile) {
        String prefix = prefix(profile.id);
        SharedPreferences.Editor editor = preferences(context).edit()
                .putString(KEY_ACTIVE_PROFILE, profile.id)
                .putString(prefix + "scale", profile.scaleMode.name())
                .putInt(prefix + "rotation", profile.rotationDegrees)
                .putBoolean(prefix + "mirror", profile.mirrorHorizontally)
                .putInt(prefix + "brightness", profile.brightnessPercent)
                .putInt(prefix + "zoom", profile.zoomPercent)
                .putInt(prefix + "offset_x", profile.horizontalOffsetPercent)
                .putInt(prefix + "offset_y", profile.verticalOffsetPercent);
        if (profile.isCustom()) {
            editor.putString(prefix + "name", normalizeProfileName(profile.customName));
        }
        editor.apply();
        notifyListeners(profile);
    }

    static List<MirrorProfile> loadProfiles(Context context) {
        SharedPreferences preferences = preferences(context);
        List<MirrorProfile> profiles = new ArrayList<>();
        profiles.add(loadProfile(preferences, MirrorProfile.CAMERA_ID));
        profiles.add(loadProfile(preferences, MirrorProfile.NAVIGATION_ID));
        profiles.add(loadProfile(preferences, MirrorProfile.VIDEO_ID));

        List<MirrorProfile> customProfiles = new ArrayList<>();
        for (String id : customProfileIds(preferences)) {
            customProfiles.add(loadProfile(preferences, id));
        }
        customProfiles.sort(Comparator.comparing(
                profile -> normalizeProfileName(profile.customName).toLowerCase(Locale.ROOT)
        ));
        profiles.addAll(customProfiles);
        return profiles;
    }

    static MirrorProfile createCustomProfile(Context context, String name, MirrorProfile template) {
        SharedPreferences preferences = preferences(context);
        Set<String> ids = customProfileIds(preferences);
        String id = MirrorProfile.CUSTOM_PREFIX + System.currentTimeMillis();
        while (ids.contains(id)) {
            id += "_1";
        }
        ids.add(id);
        preferences.edit().putStringSet(KEY_CUSTOM_PROFILES, ids).apply();

        MirrorProfile source = template == null ? defaults(MirrorProfile.CAMERA_ID) : template;
        MirrorProfile profile = new MirrorProfile(
                id,
                normalizeProfileName(name),
                source.scaleMode,
                source.rotationDegrees,
                source.mirrorHorizontally,
                source.brightnessPercent,
                source.zoomPercent,
                source.horizontalOffsetPercent,
                source.verticalOffsetPercent
        );
        saveProfile(context, profile);
        return profile;
    }

    static MirrorProfile renameCustomProfile(Context context, MirrorProfile profile, String name) {
        if (profile == null || !profile.isCustom()) {
            return profile;
        }
        MirrorProfile renamed = profile.withCustomName(normalizeProfileName(name));
        saveProfile(context, renamed);
        return renamed;
    }

    static MirrorProfile deleteCustomProfile(Context context, String id) {
        SharedPreferences preferences = preferences(context);
        if (id == null || !id.startsWith(MirrorProfile.CUSTOM_PREFIX)) {
            return loadActiveProfile(context);
        }
        Set<String> ids = customProfileIds(preferences);
        if (!ids.remove(id)) {
            return loadActiveProfile(context);
        }

        Map<String, ?> all = preferences.getAll();
        SharedPreferences.Editor editor = preferences.edit().putStringSet(KEY_CUSTOM_PROFILES, ids);
        String profilePrefix = prefix(id);
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(profilePrefix)
                    || (entry.getKey().startsWith(APP_ASSIGNMENT_PREFIX)
                    && id.equals(entry.getValue()))) {
                editor.remove(entry.getKey());
            }
        }
        if (id.equals(preferences.getString(KEY_ACTIVE_PROFILE, MirrorProfile.CAMERA_ID))) {
            editor.putString(KEY_ACTIVE_PROFILE, MirrorProfile.CAMERA_ID);
        }
        editor.apply();
        MirrorProfile active = loadActiveProfile(context);
        notifyListeners(active);
        return active;
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    static boolean isAutoProfileEnabled(Context context) {
        return preferences(context).getBoolean(KEY_AUTO_PROFILE, false);
    }

    static void setAutoProfileEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_AUTO_PROFILE, enabled).apply();
        notifyListeners(loadActiveProfile(context));
    }

    static boolean isAutoVisibilityEnabled(Context context) {
        return preferences(context).getBoolean(KEY_AUTO_VISIBILITY, false);
    }

    static void setAutoVisibilityEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_AUTO_VISIBILITY, enabled).apply();
        notifyListeners(loadActiveProfile(context));
    }

    static ProjectionQuality loadProjectionQuality(Context context) {
        SharedPreferences preferences = preferences(context);
        return parseEnum(
                ProjectionQuality.class,
                preferences.getString(KEY_PROJECTION_QUALITY, ProjectionQuality.SHARP.name()),
                ProjectionQuality.SHARP
        );
    }

    static void setProjectionQuality(Context context, ProjectionQuality quality) {
        preferences(context).edit().putString(KEY_PROJECTION_QUALITY, quality.name()).apply();
        notifyListeners(loadActiveProfile(context));
    }

    static boolean isCalibrationGridEnabled(Context context) {
        return preferences(context).getBoolean(KEY_CALIBRATION_GRID, false);
    }

    static void setCalibrationGridEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_CALIBRATION_GRID, enabled).apply();
        notifyListeners(loadActiveProfile(context));
    }

    static boolean isTemperatureProtectionEnabled(Context context) {
        return preferences(context).getBoolean(KEY_TEMPERATURE_PROTECTION, true);
    }

    static void setTemperatureProtectionEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_TEMPERATURE_PROTECTION, enabled).apply();
        notifyListeners(loadActiveProfile(context));
    }

    static int loadTemperatureThreshold(Context context) {
        return Math.max(40, Math.min(55,
                preferences(context).getInt(KEY_TEMPERATURE_THRESHOLD, 45)));
    }

    static void setTemperatureThreshold(Context context, int celsius) {
        preferences(context).edit()
                .putInt(KEY_TEMPERATURE_THRESHOLD, Math.max(40, Math.min(55, celsius)))
                .apply();
        notifyListeners(loadActiveProfile(context));
    }

    static boolean isBatteryProtectionEnabled(Context context) {
        return preferences(context).getBoolean(KEY_BATTERY_PROTECTION, true);
    }

    static void setBatteryProtectionEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_BATTERY_PROTECTION, enabled).apply();
        notifyListeners(loadActiveProfile(context));
    }

    static int loadBatteryThreshold(Context context) {
        return Math.max(5, Math.min(30,
                preferences(context).getInt(KEY_BATTERY_THRESHOLD, 15)));
    }

    static void setBatteryThreshold(Context context, int percent) {
        preferences(context).edit()
                .putInt(KEY_BATTERY_THRESHOLD, Math.max(5, Math.min(30, percent)))
                .apply();
        notifyListeners(loadActiveProfile(context));
    }

    static DashboardSettings loadDashboardSettings(Context context) {
        SharedPreferences preferences = preferences(context);
        DashboardSettings defaults = DashboardSettings.defaults();
        return new DashboardSettings(
                parseEnum(
                        RearContentMode.class,
                        preferences.getString(KEY_DASHBOARD_MODE, defaults.contentMode.name()),
                        defaults.contentMode
                ),
                parseEnum(
                        DashboardSettings.Layout.class,
                        preferences.getString(KEY_DASHBOARD_LAYOUT, defaults.layout.name()),
                        defaults.layout
                ),
                parseEnum(
                        DashboardSettings.Theme.class,
                        preferences.getString(KEY_DASHBOARD_THEME, defaults.theme.name()),
                        defaults.theme
                ),
                preferences.getBoolean(KEY_DASHBOARD_CLOCK, defaults.showClock),
                preferences.getBoolean(KEY_DASHBOARD_DATE, defaults.showDate),
                preferences.getBoolean(KEY_DASHBOARD_BATTERY, defaults.showBattery),
                preferences.getBoolean(KEY_DASHBOARD_TEMPERATURE, defaults.showTemperature),
                preferences.getBoolean(KEY_DASHBOARD_WEATHER, defaults.showWeather),
                preferences.getBoolean(KEY_DASHBOARD_NEXT_ALARM, defaults.showNextAlarm),
                preferences.getBoolean(KEY_DASHBOARD_MEDIA, defaults.showMedia),
                preferences.getBoolean(KEY_DASHBOARD_COMPASS, defaults.showCompass),
                preferences.getBoolean(KEY_DASHBOARD_SPEED, defaults.showSpeed),
                preferences.getBoolean(KEY_DASHBOARD_ALTITUDE, defaults.showAltitude),
                preferences.getBoolean(KEY_DASHBOARD_SESSION_TIMER, defaults.showSessionTimer),
                preferences.getBoolean(KEY_DASHBOARD_ACTIVE_PROFILE, defaults.showActiveProfile),
                preferences.getBoolean(
                        KEY_DASHBOARD_CUSTOM_TEXT_ENABLED,
                        defaults.showCustomText
                ),
                preferences.getString(KEY_DASHBOARD_WEATHER_CITY, defaults.weatherCity),
                preferences.getString(KEY_DASHBOARD_CUSTOM_TEXT, defaults.customText),
                preferences.getInt(KEY_DASHBOARD_TEXT_SCALE, defaults.textScalePercent),
                preferences.getInt(
                        KEY_DASHBOARD_BACKGROUND_OPACITY,
                        defaults.backgroundOpacityPercent
                ),
                preferences.getBoolean(KEY_DASHBOARD_CUSTOM_IMAGE, defaults.showCustomImage),
                preferences.getInt(KEY_DASHBOARD_CUSTOM_IMAGE_OPACITY,
                        defaults.customImageOpacityPercent)
        );
    }

    static void saveDashboardSettings(Context context, DashboardSettings settings) {
        SharedPreferences.Editor editor = preferences(context).edit();
        writeDashboardSettings(editor, settings).apply();
        notifyListeners(loadActiveProfile(context));
    }

    static MirrorProfile loadProfile(Context context, String id) {
        return loadProfile(preferences(context), id);
    }

    static MirrorProfile loadProfile(Context context, MirrorProfile.Id id) {
        return loadProfile(preferences(context), id.name());
    }

    static void initializeDefaultAssignments(Context context, Set<String> launchablePackages) {
        SharedPreferences preferences = preferences(context);
        if (preferences.getBoolean(KEY_ASSIGNMENTS_INITIALIZED, false)) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (String packageName : launchablePackages) {
            String profileId = AppProfileClassifier.classify(packageName);
            if (profileId != null) {
                editor.putString(APP_ASSIGNMENT_PREFIX + packageName, profileId);
            }
        }
        editor.putBoolean(KEY_ASSIGNMENTS_INITIALIZED, true).apply();
    }

    @Nullable
    static String assignedProfileId(Context context, String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return null;
        }
        SharedPreferences preferences = preferences(context);
        if (!preferences.getBoolean(KEY_ASSIGNMENTS_INITIALIZED, false)) {
            return AppProfileClassifier.classify(packageName);
        }
        String id = preferences.getString(APP_ASSIGNMENT_PREFIX + packageName, null);
        return profileExists(preferences, id) ? id : null;
    }

    static void assignApp(Context context, String packageName, @Nullable String profileId) {
        if (packageName == null || packageName.isBlank()) {
            return;
        }
        SharedPreferences preferences = preferences(context);
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_ASSIGNMENTS_INITIALIZED, true);
        String key = APP_ASSIGNMENT_PREFIX + packageName;
        if (profileExists(preferences, profileId)) {
            editor.putString(key, profileId);
        } else {
            editor.remove(key);
        }
        editor.apply();
        notifyListeners(loadActiveProfile(context));
    }

    static Set<String> assignedPackages(Context context, String profileId) {
        Set<String> packages = new HashSet<>();
        for (Map.Entry<String, ?> entry : preferences(context).getAll().entrySet()) {
            if (entry.getKey().startsWith(APP_ASSIGNMENT_PREFIX)
                    && profileId.equals(entry.getValue())) {
                packages.add(entry.getKey().substring(APP_ASSIGNMENT_PREFIX.length()));
            }
        }
        return packages;
    }

    static byte[] exportBackup(Context context) throws IOException {
        List<MirrorProfile> profiles = loadProfiles(context);
        Map<String, String> assignments = new LinkedHashMap<>();
        for (MirrorProfile profile : profiles) {
            List<String> packages = new ArrayList<>(assignedPackages(context, profile.id));
            packages.sort(String::compareTo);
            for (String packageName : packages) {
                assignments.put(packageName, profile.id);
            }
        }
        SettingsBackupCodec.Data data = new SettingsBackupCodec.Data(
                loadActiveProfile(context).id,
                loadProjectionQuality(context),
                isAutoProfileEnabled(context),
                isAutoVisibilityEnabled(context),
                isCalibrationGridEnabled(context),
                isTemperatureProtectionEnabled(context),
                loadTemperatureThreshold(context),
                isBatteryProtectionEnabled(context),
                loadBatteryThreshold(context),
                loadDashboardSettings(context),
                profiles,
                assignments,
                DashboardImageStore.read(context),
                DashboardWidgetLayout.exportState(context),
                DashboardTemplateStore.exportState(context)
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SettingsBackupCodec.write(data, output);
        return output.toByteArray();
    }

    static SettingsBackupCodec.Data decodeBackup(byte[] bytes)
            throws SettingsBackupCodec.BackupException {
        if (bytes == null || bytes.length == 0 || bytes.length > SettingsBackupCodec.MAX_BACKUP_BYTES) {
            throw new SettingsBackupCodec.BackupException("invalid_size");
        }
        return SettingsBackupCodec.read(new ByteArrayInputStream(bytes));
    }

    static MirrorProfile applyBackup(Context context, SettingsBackupCodec.Data data)
            throws SettingsBackupCodec.BackupException {
        SharedPreferences.Editor editor = preferences(context).edit().clear()
                .putString(KEY_ACTIVE_PROFILE, data.activeProfileId)
                .putString(KEY_PROJECTION_QUALITY, data.projectionQuality.name())
                .putBoolean(KEY_AUTO_PROFILE, data.autoProfileEnabled)
                .putBoolean(KEY_AUTO_VISIBILITY, data.autoVisibilityEnabled)
                .putBoolean(KEY_CALIBRATION_GRID, data.calibrationGridEnabled)
                .putBoolean(KEY_TEMPERATURE_PROTECTION, data.temperatureProtectionEnabled)
                .putInt(KEY_TEMPERATURE_THRESHOLD, data.temperatureThreshold)
                .putBoolean(KEY_BATTERY_PROTECTION, data.batteryProtectionEnabled)
                .putInt(KEY_BATTERY_THRESHOLD, data.batteryThreshold)
                .putBoolean(KEY_ASSIGNMENTS_INITIALIZED, true);
        writeDashboardSettings(editor, data.dashboardSettings);

        Set<String> customIds = new LinkedHashSet<>();
        for (MirrorProfile profile : data.profiles) {
            if (profile.isCustom()) {
                customIds.add(profile.id);
            }
            String profilePrefix = prefix(profile.id);
            editor.putString(profilePrefix + "scale", profile.scaleMode.name())
                    .putInt(profilePrefix + "rotation", profile.rotationDegrees)
                    .putBoolean(profilePrefix + "mirror", profile.mirrorHorizontally)
                    .putInt(profilePrefix + "brightness", profile.brightnessPercent)
                    .putInt(profilePrefix + "zoom", profile.zoomPercent)
                    .putInt(profilePrefix + "offset_x", profile.horizontalOffsetPercent)
                    .putInt(profilePrefix + "offset_y", profile.verticalOffsetPercent);
            if (profile.isCustom()) {
                editor.putString(profilePrefix + "name", normalizeProfileName(profile.customName));
            }
        }
        editor.putStringSet(KEY_CUSTOM_PROFILES, customIds);
        for (Map.Entry<String, String> assignment : data.assignments.entrySet()) {
            editor.putString(APP_ASSIGNMENT_PREFIX + assignment.getKey(), assignment.getValue());
        }
        if (!editor.commit()) {
            throw new SettingsBackupCodec.BackupException("write_failed");
        }
        try {
            DashboardImageStore.restore(context, data.dashboardImage);
        } catch (IOException error) {
            throw new SettingsBackupCodec.BackupException("image_write_failed", error);
        }
        DashboardWidgetLayout.importState(context, data.dashboardLayoutState);
        DashboardTemplateStore.importState(context, data.dashboardTemplateState);
        MirrorProfile active = loadActiveProfile(context);
        notifyListeners(active);
        return active;
    }

    static String profileDisplayName(Context context, MirrorProfile profile) {
        if (profile == null) {
            return context.getString(R.string.profile_camera);
        }
        switch (profile.id) {
            case MirrorProfile.NAVIGATION_ID:
                return context.getString(R.string.profile_navigation);
            case MirrorProfile.VIDEO_ID:
                return context.getString(R.string.profile_video);
            case MirrorProfile.CAMERA_ID:
                return context.getString(R.string.profile_camera);
            default:
                return profile.customName == null || profile.customName.isBlank()
                        ? context.getString(R.string.default_custom_profile_name)
                        : profile.customName;
        }
    }

    private static SharedPreferences preferences(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        migrateBrightnessScale(preferences);
        return preferences;
    }

    /**
     * Re-reads saved brightnesses on the scale they are now written in.
     *
     * <p>A profile saved before {@link PerceptualBrightness} existed holds a
     * share of the panel's output. Left alone, the same number read as a
     * slider position would leave every profile a fraction of the brightness
     * its owner chose, so each one is converted to the position that asks for
     * the light it already had. Nothing on the panel changes; only where the
     * slider's handle sits.
     */
    private static void migrateBrightnessScale(SharedPreferences preferences) {
        if (brightnessScaleChecked) {
            return;
        }
        synchronized (MirrorSettings.class) {
            if (brightnessScaleChecked) {
                return;
            }
            brightnessScaleChecked = true;
            if (preferences.getInt(KEY_BRIGHTNESS_SCALE, 1) >= BRIGHTNESS_SCALE_PERCEPTUAL) {
                return;
            }
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                if (!entry.getKey().endsWith("_brightness")
                        || !(entry.getValue() instanceof Integer)) {
                    continue;
                }
                editor.putInt(entry.getKey(),
                        PerceptualBrightness.toPercent((Integer) entry.getValue() / 100f));
            }
            editor.putInt(KEY_BRIGHTNESS_SCALE, BRIGHTNESS_SCALE_PERCEPTUAL).apply();
        }
    }

    private static SharedPreferences.Editor writeDashboardSettings(
            SharedPreferences.Editor editor,
            DashboardSettings settings
    ) {
        return editor
                .putString(KEY_DASHBOARD_MODE, settings.contentMode.name())
                .putString(KEY_DASHBOARD_LAYOUT, settings.layout.name())
                .putString(KEY_DASHBOARD_THEME, settings.theme.name())
                .putBoolean(KEY_DASHBOARD_CLOCK, settings.showClock)
                .putBoolean(KEY_DASHBOARD_DATE, settings.showDate)
                .putBoolean(KEY_DASHBOARD_BATTERY, settings.showBattery)
                .putBoolean(KEY_DASHBOARD_TEMPERATURE, settings.showTemperature)
                .putBoolean(KEY_DASHBOARD_WEATHER, settings.showWeather)
                .putBoolean(KEY_DASHBOARD_NEXT_ALARM, settings.showNextAlarm)
                .putBoolean(KEY_DASHBOARD_MEDIA, settings.showMedia)
                .putBoolean(KEY_DASHBOARD_COMPASS, settings.showCompass)
                .putBoolean(KEY_DASHBOARD_SPEED, settings.showSpeed)
                .putBoolean(KEY_DASHBOARD_ALTITUDE, settings.showAltitude)
                .putBoolean(KEY_DASHBOARD_SESSION_TIMER, settings.showSessionTimer)
                .putBoolean(KEY_DASHBOARD_ACTIVE_PROFILE, settings.showActiveProfile)
                .putBoolean(KEY_DASHBOARD_CUSTOM_TEXT_ENABLED, settings.showCustomText)
                .putString(KEY_DASHBOARD_WEATHER_CITY, settings.weatherCity)
                .putString(KEY_DASHBOARD_CUSTOM_TEXT, settings.customText)
                .putInt(KEY_DASHBOARD_TEXT_SCALE, settings.textScalePercent)
                .putInt(KEY_DASHBOARD_BACKGROUND_OPACITY, settings.backgroundOpacityPercent)
                .putBoolean(KEY_DASHBOARD_CUSTOM_IMAGE, settings.showCustomImage)
                .putInt(KEY_DASHBOARD_CUSTOM_IMAGE_OPACITY, settings.customImageOpacityPercent);
    }

    private static MirrorProfile loadProfile(SharedPreferences preferences, String requestedId) {
        String id = profileExists(preferences, requestedId) ? requestedId : MirrorProfile.CAMERA_ID;
        MirrorProfile defaults = defaults(id);
        String prefix = prefix(id);
        return new MirrorProfile(
                id,
                defaults.isCustom()
                        ? preferences.getString(prefix + "name", defaults.customName)
                        : null,
                parseEnum(
                        MirrorProfile.ScaleMode.class,
                        preferences.getString(prefix + "scale", defaults.scaleMode.name()),
                        defaults.scaleMode
                ),
                preferences.getInt(prefix + "rotation", defaults.rotationDegrees),
                preferences.getBoolean(prefix + "mirror", defaults.mirrorHorizontally),
                preferences.getInt(prefix + "brightness", defaults.brightnessPercent),
                preferences.getInt(prefix + "zoom", defaults.zoomPercent),
                preferences.getInt(prefix + "offset_x", defaults.horizontalOffsetPercent),
                preferences.getInt(prefix + "offset_y", defaults.verticalOffsetPercent)
        );
    }

    private static MirrorProfile defaults(String id) {
        switch (id) {
            case MirrorProfile.NAVIGATION_ID:
                return new MirrorProfile(MirrorProfile.Id.NAVIGATION,
                        MirrorProfile.ScaleMode.FIT, 0, false, 96);
            case MirrorProfile.VIDEO_ID:
                return new MirrorProfile(MirrorProfile.Id.VIDEO,
                        MirrorProfile.ScaleMode.FIT, 90, false, 93);
            case MirrorProfile.CAMERA_ID:
                return new MirrorProfile(MirrorProfile.Id.CAMERA,
                        MirrorProfile.ScaleMode.FILL, 0, true, 100);
            default:
                return new MirrorProfile(id, "", MirrorProfile.ScaleMode.FILL,
                        0, false, 100, 100, 0, 0);
        }
    }

    private static boolean profileExists(SharedPreferences preferences, @Nullable String id) {
        return MirrorProfile.CAMERA_ID.equals(id)
                || MirrorProfile.NAVIGATION_ID.equals(id)
                || MirrorProfile.VIDEO_ID.equals(id)
                || (id != null && customProfileIds(preferences).contains(id));
    }

    private static Set<String> customProfileIds(SharedPreferences preferences) {
        return new LinkedHashSet<>(preferences.getStringSet(KEY_CUSTOM_PROFILES, Set.of()));
    }

    private static String prefix(String id) {
        return id.toLowerCase(Locale.ROOT) + "_";
    }

    private static String normalizeProfileName(String value) {
        if (value == null || value.isBlank()) {
            return "Profile";
        }
        String normalized = value.trim();
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }

    private static void notifyListeners(MirrorProfile profile) {
        for (Listener listener : LISTENERS) {
            listener.onMirrorSettingsChanged(profile);
        }
    }
}
