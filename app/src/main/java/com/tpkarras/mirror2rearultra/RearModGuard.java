package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Keeps a system mod for the rear panel out of the way while a session runs.
 *
 * <p>Some builds of this phone carry "RearScreen" scripts in
 * {@code /system/bin/rearscreen}. They watch the screen-down sensor and write
 * the panel's backlight themselves, every few seconds: off when the sensor
 * does not say the panel is up, on again from a loop that also sends wake
 * keys. Standing the phone on its end makes the sensor waver and the two
 * halves of that script take turns, so the panel flickers whatever this app
 * does. The mod has a master switch, the secure setting
 * {@code rear_doze_always_on}; with it at 0 the whole set stops. It is put to
 * 0 for the length of a session and put back as it was afterwards.
 *
 * <p>The value found is kept in the preferences, so a session that ends by
 * the process being killed hands the switch back the next time the app runs
 * rather than leaving the mod off for good. A phone without the mod has no
 * such setting and nothing is touched.
 */
final class RearModGuard {
    private static final String TAG = "RearModGuard";
    private static final String SWITCH_KEY = "rear_doze_always_on";
    private static final String PREFS = "rear_mod_guard";
    private static final String KEY_SAVED = "saved_value";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rear-mod-guard");
        thread.setDaemon(true);
        return thread;
    });

    private RearModGuard() {
    }

    /** Switches the mod off for the session, remembering what it was. */
    static void pause(Context context) {
        SharedPreferences prefs = prefs(context);
        WORK.execute(() -> {
            if (prefs.contains(KEY_SAVED)) {
                // Already paused, by this session or one that never gave it back.
                return;
            }
            String current = run("settings get secure " + SWITCH_KEY);
            if (current == null || !current.matches("[01]")) {
                // No such setting - no mod - or something unexpected: leave it be.
                return;
            }
            if (current.equals("0")) {
                return;
            }
            prefs.edit().putString(KEY_SAVED, current).commit();
            if (run("settings put secure " + SWITCH_KEY + " 0") == null) {
                prefs.edit().remove(KEY_SAVED).commit();
                return;
            }
            Log.i(TAG, "Rear screen mod paused for the session");
        });
    }

    /** Gives the mod its switch back, if this app took it. */
    static void resume(Context context) {
        SharedPreferences prefs = prefs(context);
        WORK.execute(() -> {
            String saved = prefs.getString(KEY_SAVED, null);
            if (saved == null) {
                return;
            }
            if (run("settings put secure " + SWITCH_KEY + " " + saved) != null) {
                prefs.edit().remove(KEY_SAVED).commit();
                Log.i(TAG, "Rear screen mod handed back");
            }
        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** @return the command's output, trimmed, or null when it did not succeed */
    private static String run(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            return process.exitValue() == 0 ? output.toString().trim() : null;
        } catch (IOException error) {
            Log.w(TAG, "Unable to run a root command", error);
            return null;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
