package com.tpkarras.mirror2rearultra;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * What the notification widgets show: how many are waiting, and what the
 * newest one says.
 *
 * <p>The count and the content are gathered in one pass but kept apart on
 * purpose. The rear panel faces away from its owner and towards everyone
 * else, so the words only ever reach it through a widget that was switched
 * on for them; turning the counter on never starts showing anyone's messages.
 *
 * <p>Listeners exist because a notification is worth seeing when it arrives.
 * The panel otherwise redraws every thirty seconds unless the session timer
 * is running, which is a long time to sit next to a phone that has already
 * buzzed.
 */
final class NotificationWidgetState {
    interface Listener {
        void onNotificationWidgetChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        final int count;
        /** The app it came from, under the name a person would recognise. */
        final String app;
        final String title;
        final String text;

        Snapshot(int count, String app, String title, String text) {
            this.count = Math.max(0, count);
            this.app = app == null ? "" : app;
            this.title = title == null ? "" : title;
            this.text = text == null ? "" : text;
        }

        boolean hasContent() {
            return !app.isEmpty() || !title.isEmpty() || !text.isEmpty();
        }
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot current = new Snapshot(0, "", "", "");

    private NotificationWidgetState() {
    }

    static Snapshot get() {
        return current;
    }

    static int getCount() {
        return current.count;
    }

    static void set(int count, String app, String title, String text) {
        Snapshot next = new Snapshot(count, app, title, text);
        current = next;
        for (Listener listener : LISTENERS) {
            listener.onNotificationWidgetChanged(next);
        }
    }

    static void clear() {
        set(0, "", "", "");
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }
}
