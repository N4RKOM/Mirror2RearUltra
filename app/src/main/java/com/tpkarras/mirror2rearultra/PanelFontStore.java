package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Font files the user brought themselves.
 *
 * <p>The faces the device ships with are a short list, and the one a theme
 * installs cannot be reached as a file at all. A font downloaded and picked
 * from storage sidesteps both: it is copied in here, so it keeps working
 * without any storage permission and without depending on the file staying
 * where it was picked from.
 */
final class PanelFontStore {
    /** Long enough for a full family; a panel face has no business being larger. */
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final String DIRECTORY = "panel_fonts";
    /** Marks an id as one of these rather than one of the built-in faces. */
    static final String ID_PREFIX = "user:";

    private PanelFontStore() {}

    static final class Entry {
        final String id;
        final String label;
        final File file;

        Entry(String id, String label, File file) {
            this.id = id;
            this.label = label;
            this.file = file;
        }
    }

    static List<Entry> list(Context context) {
        List<Entry> entries = new ArrayList<>();
        File[] files = directory(context).listFiles();
        if (files == null) {
            return entries;
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File file : files) {
            if (file.isFile()) {
                entries.add(new Entry(ID_PREFIX + file.getName(), labelOf(file.getName()), file));
            }
        }
        return entries;
    }

    static boolean any(Context context) {
        return !list(context).isEmpty();
    }

    /**
     * Copies a picked font in under a name of its own.
     *
     * @return the label it was stored under
     * @throws IOException if it cannot be read, is too large, or is not a font
     */
    static String importFromUri(Context context, Uri uri) throws IOException {
        String name = sanitise(displayName(context, uri));
        byte[] bytes;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("font_unavailable");
            }
            bytes = readLimited(input);
        }
        File directory = directory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("font_store_unavailable");
        }
        File target = new File(directory, name);
        File pending = new File(directory, name + ".pending");
        try (FileOutputStream output = new FileOutputStream(pending)) {
            output.write(bytes);
        }
        // Parsed before it is kept: a file that is not a font would otherwise
        // sit in the list and quietly draw nothing wherever it was chosen.
        Typeface parsed = null;
        try {
            parsed = Typeface.createFromFile(pending);
        } catch (RuntimeException ignored) {
            // Left null, handled below.
        }
        if (parsed == null || parsed.equals(Typeface.DEFAULT)) {
            //noinspection ResultOfMethodCallIgnored
            pending.delete();
            throw new IOException("invalid_font");
        }
        //noinspection ResultOfMethodCallIgnored
        target.delete();
        if (!pending.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            pending.delete();
            throw new IOException("font_store_unavailable");
        }
        return labelOf(name);
    }

    static void remove(Context context, String id) {
        File file = fileFor(context, id);
        if (file != null) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Nullable
    static File fileFor(Context context, String id) {
        if (id == null || !id.startsWith(ID_PREFIX)) {
            return null;
        }
        File file = new File(directory(context), sanitise(id.substring(ID_PREFIX.length())));
        return file.isFile() ? file : null;
    }

    private static File directory(Context context) {
        return new File(context.getFilesDir(), DIRECTORY);
    }

    /** The file name without its extension, which is what a font is called. */
    private static String labelOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.isEmpty() ? fileName : base;
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // Providers are allowed to refuse; fall through to the last resort.
        }
        String path = uri.getLastPathSegment();
        return path == null ? "font.ttf" : path;
    }

    /** One path segment, no directories, so a name cannot reach out of here. */
    private static String sanitise(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            cleaned = "font.ttf";
        }
        return cleaned.length() > 64 ? cleaned.substring(cleaned.length() - 64) : cleaned;
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_BYTES) {
                throw new IOException("font_too_large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
