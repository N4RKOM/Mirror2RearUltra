package com.tpkarras.mirror2rearultra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class ForegroundService extends Service {
    private static final String TAG = "Mirror2RearService";
    private static final String ACTION_START = "com.tpkarras.mirror2rearultra.action.START";
    private static final String ACTION_STOP = "com.tpkarras.mirror2rearultra.action.STOP";
    private static final String ACTION_ATTACH_SURFACE = "com.tpkarras.mirror2rearultra.action.ATTACH_SURFACE";
    private static final String ACTION_DETACH_SURFACE = "com.tpkarras.mirror2rearultra.action.DETACH_SURFACE";
    private static final String EXTRA_RESULT_CODE = "result_code";
    private static final String EXTRA_RESULT_DATA = "result_data";
    private static final String EXTRA_SURFACE = "surface";
    private static final String EXTRA_WIDTH = "width";
    private static final String EXTRA_HEIGHT = "height";
    private static final String EXTRA_DENSITY_DPI = "density_dpi";
    private static final String CHANNEL_ID = "mirror2rear";
    private static final int NOTIFICATION_ID = 101;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaProjection mediaProjection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private Surface projectionSurface;
    private int surfaceWidth;
    private int surfaceHeight;
    private int surfaceDensityDpi;
    private boolean shuttingDown;
    private static volatile boolean running;

    static boolean isRunning() {
        return running;
    }

    static Intent createStartIntent(Context context, int resultCode, Intent resultData) {
        return new Intent(context, ForegroundService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData);
    }

    static Intent createStopIntent(Context context) {
        return new Intent(context, ForegroundService.class).setAction(ACTION_STOP);
    }

    static Intent createAttachSurfaceIntent(
            Context context,
            Surface surface,
            int width,
            int height,
            int densityDpi
    ) {
        return new Intent(context, ForegroundService.class)
                .setAction(ACTION_ATTACH_SURFACE)
                .putExtra(EXTRA_SURFACE, surface)
                .putExtra(EXTRA_WIDTH, width)
                .putExtra(EXTRA_HEIGHT, height)
                .putExtra(EXTRA_DENSITY_DPI, densityDpi);
    }

    static Intent createDetachSurfaceIntent(Context context) {
        return new Intent(context, ForegroundService.class).setAction(ACTION_DETACH_SURFACE);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            shutdown(false);
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_START:
                // Going foreground belongs with the projection that justifies
                // it. Doing it in onCreate meant that any intent which found
                // the service gone recreated it and asked to be a media
                // projection service without holding a projection, which
                // targetSDK 36 answers with a SecurityException that takes the
                // whole app down - a stop sent twice was enough.
                startProjectionForeground();
                startProjection(intent);
                break;
            case ACTION_ATTACH_SURFACE:
                attachSurface(intent);
                break;
            case ACTION_DETACH_SURFACE:
                detachSurface();
                break;
            case ACTION_STOP:
                shutdown(false);
                break;
            default:
                Log.w(TAG, "Ignoring unknown action: " + intent.getAction());
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        shutdown(false);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (!shuttingDown) {
            releaseProjection(false);
            MirrorState.setActive(this, false);
        }
        super.onDestroy();
    }

    private void startProjectionForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.projection_service_channel),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            manager.createNotificationChannel(channel);
        }

        PendingIntent stopIntent = PendingIntent.getService(
                this,
                0,
                createStopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_rear2screenultra)
                .setContentTitle(getString(R.string.projection_service_title))
                .setContentText(getString(R.string.projection_service_text))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, getString(R.string.stop), stopIntent)
                .build();

        startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        );
    }

    private void startProjection(Intent intent) {
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData = getParcelableIntentExtra(intent, EXTRA_RESULT_DATA);
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        if (resultCode == 0 || resultData == null || manager == null) {
            Log.e(TAG, "Missing media projection consent data");
            shutdown(false);
            return;
        }

        if (mediaProjection != null || virtualDisplay != null) {
            releaseProjection(false);
        }
        shuttingDown = false;
        try {
            mediaProjection = manager.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) {
                throw new IllegalStateException("MediaProjectionManager returned null");
            }
            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.i(TAG, "Media projection stopped by the system or user");
                    shutdown(true);
                }
            };
            mediaProjection.registerCallback(projectionCallback, mainHandler);
            MirrorState.setActive(this, true);
            createOrUpdateVirtualDisplay();
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to create media projection", error);
            shutdown(false);
        }
    }

    private void attachSurface(Intent intent) {
        Surface surface = getParcelableSurfaceExtra(intent, EXTRA_SURFACE);
        int width = Math.max(1, intent.getIntExtra(EXTRA_WIDTH, 1));
        int height = Math.max(1, intent.getIntExtra(EXTRA_HEIGHT, 1));
        int densityDpi = Math.max(1, intent.getIntExtra(EXTRA_DENSITY_DPI, 1));
        if (surface == null || !surface.isValid()) {
            if (surface != null) {
                surface.release();
            }
            Log.w(TAG, "Ignoring an invalid projection surface");
            return;
        }

        if (projectionSurface != null) {
            projectionSurface.release();
        }
        projectionSurface = surface;
        surfaceWidth = width;
        surfaceHeight = height;
        surfaceDensityDpi = densityDpi;

        if (mediaProjection == null) {
            Log.i(TAG, "Projection surface queued until consent initialization completes");
            return;
        }

        createOrUpdateVirtualDisplay();
    }

    private void createOrUpdateVirtualDisplay() {
        if (mediaProjection == null || projectionSurface == null || !projectionSurface.isValid()) {
            return;
        }
        try {
            if (virtualDisplay == null) {
                virtualDisplay = mediaProjection.createVirtualDisplay(
                        "Mirror2RearUltra",
                        surfaceWidth,
                        surfaceHeight,
                        surfaceDensityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        projectionSurface,
                        null,
                        mainHandler
                );
            } else {
                virtualDisplay.resize(surfaceWidth, surfaceHeight, surfaceDensityDpi);
                virtualDisplay.setSurface(projectionSurface);
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to attach the projection surface", error);
            shutdown(false);
        }
    }

    private void detachSurface() {
        if (virtualDisplay != null) {
            virtualDisplay.setSurface(null);
        }
        if (projectionSurface != null) {
            projectionSurface.release();
            projectionSurface = null;
        }
        surfaceWidth = 0;
        surfaceHeight = 0;
        surfaceDensityDpi = 0;
    }

    private void shutdown(boolean projectionAlreadyStopped) {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        releaseProjection(projectionAlreadyStopped);
        MirrorState.setActive(this, false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseProjection(boolean projectionAlreadyStopped) {
        if (virtualDisplay != null) {
            virtualDisplay.setSurface(null);
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (projectionSurface != null) {
            projectionSurface.release();
            projectionSurface = null;
        }
        if (mediaProjection != null) {
            if (projectionCallback != null) {
                mediaProjection.unregisterCallback(projectionCallback);
            }
            if (!projectionAlreadyStopped) {
                mediaProjection.stop();
            }
            mediaProjection = null;
        }
        projectionCallback = null;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static Intent getParcelableIntentExtra(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, Intent.class);
        }
        return intent.getParcelableExtra(key);
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static Surface getParcelableSurfaceExtra(Intent intent, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(key, Surface.class);
        }
        return intent.getParcelableExtra(key);
    }
}
