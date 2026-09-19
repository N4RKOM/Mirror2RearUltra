package com.tpkarras.mirror2rearultra;

import android.content.Context;

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

        Snapshot(String title, String artist, boolean playing) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.playing = playing;
        }

        boolean hasMedia() {
            return !title.isEmpty() || !artist.isEmpty();
        }
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot current = new Snapshot("", "", false);

    private MediaWidgetState() {
    }

    static Snapshot get() {
        return current;
    }

    static void set(String title, String artist, boolean playing) {
        Snapshot next = new Snapshot(title, artist, playing);
        Snapshot previous = current;
        // A session can report its state several times a second, position and
        // all. Only a change anyone can see is worth waking the panel for.
        if (previous.playing == next.playing
                && previous.title.equals(next.title)
                && previous.artist.equals(next.artist)) {
            return;
        }
        current = next;
        for (Listener listener : LISTENERS) {
            listener.onMediaWidgetChanged(next);
        }
    }

    static void clear() {
        set("", "", false);
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
