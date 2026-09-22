package com.tpkarras.mirror2rearultra;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes the picture the panel is framing.
 *
 * <p>A phone held for a rear-camera selfie has the panel facing the subject
 * and the shutter button facing away, which is a photograph nobody can take.
 * A tap on the panel presses that button instead.
 *
 * <p>It presses the button rather than sending the camera key, and the
 * difference was measured rather than guessed: a key goes to whichever window
 * holds the focus, and the finger that asks for the photograph gives the focus
 * to the panel on its way - so the key arrived back at this app every time,
 * while the camera was left with none. A touch is delivered by where it lands,
 * which no amount of focus can redirect.
 *
 * <p>Where the button is comes from the system's own dump of what each display
 * has on top. The accessibility dump was tried first and could not be used:
 * it describes whichever window is active, which while the panel is being
 * touched is the panel's own. This one covers every display at once and says
 * which is which, so the camera can be picked out by name however the focus
 * has moved.
 *
 * <p>Both the reading and the tap go through the root shell the panel's
 * backlight already uses: an ordinary app may neither read another app's
 * layout nor touch its window. With shutter access switched on none of that
 * is needed - {@link PanelShutterService} is handed the camera's own window
 * and presses the button by name - and no shell is started at all.
 */
final class PanelShutter {
    private static final String TAG = "PanelShutter";
    /**
     * How soon after a shot the next tap counts.
     *
     * <p>The panel is held against a palm as often as it is tapped, and a
     * camera that fires twice on one press is worse than one that misses.
     */
    private static final long MINIMUM_GAP_MILLIS = 700L;
    /** How long a fruitless search stands before another is worth making. */
    private static final long RETRY_MILLIS = 10_000L;
    private static final String DUMP_COMMAND = "dumpsys activity top";
    /** The heading each display's top activity is dumped under. */
    private static final Pattern ACTIVITY =
            Pattern.compile("^\\s*ACTIVITY (\\S+)/\\S+ \\S+ pid=\\S+.*displayId=(\\d+)");
    /**
     * One view: its indent, its flags, and its box within its parent.
     *
     * <p>The flags are nine characters, of which the first is the visibility
     * and the seventh says whether it can be clicked.
     */
    private static final Pattern VIEW = Pattern.compile(
            "^(\\s*)[\\w.$]+\\{[0-9a-f]+ (\\S{9}) \\S+ (-?\\d+),(-?\\d+)-(-?\\d+),(-?\\d+)(.*)$");
    /** What a shutter button calls itself. */
    private static final Pattern SHUTTER_NAME = Pattern.compile("shutter|capture");
    private static final int MAXIMUM_DEPTH = 64;

    /** Two threads: a tap must not queue behind a search that takes seconds. */
    private final ExecutorService presser = Executors.newSingleThreadExecutor();
    private final ExecutorService finder = Executors.newSingleThreadExecutor();
    private long lastFiredAt;
    /** The app and turn the button below was found in. */
    @Nullable private volatile String foundFor;
    @Nullable private volatile int[] button;
    /** The camera the button belongs to. */
    @Nullable private volatile String shutterPackage;
    /** The last app and turn a search was started for, and when. */
    @Nullable private volatile String triedFor;
    private volatile long triedAt;

    /**
     * Looks for the shutter button of the app in front, if it has not already.
     *
     * <p>Called when there is time: the search is slow and the tap must not be.
     */
    void prepare(@Nullable String packageName, int rotation) {
        if (packageName == null) {
            return;
        }
        // Remembered either way: it is the camera whose shutter is wanted.
        shutterPackage = packageName;
        // With shutter access on, the button is found at the moment it is
        // pressed and by name, so there is nothing to look up in advance and
        // no reason to start a root shell at all.
        if (PanelShutterService.isRunning()) {
            return;
        }
        String key = packageName + "/" + rotation;
        long now = SystemClock.elapsedRealtime();
        // A search that found nothing is worth repeating - the app may have
        // been mid-start - but not on every call, and this is called whenever
        // anything about the panel changes.
        if (key.equals(foundFor)
                || (key.equals(triedFor) && now - triedAt < RETRY_MILLIS)) {
            return;
        }
        triedFor = key;
        triedAt = now;
        try {
            finder.execute(() -> {
                if (key.equals(foundFor)) {
                    return;
                }
                int[] found = findButton(packageName);
                if (found == null) {
                    Log.i(TAG, "No shutter button found in " + packageName);
                    return;
                }
                button = found;
                foundFor = key;
                shutterPackage = packageName;
                Log.i(TAG, "Shutter button of " + packageName
                        + " at " + found[0] + ", " + found[1]);
            });
        } catch (RuntimeException alreadyClosed) {
            // The session is going away; nothing to prepare for.
        }
    }

    /** Forgets the button, for when the app in front or the screen turns. */
    void forget() {
        foundFor = null;
        triedFor = null;
        button = null;
        shutterPackage = null;
    }

