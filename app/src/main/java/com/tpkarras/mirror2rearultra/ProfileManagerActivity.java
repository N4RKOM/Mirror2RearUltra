package com.tpkarras.mirror2rearultra;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which profile is in use and which apps switch to it: choosing, adding,
 * renaming and deleting profiles, assigning apps, and the automatic switching
 * (with the floating control) that those assignments depend on.
 */
public class ProfileManagerActivity extends AppCompatActivity
        implements AutoProfileState.Listener {
    private LinearLayout profileChips;
    private PanelPreviewView preview;
    private TextView profileName;
    private TextView profileSummary;
    private TextView profileExtra;
    private LinearLayout assignedStrip;
    private TextView assignedStripLabel;
    private View assignedStripCard;
    private MaterialButton appsTab;
    private MaterialSwitch autoProfileSwitch;
    private MaterialSwitch autoVisibilitySwitch;
    private TextView autoProfileStatus;
    private View usageAccessButton;
    private View usageAccessGroup;
    private TextView overlayAccessStatus;
    private View overlayAccessButton;
    private TextView silentCaptureStatus;
    private View silentCaptureButton;
    private EditText searchInput;
    private LinearLayout appsContainer;
    private TextView appsEmpty;
    private View addProfileButton;
    private View renameProfileButton;
    private View deleteProfileButton;
    private ViewGroup content;

    private List<MirrorProfile> profiles = List.of();
    private List<AppEntry> apps = List.of();
    private MirrorProfile activeProfile;
    /** Guards the automation listeners while the UI is written from stored state. */
    private boolean bindingUi;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_manager);
        SettingsLayout.bindCollapsingTitle(this, R.id.profile_manager_toolbar,
                R.id.profile_manager_scroll, R.id.profile_manager_page_title);

        MaterialToolbar toolbar = findViewById(R.id.profile_manager_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        profileChips = findViewById(R.id.profile_manager_chips);
        preview = findViewById(R.id.profile_manager_preview);
        preview.fitToRearPanel();
        profileName = findViewById(R.id.profile_manager_name);
        profileSummary = findViewById(R.id.profile_manager_summary);
        autoProfileSwitch = findViewById(R.id.auto_profile_switch);
        autoVisibilitySwitch = findViewById(R.id.auto_visibility_switch);
        autoProfileStatus = findViewById(R.id.auto_profile_status);
        usageAccessButton = findViewById(R.id.usage_access_button);
        usageAccessGroup = findViewById(R.id.usage_access_group);
        overlayAccessStatus = findViewById(R.id.overlay_access_status);
        overlayAccessButton = findViewById(R.id.overlay_access_button);
        silentCaptureStatus = findViewById(R.id.silent_capture_status);
        silentCaptureButton = findViewById(R.id.silent_capture_button);
        searchInput = findViewById(R.id.app_search_input);
        appsContainer = findViewById(R.id.apps_container);
        appsEmpty = findViewById(R.id.apps_empty);
        profileExtra = findViewById(R.id.profile_manager_extra);
        assignedStrip = findViewById(R.id.assigned_strip);
        assignedStripLabel = findViewById(R.id.assigned_strip_label);
        assignedStripCard = findViewById(R.id.assigned_strip_card);
        appsTab = findViewById(R.id.profile_manager_tab_apps);
        addProfileButton = findViewById(R.id.add_profile_button);
        renameProfileButton = findViewById(R.id.rename_profile_button);
        deleteProfileButton = findViewById(R.id.delete_profile_button);
        content = findViewById(R.id.profile_manager_content);

        constrainContentOnWideScreens();
        apps = loadLaunchableApps();
        Set<String> launchablePackages = new LinkedHashSet<>();
        for (AppEntry app : apps) {
            launchablePackages.add(app.packageName);
        }
        MirrorSettings.initializeDefaultAssignments(this, launchablePackages);

        activeProfile = MirrorSettings.loadActiveProfile(this);
        refreshProfiles(activeProfile.id);
        bindTabs();
        bindHelpToggle(R.id.overlay_help_button, R.id.overlay_help);
        bindHelpToggle(R.id.auto_help_button, R.id.auto_help);
        twoLine(autoProfileSwitch, R.string.auto_switch_title, R.string.auto_switch_hint);
        twoLine(autoVisibilitySwitch, R.string.auto_visibility_title, R.string.auto_visibility_hint);
        bindInteractions();
        renderApps();
        MiuixRows.installSwitches(this, R.id.auto_profile_switch, R.id.auto_visibility_switch);
    }

    @Override
    protected void onStart() {
        super.onStart();
        AutoProfileState.addListener(this);
    }

    @Override
    protected void onStop() {
        AutoProfileState.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindingUi = true;
        autoProfileSwitch.setChecked(MirrorSettings.isAutoProfileEnabled(this));
        autoVisibilitySwitch.setChecked(MirrorSettings.isAutoVisibilityEnabled(this));
        bindingUi = false;
        // Returning from the system usage-access screen is the common way back
        // here, so re-arm the monitor if the permission has just been granted.
        if (MirrorSettings.isAutoProfileEnabled(this)
                && ForegroundAppMonitor.hasUsageAccess(this)) {
            MirrorSettings.setAutoProfileEnabled(this, true);
        }
        updateAutoProfileUi(AutoProfileState.get());
        updateOverlayAccessUi();
        updateSilentCaptureUi();
    }

    @Override
    public void onAutoProfileChanged(AutoProfileState.Snapshot snapshot) {
        runOnUiThread(() -> updateAutoProfileUi(snapshot));
    }

    /** A "?" next to a header that shows or hides that group's explanation. */
    private void bindHelpToggle(int buttonId, int helpId) {
        View help = findViewById(helpId);
        findViewById(buttonId).setOnClickListener(view ->
                help.setVisibility(help.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE));
    }

    /** Profile and Apps share the page below the chips; one is shown at a time. */
    private void bindTabs() {
        MaterialButtonToggleGroup tabs = findViewById(R.id.profile_manager_tabs);
        View profilesPage = findViewById(R.id.profile_manager_page_profiles);
        View appsPage = findViewById(R.id.profile_manager_page_apps);
        tabs.check(R.id.profile_manager_tab_profiles);
        tabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            boolean apps = checkedId == R.id.profile_manager_tab_apps;
            profilesPage.setVisibility(apps ? View.GONE : View.VISIBLE);
            appsPage.setVisibility(apps ? View.VISIBLE : View.GONE);
        });
    }

    private void bindInteractions() {
        autoProfileSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) {
                return;
            }
            MirrorSettings.setAutoProfileEnabled(this, checked);
            updateAutoProfileUi(AutoProfileState.get());
        });
        autoVisibilitySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) {
                return;
            }
            MirrorSettings.setAutoVisibilityEnabled(this, checked);
            updateAutoProfileUi(AutoProfileState.get());
        });
        usageAccessButton.setOnClickListener(view -> openUsageAccessSettings());
        overlayAccessButton.setOnClickListener(view -> openOverlayAccessSettings());
        silentCaptureButton.setOnClickListener(view -> grantSilentCapture());

        findViewById(R.id.app_search_clear).setOnClickListener(view -> searchInput.setText(""));
        addProfileButton.setOnClickListener(view -> showProfileNameDialog(false));
        renameProfileButton.setOnClickListener(view -> showProfileNameDialog(true));
        deleteProfileButton.setOnClickListener(view -> confirmDeleteProfile());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                findViewById(R.id.app_search_clear).setVisibility(
                        value.length() == 0 ? View.GONE : View.VISIBLE);
                renderApps();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
    }

    private void refreshProfiles(String selectedId) {
        profiles = MirrorSettings.loadProfiles(this);
        int selectedIndex = 0;
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).id.equals(selectedId)) {
                selectedIndex = index;
            }
        }
        activeProfile = profiles.get(selectedIndex);
        ProfileChips.bind(this, profileChips, profiles, activeProfile.id, this::selectProfile,
                () -> showProfileNameDialog(false));
        updateProfileActions();
    }

    private void selectProfile(MirrorProfile profile) {
        if (activeProfile != null && activeProfile.id.equals(profile.id)) {
            return;
        }
        activeProfile = MirrorSettings.selectProfile(this, profile.id);
        refreshProfiles(activeProfile.id);
        renderApps();
    }

    private void updateProfileActions() {
        boolean custom = activeProfile != null && activeProfile.isCustom();
        renameProfileButton.setEnabled(custom);
        deleteProfileButton.setEnabled(custom);
        renameProfileButton.setAlpha(custom ? 1f : 0.4f);
        deleteProfileButton.setAlpha(custom ? 1f : 0.4f);
        addProfileButton.setEnabled(true);
        if (activeProfile != null) {
            preview.setProfile(activeProfile);
            profileName.setText(MirrorSettings.profileDisplayName(this, activeProfile));
            profileSummary.setText(getString(
                    R.string.profile_mode_summary,
                    getResources().getStringArray(R.array.scale_mode_entries)[
                            activeProfile.scaleMode.ordinal()],
                    activeProfile.rotationDegrees,
                    activeProfile.zoomPercent));
            profileExtra.setText(getString(
                    R.string.profile_extra,
                    activeProfile.brightnessPercent,
                    getString(activeProfile.mirrorHorizontally
                            ? R.string.profile_extra_mirrored
                            : R.string.profile_extra_not_mirrored)));
        }
    }

    private void showProfileNameDialog(boolean rename) {
        if (rename && (activeProfile == null || !activeProfile.isCustom())) {
            return;
        }
        TextInputLayout inputLayout = (TextInputLayout) getLayoutInflater().inflate(
                R.layout.dialog_profile_name,
                null,
                false
        );
        TextInputEditText input = inputLayout.findViewById(R.id.profile_name_input);
        if (rename) {
            input.setText(activeProfile.customName);
            input.setSelection(input.length());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(rename ? R.string.rename_profile_title : R.string.create_profile_title)
                .setView(inputLayout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(rename ? R.string.save : R.string.create, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        inputLayout.setError(getString(R.string.profile_name_required));
                        return;
                    }
                    inputLayout.setError(null);
                    if (rename) {
                        activeProfile = MirrorSettings.renameCustomProfile(
                                this,
                                activeProfile,
                                name
                        );
                    } else {
                        activeProfile = MirrorSettings.createCustomProfile(
                                this,
                                name,
                                activeProfile
                        );
                    }
                    refreshProfiles(activeProfile.id);
                    renderApps();
                    dialog.dismiss();
                    Snackbar.make(
                            content,
                            rename ? R.string.profile_renamed : R.string.profile_created,
                            Snackbar.LENGTH_SHORT
                    ).show();
                }));
        dialog.show();
    }

    private void confirmDeleteProfile() {
        if (activeProfile == null || !activeProfile.isCustom()) {
            return;
        }
        String profileName = MirrorSettings.profileDisplayName(this, activeProfile);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_profile_title, profileName))
                .setMessage(R.string.delete_profile_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_profile, (dialog, which) -> {
                    activeProfile = MirrorSettings.deleteCustomProfile(this, activeProfile.id);
                    refreshProfiles(activeProfile.id);
                    renderApps();
                    Snackbar.make(content, R.string.profile_deleted, Snackbar.LENGTH_SHORT).show();
                })
                .show();
    }

    private void renderApps() {
        if (activeProfile == null) {
            return;
        }
        String query = searchInput.getText() == null
                ? ""
                : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        Set<String> assigned = MirrorSettings.assignedPackages(this, activeProfile.id);
        appsContainer.removeAllViews();
        int visibleCount = 0;
        List<AppEntry> ordered = new ArrayList<>(apps);
        ordered.sort(Comparator.comparing(
                (AppEntry app) -> assigned.contains(app.packageName) ? 0 : 1));
        for (AppEntry app : ordered) {
            if (!query.isEmpty()
                    && !app.label.toLowerCase(Locale.ROOT).contains(query)
                    && !app.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            View row = getLayoutInflater().inflate(R.layout.item_app_row, appsContainer, false);
            ((ImageView) row.findViewById(R.id.app_row_icon)).setImageDrawable(app.icon);
            ((TextView) row.findViewById(R.id.app_row_name)).setText(app.label);
            ((TextView) row.findViewById(R.id.app_row_detail)).setText(
                    appRowDetail(app, assigned));
            MaterialCheckBox checkBox = row.findViewById(R.id.app_row_check);
            checkBox.setChecked(assigned.contains(app.packageName));
            row.setOnClickListener(view -> checkBox.setChecked(!checkBox.isChecked()));
            checkBox.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    MirrorSettings.assignApp(this, app.packageName, activeProfile.id);
                } else if (activeProfile.id.equals(
                        MirrorSettings.assignedProfileId(this, app.packageName))) {
                    MirrorSettings.assignApp(this, app.packageName, null);
                }
                updateAssignedCount();
            });
            appsContainer.addView(row);
            visibleCount++;
        }
        appsEmpty.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
        appsContainer.setVisibility(visibleCount == 0 ? View.GONE : View.VISIBLE);
        updateAssignedCount();
    }

    /** The line under an app's name: its package, or the profile that has it already. */
    private String appRowDetail(AppEntry app, Set<String> assigned) {
        String otherId = assigned.contains(app.packageName)
                ? null : MirrorSettings.assignedProfileId(this, app.packageName);
        for (MirrorProfile profile : profiles) {
            if (profile.id.equals(otherId)) {
                return getString(R.string.assigned_to_profile,
                        MirrorSettings.profileDisplayName(this, profile));
            }
        }
        return app.packageName;
    }

    private void updateAssignedCount() {
        Set<String> assigned = activeProfile == null
                ? Set.of() : MirrorSettings.assignedPackages(this, activeProfile.id);
        appsTab.setText(getString(R.string.profile_apps_tab, assigned.size()));
        assignedStripLabel.setText(getString(R.string.assigned_apps_to_profile,
                activeProfile == null ? "" : MirrorSettings.profileDisplayName(this, activeProfile),
                assigned.size()));
        assignedStrip.removeAllViews();
        PackageManager manager = getPackageManager();
        int shown = 0;
        for (String packageName : assigned) {
            if (shown == 7) {
                TextView more = new TextView(this);
                more.setText("+" + (assigned.size() - shown));
                more.setTextColor(androidx.core.content.ContextCompat.getColor(
                        this, R.color.hyper_text_secondary));
                more.setGravity(android.view.Gravity.CENTER_VERTICAL);
                assignedStrip.addView(more);
                break;
            }
            try {
                android.widget.ImageView icon = new android.widget.ImageView(this);
                icon.setImageDrawable(manager.getApplicationIcon(packageName));
                icon.setContentDescription(applicationName(packageName));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(36), dp(36));
                params.setMarginEnd(dp(8));
                assignedStrip.addView(icon, params);
                shown++;
            } catch (PackageManager.NameNotFoundException ignored) {
                // The app was removed since it was assigned; the list drops it too.
            }
        }
        TextView add = new TextView(this);
        add.setText("+");
        add.setTextSize(20);
        add.setGravity(android.view.Gravity.CENTER);
        add.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.hyper_accent));
        add.setBackgroundResource(R.drawable.hyper_chip_background);
        add.setSelected(true);
        add.setContentDescription(getString(R.string.add_apps));
        add.setOnClickListener(view -> {
            View picker = findViewById(R.id.apps_picker);
            picker.setVisibility(picker.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        assignedStrip.addView(add, new LinearLayout.LayoutParams(dp(36), dp(36)));
    }

    @SuppressWarnings("deprecation")
    private List<AppEntry> loadLaunchableApps() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager manager = getPackageManager();
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        for (ResolveInfo info : manager.queryIntentActivities(launcherIntent, 0)) {
            if (info.activityInfo == null
                    || getPackageName().equals(info.activityInfo.packageName)) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            CharSequence label = info.loadLabel(manager);
            byPackage.putIfAbsent(packageName, new AppEntry(
                    label == null ? packageName : label.toString(),
                    packageName,
                    info.loadIcon(manager)
            ));
        }
        List<AppEntry> result = new ArrayList<>(byPackage.values());
        result.sort(Comparator.comparing(app -> app.label.toLowerCase(Locale.ROOT)));
        return result;
    }

    private void constrainContentOnWideScreens() {
        float density = getResources().getDisplayMetrics().density;
        int availableWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        if (availableWidthDp <= 640) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) content.getLayoutParams();
        params.width = Math.round(600 * density);
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        content.setLayoutParams(params);
    }

    /** A title with a quieter line of explanation under it, drawn by {@link MiuixRows}. */
    private void twoLine(MaterialSwitch row, int titleRes, int hintRes) {
        row.setText(titleRes);
        row.setTag(getString(hintRes));
    }

    private enum Tone { QUIET, WAIT, BAD, GOOD }

    /** Paints a state pill: a tinted background with text in the same hue. */
    private void setPill(TextView pill, CharSequence label, Tone tone) {
        int text;
        int fill;
        switch (tone) {
            case GOOD:
                text = androidx.core.content.ContextCompat.getColor(
                        this, R.color.hyper_on_success_container);
                fill = androidx.core.content.ContextCompat.getColor(
                        this, R.color.hyper_success_container);
                break;
            case WAIT:
                text = androidx.core.content.ContextCompat.getColor(this, R.color.hyper_warning);
                fill = androidx.core.graphics.ColorUtils.setAlphaComponent(text, 40);
                break;
            case BAD:
                text = androidx.core.content.ContextCompat.getColor(this, R.color.hyper_error);
                fill = androidx.core.graphics.ColorUtils.setAlphaComponent(text, 40);
                break;
            case QUIET:
            default:
                text = androidx.core.content.ContextCompat.getColor(
                        this, R.color.hyper_text_secondary);
                fill = androidx.core.graphics.ColorUtils.setAlphaComponent(text, 30);
                break;
        }
        pill.setText(label);
        pill.setTextColor(text);
        pill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(fill));
    }

    private void updateAutoProfileUi(AutoProfileState.Snapshot snapshot) {
        boolean enabled = MirrorSettings.isAutoProfileEnabled(this)
                || MirrorSettings.isAutoVisibilityEnabled(this);
        boolean permitted = ForegroundAppMonitor.hasUsageAccess(this);
        usageAccessGroup.setVisibility(enabled && !permitted ? View.VISIBLE : View.GONE);
        if (!enabled) {
            setPill(autoProfileStatus, getString(R.string.auto_state_off), Tone.QUIET);
            return;
        }
        if (!permitted) {
            setPill(autoProfileStatus, getString(R.string.auto_state_permission), Tone.BAD);
            return;
        }
        if (snapshot.packageName == null) {
            setPill(autoProfileStatus, getString(R.string.auto_state_waiting), Tone.WAIT);
            return;
        }
        String appName = applicationName(snapshot.packageName);
        if (snapshot.profileId == null) {
            setPill(autoProfileStatus,
                    getString(R.string.auto_profile_manual_for_app, appName), Tone.WAIT);
        } else {
            setPill(autoProfileStatus, getString(
                    R.string.auto_profile_current,
                    MirrorSettings.profileDisplayName(
                            this, MirrorSettings.loadProfile(this, snapshot.profileId)),
                    appName
            ), Tone.GOOD);
        }
    }

    private String applicationName(String packageName) {
        PackageManager manager = getPackageManager();
        try {
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            return String.valueOf(manager.getApplicationLabel(info));
        } catch (PackageManager.NameNotFoundException error) {
            return packageName;
        }
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void updateOverlayAccessUi() {
        boolean permitted = Settings.canDrawOverlays(this);
        setPill(overlayAccessStatus, getString(permitted
                ? R.string.overlay_state_granted : R.string.overlay_state_required),
                permitted ? Tone.GOOD : Tone.BAD);
        overlayAccessButton.setVisibility(permitted ? View.GONE : View.VISIBLE);
    }

    private void updateSilentCaptureUi() {
        boolean silent = ProjectionConsent.isSilent(this);
        setPill(silentCaptureStatus, getString(silent
                ? R.string.silent_state_on : R.string.silent_state_off),
                silent ? Tone.GOOD : Tone.QUIET);
        silentCaptureButton.setVisibility(silent ? View.GONE : View.VISIBLE);
    }

    private void grantSilentCapture() {
        silentCaptureButton.setEnabled(false);
        new Thread(() -> {
            boolean granted = ProjectionConsent.grantWithRoot(getApplicationContext());
            runOnUiThread(() -> {
                silentCaptureButton.setEnabled(true);
                updateSilentCaptureUi();
                if (!granted) {
                    Toast.makeText(this, R.string.silent_capture_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }, "silent-capture-grant").start();
    }

    private void openOverlayAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final android.graphics.drawable.Drawable icon;

        AppEntry(String label, String packageName, android.graphics.drawable.Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}
