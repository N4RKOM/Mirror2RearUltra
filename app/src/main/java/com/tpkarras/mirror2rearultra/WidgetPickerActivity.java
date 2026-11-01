package com.tpkarras.mirror2rearultra;

import android.Manifest;
import android.os.Build;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The add-widget gallery: chooses which widgets the rear panel shows.
 *
 * <p>The single place a widget is switched on or off. A widget is a card;
 * tapping it puts the widget on the page the gallery was opened for, tapping
 * it again takes it off. A search field and group chips narrow what is shown.
 *
 * <p>The on/off state still lives in one {@link MaterialSwitch} per widget,
 * kept in code and never shown: the listeners that save a widget, ask for its
 * permission and tell the panel were written against them, and a card is a
 * view of the same switch. What a widget needs - a city, a text, a code, a
 * permission - sits under "Widget settings", and a block appears only while a
 * widget that uses it is on.
 *
 * <p>Reached from the widgets tab and the builder.
 */
public class WidgetPickerActivity extends AppCompatActivity implements MediaWidgetState.Listener {
    /**
     * The builder page a widget switched on here should land on. Without it a
     * widget turned on while page three was being edited appeared on whatever
     * page it last sat on, usually page one, and seemed not to have been
     * added at all.
     */
    static final String EXTRA_TARGET_PAGE = "target_page";

    /** Every widget, in the order the gallery lists them within its group. */
    private static final DashboardWidgetLayout.Widget[] WIDGETS = {
            // Time
            DashboardWidgetLayout.Widget.CLOCK,
            DashboardWidgetLayout.Widget.DATE,
            DashboardWidgetLayout.Widget.WORLD_CLOCK,
            DashboardWidgetLayout.Widget.COUNTDOWN,
            DashboardWidgetLayout.Widget.PROGRESS,
            DashboardWidgetLayout.Widget.NEXT_ALARM,
            DashboardWidgetLayout.Widget.SESSION_TIMER,
            DashboardWidgetLayout.Widget.TIMER,
            DashboardWidgetLayout.Widget.CALENDAR,
            // Device
            DashboardWidgetLayout.Widget.BATTERY,
            DashboardWidgetLayout.Widget.CHARGING,
            DashboardWidgetLayout.Widget.SOUND_MODE,
            DashboardWidgetLayout.Widget.TEMPERATURE,
            DashboardWidgetLayout.Widget.MEMORY,
            DashboardWidgetLayout.Widget.STORAGE,
            DashboardWidgetLayout.Widget.NETWORK,
            DashboardWidgetLayout.Widget.NETWORK_SPEED,
            // Weather and sensors
            DashboardWidgetLayout.Widget.WEATHER,
            DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER,
            DashboardWidgetLayout.Widget.SUN,
            DashboardWidgetLayout.Widget.UV_INDEX,
            DashboardWidgetLayout.Widget.AIR_QUALITY,
            DashboardWidgetLayout.Widget.MOON,
            DashboardWidgetLayout.Widget.COMPASS,
            DashboardWidgetLayout.Widget.LEVEL,
            DashboardWidgetLayout.Widget.SPEED,
            DashboardWidgetLayout.Widget.ALTITUDE,
            DashboardWidgetLayout.Widget.PRESSURE,
            DashboardWidgetLayout.Widget.STEPS,
            // Media and notifications
            DashboardWidgetLayout.Widget.MEDIA,
            DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA,
            DashboardWidgetLayout.Widget.NOTIFICATIONS,
            DashboardWidgetLayout.Widget.LAST_NOTIFICATION,
            // Other
            DashboardWidgetLayout.Widget.ACTIVE_PROFILE,
            DashboardWidgetLayout.Widget.CUSTOM_TEXT,
            DashboardWidgetLayout.Widget.QR_CODE,
    };

    /** One state holder per widget; see the class comment. Never attached. */
    private final Map<DashboardWidgetLayout.Widget, MaterialSwitch> widgetSwitches =
            new EnumMap<>(DashboardWidgetLayout.Widget.class);

