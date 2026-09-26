package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Page renumbering: every builder page action is built on these maps. */
public class DashboardPagesTest {

    @Test
    public void swapTradesTwoPagesAndLeavesTheRest() {
        assertArrayEquals(new int[]{0, 2, 1, 3}, DashboardPages.swap(3, 1, 2));
        assertArrayEquals(new int[]{0, 1, 3, 2}, DashboardPages.swap(3, 3, 2));
    }

    @Test
    public void swapOutOfRangeChangesNothing() {
        assertArrayEquals(new int[]{0, 1, 2}, DashboardPages.swap(2, 2, 3));
        assertArrayEquals(new int[]{0, 1, 2}, DashboardPages.swap(2, 0, 1));
    }

    @Test
    public void removeClosesTheGap() {
        int[] map = DashboardPages.remove(3, 2);
        assertArrayEquals(new int[]{0, 1, 0, 2}, map);
        assertEquals(2, DashboardPages.countAfter(map));
    }

    @Test
    public void theLastPageCannotBeRemoved() {
        assertArrayEquals(new int[]{0, 1}, DashboardPages.remove(1, 1));
    }

    @Test
    public void homeFollowsItsPageWhenItMoves() {
        assertEquals(1, DashboardPages.homeAfter(2, DashboardPages.swap(3, 1, 2)));
        assertEquals(3, DashboardPages.homeAfter(3, DashboardPages.swap(3, 1, 2)));
        assertEquals(2, DashboardPages.homeAfter(3, DashboardPages.remove(3, 1)));
    }

    @Test
    public void deletedHomePassesToThePageThatTookItsPlace() {
        assertEquals(2, DashboardPages.homeAfter(2, DashboardPages.remove(3, 2)));
        assertEquals(1, DashboardPages.homeAfter(1, DashboardPages.remove(2, 1)));
        // The last page has nothing after it, so the one before takes over.
        assertEquals(2, DashboardPages.homeAfter(3, DashboardPages.remove(3, 3)));
    }
}
