package com.tpkarras.mirror2rearultra;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app's home screen: current state, the active profile, and the way in to
 * everything else.
 *
 * <p>This used to be the whole app - every setting on one scroll, with
 * per-profile controls sitting beside app-wide ones. Anything scoped to a
 * profile now lives in {@link ImageSettingsActivity}, and the two long device
 * sections became {@link AutomationActivity} and
 * {@link DeviceProtectionActivity}. What remains here is app-wide, short, and
 * either read-only or infrequent.
 */
public class SettingsActivity extends AppCompatActivity implements
        MirrorState.Listener,
        DeviceCapabilityState.Listener {

    private HyperValueRow profileInput;
    private TextView profileSummary;
    private TextView statusValue;
    private TextView rootStatusValue;
    private TextView hardwareBrightnessStatusValue;
    private View capabilityRefreshButton;
    private TextView backupStatus;
    private ViewGroup settingsContent;

    private MirrorProfile activeProfile;
    /** Guards the listeners while the UI is being written from stored state. */
    private boolean bindingUi;
    private String[] scaleLabels;
    private List<MirrorProfile> profiles;
    private String[] profileLabels;
    private ActivityResultLauncher<String> exportDocumentLauncher;
    private ActivityResultLauncher<String[]> importDocumentLauncher;
    private final ExecutorService backupExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exportDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/xml"),
                uri -> {
                    if (uri != null) {
                        exportSettings(uri);
                    }
                }
        );
        importDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        readBackupForImport(uri);
                    }
                }
        );
        setContentView(R.layout.activity_settings);

        profileInput = findViewById(R.id.profile_input);
        profileSummary = findViewById(R.id.profile_summary);
        statusValue = findViewById(R.id.status_value);
        rootStatusValue = findViewById(R.id.root_status_value);
        hardwareBrightnessStatusValue = findViewById(R.id.hardware_brightness_status_value);
        capabilityRefreshButton = findViewById(R.id.capability_refresh_button);
        backupStatus = findViewById(R.id.backup_status);
        settingsContent = findViewById(R.id.settings_content);

        scaleLabels = getResources().getStringArray(R.array.scale_mode_entries);

        bindInteractions();
        SettingsLayout.constrainContentOnWideScreens(this, settingsContent);
        renderStoredSettings();
        updateCapabilityUi(DeviceCapabilityState.get());
        updateStatus(MirrorState.isActive());
    }

    @Override
    protected void onStart() {
        super.onStart();
        MirrorState.addListener(this);
        DeviceCapabilityState.addListener(this);
        probeCapabilities();
        updateStatus(MirrorState.isActive());
    }

    @Override
    protected void onStop() {
        MirrorState.removeListener(this);
        DeviceCapabilityState.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        backupExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A sub-page may have changed the profile or its settings, so the
        // summary and picker are rebuilt every time this screen comes back.
        renderStoredSettings();
    }

    @Override
    public void onMirrorStateChanged(boolean active) {
        runOnUiThread(() -> updateStatus(active));
    }

    @Override
    public void onDeviceCapabilityChanged(DeviceCapabilityState.Snapshot snapshot) {
        runOnUiThread(() -> updateCapabilityUi(snapshot));
    }

    private void bindInteractions() {
        profileInput.setOnItemSelectedListener(position -> {
            if (bindingUi || position < 0 || position >= profiles.size()) {
                return;
            }
            activeProfile = MirrorSettings.selectProfile(this, profiles.get(position).id);
            updateProfileSummary(activeProfile);
            settingsContent.announceForAccessibility(getString(
                    R.string.profile_selected_announcement,
                    profileName(activeProfile)
            ));
        });

        findViewById(R.id.image_settings_button).setOnClickListener(view ->
                startActivity(new Intent(this, ImageSettingsActivity.class))
        );

        findViewById(R.id.manage_profiles_button).setOnClickListener(view ->
                startActivity(new Intent(this, ProfileManagerActivity.class))
        );

        findViewById(R.id.dashboard_settings_button).setOnClickListener(view ->
                startActivity(new Intent(this, DashboardSettingsActivity.class))
        );

        findViewById(R.id.automation_button).setOnClickListener(view ->
                startActivity(new Intent(this, AutomationActivity.class))
        );

        findViewById(R.id.device_protection_button).setOnClickListener(view ->
                startActivity(new Intent(this, DeviceProtectionActivity.class))
        );

        capabilityRefreshButton.setOnClickListener(view -> probeCapabilities());

        findViewById(R.id.export_backup_button).setOnClickListener(view ->
                exportDocumentLauncher.launch(createBackupFileName())
        );

        findViewById(R.id.import_backup_button).setOnClickListener(view ->
                importDocumentLauncher.launch(new String[]{
                        "application/xml",
                        "text/xml",
                        "application/octet-stream"
                })
        );
    }

    private void renderStoredSettings() {
        activeProfile = MirrorSettings.loadActiveProfile(this);
        refreshProfileOptions(activeProfile.id);
        updateProfileSummary(activeProfile);
    }

    private void exportSettings(Uri uri) {
        backupStatus.setText(R.string.backup_exporting);
        backupExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) {
                    throw new IOException("No output stream");
                }
                output.write(MirrorSettings.exportBackup(this));
                output.flush();
                runOnUiThread(() -> showBackupMessage(R.string.backup_export_success));
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> showBackupMessage(R.string.backup_export_failed));
            }
        });
    }

    private void readBackupForImport(Uri uri) {
        backupStatus.setText(R.string.backup_reading);
        backupExecutor.execute(() -> {
            try {
                byte[] bytes = readLimited(uri);
                SettingsBackupCodec.Data data = MirrorSettings.decodeBackup(bytes);
                runOnUiThread(() -> confirmImport(data));
            } catch (IOException | SettingsBackupCodec.BackupException | RuntimeException error) {
                runOnUiThread(() -> showBackupMessage(R.string.backup_import_invalid));
            }
        });
    }

    private byte[] readLimited(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IOException("No input stream");
            }
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > SettingsBackupCodec.MAX_BACKUP_BYTES) {
                    throw new IOException("Backup is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void confirmImport(SettingsBackupCodec.Data data) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_import_confirm_title)
                .setMessage(getString(
                        R.string.backup_import_confirm_message,
                        data.profiles.size(),
                        data.assignments.size()
                ))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.backup_import_action, (dialog, which) -> {
                    try {
                        activeProfile = MirrorSettings.applyBackup(this, data);
                        renderStoredSettings();
                        showBackupMessage(R.string.backup_import_success);
                    } catch (SettingsBackupCodec.BackupException error) {
                        showBackupMessage(R.string.backup_import_failed);
                    }
                })
                .show();
    }

    private void showBackupMessage(int messageResource) {
        backupStatus.setText(messageResource);
        Snackbar.make(settingsContent, messageResource, Snackbar.LENGTH_LONG).show();
    }

    private static String createBackupFileName() {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
                .format(new Date());
        return "Mirror2RearUltra-settings-" + timestamp + ".xml";
    }

    /**
     * One line under the profile group naming what the selected profile is set
     * to, so the hub still says what the image sub-page holds.
     */
    private void updateProfileSummary(MirrorProfile profile) {
        profileSummary.setText(getString(
                R.string.profile_summary,
                profileName(profile),
                scaleLabels[profile.scaleMode.ordinal()],
                profile.rotationDegrees
        ));
    }

    private void updateStatus(boolean active) {
        statusValue.setText(active ? R.string.status_active : R.string.status_inactive);
        statusValue.setContentDescription(getString(
                active ? R.string.status_active_accessibility : R.string.status_inactive_accessibility
        ));
    }

    private void updateCapabilityUi(DeviceCapabilityState.Snapshot snapshot) {
        capabilityRefreshButton.setEnabled(!snapshot.probing);
        if (snapshot.probing) {
            rootStatusValue.setText(R.string.capability_checking);
            hardwareBrightnessStatusValue.setText(R.string.capability_checking);
            return;
        }
        rootStatusValue.setText(snapshot.rootAvailable == null
                ? R.string.capability_unknown
                : snapshot.rootAvailable
                ? R.string.root_available
                : R.string.root_unavailable);
        if (snapshot.hardwareBrightnessActive) {
            hardwareBrightnessStatusValue.setText(R.string.hardware_brightness_active);
        } else if (snapshot.rearBrightnessAvailable == null) {
            hardwareBrightnessStatusValue.setText(R.string.capability_unknown);
        } else {
            hardwareBrightnessStatusValue.setText(snapshot.rearBrightnessAvailable
                    ? R.string.hardware_brightness_ready
                    : R.string.hardware_brightness_unavailable);
        }
    }

    private void probeCapabilities() {
        RearBrightnessController.probeCapabilities((root, rearBrightness) -> {
            // DeviceCapabilityState publishes the result to this screen.
        });
    }

    private String profileName(MirrorProfile profile) {
        return MirrorSettings.profileDisplayName(this, profile);
    }

    private void refreshProfileOptions(String selectedId) {
        profiles = MirrorSettings.loadProfiles(this);
        profileLabels = new String[profiles.size()];
        int selectedIndex = 0;
        for (int index = 0; index < profiles.size(); index++) {
            MirrorProfile profile = profiles.get(index);
            profileLabels[index] = profileName(profile);
            if (profile.id.equals(selectedId)) {
                selectedIndex = index;
            }
        }
        bindingUi = true;
        profileInput.setEntries(profileLabels);
        profileInput.setValue(profileLabels[selectedIndex]);
        bindingUi = false;
    }
}
