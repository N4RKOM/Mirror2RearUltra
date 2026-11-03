package com.tpkarras.mirror2rearultra;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class DashboardTileService extends TileService {
    private static final String TAG = "RearWidgetsTile";
    private static final int REQUEST_START_DASHBOARD = 1002;

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    @SuppressLint("StartActivityAndCollapseDeprecated")
    public void onClick() {
        super.onClick();
        Intent intent = DashboardLauncher.toggle(this);
        updateTile();
        if (intent == null) return;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, REQUEST_START_DASHBOARD,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to launch the rear widget panel", error);
            MirrorState.setActive(this, false);
            updateTile();
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean active = MirrorState.isDashboardOnly();
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(active ? R.string.tile_on : R.string.tile_off));
        tile.setContentDescription(getString(R.string.dashboard_quicktile));
        tile.updateTile();
    }
}
