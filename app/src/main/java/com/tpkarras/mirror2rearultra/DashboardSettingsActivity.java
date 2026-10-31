package com.tpkarras.mirror2rearultra;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * The pages of the rear panel's settings that have no place on the widgets
 * tab: what the panel shows (the content mode) and the mirror's camera
 * helpers. The looks, the screen and the touch settings fold open on the tab
 * itself; all of them are read and written by {@link PanelSettingsController}.
 *
 * <p>Everything about widgets is done in {@link DashboardBuilderActivity},
 * which opens {@link WidgetPickerActivity} from its own list, so this screen
 * leads only to the builder.
 */
public class DashboardSettingsActivity extends AppCompatActivity {

    /** Which page of this screen to show, one of the {@code SECTION_} values. */
    static final String EXTRA_SECTION = "section";
    static final int SECTION_MODE = 0;
    static final int SECTION_APPEARANCE = 1;
    static final int SECTION_SCREEN = 2;
    static final int SECTION_TOUCH = 3;
    static final int SECTION_MIRROR_TOOLS = 4;

    /** A section's page, titled after the row that opens it. */
    static Intent intentFor(android.content.Context context, int section) {
        return new Intent(context, DashboardSettingsActivity.class)
                .putExtra(EXTRA_SECTION, section);
    }

    private PanelSettingsController controller;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_settings);
        showSection(getIntent().getIntExtra(EXTRA_SECTION, SECTION_MODE));
        SettingsLayout.bindCollapsingTitle(this, R.id.dashboard_settings_toolbar,
                R.id.dashboard_settings_scroll, R.id.dashboard_settings_page_title);

        MaterialToolbar toolbar = findViewById(R.id.dashboard_settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        ViewGroup content = findViewById(R.id.dashboard_settings_content);
        controller = new PanelSettingsController(this, content, null);
        SettingsLayout.constrainContentOnWideScreens(this, content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        controller.onResume();
    }

    @Override
    protected void onPause() {
        controller.onPause();
        super.onPause();
    }

    /**
     * Shows one page of the screen and titles it. Every control stays in the
     * layout, so the binding code does not care which page is up.
     */
    private void showSection(int section) {
        int[] ids = {R.id.dashboard_section_mode, R.id.dashboard_section_appearance,
                R.id.dashboard_section_screen, R.id.dashboard_section_touch,
                R.id.dashboard_section_mirror_tools};
        int[] titles = {R.string.dashboard_mode_title, R.string.dashboard_appearance_title,
                R.string.panel_screen_title, R.string.panel_touch_title,
                R.string.panel_mirror_tools_title};
        int shown = section >= 0 && section < ids.length ? section : SECTION_MODE;
        for (int index = 0; index < ids.length; index++) {
            findViewById(ids[index]).setVisibility(index == shown ? View.VISIBLE : View.GONE);
        }
        ((TextView) findViewById(R.id.dashboard_settings_page_title)).setText(titles[shown]);
    }
}
