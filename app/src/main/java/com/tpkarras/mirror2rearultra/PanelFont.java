package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The faces a panel widget can be drawn in.
 *
 * <p>Two of them are not files. {@link #PANEL} defers to the panel-wide
 * setting, so a widget only names a face when it wants to differ; {@link
 * #SYSTEM} is whatever the system says, which is how a font installed through
 * Themes arrives - the theme replaces the files the default resolves to, and
 * those files are not readable from here, so a theme font can be used but
 * cannot be listed among the others by name.
 *
 * <p>The rest are files under /system/fonts, which is readable and which a
 * theme does not touch. Only the ones actually on the device are offered.
 *
 * <p>Several of them are small display faces meant for a clock and carry
 * little more than digits: a degree sign or a Cyrillic month simply does not
 * appear in them. Whether a face has a character cannot be asked from here -
 * both hasGlyph and the measured width answer for the whole system rather than
 * for one file - so the choice is left to the eye, and the builder's preview
 * shows what the panel will show.
 */
enum PanelFont {
    PANEL(R.string.dashboard_font_widget_panel, null),
    SYSTEM(R.string.dashboard_font_system, null),
    MISANS(R.string.dashboard_font_misans, "/system/fonts/MiSansVF.ttf"),
    ROBOTO(R.string.dashboard_font_roboto, "/system/fonts/Roboto-Regular.ttf"),
    SERIF(R.string.dashboard_font_serif, "/system/fonts/NotoSerif-Regular.ttf"),
    MONO(R.string.dashboard_font_mono, "/system/fonts/DroidSansMono.ttf"),
    MITYPE(R.string.dashboard_font_mitype, "/system/fonts/MitypeVF.ttf"),
    MITYPE_MONO(R.string.dashboard_font_mitype_mono, "/system/fonts/MitypeMonoVF.ttf"),
    MITYPE_CLOCK(R.string.dashboard_font_mitype_clock, "/system/fonts/MitypeClock.otf"),
    MIUI(R.string.dashboard_font_miui, "/system/fonts/MiuiEx-Regular.ttf");

    final int labelResource;
    @Nullable final String path;

    PanelFont(int labelResource, @Nullable String path) {
        this.labelResource = labelResource;
        this.path = path;
    }

    /** Loaded once each: a file face costs a parse, and the list is short. */
    private static final Map<PanelFont, Typeface> LOADED = new HashMap<>();


    /** The faces this device actually has, in the order they are declared. */
    static List<PanelFont> available() {
        List<PanelFont> result = new ArrayList<>();
        for (PanelFont font : values()) {
            if (font.path == null || new File(font.path).canRead()) {
                result.add(font);
            }
        }
        return result;
    }

    static String[] labels(Context context, List<PanelFont> fonts) {
        String[] labels = new String[fonts.size()];
        for (int index = 0; index < fonts.size(); index++) {
            labels[index] = context.getString(fonts.get(index).labelResource);
        }
        return labels;
    }

    /** The face itself, or null for the two that name no file. */
    @Nullable
    synchronized Typeface typeface() {
        if (path == null) {
            return null;
        }
        if (LOADED.containsKey(this)) {
            return LOADED.get(this);
        }
        Typeface loaded = null;
        try {
            File file = new File(path);
            if (file.canRead()) {
                loaded = Typeface.createFromFile(file);
            }
        } catch (RuntimeException ignored) {
            // Not a font after all, or gone since the list was built.
        }
        LOADED.put(this, loaded);
        return loaded;
    }

}