    private LinearLayout gallery;
    private TextView noResults;
    private TextInputEditText searchInput;
    /** The group chosen in the chips, or null for all of them. */
    @Nullable private WidgetCatalog.Group groupFilter;
    /** The page chosen in the page chips, or 0 for every widget whatever its page. */
    private int pageFilter;

    private TextInputLayout weatherCityContainer;
    private TextInputEditText weatherCityInput;
    private TextInputLayout customTextContainer;
    private TextInputEditText customTextInput;
    private MaterialSwitch qrWifiSwitch;
    private TextInputLayout qrTextContainer;
    private TextInputEditText qrTextInput;
    private TextInputLayout qrSsidContainer;
    private TextInputEditText qrSsidInput;
    private TextInputLayout qrPasswordContainer;
    private TextInputEditText qrPasswordInput;
    private TextView locationAccessStatus;
    private TextView mediaAccessStatus;
    private TextView mediaNowPlaying;
    private View mediaPreviousButton;
    private View mediaPlayPauseButton;
    private View mediaNextButton;
    private View locationAccessButton;
    private View mediaAccessButton;
    private ViewGroup content;

    /** Guards the listeners while the UI is being written from stored state. */
    private boolean bindingUi;
    private MaterialSwitch imperialUnitsSwitch;
    private ActivityResultLauncher<String> locationPermissionLauncher;
    /** Calendar and step counting each need their own runtime permission. */
    private ActivityResultLauncher<String> widgetPermissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> updatePermissionUi()
        );
        widgetPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> renderGallery());
        setContentView(R.layout.activity_widget_picker);
        SettingsLayout.bindCollapsingTitle(this, R.id.widget_picker_toolbar,
                R.id.widget_picker_scroll, R.id.widget_picker_page_title);

        MaterialToolbar toolbar = findViewById(R.id.widget_picker_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        for (DashboardWidgetLayout.Widget widget : WIDGETS) {
            widgetSwitches.put(widget, new MaterialSwitch(this));
        }
        gallery = findViewById(R.id.widget_picker_gallery);
        noResults = findViewById(R.id.widget_picker_no_results);
        searchInput = findViewById(R.id.widget_picker_search);
        weatherCityContainer = findViewById(R.id.dashboard_weather_city_container);
        weatherCityInput = findViewById(R.id.dashboard_weather_city_input);
        imperialUnitsSwitch = findViewById(R.id.dashboard_imperial_units_switch);
        customTextContainer = findViewById(R.id.dashboard_custom_text_container);
        customTextInput = findViewById(R.id.dashboard_custom_text_input);
        qrWifiSwitch = findViewById(R.id.dashboard_qr_wifi_switch);
        qrTextContainer = findViewById(R.id.dashboard_qr_text_container);
        qrTextInput = findViewById(R.id.dashboard_qr_text_input);
        qrSsidContainer = findViewById(R.id.dashboard_qr_ssid_container);
        qrSsidInput = findViewById(R.id.dashboard_qr_ssid_input);
        qrPasswordContainer = findViewById(R.id.dashboard_qr_password_container);
        qrPasswordInput = findViewById(R.id.dashboard_qr_password_input);
        locationAccessStatus = findViewById(R.id.dashboard_location_access_status);
        mediaAccessStatus = findViewById(R.id.dashboard_media_access_status);
        mediaNowPlaying = findViewById(R.id.dashboard_media_now_playing);
        mediaPreviousButton = findViewById(R.id.dashboard_media_previous_button);
        mediaPlayPauseButton = findViewById(R.id.dashboard_media_play_pause_button);
        mediaNextButton = findViewById(R.id.dashboard_media_next_button);
        locationAccessButton = findViewById(R.id.dashboard_location_access_button);
        mediaAccessButton = findViewById(R.id.dashboard_media_access_button);
        content = findViewById(R.id.widget_picker_content);

        SettingsLayout.constrainContentOnWideScreens(this, content);
        bindInteractions();
        MiuixRows.installSwitches(this,
                R.id.dashboard_qr_wifi_switch,
                R.id.dashboard_imperial_units_switch);
        bindGallery();
    }

    @Override
    protected void onStart() {
        super.onStart();
        MediaWidgetState.addListener(this);
        updateMediaControls(MediaWidgetState.get());
    }

    @Override
    protected void onStop() {
        MediaWidgetState.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        updatePermissionUi();
    }

    @Override
    protected void onPause() {
        saveTexts();
        saveQr();
        super.onPause();
    }

    @Override
    public void onMediaWidgetChanged(MediaWidgetState.Snapshot snapshot) {
        runOnUiThread(() -> updateMediaControls(snapshot));
    }

    private void bindInteractions() {
        imperialUnitsSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) {
                return;
            }
            DashboardWidgetLayout.setImperialUnits(this, checked);
            // Lives outside DashboardSettings, so re-saving is what tells the
            // running panel to pick the change up.
            MirrorSettings.saveDashboardSettings(this, MirrorSettings.loadDashboardSettings(this));
        });
        for (Map.Entry<DashboardWidgetLayout.Widget, MaterialSwitch> entry
                : widgetSwitches.entrySet()) {
            DashboardWidgetLayout.Widget widget = entry.getKey();
            entry.getValue().setOnCheckedChangeListener((button, checked) -> {
                if (bindingUi) {
                    return;
                }
                int targetPage = getIntent().getIntExtra(EXTRA_TARGET_PAGE, 0);
                if (checked && targetPage > 0) {
                    // Before enabling, so the full-screen rules see the widget
                    // where it is going rather than where it was.
                    DashboardWidgetLayout.savePage(this, widget, targetPage);
                }
                DashboardWidgetLayout.setWidgetEnabled(this, widget, checked);
                onWidgetToggled(widget, checked);
                renderGallery();
            });
        }

        mediaPreviousButton.setOnClickListener(view -> MediaNotificationListenerService.previous());
        mediaPlayPauseButton.setOnClickListener(view -> MediaNotificationListenerService.playPause());
        mediaNextButton.setOnClickListener(view -> MediaNotificationListenerService.next());

        weatherCityInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                saveTexts();
            }
        });
        weatherCityInput.setOnEditorActionListener((view, actionId, event) -> {
            saveTexts();
            weatherCityInput.clearFocus();
            return false;
        });
        customTextInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                saveTexts();
            }
        });
        customTextInput.setOnEditorActionListener((view, actionId, event) -> {
            saveTexts();
            customTextInput.clearFocus();
            return false;
        });

        qrWifiSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingUi) {
                return;
            }
            saveQr();
            updateQrFields();
        });
        for (TextInputEditText input : new TextInputEditText[]{
                qrTextInput, qrSsidInput, qrPasswordInput}) {
            input.setOnFocusChangeListener((view, hasFocus) -> {
                if (!hasFocus) {
                    saveQr();
                }
            });
            input.setOnEditorActionListener((view, actionId, event) -> {
                saveQr();
                return false;
            });
        }

        locationAccessButton.setOnClickListener(view -> locationPermissionLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
        ));
        mediaAccessButton.setOnClickListener(view -> openMediaAccessSettings());
    }

    /** Extra work for the widgets that carry a value or need a permission. */
    private void onWidgetToggled(DashboardWidgetLayout.Widget widget, boolean checked) {
        switch (widget) {
            case WEATHER:
            case FULLSCREEN_WEATHER:
            case SUN:
            case UV_INDEX:
            case AIR_QUALITY:
                weatherCityContainer.setEnabled(needsWeatherCity());
                break;
            case CUSTOM_TEXT:
                customTextContainer.setEnabled(checked);
                break;
            case QR_CODE:
                updateQrFields();
                break;
            case MEDIA:
            case FULLSCREEN_MEDIA:
            case NOTIFICATIONS:
            case LAST_NOTIFICATION:
                updatePermissionUi();
                break;
            case SPEED:
            case ALTITUDE:
                requestLocationIfNeeded(checked);
                updatePermissionUi();
                break;
            case CALENDAR:
                if (checked) {
                    widgetPermissionLauncher.launch(Manifest.permission.READ_CALENDAR);
                }
                break;
            case STEPS:
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    widgetPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
                }
                break;
            default:
                break;
        }
    }

    private void render() {
        DashboardSettings settings = MirrorSettings.loadDashboardSettings(this);
        bindingUi = true;
        for (Map.Entry<DashboardWidgetLayout.Widget, MaterialSwitch> entry
                : widgetSwitches.entrySet()) {
            entry.getValue().setChecked(
                    DashboardWidgetLayout.isWidgetEnabled(this, entry.getKey()));
        }
        weatherCityInput.setText(settings.weatherCity);
        weatherCityContainer.setEnabled(needsWeatherCity());
        customTextInput.setText(settings.customText);
        customTextContainer.setEnabled(isWidgetOn(DashboardWidgetLayout.Widget.CUSTOM_TEXT));
        imperialUnitsSwitch.setChecked(DashboardWidgetLayout.isImperialUnits(this));
        qrWifiSwitch.setChecked(DashboardWidgetLayout.isQrWifi(this));
        qrTextInput.setText(DashboardWidgetLayout.qrText(this));
        qrSsidInput.setText(DashboardWidgetLayout.qrWifiSsid(this));
        qrPasswordInput.setText(DashboardWidgetLayout.qrWifiPassword(this));
        updateQrFields();
        bindingUi = false;
        renderGallery();
    }

    /** The text field or the network's two, whichever the code is made of. */
    private void updateQrFields() {
        boolean on = isWidgetOn(DashboardWidgetLayout.Widget.QR_CODE);
        boolean wifi = qrWifiSwitch.isChecked();
        qrWifiSwitch.setEnabled(on);
        qrTextContainer.setVisibility(wifi ? View.GONE : View.VISIBLE);
        qrSsidContainer.setVisibility(wifi ? View.VISIBLE : View.GONE);
        qrPasswordContainer.setVisibility(wifi ? View.VISIBLE : View.GONE);
        qrTextContainer.setEnabled(on);
        qrSsidContainer.setEnabled(on);
        qrPasswordContainer.setEnabled(on);
    }

    private void saveQr() {
        boolean wifi = qrWifiSwitch.isChecked();
        String text = textOf(qrTextInput);
        String ssid = textOf(qrSsidInput);
        String password = textOf(qrPasswordInput);
        if (wifi == DashboardWidgetLayout.isQrWifi(this)
                && text.equals(DashboardWidgetLayout.qrText(this))
                && ssid.equals(DashboardWidgetLayout.qrWifiSsid(this))
                && password.equals(DashboardWidgetLayout.qrWifiPassword(this))) {
            return;
        }
        DashboardWidgetLayout.saveQr(this, wifi, text, ssid, password);
        // Kept outside DashboardSettings, so re-saving is what tells the
        // running panel to redraw.
        MirrorSettings.saveDashboardSettings(this, MirrorSettings.loadDashboardSettings(this));
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    /**
     * Writes the two text values. Widget flags are not touched here: each
     * switch saves its own widget the moment it is toggled.
     */
    private void saveTexts() {
        String city = weatherCityInput.getText() == null
                ? "" : weatherCityInput.getText().toString();
        String text = customTextInput.getText() == null
                ? "" : customTextInput.getText().toString();
        DashboardSettings current = MirrorSettings.loadDashboardSettings(this);
        if (current.weatherCity.equals(city) && current.customText.equals(text)) {
            return;
        }
        MirrorSettings.saveDashboardSettings(this, current.withTexts(city, text));
    }

    /** The sun, UV and air widgets read from the same forecast as the weather. */
    private boolean needsWeatherCity() {
        return isWidgetOn(DashboardWidgetLayout.Widget.WEATHER)
                || isWidgetOn(DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER)
                || isWidgetOn(DashboardWidgetLayout.Widget.SUN)
                || isWidgetOn(DashboardWidgetLayout.Widget.UV_INDEX)
                || isWidgetOn(DashboardWidgetLayout.Widget.AIR_QUALITY);
    }

    private boolean isWidgetOn(DashboardWidgetLayout.Widget widget) {
        MaterialSwitch control = widgetSwitches.get(widget);
        return control != null && control.isChecked();
    }

    private void requestLocationIfNeeded(boolean enabled) {
        if (bindingUi || !enabled || hasLocationAccess()) {
            return;
        }
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void updatePermissionUi() {
        if (locationAccessStatus == null || mediaAccessStatus == null) {
            return;
        }
        boolean locationRequired = isWidgetOn(DashboardWidgetLayout.Widget.SPEED)
                || isWidgetOn(DashboardWidgetLayout.Widget.ALTITUDE);
        boolean locationGranted = hasLocationAccess();
        locationAccessStatus.setText(locationGranted
                ? R.string.dashboard_location_access_granted
                : locationRequired
                ? R.string.dashboard_location_access_required
                : R.string.dashboard_location_access_optional);
        locationAccessButton.setVisibility(
                locationRequired && !locationGranted ? View.VISIBLE : View.GONE
        );

        boolean mediaGranted = MediaWidgetState.hasNotificationAccess(this);
        // Four widgets read the shade, not two. The counter had been left out
        // of this since it was added, so switching it on alone offered no way
        // to grant the access it needs and it simply stayed at zero.
        boolean needsShade = isWidgetOn(DashboardWidgetLayout.Widget.MEDIA)
                || isWidgetOn(DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA)
                || isWidgetOn(DashboardWidgetLayout.Widget.NOTIFICATIONS)
                || isWidgetOn(DashboardWidgetLayout.Widget.LAST_NOTIFICATION);
        mediaAccessStatus.setText(mediaGranted
                ? R.string.dashboard_media_access_granted
                : needsShade
                ? R.string.dashboard_media_access_required
                : R.string.dashboard_media_access_optional);
        mediaAccessButton.setVisibility(
                needsShade && !mediaGranted ? View.VISIBLE : View.GONE);
        renderGallery();
    }

    // ------------------------------------------------------------------
    // Gallery
    // ------------------------------------------------------------------

    /**
     * A chip per page, so what is on each page can be read on its own. The
     * row is left out with a single page, where there is nothing to tell apart.
     */
    private void bindPageChips() {
        int pageCount = DashboardWidgetLayout.loadPageCount(this);
        if (pageCount <= 1) {
            return;
        }
        ChipGroup chips = findViewById(R.id.widget_picker_page_chips);
        findViewById(R.id.widget_picker_page_scroll).setVisibility(View.VISIBLE);
        addPageChip(chips, getString(R.string.widget_gallery_all_pages), 0);
        for (int page = 1; page <= pageCount; page++) {
            addPageChip(chips, WidgetCatalog.pageLabel(this, page), page);
        }
        ((Chip) chips.getChildAt(0)).setChecked(true);
        chips.setOnCheckedStateChangeListener((container, checkedIds) -> {
            pageFilter = 0;
            if (!checkedIds.isEmpty()) {
                Object tag = container.findViewById(checkedIds.get(0)).getTag();
                pageFilter = tag instanceof Integer ? (Integer) tag : 0;
            }
            renderGallery();
        });
    }

    private void addPageChip(ChipGroup chips, String label, int page) {
        Chip chip = new Chip(this);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(page);
        chips.addView(chip);
    }

    private void bindGallery() {
        bindPageChips();
        ChipGroup chips = findViewById(R.id.widget_picker_chips);
        addChip(chips, getString(R.string.widget_gallery_all), null);
        for (WidgetCatalog.Group group : WidgetCatalog.Group.values()) {
            addChip(chips, getString(group.title), group);
        }
        ((Chip) chips.getChildAt(0)).setChecked(true);
        chips.setOnCheckedStateChangeListener((container, checkedIds) -> {
            groupFilter = null;
            if (!checkedIds.isEmpty()) {
                Object tag = container.findViewById(checkedIds.get(0)).getTag();
                groupFilter = tag instanceof WidgetCatalog.Group
                        ? (WidgetCatalog.Group) tag : null;
            }
            renderGallery();
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                renderGallery();
            }
        });
    }

    private void addChip(ChipGroup chips, String label, @Nullable WidgetCatalog.Group group) {
        Chip chip = new Chip(this);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(group);
        chips.addView(chip);
    }

    /**
     * Draws the cards for what the search and the chips leave, and shows the
     * settings blocks the widgets that are on need. Called after anything that
     * can change either: a toggle, a permission, a search, a chip.
     */
    private void renderGallery() {
        if (gallery == null) {
            return;
        }
        String query = searchInput.getText() == null ? ""
                : searchInput.getText().toString().trim().toLowerCase(Locale.getDefault());
        int targetPage = getIntent().getIntExtra(EXTRA_TARGET_PAGE, 0);
        gallery.removeAllViews();
        boolean any = false;
        for (WidgetCatalog.Group group : WidgetCatalog.Group.values()) {
            if (groupFilter != null && groupFilter != group) {
                continue;
            }
            List<DashboardWidgetLayout.Widget> shown = new ArrayList<>();
            for (DashboardWidgetLayout.Widget widget : WIDGETS) {
                if (WidgetCatalog.groupOf(widget) == group
                        && (pageFilter == 0 || (isWidgetOn(widget)
                        && DashboardWidgetLayout.loadPage(this, widget) == pageFilter))
                        && (query.isEmpty() || WidgetCatalog.label(this, widget)
                        .toLowerCase(Locale.getDefault()).contains(query))) {
                    shown.add(widget);
                }
            }
            if (shown.isEmpty()) {
                continue;
            }
            any = true;
            TextView header = (TextView) getLayoutInflater()
                    .inflate(R.layout.widget_gallery_header, gallery, false);
            header.setText(group.title);
            gallery.addView(header);
            for (int start = 0; start < shown.size(); start += 3) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                // Three to a row whatever the row holds, so a short last row
                // keeps its cards the width of the rest without filler views.
                row.setWeightSum(3f);
                for (int index = start; index < Math.min(start + 3, shown.size()); index++) {
                    // A cell without margins, so a third of the row is a third
                    // however many cards the row holds; the card's own margin
                    // is set inside it.
                    FrameLayout cell = new FrameLayout(this);
                    FrameLayout.LayoutParams inner = new FrameLayout.LayoutParams(-1, -2);
                    inner.setMargins(dp(4), 0, dp(4), 0);
                    cell.addView(widgetCard(row, shown.get(index), group, targetPage), inner);
                    row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
                }
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                // Pulls the cards' own side margins back out at the edges.
                rowParams.setMargins(-dp(4), dp(8), -dp(4), 0);
                gallery.addView(row, rowParams);
            }
        }
        noResults.setText(pageFilter > 0 && query.isEmpty()
                ? R.string.widget_gallery_page_empty : R.string.widget_gallery_no_results);
        noResults.setVisibility(any ? View.GONE : View.VISIBLE);
        updateSettingsBlocks();
    }

    private View widgetCard(ViewGroup parent, DashboardWidgetLayout.Widget widget,
            WidgetCatalog.Group group, int targetPage) {
        MaterialCardView card = (MaterialCardView) getLayoutInflater()
                .inflate(R.layout.widget_gallery_card, parent, false);
        boolean on = isWidgetOn(widget);
        ImageView icon = card.findViewById(R.id.widget_gallery_icon);
        icon.setImageResource(WidgetCatalog.icon(widget));
        String name = WidgetCatalog.label(this, widget);
        ((TextView) card.findViewById(R.id.widget_gallery_name)).setText(name);
        TextView state = card.findViewById(R.id.widget_gallery_state);
        if (permissionMissing(widget)) {
            state.setText(R.string.widget_gallery_needs_access);
            state.setTextColor(ContextCompat.getColor(this, R.color.hyper_warning));
        } else if (on) {
            // With several pages the line names the page a widget is on, in
            // the accent colour for the page being added to and muted for the
            // others; "Added" alone cannot say which page it means.
            int page = DashboardWidgetLayout.loadPage(this, widget);
            boolean several = DashboardWidgetLayout.loadPageCount(this) > 1;
            boolean here = targetPage <= 0 || page == targetPage;
            state.setText(several ? getString(R.string.widget_gallery_on_page, page)
                    : getString(R.string.widget_gallery_added));
            state.setTextColor(here
                    ? com.google.android.material.color.MaterialColors.getColor(
                            this, R.attr.hyperAccent, 0)
                    : ContextCompat.getColor(this, R.color.hyper_text_secondary));
        } else {
            state.setText("");
        }
        card.setStrokeWidth(on ? dp(2) : 0);
        card.setChecked(on);
        card.setContentDescription(on
                ? getString(R.string.widget_gallery_added) + ", " + name + ", "
                + getString(R.string.widget_gallery_on_page,
                DashboardWidgetLayout.loadPage(this, widget)) : name);
        card.setOnClickListener(view ->
                widgetSwitches.get(widget).setChecked(!isWidgetOn(widget)));
        return card;
    }

    /** Whether a widget that is on still lacks what it reads from the phone. */
    private boolean permissionMissing(DashboardWidgetLayout.Widget widget) {
        if (!isWidgetOn(widget)) {
            return false;
        }
        switch (widget) {
            case SPEED:
            case ALTITUDE:
                return !hasLocationAccess();
            case MEDIA:
            case FULLSCREEN_MEDIA:
            case NOTIFICATIONS:
            case LAST_NOTIFICATION:
                return !MediaWidgetState.hasNotificationAccess(this);
            case CALENDAR:
                return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                        != PackageManager.PERMISSION_GRANTED;
            case STEPS:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED;
            default:
                return false;
        }
    }

    /** Shows the value and permission blocks only for widgets that are on. */
    private void updateSettingsBlocks() {
        boolean weather = needsWeatherCity();
        boolean location = isWidgetOn(DashboardWidgetLayout.Widget.SPEED)
                || isWidgetOn(DashboardWidgetLayout.Widget.ALTITUDE);
        boolean media = isWidgetOn(DashboardWidgetLayout.Widget.MEDIA)
                || isWidgetOn(DashboardWidgetLayout.Widget.FULLSCREEN_MEDIA)
                || isWidgetOn(DashboardWidgetLayout.Widget.NOTIFICATIONS)
                || isWidgetOn(DashboardWidgetLayout.Widget.LAST_NOTIFICATION);
        boolean text = isWidgetOn(DashboardWidgetLayout.Widget.CUSTOM_TEXT);
        boolean qr = isWidgetOn(DashboardWidgetLayout.Widget.QR_CODE);
        findViewById(R.id.widget_picker_block_weather).setVisibility(vis(weather));
        findViewById(R.id.widget_picker_block_location).setVisibility(vis(location));
        findViewById(R.id.widget_picker_block_media).setVisibility(vis(media));
        findViewById(R.id.widget_picker_block_custom_text).setVisibility(vis(text));
        findViewById(R.id.widget_picker_block_qr).setVisibility(vis(qr));
        findViewById(R.id.widget_picker_settings_header).setVisibility(
                vis(weather || location || media || text || qr));
    }

    private static int vis(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateMediaControls(MediaWidgetState.Snapshot snapshot) {
        boolean available = snapshot != null && snapshot.hasMedia();
        String title = available ? (snapshot.title.isEmpty() ? snapshot.artist : snapshot.title)
                : getString(R.string.dashboard_media_nothing_playing);
        mediaNowPlaying.setText(title);
        mediaPreviousButton.setEnabled(available);
        mediaPlayPauseButton.setEnabled(available);
        mediaNextButton.setEnabled(available);
    }

    private boolean hasLocationAccess() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openMediaAccessSettings() {
        ComponentName service = new ComponentName(this, MediaNotificationListenerService.class);
        Intent details = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        service.flattenToString());
        try {
            startActivity(details);
        } catch (RuntimeException unavailable) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }
}
