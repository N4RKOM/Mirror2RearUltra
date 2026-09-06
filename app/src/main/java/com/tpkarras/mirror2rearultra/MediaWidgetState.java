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

        Snapshot(String title, String artist) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
        }

        boolean hasMedia() {
            return !title.isEmpty() || !artist.isEmpty();
        }
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot current = new Snapshot("", "");

    private MediaWidgetState() {
    }

    static Snapshot get() {
        return current;
    }

    static void set(String title, String artist) {
        Snapshot next = new Snapshot(title, artist);
        current = next;
        for (Listener listener : LISTENERS) {
            listener.onMediaWidgetChanged(next);
        }
    }

    static void clear() {
        set("", "");
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
