package com.tpkarras.rearunlock;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.SparseArray;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Compatibility for the no-op legacy rear power calls in the tested HyperOS build. */
final class RearPowerHooks {
    private static final String TARGET = "com.tpkarras.mirror2rearultra";
    private static final String TAG = "[M2RU-rear] ";
    private static IBinder owner;
    private static IBinder.DeathRecipient death;

    static void install(ClassLoader loader) {
        if (android.os.Build.VERSION.SDK_INT != 37 || !"star".equals(android.os.Build.DEVICE)) return;
        try {
            Class<?> impl = XposedHelpers.findClass("com.android.server.power.PowerManagerServiceImpl", loader);
            Class<?> service = XposedHelpers.findClass("com.android.server.power.PowerManagerService", loader);
            Class<?> group = XposedHelpers.findClass("com.android.server.power.PowerGroup", loader);
            // Verify the adapter's dependencies before installing any power hooks.
            XposedHelpers.findMethodExact(service, "wakeUpSecondaryDisplay", group, long.class, int.class, String.class, int.class);
            XposedHelpers.findMethodExact(service, "goToSleepSecondaryDisplay", group, long.class, int.class, int.class);
            XposedHelpers.findMethodExact(service, "updatePowerStateLocked");
            XposedHelpers.findAndHookMethod(impl, "wakeUpSecondaryDisplay", IBinder.class,
                    long.class, int.class, String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!isTarget(p.thisObject) || !(p.args[0] instanceof IBinder)) return;
                    try {
                        changePower(p.thisObject, (IBinder) p.args[0], true);
                        p.setResult(null);
                    } catch (Throwable e) { XposedBridge.log(TAG + "wake adapter failed: " + e); }
                }
            });
            XposedHelpers.findAndHookMethod(impl, "goToSleepSecondaryDisplay", long.class,
                    String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!isTarget(p.thisObject)) return;
                    try {
                        changePower(p.thisObject, null, false);
                        p.setResult(null);
                    } catch (Throwable e) { XposedBridge.log(TAG + "sleep adapter failed: " + e); }
                }
            });
            XposedHelpers.findAndHookMethod(impl, "shouldSubScreenGoToSleepGroup", group,
                    int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    // Only suppress following the main screen's power key, while our client lives.
                    synchronized (RearPowerHooks.class) {
                        if (owner != null && owner.isBinderAlive() && (int) p.args[1] == 4
                                && (int) XposedHelpers.callMethod(p.args[0], "getGroupId") == 1) {
                            p.setResult(false);
                        }
                    }
                }
            });
            XposedBridge.log(TAG + "installed rear power adapter (API 37)");
        } catch (Throwable e) { XposedBridge.log(TAG + "power adapter unavailable: " + e); }
    }

    private static boolean isTarget(Object impl) {
        try {
            Context context = (Context) XposedHelpers.getObjectField(impl, "mContext");
            String[] packages = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
            return packages != null && packages.length == 1 && TARGET.equals(packages[0]);
        } catch (Throwable e) { return false; }
    }

    private static void changePower(Object impl, IBinder token, boolean enabled) throws Exception {
        int uid = Binder.getCallingUid();
        Object lock = XposedHelpers.getObjectField(impl, "mLock");
        Object service = XposedHelpers.getObjectField(impl, "mPowerManagerService");
        long identity = Binder.clearCallingIdentity();
        try {
            synchronized (lock) {
                SparseArray<?> groups = (SparseArray<?>) XposedHelpers.getObjectField(impl, "mPowerGroups");
                Object group = groups.get(1);
                if (group == null) return;
                synchronized (RearPowerHooks.class) {
                    if (enabled && owner != token) {
                        clearOwner();
                        IBinder.DeathRecipient callback = () -> {
                            synchronized (RearPowerHooks.class) {
                                if (owner == token) clearOwner();
                            }
                        };
                        token.linkToDeath(callback, 0);
                        owner = token;
                        death = callback;
                    } else if (!enabled) clearOwner();
                }
                if (enabled) {
                    XposedHelpers.callMethod(service, "wakeUpSecondaryDisplay", group,
                            SystemClock.uptimeMillis(), 2, "M2RU session", uid);
                } else {
                    XposedHelpers.callMethod(service, "goToSleepSecondaryDisplay", group,
                            SystemClock.uptimeMillis(), 0, uid);
                }
                XposedHelpers.callMethod(service, "updatePowerStateLocked");
            }
        } finally { Binder.restoreCallingIdentity(identity); }
    }

    private static void clearOwner() {
        if (owner != null && death != null) owner.unlinkToDeath(death, 0);
        owner = null;
        death = null;
    }
}
