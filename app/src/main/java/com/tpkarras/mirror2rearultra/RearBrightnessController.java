package com.tpkarras.mirror2rearultra;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class RearBrightnessController {
    interface Listener {
        void onBrightnessApplied(boolean hardwareControlActive, int appliedPercent);
    }

    interface CapabilityCallback {
        void onCapabilitiesProbed(boolean rootAvailable, boolean rearBrightnessAvailable);
    }

    private static final String TAG = "RearBrightness";
    private static final String BRIGHTNESS_PATH =
            "/sys/class/backlight/panel1-backlight/brightness";
    private static final String MAX_BRIGHTNESS_PATH =
            "/sys/class/backlight/panel1-backlight/max_brightness";
    private static final Object HARDWARE_STATE_LOCK = new Object();
    private static int originalBrightness = -1;
    private static int maximumBrightness = -1;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Listener listener;
    private Boolean hardwareAvailable;

    RearBrightnessController(Listener listener) {
        this.listener = listener;
    }

    void applyPercent(int percent) {
        if (closed.get()) {
            return;
        }
        int clampedPercent = Math.max(1, Math.min(100, percent));
        executor.execute(() -> {
            boolean applied = applyHardwareBrightness(clampedPercent);
            DeviceCapabilityState.setHardwareBrightnessActive(applied);
            listener.onBrightnessApplied(applied, clampedPercent);
        });
    }

    static void probeCapabilities(CapabilityCallback callback) {
        DeviceCapabilityState.setProbing();
        Thread thread = new Thread(() -> {
            CommandResult result = runRootCommand(
                    "id; if [ -r " + BRIGHTNESS_PATH + " ] && [ -w " + BRIGHTNESS_PATH
                            + " ] && [ -r " + MAX_BRIGHTNESS_PATH
                            + " ]; then echo M2RU_REAR_BRIGHTNESS_READY; fi"
            );
            boolean rootAvailable = result.exitCode == 0 && result.output.stream()
                    .anyMatch(line -> line.contains("uid=0"));
            boolean rearBrightnessAvailable = rootAvailable && result.output.stream()
                    .anyMatch(line -> line.contains("M2RU_REAR_BRIGHTNESS_READY"));
            DeviceCapabilityState.setCapabilities(rootAvailable, rearBrightnessAvailable);
            callback.onCapabilitiesProbed(rootAvailable, rearBrightnessAvailable);
        }, "rear-capability-probe");
        thread.start();
    }

    void closeAndRestore() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            restoreOriginalBrightness();
            DeviceCapabilityState.setHardwareBrightnessActive(false);
        });
        executor.shutdown();
    }

    void restoreWhileOpen() {
        if (!closed.get()) {
            executor.execute(() -> {
                restoreOriginalBrightness();
                DeviceCapabilityState.setHardwareBrightnessActive(false);
            });
        }
    }

    void closeWithoutRestore() {
        if (closed.compareAndSet(false, true)) {
            DeviceCapabilityState.setHardwareBrightnessActive(false);
            executor.shutdown();
        }
    }

    private boolean applyHardwareBrightness(int percent) {
        if (Boolean.FALSE.equals(hardwareAvailable)) {
            return false;
        }
        if (!ensureHardwareState()) {
            hardwareAvailable = false;
            return false;
        }
        int value;
        synchronized (HARDWARE_STATE_LOCK) {
            // The sysfs value is a share of the panel's output, and the
            // percentage is a share of what the eye reads, so the two are not
            // the same number.
            value = Math.max(1, Math.round(
                    maximumBrightness * PerceptualBrightness.toLinear(percent)));
        }
        CommandResult result = runRootCommand(
                "printf %d " + value + " > " + BRIGHTNESS_PATH
        );
        boolean success = result.exitCode == 0;
        hardwareAvailable = success;
        if (!success) {
            Log.w(TAG, "Root brightness write failed; using software dimming: "
                    + result.describe());
        } else {
            Log.i(TAG, "Rear-panel hardware brightness applied: " + percent + "%");
        }
        return success;
    }

    private static boolean ensureHardwareState() {
        synchronized (HARDWARE_STATE_LOCK) {
            if (originalBrightness >= 0 && maximumBrightness > 0) {
                return true;
            }
            CommandResult result = runRootCommand(
                    "cat " + BRIGHTNESS_PATH + "; cat " + MAX_BRIGHTNESS_PATH
            );
            if (result.exitCode != 0) {
                Log.w(TAG, "Root brightness probe failed: " + result.describe());
                return false;
            }
            List<Integer> numericLines = new ArrayList<>();
            for (String line : result.output) {
                String trimmed = line.trim();
                if (trimmed.matches("[0-9]+")) {
                    numericLines.add(Integer.parseInt(trimmed));
                }
            }
            if (numericLines.size() < 2 || numericLines.get(1) <= 0) {
                Log.w(TAG, "Rear-panel brightness probe returned an unexpected response: "
                        + result.describe());
                return false;
            }
            originalBrightness = numericLines.get(0);
            maximumBrightness = numericLines.get(1);
            return true;
        }
    }

    private static void restoreOriginalBrightness() {
        int value;
        synchronized (HARDWARE_STATE_LOCK) {
            value = originalBrightness;
        }
        if (value < 0) {
            return;
        }
        CommandResult result = runRootCommand("printf %d " + value + " > " + BRIGHTNESS_PATH);
        if (result.exitCode == 0) {
            synchronized (HARDWARE_STATE_LOCK) {
                originalBrightness = -1;
                maximumBrightness = -1;
            }
        } else {
            Log.w(TAG, "Unable to restore the original rear-panel brightness");
        }
    }

    private static CommandResult runRootCommand(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandResult(-1, List.of("timeout"));
            }
            List<String> output = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }
            return new CommandResult(process.exitValue(), output);
        } catch (IOException error) {
            Log.w(TAG, "Unable to start root brightness command", error);
            return new CommandResult(-1, List.of(error.getClass().getSimpleName()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, List.of("interrupted"));
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static final class CommandResult {
        final int exitCode;
        final List<String> output;

        CommandResult(int exitCode, List<String> output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        String describe() {
            return "exit=" + exitCode + ", output=" + String.join(" | ", output);
        }
    }
}
