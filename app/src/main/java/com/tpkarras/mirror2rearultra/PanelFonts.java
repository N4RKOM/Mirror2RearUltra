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
 * Every face a widget can be drawn in: the built-in ones and the user's own.
 *
 * <p>A choice is stored as a string rather than an enum so the two kinds live
 * in one list - the built-ins keep their enum names, an imported font is
 * prefixed. An id that no longer names anything, because its file was
 * removed, falls back to the panel's face rather than to nothing.
 */
final class PanelFonts {

    static final class Choice {
        final String id;
        final String label;

        Choice(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /** Loaded once each: parsing a font file is not free. */
    private static final Map<String, Typeface> LOADED = new HashMap<>();

    private PanelFonts() {}

    static List<Choice> choices(Context context) {
        List<Choice> result = new ArrayList<>();
        for (PanelFont font : PanelFont.available()) {
            result.add(new Choice(font.name(), context.getString(font.labelResource)));
        }
        for (PanelFontStore.Entry entry : PanelFontStore.list(context)) {
            result.add(new Choice(entry.id, entry.label));
        }
        return result;
    }

    static String[] labels(List<Choice> choices) {
        String[] labels = new String[choices.size()];
        for (int index = 0; index < choices.size(); index++) {
            labels[index] = choices.get(index).label;
        }
        return labels;
    }

    static int indexOf(List<Choice> choices, String id) {
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).id.equals(id)) {
                return index;
            }
        }
        return 0;
    }

    static String labelFor(Context context, String id) {
        for (Choice choice : choices(context)) {
            if (choice.id.equals(id)) {
                return choice.label;
            }
        }
        return context.getString(PanelFont.PANEL.labelResource);
    }

    /** The face for an id, or null to take whatever the panel is drawn in. */
    @Nullable
    static synchronized Typeface resolve(Context context, String id) {
        if (id == null || id.equals(PanelFont.PANEL.name())) {
            return null;
        }
        if (id.equals(PanelFont.SYSTEM.name())) {
            return Typeface.DEFAULT;
        }
        if (LOADED.containsKey(id)) {
            return LOADED.get(id);
        }
        Typeface loaded = null;
        File file = id.startsWith(PanelFontStore.ID_PREFIX)
                ? PanelFontStore.fileFor(context, id)
                : builtInFile(id);
        if (file != null) {
            try {
                loaded = Typeface.createFromFile(file);
            } catch (RuntimeException ignored) {
                // Gone or unreadable since the list was built.
            }
        }
        LOADED.put(id, loaded);
        return loaded;
    }

    /** Drops a removed font from the cache so its id stops resolving. */
    static synchronized void forget(String id) {
        LOADED.remove(id);
    }

    @Nullable
    private static File builtInFile(String id) {
        for (PanelFont font : PanelFont.values()) {
            if (font.name().equals(id) && font.path != null) {
                File file = new File(font.path);
                return file.canRead() ? file : null;
            }
        }
        return null;
    }
}
