package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

final class DashboardImageStore {
    static final int MAX_BACKUP_BYTES = 384 * 1024;
    private static final int MAX_SOURCE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_DIMENSION = 1024;
    private static final String FILE_NAME = "dashboard-background.webp";

    private DashboardImageStore() {}
    static boolean exists(Context context) { return file(context).isFile(); }
    static Bitmap load(Context context) { return BitmapFactory.decodeFile(file(context).getAbsolutePath()); }
    static byte[] read(Context context) throws IOException {
        File image = file(context);
        return image.isFile() ? Files.readAllBytes(image.toPath()) : new byte[0];
    }
    static void importFromUri(Context context, Uri uri) throws IOException {
        byte[] source;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("image_unavailable");
            source = readLimited(input, MAX_SOURCE_BYTES);
        }
        Bitmap decoded = BitmapFactory.decodeByteArray(source, 0, source.length);
        if (decoded == null) throw new IOException("invalid_image");
        int largest = Math.max(decoded.getWidth(), decoded.getHeight());
        Bitmap scaled = decoded;
        if (largest > MAX_DIMENSION) {
            float ratio = MAX_DIMENSION / (float) largest;
            scaled = Bitmap.createScaledBitmap(decoded, Math.round(decoded.getWidth() * ratio),
                    Math.round(decoded.getHeight() * ratio), true);
        }
        byte[] encoded = null;
        for (int quality : new int[]{88, 76, 64, 52, 40}) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.WEBP, quality, output);
            if (output.size() <= MAX_BACKUP_BYTES) { encoded = output.toByteArray(); break; }
        }
        if (scaled != decoded) scaled.recycle();
        decoded.recycle();
        if (encoded == null) throw new IOException("image_too_large");
        replace(context, encoded);
    }
    static void restore(Context context, byte[] bytes) throws IOException {
        if (bytes == null) return;
        if (bytes.length == 0) { remove(context); return; }
        if (bytes.length > MAX_BACKUP_BYTES
                || BitmapFactory.decodeByteArray(bytes, 0, bytes.length) == null) {
            throw new IOException("invalid_image");
        }
        replace(context, bytes);
    }
    static void remove(Context context) { file(context).delete(); }
    private static void replace(Context context, byte[] bytes) throws IOException {
        File target = file(context);
        File temporary = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes); output.getFD().sync();
        }
        if (target.exists() && !target.delete()) throw new IOException("replace_failed");
        if (!temporary.renameTo(target)) throw new IOException("replace_failed");
    }
    private static byte[] readLimited(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int total = 0; int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximum) throw new IOException("image_too_large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    private static File file(Context context) { return new File(context.getFilesDir(), FILE_NAME); }
}
