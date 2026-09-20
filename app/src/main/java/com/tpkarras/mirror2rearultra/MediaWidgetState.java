package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class MediaWidgetState {
    interface Listener {
        void onMediaWidgetChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        final String title;
        final String artist;
        /** Whether the session is actually playing, for the transport icon. */
        final boolean playing;
        /** Who is playing it, so the panel can show whose icon it is. */
        final String packageName;
        /** The track's own picture, already cut down, or null if it has none. */
        @Nullable final Bitmap artwork;

        Snapshot(String title, String artist, boolean playing, String packageName,
                @Nullable Bitmap artwork) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.playing = playing;
            this.packageName = packageName == null ? "" : packageName;
            this.artwork = artwork;
        }

        boolean hasMedia() {
            return !title.isEmpty() || !artist.isEmpty();
        }
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot current = new Snapshot("", "", false, "", null);

    private MediaWidgetState() {
    }

    static Snapshot get() {
        return current;
    }

    static void set(String title, String artist, boolean playing, String packageName,
            @Nullable Bitmap artwork) {
        Snapshot next = new Snapshot(title, artist, playing, packageName, artwork);
        Snapshot previous = current;
        // A session can report its state several times a second, position and
        // all. Only a change anyone can see is worth waking the panel for.
        if (previous.playing == next.playing
                && previous.title.equals(next.title)
                && previous.artist.equals(next.artist)
                && previous.packageName.equals(next.packageName)
                && previous.artwork == next.artwork) {
            return;
        }
        current = next;
        for (Listener listener : LISTENERS) {
            listener.onMediaWidgetChanged(next);
        }
    }

    static void clear() {
        set("", "", false, "", null);
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    static boolean hasNotificationAccess(Context context) {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.getPackageName());
    }
}
