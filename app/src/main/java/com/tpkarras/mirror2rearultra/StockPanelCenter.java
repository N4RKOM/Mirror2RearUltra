package com.tpkarras.mirror2rearultra;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Turns the stock rear screen centre app on and off through root. */
final class StockPanelCenter {
    private static final String TAG = "StockPanelCenter";
    private static final String PACKAGE = "com.xiaomi.subscreencenter";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();

    interface Callback {
        void onResult(boolean enabled, boolean succeeded);
    }

    private StockPanelCenter() {
    }

    /** Reads whether the package is enabled; the result arrives on a worker thread. */
    static void query(Callback callback) {
        WORK.execute(() -> {
            String disabled = run("pm list packages -d " + PACKAGE);
            String installed = run("pm list packages " + PACKAGE);
            boolean present = installed != null && installed.contains(PACKAGE);
            boolean off = disabled != null && disabled.contains(PACKAGE);
            callback.onResult(present && !off, installed != null && present);
        });
    }

    static void setEnabled(boolean enabled, Callback callback) {
        WORK.execute(() -> {
            String command = enabled
                    ? "pm enable " + PACKAGE
                    : "am force-stop " + PACKAGE + "; pm disable-user --user 0 " + PACKAGE;
            boolean ok = run(command) != null;
            callback.onResult(enabled, ok);
        });
    }

    private static String run(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
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
