package com.tpkarras.mirror2rearultra;

import android.app.AppOpsManager;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Whether the system will hand out screen capture without asking.
 *
 * <p>Since Android 14 every capture needs its own answer to the consent
 * dialog, so a capture that starts and stops with the app in front would ask
 * every single time. The one exception is the app-op that older versions
 * used for the same purpose: while it is allowed the dialog is skipped. It
 * can only be granted from outside the app, so it is a one-off root command
 * (or {@code adb shell appops set <package> PROJECT_MEDIA allow}).
 *
 * <p>With it the capture can live only as long as it is wanted, and the
 * system's "this app can see your screen" indicator goes away in between.
 * Without it the capture is asked for once per session and kept, as before.
 */
final class ProjectionConsent {
    private static final String TAG = "Mirror2RearConsent";
    private static final String OP = "android:project_media";

    private ProjectionConsent() {
    }

    /** Whether a capture can be started without the consent dialog. */
    static boolean isSilent(Context context) {
        try {
            AppOpsManager ops = context.getSystemService(AppOpsManager.class);
            return ops != null && ops.unsafeCheckOpNoThrow(
                    OP, android.os.Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to read the projection app-op", error);
            return false;
        }
    }

    /**
     * Allows silent capture with a root command. Blocks, so not for the main thread.
     *
     * @return whether the app-op is now allowed
     */
    static boolean grantWithRoot(Context context) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "appops set " + context.getPackageName() + " PROJECT_MEDIA allow"});
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
        } catch (IOException error) {
            Log.w(TAG, "Unable to run the root command", error);
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return isSilent(context);
    }
}
