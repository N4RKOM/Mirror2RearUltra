package com.tpkarras.mirror2rearultra;

import android.content.ClipData;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.List;

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
    private final List<DashboardWidgetLayout.Widget> widgets = new ArrayList<>();
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
    @Nullable private BottomSheetDialog widgetSheet;
    /**
     * The page being edited: the tab that is checked, the page the preview
     * shows, and the page the layout rows and the widget list belong to.
     */
    private int editedPage = 1;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_dashboard_builder);
        MaterialToolbar toolbar = findViewById(R.id.dashboard_builder_toolbar);
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
        pageTabs = findViewById(R.id.dashboard_builder_page_tabs);
        addPageButton = findViewById(R.id.dashboard_builder_page_add);
        pageActionsButton = findViewById(R.id.dashboard_builder_page_actions);
        preview = findViewById(R.id.dashboard_builder_preview_view);
        previewContainer = findViewById(R.id.dashboard_builder_preview_container);
        layoutRow = findViewById(R.id.dashboard_builder_layout);
        orientationRow = findViewById(R.id.dashboard_builder_orientation);
        pageNameRow = findViewById(R.id.dashboard_builder_page_name);
        pageInCycleSwitch = findViewById(R.id.dashboard_builder_page_in_cycle);
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
        findViewById(R.id.dashboard_builder_templates).setOnClickListener(view ->
                startActivity(new Intent(this, DashboardTemplatesActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The picker and the templates screen both change the arrangement
        // while this one is stopped.
        reload();
    }

    @Override
    protected void onDestroy() {
        MediaWidgetState.removeListener(mediaListener);
        if (widgetSheet != null) {
            widgetSheet.dismiss();
        }
        super.onDestroy();
    }

    /** Rebuilds every part of the screen from stored state. */
    private void reload() {
        widgets.clear();
        widgets.addAll(DashboardWidgetLayout.loadOrder(this));
        editedPage = Math.max(1, Math.min(DashboardWidgetLayout.loadPageCount(this), editedPage));
        renderPageTabs();
        renderRows();
        refreshPreview();
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
        if (page == editedPage) {
            return;
        }
        editedPage = page;
        selectedWidget = null;
        preview.setSelectedWidget(null);
        renderPageTabs();
        renderRows();
        refreshPreview();
    }

    private void addPage() {
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
        listHint.setText(total == 0 ? R.string.dashboard_builder_empty
                : page.isEmpty() ? R.string.dashboard_builder_page_empty_hint
                : R.string.dashboard_builder_list_hint);
        for (int index = 0; index < page.size(); index++) {
            DashboardWidgetLayout.Widget widget = page.get(index);
            MaterialButton row = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.widget_builder_row, rows, false);
            row.setTag(widget);
            row.setText(label(widget));
            row.setShapeAppearanceModel(ShapeAppearanceModel.builder(this, 0,
                    page.size() == 1 ? R.style.HyperOS_Shape_Group
                            : index == 0 ? R.style.HyperOS_Shape_RowTop
                            : index == page.size() - 1 ? R.style.HyperOS_Shape_RowBottom
                            : R.style.HyperOS_Shape_RowMiddle).build());
            markSelected(row, widget == selectedWidget);
            row.setOnClickListener(view -> {
                selectWidget(widget);
                openWidgetSheet(widget);
            });
            row.setOnLongClickListener(view -> {
                ClipData data = ClipData.newPlainText("widget", widget.name());
                return view.startDragAndDrop(data, new View.DragShadowBuilder(view), widget, 0);
            });
            addMoveActions(row, widget, index, page.size(), pages.size());
            rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void addMoveActions(View row, DashboardWidgetLayout.Widget widget,
            int index, int pageSize, int pageCount) {
        if (index > 0) {
            ViewCompat.addAccessibilityAction(row,
                    getString(R.string.dashboard_builder_move_up, label(widget)),
                    (view, arguments) -> {
                        moveWidget(widget, editedPage, index - 1);
                        return true;
                    });
        }
        if (index < pageSize - 1) {
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
            if (child instanceof MaterialButton) {
                markSelected((MaterialButton) child, widget != null && child.getTag() == widget);
            }
        }
    }

    private void markSelected(MaterialButton row, boolean picked) {
        row.setTextColor(ContextCompat.getColorStateList(this,
                picked ? R.color.hyper_text_button_text : R.color.hyper_row_title_text));
    }

    /** Reorders within the edited page, by where the finger lets go. */
    private boolean handleRowDrop(View view, DragEvent event) {
        if (event.getAction() != DragEvent.ACTION_DROP) {
            return true;
        }
        Object state = event.getLocalState();
        if (!(state instanceof DashboardWidgetLayout.Widget)) {
            return false;
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
    // Widget sheet
    // ------------------------------------------------------------------

    /**
     * Opens a widget's settings over the lower half of the screen.
     *
     * <p>They were nine rows inside every card, so a page of five widgets was
     * several screens of controls to scroll past. The builder scrolls the
     * preview into view first, so each change can be watched as it lands.
     */
    private void openWidgetSheet(DashboardWidgetLayout.Widget widget) {
        if (widgetSheet != null) {
            widgetSheet.dismiss();
        }
        scroll.smoothScrollTo(0, Math.max(0, previewContainer.getTop() - dp(56)));
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.HyperOS_BottomSheetDialog);
        NestedScrollView content = new NestedScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int margin = getResources().getDimensionPixelSize(R.dimen.page_margin_horizontal);
        column.setPadding(margin, 0, margin, dp(24));
        column.addView(new BottomSheetDragHandleView(this), new LinearLayout.LayoutParams(-1, -2));
        content.addView(column, new ViewGroup.LayoutParams(-1, -2));
        fillWidgetSheet(column, widget, sheet);
        sheet.setContentView(content);
        if (sheet.getWindow() != null) {
            // Light enough that the preview reads through it.
            sheet.getWindow().setDimAmount(0.2f);
        }
        BottomSheetBehavior<?> behavior = sheet.getBehavior();
        behavior.setPeekHeight(Math.round(getResources().getDisplayMetrics().heightPixels * 0.5f));
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        sheet.setOnDismissListener(dialog -> {
            if (widgetSheet == sheet) {
                widgetSheet = null;
            }
        });
        widgetSheet = sheet;
        sheet.show();
    }

    private void fillWidgetSheet(LinearLayout column, DashboardWidgetLayout.Widget widget,
            BottomSheetDialog sheet) {
        TextView title = new TextView(this);
        title.setText(label(widget));
        title.setTextAppearance(R.style.HyperOS_Text_Title);
        title.setPadding(0, 0, 0, dp(4));
        column.addView(title);

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
                            selectPage(target);
                            selectWidget(widget);
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
            column.addView(pageRow);
        }

        boolean freeLayout = DashboardWidgetLayout.loadPageLayout(this, editedPage,
                MirrorSettings.loadDashboardSettings(this).layout) == DashboardSettings.Layout.FREE;
        // The free layout places a widget by hand in the preview, so the
        // flowed layouts' alignment is left out there.
        if (!freeLayout) {
            column.addView(segmentedRow(R.string.dashboard_builder_position_label,
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

        // Size on every page, the free one included. A pinch sets it there
        // too, but a pinch cannot say "back to normal", and a widget left
        // large from some earlier arrangement had no other way down.
        column.addView(segmentedRow(R.string.dashboard_builder_size_label,
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

        if (DashboardWidgetLayout.supportsVariant(widget)) {
            column.addView(segmentedRow(R.string.dashboard_builder_variant_label,
                    variantLabels(widget),
                    DashboardWidgetLayout.loadVariant(this, widget).ordinal(),
                    getString(R.string.dashboard_builder_variant, label(widget)),
                    choice -> {
                        DashboardWidgetLayout.saveVariant(this, widget,
                                DashboardWidgetLayout.Variant.values()[choice]);
                        notifyDashboardChanged();
                    }));
        }

        column.addView(segmentedRow(R.string.dashboard_builder_style_label,
                new String[]{
                        getString(R.string.dashboard_builder_style_default),
                        getString(R.string.dashboard_builder_style_accent),
                        getString(R.string.dashboard_builder_style_muted)},
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
            column.addView(durationRow, spaced());
        }

        // Rarely changed, so after the four that shape the widget.
        column.addView(segmentedRow(R.string.dashboard_builder_rotation_label,
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
            column.addView(segmentedRow(R.string.dashboard_builder_presence_label,
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

        column.addView(segmentedRow(R.string.dashboard_builder_gap_label,
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
        column.addView(group, spaced(16));

        MaterialButton remove = (MaterialButton) getLayoutInflater()
                .inflate(R.layout.widget_builder_remove_button, column, false);
        remove.setOnClickListener(view -> {
            DashboardWidgetLayout.setWidgetEnabled(this, widget, false);
            sheet.dismiss();
            selectWidget(null);
            reload();
        });
        column.addView(remove, spaced(12));
    }

    private LinearLayout.LayoutParams spaced() {
        return spaced(10);
    }

    private LinearLayout.LayoutParams spaced(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topDp);
        return params;
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
        bindingCycle = true;
        pageInCycleSwitch.setChecked(DashboardWidgetLayout.isPageInCycle(this, editedPage));
        bindingCycle = false;
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
                selectWidget(widget);
                if (widget != null) {
                    openWidgetSheet(widget);
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
            }
        });
        preview.setOnPageChangedListener(page -> {
            if (!isFinishing() && page != editedPage
                    && page <= DashboardWidgetLayout.loadPageCount(this)) {
                selectPage(page);
            }
        });
    }

    /**
     * Fills the preview from live state where the app already has it.
     *
     * <p>Battery, temperature, charging, the current track, the clock, the
     * profile name, the network, memory and storage are real. Weather,
     * heading, speed, altitude, the next event and today's steps stand in:
     * those come from sensors, a network fetch and a content query the builder
     * does not start.
     *
     * <p>The system counters are stood in for rather than left at "no
     * reading": a widget with no reading is dropped, so a page made of them
     * would look empty here while the panel showed it.
     */
    private RearDashboardSnapshot previewSnapshot() {
        MediaWidgetState.Snapshot media = MediaWidgetState.get();
        boolean playing = media != null && media.hasMedia();
        // Read the battery straight from the sticky broadcast rather than from
        // DeviceHealthState: that is filled by DeviceHealthMonitor, which only
        // runs while the protection screen or the mirroring service is up, so
        // in the builder it is almost always unknown.
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPercent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : 72;
        int temperature = battery == null
                ? 365 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 365);
        int status = battery == null
                ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        SystemStats stats = SystemStats.read(this);
        NotificationWidgetState.Snapshot notification = NotificationWidgetState.get();
        return new RearDashboardSnapshot(
                System.currentTimeMillis(),
                batteryPercent,
                temperature,
                charging,
                "",
                18,
                1,
                false,
                System.currentTimeMillis() + 3_600_000L,
                playing ? media.title : getString(R.string.dashboard_builder_sample_track),
                playing ? media.artist : "",
                media != null && media.playing,
                45f,
                8.3f,
                120.0,
                754_000L,
                MirrorSettings.profileDisplayName(this, MirrorSettings.loadActiveProfile(this)),
                stats.networkSummary,
                stats.memoryPercent,
                stats.storagePercentFree,
                getString(R.string.dashboard_builder_sample_event),
                System.currentTimeMillis() + 5_400_000L,
                4_820,
                notification.hasContent() ? notification.app
                        : getString(R.string.dashboard_builder_sample_notification_app),
                notification.hasContent() ? notification.title
                        : getString(R.string.dashboard_builder_sample_notification),
                notification.hasContent() ? notification.text : "");
    }

    /**
     * Gives the preview the rear panel's proportions.
     *
     * <p>The shape comes from the panel itself, and falls back to fixed boxes
     * only when the rear display cannot be read.
     */
    private void sizePreviewToPanel(DashboardWidgetLayout.Orientation orientation) {
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) previewContainer.getLayoutParams();
        Point panel = rearPanelSize();
        if (panel == null) {
            if (orientation == DashboardWidgetLayout.Orientation.PORTRAIT) {
                params.width = dp(180);
                params.height = dp(300);
                params.gravity = Gravity.CENTER_HORIZONTAL;
            } else {
                params.width = -1;
                params.height = dp(150);
                params.gravity = Gravity.NO_GRAVITY;
            }
            previewContainer.setLayoutParams(params);
            return;
        }
        boolean landscape = orientation == DashboardWidgetLayout.Orientation.LANDSCAPE;
        float aspect = landscape
                ? panel.y / (float) panel.x
                : panel.x / (float) panel.y;
        int availableWidth = Math.max(dp(120), previewMaxWidth());
        int maxHeight = dp(320);
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

    /**
     * Density of the rear panel, or zero when it cannot be read.
     *
     * <p>Its own, not this screen's: the preview sizes its text against the
     * panel's dp, which is a different size from the phone's.
     */
    private float rearPanelDensity() {
        DisplayManager manager = getSystemService(DisplayManager.class);
        int rearDisplayId = DisplayActivity.findRearDisplayId(manager);
        if (manager == null || rearDisplayId == Display.INVALID_DISPLAY) {
            return 0f;
        }
        Display display = manager.getDisplay(rearDisplayId);
        if (display == null) {
            return 0f;
        }
        // The display's own metrics rather than a context's: a display context
        // reports the density this screen was configured with, which is the
        // phone's, and sizing the preview against that made it a drawing of
        // the wrong panel.
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        display.getRealMetrics(metrics);
        return metrics.density;
    }

    /** Physical size of the rear panel, or null when it cannot be read. */
    private Point rearPanelSize() {
        DisplayManager manager = getSystemService(DisplayManager.class);
        int rearDisplayId = DisplayActivity.findRearDisplayId(manager);
        if (manager == null || rearDisplayId == Display.INVALID_DISPLAY) {
            return null;
        }
        Display display = manager.getDisplay(rearDisplayId);
        if (display == null || display.getMode() == null) {
            return null;
        }
        int width = display.getMode().getPhysicalWidth();
        int height = display.getMode().getPhysicalHeight();
        return width > 0 && height > 0 ? new Point(width, height) : null;
    }

    private String label(DashboardWidgetLayout.Widget widget) {
        int[] labels = {R.string.dashboard_widget_clock, R.string.dashboard_widget_date,
                R.string.dashboard_widget_battery, R.string.dashboard_widget_temperature,
                R.string.dashboard_widget_weather, R.string.dashboard_widget_next_alarm,
                R.string.dashboard_widget_media, R.string.dashboard_widget_compass,
                R.string.dashboard_widget_speed, R.string.dashboard_widget_altitude,
                R.string.dashboard_widget_session_timer, R.string.dashboard_widget_active_profile,
                R.string.dashboard_widget_custom_text, R.string.dashboard_widget_network,
                R.string.dashboard_widget_memory, R.string.dashboard_widget_storage,
                R.string.dashboard_widget_notifications, R.string.dashboard_widget_calendar,
                R.string.dashboard_widget_steps, R.string.dashboard_widget_fullscreen_weather,
                R.string.dashboard_widget_fullscreen_media,
                R.string.dashboard_widget_last_notification,
                R.string.dashboard_widget_timer};
        // Indexed by ordinal, so a widget added without a label here would
        // take the whole screen down rather than show a blank row.
        int index = widget.ordinal();
        return getString(index < labels.length ? labels[index] : R.string.dashboard_widgets_title);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
