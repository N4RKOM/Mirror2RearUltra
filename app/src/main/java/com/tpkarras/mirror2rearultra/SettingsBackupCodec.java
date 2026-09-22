package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class SettingsBackupCodec {
    static final int SCHEMA_VERSION = 2;
    /** The last schema that wrote brightness as a share of the panel's output. */
    private static final int SCHEMA_LINEAR_BRIGHTNESS = 1;
    static final int MAX_BACKUP_BYTES = 1_048_576;
    private static final String FORMAT = "Mirror2RearUltra settings";
    private static final int MAX_PROFILES = 50;
    private static final int MAX_ASSIGNMENTS = 2_000;
    private static final int MAX_DASHBOARD_STATE_ENTRIES = 2_000;

    static final class Data {
        final String activeProfileId;
        final ProjectionQuality projectionQuality;
        final boolean autoProfileEnabled;
        final boolean autoVisibilityEnabled;
        final boolean calibrationGridEnabled;
        final boolean temperatureProtectionEnabled;
        final int temperatureThreshold;
        final boolean batteryProtectionEnabled;
        final int batteryThreshold;
        final DashboardSettings dashboardSettings;
        final List<MirrorProfile> profiles;
        final Map<String, String> assignments;
        final byte[] dashboardImage;
        final Map<String, String> dashboardLayoutState;
        final Map<String, String> dashboardTemplateState;

        Data(
                String activeProfileId,
                ProjectionQuality projectionQuality,
                boolean autoProfileEnabled,
                boolean autoVisibilityEnabled,
                boolean calibrationGridEnabled,
                boolean temperatureProtectionEnabled,
                int temperatureThreshold,
                boolean batteryProtectionEnabled,
                int batteryThreshold,
                List<MirrorProfile> profiles,
                Map<String, String> assignments
        ) {
            this(
                    activeProfileId,
                    projectionQuality,
                    autoProfileEnabled,
                    autoVisibilityEnabled,
                    calibrationGridEnabled,
                    temperatureProtectionEnabled,
                    temperatureThreshold,
                    batteryProtectionEnabled,
                    batteryThreshold,
                    DashboardSettings.defaults(),
                    profiles,
                    assignments,
                    null
            );
        }

        Data(
                String activeProfileId,
                ProjectionQuality projectionQuality,
                boolean autoProfileEnabled,
                boolean autoVisibilityEnabled,
                boolean calibrationGridEnabled,
                boolean temperatureProtectionEnabled,
                int temperatureThreshold,
                boolean batteryProtectionEnabled,
                int batteryThreshold,
                DashboardSettings dashboardSettings,
                List<MirrorProfile> profiles,
            Map<String, String> assignments
        ) {
            this(activeProfileId, projectionQuality, autoProfileEnabled, autoVisibilityEnabled,
                    calibrationGridEnabled, temperatureProtectionEnabled, temperatureThreshold,
                    batteryProtectionEnabled, batteryThreshold, dashboardSettings, profiles,
                    assignments, null);
        }

        Data(
                String activeProfileId, ProjectionQuality projectionQuality,
                boolean autoProfileEnabled, boolean autoVisibilityEnabled,
                boolean calibrationGridEnabled, boolean temperatureProtectionEnabled,
                int temperatureThreshold, boolean batteryProtectionEnabled, int batteryThreshold,
                DashboardSettings dashboardSettings, List<MirrorProfile> profiles,
                Map<String, String> assignments, byte[] dashboardImage
        ) {
            this(activeProfileId, projectionQuality, autoProfileEnabled, autoVisibilityEnabled,
                    calibrationGridEnabled, temperatureProtectionEnabled, temperatureThreshold,
                    batteryProtectionEnabled, batteryThreshold, dashboardSettings, profiles,
                    assignments, dashboardImage, Collections.emptyMap(), Collections.emptyMap());
        }

        Data(
                String activeProfileId, ProjectionQuality projectionQuality,
                boolean autoProfileEnabled, boolean autoVisibilityEnabled,
                boolean calibrationGridEnabled, boolean temperatureProtectionEnabled,
                int temperatureThreshold, boolean batteryProtectionEnabled, int batteryThreshold,
                DashboardSettings dashboardSettings, List<MirrorProfile> profiles,
                Map<String, String> assignments, byte[] dashboardImage,
                Map<String, String> dashboardLayoutState,
                Map<String, String> dashboardTemplateState
        ) {
            this.activeProfileId = activeProfileId;
            this.projectionQuality = projectionQuality;
            this.autoProfileEnabled = autoProfileEnabled;
            this.autoVisibilityEnabled = autoVisibilityEnabled;
            this.calibrationGridEnabled = calibrationGridEnabled;
            this.temperatureProtectionEnabled = temperatureProtectionEnabled;
            this.temperatureThreshold = temperatureThreshold;
            this.batteryProtectionEnabled = batteryProtectionEnabled;
            this.batteryThreshold = batteryThreshold;
            this.dashboardSettings = dashboardSettings;
            this.profiles = List.copyOf(profiles);
            this.assignments = Collections.unmodifiableMap(new LinkedHashMap<>(assignments));
            this.dashboardImage = dashboardImage == null ? null : dashboardImage.clone();
            this.dashboardLayoutState = immutableCopy(dashboardLayoutState);
            this.dashboardTemplateState = immutableCopy(dashboardTemplateState);
        }

        private static Map<String, String> immutableCopy(Map<String, String> source) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }

    static final class BackupException extends Exception {
        BackupException(String message) {
            super(message);
        }

        BackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private SettingsBackupCodec() {
    }

    static void write(Data data, OutputStream outputStream) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", FORMAT);
        properties.setProperty("schema", String.valueOf(SCHEMA_VERSION));
        properties.setProperty("activeProfile", data.activeProfileId);
        properties.setProperty("projectionQuality", data.projectionQuality.name());
        properties.setProperty("autoProfile", String.valueOf(data.autoProfileEnabled));
        properties.setProperty("autoVisibility", String.valueOf(data.autoVisibilityEnabled));
        properties.setProperty("calibrationGrid", String.valueOf(data.calibrationGridEnabled));
        properties.setProperty("temperatureProtection",
                String.valueOf(data.temperatureProtectionEnabled));
        properties.setProperty("temperatureThreshold", String.valueOf(data.temperatureThreshold));
        properties.setProperty("batteryProtection", String.valueOf(data.batteryProtectionEnabled));
        properties.setProperty("batteryThreshold", String.valueOf(data.batteryThreshold));
        properties.setProperty("dashboard.mode", data.dashboardSettings.contentMode.name());
        properties.setProperty("dashboard.layout", data.dashboardSettings.layout.name());
        properties.setProperty("dashboard.theme", data.dashboardSettings.theme.name());
        properties.setProperty("dashboard.clock", String.valueOf(data.dashboardSettings.showClock));
        properties.setProperty("dashboard.date", String.valueOf(data.dashboardSettings.showDate));
        properties.setProperty("dashboard.battery", String.valueOf(data.dashboardSettings.showBattery));
        properties.setProperty("dashboard.temperature",
                String.valueOf(data.dashboardSettings.showTemperature));
        properties.setProperty("dashboard.weather", String.valueOf(data.dashboardSettings.showWeather));
        properties.setProperty("dashboard.nextAlarm",
                String.valueOf(data.dashboardSettings.showNextAlarm));
        properties.setProperty("dashboard.media", String.valueOf(data.dashboardSettings.showMedia));
        properties.setProperty("dashboard.compass", String.valueOf(data.dashboardSettings.showCompass));
        properties.setProperty("dashboard.speed", String.valueOf(data.dashboardSettings.showSpeed));
        properties.setProperty("dashboard.altitude", String.valueOf(data.dashboardSettings.showAltitude));
        properties.setProperty("dashboard.sessionTimer",
                String.valueOf(data.dashboardSettings.showSessionTimer));
        properties.setProperty("dashboard.activeProfile",
                String.valueOf(data.dashboardSettings.showActiveProfile));
        properties.setProperty("dashboard.customTextEnabled",
                String.valueOf(data.dashboardSettings.showCustomText));
        properties.setProperty("dashboard.weatherCity", data.dashboardSettings.weatherCity);
        properties.setProperty("dashboard.customText", data.dashboardSettings.customText);
        properties.setProperty("dashboard.textScale",
                String.valueOf(data.dashboardSettings.textScalePercent));
        properties.setProperty("dashboard.backgroundOpacity",
                String.valueOf(data.dashboardSettings.backgroundOpacityPercent));
        properties.setProperty("dashboard.customImageEnabled",
                String.valueOf(data.dashboardSettings.showCustomImage));
        properties.setProperty("dashboard.customImageOpacity",
                String.valueOf(data.dashboardSettings.customImageOpacityPercent));
        if (data.dashboardImage != null) {
            properties.setProperty("dashboard.image", Base64.getEncoder().encodeToString(data.dashboardImage));
        }

        properties.setProperty("profile.count", String.valueOf(data.profiles.size()));
        for (int index = 0; index < data.profiles.size(); index++) {
            MirrorProfile profile = data.profiles.get(index);
            String prefix = "profile." + index + ".";
            properties.setProperty(prefix + "id", profile.id);
            properties.setProperty(prefix + "name", profile.customName == null ? "" : profile.customName);
            properties.setProperty(prefix + "scale", profile.scaleMode.name());
            properties.setProperty(prefix + "rotation", String.valueOf(profile.rotationDegrees));
            properties.setProperty(prefix + "mirror", String.valueOf(profile.mirrorHorizontally));
            properties.setProperty(prefix + "brightness", String.valueOf(profile.brightnessPercent));
            properties.setProperty(prefix + "zoom", String.valueOf(profile.zoomPercent));
            properties.setProperty(prefix + "offsetX", String.valueOf(profile.horizontalOffsetPercent));
            properties.setProperty(prefix + "offsetY", String.valueOf(profile.verticalOffsetPercent));
            if (profile.crop != null) {
                // One line rather than five keys: a frame is read and written
                // whole, and an older file simply has no line at all.
                properties.setProperty(prefix + "crop", String.format(
                        java.util.Locale.ROOT, "%f,%f,%f,%f,%d",
                        profile.crop.left, profile.crop.top,
                        profile.crop.right, profile.crop.bottom, profile.crop.rotation));
            }
        }

        properties.setProperty("assignment.count", String.valueOf(data.assignments.size()));
        int assignmentIndex = 0;
        for (Map.Entry<String, String> assignment : data.assignments.entrySet()) {
            String prefix = "assignment." + assignmentIndex++ + ".";
            properties.setProperty(prefix + "package", assignment.getKey());
            properties.setProperty(prefix + "profile", assignment.getValue());
        }
        writeMap(properties, "dashboardLayout", data.dashboardLayoutState);
        writeMap(properties, "dashboardTemplate", data.dashboardTemplateState);
        properties.storeToXML(outputStream, "Mirror2RearUltra settings backup", "UTF-8");
    }

    static Data read(InputStream inputStream) throws BackupException {
        Properties properties = new Properties();
        try {
            properties.loadFromXML(inputStream);
        } catch (IOException | IllegalArgumentException error) {
            throw new BackupException("invalid_xml", error);
        }
        if (!FORMAT.equals(required(properties, "format"))) {
            throw new BackupException("wrong_format");
        }
        // Older files are read on the scale they were written in; a newer one
        // is refused, because there is no knowing what it changed.
        int schema = integer(properties, "schema", 1, Integer.MAX_VALUE);
        if (schema > SCHEMA_VERSION) {
            throw new BackupException("unsupported_schema");
        }

        int profileCount = integer(properties, "profile.count", 3, MAX_PROFILES);
        List<MirrorProfile> profiles = new ArrayList<>(profileCount);
        Set<String> profileIds = new HashSet<>();
        for (int index = 0; index < profileCount; index++) {
            String prefix = "profile." + index + ".";
            String id = profileId(required(properties, prefix + "id"));
            if (!profileIds.add(id)) {
                throw new BackupException("duplicate_profile");
            }
            String name = properties.getProperty(prefix + "name", "").trim();
            if (id.startsWith(MirrorProfile.CUSTOM_PREFIX)) {
                if (id.length() == MirrorProfile.CUSTOM_PREFIX.length()
                        || name.isEmpty()
                        || name.length() > 40) {
                    throw new BackupException("invalid_profile_name");
                }
            } else if (!isBuiltInProfile(id)) {
                throw new BackupException("invalid_profile_id");
            }
            MirrorProfile.ScaleMode scaleMode = enumValue(
                    MirrorProfile.ScaleMode.class,
                    required(properties, prefix + "scale")
            );
            int rotation = integer(properties, prefix + "rotation", 0, 270);
            if (rotation % 90 != 0) {
                throw new BackupException("invalid_rotation");
            }
            MirrorProfile profile = new MirrorProfile(
                    id,
                    id.startsWith(MirrorProfile.CUSTOM_PREFIX) ? name : null,
                    scaleMode,
                    rotation,
                    bool(properties, prefix + "mirror"),
                    brightness(properties, prefix + "brightness", schema),
                    integer(properties, prefix + "zoom", 100, 200),
                    integer(properties, prefix + "offsetX", -50, 50),
                    integer(properties, prefix + "offsetY", -50, 50),
                    crop(properties, prefix + "crop")
            );
            profiles.add(profile);
        }
        if (!profileIds.containsAll(Set.of(
                MirrorProfile.CAMERA_ID,
                MirrorProfile.NAVIGATION_ID,
                MirrorProfile.VIDEO_ID
        ))) {
            throw new BackupException("missing_builtin_profile");
        }

        String activeProfile = profileId(required(properties, "activeProfile"));
        if (!profileIds.contains(activeProfile)) {
            throw new BackupException("missing_active_profile");
        }

        int assignmentCount = integer(properties, "assignment.count", 0, MAX_ASSIGNMENTS);
        Map<String, String> assignments = new LinkedHashMap<>();
        for (int index = 0; index < assignmentCount; index++) {
            String prefix = "assignment." + index + ".";
            String packageName = required(properties, prefix + "package").trim();
            if (!validPackageName(packageName)) {
                throw new BackupException("invalid_package");
            }
            String profileId = profileId(required(properties, prefix + "profile"));
            if (!profileIds.contains(profileId) || assignments.put(packageName, profileId) != null) {
                throw new BackupException("invalid_assignment");
            }
        }
        Map<String, String> dashboardLayoutState = readMap(properties, "dashboardLayout");
        Map<String, String> dashboardTemplateState = readMap(properties, "dashboardTemplate");

        String weatherCity = properties.getProperty("dashboard.weatherCity", "").trim();
        if (weatherCity.length() > DashboardSettings.MAX_CITY_LENGTH) {
            throw new BackupException("weather_city_too_long");
        }
        String customText = properties.getProperty("dashboard.customText", "").trim();
        if (customText.length() > DashboardSettings.MAX_CUSTOM_TEXT_LENGTH) {
            throw new BackupException("custom_text_too_long");
        }
        byte[] dashboardImage = null;
        if (properties.containsKey("dashboard.image")) {
            try {
                dashboardImage = Base64.getDecoder().decode(properties.getProperty("dashboard.image"));
            } catch (IllegalArgumentException error) {
                throw new BackupException("invalid_dashboard_image", error);
            }
            if (dashboardImage.length > DashboardImageStore.MAX_BACKUP_BYTES) {
                throw new BackupException("dashboard_image_too_large");
            }
        }
        DashboardSettings dashboardSettings = new DashboardSettings(
                optionalEnum(
                        properties,
                        "dashboard.mode",
                        RearContentMode.class,
                        RearContentMode.MIRROR
                ),
                optionalEnum(
                        properties,
                        "dashboard.layout",
                        DashboardSettings.Layout.class,
                        DashboardSettings.Layout.STACKED
                ),
                optionalEnum(
                        properties,
                        "dashboard.theme",
                        DashboardSettings.Theme.class,
                        DashboardSettings.Theme.SYSTEM
                ),
                optionalBool(properties, "dashboard.clock", true),
                optionalBool(properties, "dashboard.date", true),
                optionalBool(properties, "dashboard.battery", true),
                optionalBool(properties, "dashboard.temperature", true),
                optionalBool(properties, "dashboard.weather", false),
                optionalBool(properties, "dashboard.nextAlarm", false),
                optionalBool(properties, "dashboard.media", false),
                optionalBool(properties, "dashboard.compass", false),
                optionalBool(properties, "dashboard.speed", false),
                optionalBool(properties, "dashboard.altitude", false),
                optionalBool(properties, "dashboard.sessionTimer", false),
                optionalBool(properties, "dashboard.activeProfile", false),
                optionalBool(properties, "dashboard.customTextEnabled", false),
                weatherCity,
                customText,
                optionalInteger(
                        properties,
                        "dashboard.textScale",
                        DashboardSettings.MIN_TEXT_SCALE,
                        DashboardSettings.MAX_TEXT_SCALE,
                        100
                ),
                optionalInteger(
                        properties,
                        "dashboard.backgroundOpacity",
                        DashboardSettings.MIN_BACKGROUND_OPACITY,
                        DashboardSettings.MAX_BACKGROUND_OPACITY,
                        55
                ),
                optionalBool(properties, "dashboard.customImageEnabled", false),
                optionalInteger(properties, "dashboard.customImageOpacity",
                        DashboardSettings.MIN_IMAGE_OPACITY,
                        DashboardSettings.MAX_IMAGE_OPACITY, 70)
        );

        return new Data(
                activeProfile,
                enumValue(ProjectionQuality.class, required(properties, "projectionQuality")),
                bool(properties, "autoProfile"),
                bool(properties, "autoVisibility"),
                bool(properties, "calibrationGrid"),
                bool(properties, "temperatureProtection"),
                integer(properties, "temperatureThreshold", 40, 55),
                bool(properties, "batteryProtection"),
                integer(properties, "batteryThreshold", 5, 30),
                dashboardSettings,
                profiles,
                assignments,
                dashboardImage,
                dashboardLayoutState,
                dashboardTemplateState
        );
    }

    private static void writeMap(Properties properties, String name, Map<String, String> values) {
        properties.setProperty(name + ".count", String.valueOf(values.size()));
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String prefix = name + "." + index++ + ".";
            properties.setProperty(prefix + "key", entry.getKey());
            properties.setProperty(prefix + "value", entry.getValue());
        }
    }

    private static Map<String, String> readMap(Properties properties, String name)
            throws BackupException {
        int count = optionalInteger(properties, name + ".count", 0,
                MAX_DASHBOARD_STATE_ENTRIES, 0);
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = name + "." + index + ".";
            String key = required(properties, prefix + "key");
            String value = required(properties, prefix + "value");
            if (key.isEmpty() || key.length() > 100 || value.length() > 2_000
                    || result.put(key, value) != null) {
                throw new BackupException("invalid_" + name);
            }
        }
        return result;
    }

    private static String required(Properties properties, String key) throws BackupException {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new BackupException("missing_" + key);
        }
        return value;
    }

    /** A frame, when the file has one: four fractions and a rotation. */
    @Nullable
    private static MirrorProfile.Crop crop(Properties properties, String key)
            throws BackupException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 5) {
            throw new BackupException("invalid_crop");
        }
        try {
            float left = Float.parseFloat(parts[0]);
            float top = Float.parseFloat(parts[1]);
            float right = Float.parseFloat(parts[2]);
            float bottom = Float.parseFloat(parts[3]);
            int rotation = Integer.parseInt(parts[4]);
            if (left < 0f || top < 0f || right > 1f || bottom > 1f
                    || right <= left || bottom <= top || rotation < 0 || rotation > 3) {
                throw new BackupException("invalid_crop");
            }
            return new MirrorProfile.Crop(left, top, right, bottom, rotation);
        } catch (NumberFormatException error) {
            throw new BackupException("invalid_crop", error);
        }
    }

    private static boolean bool(Properties properties, String key) throws BackupException {
        String value = required(properties, key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new BackupException("invalid_boolean");
        }
        return Boolean.parseBoolean(value);
    }

    private static boolean optionalBool(
            Properties properties,
            String key,
            boolean fallback
    ) throws BackupException {
        return properties.containsKey(key) ? bool(properties, key) : fallback;
    }

    /**
     * Reads a saved brightness on the scale the file was written in.
     *
     * <p>A file from before {@link PerceptualBrightness} holds a share of the
     * panel's output, so restoring one has the same conversion to do as an
     * upgrade does - otherwise the restored profiles would come back a
     * fraction of the brightness the backup was taken at.
     */
    private static int brightness(Properties properties, String key, int schema)
            throws BackupException {
        int stored = integer(properties, key, 10, 100);
        return schema <= SCHEMA_LINEAR_BRIGHTNESS
                ? PerceptualBrightness.toPercent(stored / 100f)
                : stored;
    }

    private static int integer(Properties properties, String key, int minimum, int maximum)
            throws BackupException {
        try {
            int value = Integer.parseInt(required(properties, key));
            if (value < minimum || value > maximum) {
                throw new BackupException("integer_out_of_range");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new BackupException("invalid_integer", error);
        }
    }

    private static int optionalInteger(
            Properties properties,
            String key,
            int minimum,
            int maximum,
            int fallback
    ) throws BackupException {
        return properties.containsKey(key)
                ? integer(properties, key, minimum, maximum)
                : fallback;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value)
            throws BackupException {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException error) {
            throw new BackupException("invalid_enum", error);
        }
    }

    private static <T extends Enum<T>> T optionalEnum(
            Properties properties,
            String key,
            Class<T> type,
            T fallback
    ) throws BackupException {
        return properties.containsKey(key)
                ? enumValue(type, required(properties, key))
                : fallback;
    }

    private static String profileId(String value) throws BackupException {
        String id = value.trim();
        if (id.length() > 64 || !id.matches("[A-Z0-9_]+")) {
            throw new BackupException("invalid_profile_id");
        }
        return id;
    }

    private static boolean isBuiltInProfile(String id) {
        return MirrorProfile.CAMERA_ID.equals(id)
                || MirrorProfile.NAVIGATION_ID.equals(id)
                || MirrorProfile.VIDEO_ID.equals(id);
    }

    private static boolean validPackageName(String value) {
        return value.length() <= 255
                && !value.startsWith(".")
                && !value.endsWith(".")
                && !value.contains("..")
                && value.matches("[A-Za-z0-9_.]+");
    }
}
