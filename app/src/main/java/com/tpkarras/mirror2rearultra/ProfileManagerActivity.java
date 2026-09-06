package com.tpkarras.mirror2rearultra;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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

public class ProfileManagerActivity extends AppCompatActivity {
    private HyperValueRow profileInput;
    private TextInputEditText searchInput;
    private LinearLayout appsContainer;
    private TextView appsEmpty;
    private TextView assignedAppsSummary;
    private MaterialButton addProfileButton;
    private MaterialButton renameProfileButton;
    private MaterialButton deleteProfileButton;
    private ViewGroup content;

    private List<MirrorProfile> profiles = List.of();
    private List<AppEntry> apps = List.of();
    private MirrorProfile activeProfile;
    private boolean bindingProfile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_manager);

        MaterialToolbar toolbar = findViewById(R.id.profile_manager_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        profileInput = findViewById(R.id.profile_manager_input);
        searchInput = findViewById(R.id.app_search_input);
        appsContainer = findViewById(R.id.apps_container);
        appsEmpty = findViewById(R.id.apps_empty);
        assignedAppsSummary = findViewById(R.id.assigned_apps_summary);
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
        bindInteractions();
        renderApps();
    }

    private void bindInteractions() {
        profileInput.setOnItemSelectedListener(position -> {
            if (bindingProfile || position < 0 || position >= profiles.size()) {
                return;
            }
            activeProfile = MirrorSettings.selectProfile(this, profiles.get(position).id);
            updateProfileActions();
            renderApps();
        });

        addProfileButton.setOnClickListener(view -> showProfileNameDialog(false));
        renameProfileButton.setOnClickListener(view -> showProfileNameDialog(true));
        deleteProfileButton.setOnClickListener(view -> confirmDeleteProfile());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                renderApps();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
    }

    private void refreshProfiles(String selectedId) {
        profiles = MirrorSettings.loadProfiles(this);
        String[] labels = new String[profiles.size()];
        int selectedIndex = 0;
        for (int index = 0; index < profiles.size(); index++) {
            MirrorProfile profile = profiles.get(index);
            labels[index] = MirrorSettings.profileDisplayName(this, profile);
            if (profile.id.equals(selectedId)) {
                selectedIndex = index;
            }
        }
        activeProfile = profiles.get(selectedIndex);
        bindingProfile = true;
        profileInput.setEntries(labels);
        profileInput.setValue(labels[selectedIndex]);
        bindingProfile = false;
        updateProfileActions();
    }

    private void updateProfileActions() {
        boolean custom = activeProfile != null && activeProfile.isCustom();
        renameProfileButton.setEnabled(custom);
        deleteProfileButton.setEnabled(custom);
        addProfileButton.setEnabled(true);
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
        for (AppEntry app : apps) {
            if (!query.isEmpty()
                    && !app.label.toLowerCase(Locale.ROOT).contains(query)
                    && !app.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            MaterialCheckBox checkBox = new MaterialCheckBox(this);
            checkBox.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            checkBox.setMinHeight(dp(60));
            checkBox.setPadding(dp(18), dp(8), dp(18), dp(8));
            checkBox.setBackgroundResource(R.drawable.hyper_row_bg_middle);
            checkBox.setText(appRowLabel(app));
            checkBox.setChecked(assigned.contains(app.packageName));
            checkBox.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    MirrorSettings.assignApp(this, app.packageName, activeProfile.id);
                } else if (activeProfile.id.equals(
                        MirrorSettings.assignedProfileId(this, app.packageName))) {
                    MirrorSettings.assignApp(this, app.packageName, null);
                }
                updateAssignedCount();
            });
            appsContainer.addView(checkBox);
            visibleCount++;
        }
        appsEmpty.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
        appsContainer.setVisibility(visibleCount == 0 ? View.GONE : View.VISIBLE);
        updateAssignedCount();
    }

    /**
     * Two-tier row label: the app name reads at full weight, the package name
     * drops to secondary size and colour behind it. Both were previously drawn
     * at the same size, which made the list hard to scan.
     */
    private CharSequence appRowLabel(AppEntry app) {
        SpannableStringBuilder text = new SpannableStringBuilder(app.label);
        int start = text.length() + 1;
        text.append('\n').append(app.packageName);
        text.setSpan(new RelativeSizeSpan(0.8f), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(
                        this, R.color.hyper_text_secondary)),
                start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private void updateAssignedCount() {
        int count = activeProfile == null
                ? 0
                : MirrorSettings.assignedPackages(this, activeProfile.id).size();
        assignedAppsSummary.setText(getString(R.string.assigned_apps_count, count));
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
                    packageName
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AppEntry {
        final String label;
        final String packageName;

        AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }
}
