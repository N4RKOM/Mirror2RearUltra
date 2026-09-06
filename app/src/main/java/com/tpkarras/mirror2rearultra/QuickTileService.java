package com.tpkarras.mirror2rearultra;

import android.app.PendingIntent;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class QuickTileService extends TileService {
    private static final String TAG = "Mirror2RearTile";
    private static final int REQUEST_START_MIRROR = 1001;

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    @SuppressLint("StartActivityAndCollapseDeprecated")
    public void onClick() {
        super.onClick();

        if (MirrorState.isActive()) {
            MirrorState.setActive(this, false);
            if (ForegroundService.isRunning()) {
                startService(ForegroundService.createStopIntent(this));
            }
            updateTile();
            return;
        }

        MirrorState.setActive(this, true);
        updateTile();

        Intent activityIntent = new Intent(this, DisplayActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_START_MIRROR,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(activityIntent);
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to launch the media projection consent flow", error);
            MirrorState.setActive(this, false);
            updateTile();
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean active = MirrorState.isActive();
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(active ? R.string.tile_on : R.string.tile_off));
        tile.setContentDescription(getString(R.string.quicktile));
        tile.updateTile();
    }
}
