package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Brings a release APK onto the phone and hands it to the system installer.
 *
 * <p>The file lands in the app's cache and is shared with the installer through
 * a FileProvider. Installing from an app needs the "install unknown apps"
 * permission, which only the user can grant on its own settings page.
 */
final class UpdateInstaller {
    /** Called from the download thread with bytes received and bytes expected (0 when unknown). */
    interface Progress {
        void onProgress(long received, long total);
    }

    /** The user stopped the download. */
    static final class Cancelled extends IOException {
        Cancelled() {
            super("Update download cancelled");
        }
    }

    /** The file arrived but is not the one GitHub published. */
    static final class Damaged extends IOException {
        Damaged(String message) {
            super(message);
        }
    }

    private static final String TRUSTED_PREFIX = "https://";
    private static final String UPDATES_DIR = "updates";
    private static final String APK_TYPE = "application/vnd.android.package-archive";

    private UpdateInstaller() {
    }

    static String authority(Context context) {
        return context.getPackageName() + ".updates";
    }

    /** Whether this app may start the installer for a file it downloaded. */
    static boolean canInstall(Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /** The system page where the user lets this app install packages. */
    static Intent permissionIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
    }

    /**
     * Downloads the release APK, checks it against the published size and hash,
     * and returns the finished file. Blocks; keep it off the main thread.
     */
    static File download(Context context, UpdateChecker.Release release, Progress progress,
                         AtomicBoolean cancelled) throws IOException {
        if (release.apkUrl == null || !release.apkUrl.startsWith(TRUSTED_PREFIX)) {
            throw new IOException("Release has no APK");
        }
        File dir = new File(context.getCacheDir(), UPDATES_DIR);
        clear(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create " + dir);
        }
        File part = new File(dir, "update.part");
        File apk = new File(dir, "Mirror2RearUltra-" + release.version() + ".apk");

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(release.apkUrl).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "Mirror2RearUltra");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Update download HTTP " + responseCode);
            }
            long total = release.apkSize > 0 ? release.apkSize : connection.getContentLengthLong();
            long received = 0;
            long lastReport = 0;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(part)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (cancelled.get()) {
                        throw new Cancelled();
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    received += read;
                    long now = System.nanoTime();
                    if (now - lastReport > 100_000_000L) {
                        lastReport = now;
                        progress.onProgress(received, total);
                    }
                }
            }
            if (cancelled.get()) {
                throw new Cancelled();
            }
            if (release.apkSize > 0 && received != release.apkSize) {
                throw new Damaged("Size " + received + " != " + release.apkSize);
            }
            if (release.apkSha256 != null && !release.apkSha256.equals(hex(digest.digest()))) {
                throw new Damaged("Checksum does not match the release");
            }
            if (received == 0 || !part.renameTo(apk)) {
                throw new IOException("Cannot finish " + apk);
            }
            progress.onProgress(received, total);
            return apk;
        } catch (IOException failed) {
            // Never leave a half-written or rejected file where it could be installed.
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw failed;
        } finally {
            connection.disconnect();
        }
    }

    /** Opens the system installer on a downloaded APK. */
    static Intent installIntent(Context context, File apk) {
        Uri uri = FileProvider.getUriForFile(context, authority(context), apk);
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static void clear(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format(Locale.ROOT, "%02x", b));
        }
        return out.toString();
    }
}
