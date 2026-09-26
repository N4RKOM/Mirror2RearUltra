package com.tpkarras.mirror2rearultra;

/**
 * How pages renumber when one is moved or deleted.
 *
 * <p>Every page operation in the builder comes down to the same question -
 * which page does each old page become - and the stored state (a page number
 * per widget, a layout and an orientation per page, the main page) is then
 * rewritten from one answer in {@link DashboardWidgetLayout#remapPages}. Kept
 * free of Android so the renumbering can be tested on its own.
 *
 * <p>A map is indexed by the old page number, from 1; slot 0 is unused. A
 * value of 0 means the page is gone.
 */
final class DashboardPages {
    private DashboardPages() {}

    /** Every page stays where it is. */
    static int[] identity(int count) {
        int[] map = new int[count + 1];
        for (int page = 1; page <= count; page++) {
            map[page] = page;
        }
        return map;
    }

    /** Two pages trade places; the rest stay put. */
    static int[] swap(int count, int first, int second) {
        int[] map = identity(count);
        if (inRange(count, first) && inRange(count, second)) {
            map[first] = second;
            map[second] = first;
        }
        return map;
    }

    /** One page goes, and the pages after it close the gap. */
    static int[] remove(int count, int removed) {
        int[] map = identity(count);
        if (!inRange(count, removed) || count <= 1) {
            return map;
        }
        map[removed] = 0;
        for (int page = removed + 1; page <= count; page++) {
            map[page] = page - 1;
        }
        return map;
    }

    /** How many pages a map leaves. */
    static int countAfter(int[] map) {
        int count = 0;
        for (int page = 1; page < map.length; page++) {
            count = Math.max(count, map[page]);
        }
        return Math.max(1, count);
    }

    /**
     * Where the main page ends up.
     *
     * <p>It follows its page when that page moves. When its page is deleted it
     * passes to whichever page slid into the same place, or the last one - not
     * back to page one, which would jump the panel somewhere unrelated to what
     * the user was just looking at.
     */
    static int homeAfter(int home, int[] map) {
        int count = countAfter(map);
        if (home >= 1 && home < map.length && map[home] > 0) {
            return map[home];
        }
        return Math.max(1, Math.min(home, count));
    }

    /**
     * The page automatic cycling shows after a number of steps.
     *
     * <p>Cycling moves on from the main page through the pages that take part,
     * in order, and wraps. A page can be left out - reached by a swipe but
     * never turned to by itself - and so can a page with nothing on it. The
     * main page rests first even when it is left out; it is where the panel
     * sits, not a stop on the round.
     *
     * @param inCycle indexed by page from 1; slot 0 unused
     * @param steps how many turns since cycling resumed, from 0
     */
    static int autoPage(int home, long steps, boolean[] inCycle) {
        int[] stops = new int[inCycle.length];
        int count = 0;
        for (int page = 1; page < inCycle.length; page++) {
            if (inCycle[page]) {
                stops[count++] = page;
            }
        }
        if (count == 0) {
            return home;
        }
        // The first stop after the main page, wrapping to the first stop.
        int start = 0;
        for (int index = 0; index < count; index++) {
            if (stops[index] > home) {
                start = index;
                break;
            }
        }
        return stops[(int) ((start + Math.max(0L, steps)) % count)];
    }

    private static boolean inRange(int count, int page) {
        return page >= 1 && page <= count;
    }
}
