package com.n4r.rearunlock;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Tells the app that this module really is loaded in the running system.
 *
 * <p>An installed package, an enabled switch and a saved scope prove nothing: the module only
 * reaches {@code system_server} after a reboot. So once the system is up this writes
 * {@code <module versionCode>:<boot count>} to the secure setting {@link #KEY}. Any app can read
 * secure settings, so the app compares the stamp with its own view of the boot count and with the
 * module version it expects. A stamp left over from before a reboot, or from a module that has
 * since been disabled, carries an older boot count and so counts for nothing. The system raises the
 * count a little after this module loads, so the stamp is written again once it has changed.
 */
final class LoadStamp {
    /** Secure setting read by the app; keep in step with the app's reader. */
    static final String KEY = "m2ru_rearunlock_stamp";

    private static final String TAG = "[M2RU-rear] ";
    private static final long RETRY_MS = 2_000;
    private static final int ATTEMPTS = 90;

    private LoadStamp() {
    }

    /** Starts writing the stamp as soon as the settings provider answers. Never blocks the caller. */
    static void install() {
        Thread thread = new Thread(LoadStamp::writeWhenReady, "m2ru-load-stamp");
        thread.setDaemon(true);
        thread.start();
    }

    private static void writeWhenReady() {
        Throwable last = null;
        int first = -2;
        String written = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                Context context = systemContext();
                if (context != null) {
                    ContentResolver resolver = context.getContentResolver();
                    int boot = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1);
                    if (first == -2) {
                        first = boot;
                    }
                    String stamp = BuildConfig.VERSION_CODE + ":" + boot;
                    if (!stamp.equals(written) && Settings.Secure.putString(resolver, KEY, stamp)) {
                        written = stamp;
                        XposedBridge.log(TAG + "load stamp " + stamp);
                    }
                    // The system counts this boot a little after the module loads; the stamp must
                    // carry the new count, so keep going until it has changed.
                    if (written != null && boot != first) {
                        return;
                    }
                }
            } catch (Throwable error) {
                last = error;
                if (attempt % 10 == 0) {
                    XposedBridge.log(TAG + "load stamp attempt " + attempt + ": " + error);
                }
            }
            try {
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (written == null) {
            XposedBridge.log(TAG + "load stamp not written: " + last);
        }
    }

    private static Context systemContext() {
        Object thread = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null),
                "currentActivityThread");
        return thread == null ? null : (Context) XposedHelpers.callMethod(thread, "getSystemContext");
    }
}
