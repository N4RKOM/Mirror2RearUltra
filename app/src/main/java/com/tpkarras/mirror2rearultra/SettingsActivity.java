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
 * profile now lives in {@link ImageSettingsActivity}, profile switching in
 * {@link ProfileManagerActivity}, and the long device section became
 * {@link DeviceProtectionActivity}. What remains here is app-wide, short, and
 * either read-only or infrequent.
 */
public class SettingsActivity extends AppCompatActivity implements
        MirrorState.Listener,
        DeviceCapabilityState.Listener {

    private LinearLayout profileChips;
    private PanelPreviewView hubPreview;
    private TextView profileSummary;
    private TextView toggleButton;
    private TextView widgetsToggleButton;
    private TextView statusValue;
    private TextView rootStatusValue;
    /** Shown over the home screen while the rear screen cannot work without the module. */
    private View moduleBanner;
    private TextView moduleBannerText;
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
    private String[] scaleLabels;
    private List<MirrorProfile> profiles;
    private ActivityResultLauncher<String> exportDocumentLauncher;
    private ActivityResultLauncher<String[]> importDocumentLauncher;
    private final ExecutorService backupExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MirrorState.isActive()) {
            // A session that ended with the process, not with itself, still
            // holds the rear-screen mod's switch.
            RearModGuard.resume(this);
        }
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

        profileChips = findViewById(R.id.hub_profile_chips);
        hubPreview = findViewById(R.id.hub_panel_preview);
        hubPreview.fitToRearPanel();
        profileSummary = findViewById(R.id.hub_profile_summary);
        toggleButton = findViewById(R.id.hub_toggle_button);
        statusValue = findViewById(R.id.status_value);
        rootStatusValue = findViewById(R.id.root_status_value);
        hardwareBrightnessStatusValue = findViewById(R.id.hardware_brightness_status_value);
        capabilityRefreshButton = findViewById(R.id.capability_refresh_button);
        backupStatus = findViewById(R.id.backup_status);
        settingsContent = findViewById(R.id.settings_content);
        moduleBanner = findViewById(R.id.module_banner);
        moduleBannerText = findViewById(R.id.module_banner_text);
        findViewById(R.id.module_banner_button).setOnClickListener(
                view -> startActivity(OnboardingActivity.intent(this)));
        if (savedInstanceState == null && !OnboardingActivity.wasShown(this)) {
            // Fresh data means a fresh setup, whatever the phone already has working:
            // permissions and root are per install, not per phone.
            startActivity(OnboardingActivity.intent(this));
        }

        scaleLabels = getResources().getStringArray(R.array.scale_mode_entries);

        bindInteractions();
        bindAppearance();
        panelSettings = new PanelSettingsController(this, settingsContent,
                this::onPanelSettingsChanged);
        MiuixRows.installWithIcon(this, R.id.device_protection_button,
                R.drawable.ic_row_protection);
        MiuixRows.installWithIcon(this, R.id.app_about_button, R.drawable.ic_row_about);
        MiuixRows.installWithIcon(this, R.id.app_source_button, R.drawable.ic_row_link);
        findViewById(R.id.app_about_button).setOnClickListener(view -> showAbout());
        findViewById(R.id.app_source_button).setOnClickListener(view -> openSource());
        MiuixRows.installWithIcon(this, R.id.widgets_home_add_button, R.drawable.ic_row_add);
        MiuixRows.installChoiceRow(this, R.id.dashboard_mode_input, R.drawable.ic_row_content);
        bindStockCenter();
        MiuixRows.installSwitchWithIcon(this, R.id.stock_center_switch, R.drawable.ic_row_screen);
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
        updateModuleBanner();
    }

    private void updateModuleBanner() {
        int text;
        switch (RearUnlockModule.state(this)) {
            case LOADED:
                moduleBanner.setVisibility(View.GONE);
                return;
            case OUTDATED:
                text = R.string.module_banner_outdated;
                break;
            case NOT_LOADED:
                text = R.string.module_banner_not_loaded;
                break;
            default:
                text = R.string.module_banner_missing;
                break;
        }
        moduleBannerText.setText(text);
        moduleBanner.setVisibility(View.VISIBLE);
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
        toggleButton.setOnClickListener(view -> toggleMirroring());
        widgetsToggleButton = findViewById(R.id.widgets_home_toggle_button);
        widgetsToggleButton.setOnClickListener(view -> toggleDashboard());

        findViewById(R.id.image_settings_button).setOnClickListener(view ->
                startActivity(new Intent(this, ImageSettingsActivity.class))
        );

        findViewById(R.id.manage_profiles_button).setOnClickListener(view ->
                startActivity(new Intent(this, ProfileManagerActivity.class))
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
        String[] themes = {getString(R.string.app_theme_system), getString(R.string.app_theme_light),
                getString(R.string.app_theme_dark)};
        com.google.android.material.button.MaterialButtonToggleGroup themeGroup =
                (com.google.android.material.button.MaterialButtonToggleGroup) getLayoutInflater()
                        .inflate(R.layout.widget_segment_group, null, false);
        themeGroup.setContentDescription(getString(R.string.app_theme_title));
        int[] themeIds = new int[themes.length];
        for (int index = 0; index < themes.length; index++) {
            com.google.android.material.button.MaterialButton segment =
                    (com.google.android.material.button.MaterialButton) getLayoutInflater()
                            .inflate(R.layout.widget_segment_button, themeGroup, false);
            segment.setId(View.generateViewId());
            segment.setText(themes[index]);
            themeIds[index] = segment.getId();
            themeGroup.addView(segment, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        themeGroup.check(themeIds[AppLook.theme(this)]);
        themeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            for (int index = 0; index < themeIds.length; index++) {
                if (themeIds[index] == checkedId) {
                    AppLook.setTheme(this, index);
                    return;
                }
            }
        });
        ((android.widget.FrameLayout) findViewById(R.id.app_theme_segments)).addView(themeGroup);

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
        MiuixRows.installTitleWithIcon(this, R.id.app_theme_title_row, R.drawable.ic_row_appearance);
        MiuixRows.installChoiceRow(this, R.id.app_language_input, R.drawable.ic_row_language);
        MiuixRows.installSwitchWithIcon(this, R.id.app_monet_switch, R.drawable.ic_row_wallpaper);
        MiuixRows.installSwitchWithIcon(this, R.id.app_blur_switch, R.drawable.ic_row_blur);
    }

    private void bindStockCenter() {
        com.google.android.material.materialswitch.MaterialSwitch center = findViewById(R.id.stock_center_switch);
        center.setOnCheckedChangeListener(null);
        center.setEnabled(false);
        StockPanelCenter.query((enabled, ok) -> runOnUiThread(() -> {
            center.setChecked(enabled);
            center.setEnabled(ok);
            center.setOnCheckedChangeListener((button, checked) -> {
                center.setEnabled(false);
                StockPanelCenter.setEnabled(checked, (state, done) -> runOnUiThread(() -> {
                    if (!done) {
                        android.widget.Toast.makeText(this, R.string.stock_center_failed,
                                android.widget.Toast.LENGTH_SHORT).show();
                        bindStockCenter();
                        return;
                    }
                    center.setEnabled(true);
                }));
            });
        }));
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
            View row = MiuixRows.arrowRow(this, WidgetCatalog.label(this, widget),
                    WidgetCatalog.icon(widget), () -> openBuilder(widget));
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
        matchTopBlocksToPreview(panel);
        homePreview.setContentDescription(WidgetCatalog.pageLabel(this, homePage));
        sizeHomePreview(DashboardWidgetLayout.loadPageOrientation(this, homePage), panel);
    }

    /** The panel's own proportions, kept short enough to leave the list in view. */
    private void sizeHomePreview(DashboardWidgetLayout.Orientation orientation,
            @Nullable Point panel) {
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) homePreviewContainer.getLayoutParams();
        int maxHeight = previewMaxHeight();
        int availableWidth = previewAvailableWidth();
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

    private int previewMaxHeight() {
        return Math.min(dp(220),
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.32f));
    }

    private int previewAvailableWidth() {
        return Math.max(dp(120), getResources().getDisplayMetrics().widthPixels
                - 2 * getResources().getDimensionPixelSize(R.dimen.page_margin_horizontal));
    }

    /**
     * The top block of the Mirror and Settings tabs stands where the widgets
     * preview does, as tall as it is on a landscape page, so switching tabs
     * does not make the first block jump.
     */
    private void matchTopBlocksToPreview(@Nullable Point panel) {
        int height = dp(150);
        if (panel != null) {
            float aspect = (float) Math.max(panel.x, panel.y) / Math.min(panel.x, panel.y);
            height = Math.min(Math.round(previewAvailableWidth() / aspect), previewMaxHeight());
        }
        findViewById(R.id.status_card).setMinimumHeight(height);
        findViewById(R.id.capabilities_card).setMinimumHeight(height);
    }

    /** One line under each settings row saying what it is set to. */
    private void renderHomeSummaries() {
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this);
        String[] themes = getResources().getStringArray(R.array.dashboard_theme_entries);
        String[] idle = {
                getString(R.string.dashboard_idle_15_seconds),
                getString(R.string.dashboard_idle_30_seconds),
                getString(R.string.dashboard_idle_always_on),
        };
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
        hubPreview.setProfile(profile);
        ((TextView) findViewById(R.id.hub_image_summary)).setText(getString(
                R.string.hub_tile_image,
                scaleLabels[profile.scaleMode.ordinal()],
                profile.rotationDegrees,
                profile.zoomPercent
        ));
        ((TextView) findViewById(R.id.hub_profiles_summary)).setText(getString(
                R.string.hub_tile_profiles,
                profiles.size(),
                getString(MirrorSettings.isAutoProfileEnabled(this)
                        ? R.string.hub_auto_on : R.string.hub_auto_off)));
    }

    /** Same start and stop as the Quick Settings tile, from the home screen. */
    private void toggleMirroring() {
        if (MirrorState.isActive() && !MirrorState.isDashboardOnly()) {
            MirrorState.setActive(this, false);
            if (ForegroundService.isRunning()) {
                startService(ForegroundService.createStopIntent(this));
            }
            updateStatus(MirrorState.isActive());
            return;
        }
        if (MirrorState.isActive()) {
            MirrorState.setActive(this, false);
        }
        MirrorState.armSession(this, false);
        startActivity(new Intent(this, DisplayActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    /** Same start and stop as the widgets tile, from the Widgets tab. */
    private void toggleDashboard() {
        Intent intent = DashboardLauncher.toggle(this);
        updateStatus(MirrorState.isActive());
        if (intent != null) {
            startActivity(intent);
        }
    }

    private void selectProfile(MirrorProfile profile) {
        if (activeProfile != null && activeProfile.id.equals(profile.id)) {
            return;
        }
        activeProfile = MirrorSettings.selectProfile(this, profile.id);
        refreshProfileOptions(activeProfile.id);
        updateProfileSummary(activeProfile);
        settingsContent.announceForAccessibility(getString(
                R.string.profile_selected_announcement,
                profileName(activeProfile)
        ));
    }

    private void updateStatus(boolean running) {
        // Only the widgets running is not a broadcast, and must not read as one.
        boolean active = running && !MirrorState.isDashboardOnly();
        statusValue.setText(active ? R.string.hub_status_on : R.string.hub_status_off);
        statusValue.setContentDescription(getString(
                active ? R.string.status_active_accessibility : R.string.status_inactive_accessibility
        ));
        // Running reads green at a glance; off stays on the plain surface.
        View card = findViewById(R.id.status_card);
        TextView title = findViewById(R.id.status_title);
        int onColor = ContextCompat.getColor(this, active
                ? R.color.hyper_on_success_container : R.color.hyper_text_primary);
        int quietColor = ContextCompat.getColor(this, active
                ? R.color.hyper_on_success_container : R.color.hyper_text_secondary);
        card.setBackgroundTintList(active ? ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.hyper_success_container)) : null);
        title.setTextColor(quietColor);
        statusValue.setTextColor(onColor);
        profileSummary.setTextColor(quietColor);
        // The button follows the tile: it stops a mirror session, and starts one
        // when nothing, or only the widgets, is running.
        boolean mirroring = active;
        toggleButton.setText(mirroring ? R.string.hub_stop : R.string.hub_start);
        boolean widgets = running && MirrorState.isDashboardOnly();
        widgetsToggleButton.setText(widgets
                ? R.string.widgets_home_stop : R.string.widgets_home_start);
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
        ProfileChips.bind(this, profileChips, profiles, selectedId, this::selectProfile);
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
