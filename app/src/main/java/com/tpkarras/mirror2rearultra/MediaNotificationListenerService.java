package com.tpkarras.mirror2rearultra;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MediaNotificationListenerService extends NotificationListenerService {
    private static volatile MediaController currentController;

    static boolean previous() { return dispatch(Transport.PREVIOUS); }
    static boolean playPause() { return dispatch(Transport.PLAY_PAUSE); }
    static boolean next() { return dispatch(Transport.NEXT); }

    private static boolean dispatch(Transport action) {
        MediaController controller = currentController;
        if (controller == null) return false;
        try {
            MediaController.TransportControls controls = controller.getTransportControls();
            if (action == Transport.PREVIOUS) controls.skipToPrevious();
            else if (action == Transport.NEXT) controls.skipToNext();
            else {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) controls.pause();
                else controls.play();
            }
            return true;
        } catch (RuntimeException error) { return false; }
    }
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        refreshNotifications();
        refreshMedia();
    }

    @Override
    public void onListenerDisconnected() {
        currentController = null;
        NotificationWidgetState.clear();
        MediaWidgetState.clear();
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        refreshNotifications();
        if (isMediaNotification(notification)) {
            refreshMedia();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification notification) {
        refreshNotifications();
        if (isMediaNotification(notification)) {
            refreshMedia();
        }
    }

    private void refreshMedia() {
        StatusBarNotification selected = null;
        StatusBarNotification[] notifications;
        try {
            notifications = getActiveNotifications();
        } catch (RuntimeException error) {
            currentController = null;
            MediaWidgetState.clear();
            return;
        }
        if (notifications != null) {
            for (StatusBarNotification notification : notifications) {
                if (isMediaNotification(notification)
                        && (selected == null
                        || notification.getPostTime() > selected.getPostTime())) {
                    selected = notification;
                }
            }
        }
        if (selected == null) {
            currentController = null;
            MediaWidgetState.clear();
            return;
        }
        Bundle extras = selected.getNotification().extras;
        currentController = controllerFrom(extras);
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence artist = extras.getCharSequence(Notification.EXTRA_TEXT);
        MediaWidgetState.set(
                title == null ? "" : title.toString(),
                artist == null ? "" : artist.toString()
        );
    }

    /**
     * Counts what is waiting, and keeps the newest one worth reading.
     *
     * <p>The count is unchanged: anything that is not this app's own and not a
     * group summary. The content skips two more kinds. Media notifications
     * have a widget of their own, and an ongoing one - a foreground service,
     * a VPN, a headset companion - would pin itself to the panel for as long
     * as it ran, which is exactly what a "latest" widget should not do. Both
     * still count.
     */
    private void refreshNotifications() {
        int count = 0;
        StatusBarNotification newest = null;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification item : active) {
                Notification value = item.getNotification();
                if (value == null || getPackageName().equals(item.getPackageName())
                        || (value.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
                    continue;
                }
                count++;
                if (isMediaNotification(item)
                        || (value.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
                    continue;
                }
                if (newest == null || item.getPostTime() > newest.getPostTime()) {
                    newest = item;
                }
            }
        } catch (RuntimeException ignored) {
            NotificationWidgetState.clear();
            return;
        }
        if (newest == null) {
            NotificationWidgetState.set(count, "", "", "");
            return;
        }
        Bundle extras = newest.getNotification().extras;
        CharSequence title = extras == null
                ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras == null
                ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
        NotificationWidgetState.set(count, appLabel(newest.getPackageName()),
                title == null ? "" : title.toString(),
                text == null ? "" : text.toString());
    }

    /** The name a person would recognise, or the package name if it is gone. */
    private String appLabel(String packageName) {
        if (packageName == null) {
            return "";
        }
        try {
            PackageManager packages = getPackageManager();
            return packages.getApplicationLabel(
                    packages.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return packageName;
        }
    }

    private MediaController controllerFrom(Bundle extras) {
        if (extras == null) return null;
        MediaSession.Token token;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            token = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token.class);
        } else {
            token = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        }
        return token == null ? null : new MediaController(this, token);
    }

    private enum Transport { PREVIOUS, PLAY_PAUSE, NEXT }

    private static boolean isMediaNotification(StatusBarNotification notification) {
        if (notification == null || notification.getNotification() == null) {
            return false;
        }
        Notification value = notification.getNotification();
        return Notification.CATEGORY_TRANSPORT.equals(value.category)
                || (value.extras != null && value.extras.containsKey(Notification.EXTRA_MEDIA_SESSION));
    }
}
