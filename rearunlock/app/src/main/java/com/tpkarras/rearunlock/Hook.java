package com.tpkarras.rearunlock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed module: whitelists Mirror2RearUltra on the Mi 11 Ultra rear display.
 *
 * HyperOS guards launches on displayId 1 in two places inside
 * com.android.server.wm.ActivityStarterImpl (system_server / miui-services.jar):
 *
 *   isShouldShowOnRearDisplay(ActivityInfo)   - the meta-data gate (miui.rear.policy)
 *   isAllowedToStartOnRearDisplay(ActivityRecord) - additionally requires the
 *       package to be in the hardcoded SECONDARY_DISPLAY_WHITE_PACKAGE set when
 *       the keyguard is locked.
 *
 * Forcing both to return true for our package is exactly equivalent to adding it
 * to that whitelist, done at runtime so no framework file is patched.
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "[M2RU-rear] ";
    private static final String TARGET = "com.tpkarras.mirror2rearultra";
    private static final String CLASS = "com.android.server.wm.ActivityStarterImpl";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // Legacy Xposed reports system_server as "android" (including Vector).
        if (!"android".equals(lpparam.packageName)
                || !("android".equals(lpparam.processName)
                    || "system_server".equals(lpparam.processName))) {
            return;
        }
        XposedBridge.log(TAG + "initializing in " + lpparam.processName);

        final Class<?> impl;
        try {
            impl = XposedHelpers.findClass(CLASS, lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "class not found, firmware may differ: " + t);
            return;
        }

        // Vector binds each callback instance to one hook entry.
        hook(impl, "isShouldShowOnRearDisplay", "android.content.pm.ActivityInfo", allowTarget());
        hook(impl, "isAllowedToStartOnRearDisplay", "com.android.server.wm.ActivityRecord", allowTarget());
        RearPowerHooks.install(lpparam.classLoader);
    }

    private static XC_MethodHook allowTarget() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object arg = (param.args != null && param.args.length > 0) ? param.args[0] : null;
                if (arg == null) {
                    return;
                }
                Object pkg;
                try {
                    pkg = XposedHelpers.getObjectField(arg, "packageName");
                } catch (Throwable t) {
                    return;
                }
                if (TARGET.equals(pkg)) {
                    param.setResult(Boolean.TRUE);
                }
            }
        };

    }

    private static void hook(Class<?> cls, String method, String paramType, XC_MethodHook cb) {
        try {
            XposedHelpers.findAndHookMethod(cls, method, paramType, cb);
            XposedBridge.log(TAG + "hooked " + method);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "failed to hook " + method + ": " + t);
        }
    }
}
