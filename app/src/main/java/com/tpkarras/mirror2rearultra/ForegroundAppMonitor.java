package com.tpkarras.mirror2rearultra;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import androidx.annotation.Nullable;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class ForegroundAppMonitor {
    interface Listener {
        void onForegroundProfile(@Nullable String profileId, String packageName);
    }

    private static final Set<String> IGNORED_PACKAGES = Set.of(
            "android",
            "com.android.systemui",
            "com.google.android.permissioncontroller"
    );

    private final Context context;
    private final Listener listener;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private long queryFromMillis;
    private String lastPackage;

    ForegroundAppMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager manager = context.getSystemService(AppOpsManager.class);
        if (manager == null) {
            return false;
        }
        int mode = manager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    boolean start() {
        if (!hasUsageAccess(context)) {
            return false;
        }
        queryFromMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
        executor.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
        return true;
    }

    void stop() {
        executor.shutdownNow();
    }

    private void poll() {
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
        if (manager == null) {
            return;
        }
        long now = System.currentTimeMillis();
        UsageEvents events = manager.queryEvents(queryFromMillis, now);
        queryFromMillis = now - 2_000;
        if (events == null) {
            return;
        }

        UsageEvents.Event event = new UsageEvents.Event();
        String newestPackage = null;
        long newestTimestamp = Long.MIN_VALUE;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if (type != UsageEvents.Event.ACTIVITY_RESUMED
                    && type != UsageEvents.Event.MOVE_TO_FOREGROUND) {
                continue;
            }
            String packageName = event.getPackageName();
            if (packageName == null
                    || packageName.equals(context.getPackageName())
                    || IGNORED_PACKAGES.contains(packageName)) {
                continue;
            }
            if (event.getTimeStamp() >= newestTimestamp) {
                newestTimestamp = event.getTimeStamp();
                newestPackage = packageName;
            }
        }
        if (newestPackage == null || newestPackage.equals(lastPackage)) {
            return;
        }
        lastPackage = newestPackage;
        listener.onForegroundProfile(
                MirrorSettings.assignedProfileId(context, newestPackage),
                newestPackage
        );
    }
}
