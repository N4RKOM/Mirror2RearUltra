package com.tpkarras.mirror2rearultra;

import android.Manifest;
import android.app.ActivityOptions;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.content.ClipData;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.view.Gravity;
import android.view.Display;
import android.view.DragEvent;
import android.view.View;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

public class DashboardBuilderActivity extends AppCompatActivity {
    private enum Preset { CLOCK, TRIP, MUSIC, WEATHER }
    private final List<DashboardWidgetLayout.Widget> widgets = new ArrayList<>();
    private LinearLayout rows;
    private RearDashboardView preview;
    private MaterialCardView previewContainer;
    private MaterialButton orientationButton;
    private MaterialButton layoutButton;
    private MaterialButtonToggleGroup pagesSegment;
    private MaterialButtonToggleGroup previewPagesSegment;
    private TextView previewPageNote;
    private LinearLayout namedTemplates;
    private TextView namedTemplatesEmpty;
    private MaterialButton addNamedTemplateButton;
    private TextView emptyState;
    private MaterialButton showOnPanelButton;
    private ActivityResultLauncher<String> permissionLauncher;
    /**
     * The page the preview shows and the two buttons above it change.
     *
     * <p>Orientation and arrangement used to be one setting for the whole
     * panel, so a trip page could not sit sideways next to an upright clock
     * page. They belong to a page now, which means the screen has to say which
     * page is being edited.
     */
    private int previewPage = 1;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> refreshPreview());
        setContentView(R.layout.activity_dashboard_builder);
        MaterialToolbar toolbar = findViewById(R.id.dashboard_builder_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        rows = findViewById(R.id.dashboard_builder_rows);
        preview = findViewById(R.id.dashboard_builder_preview_view);
        previewContainer = findViewById(R.id.dashboard_builder_preview_container);
        orientationButton = findViewById(R.id.dashboard_builder_orientation);
        layoutButton = findViewById(R.id.dashboard_builder_layout);
        pagesSegment = findViewById(R.id.dashboard_builder_pages_segment);
        previewPagesSegment = findViewById(R.id.dashboard_builder_preview_pages);
        previewPageNote = findViewById(R.id.dashboard_builder_preview_page_note);
        namedTemplates = findViewById(R.id.dashboard_named_templates);
        namedTemplatesEmpty = findViewById(R.id.dashboard_named_templates_empty);
        addNamedTemplateButton = findViewById(R.id.dashboard_named_template_add);
        emptyState = findViewById(R.id.dashboard_builder_empty);
        orientationButton.setOnClickListener(view -> cycleOrientation());
        layoutButton.setOnClickListener(view -> cycleLayout());
        widgets.addAll(DashboardWidgetLayout.loadOrder(this));
        rows.setOnDragListener(this::handleDrop);
        refreshPageControls();
        addNamedTemplateButton.setOnClickListener(view -> promptForNewTemplate());
        renderNamedTemplates();
        refreshPreview();
        renderRows();
        findViewById(R.id.dashboard_builder_choose).setOnClickListener(view ->
                startActivity(new android.content.Intent(this, WidgetPickerActivity.class)));
        showOnPanelButton = findViewById(R.id.dashboard_builder_show_on_panel);
        showOnPanelButton.setOnClickListener(view -> showOnRearPanel());
        findViewById(R.id.dashboard_builder_done).setOnClickListener(view -> finish());
        findViewById(R.id.dashboard_builder_reset).setOnClickListener(view -> confirmReset());
        findViewById(R.id.dashboard_preset_clock).setOnClickListener(view -> applyPreset(Preset.CLOCK));
        findViewById(R.id.dashboard_preset_trip).setOnClickListener(view -> applyPreset(Preset.TRIP));
        findViewById(R.id.dashboard_preset_music).setOnClickListener(view -> applyPreset(Preset.MUSIC));
        findViewById(R.id.dashboard_preset_weather).setOnClickListener(view -> applyPreset(Preset.WEATHER));
        findViewById(R.id.dashboard_custom_template_save).setOnClickListener(view -> {
            DashboardTemplateStore.save(this);
            Toast.makeText(this, R.string.dashboard_custom_template_saved, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.dashboard_custom_template_apply).setOnClickListener(view -> {
            if (!DashboardTemplateStore.apply(this)) {
                Toast.makeText(this, R.string.dashboard_custom_template_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            widgets.clear(); widgets.addAll(DashboardWidgetLayout.loadOrder(this));
            refreshPageControls();
            renderRows(); notifyDashboardChanged();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The picker may have switched widgets on or off while this screen was
        // stopped, and this list only shows the ones that are on.
        widgets.clear();
        widgets.addAll(DashboardWidgetLayout.loadOrder(this));
        renderRows();
        refreshPreview();
        updateShowOnPanelState();
    }

    /**
     * Puts the current arrangement on the rear panel for half a minute.
     *
     * <p>Uses the same launch-on-the-rear-display route as the calibration
     * grid. Refused while mirroring owns the panel, and when the rear display
     * is not reachable at all.
     */
    private void showOnRearPanel() {
        DisplayManager manager = getSystemService(DisplayManager.class);
        int rearDisplayId = DisplayActivity.findRearDisplayId(manager);
        if (rearDisplayId == Display.INVALID_DISPLAY) {
            Toast.makeText(this, R.string.rear_display_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(rearDisplayId);
        startActivity(new android.content.Intent(this, DashboardPreviewActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
                options.toBundle());
    }

    /** The panel cannot show a preview while a mirroring session owns it. */
    private void updateShowOnPanelState() {
        if (showOnPanelButton == null) {
            return;
        }
        boolean mirroring = MirrorState.isActive();
        showOnPanelButton.setEnabled(!mirroring);
        showOnPanelButton.setText(mirroring
                ? R.string.dashboard_builder_show_on_panel_active
                : R.string.dashboard_builder_show_on_panel);
    }

    /**
     * Moves a widget one place up or down the visible list.
     *
     * <p>The stored order holds every widget, on or off, but the list shows
     * only the ones that are on. Swapping with the neighbour in the stored
     * order would look like nothing happened whenever a disabled widget sat
     * between the two, so this swaps with the nearest enabled neighbour.
     */
    private void moveWidget(DashboardWidgetLayout.Widget widget, int direction) {
        List<List<DashboardWidgetLayout.Widget>> pages = currentPages();
        int fromPage = -1;
        int fromIndex = -1;
        for (int page = 0; page < pages.size(); page++) {
            int index = pages.get(page).indexOf(widget);
            if (index >= 0) {
                fromPage = page;
                fromIndex = index;
                break;
            }
        }
        if (fromPage < 0) {
            return;
        }
        int toPage = fromPage;
        int toIndex = fromIndex + direction;
        if (toIndex < 0) {
            // Off the top of a page: the previous page's last place, so the
            // buttons walk the whole arrangement rather than stopping at a
            // page boundary that TalkBack cannot cross by dragging.
            if (fromPage == 0) {
                return;
            }
            toPage = fromPage - 1;
            toIndex = pages.get(toPage).size();
        } else if (toIndex > pages.get(fromPage).size() - 1) {
            if (fromPage == pages.size() - 1) {
                return;
            }
            toPage = fromPage + 1;
            toIndex = 0;
        }
        pages.get(fromPage).remove(fromIndex);
        pages.get(toPage).add(Math.min(toIndex, pages.get(toPage).size()), widget);
        applyArrangement(pages);
        rows.announceForAccessibility(getString(R.string.dashboard_builder_moved,
                label(widget), toPage + 1));
    }

    /**
     * The widgets that are on, split into the pages they sit on.
     *
     * <p>This is the model the list, the move buttons and dragging all work
     * on, so the three cannot disagree about where a widget is.
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
     * The page-count chooser. Rebuilt rather than built once: a preset or a
     * template can change the count, and the chooser has to follow.
     */
    private void buildPagesSegment() {
        int current = DashboardWidgetLayout.loadPageCount(this);
        pagesSegment.clearOnButtonCheckedListeners();
        pagesSegment.removeAllViews();
        int[] ids = new int[DashboardWidgetLayout.MAX_PAGES];
        for (int index = 0; index < DashboardWidgetLayout.MAX_PAGES; index++) {
            MaterialButton segment = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.widget_segment_button, pagesSegment, false);
            segment.setId(View.generateViewId());
            segment.setText(getString(R.string.dashboard_builder_page_short, index + 1));
            ids[index] = segment.getId();
            pagesSegment.addView(segment, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        pagesSegment.check(ids[Math.max(0, Math.min(ids.length - 1, current - 1))]);
        pagesSegment.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            for (int index = 0; index < ids.length; index++) {
                if (ids[index] == checkedId) {
                    DashboardWidgetLayout.savePageCount(this, index + 1);
                    // The list is cut into one section per page, and the
                    // preview gains or loses a page to look at, so both are
                    // rebuilt.
                    previewPage = Math.min(previewPage, index + 1);
                    buildPreviewPagesSegment();
                    renderRows();
                    notifyDashboardChanged();
                    return;
                }
            }
        });
    }

    /**
     * Builds the arrangement as a list of pages.
     *
     * <p>The list used to show all nineteen widgets with an on/off switch in
     * every card, so arranging four of them meant scrolling past fifteen
     * greyed-out cards. Selection moved to {@link WidgetPickerActivity}; this
     * screen arranges what is on and nothing else.
     *
     * <p>Pages used to be a number picked inside each card, which meant reading
     * nineteen cards to find out what page two held. They are sections here
     * instead: a widget is on the page it is listed under, and it changes page
     * by crossing a heading, by drag or with the move buttons.
     */
    private void renderRows() {
        rows.removeAllViews();
        List<List<DashboardWidgetLayout.Widget>> pages = currentPages();
        int total = 0;
        for (List<DashboardWidgetLayout.Widget> page : pages) {
            total += page.size();
        }
        emptyState.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        if (total == 0) {
            // Empty page headings over an empty list say nothing twice.
            return;
        }
        for (int page = 0; page < pages.size(); page++) {
            if (pages.size() > 1) {
                rows.addView(pageHeader(page + 1));
            }
            if (pages.get(page).isEmpty()) {
                if (pages.size() > 1) {
                    rows.addView(emptyPageHint());
                }
                continue;
            }
            for (int index = 0; index < pages.get(page).size(); index++) {
                // The move buttons stop only at the two ends of the whole
                // arrangement. Anywhere else they step over a page heading,
                // including onto a page that is still empty.
                boolean canMoveUp = page > 0 || index > 0;
                boolean canMoveDown = page < pages.size() - 1
                        || index < pages.get(page).size() - 1;
                MaterialCardView card = widgetCard(
                        pages.get(page).get(index), canMoveUp, canMoveDown);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
                cardParams.bottomMargin = dp(8);
                rows.addView(card, cardParams);
            }
        }
    }

    /** A page heading, tagged with its number so a drop can read it back. */
    private TextView pageHeader(int page) {
        TextView header = new TextView(this);
        header.setText(getString(R.string.dashboard_builder_page_section, page));
        header.setTextAppearance(R.style.HyperOS_Text_SectionHeader);
        header.setTag(page);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(page == 1 ? 0 : 12);
        params.bottomMargin = dp(6);
        header.setLayoutParams(params);
        return header;
    }

    /** Says a page is empty, and gives a drop something to land on. */
    private TextView emptyPageHint() {
        TextView hint = new TextView(this);
        hint.setText(R.string.dashboard_builder_page_empty);
        hint.setTextAppearance(R.style.HyperOS_Text_Caption);
        hint.setTag(Boolean.TRUE);
        hint.setPadding(dp(16), dp(14), dp(16), dp(18));
        hint.setBackgroundResource(R.drawable.hyper_group_background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        hint.setLayoutParams(params);
        return hint;
    }

    private MaterialCardView widgetCard(DashboardWidgetLayout.Widget widget,
            boolean canMoveUp, boolean canMoveDown) {
        MaterialCardView card = new MaterialCardView(this);
        card.setTag(widget);
        card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(
                this, R.color.hyper_surface_grouped));
        card.setRadius(dp(20)); card.setCardElevation(0f); card.setStrokeWidth(0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(8), dp(8), dp(12));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(label(widget));
        title.setTextAppearance(R.style.HyperOS_Text_RowTitle);
        title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton drag = compactButton("≡");
        drag.setContentDescription(getString(R.string.dashboard_builder_drag, label(widget)));
        drag.setTooltipText(getString(R.string.dashboard_builder_drag_hint));
        View.OnLongClickListener startDrag = view -> {
            ClipData data = ClipData.newPlainText("widget", widget.name());
            return card.startDragAndDrop(data, new View.DragShadowBuilder(card), widget, 0);
        };
        drag.setOnLongClickListener(startDrag);
        // The whole card starts a drag too: aiming at a 56dp handle to
        // reorder a list is needless precision.
        card.setOnLongClickListener(startDrag);

        // Explicit move buttons beside the drag handle. Long-press drag was
        // the only way to reorder, which TalkBack cannot practically perform
        // and which is awkward with one hand. They step over page headings as
        // well, so changing page never requires a drag.
        MaterialButton up = compactButton("↑");
        up.setContentDescription(getString(R.string.dashboard_builder_move_up, label(widget)));
        up.setEnabled(canMoveUp);
        up.setOnClickListener(view -> moveWidget(widget, -1));
        header.addView(up, new LinearLayout.LayoutParams(dp(48), dp(48)));

        MaterialButton down = compactButton("↓");
        down.setContentDescription(getString(R.string.dashboard_builder_move_down, label(widget)));
        down.setEnabled(canMoveDown);
        down.setOnClickListener(view -> moveWidget(widget, 1));
        header.addView(down, new LinearLayout.LayoutParams(dp(48), dp(48)));

        header.addView(drag, new LinearLayout.LayoutParams(dp(56), dp(48)));
        row.addView(header, new LinearLayout.LayoutParams(-1, -2));

        row.addView(segmentedRow(R.string.dashboard_builder_position_label,
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

        row.addView(segmentedRow(R.string.dashboard_builder_size_label,
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

        row.addView(segmentedRow(R.string.dashboard_builder_style_label,
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

        // Only the widgets that can run out of data have anything to decide
        // here; the clock always has a value.
        if (DashboardWidgetLayout.canBeEmpty(widget)) {
            row.addView(segmentedRow(R.string.dashboard_builder_presence_label,
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

        row.addView(segmentedRow(R.string.dashboard_builder_gap_label,
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

        card.addView(row);
        return card;
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
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(-1, -2);
        columnParams.topMargin = dp(10);
        column.setLayoutParams(columnParams);

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
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(42), 1f);
            group.addView(segment, params);
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
                    widgets.clear(); widgets.addAll(DashboardWidgetLayout.loadOrder(this));
                    refreshPageControls();
                    renderRows(); notifyDashboardChanged();
                }).show();
    }

    private void applyPreset(Preset preset) {
        DashboardWidgetLayout.reset(this);
        DashboardWidgetLayout.Widget[] selected;
        DashboardSettings.Layout layout;
        DashboardWidgetLayout.Orientation orientation;
        if (preset == Preset.TRIP) {
            selected = new DashboardWidgetLayout.Widget[]{DashboardWidgetLayout.Widget.SPEED,
                    DashboardWidgetLayout.Widget.COMPASS, DashboardWidgetLayout.Widget.ALTITUDE,
                    DashboardWidgetLayout.Widget.SESSION_TIMER, DashboardWidgetLayout.Widget.BATTERY,
                    DashboardWidgetLayout.Widget.CLOCK};
            layout = DashboardSettings.Layout.COMPACT;
            orientation = DashboardWidgetLayout.Orientation.LANDSCAPE;
        } else if (preset == Preset.MUSIC) {
            selected = new DashboardWidgetLayout.Widget[]{DashboardWidgetLayout.Widget.MEDIA,
                    DashboardWidgetLayout.Widget.CLOCK, DashboardWidgetLayout.Widget.BATTERY};
            layout = DashboardSettings.Layout.STACKED;
            orientation = DashboardWidgetLayout.Orientation.LANDSCAPE;
        } else if (preset == Preset.WEATHER) {
            selected = new DashboardWidgetLayout.Widget[]{DashboardWidgetLayout.Widget.WEATHER,
                    DashboardWidgetLayout.Widget.CLOCK, DashboardWidgetLayout.Widget.DATE,
                    DashboardWidgetLayout.Widget.TEMPERATURE, DashboardWidgetLayout.Widget.BATTERY};
            layout = DashboardSettings.Layout.STACKED;
            orientation = DashboardWidgetLayout.Orientation.PORTRAIT;
        } else {
            selected = new DashboardWidgetLayout.Widget[]{DashboardWidgetLayout.Widget.CLOCK,
                    DashboardWidgetLayout.Widget.DATE, DashboardWidgetLayout.Widget.BATTERY};
            layout = DashboardSettings.Layout.STACKED;
            orientation = DashboardWidgetLayout.Orientation.AUTO;
        }
        List<DashboardWidgetLayout.Widget> order = new ArrayList<>();
        for (DashboardWidgetLayout.Widget widget : selected) order.add(widget);
        for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.Widget.values())
            if (!order.contains(widget)) order.add(widget);
        DashboardWidgetLayout.saveOrder(this, order);
        DashboardWidgetLayout.saveOrientation(this, orientation);
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this).withLayout(layout);
        for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.Widget.values())
            settings = settings.withWidget(widget, order.indexOf(widget) < selected.length);
        if (preset == Preset.CLOCK) {
            DashboardWidgetLayout.saveSize(this, DashboardWidgetLayout.Widget.CLOCK,
                    DashboardWidgetLayout.Size.LARGE);
        }
        MirrorSettings.saveDashboardSettings(this, settings);
        widgets.clear(); widgets.addAll(order);
        refreshPageControls(); renderRows(); refreshPreview();
    }

    private MaterialButton compactButton(String text) {
        // Inflated rather than constructed: a MaterialButton built in Java cannot
        // take an XML widget style, and these sit next to buttons that do.
        MaterialButton button = (MaterialButton) getLayoutInflater()
                .inflate(R.layout.widget_compact_button, null, false);
        button.setText(text); button.setMinWidth(dp(48)); button.setMinimumHeight(dp(48));
        button.setMaxLines(1); button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
    }

    /**
     * Drops a dragged widget where the pointer is, page included.
     *
     * <p>The drop used to be resolved against the position of the card in the
     * list and then applied to the stored order, which also holds the widgets
     * that are switched off - so a widget landed one place out for every
     * disabled widget above it. It resolves to a page and a place on that page
     * now, which is the model the list itself is built from.
     */
    private boolean handleDrop(View view, DragEvent event) {
        if (event.getAction() != DragEvent.ACTION_DROP) return true;
        Object state = event.getLocalState();
        if (!(state instanceof DashboardWidgetLayout.Widget)) return false;
        DashboardWidgetLayout.Widget dragged = (DashboardWidgetLayout.Widget) state;

        int page = 1;
        int index = 0;
        int targetPage = 1;
        int targetIndex = 0;
        boolean found = false;
        for (int child = 0; child < rows.getChildCount(); child++) {
            View row = rows.getChildAt(child);
            Object tag = row.getTag();
            if (tag instanceof Integer) {
                // A page heading: everything after it belongs to that page.
                page = (Integer) tag;
                index = 0;
                continue;
            }
            if (tag instanceof Boolean) {
                // The placeholder on an empty page: the only place to land.
                if (!found && event.getY() < row.getBottom()) {
                    targetPage = page; targetIndex = 0; found = true;
                }
                continue;
            }
            if (!found && event.getY() < row.getTop() + row.getHeight() / 2f) {
                targetPage = page; targetIndex = index; found = true;
            }
            index++;
        }
        if (!found) {
            targetPage = page;
            targetIndex = index;
        }

        List<List<DashboardWidgetLayout.Widget>> pages = currentPages();
        int fromPage = -1;
        int fromIndex = -1;
        for (int candidate = 0; candidate < pages.size(); candidate++) {
            int at = pages.get(candidate).indexOf(dragged);
            if (at >= 0) { fromPage = candidate; fromIndex = at; break; }
        }
        if (fromPage < 0) return true;
        pages.get(fromPage).remove(fromIndex);
        int toPage = Math.max(0, Math.min(pages.size() - 1, targetPage - 1));
        if (toPage == fromPage && fromIndex < targetIndex) targetIndex--;
        targetIndex = Math.max(0, Math.min(targetIndex, pages.get(toPage).size()));
        pages.get(toPage).add(targetIndex, dragged);
        applyArrangement(pages);
        return true;
    }


    private void notifyDashboardChanged() {
        MirrorSettings.saveDashboardSettings(this, MirrorSettings.loadDashboardSettings(this));
        refreshPreview();
    }

    private void refreshPreview() {
        if (preview == null) return;
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this)
                .withContentMode(RearContentMode.DASHBOARD);
        preview.setDashboardSettings(settings, RearContentMode.DASHBOARD);
        preview.setSnapshot(previewSnapshot());
        // Pinned rather than left to cycle: editing page two should not mean
        // waiting eight seconds for it to come round again.
        preview.setSelectedPage(DashboardWidgetLayout.loadPageCount(this) > 1 ? previewPage : 0);
        DashboardWidgetLayout.Orientation orientation =
                DashboardWidgetLayout.loadPageOrientation(this, previewPage);
        orientationButton.setText(orientationLabel(orientation));
        layoutButton.setText(layoutLabel(
                DashboardWidgetLayout.loadPageLayout(this, previewPage, settings.layout)));
        sizePreviewToPanel(orientation);
    }

    /**
     * Fills the preview from live state where the app already has it.
     *
     * <p>It used to be entirely invented - 72%, 36.5 degrees, a sample track -
     * so the preview could not show that the battery reading wraps at the
     * current text size, or that the playing track is too long for one line.
     * Battery, temperature, charging, the current track, the clock and the
     * profile name are real. Weather, heading, speed, altitude and the system
     * counters still stand in: those come from sensors and network the builder
     * does not start, and the rear-panel preview shows them for real.
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
                45f,
                8.3f,
                120.0,
                754_000L,
                MirrorSettings.profileDisplayName(this, MirrorSettings.loadActiveProfile(this)));
    }

    /**
     * Gives the preview the rear panel's proportions.
     *
     * <p>The preview was a fixed 180x300dp box standing in for a 126x294 panel
     * - a 0.60 shape for a 0.43 one, at twice the linear size. Text that fit in
     * the preview could overflow the panel, and edge alignment landed
     * elsewhere. The shape now comes from the panel itself, and falls back to
     * the old boxes only when the rear display cannot be read.
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

    private void cycleOrientation() {
        DashboardWidgetLayout.Orientation old =
                DashboardWidgetLayout.loadPageOrientation(this, previewPage);
        DashboardWidgetLayout.Orientation next = old == DashboardWidgetLayout.Orientation.AUTO
                ? DashboardWidgetLayout.Orientation.LANDSCAPE
                : old == DashboardWidgetLayout.Orientation.LANDSCAPE
                ? DashboardWidgetLayout.Orientation.PORTRAIT : DashboardWidgetLayout.Orientation.AUTO;
        DashboardWidgetLayout.savePageOrientation(this, previewPage, next);
        notifyDashboardChanged();
    }

    private void cycleLayout() {
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this);
        DashboardSettings.Layout old =
                DashboardWidgetLayout.loadPageLayout(this, previewPage, settings.layout);
        DashboardSettings.Layout next = old == DashboardSettings.Layout.STACKED
                ? DashboardSettings.Layout.CORNERS : old == DashboardSettings.Layout.CORNERS
                ? DashboardSettings.Layout.COMPACT : DashboardSettings.Layout.STACKED;
        // The first page is the panel-wide setting the rear-panel screen shows,
        // so it is written there; later pages are overrides of it.
        if (previewPage <= 1) {
            MirrorSettings.saveDashboardSettings(this, settings.withLayout(next));
            refreshPreview();
        } else {
            DashboardWidgetLayout.savePageLayout(this, previewPage, next);
            notifyDashboardChanged();
        }
    }

    /**
     * The chooser that says which page the preview and the two buttons above
     * it are looking at. Hidden while the arrangement has a single page.
     */
    /** Both page choosers, after something changed the count wholesale. */
    private void refreshPageControls() {
        buildPagesSegment();
        buildPreviewPagesSegment();
    }

    private void buildPreviewPagesSegment() {
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        previewPagesSegment.removeAllViews();
        previewPagesSegment.setVisibility(pageCount > 1 ? View.VISIBLE : View.GONE);
        if (pageCount <= 1) {
            previewPage = 1;
            previewPageNote.setVisibility(View.GONE);
            return;
        }
        previewPage = Math.max(1, Math.min(pageCount, previewPage));
        int[] ids = new int[pageCount];
        for (int index = 0; index < pageCount; index++) {
            MaterialButton segment = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.widget_segment_button, previewPagesSegment, false);
            segment.setId(View.generateViewId());
            segment.setText(getString(R.string.dashboard_builder_page_short, index + 1));
            ids[index] = segment.getId();
            previewPagesSegment.addView(segment, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        previewPagesSegment.setContentDescription(
                getString(R.string.dashboard_builder_preview_page));
        previewPageNote.setVisibility(View.VISIBLE);
        previewPagesSegment.check(ids[previewPage - 1]);
        previewPagesSegment.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            for (int index = 0; index < ids.length; index++) {
                if (ids[index] == checkedId) {
                    previewPage = index + 1;
                    refreshPreview();
                    return;
                }
            }
        });
    }

    /**
     * The list of templates the user saved.
     *
     * <p>Tapping one applies it. The overflow beside it renames, overwrites or
     * deletes, so the row itself stays a single obvious action.
     */
    private void renderNamedTemplates() {
        List<DashboardTemplateStore.Named> saved = DashboardTemplateStore.listNamed(this);
        namedTemplates.removeAllViews();
        namedTemplates.setVisibility(saved.isEmpty() ? View.GONE : View.VISIBLE);
        namedTemplatesEmpty.setText(saved.isEmpty()
                ? R.string.dashboard_named_templates_help
                : R.string.dashboard_named_templates_hint);
        addNamedTemplateButton.setEnabled(saved.size() < DashboardTemplateStore.MAX_NAMED);
        for (int index = 0; index < saved.size(); index++) {
            DashboardTemplateStore.Named template = saved.get(index);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            MaterialButton apply = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.widget_template_row, row, false);
            apply.setText(template.name);
            apply.setMaxLines(1);
            apply.setEllipsize(TextUtils.TruncateAt.END);
            // The group draws the surface; only the pressed tint is rounded,
            // and it has to follow the corner the row sits in.
            apply.setShapeAppearanceModel(ShapeAppearanceModel.builder(this, 0,
                    saved.size() == 1 ? R.style.HyperOS_Shape_Group
                            : index == 0 ? R.style.HyperOS_Shape_RowTop
                            : index == saved.size() - 1 ? R.style.HyperOS_Shape_RowBottom
                            : R.style.HyperOS_Shape_RowMiddle).build());
            apply.setOnClickListener(view -> applyNamedTemplate(template));
            row.addView(apply, new LinearLayout.LayoutParams(0, -2, 1f));

            MaterialButton more = (MaterialButton) getLayoutInflater()
                    .inflate(R.layout.widget_template_row, row, false);
            more.setText("\u22EF");
            more.setContentDescription(getString(
                    R.string.dashboard_named_template_actions, template.name));
            more.setPadding(0, 0, 0, 0);
            more.setOnClickListener(view -> showTemplateActions(template));
            row.addView(more, new LinearLayout.LayoutParams(dp(56), -2));
            namedTemplates.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void applyNamedTemplate(DashboardTemplateStore.Named template) {
        if (!DashboardTemplateStore.applyNamed(this, template.id)) {
            Toast.makeText(this, R.string.dashboard_custom_template_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        widgets.clear();
        widgets.addAll(DashboardWidgetLayout.loadOrder(this));
        refreshPageControls();
        renderRows();
        notifyDashboardChanged();
        Toast.makeText(this, getString(R.string.dashboard_named_template_applied, template.name),
                Toast.LENGTH_SHORT).show();
    }

    private void showTemplateActions(DashboardTemplateStore.Named template) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(template.name)
                .setItems(new CharSequence[]{
                        getString(R.string.dashboard_named_template_overwrite),
                        getString(R.string.dashboard_named_template_rename),
                        getString(R.string.dashboard_named_template_delete)
                }, (dialog, which) -> {
                    if (which == 0) {
                        DashboardTemplateStore.overwriteNamed(this, template.id);
                        Toast.makeText(this, R.string.dashboard_custom_template_saved,
                                Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        promptForTemplateName(R.string.dashboard_named_template_rename,
                                template.name, name -> {
                                    DashboardTemplateStore.renameNamed(this, template.id, name);
                                    renderNamedTemplates();
                                });
                    } else {
                        confirmDeleteTemplate(template);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteTemplate(DashboardTemplateStore.Named template) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dashboard_named_template_delete)
                .setMessage(getString(R.string.dashboard_named_template_delete_message,
                        template.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dashboard_named_template_delete, (dialog, which) -> {
                    DashboardTemplateStore.deleteNamed(this, template.id);
                    renderNamedTemplates();
                })
                .show();
    }

    private void promptForNewTemplate() {
        promptForTemplateName(R.string.dashboard_named_template_add, "", name -> {
            if (DashboardTemplateStore.createNamed(this, name) == null) {
                Toast.makeText(this, R.string.dashboard_named_template_full,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            renderNamedTemplates();
        });
    }

    /** The name sheet, shared by saving a new template and renaming one. */
    private void promptForTemplateName(int titleResource, String initial,
            java.util.function.Consumer<String> onNamed) {
        TextInputLayout inputLayout = (TextInputLayout) getLayoutInflater()
                .inflate(R.layout.dialog_template_name, null, false);
        TextInputEditText input = inputLayout.findViewById(R.id.template_name_input);
        input.setText(initial);
        input.setSelection(input.length());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(titleResource)
                .setView(inputLayout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        inputLayout.setError(getString(R.string.dashboard_named_template_required));
                        return;
                    }
                    inputLayout.setError(null);
                    onNamed.accept(name);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private String layoutLabel(DashboardSettings.Layout layout) {
        return getString(layout == DashboardSettings.Layout.CORNERS
                ? R.string.dashboard_builder_layout_corners
                : layout == DashboardSettings.Layout.COMPACT
                ? R.string.dashboard_builder_layout_compact
                : R.string.dashboard_builder_layout_stacked);
    }

    private String orientationLabel(DashboardWidgetLayout.Orientation orientation) {
        return getString(orientation == DashboardWidgetLayout.Orientation.LANDSCAPE
                ? R.string.dashboard_orientation_landscape
                : orientation == DashboardWidgetLayout.Orientation.PORTRAIT
                ? R.string.dashboard_orientation_portrait : R.string.dashboard_orientation_auto);
    }


    private String positionLabel(DashboardWidgetLayout.Position position) {
        return getString(position == DashboardWidgetLayout.Position.LEFT
                ? R.string.dashboard_builder_left : position == DashboardWidgetLayout.Position.RIGHT
                ? R.string.dashboard_builder_right : R.string.dashboard_builder_center);
    }

    private String sizeLabel(DashboardWidgetLayout.Size size) {
        return getString(size == DashboardWidgetLayout.Size.SMALL ? R.string.dashboard_builder_small
                : size == DashboardWidgetLayout.Size.LARGE ? R.string.dashboard_builder_large
                : R.string.dashboard_builder_normal);
    }







    private String styleLabel(DashboardWidgetLayout.Style style) {
        return getString(style == DashboardWidgetLayout.Style.ACCENT
                ? R.string.dashboard_builder_style_accent
                : style == DashboardWidgetLayout.Style.MUTED
                ? R.string.dashboard_builder_style_muted : R.string.dashboard_builder_style_default);
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
                R.string.dashboard_widget_steps};
        return getString(labels[widget.ordinal()]);
    }


    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
