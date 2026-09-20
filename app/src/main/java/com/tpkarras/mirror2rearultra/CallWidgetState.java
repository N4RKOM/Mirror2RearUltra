package com.tpkarras.mirror2rearultra;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Who is ringing, for a panel that faces away from its owner.
 *
 * <p>The one thing a rear panel is well placed to answer is "is this worth
 * turning the phone over for", and until now it could not: a ringing call is
 * an ongoing notification, and the widget that reads notifications throws
 * those away as furniture. So it is picked out on its own, ahead of that
 * filter, and pre-empts whatever page the panel was showing.
 *
 * <p>Nothing here can answer or refuse the call. Doing that means either the
 * permission to place calls or firing the notification's own buttons, and
 * guessing which button is which; a panel the owner cannot see is the last
 * place to guess.
 */
final class CallWidgetState {
    interface Listener {
        void onCallChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        final boolean ringing;
        /** Whoever the dialer named: a contact, a number, or nothing at all. */
        final String caller;
        /** What the dialer called it - "Incoming call", usually. */
        final String label;
        /** Which app is ringing, so the panel can show whose call it is. */
        final String packageName;

        Snapshot(boolean ringing, String caller, String label, String packageName) {
            this.ringing = ringing;
            this.caller = caller == null ? "" : caller;
            this.label = label == null ? "" : label;
            this.packageName = packageName == null ? "" : packageName;
        }
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot current = new Snapshot(false, "", "", "");

    private CallWidgetState() {
    }

    static Snapshot get() {
        return current;
    }

    static void set(boolean ringing, String caller, String label, String packageName) {
        Snapshot next = new Snapshot(ringing, caller, label, packageName);
        Snapshot previous = current;
        if (previous.ringing == next.ringing
                && previous.caller.equals(next.caller)
                && previous.label.equals(next.label)
                && previous.packageName.equals(next.packageName)) {
            return;
        }
        current = next;
        for (Listener listener : LISTENERS) {
            listener.onCallChanged(next);
        }
    }

    static void clear() {
        set(false, "", "", "");
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }
}
