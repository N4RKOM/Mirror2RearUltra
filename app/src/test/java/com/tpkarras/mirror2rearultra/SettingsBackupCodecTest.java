package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SettingsBackupCodecTest {
    @Test
    public void roundTripPreservesProfilesGlobalsAndAssignments() throws Exception {
        SettingsBackupCodec.Data source = sampleData(true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        SettingsBackupCodec.write(source, output);
        SettingsBackupCodec.Data restored = SettingsBackupCodec.read(
                new ByteArrayInputStream(output.toByteArray())
        );

        assertEquals("CUSTOM_42", restored.activeProfileId);
        assertEquals(ProjectionQuality.SHARP, restored.projectionQuality);
        assertTrue(restored.autoProfileEnabled);
        assertTrue(restored.temperatureProtectionEnabled);
        assertEquals(47, restored.temperatureThreshold);
        assertEquals(4, restored.profiles.size());
        assertEquals("Мой профиль", restored.profiles.get(3).customName);
        assertEquals("CUSTOM_42", restored.assignments.get("com.example.maps"));
    }

    @Test
    public void readsBrightnessFromAnOlderFileOnItsOwnScale() throws Exception {
        // A file written before the slider became perceptual holds a share of
        // the panel's output, and has to come back as the position that asks
        // for the same light.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SettingsBackupCodec.write(sampleData(true), output);
        String older = output.toString(StandardCharsets.UTF_8).replace(
                "<entry key=\"schema\">" + SettingsBackupCodec.SCHEMA_VERSION + "</entry>",
                "<entry key=\"schema\">1</entry>"
        );
        assertTrue("the schema entry was not rewritten",
                older.contains("<entry key=\"schema\">1</entry>"));

        SettingsBackupCodec.Data restored = SettingsBackupCodec.read(
                new ByteArrayInputStream(older.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(PerceptualBrightness.toPercent(0.80f),
                profile(restored, MirrorProfile.NAVIGATION_ID).brightnessPercent);
        assertEquals(PerceptualBrightness.toPercent(0.70f),
                profile(restored, MirrorProfile.VIDEO_ID).brightnessPercent);
    }

    @Test
    public void readsBrightnessFromTheCurrentFileUnchanged() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SettingsBackupCodec.write(sampleData(true), output);

        SettingsBackupCodec.Data restored = SettingsBackupCodec.read(
                new ByteArrayInputStream(output.toByteArray())
        );

        assertEquals(80, profile(restored, MirrorProfile.NAVIGATION_ID).brightnessPercent);
    }

    private static MirrorProfile profile(SettingsBackupCodec.Data data, String id) {
        for (MirrorProfile profile : data.profiles) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        throw new AssertionError("no profile " + id);
    }

    @Test
    public void rejectsBackupWithoutAllBuiltInProfiles() throws Exception {
        SettingsBackupCodec.Data source = sampleData(false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SettingsBackupCodec.write(source, output);

        assertThrows(
                SettingsBackupCodec.BackupException.class,
                () -> SettingsBackupCodec.read(new ByteArrayInputStream(output.toByteArray()))
        );
    }

    @Test
    public void rejectsOutOfRangeProtectionThreshold() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SettingsBackupCodec.write(sampleData(true), output);
        String xml = output.toString(StandardCharsets.UTF_8)
                .replace(">20</entry>", ">99</entry>");

        assertThrows(
                SettingsBackupCodec.BackupException.class,
                () -> SettingsBackupCodec.read(new ByteArrayInputStream(
                        xml.getBytes(StandardCharsets.UTF_8)
                ))
        );
    }

    @Test
    public void rejectsNonBackupXml() {
        byte[] bytes = "<not-a-backup/>".getBytes(StandardCharsets.UTF_8);
        assertThrows(
                SettingsBackupCodec.BackupException.class,
                () -> SettingsBackupCodec.read(new ByteArrayInputStream(bytes))
        );
    }

    @Test
    public void aFrameSurvivesTheRoundTripAndItsAbsenceDoesToo() throws Exception {
        // A frame is the whole of a profile's crop, so a backup that dropped
        // it would come back showing a different part of the screen.
        List<MirrorProfile> profiles = new java.util.ArrayList<>(sampleData(true).profiles);
        profiles.set(0, profiles.get(0).withCrop(
                new MirrorProfile.Crop(0f, 0.1475f, 1f, 0.7475f, 0)));
        SettingsBackupCodec.Data source = withProfiles(sampleData(true), profiles);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        SettingsBackupCodec.write(source, output);
        SettingsBackupCodec.Data restored = SettingsBackupCodec.read(
                new ByteArrayInputStream(output.toByteArray()));

        MirrorProfile.Crop crop = profile(restored, MirrorProfile.CAMERA_ID).crop;
        assertNotNull(crop);
        assertEquals(0f, crop.left, 1e-4f);
        assertEquals(0.1475f, crop.top, 1e-4f);
        assertEquals(1f, crop.right, 1e-4f);
        assertEquals(0.7475f, crop.bottom, 1e-4f);
        assertEquals(0, crop.rotation);
        assertNull(profile(restored, MirrorProfile.NAVIGATION_ID).crop);
    }

    private static SettingsBackupCodec.Data withProfiles(
            SettingsBackupCodec.Data source, List<MirrorProfile> profiles) {
        return new SettingsBackupCodec.Data(
                source.activeProfileId,
                source.projectionQuality,
                source.autoProfileEnabled,
                source.autoVisibilityEnabled,
                source.calibrationGridEnabled,
                source.temperatureProtectionEnabled,
                source.temperatureThreshold,
                source.batteryProtectionEnabled,
                source.batteryThreshold,
                source.dashboardSettings,
                profiles,
                source.assignments,
                source.dashboardImage,
                source.dashboardLayoutState,
                source.dashboardTemplateState
        );
    }

    private static SettingsBackupCodec.Data sampleData(boolean includeVideo) {
        List<MirrorProfile> profiles = new java.util.ArrayList<>();
        profiles.add(new MirrorProfile(MirrorProfile.Id.CAMERA,
                MirrorProfile.ScaleMode.FILL, 0, true, 100));
        profiles.add(new MirrorProfile(MirrorProfile.Id.NAVIGATION,
                MirrorProfile.ScaleMode.FIT, 0, false, 80));
        if (includeVideo) {
            profiles.add(new MirrorProfile(MirrorProfile.Id.VIDEO,
                    MirrorProfile.ScaleMode.FIT, 90, false, 70));
        }
        profiles.add(new MirrorProfile(
                "CUSTOM_42",
                "Мой профиль",
                MirrorProfile.ScaleMode.STRETCH,
                180,
                false,
                65,
                150,
                10,
                -5
        ));
        Map<String, String> assignments = new LinkedHashMap<>();
        assignments.put("com.example.maps", "CUSTOM_42");
        return new SettingsBackupCodec.Data(
                "CUSTOM_42",
                ProjectionQuality.SHARP,
                true,
                false,
                true,
                true,
                47,
                true,
                20,
                profiles,
                assignments
        );
    }
}
