package com.tpkarras.mirror2rearultra;

import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import java.lang.reflect.Field;

/** Uses the same per-window gesture opt-out as Xiaomi's rear-screen activities. */
final class RearWindowPolicy {
    static boolean configure(Window window) {
        try {
            Class<WindowManager.LayoutParams> type = WindowManager.LayoutParams.class;
            int flag = type.getField("MIUI_FLAG_DISABLE_SUBSCREEN_DOUBLE_TAP").getInt(null);
            Field field = type.getField("miuiFlags");
            WindowManager.LayoutParams params = window.getAttributes();
            field.setInt(params, field.getInt(params) | flag);
            window.setAttributes(params);
            Log.i("Mirror2Rear", "Rear window uses firmware brightness and disables system double tap");
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.i("Mirror2Rear", "Legacy rear window policy: " + error);
            return false;
        }
    }
}
