package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

/** The start and stop of the rear widgets, shared by the Quick Settings tile and the app. */
final class DashboardLauncher {
    private DashboardLauncher() {
    }

    /**
     * Stops the widgets when they run; otherwise arms a widgets-only session,
     * ending a mirror session first, and returns the intent that opens it.
     * The caller starts the intent, and may collapse the shade while doing so.
     */
    @Nullable
    static Intent toggle(Context context) {
        if (MirrorState.isDashboardOnly()) {
            MirrorState.setActive(context, false);
            return null;
        }
        if (MirrorState.isActive()) {
            MirrorState.setActive(context, false);
            if (ForegroundService.isRunning()) {
                context.startService(ForegroundService.createStopIntent(context));
            }
        }
        MirrorState.armSession(context, true);
        return new Intent(context, DisplayActivity.class)
                .putExtra(DisplayActivity.EXTRA_DASHBOARD_ONLY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }
}
