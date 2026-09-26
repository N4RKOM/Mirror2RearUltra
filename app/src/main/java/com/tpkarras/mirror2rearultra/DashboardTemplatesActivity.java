package com.tpkarras.mirror2rearultra;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Presets, saved templates and the triggers that bring them up.
 *
 * <p>These used to sit in the middle of the builder, between the preview and
 * the widgets, so editing a page meant scrolling past four groups that do not
 * edit a page. Everything here replaces or stores the whole arrangement, so
 * applying something returns to the builder to show the result.
 */
public class DashboardTemplatesActivity extends AppCompatActivity {
    private enum Preset { CLOCK, TRIP, MUSIC, WEATHER }

    private HyperValueRow triggerChargingInput;
    private HyperValueRow triggerTimeInput;
    private HyperValueRow triggerFromInput;
    private HyperValueRow triggerToInput;
    private LinearLayout namedTemplates;
    private TextView namedTemplatesEmpty;
    private MaterialButton addNamedTemplateButton;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_dashboard_templates);
        MaterialToolbar toolbar = findViewById(R.id.dashboard_templates_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        HyperValueRow presetInput = findViewById(R.id.dashboard_builder_preset);
        String[] presetLabels = {getString(R.string.dashboard_preset_clock),
                getString(R.string.dashboard_preset_trip), getString(R.string.dashboard_preset_music),
                getString(R.string.dashboard_preset_weather)};
        presetInput.setEntries(presetLabels);
        presetInput.setValue(getString(R.string.dashboard_builder_choose_value));
        presetInput.setOnItemSelectedListener(position -> {
            if (position >= 0 && position < Preset.values().length) {
                applyPreset(Preset.values()[position]);
                appliedAndBack(presetLabels[position]);
            }
        });

        findViewById(R.id.dashboard_custom_template_save).setOnClickListener(view -> {
            DashboardTemplateStore.save(this);
            Toast.makeText(this, R.string.dashboard_custom_template_saved, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.dashboard_custom_template_apply).setOnClickListener(view -> {
            if (!DashboardTemplateStore.apply(this)) {
                Toast.makeText(this, R.string.dashboard_custom_template_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            appliedAndBack(MirrorSettings.profileDisplayName(
                    this, MirrorSettings.loadActiveProfile(this)));
        });

        namedTemplates = findViewById(R.id.dashboard_named_templates);
        namedTemplatesEmpty = findViewById(R.id.dashboard_named_templates_empty);
        addNamedTemplateButton = findViewById(R.id.dashboard_named_template_add);
        addNamedTemplateButton.setOnClickListener(view -> promptForNewTemplate());
        renderNamedTemplates();

        triggerChargingInput = findViewById(R.id.panel_trigger_charging_input);
        triggerTimeInput = findViewById(R.id.panel_trigger_time_input);
        triggerFromInput = findViewById(R.id.panel_trigger_from_input);
        triggerToInput = findViewById(R.id.panel_trigger_to_input);
        triggerFromInput.setOnClickListener(view -> pickTriggerTime(true));
        triggerToInput.setOnClickListener(view -> pickTriggerTime(false));
        renderTriggers();
    }

    /**
     * Publishes a replaced arrangement and goes back to the builder, which
     * reloads it on resume - the result is only visible there.
     */
    private void appliedAndBack(String name) {
        MirrorSettings.saveDashboardSettings(this, MirrorSettings.loadDashboardSettings(this));
        Toast.makeText(this, getString(R.string.dashboard_named_template_applied, name),
                Toast.LENGTH_SHORT).show();
        finish();
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
    }

    /**
     * The rows that say when a page or a saved template should come up by
     * itself.
     *
     * <p>Rebuilt whenever the templates are, because both rows list them: one
     * deleted there must stop being offered here. The pages of the
     * arrangement in use come first - holding the panel on one of its own
     * pages is the lighter thing to ask for. The two times are only shown once
     * something is chosen for them - a window with nothing to put in it is a
     * question with no answer.
     */
    private void renderTriggers() {
        List<String> slots = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        slots.add(null);
        labels.add(getString(R.string.panel_trigger_off));
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        for (int page = 1; page <= pageCount; page++) {
            slots.add(PanelTriggers.pageSlot(page));
            String name = DashboardWidgetLayout.loadPageName(this, page);
            labels.add(name.isEmpty()
                    ? getString(R.string.dashboard_builder_page_section, page)
                    : getString(R.string.dashboard_page_label_named, page, name));
        }
        for (DashboardTemplateStore.Named named : DashboardTemplateStore.listNamed(this)) {
            slots.add(named.id);
            labels.add(getString(R.string.panel_trigger_template, named.name));
        }
        bindTriggerRow(triggerChargingInput, slots, labels,
                PanelTriggers.chargingSlot(this), true);
        bindTriggerRow(triggerTimeInput, slots, labels,
                PanelTriggers.timeSlot(this), false);
        boolean timed = PanelTriggers.timeSlot(this) != null;
        triggerFromInput.setVisibility(timed ? android.view.View.VISIBLE : android.view.View.GONE);
        triggerToInput.setVisibility(timed ? android.view.View.VISIBLE : android.view.View.GONE);
        triggerFromInput.setValue(clockLabel(PanelTriggers.fromMinutes(this)));
        triggerToInput.setValue(clockLabel(PanelTriggers.toMinutes(this)));
    }

    private void bindTriggerRow(HyperValueRow row, List<String> slots, List<String> labels,
            @Nullable String current, boolean forCharging) {
        row.setEntries(labels.toArray(new String[0]));
        int index = current == null ? 0 : Math.max(0, slots.indexOf(current));
        row.setValue(labels.get(index));
        row.setOnItemSelectedListener(position -> {
            String slot = position <= 0 || position >= slots.size()
                    ? null : slots.get(position);
            if (forCharging) {
                PanelTriggers.setChargingSlot(this, slot);
            } else {
                PanelTriggers.setTimeSlot(this, slot);
            }
            renderTriggers();
            MirrorSettings.saveDashboardSettings(this, MirrorSettings.loadDashboardSettings(this));
        });
    }

    /** A time of day written the way the phone writes it. */
    private String clockLabel(int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, minutes / 60);
        calendar.set(Calendar.MINUTE, minutes % 60);
        return android.text.format.DateFormat.getTimeFormat(this).format(calendar.getTime());
    }

    private void pickTriggerTime(boolean start) {
        int current = start ? PanelTriggers.fromMinutes(this) : PanelTriggers.toMinutes(this);
        new android.app.TimePickerDialog(this, (view, hour, minute) -> {
            int picked = hour * 60 + minute;
            PanelTriggers.setWindow(this,
                    start ? picked : PanelTriggers.fromMinutes(this),
                    start ? PanelTriggers.toMinutes(this) : picked);
            renderTriggers();
            MirrorSettings.saveDashboardSettings(this, MirrorSettings.loadDashboardSettings(this));
        }, current / 60, current % 60,
                android.text.format.DateFormat.is24HourFormat(this)).show();
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
        namedTemplates.setVisibility(saved.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
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
            more.setText("⋯");
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
        appliedAndBack(template.name);
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
                                    renderTriggers();
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
                    renderTriggers();
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
            renderTriggers();
        });
    }

    /** The name sheet, shared by saving a new template and renaming one. */
    private void promptForTemplateName(int titleResource, String initial,
            java.util.function.Consumer<String> onNamed) {
        // Inflated into a holder so its margins survive: without a parent
        // the field ran edge to edge of the dialog.
        android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        TextInputLayout inputLayout = (TextInputLayout) getLayoutInflater()
                .inflate(R.layout.dialog_template_name, holder, false);
        holder.addView(inputLayout);
        TextInputEditText input = inputLayout.findViewById(R.id.template_name_input);
        input.setText(initial);
        input.setSelection(input.length());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(titleResource)
                .setView(holder)
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

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
