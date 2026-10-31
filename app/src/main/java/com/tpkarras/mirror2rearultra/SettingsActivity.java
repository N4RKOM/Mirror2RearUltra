package com.tpkarras.mirror2rearultra;

import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.snackbar.Snackbar;
import androidx.compose.ui.platform.ComposeView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    /** The looks, screen and touch sections folded open on the widgets tab. */
    private PanelSettingsController panelSettings;
    private int selectedSection = 1;

    /** Which tab a launcher shortcut asks for; the Mirror tab otherwise. */
    private static int sectionFor(@Nullable String action) {
        if ("com.tpkarras.mirror2rearultra.action.TAB_WIDGETS".equals(action)) return 0;
        if ("com.tpkarras.mirror2rearultra.action.TAB_SETTINGS".equals(action)) return 2;
        return 1;
    }
    private MiuixNavigation navigation;

    private RearDashboardView homePreview;
    private MaterialCardView homePreviewContainer;
    private FrameLayout homePageTabs;
    private LinearLayout homeRows;
    private TextView homeHint;
    /** The page the widgets tab shows: its preview, its tab and its list. */
    private int homePage = 1;

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
        selectedSection = sectionFor(getIntent().getAction());
        if (savedInstanceState != null) selectedSection = savedInstanceState.getInt("section", 1);
        navigation = new MiuixNavigation((ComposeView) findViewById(R.id.floating_navigation),
                selectedSection, this::selectSection, findViewById(R.id.frosted_backdrop),
                findViewById(R.id.settings_scroll), AppLook.blur(this));
        navigation.followScroll(findViewById(R.id.settings_scroll));

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
        bindAppearance();
        panelSettings = new PanelSettingsController(this, settingsContent,
                this::onPanelSettingsChanged);
        MiuixRows.installWithIcon(this, R.id.image_settings_button, R.drawable.ic_row_image);
        MiuixRows.installWithIcon(this, R.id.manage_profiles_button, R.drawable.ic_row_profiles);
        MiuixRows.installWithIcon(this, R.id.automation_button, R.drawable.ic_row_automation);
        MiuixRows.installWithIcon(this, R.id.mirror_tools_button, R.drawable.ic_row_shutter);
        MiuixRows.installWithIcon(this, R.id.device_protection_button,
                R.drawable.ic_row_protection);
        MiuixRows.installWithIcon(this, R.id.app_about_button, R.drawable.ic_row_about);
        MiuixRows.installWithIcon(this, R.id.app_source_button, R.drawable.ic_row_link);
        findViewById(R.id.app_about_button).setOnClickListener(view -> showAbout());
        findViewById(R.id.app_source_button).setOnClickListener(view -> openSource());
        MiuixRows.installWithIcon(this, R.id.widgets_home_add_button, R.drawable.ic_row_add);
        MiuixRows.installWithIcon(this, R.id.general_content_button, R.drawable.ic_row_content);
        MiuixRows.installExpandableRow(this, R.id.widgets_home_appearance_button,
                R.drawable.ic_row_appearance, R.id.widgets_home_appearance_panel);
        MiuixRows.installExpandableRow(this, R.id.widgets_home_screen_button,
                R.drawable.ic_row_screen, R.id.widgets_home_screen_panel);
        MiuixRows.installExpandableRow(this, R.id.widgets_home_touch_button,
                R.drawable.ic_row_touch, R.id.widgets_home_touch_panel);
        MiuixRows.installWithIcon(this, R.id.widgets_home_builder_button,
                R.drawable.ic_row_builder);
        MiuixRows.installWithIcon(this, R.id.widgets_home_templates_button,
                R.drawable.ic_row_templates);
        selectSection(selectedSection);
        SettingsLayout.constrainContentOnWideScreens(this, settingsContent);
        renderStoredSettings();
        updateCapabilityUi(DeviceCapabilityState.get());
        updateStatus(MirrorState.isActive());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("section", selectedSection);
        super.onSaveInstanceState(outState);
    }

    private void selectSection(int section) {
        selectedSection = section;
        int[] sections = {R.id.section_widgets, R.id.section_mirror, R.id.section_general};
        int[] titles = {R.string.dashboard_widgets_title, R.string.nav_mirror, R.string.nav_settings};
        ((TextView) findViewById(R.id.section_title)).setText(titles[section]);
        for (int i = 0; i < sections.length; i++) {
            boolean selected = i == section;
            findViewById(sections[i]).setVisibility(selected ? View.VISIBLE : View.GONE);
        }
        navigation.select(section);
        navigation.setHidden(false);
        ((androidx.core.widget.NestedScrollView) settingsContent.getParent()).scrollTo(0, 0);
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
        renderWidgetsHome();
        panelSettings.onResume();
    }

    @Override
    protected void onPause() {
        panelSettings.onPause();
        super.onPause();
    }

    /** A setting in a folded-open section changed: the preview and summaries follow. */
    private void onPanelSettingsChanged() {
        renderHomeSummaries();
        refreshHomePreview(DashboardWidgetLayout.loadPageCount(this));
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
        bindWidgetsHome();
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

        findViewById(R.id.mirror_tools_button).setOnClickListener(view ->
                startActivity(DashboardSettingsActivity.intentFor(this,
                        DashboardSettingsActivity.SECTION_MIRROR_TOOLS))
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

    // ------------------------------------------------------------------
    // Interface: theme and language
    // ------------------------------------------------------------------

    private void bindAppearance() {
        HyperValueRow themeRow = findViewById(R.id.app_theme_input);
        String[] themes = {getString(R.string.app_theme_system), getString(R.string.app_theme_light),
                getString(R.string.app_theme_dark)};
        themeRow.setEntries(themes);
        themeRow.setValue(themes[AppLook.theme(this)]);
        themeRow.setOnItemSelectedListener(position -> AppLook.setTheme(this, position));

        HyperValueRow languageRow = findViewById(R.id.app_language_input);
        List<String> tags = AppLook.languageTags();
        String[] names = new String[tags.size() + 1];
        names[0] = getString(R.string.app_language_system);
        for (int index = 0; index < tags.size(); index++) {
            names[index + 1] = AppLook.nativeName(tags.get(index));
        }
        languageRow.setEntries(names);
        int current = tags.indexOf(AppLook.language()) + 1;
        languageRow.setValue(names[current]);
        languageRow.setOnItemSelectedListener(position ->
                AppLook.setLanguage(position == 0 ? "" : tags.get(position - 1)));

        com.google.android.material.materialswitch.MaterialSwitch monet = findViewById(R.id.app_monet_switch);
        monet.setChecked(AppLook.monet(this));
        monet.setOnCheckedChangeListener((button, checked) -> {
            AppLook.setMonet(this, checked);
            recreate();
        });
        com.google.android.material.materialswitch.MaterialSwitch blur = findViewById(R.id.app_blur_switch);
        blur.setChecked(AppLook.blur(this));
        blur.setOnCheckedChangeListener((button, checked) -> {
            AppLook.setBlur(this, checked);
            navigation.setFrosted(AppLook.blur(this));
        });
        MiuixRows.installSwitches(this, R.id.app_monet_switch, R.id.app_blur_switch);
    }

    // ------------------------------------------------------------------
    // Widgets tab
    // ------------------------------------------------------------------

    private void bindWidgetsHome() {
        homePreview = findViewById(R.id.widgets_home_preview_view);
        homePreviewContainer = findViewById(R.id.widgets_home_preview_container);
        homePageTabs = findViewById(R.id.widgets_home_page_tabs);
        homeRows = findViewById(R.id.widgets_home_rows);
        homeHint = findViewById(R.id.widgets_home_hint);
        // The preview is a picture here. Left to take touches it would move
        // widgets in the free layout, and a long press would switch the phone
        // into always-on, so a tap on it opens the builder instead.
        findViewById(R.id.widgets_home_preview_tap).setOnClickListener(view -> openBuilder(null));
        findViewById(R.id.widgets_home_add_button).setOnClickListener(view ->
                startActivity(new Intent(this, WidgetPickerActivity.class)
                        .putExtra(WidgetPickerActivity.EXTRA_TARGET_PAGE, homePage)));
        findViewById(R.id.widgets_home_builder_button).setOnClickListener(view ->
                openBuilder(null));
        findViewById(R.id.widgets_home_templates_button).setOnClickListener(view ->
                startActivity(new Intent(this, DashboardTemplatesActivity.class)));
        bindSectionRow(R.id.general_content_button,
                DashboardSettingsActivity.SECTION_MODE);
    }

    private void bindSectionRow(int buttonId, int section) {
        findViewById(buttonId).setOnClickListener(view ->
                startActivity(DashboardSettingsActivity.intentFor(this, section)));
    }

    private void openBuilder(@Nullable DashboardWidgetLayout.Widget widget) {
        Intent intent = new Intent(this, DashboardBuilderActivity.class)
                .putExtra(DashboardBuilderActivity.EXTRA_PAGE, homePage);
        if (widget != null) {
            intent.putExtra(DashboardBuilderActivity.EXTRA_WIDGET, widget.name());
        }
        startActivity(intent);
    }

    /** Rebuilds the tab from stored state; the picker and builder change it. */
    private void renderWidgetsHome() {
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        homePage = Math.max(1, Math.min(pageCount, homePage));
        renderHomePageTabs(pageCount);
        renderHomeRows(pageCount);
        refreshHomePreview(pageCount);
        renderHomeSummaries();
    }

    private void renderHomePageTabs(int pageCount) {
        homePageTabs.removeAllViews();
        homePageTabs.setVisibility(pageCount > 1 ? View.VISIBLE : View.GONE);
        if (pageCount <= 1) {
            return;
        }
        MaterialButtonToggleGroup group = (MaterialButtonToggleGroup) getLayoutInflater()
                .inflate(R.layout.widget_segment_group, homePageTabs, false);
        int[] ids = new int[pageCount];
        int home = DashboardWidgetLayout.loadHomePage(this);
        for (int page = 1; page <= pageCount; page++) {
            MaterialButton tab = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.widget_segment_button, group, false);
            tab.setId(View.generateViewId());
            ids[page - 1] = tab.getId();
            tab.setText(page == home ? page + " ★" : String.valueOf(page));
            tab.setContentDescription(WidgetCatalog.pageLabel(this, page));
            group.addView(tab, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        group.check(ids[homePage - 1]);
        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            for (int index = 0; index < ids.length; index++) {
                if (ids[index] == checkedId && index + 1 != homePage) {
                    homePage = index + 1;
                    renderHomeRows(ids.length);
                    refreshHomePreview(ids.length);
                    return;
                }
            }
        });
        homePageTabs.addView(group);
    }

    /** The widgets on the shown page, as the builder lists them. */
    private void renderHomeRows(int pageCount) {
        homeRows.removeAllViews();
        List<DashboardWidgetLayout.Widget> onPage = new ArrayList<>();
        for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.loadOrder(this)) {
            if (DashboardWidgetLayout.isWidgetEnabled(this, widget)
                    && Math.min(pageCount, DashboardWidgetLayout.loadPage(this, widget))
                    == homePage) {
                onPage.add(widget);
            }
        }
        homeRows.setVisibility(onPage.isEmpty() ? View.GONE : View.VISIBLE);
        homeHint.setText(onPage.isEmpty()
                ? R.string.widgets_home_empty : R.string.widgets_home_preview_hint);
        for (int index = 0; index < onPage.size(); index++) {
            DashboardWidgetLayout.Widget widget = onPage.get(index);
            MaterialButton row = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.widget_builder_row, homeRows, false);
            row.setText(WidgetCatalog.label(this, widget));
            row.setShapeAppearanceModel(ShapeAppearanceModel.builder(this, 0,
                    onPage.size() == 1 ? R.style.HyperOS_Shape_Group
                            : index == 0 ? R.style.HyperOS_Shape_RowTop
                            : index == onPage.size() - 1 ? R.style.HyperOS_Shape_RowBottom
                            : R.style.HyperOS_Shape_RowMiddle).build());
            row.setOnClickListener(view -> openBuilder(widget));
            homeRows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void refreshHomePreview(int pageCount) {
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this)
                .withContentMode(RearContentMode.DASHBOARD);
        Point panel = PreviewSupport.panelSize(this);
        homePreview.setDashboardSettings(settings, RearContentMode.DASHBOARD);
        homePreview.setPanelMetrics(panel == null ? 0 : Math.min(panel.x, panel.y),
                panel == null ? 0f : PreviewSupport.panelDensity(this));
        homePreview.setSnapshot(PreviewSupport.snapshot(this));
        // Pinned to the shown page, as the builder pins it: waiting for the
        // preview to cycle round to the page being looked at is no preview.
        homePreview.setSelectedPage(pageCount > 1 ? homePage : 0);
        homePreview.setContentDescription(WidgetCatalog.pageLabel(this, homePage));
        sizeHomePreview(DashboardWidgetLayout.loadPageOrientation(this, homePage), panel);
    }

    /** The panel's own proportions, kept short enough to leave the list in view. */
    private void sizeHomePreview(DashboardWidgetLayout.Orientation orientation,
            @Nullable Point panel) {
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) homePreviewContainer.getLayoutParams();
        int maxHeight = Math.min(dp(220),
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.32f));
        int availableWidth = Math.max(dp(120), getResources().getDisplayMetrics().widthPixels
                - 2 * getResources().getDimensionPixelSize(R.dimen.page_margin_horizontal));
        if (panel == null) {
            boolean portrait = orientation == DashboardWidgetLayout.Orientation.PORTRAIT;
            params.width = portrait ? dp(130) : -1;
            params.height = portrait ? maxHeight : dp(150);
        } else {
            boolean landscape = orientation != DashboardWidgetLayout.Orientation.PORTRAIT;
            float longSide = Math.max(panel.x, panel.y);
            float shortSide = Math.min(panel.x, panel.y);
            float aspect = landscape ? longSide / shortSide : shortSide / longSide;
            int width = availableWidth;
            int height = Math.round(width / aspect);
            if (height > maxHeight) {
                height = maxHeight;
                width = Math.round(height * aspect);
            }
            params.width = width;
            params.height = height;
        }
        params.gravity = Gravity.CENTER_HORIZONTAL;
        homePreviewContainer.setLayoutParams(params);
    }

    /** One line under each settings row saying what it is set to. */
    private void renderHomeSummaries() {
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this);
        String[] modes = getResources().getStringArray(R.array.dashboard_mode_entries);
        String[] themes = getResources().getStringArray(R.array.dashboard_theme_entries);
        String[] idle = {
                getString(R.string.dashboard_idle_15_seconds),
                getString(R.string.dashboard_idle_30_seconds),
                getString(R.string.dashboard_idle_always_on),
        };
        ((TextView) findViewById(R.id.general_content_summary)).setText(
                labelAt(modes, settings.contentMode.ordinal()));
        ((TextView) findViewById(R.id.widgets_home_appearance_summary)).setText(getString(
                R.string.widgets_home_appearance_summary,
                labelAt(themes, settings.theme.ordinal()), settings.textScalePercent));
        ((TextView) findViewById(R.id.widgets_home_screen_summary)).setText(
                labelAt(idle, DashboardWidgetLayout.loadIdleMode(this).ordinal()));
        boolean[] touch = {
                DashboardWidgetLayout.isTapCyclesVariantEnabled(this),
                DashboardWidgetLayout.isPocketLockEnabled(this),
                DashboardWidgetLayout.isFaceDownOffEnabled(this),
        };
        int on = 0;
        for (boolean enabled : touch) {
            if (enabled) {
                on++;
            }
        }
        ((TextView) findViewById(R.id.widgets_home_touch_summary)).setText(
                getString(R.string.widgets_home_touch_summary, on, touch.length));
    }

    private static String labelAt(String[] labels, int index) {
        return index >= 0 && index < labels.length ? labels[index] : "";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        // Running reads green at a glance; off stays on the plain surface.
        View card = findViewById(R.id.status_card);
        ImageView icon = findViewById(R.id.status_icon);
        TextView title = findViewById(R.id.status_title);
        int onColor = ContextCompat.getColor(this, active
                ? R.color.hyper_on_success_container : R.color.hyper_text_primary);
        int quietColor = ContextCompat.getColor(this, active
                ? R.color.hyper_on_success_container : R.color.hyper_text_secondary);
        card.setBackgroundTintList(active ? ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.hyper_success_container)) : null);
        icon.setImageResource(active ? R.drawable.ic_status_on : R.drawable.ic_status_off);
        icon.setImageTintList(ColorStateList.valueOf(active
                ? ContextCompat.getColor(this, R.color.hyper_success) : quietColor));
        title.setTextColor(quietColor);
        statusValue.setTextColor(onColor);
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

    private static final String SOURCE_URL = "https://github.com/tpkarras/Mirror2RearUltra";

    private void openSource() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)));
        } catch (android.content.ActivityNotFoundException ignored) {
            // No browser installed: nothing to open.
        }
    }

    /** Version, licence and where the project comes from. */
    private void showAbout() {
        String version = "";
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = getString(R.string.app_about_version, info.versionName,
                    String.valueOf(info.getLongVersionCode()));
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // Our own package is always installed.
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_about_name)
                .setMessage(version + "\n\n" + getString(R.string.app_about_text))
                .setPositiveButton(R.string.app_about_open, (dialog, which) -> openSource())
                .setNegativeButton(R.string.app_about_close, null)
                .show();
    }
}
