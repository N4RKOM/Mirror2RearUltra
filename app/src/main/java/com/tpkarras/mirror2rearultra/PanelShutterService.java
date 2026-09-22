package com.tpkarras.mirror2rearultra;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Presses a camera's shutter button without root.
 *
 * <p>Touching another app's window is the one thing an ordinary app may never
 * do, which is why the panel's tap otherwise has to go out through a root
 * shell. An accessibility service may, and it does it better: it is handed the
 * camera's own window - on the main screen, whichever window happens to hold
 * the focus - and presses the button itself rather than a point on the glass,
 * so nothing depends on where that button was a moment ago.
 *
 * <p>It is switched on by hand in the system's own settings and does nothing
 * until it is. While it is off the panel falls back to root; with neither, a
 * tap on the panel does nothing.
 */
public final class PanelShutterService extends AccessibilityService {
    private static final String TAG = "PanelShutterService";
    /** What a shutter button calls itself, as in the dump the shell makes. */
    private static final Pattern SHUTTER_NAME = Pattern.compile("shutter|capture");
    /**
     * The same, in words, for a camera whose buttons carry no useful ids.
     *
     * <p>A description is written for a screen reader, so it says what the
     * button does in the phone's own language.
     */
    private static final String[] SHUTTER_WORDS = {
            "shutter", "capture", "take photo", "take picture",
            "снимок", "затвор", "сфотограф", "фото",
    };
    private static final int MAXIMUM_DEPTH = 24;
    private static final long TAP_MILLIS = 40L;

    @Nullable
    private static volatile PanelShutterService instance;

    /** Whether the service is running and can press anything. */
    static boolean isRunning() {
        return instance != null;
    }

    /**
     * Whether the user has switched the service on.
     *
     * <p>Asked of the system rather than of {@link #isRunning}: a service that
     * has been enabled but not yet bound would otherwise read as missing.
     */
    static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null
                && enabled.contains(context.getPackageName() + "/"
                        + PanelShutterService.class.getName());
    }

    /**
     * Presses the shutter of this app on the main screen.
     *
     * @return whether a button was found and pressed
     */
    static boolean press(@Nullable String packageName) {
        PanelShutterService service = instance;
        return service != null && packageName != null && service.pressIn(packageName);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Shutter access on");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        Log.i(TAG, "Shutter access off");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Nothing to watch: the panel asks when it has been tapped.
    }

    @Override
    public void onInterrupt() {
    }

    private boolean pressIn(String packageName) {
        SparseArray<List<AccessibilityWindowInfo>> displays;
        try {
            displays = getWindowsOnAllDisplays();
        } catch (RuntimeException notReady) {
            return false;
        }
        List<AccessibilityWindowInfo> windows = displays.get(Display.DEFAULT_DISPLAY);
        if (windows == null) {
            return false;
        }
        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null || root.getPackageName() == null
                    || !packageName.contentEquals(root.getPackageName())) {
                continue;
            }
            Rect screen = new Rect();
            root.getBoundsInScreen(screen);
            AccessibilityNodeInfo shutter = findShutter(root, 0, screen, null);
            if (shutter == null) {
                continue;
            }
            if (shutter.isClickable()
                    && shutter.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            // Refuses the press, so press where it sits instead.
            Rect bounds = new Rect();
            shutter.getBoundsInScreen(bounds);
            return !bounds.isEmpty() && tap(bounds.centerX(), bounds.centerY());
        }
        return false;
    }

    /**
     * The smallest button in this window that calls itself a shutter.
     *
     * <p>The smallest, because the word turns up in the names of whole
     * containers as well: the first match in a camera app was a layout the
     * size of the screen, and pressing its middle focused the camera in the
     * centre of the frame instead of taking a picture. A shutter button is
     * small, is visible, and takes a press - anything else is scenery.
     */
    @Nullable
    private AccessibilityNodeInfo findShutter(
            AccessibilityNodeInfo node, int depth, Rect window,
            @Nullable AccessibilityNodeInfo best) {
        if (depth > MAXIMUM_DEPTH) {
            return best;
        }
        if (node.isVisibleToUser() && node.isClickable() && names(node)
                && smallEnough(node, window)) {
            best = smaller(best, node);
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                best = findShutter(child, depth + 1, window, best);
            }
        }
        return best;
    }

    /** A button, not the layout it sits in. */
    private static boolean smallEnough(AccessibilityNodeInfo node, Rect window) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return false;
        }
        long area = (long) bounds.width() * bounds.height();
        long whole = (long) Math.max(1, window.width()) * Math.max(1, window.height());
        return area * 4 < whole;
    }

    private static AccessibilityNodeInfo smaller(
            @Nullable AccessibilityNodeInfo best, AccessibilityNodeInfo node) {
        if (best == null) {
            return node;
        }
        Rect one = new Rect();
        Rect two = new Rect();
        best.getBoundsInScreen(one);
        node.getBoundsInScreen(two);
        return (long) two.width() * two.height() < (long) one.width() * one.height()
                ? node : best;
    }

    /** Whether this view calls itself a shutter, by id or in words. */
    private static boolean names(AccessibilityNodeInfo node) {
        String id = node.getViewIdResourceName();
        if (id != null && SHUTTER_NAME.matcher(id.toLowerCase(Locale.ROOT)).find()) {
            return true;
        }
        CharSequence description = node.getContentDescription();
        if (description == null) {
            return false;
        }
        String said = description.toString().toLowerCase(Locale.ROOT);
        for (String word : SHUTTER_WORDS) {
            if (said.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean tap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        gesture.setDisplayId(Display.DEFAULT_DISPLAY);
        gesture.addStroke(new GestureDescription.StrokeDescription(path, 0L, TAP_MILLIS));
        return dispatchGesture(gesture.build(), null, null);
    }
}
