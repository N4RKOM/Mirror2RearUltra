package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;

final class DashboardImageStore {
    /** A still picture is re-encoded until it is this small. */
    static final int MAX_STATIC_BYTES = 384 * 1024;
    /**
     * The most a stored picture may weigh, in a backup too. An animation is kept
     * as it came, since re-encoding frames is not something the platform offers,
     * so it is allowed more room than a still one.
     */
    static final int MAX_STORED_BYTES = 25 * 1024 * 1024;
    /** A still picture is only ever read in to be shrunk, so it is held to less. */
    private static final int MAX_STATIC_SOURCE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_DIMENSION = 1024;
    private static final String FILE_NAME = "dashboard-background.webp";
    /** An animated GIF or WebP, kept byte for byte. Its name says what it is. */
    private static final String ANIMATED_FILE_NAME = "dashboard-background-animated";

    private DashboardImageStore() {}
    static boolean exists(Context context) { return current(context).isFile(); }

    /** Whether the stored picture moves. */
    static boolean isAnimated(Context context) { return animatedFile(context).isFile(); }

    /** Changes when the stored image does, or 0 when there is none. */
    static long stamp(Context context) {
        File image = current(context);
        return image.isFile() ? image.lastModified() * 31L + image.length() : 0L;
    }
    static Bitmap load(Context context) { return BitmapFactory.decodeFile(current(context).getAbsolutePath()); }

    /**
     * The stored animation, repeating for ever, or null when the picture is
     * still or cannot be read as one. Scaled down by the decoder to the size
     * a still picture is kept at, so a large animation does not cost a large
     * frame buffer.
     */
    static AnimatedImageDrawable loadAnimation(Context context) {
        File image = animatedFile(context);
        if (!image.isFile()) return null;
        try {
            Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(image),
                    DashboardImageStore::limitSize);
            if (drawable instanceof AnimatedImageDrawable) {
                AnimatedImageDrawable animation = (AnimatedImageDrawable) drawable;
                animation.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                return animation;
            }
        } catch (IOException | RuntimeException ignored) {
            // Falls back to the first frame, which the still path can read.
        }
        return null;
    }

    static byte[] read(Context context) throws IOException {
        File image = current(context);
        return image.isFile() ? Files.readAllBytes(image.toPath()) : new byte[0];
    }
    static void importFromUri(Context context, Uri uri) throws IOException {
        // Through a file rather than memory: an animation can be most of 25 MB,
        // and it is kept as it came, so it never needs to be held in a heap.
        File copy = new File(context.getFilesDir(), "dashboard-background.source");
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("image_unavailable");
                copyLimited(input, copy, MAX_STORED_BYTES);
            }
            if (isAnimatedImage(copy)) {
                moveIn(context, copy, true);
                return;
            }
            if (copy.length() > MAX_STATIC_SOURCE_BYTES) throw new IOException("image_too_large");
            importStill(context, Files.readAllBytes(copy.toPath()));
        } finally {
            copy.delete();
        }
    }

    private static void importStill(Context context, byte[] source) throws IOException {
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
            if (output.size() <= MAX_STATIC_BYTES) { encoded = output.toByteArray(); break; }
        }
        if (scaled != decoded) scaled.recycle();
        decoded.recycle();
        if (encoded == null) throw new IOException("image_too_large");
        replace(context, encoded, false);
    }
    static void restore(Context context, byte[] bytes) throws IOException {
        if (bytes == null) return;
        if (bytes.length == 0) { remove(context); return; }
        if (bytes.length > MAX_STORED_BYTES) throw new IOException("invalid_image");
        if (isAnimatedImage(bytes)) {
            replace(context, bytes, true);
            return;
        }
        if (bytes.length > MAX_STATIC_BYTES
                || BitmapFactory.decodeByteArray(bytes, 0, bytes.length) == null) {
            throw new IOException("invalid_image");
        }
        replace(context, bytes, false);
    }
    static void remove(Context context) {
        staticFile(context).delete();
        animatedFile(context).delete();
    }

    /** Whether the file is a GIF or WebP with more than one frame. */
    private static boolean isAnimatedImage(File file) {
        try {
            Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),
                    DashboardImageStore::limitSize);
            return drawable instanceof AnimatedImageDrawable;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static void copyLimited(InputStream input, File target, int maximum)
            throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (FileOutputStream output = new FileOutputStream(target)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maximum) throw new IOException("image_too_large");
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        }
    }

    /** Makes the file already written the stored picture. */
    private static void moveIn(Context context, File written, boolean animated)
            throws IOException {
        File target = animated ? animatedFile(context) : staticFile(context);
        File other = animated ? staticFile(context) : animatedFile(context);
        if (other.exists() && !other.delete()) throw new IOException("replace_failed");
        if (target.exists() && !target.delete()) throw new IOException("replace_failed");
        if (!written.renameTo(target)) throw new IOException("replace_failed");
    }

    /** Whether the bytes are a GIF or WebP with more than one frame. */
    private static boolean isAnimatedImage(byte[] bytes) {
        try {
            Drawable drawable = ImageDecoder.decodeDrawable(
                    ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
                    DashboardImageStore::limitSize);
            return drawable instanceof AnimatedImageDrawable;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static void limitSize(ImageDecoder decoder, ImageDecoder.ImageInfo info,
            ImageDecoder.Source source) {
        int width = info.getSize().getWidth();
        int height = info.getSize().getHeight();
        int largest = Math.max(width, height);
        if (largest > MAX_DIMENSION) {
            float ratio = MAX_DIMENSION / (float) largest;
            decoder.setTargetSize(Math.max(1, Math.round(width * ratio)),
                    Math.max(1, Math.round(height * ratio)));
        }
    }

    private static void replace(Context context, byte[] bytes, boolean animated) throws IOException {
        File target = animated ? animatedFile(context) : staticFile(context);
        File temporary = new File(context.getFilesDir(), target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes); output.getFD().sync();
        }
        moveIn(context, temporary, animated);
    }
    /** The file the picture is in now: the animation if there is one. */
    private static File current(Context context) {
        File animated = animatedFile(context);
        return animated.isFile() ? animated : staticFile(context);
    }
    private static File staticFile(Context context) { return new File(context.getFilesDir(), FILE_NAME); }
    private static File animatedFile(Context context) { return new File(context.getFilesDir(), ANIMATED_FILE_NAME); }
}
