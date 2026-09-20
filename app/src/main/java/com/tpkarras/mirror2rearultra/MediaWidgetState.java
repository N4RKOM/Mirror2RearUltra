package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

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
        /** How far in the track was, when the session last said. */
        final long positionMillis;
        /** When it said so, on the clock {@link SystemClock#elapsedRealtime} reads. */
        final long positionAtMillis;
        /** How fast it is moving through the track: usually one, nought when paused. */
        final float speed;
        /** The whole track, or zero when the session does not say. */
        final long durationMillis;

        Snapshot(String title, String artist, boolean playing, String packageName,
                @Nullable Bitmap artwork, long positionMillis, long positionAtMillis,
                float speed, long durationMillis) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.playing = playing;
            this.packageName = packageName == null ? "" : packageName;
            this.artwork = artwork;
            this.positionMillis = Math.max(0L, positionMillis);
            this.positionAtMillis = positionAtMillis;
            this.speed = speed;
            this.durationMillis = Math.max(0L, durationMillis);
        }

        /**
         * Where in the track it is now, carried on from the last report.
         *
         * <p>A session says where it was and when, not where it is: asking it
         * again every second would be a binder call a second for a bar forty
         * pixels wide.
         */
        long positionNow() {
            return positionAt(SystemClock.elapsedRealtime());
        }

        /** Split out from {@link #positionNow()} so the arithmetic can be tested. */
        long positionAt(long nowMillis) {
            if (!playing || positionAtMillis <= 0L) {
                return durationMillis > 0L
                        ? Math.min(positionMillis, durationMillis) : positionMillis;
            }
            long since = Math.max(0L, nowMillis - positionAtMillis);
            long moved = positionMillis + Math.round(since * (double) speed);
            return durationMillis > 0L ? Math.min(moved, durationMillis) : moved;
        }

        boolean hasMedia() {
            return !title.isEmpty() || !artist.isEmpty();
        }
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot current =
            new Snapshot("", "", false, "", null, 0L, 0L, 1f, 0L);

    private MediaWidgetState() {
    }

    static Snapshot get() {
        return current;
    }

    static void set(String title, String artist, boolean playing, String packageName,
            @Nullable Bitmap artwork, long positionMillis, long positionAtMillis,
            float speed, long durationMillis) {
        Snapshot next = new Snapshot(title, artist, playing, packageName, artwork,
                positionMillis, positionAtMillis, speed, durationMillis);
        Snapshot previous = current;
        // A session can report its state several times a second, position and
        // all. Only a change anyone can see is worth waking the panel for -
        // and a position that has merely carried on at the speed already known
        // is not one: the panel works that out for itself. A jump of a couple
        // of seconds is somebody dragging the track, which is worth showing.
        if (previous.playing == next.playing
                && previous.title.equals(next.title)
                && previous.artist.equals(next.artist)
                && previous.packageName.equals(next.packageName)
                && previous.artwork == next.artwork
                && previous.durationMillis == next.durationMillis
                && Math.abs(previous.positionNow() - next.positionNow()) < 2_000L) {
            return;
        }
        current = next;
        for (Listener listener : LISTENERS) {
            listener.onMediaWidgetChanged(next);
        }
    }

    static void clear() {
        set("", "", false, "", null, 0L, 0L, 1f, 0L);
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
