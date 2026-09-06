package com.tpkarras.mirror2rearultra;

import android.app.Notification;
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
        refreshNotificationCount();
        refreshMedia();
    }

    @Override
    public void onListenerDisconnected() {
        currentController = null;
        NotificationWidgetState.setCount(0);
        MediaWidgetState.clear();
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        refreshNotificationCount();
        if (isMediaNotification(notification)) {
            refreshMedia();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification notification) {
        refreshNotificationCount();
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

    private void refreshNotificationCount() {
        try {
            int count = 0;
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification item : active) {
                Notification value = item.getNotification();
                if (!getPackageName().equals(item.getPackageName())
                        && value != null && (value.flags & Notification.FLAG_GROUP_SUMMARY) == 0) count++;
            }
            NotificationWidgetState.setCount(count);
        } catch (RuntimeException ignored) { NotificationWidgetState.setCount(0); }
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
