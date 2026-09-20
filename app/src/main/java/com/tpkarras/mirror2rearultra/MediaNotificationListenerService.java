package com.tpkarras.mirror2rearultra;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

public class MediaNotificationListenerService extends NotificationListenerService {
    /**
     * Anything the user cannot swipe away is furniture, not news.
     *
     * <p>FLAG_ONGOING_EVENT alone was not enough: a foreground service can
     * post without it and still sit there forever. MIUI's own music service
     * did exactly that and took the widget over.
     */
    private static final int UNDISMISSABLE = Notification.FLAG_ONGOING_EVENT
            | Notification.FLAG_FOREGROUND_SERVICE
            | Notification.FLAG_NO_CLEAR;

    /**
     * Where players keep the track's picture, in the order worth trying.
     *
     * <p>The display icon is the one meant for showing; the other two are the
     * full-size artwork, which some players set instead.
     */
    private static final String[] ARTWORK_KEYS = {
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
    };
    /** Kept at a size the panel can use: the art arrives far larger. */
    private static final int ARTWORK_PIXELS = 192;

    private static volatile MediaController currentController;
    /** Registered on whichever controller is current, so state changes arrive. */
    private MediaController.Callback playbackCallback;

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
            else if (isPlaying(controller.getPlaybackState())) controls.pause();
            else controls.play();
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
        attachTo(null);
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
            attachTo(null);
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
            attachTo(null);
            MediaWidgetState.clear();
            return;
        }
        Bundle extras = selected.getNotification().extras;
        attachTo(controllerFrom(extras));
        MediaController controller = currentController;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence artist = extras.getCharSequence(Notification.EXTRA_TEXT);
        String titleText = title == null ? "" : title.toString();
        String artistText = artist == null ? "" : artist.toString();
        String packageName = selected.getPackageName();
        // This runs on every notification anywhere, and cutting the art down
        // makes a new bitmap each time - which the panel would then take for
        // a change and redraw for. The same track keeps the picture it had.
        MediaWidgetState.Snapshot last = MediaWidgetState.get();
        boolean sameTrack = last.artwork != null
                && last.packageName.equals(packageName)
                && last.title.equals(titleText)
                && last.artist.equals(artistText);
        MediaWidgetState.set(
                titleText,
                artistText,
                controller != null && isPlaying(controller.getPlaybackState()),
                packageName,
                sameTrack ? last.artwork : artworkOf(controller, selected)
        );
    }

    /**
     * Follows one session at a time, and hears it start and stop.
     *
     * <p>The panel drew a play triangle whichever way round the session was,
     * because nothing ever told it. A reposted notification would have carried
     * the change, but whether one arrives is the playing app's business, so
     * the state is taken from the session itself.
     */
    private void attachTo(MediaController controller) {
        MediaController previous = currentController;
        if (previous != null && playbackCallback != null) {
            try {
                previous.unregisterCallback(playbackCallback);
            } catch (RuntimeException ignored) {
                // Already gone; nothing left to detach from.
            }
        }
        playbackCallback = null;
        currentController = controller;
        if (controller == null) {
            return;
        }
        playbackCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                // The words have not changed, only whether they are moving.
                MediaWidgetState.Snapshot last = MediaWidgetState.get();
                MediaWidgetState.set(last.title, last.artist, isPlaying(state),
                        last.packageName, last.artwork);
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                // The picture can arrive after the notification that named the
                // track, so it is taken again rather than waited for.
                MediaWidgetState.Snapshot last = MediaWidgetState.get();
                MediaWidgetState.set(last.title, last.artist, last.playing,
                        last.packageName, cutDown(bitmapFrom(metadata)));
            }
        };
        try {
            controller.registerCallback(playbackCallback);
        } catch (RuntimeException ignored) {
            playbackCallback = null;
        }
    }

    /**
     * The track's picture: the session's, or the notification's, or none.
     *
     * <p>A player that sets neither leaves the panel to fall back on the app's
     * own icon, which at least says who is playing.
     */
    @Nullable
    private Bitmap artworkOf(@Nullable MediaController controller,
            StatusBarNotification notification) {
        Bitmap art = controller == null ? null : bitmapFrom(controller.getMetadata());
        if (art == null) {
            art = largeIconOf(notification);
        }
        return cutDown(art);
    }

    @Nullable
    private static Bitmap bitmapFrom(@Nullable MediaMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        for (String key : ARTWORK_KEYS) {
            Bitmap art = metadata.getBitmap(key);
            if (art != null) {
                return art;
            }
        }
        return null;
    }

    /** Some players put the art in the notification instead of the session. */
    @Nullable
    private Bitmap largeIconOf(StatusBarNotification notification) {
        Icon icon = notification.getNotification().getLargeIcon();
        if (icon == null) {
            return null;
        }
        try {
            Drawable drawable = icon.loadDrawable(this);
            if (drawable == null) {
                return null;
            }
            int width = Math.max(1, drawable.getIntrinsicWidth());
            int height = Math.max(1, drawable.getIntrinsicHeight());
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(new Canvas(bitmap));
            return bitmap;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * The middle of the picture, at a size worth holding on to.
     *
     * <p>Art comes at whatever size the player felt like - a thousand pixels
     * square is ordinary - and the panel shows it forty across. Nothing is
     * recycled here: the crop can come back as the player's own bitmap.
     */
    @Nullable
    private static Bitmap cutDown(@Nullable Bitmap source) {
        if (source == null) {
            return null;
        }
        try {
            int side = Math.min(source.getWidth(), source.getHeight());
            if (side <= 0) {
                return null;
            }
            Bitmap square = Bitmap.createBitmap(source,
                    (source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
            return side <= ARTWORK_PIXELS ? square
                    : Bitmap.createScaledBitmap(square, ARTWORK_PIXELS, ARTWORK_PIXELS, true);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isPlaying(PlaybackState state) {
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    /**
     * Counts what is waiting, and keeps the newest one worth reading.
     *
     * <p>One rule decides both. A notification counts when the user could act
     * on it: not this app's own, not a group summary standing in for others,
     * dismissable, and rated at least IMPORTANCE_DEFAULT by the system. The
     * count used to take everything, which on a real device meant fifteen
     * where two were real - thirteen foreground services and background
     * chatter - while the widget beside it quoted one of the two. Two widgets
     * reading the same shade should not disagree about what is in it.
     *
     * <p>The content skips one thing more: a media notification has a widget
     * of its own, and repeating the track under a bell would be saying the
     * same thing twice. It still counts, because it is still something in the
     * shade.
     */
    private void refreshNotifications() {
        int count = 0;
        StatusBarNotification newest = null;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            RankingMap ranking = getCurrentRanking();
            if (active != null) for (StatusBarNotification item : active) {
                Notification value = item.getNotification();
                if (value == null || getPackageName().equals(item.getPackageName())
                        || (value.flags & Notification.FLAG_GROUP_SUMMARY) != 0
                        || (value.flags & UNDISMISSABLE) != 0
                        || importanceOf(ranking, item)
                        < NotificationManager.IMPORTANCE_DEFAULT) {
                    continue;
                }
                count++;
                if (isMediaNotification(item)) {
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

    /**
     * How loudly the system itself rates this one.
     *
     * <p>Below IMPORTANCE_DEFAULT is what Android shows without a sound - sync
     * chatter, background services, anything the user silenced. The widget
     * answers "is it worth turning the phone over", so those are not it. The
     * cost is that a chat the user deliberately muted stops appearing too,
     * which is the same answer by a different route.
     */
    private int importanceOf(RankingMap map, StatusBarNotification item) {
        if (map == null) {
            return NotificationManager.IMPORTANCE_DEFAULT;
        }
        Ranking ranking = new Ranking();
        return map.getRanking(item.getKey(), ranking)
                ? ranking.getImportance() : NotificationManager.IMPORTANCE_DEFAULT;
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