    /**
     * Presses the shutter, unless the last press was a moment ago.
     *
     * @return whether the press was sent, so the panel can show that it was
     */
    boolean fire() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFiredAt < MINIMUM_GAP_MILLIS) {
            return false;
        }
        lastFiredAt = now;
        int[] target = button;
        String packageName = shutterPackage;
        try {
            presser.execute(() -> {
                int[] point = target;
                if (point == null && packageName != null && !PanelShutterService.isRunning()) {
                    // Nothing found yet: this shot waits for the search.
                    point = findButton(packageName);
                    if (point != null) {
                        button = point;
                    }
                }
                press(point, packageName);
                // The button may have moved - another mode, another lens row -
                // so the next tap starts from a fresh answer rather than this
                // one. Cheap, and off the path of the tap that just happened.
                refresh(packageName);
            });
        } catch (RuntimeException alreadyClosed) {
            return false;
        }
        return true;
    }

    private void refresh(@Nullable String packageName) {
        if (packageName == null || PanelShutterService.isRunning()) {
            return;
        }
        try {
            finder.execute(() -> {
                int[] found = findButton(packageName);
                if (found != null) {
                    button = found;
                }
            });
        } catch (RuntimeException alreadyClosed) {
            // The session is going away.
        }
    }

    void close() {
        presser.shutdownNow();
        finder.shutdownNow();
    }

    private static void press(@Nullable int[] target, @Nullable String packageName) {
        // The service first, when it has been switched on: it is handed the
        // camera's own window and presses the button rather than a point, so
        // nothing rests on where that button was when the dump was taken -
        // and it needs no root at all.
        if (PanelShutterService.press(packageName)) {
            return;
        }
        if (target != null) {
            if (run("input -d 0 tap " + target[0] + " " + target[1]) != null) {
                return;
            }
            Log.w(TAG, "The shutter tap failed; trying the camera key");
        }
        // Nothing found to press: the camera key is worth a try, and is what
        // a camera answers when it is the window holding the focus.
        if (run("input keyevent 27") == null) {
            Log.w(TAG, "The shutter could not be pressed; root is what this needs");
        }
    }

    /**
     * The middle of the shutter button of the camera app, or null.
     *
     * <p>Each view in the dump is placed within its parent, so the parents'
     * corners are carried down the indenting to turn the button's box into a
     * place on the screen. Only the camera's own section is read, and only
     * the one on the main screen: the panel is in the same dump.
     */
    @Nullable
    private static int[] findButton(String packageName) {
        String dump = run(DUMP_COMMAND);
        return dump == null ? null : shutterPointIn(dump, packageName);
    }

    /**
     * The same, read out of a dump that has already been taken.
     *
     * <p>Split out so the reading can be tested against a real dump without a
     * phone in the room.
     */
    @Nullable
    static int[] shutterPointIn(String dump, String packageName) {
        boolean inside = false;
        int[] indents = new int[MAXIMUM_DEPTH];
        int[] lefts = new int[MAXIMUM_DEPTH];
        int[] tops = new int[MAXIMUM_DEPTH];
        int depth = 0;
        for (String line : dump.split("\n")) {
            Matcher activity = ACTIVITY.matcher(line);
            if (activity.find()) {
                inside = packageName.equals(activity.group(1)) && "0".equals(activity.group(2));
                depth = 0;
                continue;
            }
            if (!inside) {
                continue;
            }
            Matcher view = VIEW.matcher(line);
            if (!view.matches()) {
                continue;
            }
            int indent = view.group(1).length();
            while (depth > 0 && indents[depth - 1] >= indent) {
                depth--;
            }
            int left = Integer.parseInt(view.group(3));
            int top = Integer.parseInt(view.group(4));
            int x = depth > 0 ? lefts[depth - 1] + left : left;
            int y = depth > 0 ? tops[depth - 1] + top : top;
            if (depth < MAXIMUM_DEPTH) {
                indents[depth] = indent;
                lefts[depth] = x;
                tops[depth] = y;
                depth++;
            }
            String flags = view.group(2);
            if (flags.charAt(0) != 'V' || flags.charAt(6) != 'C') {
                continue;
            }
            if (SHUTTER_NAME.matcher(view.group(7).toLowerCase(Locale.ROOT)).find()) {
                int width = Integer.parseInt(view.group(5)) - left;
                int height = Integer.parseInt(view.group(6)) - top;
                return new int[] {x + width / 2, y + height / 2};
            }
        }
        return null;
    }

    /** @return what the command printed, or null when it could not be run */
    @Nullable
    private static String run(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            String output = read(process);
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                Log.w(TAG, "A shutter command did not finish");
                return null;
            }
            return process.exitValue() == 0 ? output : null;
        } catch (IOException error) {
            Log.w(TAG, "Unable to start a shutter command", error);
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

    private static String read(Process process) {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // Whatever was read is what there is.
        }
        return text.toString();
    }
}
