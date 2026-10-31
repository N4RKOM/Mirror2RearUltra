package com.tpkarras.mirror2rearultra;

import android.content.ClipData;
import android.net.Uri;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.Display;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Arranges the rear panel one page at a time.
 *
 * <p>The screen used to be one long list: the preview, a page-count row, a
 * separate row to pick which page the preview showed, every page's widgets
 * under headings with nine controls in each card, and the presets, templates
 * and triggers in between. Pages are tabs now, and everything under the tabs
 * belongs to the page picked there. A widget's settings open in a sheet, and
 * the arrangement-wide tools moved to {@link DashboardTemplatesActivity}.
 */
public class DashboardBuilderActivity extends AppCompatActivity {
    /** The page to open on, as the widgets tab last showed it. */
    static final String EXTRA_PAGE = "page";
    /** A widget (by enum name) whose settings should open straight away. */
    static final String EXTRA_WIDGET = "widget";

    private final List<DashboardWidgetLayout.Widget> widgets = new ArrayList<>();
    /** Set from {@link #EXTRA_WIDGET} and consumed once the screen is built. */
    @Nullable private DashboardWidgetLayout.Widget pendingEditor;
    private NestedScrollView scroll;
    private FrameLayout pageTabs;
    private MaterialButton addPageButton;
    private MaterialButton pageActionsButton;
    private RearDashboardView preview;
    private MaterialCardView previewContainer;
    private HyperValueRow layoutRow;
    private HyperValueRow orientationRow;
    private HyperValueRow pageNameRow;
    private MaterialSwitch pageInCycleSwitch;
    private View autoPagesGroup;
    private View autoPagesHelp;
    private MaterialSwitch autoPagesSwitch;
    private MaterialSwitch customImageSwitch;
    private TextView customImageStatus;
    private View customImageRemoveButton;
    private HyperSlider customImageOpacitySlider;
    private TextView customImageOpacityValue;
    /** Guards the image controls while they are written from stored state. */
    private boolean bindingImage;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private final java.util.concurrent.ExecutorService imageExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Guards the cycle switch while it is written from stored state. */
    private boolean bindingCycle;
    private TextView previewHint;
    private TextView overflowNote;
    private View snapGroup;
    private MaterialSwitch snapSwitch;
    /** Guards the switch while it is being written from stored state. */
    private boolean bindingSnap;
    private TextView widgetsHeader;
    private LinearLayout rows;
    private TextView listHint;
    /** The widget picked in the preview or the list, outlined in both. */
    @Nullable private DashboardWidgetLayout.Widget selectedWidget;
    private MaterialToolbar toolbar;
    /** The folded-open settings of the expanded widget, inside its own item of the list. */
    @Nullable private LinearLayout editorRows;
    /** The widget the editor is open for, or null while it is closed. */
    @Nullable private DashboardWidgetLayout.Widget editingWidget;
    /** The three sections under the preview: widgets, this page, and what all pages share. */
    private static final int SECTION_WIDGETS = 0;
    private static final int SECTION_PAGE = 1;
    private static final int SECTION_MORE = 2;
    private static final String STATE_SECTION = "section";
    private int section = SECTION_WIDGETS;
    private MaterialButtonToggleGroup sectionGroup;
    private View[] sectionPanels;
    private FrameLayout previewSlot;
    /** Brings an editor control back in line after a pinch in the preview. */
    @Nullable private Runnable editorSync;
    /**
     * The page being edited: the tab that is checked, the page the preview
     * shows, and the page the layout rows and the widget list belong to.
     */
    private int editedPage = 1;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::importImage);
        setContentView(R.layout.activity_dashboard_builder);
        MiuixRows.install(this, R.id.dashboard_builder_choose, R.id.dashboard_builder_templates);
        toolbar = findViewById(R.id.dashboard_builder_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        toolbar.inflateMenu(R.menu.dashboard_builder);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.dashboard_builder_menu_help) {
                showHelp();
                return true;
            }
            if (item.getItemId() == R.id.dashboard_builder_menu_reset) {
                confirmReset();
                return true;
            }
            return false;
        });

        scroll = findViewById(R.id.dashboard_builder_scroll);
        previewSlot = findViewById(R.id.dashboard_builder_preview_slot);
        if (state != null) {
            section = state.getInt(STATE_SECTION, SECTION_WIDGETS);
        }
        bindSections();
        pageTabs = findViewById(R.id.dashboard_builder_page_tabs);
        addPageButton = findViewById(R.id.dashboard_builder_page_add);
        pageActionsButton = findViewById(R.id.dashboard_builder_page_actions);
        preview = findViewById(R.id.dashboard_builder_preview_view);
        previewContainer = findViewById(R.id.dashboard_builder_preview_container);
        layoutRow = findViewById(R.id.dashboard_builder_layout);
        orientationRow = findViewById(R.id.dashboard_builder_orientation);
        pageNameRow = findViewById(R.id.dashboard_builder_page_name);
        pageInCycleSwitch = findViewById(R.id.dashboard_builder_page_in_cycle);
        autoPagesGroup = findViewById(R.id.dashboard_builder_auto_pages_group);
        autoPagesHelp = findViewById(R.id.dashboard_builder_auto_pages_help);
        autoPagesSwitch = findViewById(R.id.dashboard_builder_auto_pages);
        // With the pages, where the question of turning them comes up; it
        // used to sit on the panel settings screen, away from any page.
        autoPagesSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingCycle) {
                return;
            }
            DashboardWidgetLayout.setAutoPageSwitchEnabled(this, checked);
            notifyDashboardChanged();
        });
        // Replaces the row's own chooser: a name is typed, not picked.
        pageNameRow.setOnClickListener(view -> promptPageName());
        pageInCycleSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingCycle) {
                return;
            }
            DashboardWidgetLayout.setPageInCycle(this, editedPage, checked);
            notifyDashboardChanged();
        });
        previewHint = findViewById(R.id.dashboard_builder_preview_hint);
        overflowNote = findViewById(R.id.dashboard_builder_overflow);
        snapGroup = findViewById(R.id.dashboard_builder_snap_group);
        snapSwitch = findViewById(R.id.dashboard_builder_snap_switch);
        widgetsHeader = findViewById(R.id.dashboard_builder_widgets_header);
        rows = findViewById(R.id.dashboard_builder_rows);
        listHint = findViewById(R.id.dashboard_builder_empty);

        addPageButton.setOnClickListener(view -> addPage());
        pageActionsButton.setOnClickListener(view -> showPageActions(view));

        String[] layoutLabels = getResources().getStringArray(R.array.dashboard_layout_entries);
        layoutRow.setEntries(layoutLabels);
        layoutRow.setOnItemSelectedListener(position -> {
            if (position >= 0 && position < DashboardSettings.Layout.values().length) {
                setPageLayout(DashboardSettings.Layout.values()[position]);
            }
        });
        String[] orientationLabels =
                getResources().getStringArray(R.array.dashboard_orientation_entries);
        orientationRow.setEntries(orientationLabels);
        orientationRow.setOnItemSelectedListener(position -> {
            if (position >= 0 && position < DashboardWidgetLayout.Orientation.values().length) {
                DashboardWidgetLayout.savePageOrientation(this, editedPage,
                        DashboardWidgetLayout.Orientation.values()[position]);
                notifyDashboardChanged();
            }
        });

        snapSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingSnap) {
                return;
            }
            DashboardWidgetLayout.setGridSnapEnabled(this, checked);
            // Switching it on tidies what is already there; switching it off
            // leaves every widget exactly where it was, so a grid can be used
            // to line things up and then dropped without undoing the work.
            if (checked) {
                preview.snapAllToGrid();
                notifyDashboardChanged();
            } else {
                refreshPreview();
            }
        });

        rows.setOnDragListener(this::handleRowDrop);
        bindPreview();
        // Without this the preview keeps whatever the track was doing when it
        // was last drawn, which is the very confusion the icon was fixed for.
        MediaWidgetState.addListener(mediaListener);

        findViewById(R.id.dashboard_builder_choose).setOnClickListener(view ->
                startActivity(new Intent(this, WidgetPickerActivity.class)
                        .putExtra(WidgetPickerActivity.EXTRA_TARGET_PAGE, editedPage)));
        bindCustomImage();
        findViewById(R.id.dashboard_builder_templates).setOnClickListener(view ->
                startActivity(new Intent(this, DashboardTemplatesActivity.class)));

        if (state == null) {
            editedPage = Math.max(1, getIntent().getIntExtra(EXTRA_PAGE, 1));
            String widgetName = getIntent().getStringExtra(EXTRA_WIDGET);
            if (widgetName != null) {
                try {
                    pendingEditor = DashboardWidgetLayout.Widget.valueOf(widgetName);
                } catch (IllegalArgumentException unknown) {
                    pendingEditor = null;
                }
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_SECTION, section);
    }

    /** The switch under the preview that picks which section is shown. */
    private void bindSections() {
        sectionGroup = findViewById(R.id.dashboard_builder_sections);
        sectionPanels = new View[]{
                findViewById(R.id.dashboard_builder_panel_widgets),
                findViewById(R.id.dashboard_builder_panel_page),
                findViewById(R.id.dashboard_builder_panel_more)};
        int[] buttons = {R.id.dashboard_builder_section_widgets,
                R.id.dashboard_builder_section_page, R.id.dashboard_builder_section_more};
        sectionGroup.check(buttons[section]);
        showSection(section);
        sectionGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            for (int index = 0; index < buttons.length; index++) {
                if (buttons[index] == checkedId) {
                    showSection(index);
                    return;
                }
            }
        });
    }

    private void showSection(int next) {
        section = next;
        for (int index = 0; index < sectionPanels.length; index++) {
            sectionPanels[index].setVisibility(index == next ? View.VISIBLE : View.GONE);
        }
        scroll.scrollTo(0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The picker and the templates screen both change the arrangement
        // while this one is stopped.
        reload();
        if (pendingEditor != null) {
            DashboardWidgetLayout.Widget widget = pendingEditor;
            pendingEditor = null;
            if (DashboardWidgetLayout.isWidgetEnabled(this, widget)) {
                // After layout: the editor moves the preview and needs sizes.
                scroll.post(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        openWidgetEditor(widget);
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        MediaWidgetState.removeListener(mediaListener);
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    /** Rebuilds every part of the screen from stored state. */
    private void reload() {
        widgets.clear();
        widgets.addAll(DashboardWidgetLayout.loadOrder(this));
        editedPage = Math.max(1, Math.min(DashboardWidgetLayout.loadPageCount(this), editedPage));
        renderPageTabs();
        renderRows();
        renderCustomImage();
        refreshPreview();
    }

    // ------------------------------------------------------------------
    // Background image
    // ------------------------------------------------------------------

    /**
     * The picture under the widgets, here rather than on the panel settings
     * screen: the preview shows it under the arrangement being built, so its
     * opacity can be judged against the widgets it sits behind.
     */
    private void bindCustomImage() {
        customImageSwitch = findViewById(R.id.dashboard_custom_image_switch);
        customImageStatus = findViewById(R.id.dashboard_custom_image_status);
        customImageRemoveButton = findViewById(R.id.dashboard_custom_image_remove_button);
        customImageOpacitySlider = findViewById(R.id.dashboard_custom_image_opacity_slider);
        customImageOpacityValue = findViewById(R.id.dashboard_custom_image_opacity_value);
        customImageSwitch.setOnCheckedChangeListener((button, checked) -> saveCustomImage());
        findViewById(R.id.dashboard_custom_image_choose_button).setOnClickListener(view ->
                imagePickerLauncher.launch("image/*"));
        customImageRemoveButton.setOnClickListener(view -> {
            DashboardImageStore.remove(this);
            renderCustomImage();
            saveCustomImage();
        });
        customImageOpacitySlider.addOnChangeListener((slider, value, fromUser) -> {
            showCustomImageOpacity(Math.round(value));
            if (fromUser) {
                saveCustomImage();
            }
        });
    }

    private void renderCustomImage() {
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this);
        boolean exists = DashboardImageStore.exists(this);
        bindingImage = true;
        customImageSwitch.setEnabled(exists);
        customImageSwitch.setChecked(settings.showCustomImage && exists);
        customImageStatus.setText(exists ? R.string.dashboard_custom_image_selected
                : R.string.dashboard_custom_image_not_selected);
        customImageRemoveButton.setVisibility(exists ? View.VISIBLE : View.GONE);
        customImageOpacitySlider.setValue(settings.customImageOpacityPercent);
        showCustomImageOpacity(settings.customImageOpacityPercent);
        bindingImage = false;
    }

    private void saveCustomImage() {
        if (bindingImage) {
            return;
        }
        MirrorSettings.saveDashboardSettings(this, MirrorSettings.loadDashboardSettings(this)
                .withCustomImage(customImageSwitch.isChecked() && DashboardImageStore.exists(this),
                        Math.round(customImageOpacitySlider.getValue())));
        refreshPreview();
    }

    private void showCustomImageOpacity(int percent) {
        String value = getString(R.string.dashboard_custom_image_opacity_value, percent);
        customImageOpacityValue.setText(value);
        customImageOpacitySlider.setContentDescription(value);
    }

    /** Copies a picked picture in off the main thread, then shows it. */
    private void importImage(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        customImageStatus.setText(R.string.dashboard_custom_image_loading);
        imageExecutor.execute(() -> {
            try {
                DashboardImageStore.importFromUri(getApplicationContext(), uri);
                runOnUiThread(() -> {
                    renderCustomImage();
                    bindingImage = true;
                    customImageSwitch.setChecked(true);
                    bindingImage = false;
                    saveCustomImage();
                });
            } catch (java.io.IOException error) {
                runOnUiThread(() ->
                        customImageStatus.setText(R.string.dashboard_custom_image_error));
            }
        });
    }

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    /**
     * One tab per page, the main page marked with a star.
     *
     * <p>A tab is also where a dragged widget can be dropped to move it to
     * that page, and holding one opens the same page actions as the button
     * beside the tabs.
     */
    private void renderPageTabs() {
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        int home = DashboardWidgetLayout.loadHomePage(this);
        pageTabs.removeAllViews();
        MaterialButtonToggleGroup group = (MaterialButtonToggleGroup) getLayoutInflater()
                .inflate(R.layout.widget_segment_group, pageTabs, false);
        int[] ids = new int[pageCount];
        for (int page = 1; page <= pageCount; page++) {
            MaterialButton tab = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.widget_segment_button, group, false);
            tab.setId(View.generateViewId());
            ids[page - 1] = tab.getId();
            tab.setText(page == home ? page + " ★" : String.valueOf(page));
            tab.setContentDescription(page == home
                    ? getString(R.string.dashboard_page_home_description, pageLabel(page))
                    : pageLabel(page));
            int target = page;
            tab.setOnLongClickListener(view -> {
                selectPage(target);
                // The tabs were just rebuilt, so the button stays the anchor.
                showPageActions(pageActionsButton);
                return true;
            });
            tab.setOnDragListener((view, event) -> handleTabDrop(view, event, target));
            group.addView(tab, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        group.check(ids[editedPage - 1]);
        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            for (int index = 0; index < ids.length; index++) {
                if (ids[index] == checkedId) {
                    selectPage(index + 1);
                    return;
                }
            }
        });
        pageTabs.addView(group);
        addPageButton.setEnabled(pageCount < DashboardWidgetLayout.MAX_PAGES);
        pageActionsButton.setContentDescription(
                getString(R.string.dashboard_page_actions, editedPage));
    }

    /**
     * Moves the editing to another page.
     *
     * <p>The selection is dropped because the widget that was picked belongs
     * to the page being left.
     */
    private void selectPage(int page) {
        selectPage(page, false);
    }

    /**
     * @param keepEditor true when the open editor's widget moved to this page
     *     and the editor should follow it; otherwise it is closed, since its
     *     widget belongs to the page being left
     */
    private void selectPage(int page, boolean keepEditor) {
        if (page == editedPage) {
            return;
        }
        if (!keepEditor && editingWidget != null) {
            closeWidgetEditor();
        }
        editedPage = page;
        selectedWidget = null;
        preview.setSelectedWidget(null);
        renderPageTabs();
        renderRows();
        refreshPreview();
    }

    private void addPage() {
        closeWidgetEditor();
        int page = DashboardWidgetLayout.addPage(this);
        if (page == 0) {
            return;
        }
        editedPage = page;
        selectedWidget = null;
        notifyDashboardChanged();
        renderPageTabs();
        renderRows();
        pageTabs.announceForAccessibility(getString(R.string.dashboard_builder_page_section, page));
    }

    private void showPageActions(View anchor) {
        closeWidgetEditor();
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        boolean home = DashboardWidgetLayout.loadHomePage(this) == editedPage;
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        Menu items = menu.getMenu();
        items.add(0, 1, 0, R.string.dashboard_page_make_home).setEnabled(!home);
        items.add(0, 2, 1, R.string.dashboard_page_move_left).setEnabled(editedPage > 1);
        items.add(0, 3, 2, R.string.dashboard_page_move_right).setEnabled(editedPage < pageCount);
        items.add(0, 5, 3, R.string.dashboard_page_duplicate)
                .setEnabled(pageCount < DashboardWidgetLayout.MAX_PAGES);
        items.add(0, 4, 4, R.string.dashboard_page_delete).setEnabled(pageCount > 1);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    DashboardWidgetLayout.saveHomePage(this, editedPage);
                    notifyDashboardChanged();
                    renderPageTabs();
                    return true;
                case 2:
                    movePage(-1);
                    return true;
                case 3:
                    movePage(1);
                    return true;
                case 4:
                    confirmDeletePage();
                    return true;
                case 5:
                    duplicatePage();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    /**
     * Adds a page with the edited page's look and goes to it. The widgets
     * stay where they are: a widget sits on one page only.
     */
    private void duplicatePage() {
        String name = DashboardWidgetLayout.loadPageName(this, editedPage);
        String copyName = name.isEmpty() ? "" : getString(R.string.dashboard_page_copy_name, name);
        int page = DashboardWidgetLayout.duplicatePage(this, editedPage, copyName);
        if (page == 0) {
            return;
        }
        editedPage = page;
        selectedWidget = null;
        notifyDashboardChanged();
        renderPageTabs();
        renderRows();
        pageTabs.announceForAccessibility(pageLabel(page));
    }

    /**
     * The name sheet for the edited page. An empty name takes the page back
     * to its number.
     */
    private void promptPageName() {
        // Inflated into a holder so its margins survive: without a parent
        // the field ran edge to edge of the dialog.
        android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        com.google.android.material.textfield.TextInputLayout inputLayout =
                (com.google.android.material.textfield.TextInputLayout) getLayoutInflater()
                        .inflate(R.layout.dialog_template_name, holder, false);
        holder.addView(inputLayout);
        inputLayout.setHint(getString(R.string.dashboard_page_name));
        com.google.android.material.textfield.TextInputEditText input =
                inputLayout.findViewById(R.id.template_name_input);
        input.setText(DashboardWidgetLayout.loadPageName(this, editedPage));
        input.setSelection(input.length());
        int page = editedPage;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dashboard_page_rename_title)
                .setView(holder)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    DashboardWidgetLayout.savePageName(this, page,
                            input.getText() == null ? "" : input.getText().toString());
                    notifyDashboardChanged();
                    renderPageTabs();
                    renderRows();
                })
                .show();
    }

    /** "Page 2", or "Page 2 · Road" once it has a name. */
    private String pageLabel(int page) {
        String name = DashboardWidgetLayout.loadPageName(this, page);
        return name.isEmpty()
                ? getString(R.string.dashboard_builder_page_section, page)
                : getString(R.string.dashboard_page_label_named, page, name);
    }

    /** Swaps the edited page with its neighbour and follows it there. */
    private void movePage(int direction) {
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        int target = editedPage + direction;
        if (target < 1 || target > pageCount) {
            return;
        }
        DashboardWidgetLayout.remapPages(this,
                DashboardPages.swap(pageCount, editedPage, target));
        editedPage = target;
        reload();
    }

    private void confirmDeletePage() {
        if (currentPages().get(editedPage - 1).isEmpty()) {
            deletePage();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dashboard_page_delete)
                .setMessage(getString(R.string.dashboard_page_delete_message, editedPage))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dashboard_page_delete, (dialog, which) -> deletePage())
                .show();
    }

    private void deletePage() {
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        if (pageCount <= 1) {
            return;
        }
        DashboardWidgetLayout.remapPages(this, DashboardPages.remove(pageCount, editedPage));
        editedPage = Math.max(1, editedPage - 1);
        selectedWidget = null;
        reload();
    }

    /**
     * Sets the edited page's arrangement.
     *
     * <p>Page one's is the panel-wide setting the rear-panel screen shows, so
     * it is written there; later pages are overrides of it.
     */
    private void setPageLayout(DashboardSettings.Layout next) {
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this);
        if (next == DashboardWidgetLayout.loadPageLayout(this, editedPage, settings.layout)) {
            return;
        }
        if (next == DashboardSettings.Layout.FREE) {
            // Seeded from the frame still on screen, so the free layout opens
            // where the flowed one left off.
            preview.seedFreePositions();
        }
        if (editedPage <= 1) {
            MirrorSettings.saveDashboardSettings(this, settings.withLayout(next));
            refreshPreview();
        } else {
            DashboardWidgetLayout.savePageLayout(this, editedPage, next);
            notifyDashboardChanged();
        }
        // The list and an open widget's settings were built for the old layout:
        // the grips, the hint, the size control and the alignment all depend on it.
        renderRows();
    }

    // ------------------------------------------------------------------
    // Widget list
    // ------------------------------------------------------------------

    /**
     * The widgets that are on, split into the pages they sit on.
     *
     * <p>This is the model the list, dragging and the move actions all work
     * on, so they cannot disagree about where a widget is.
     */
    private List<List<DashboardWidgetLayout.Widget>> currentPages() {
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        List<List<DashboardWidgetLayout.Widget>> pages = new ArrayList<>();
        for (int page = 0; page < pageCount; page++) {
            pages.add(new ArrayList<>());
        }
        for (DashboardWidgetLayout.Widget widget : widgets) {
            if (!DashboardWidgetLayout.isWidgetEnabled(this, widget)) {
                continue;
            }
            int page = Math.min(pageCount, DashboardWidgetLayout.loadPage(this, widget));
            pages.get(page - 1).add(widget);
        }
        return pages;
    }

    /**
     * Writes a rearranged model back out.
     *
     * <p>Both the page each widget sits on and the order within it come from
     * one walk of the model, so a widget cannot end up on a page the list does
     * not show it in. Widgets that are switched off keep the places they held
     * in the stored order, so turning one back on does not send it to the end.
     */
    private void applyArrangement(List<List<DashboardWidgetLayout.Widget>> pages) {
        List<DashboardWidgetLayout.Widget> flat = new ArrayList<>();
        for (int page = 0; page < pages.size(); page++) {
            for (DashboardWidgetLayout.Widget widget : pages.get(page)) {
                DashboardWidgetLayout.savePage(this, widget, page + 1);
                flat.add(widget);
            }
        }
        List<DashboardWidgetLayout.Widget> merged = new ArrayList<>();
        int next = 0;
        for (DashboardWidgetLayout.Widget widget : widgets) {
            if (DashboardWidgetLayout.isWidgetEnabled(this, widget) && next < flat.size()) {
                merged.add(flat.get(next++));
            } else {
                merged.add(widget);
            }
        }
        widgets.clear();
        widgets.addAll(merged);
        DashboardWidgetLayout.saveOrder(this, widgets);
        renderRows();
        notifyDashboardChanged();
    }

    /**
     * Puts a widget at a place on a page.
     *
     * @param toIndex place on the target page, or -1 for its end
     * @return false when the page could not take it, which is said on screen
     */
    private boolean moveWidget(DashboardWidgetLayout.Widget widget, int toPage, int toIndex) {
        List<List<DashboardWidgetLayout.Widget>> pages = currentPages();
        int fromPage = -1;
        int fromIndex = -1;
        for (int page = 0; page < pages.size(); page++) {
            int index = pages.get(page).indexOf(widget);
            if (index >= 0) {
                fromPage = page + 1;
                fromIndex = index;
                break;
            }
        }
        if (fromPage < 0 || toPage < 1 || toPage > pages.size()) {
            return false;
        }
        if (toPage != fromPage && !DashboardWidgetLayout.canMoveTo(this, widget, toPage)) {
            Toast.makeText(this, getString(R.string.dashboard_page_full, toPage),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        pages.get(fromPage - 1).remove(fromIndex);
        List<DashboardWidgetLayout.Widget> target = pages.get(toPage - 1);
        int index = toIndex < 0 ? target.size() : toIndex;
        if (toPage == fromPage && fromIndex < index) {
            index--;
        }
        target.add(Math.max(0, Math.min(index, target.size())), widget);
        applyArrangement(pages);
        if (toPage != fromPage) {
            rows.announceForAccessibility(getString(R.string.dashboard_builder_moved,
                    label(widget), toPage));
        }
        return true;
    }

    /**
     * Lists the edited page's widgets as rows of one group.
     *
     * <p>A row is the widget's name and nothing else: tapping it opens its
     * settings, holding it drags it - within the list to reorder, or onto a
     * tab to move it to that page. The same moves are offered to TalkBack as
     * actions on the row, since a drag is not something it can perform.
     */
    private void renderRows() {
        rows.removeAllViews();
        List<List<DashboardWidgetLayout.Widget>> pages = currentPages();
        List<DashboardWidgetLayout.Widget> page = pages.get(editedPage - 1);
        String pageName = DashboardWidgetLayout.loadPageName(this, editedPage);
        widgetsHeader.setText(!pageName.isEmpty()
                ? getString(R.string.dashboard_page_named_widgets, pageName)
                : pages.size() > 1
                ? getString(R.string.dashboard_builder_page_widgets, editedPage)
                : getString(R.string.dashboard_builder_widgets_title));
        rows.setVisibility(page.isEmpty() ? View.GONE : View.VISIBLE);
        int total = 0;
        for (List<DashboardWidgetLayout.Widget> each : pages) {
            total += each.size();
        }
        boolean free = DashboardWidgetLayout.loadPageLayout(this, editedPage,
                MirrorSettings.loadDashboardSettings(this).layout) == DashboardSettings.Layout.FREE;
        listHint.setText(total == 0 ? R.string.dashboard_builder_empty
                : page.isEmpty() ? R.string.dashboard_builder_page_empty_hint
                : !free ? R.string.dashboard_builder_list_hint
                : pages.size() > 1 ? R.string.dashboard_builder_list_hint_free_pages
                : R.string.dashboard_builder_list_hint_free);
        for (int index = 0; index < page.size(); index++) {
            DashboardWidgetLayout.Widget widget = page.get(index);
            // Each widget is a card of its own: the row, and under it the
            // settings that fold open in place when the row is tapped.
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackgroundResource(R.drawable.hyper_group_background);
            item.setTag(widget);
            View row = getLayoutInflater().inflate(R.layout.widget_builder_item, item, false);
            ((TextView) row.findViewById(R.id.builder_item_title)).setText(label(widget));
            ((TextView) row.findViewById(R.id.builder_item_summary))
                    .setText(widgetSummary(widget, free));
            ((android.widget.ImageView) row.findViewById(R.id.builder_item_icon))
                    .setImageResource(WidgetCatalog.icon(widget));
            row.setBackgroundResource(R.drawable.hyper_row_bg_single);
            markSelected(row, widget == selectedWidget);
            row.setOnClickListener(view -> {
                if (widget == editingWidget) {
                    closeWidgetEditor();
                } else {
                    openWidgetEditor(widget);
                }
            });
            View handle = row.findViewById(R.id.builder_item_handle);
            if (free) {
                // A free page puts each widget where it was dropped in the
                // preview, so the list has no order to change: no grip, and
                // a long press only carries the row to another page's tab.
                handle.setVisibility(View.GONE);
                row.setPaddingRelative(dp(16), 0, row.getPaddingEnd(), 0);
                if (pages.size() > 1) {
                    row.setOnLongClickListener(view -> startRowDrag(item, widget));
                }
            } else {
                row.setOnLongClickListener(view -> startRowDrag(item, widget));
                // The grip drags at once; a long press anywhere on the row does too.
                handle.setOnTouchListener((view, event) ->
                        event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                                && startRowDrag(item, widget));
            }
            addMoveActions(row, widget, index, page.size(), pages.size(), free);
            item.addView(row, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout holder = new LinearLayout(this);
            holder.setOrientation(LinearLayout.VERTICAL);
            holder.setPadding(dp(12), 0, dp(12), dp(14));
            holder.setVisibility(View.GONE);
            item.addView(holder, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
            itemParams.topMargin = index == 0 ? 0 : dp(8);
            rows.addView(item, itemParams);
        }
        // A rebuild keeps the open widget open, on the page it is still on;
        // one that has moved to another page opens again when that page shows.
        editorRows = null;
        if (editingWidget != null && page.contains(editingWidget)) {
            expandItem(editingWidget, false);
        }
    }

    private boolean startRowDrag(View item, DashboardWidgetLayout.Widget widget) {
        ClipData data = ClipData.newPlainText("widget", widget.name());
        boolean started = item.startDragAndDrop(data, new View.DragShadowBuilder(item), widget, 0);
        if (started) {
            item.setAlpha(0.35f);
        }
        return started;
    }

    /** "Normal · Center · Accent": what the widget is set to, without opening it. */
    private String widgetSummary(DashboardWidgetLayout.Widget widget, boolean free) {
        String[] sizes = {getString(R.string.dashboard_builder_small),
                getString(R.string.dashboard_builder_normal),
                getString(R.string.dashboard_builder_large)};
        String[] positions = {getString(R.string.dashboard_builder_left),
                getString(R.string.dashboard_builder_center),
                getString(R.string.dashboard_builder_right)};
        String[] styles = {getString(R.string.dashboard_builder_style_default),
                getString(R.string.dashboard_builder_style_accent),
                getString(R.string.dashboard_builder_style_muted)};
        List<String> parts = new ArrayList<>();
        if (free) {
            parts.add(getString(R.string.dashboard_builder_size_percent,
                    Math.round(DashboardWidgetLayout.scale(this, widget) * 100f)));
        } else {
            parts.add(sizes[DashboardWidgetLayout.loadSize(this, widget).ordinal()]);
            parts.add(positions[DashboardWidgetLayout.loadPosition(this, widget).ordinal()]);
        }
        int style = DashboardWidgetLayout.loadStyle(this, widget).ordinal();
        if (style != 0) {
            parts.add(styles[style]);
        }
        return android.text.TextUtils.join(" · ", parts);
    }

    private void addMoveActions(View row, DashboardWidgetLayout.Widget widget,
            int index, int pageSize, int pageCount, boolean free) {
        if (!free && index > 0) {
            ViewCompat.addAccessibilityAction(row,
                    getString(R.string.dashboard_builder_move_up, label(widget)),
                    (view, arguments) -> {
                        moveWidget(widget, editedPage, index - 1);
                        return true;
                    });
        }
        if (!free && index < pageSize - 1) {
            ViewCompat.addAccessibilityAction(row,
                    getString(R.string.dashboard_builder_move_down, label(widget)),
                    (view, arguments) -> {
                        moveWidget(widget, editedPage, index + 2);
                        return true;
                    });
        }
        for (int page = 1; page <= pageCount; page++) {
            if (page == editedPage || !DashboardWidgetLayout.canMoveTo(this, widget, page)) {
                continue;
            }
            int target = page;
            ViewCompat.addAccessibilityAction(row,
                    getString(R.string.dashboard_builder_move_to_page, label(widget), target),
                    (view, arguments) -> {
                        moveWidget(widget, target, -1);
                        return true;
                    });
        }
    }

    /** Picks a widget in both the preview and the list. */
    private void selectWidget(@Nullable DashboardWidgetLayout.Widget widget) {
        selectedWidget = widget;
        preview.setSelectedWidget(widget);
        for (int index = 0; index < rows.getChildCount(); index++) {
            View child = rows.getChildAt(index);
            if (child.getTag() instanceof DashboardWidgetLayout.Widget) {
                markSelected(((ViewGroup) child).getChildAt(0),
                        widget != null && child.getTag() == widget);
            }
        }
    }

    private void markSelected(View row, boolean picked) {
        android.content.res.ColorStateList color = ContextCompat.getColorStateList(this,
                picked ? R.color.hyper_text_button_text : R.color.hyper_row_title_text);
        ((TextView) row.findViewById(R.id.builder_item_title)).setTextColor(color);
        ((android.widget.ImageView) row.findViewById(R.id.builder_item_icon))
                .setImageTintList(ContextCompat.getColorStateList(this, picked
                        ? R.color.hyper_text_button_text : R.color.hyper_text_secondary));
    }

    /** Reorders within the edited page, by where the finger lets go. */
    private boolean handleRowDrop(View view, DragEvent event) {
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            // A drag that went nowhere leaves the lifted row dimmed.
            for (int child = 0; child < rows.getChildCount(); child++) {
                rows.getChildAt(child).setAlpha(1f);
            }
            return true;
        }
        if (event.getAction() != DragEvent.ACTION_DROP) {
            return true;
        }
        Object state = event.getLocalState();
        if (!(state instanceof DashboardWidgetLayout.Widget)) {
            return false;
        }
        // A free page has no order to rearrange; only the tabs take a drop.
        if (DashboardWidgetLayout.loadPageLayout(this, editedPage,
                MirrorSettings.loadDashboardSettings(this).layout) == DashboardSettings.Layout.FREE) {
            return true;
        }
        int index = rows.getChildCount();
        for (int child = 0; child < rows.getChildCount(); child++) {
            View row = rows.getChildAt(child);
            if (event.getY() < row.getTop() + row.getHeight() / 2f) {
                index = child;
                break;
            }
        }
        moveWidget((DashboardWidgetLayout.Widget) state, editedPage, index);
        return true;
    }

    /** A widget dropped on a tab goes to the end of that page. */
    private boolean handleTabDrop(View tab, DragEvent event, int page) {
        Object state = event.getLocalState();
        if (!(state instanceof DashboardWidgetLayout.Widget)) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                tab.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120L).start();
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                tab.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
                return true;
            case DragEvent.ACTION_DROP:
                if (page != editedPage) {
                    moveWidget((DashboardWidgetLayout.Widget) state, page, -1);
                }
                return true;
            default:
                return true;
        }
    }

    // ------------------------------------------------------------------
    // Widget settings, folded open inside the widget's own row
    // ------------------------------------------------------------------

    /**
     * Opens a widget's settings under its row in the list.
     *
     * <p>The way the panel blocks on the main screen open: the row's arrow
     * turns, the settings fade in while the cards below slide to make room,
     * and only one widget is open at a time. The preview stays pinned above,
     * with the widget outlined, so it is in sight however far down the
     * settings run. Tapping a widget in the preview opens its row too.
     */
    private void openWidgetEditor(DashboardWidgetLayout.Widget widget) {
        if (widget == editingWidget) {
            return;
        }
        expandItem(widget, true);
        selectWidget(widget);
    }

    private void closeWidgetEditor() {
        if (editingWidget == null) {
            return;
        }
        DashboardWidgetLayout.Widget closing = editingWidget;
        beginSectionTransition();
        collapseOpenItem();
        editingWidget = null;
        editorRows = null;
        editorSync = null;
        refreshItemSummary(closing);
        refreshPreview();
    }

    /** Shows the settings of [widget] in its item, folding any other one shut. */
    private void expandItem(DashboardWidgetLayout.Widget widget, boolean animate) {
        LinearLayout item = itemOf(widget);
        if (item == null) {
            return;
        }
        if (animate) {
            beginSectionTransition();
        }
        DashboardWidgetLayout.Widget previous = editingWidget;
        collapseOpenItem();
        if (previous != null) {
            refreshItemSummary(previous);
        }
        editingWidget = widget;
        editorSync = null;
        LinearLayout holder = (LinearLayout) item.getChildAt(1);
        holder.removeAllViews();
        editorRows = holder;
        fillWidgetSheet(holder, widget);
        holder.setVisibility(View.VISIBLE);
        item.getChildAt(0).findViewById(R.id.builder_item_chevron).setRotation(180f);
        if (animate) {
            // After the slide, so the open widget lands in view rather than below it.
            scroll.postDelayed(() -> {
                if (!isFinishing() && editingWidget == widget && itemOf(widget) != null) {
                    scroll.smoothScrollTo(0, Math.max(0, topWithin(itemOf(widget), scroll) - dp(8)));
                }
            }, 260L);
        }
    }

    private void collapseOpenItem() {
        LinearLayout item = editingWidget == null ? null : itemOf(editingWidget);
        if (item != null) {
            LinearLayout holder = (LinearLayout) item.getChildAt(1);
            holder.setVisibility(View.GONE);
            holder.removeAllViews();
            item.getChildAt(0).findViewById(R.id.builder_item_chevron).setRotation(0f);
        }
    }

    private void beginSectionTransition() {
        android.transition.TransitionManager.beginDelayedTransition(
                (ViewGroup) scroll.getChildAt(0),
                new android.transition.TransitionSet()
                        .addTransition(new android.transition.Fade())
                        .addTransition(new android.transition.ChangeBounds())
                        .setDuration(240L));
    }

    @Nullable
    private LinearLayout itemOf(DashboardWidgetLayout.Widget widget) {
        for (int index = 0; index < rows.getChildCount(); index++) {
            View child = rows.getChildAt(index);
            if (child.getTag() == widget) {
                return (LinearLayout) child;
            }
        }
        return null;
    }

    /** How far down the scrolled content [view] starts. */
    private static int topWithin(View view, View container) {
        int top = 0;
        View current = view;
        while (current != null && current != container) {
            top += current.getTop();
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return top;
    }

    /** What the row says the widget is set to, after a change made under it. */
    private void refreshItemSummary(DashboardWidgetLayout.Widget widget) {
        LinearLayout item = itemOf(widget);
        if (item == null) {
            return;
        }
        boolean free = DashboardWidgetLayout.loadPageLayout(this, editedPage,
                MirrorSettings.loadDashboardSettings(this).layout) == DashboardSettings.Layout.FREE;
        ((TextView) item.getChildAt(0).findViewById(R.id.builder_item_summary))
                .setText(widgetSummary(widget, free));
    }

    private void fillWidgetSheet(LinearLayout column, DashboardWidgetLayout.Widget widget) {
        // Where it sits, how it looks, what only this widget has, and the
        // rarely touched rest - each a card under its own heading.
        LinearLayout place = settingsCard();
        LinearLayout look = settingsCard();
        LinearLayout special = settingsCard();
        LinearLayout more = settingsCard();
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        if (pageCount > 1) {
            String[] pageLabels = new String[pageCount];
            for (int index = 0; index < pageCount; index++) {
                pageLabels[index] = String.valueOf(index + 1);
            }
            LinearLayout pageRow = segmentedRow(R.string.dashboard_widget_page_label, pageLabels,
                    DashboardWidgetLayout.loadPage(this, widget) - 1,
                    getString(R.string.dashboard_widget_page_label),
                    choice -> {
                        int target = choice + 1;
                        if (target == DashboardWidgetLayout.loadPage(this, widget)) {
                            return;
                        }
                        if (moveWidget(widget, target, -1)) {
                            // Follow it, so the move is seen rather than the
                            // widget simply vanishing from the list.
                            selectPage(target, true);
                            selectWidget(widget);
                            refreshPreview();
                        }
                    });
            // Pages a full-screen widget keeps to itself are shown but not
            // offered, so the choice cannot be made and then quietly undone.
            ViewGroup segments = (ViewGroup) pageRow.getChildAt(1);
            int current = DashboardWidgetLayout.loadPage(this, widget);
            for (int page = 1; page <= segments.getChildCount(); page++) {
                segments.getChildAt(page - 1).setEnabled(page == current
                        || DashboardWidgetLayout.canMoveTo(this, widget, page));
            }
            place.addView(pageRow);
        }

        boolean freeLayout = DashboardWidgetLayout.loadPageLayout(this, editedPage,
                MirrorSettings.loadDashboardSettings(this).layout) == DashboardSettings.Layout.FREE;
        // The free layout places a widget by hand in the preview, so the
        // flowed layouts' alignment is left out there.
        if (!freeLayout) {
            place.addView(segmentedRow(R.string.dashboard_builder_position_label,
                    new String[]{
                            getString(R.string.dashboard_builder_left),
                            getString(R.string.dashboard_builder_center),
                            getString(R.string.dashboard_builder_right)},
                    DashboardWidgetLayout.loadPosition(this, widget).ordinal(),
                    getString(R.string.dashboard_builder_position, label(widget)),
                    choice -> {
                        DashboardWidgetLayout.savePosition(this, widget,
                                DashboardWidgetLayout.Position.values()[choice]);
                        notifyDashboardChanged();
                    }));
        }

        // Size on every page, the free one included: a pinch sets it there
        // too, but a pinch cannot say "back to normal", and a widget left
        // large from some earlier arrangement had no other way down. On a
        // free page the size is continuous, so it gets a slider rather than
        // three steps that could only ever show the nearest one.
        if (freeLayout) {
            place.addView(sizeSlider(widget));
        } else {
            place.addView(segmentedRow(R.string.dashboard_builder_size_label,
                    new String[]{
                            getString(R.string.dashboard_builder_small),
                            getString(R.string.dashboard_builder_normal),
                            getString(R.string.dashboard_builder_large)},
                    DashboardWidgetLayout.loadSize(this, widget).ordinal(),
                    getString(R.string.dashboard_builder_size, label(widget)),
                    choice -> {
                        DashboardWidgetLayout.saveSize(this, widget,
                                DashboardWidgetLayout.Size.values()[choice]);
                        notifyDashboardChanged();
                    }));
        }

        if (DashboardWidgetLayout.supportsVariant(widget)) {
            look.addView(choiceTiles(R.string.dashboard_builder_variant_label,
                    variantCaptions(widget), variantSamples(widget), null,
                    DashboardWidgetLayout.loadVariant(this, widget).ordinal(),
                    getString(R.string.dashboard_builder_variant, label(widget)),
                    choice -> {
                        DashboardWidgetLayout.saveVariant(this, widget,
                                DashboardWidgetLayout.Variant.values()[choice]);
                        notifyDashboardChanged();
                    }));
        }

        // The sample is the colour itself, so the choice reads without words.
        int[] styleColours = {
                ContextCompat.getColor(this, R.color.hyper_text_primary),
                ContextCompat.getColor(this, R.color.hyper_accent),
                ContextCompat.getColor(this, R.color.hyper_text_tertiary)};
        look.addView(choiceTiles(R.string.dashboard_builder_style_label,
                new String[]{
                        getString(R.string.dashboard_builder_style_default),
                        getString(R.string.dashboard_builder_style_accent),
                        getString(R.string.dashboard_builder_style_muted)},
                new String[]{"Aa", "Aa", "Aa"}, styleColours,
                DashboardWidgetLayout.loadStyle(this, widget).ordinal(),
                getString(R.string.dashboard_builder_style, label(widget)),
                choice -> {
                    DashboardWidgetLayout.saveStyle(this, widget,
                            DashboardWidgetLayout.Style.values()[choice]);
                    notifyDashboardChanged();
                }));

        // The one number that decides whether this widget counts up or down,
        // so it belongs with the widget rather than in a settings screen.
        if (widget == DashboardWidgetLayout.Widget.TIMER) {
            int[] choices = {0, 1, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120};
            String[] durationLabels = new String[choices.length];
            for (int index = 0; index < choices.length; index++) {
                durationLabels[index] = choices[index] == 0
                        ? getString(R.string.dashboard_timer_stopwatch)
                        : getResources().getQuantityString(
                                R.plurals.dashboard_timer_minutes,
                                choices[index], choices[index]);
            }
            HyperValueRow durationRow = (HyperValueRow) getLayoutInflater()
                    .inflate(R.layout.widget_value_row_single, column, false);
            durationRow.setTitle(getString(R.string.dashboard_timer_duration));
            durationRow.setEntries(durationLabels);
            int minutes = DashboardWidgetLayout.timerMinutes(this);
            int current = 0;
            for (int index = 0; index < choices.length; index++) {
                if (choices[index] == minutes) {
                    current = index;
                }
            }
            durationRow.setValue(durationLabels[current]);
            durationRow.setBackgroundResource(R.drawable.hyper_segment_track);
            durationRow.setOnItemSelectedListener(position -> {
                if (position >= 0 && position < choices.length) {
                    DashboardWidgetLayout.setTimerMinutes(this, choices[position]);
                    // Changing the length mid-count would leave a countdown
                    // already past its new end, showing zero for no reason.
                    TimerWidgetState.reset(this);
                    notifyDashboardChanged();
                }
            });
            special.addView(durationRow, spaced());
        }

        // The date counted to and what it is, with the widget like the timer's length.
        if (widget == DashboardWidgetLayout.Widget.COUNTDOWN) {
            HyperValueRow dateRow = (HyperValueRow) getLayoutInflater()
                    .inflate(R.layout.widget_value_row_single, column, false);
            dateRow.setTitle(getString(R.string.dashboard_countdown_date));
            long day = DashboardWidgetLayout.countdownDay(this);
            java.util.TimeZone zone = java.util.TimeZone.getDefault();
            dateRow.setValue(day < 0L ? getString(R.string.dashboard_builder_choose_value)
                    : java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                            .format(new java.util.Date(day * 86_400_000L
                                    - zone.getOffset(day * 86_400_000L))));
            dateRow.setBackgroundResource(R.drawable.hyper_segment_track);
            dateRow.setOnClickListener(view -> {
                java.util.Calendar shown = java.util.Calendar.getInstance();
                long current = DashboardWidgetLayout.countdownDay(this);
                if (current >= 0L) {
                    shown.setTimeInMillis(current * 86_400_000L
                            - zone.getOffset(current * 86_400_000L));
                }
                new android.app.DatePickerDialog(this, (picker, year, month, dayOfMonth) -> {
                    java.util.Calendar picked = java.util.Calendar.getInstance();
                    picked.set(year, month, dayOfMonth, 12, 0, 0);
                    DashboardWidgetLayout.setCountdownDay(this, WidgetMath.dayNumber(
                            picked.getTimeInMillis(), zone));
                    dateRow.setValue(java.text.DateFormat.getDateInstance(
                            java.text.DateFormat.MEDIUM).format(picked.getTime()));
                    notifyDashboardChanged();
                }, shown.get(java.util.Calendar.YEAR), shown.get(java.util.Calendar.MONTH),
                        shown.get(java.util.Calendar.DAY_OF_MONTH)).show();
            });
            special.addView(dateRow, spaced());
            HyperValueRow labelRow = (HyperValueRow) getLayoutInflater()
                    .inflate(R.layout.widget_value_row_single, column, false);
            labelRow.setTitle(getString(R.string.dashboard_countdown_label));
            String label = DashboardWidgetLayout.countdownLabel(this);
            labelRow.setValue(label.isEmpty() ? getString(R.string.dashboard_builder_choose_value)
                    : label);
            labelRow.setBackgroundResource(R.drawable.hyper_segment_track);
            labelRow.setOnClickListener(view -> {
                android.widget.EditText input = new android.widget.EditText(this);
                input.setSingleLine(true);
                input.setFilters(new android.text.InputFilter[]{
                        new android.text.InputFilter.LengthFilter(30)});
                input.setText(DashboardWidgetLayout.countdownLabel(this));
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dashboard_countdown_label)
                        .setView(input)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            String typed = input.getText().toString().trim();
                            DashboardWidgetLayout.setCountdownLabel(this, typed);
                            labelRow.setValue(typed.isEmpty()
                                    ? getString(R.string.dashboard_builder_choose_value) : typed);
                            notifyDashboardChanged();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
            special.addView(labelRow, spaced());
        }

        // The forecast's city, on every widget that reads the forecast: it
        // used to be asked only at the foot of the list of all widgets.
        if (widget == DashboardWidgetLayout.Widget.WEATHER
                || widget == DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER
                || widget == DashboardWidgetLayout.Widget.SUN
                || widget == DashboardWidgetLayout.Widget.UV_INDEX
                || widget == DashboardWidgetLayout.Widget.AIR_QUALITY) {
            HyperValueRow cityRow = (HyperValueRow) getLayoutInflater()
                    .inflate(R.layout.widget_value_row_single, column, false);
            cityRow.setTitle(getString(R.string.dashboard_weather_city));
            String city = MirrorSettings.loadDashboardSettings(this).weatherCity;
            cityRow.setValue(city.isEmpty()
                    ? getString(R.string.dashboard_weather_city_device) : city);
            cityRow.setBackgroundResource(R.drawable.hyper_segment_track);
            cityRow.setOnClickListener(view -> {
                android.widget.EditText input = new android.widget.EditText(this);
                input.setSingleLine(true);
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                input.setHint(R.string.dashboard_weather_city_device);
                input.setFilters(new android.text.InputFilter[]{
                        new android.text.InputFilter.LengthFilter(
                                DashboardSettings.MAX_CITY_LENGTH)});
                input.setText(MirrorSettings.loadDashboardSettings(this).weatherCity);
                input.setSelection(input.getText().length());
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dashboard_weather_city)
                        .setView(input)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            String typed = input.getText().toString().trim();
                            DashboardSettings current = MirrorSettings.loadDashboardSettings(this);
                            MirrorSettings.saveDashboardSettings(this,
                                    current.withTexts(typed, current.customText));
                            // Read back: the settings tidy the name they are given.
                            String kept = MirrorSettings.loadDashboardSettings(this).weatherCity;
                            cityRow.setValue(kept.isEmpty()
                                    ? getString(R.string.dashboard_weather_city_device) : kept);
                            notifyDashboardChanged();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
            special.addView(cityRow, spaced());
        }

        // Which stretch of time to follow.
        if (widget == DashboardWidgetLayout.Widget.PROGRESS) {
            special.addView(segmentedRow(R.string.dashboard_progress_period,
                    new String[]{
                            getString(R.string.dashboard_progress_day),
                            getString(R.string.dashboard_progress_week),
                            getString(R.string.dashboard_progress_month),
                            getString(R.string.dashboard_progress_year)},
                    DashboardWidgetLayout.progressPeriod(this).ordinal(),
                    getString(R.string.dashboard_progress_period),
                    choice -> {
                        DashboardWidgetLayout.setProgressPeriod(this,
                                WidgetMath.Period.values()[choice]);
                        notifyDashboardChanged();
                    }));
        }

        // Which city, for the same reason as the timer's length.
        if (widget == DashboardWidgetLayout.Widget.WORLD_CLOCK) {
            long now = System.currentTimeMillis();
            List<String> zones = WorldClock.sortedZones(now);
            android.icu.text.TimeZoneNames names =
                    android.icu.text.TimeZoneNames.getInstance(Locale.getDefault());
            String[] zoneLabels = new String[zones.size()];
            String chosen = DashboardWidgetLayout.worldClockZone(this);
            int current = 0;
            for (int index = 0; index < zones.size(); index++) {
                String id = zones.get(index);
                String city = names.getExemplarLocationName(id);
                if (city == null || city.isEmpty()) city = WorldClock.fallbackName(id);
                int offset = java.util.TimeZone.getTimeZone(id).getOffset(now) / 60_000;
                zoneLabels[index] = city + " · " + WorldClock.utcLabel(offset);
                if (id.equals(chosen)) current = index;
            }
            HyperValueRow zoneRow = (HyperValueRow) getLayoutInflater()
                    .inflate(R.layout.widget_value_row_single, column, false);
            zoneRow.setTitle(getString(R.string.dashboard_world_clock_zone));
            zoneRow.setEntries(zoneLabels);
            zoneRow.setValue(zoneLabels[current]);
            zoneRow.setBackgroundResource(R.drawable.hyper_segment_track);
            zoneRow.setOnItemSelectedListener(position -> {
                if (position >= 0 && position < zones.size()) {
                    DashboardWidgetLayout.setWorldClockZone(this, zones.get(position));
                    notifyDashboardChanged();
                }
            });
            special.addView(zoneRow, spaced());
        }

        // Rarely changed, so after the four that shape the widget.
        more.addView(segmentedRow(R.string.dashboard_builder_rotation_label,
                new String[]{"0°", "90°", "180°", "270°"},
                DashboardWidgetLayout.loadRotation(this, widget) / 90,
                getString(R.string.dashboard_builder_rotation, label(widget)),
                choice -> {
                    DashboardWidgetLayout.saveRotation(this, widget, choice * 90);
                    notifyDashboardChanged();
                }));

        // Only the widgets that can run out of data have anything to decide
        // here; the clock always has a value.
        if (DashboardWidgetLayout.canBeEmpty(widget)) {
            more.addView(segmentedRow(R.string.dashboard_builder_presence_label,
                    new String[]{
                            getString(R.string.dashboard_builder_presence_when_data),
                            getString(R.string.dashboard_builder_presence_always)},
                    DashboardWidgetLayout.loadPresence(this, widget).ordinal(),
                    getString(R.string.dashboard_builder_presence, label(widget)),
                    choice -> {
                        DashboardWidgetLayout.savePresence(this, widget,
                                DashboardWidgetLayout.Presence.values()[choice]);
                        notifyDashboardChanged();
                    }));
        }

        more.addView(segmentedRow(R.string.dashboard_builder_gap_label,
                new String[]{
                        getString(R.string.dashboard_builder_gap_none),
                        getString(R.string.dashboard_builder_gap_small),
                        getString(R.string.dashboard_builder_gap_large)},
                DashboardWidgetLayout.loadGap(this, widget).ordinal(),
                getString(R.string.dashboard_builder_gap, label(widget)),
                choice -> {
                    DashboardWidgetLayout.saveGap(this, widget,
                            DashboardWidgetLayout.Gap.values()[choice]);
                    notifyDashboardChanged();
                }));

        // The font list and the icon switch share one block, drawn in the
        // same track tone as the segmented rows above it: on the sheet's
        // surface a group background would not show.
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackgroundResource(R.drawable.hyper_segment_track);
        group.setClipToOutline(true);

        // A list rather than a segmented row: there are ten faces, and the
        // panel-wide setting is the first of them.
        List<PanelFonts.Choice> fonts = PanelFonts.choices(this);
        HyperValueRow fontRow = (HyperValueRow) getLayoutInflater()
                .inflate(R.layout.widget_value_row_top, group, false);
        fontRow.setTitle(getString(R.string.dashboard_font_widget_label));
        fontRow.setEntries(PanelFonts.labels(fonts));
        int chosenIndex = PanelFonts.indexOf(fonts,
                DashboardWidgetLayout.loadWidgetFontId(this, widget));
        fontRow.setValue(fonts.get(chosenIndex).label);
        fontRow.setBackground(null);
        fontRow.setOnItemSelectedListener(position -> {
            if (position >= 0 && position < fonts.size()) {
                DashboardWidgetLayout.saveWidgetFontId(this, widget, fonts.get(position).id);
                notifyDashboardChanged();
            }
        });
        group.addView(fontRow);

        MaterialSwitch iconSwitch = (MaterialSwitch) getLayoutInflater()
                .inflate(R.layout.widget_switch_row_bottom, group, false);
        iconSwitch.setText(R.string.dashboard_builder_hide_icon);
        iconSwitch.setContentDescription(getString(
                R.string.dashboard_builder_hide_icon_for, label(widget)));
        iconSwitch.setChecked(DashboardWidgetLayout.isIconHidden(this, widget));
        iconSwitch.setBackground(null);
        iconSwitch.setOnCheckedChangeListener((button, checked) -> {
            DashboardWidgetLayout.setIconHidden(this, widget, checked);
            notifyDashboardChanged();
        });
        group.addView(iconSwitch);
        more.addView(group, spaced());

        addSettingsGroup(column, R.string.dashboard_builder_group_place, place);
        addSettingsGroup(column, R.string.dashboard_builder_group_look, look);
        addSettingsGroup(column, R.string.dashboard_builder_group_widget, special);
        addSettingsGroup(column, R.string.dashboard_builder_group_more, more);

        MaterialButton reset = (MaterialButton) getLayoutInflater()
                .inflate(R.layout.widget_builder_reset_button, column, false);
        reset.setOnClickListener(view -> {
            DashboardWidgetLayout.resetAppearance(this, widget);
            notifyDashboardChanged();
            // The controls are rebuilt so every one shows the default again.
            LinearLayout target = editorRows;
            if (target != null) {
                target.removeAllViews();
                editorSync = null;
                fillWidgetSheet(target, widget);
                refreshItemSummary(widget);
            }
        });
        column.addView(reset, spaced(16));

        MaterialButton remove = (MaterialButton) getLayoutInflater()
                .inflate(R.layout.widget_builder_remove_button, column, false);
        remove.setOnClickListener(view -> {
            DashboardWidgetLayout.setWidgetEnabled(this, widget, false);
            closeWidgetEditor();
            selectWidget(null);
            reload();
        });
        column.addView(remove, spaced(4));
    }

    /** A rounded card the editor's controls of one kind sit in. */
    private LinearLayout settingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        // The widget's own card is the surface; these only group.
        card.setPadding(0, 0, 0, dp(4));
        return card;
    }

    /** Adds a card under its heading, unless nothing was put in it. */
    private void addSettingsGroup(LinearLayout column, int titleResource, LinearLayout card) {
        if (card.getChildCount() == 0) {
            return;
        }
        TextView title = new TextView(this);
        title.setTextAppearance(R.style.HyperOS_Text_SectionHeader);
        title.setText(titleResource);
        title.setPadding(dp(4), 0, dp(4), 0);
        column.addView(title, spaced(16));
        column.addView(card, spaced(6));
    }

    /**
     * A row of tiles that each show what they would give.
     *
     * <p>Words alone make you pick a variant to find out what it looks like;
     * a sample on the tile, in the colour it would be drawn in, does not.
     */
    private LinearLayout choiceTiles(int labelResource, String[] names,
            @Nullable String[] samples, @Nullable int[] sampleColours, int selectedIndex,
            String accessibilityName, java.util.function.IntConsumer onChosen) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(spaced());
        TextView label = (TextView) getLayoutInflater()
                .inflate(R.layout.widget_segment_label, column, false);
        label.setText(labelResource);
        column.addView(label);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setContentDescription(accessibilityName);
        LinearLayout[] all = new LinearLayout[names.length];
        for (int index = 0; index < names.length; index++) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(4), dp(10), dp(4), dp(8));
            tile.setMinimumHeight(dp(64));
            if (samples != null && samples[index] != null) {
                TextView sample = new TextView(this);
                sample.setText(samples[index]);
                sample.setGravity(Gravity.CENTER);
                sample.setTextSize(samples[index].length() > 6 ? 14f : 17f);
                sample.setTypeface(sample.getTypeface(), android.graphics.Typeface.BOLD);
                sample.setTextColor(sampleColours != null ? sampleColours[index]
                        : ContextCompat.getColor(this, R.color.hyper_text_primary));
                tile.addView(sample, new LinearLayout.LayoutParams(-2, -2));
            }
            TextView name = new TextView(this);
            name.setText(names[index]);
            name.setGravity(Gravity.CENTER);
            name.setTextSize(11.5f);
            name.setMaxLines(2);
            name.setTextColor(ContextCompat.getColor(this, R.color.hyper_text_secondary));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
            nameParams.topMargin = samples != null && samples[index] != null ? dp(4) : 0;
            tile.addView(name, nameParams);
            tile.setContentDescription(names[index]);
            tile.setClickable(true);
            tile.setFocusable(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            if (index > 0) {
                params.setMarginStart(dp(8));
            }
            all[index] = tile;
            final int chosen = index;
            tile.setOnClickListener(view -> {
                for (int other = 0; other < all.length; other++) {
                    paintTile(all[other], other == chosen);
                }
                onChosen.accept(chosen);
            });
            tiles.addView(tile, params);
        }
        for (int index = 0; index < all.length; index++) {
            paintTile(all[index], index == selectedIndex);
        }
        column.addView(tiles);
        return column;
    }

    private void paintTile(View tile, boolean selected) {
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(dp(14));
        shape.setColor(ContextCompat.getColor(this,
                selected ? R.color.hyper_accent_container : R.color.hyper_field));
        shape.setStroke(dp(selected ? 2 : 1), ContextCompat.getColor(this,
                selected ? R.color.hyper_accent : R.color.hyper_field));
        tile.setBackground(shape);
        tile.setSelected(selected);
    }

    /** What each variant of a widget looks like, where there is something to show. */
    @Nullable
    private String[] variantSamples(DashboardWidgetLayout.Widget widget) {
        java.util.Date now = new java.util.Date();
        if (widget == DashboardWidgetLayout.Widget.CLOCK) {
            return new String[]{
                    new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                    new java.text.SimpleDateFormat("HH\nmm", Locale.getDefault()).format(now),
                    new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)};
        }
        if (widget == DashboardWidgetLayout.Widget.DATE) {
            return new String[]{
                    new java.text.SimpleDateFormat("d MMM", Locale.getDefault()).format(now),
                    new java.text.SimpleDateFormat("dd.MM", Locale.getDefault()).format(now),
                    new java.text.SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(now)};
        }
        return null;
    }

    /** The free page's size control: a label with the percentage, and a slider. */
    private LinearLayout sizeSlider(DashboardWidgetLayout.Widget widget) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(spaced());
        TextView label = (TextView) getLayoutInflater()
                .inflate(R.layout.widget_segment_label, column, false);
        column.addView(label);
        View track = getLayoutInflater().inflate(R.layout.widget_size_slider, column, false);
        HyperSlider slider = track.findViewById(R.id.widget_size_slider);
        int percent = Math.round(DashboardWidgetLayout.scale(this, widget) * 100f);
        label.setText(getString(R.string.dashboard_builder_size_percent, percent));
        slider.setValue(percent);
        slider.setContentDescription(getString(R.string.dashboard_builder_size, label(widget)));
        editorSync = () -> slider.setValue(
                Math.round(DashboardWidgetLayout.scale(this, widget) * 100f));
        slider.addOnChangeListener((control, value, fromUser) -> {
            label.setText(getString(R.string.dashboard_builder_size_percent, Math.round(value)));
            if (fromUser) {
                DashboardWidgetLayout.saveScale(this, widget, value / 100f);
                notifyDashboardChanged();
            }
        });
        column.addView(track);
        return column;
    }

    private LinearLayout.LayoutParams spaced() {
        return spaced(10);
    }

    private LinearLayout.LayoutParams spaced(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topDp);
        return params;
    }

    /** What each variant is called on its tile, where the tile already shows a sample of it. */
    private String[] variantCaptions(DashboardWidgetLayout.Widget widget) {
        if (widget == DashboardWidgetLayout.Widget.CLOCK) {
            return new String[]{
                    getString(R.string.dashboard_variant_clock_inline_caption),
                    getString(R.string.dashboard_variant_clock_stacked_caption),
                    getString(R.string.dashboard_variant_clock_seconds_caption)};
        }
        if (widget == DashboardWidgetLayout.Widget.DATE) {
            return new String[]{
                    getString(R.string.dashboard_variant_date_text_caption),
                    getString(R.string.dashboard_variant_date_numeric_caption),
                    getString(R.string.dashboard_variant_date_year_caption)};
        }
        return variantLabels(widget);
    }

    private String[] variantLabels(DashboardWidgetLayout.Widget widget) {
        if (widget == DashboardWidgetLayout.Widget.CLOCK) {
            return new String[]{
                    getString(R.string.dashboard_variant_clock_inline),
                    getString(R.string.dashboard_variant_clock_stacked),
                    getString(R.string.dashboard_variant_clock_seconds)};
        }
        if (widget == DashboardWidgetLayout.Widget.DATE) {
            return new String[]{
                    getString(R.string.dashboard_variant_date_text),
                    getString(R.string.dashboard_variant_date_numeric),
                    getString(R.string.dashboard_variant_date_year)};
        }
        return new String[]{
                getString(R.string.dashboard_variant_default),
                getString(R.string.dashboard_variant_alternate),
                getString(R.string.dashboard_variant_detailed)};
    }

    /**
     * A labelled segmented control.
     *
     * <p>Replaces the cycle buttons this screen used, where one button
     * relabelled itself on each press: with three values you could not see the
     * options, jump to one, or go back.
     */
    private LinearLayout segmentedRow(int labelResource, String[] options, int selectedIndex,
            String accessibilityName, java.util.function.IntConsumer onChosen) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(spaced());

        TextView label = (TextView) getLayoutInflater()
                .inflate(R.layout.widget_segment_label, column, false);
        label.setText(labelResource);
        column.addView(label);

        MaterialButtonToggleGroup group = (MaterialButtonToggleGroup) getLayoutInflater()
                .inflate(R.layout.widget_segment_group, column, false);
        group.setContentDescription(accessibilityName);
        int[] ids = new int[options.length];
        for (int index = 0; index < options.length; index++) {
            MaterialButton segment = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.widget_segment_button, group, false);
            segment.setId(View.generateViewId());
            segment.setText(options[index]);
            ids[index] = segment.getId();
            group.addView(segment, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        if (selectedIndex >= 0 && selectedIndex < ids.length) {
            group.check(ids[selectedIndex]);
        }
        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            for (int index = 0; index < ids.length; index++) {
                if (ids[index] == checkedId) {
                    onChosen.accept(index);
                    return;
                }
            }
        });
        column.addView(group);
        return column;
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    /**
     * What used to be five footnotes spread down the screen, read once and
     * then only in the way.
     */
    private void showHelp() {
        String message = getString(R.string.dashboard_builder_pages_help)
                + "\n\n" + getString(R.string.dashboard_page_home_help)
                + "\n\n" + getString(R.string.dashboard_page_cycle_help)
                + "\n\n" + getString(R.string.panel_gestures_help)
                + "\n\n" + getString(R.string.dashboard_builder_preview_note);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dashboard_builder_help_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dashboard_builder_reset_title)
                .setMessage(R.string.dashboard_builder_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dashboard_builder_reset_confirm, (dialog, which) -> {
                    DashboardWidgetLayout.reset(this);
                    DashboardSettings current = MirrorSettings.loadDashboardSettings(this);
                    DashboardSettings defaults = DashboardSettings.defaults();
                    current = current.withLayout(defaults.layout);
                    for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.Widget.values())
                        current = current.withWidget(widget, defaults.isWidgetVisible(widget));
                    MirrorSettings.saveDashboardSettings(this, current);
                    editedPage = 1;
                    selectedWidget = null;
                    reload();
                }).show();
    }

    // ------------------------------------------------------------------
    // Preview
    // ------------------------------------------------------------------

    private void notifyDashboardChanged() {
        MirrorSettings.saveDashboardSettings(this, MirrorSettings.loadDashboardSettings(this));
        refreshPreview();
        if (editingWidget != null) {
            refreshItemSummary(editingWidget);
        }
    }

    private void refreshPreview() {
        if (preview == null) return;
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this)
                .withContentMode(RearContentMode.DASHBOARD);
        Point panel = rearPanelSize();
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        preview.setDashboardSettings(settings, RearContentMode.DASHBOARD);
        preview.setPanelMetrics(panel == null ? 0 : Math.min(panel.x, panel.y),
                panel == null ? 0f : rearPanelDensity());
        preview.setSnapshot(previewSnapshot());
        // Pinned rather than left to cycle: editing page two should not mean
        // waiting eight seconds for it to come round again.
        preview.setSelectedPage(pageCount > 1 ? editedPage : 0);
        preview.setContentDescription(pageLabel(editedPage));

        DashboardWidgetLayout.Orientation orientation =
                DashboardWidgetLayout.loadPageOrientation(this, editedPage);
        DashboardSettings.Layout layout =
                DashboardWidgetLayout.loadPageLayout(this, editedPage, settings.layout);
        layoutRow.setValue(getResources().getStringArray(
                R.array.dashboard_layout_entries)[layout.ordinal()]);
        orientationRow.setValue(getResources().getStringArray(
                R.array.dashboard_orientation_entries)[orientation.ordinal()]);
        String name = DashboardWidgetLayout.loadPageName(this, editedPage);
        pageNameRow.setValue(name.isEmpty() ? getString(R.string.dashboard_page_name_none) : name);
        // Cycling is a question only once there is more than one page, and
        // the orientation row closes the group when the switch is not there.
        boolean several = pageCount > 1;
        pageInCycleSwitch.setVisibility(several ? View.VISIBLE : View.GONE);
        orientationRow.setBackgroundResource(several
                ? R.drawable.hyper_row_bg_middle : R.drawable.hyper_row_bg_bottom);
        autoPagesGroup.setVisibility(several ? View.VISIBLE : View.GONE);
        autoPagesHelp.setVisibility(several ? View.VISIBLE : View.GONE);
        boolean cycling = DashboardWidgetLayout.isAutoPageSwitchEnabled(this);
        bindingCycle = true;
        pageInCycleSwitch.setChecked(DashboardWidgetLayout.isPageInCycle(this, editedPage));
        autoPagesSwitch.setChecked(cycling);
        bindingCycle = false;
        // Leaving a page out of a cycle that is not running means nothing.
        pageInCycleSwitch.setEnabled(cycling);
        boolean free = layout == DashboardSettings.Layout.FREE;
        previewHint.setText(free
                ? R.string.dashboard_builder_preview_free_hint
                : R.string.dashboard_builder_preview_pick_hint);
        snapGroup.setVisibility(free ? View.VISIBLE : View.GONE);
        // Say when the panel has run out of room, rather than leaving the
        // preview to cycle through widgets with no explanation of why.
        preview.post(() -> {
            int overflow = preview.overflowCount();
            overflowNote.setVisibility(overflow > 0 ? View.VISIBLE : View.GONE);
            if (overflow > 0) {
                overflowNote.setText(getResources().getQuantityString(
                        R.plurals.dashboard_builder_overflow, overflow, overflow));
            }
        });
        bindingSnap = true;
        snapSwitch.setChecked(DashboardWidgetLayout.isGridSnapEnabled(this));
        bindingSnap = false;
        sizePreviewToPanel(orientation);
    }

    /** Redraws the preview when the session starts, stops or changes track. */
    private final MediaWidgetState.Listener mediaListener = snapshot -> runOnUiThread(() -> {
        if (!isFinishing() && !isDestroyed()) {
            refreshPreview();
        }
    });

    /**
     * Wires the preview back into the list: a tap there opens the widget's
     * settings, in the free layout a drag moves it, and a swipe turns to the
     * next page the way it does on the panel.
     */
    private void bindPreview() {
        preview.setOnWidgetSelectedListener(new RearDashboardView.OnWidgetSelectedListener() {
            @Override public void onWidgetSelected(@Nullable DashboardWidgetLayout.Widget widget) {
                if (widget != null) {
                    openWidgetEditor(widget);
                } else if (editingWidget == null) {
                    selectWidget(null);
                }
            }

            @Override public void onWidgetGrabbed(DashboardWidgetLayout.Widget widget) {
                selectWidget(widget);
            }

            @Override public void onWidgetChanged(DashboardWidgetLayout.Widget widget) {
                // Not renderRows(): this arrives when a widget has been moved
                // or resized, and no row shows either.
                refreshPreview();
                selectWidget(widget);
                refreshItemSummary(widget);
                if (editorSync != null) {
                    editorSync.run();
                }
            }
        });
        preview.setOnPageChangedListener(page -> {
            if (!isFinishing() && page != editedPage
                    && page <= DashboardWidgetLayout.loadPageCount(this)) {
                selectPage(page);
            }
        });
    }

    /** Live readings where the app has them, stand-ins elsewhere. */
    private RearDashboardSnapshot previewSnapshot() {
        return PreviewSupport.snapshot(this);
    }

    /**
     * Gives the preview the rear panel's proportions.
     *
     * <p>The shape comes from the panel itself, and falls back to fixed boxes
     * only when the rear display cannot be read.
     */
    private void sizePreviewToPanel(DashboardWidgetLayout.Orientation orientation) {
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(previewContainer.getLayoutParams());
        params.gravity = Gravity.CENTER_HORIZONTAL;
        Point panel = rearPanelSize();
        if (panel == null) {
            if (orientation == DashboardWidgetLayout.Orientation.PORTRAIT) {
                params.width = dp(120);
                params.height = dp(220);
            } else {
                params.width = -1;
                params.height = dp(150);
            }
            previewContainer.setLayoutParams(params);
            return;
        }
        boolean landscape = orientation != DashboardWidgetLayout.Orientation.PORTRAIT;
        // The panel reports its size in its own frame, which is the wide one,
        // so a landscape page takes the long side across whichever way round
        // the two numbers arrive.
        float longSide = Math.max(panel.x, panel.y);
        float shortSide = Math.min(panel.x, panel.y);
        float aspect = landscape ? longSide / shortSide : shortSide / longSide;
        int availableWidth = Math.max(dp(120), previewMaxWidth());
        // The preview is pinned above everything else, so an upright page is
        // kept short enough to leave the sections room below it.
        int maxHeight = Math.min(dp(250),
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.3f));
        int width = availableWidth;
        int height = Math.round(width / aspect);
        if (height > maxHeight) {
            height = maxHeight;
            width = Math.round(height * aspect);
        }
        params.width = width;
        params.height = height;
        params.gravity = Gravity.CENTER_HORIZONTAL;
        previewContainer.setLayoutParams(params);
    }

    private int previewMaxWidth() {
        int screen = getResources().getDisplayMetrics().widthPixels;
        int margins = 2 * getResources().getDimensionPixelSize(R.dimen.page_margin_horizontal);
        return screen - margins;
    }

    /** Density of the rear panel, or zero when it cannot be read. */
    private float rearPanelDensity() {
        return PreviewSupport.panelDensity(this);
    }

    /** Physical size of the rear panel, or null when it cannot be read. */
    private Point rearPanelSize() {
        return PreviewSupport.panelSize(this);
    }

    private String label(DashboardWidgetLayout.Widget widget) {
        return WidgetCatalog.label(this, widget);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
