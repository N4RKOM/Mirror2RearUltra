package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** A session says where it was and when; the panel carries it on from there. */
public class MediaProgressTest {
    private static MediaWidgetState.Snapshot playing(long position, long at, float speed,
            long duration) {
        return new MediaWidgetState.Snapshot("Track", "Artist", true, "com.example", null,
                position, at, speed, duration);
    }

    @Test
    public void carriesOnFromTheLastReport() {
        assertEquals(30_000L, playing(20_000L, 1_000L, 1f, 180_000L).positionAt(11_000L));
    }

    @Test
    public void followsTheSpeedTheSessionGave() {
        assertEquals(35_000L, playing(20_000L, 1_000L, 1.5f, 180_000L).positionAt(11_000L));
    }

    @Test
    public void stopsAtTheEndOfTheTrack() {
        assertEquals(180_000L, playing(170_000L, 1_000L, 1f, 180_000L).positionAt(9_999_000L));
    }

    @Test
    public void staysPutWhilePaused() {
        MediaWidgetState.Snapshot paused = new MediaWidgetState.Snapshot(
                "Track", "Artist", false, "com.example", null, 42_000L, 1_000L, 1f, 180_000L);
        assertEquals(42_000L, paused.positionAt(9_999_000L));
    }

    @Test
    public void holdsStillWhenTheSessionNeverSaidWhen() {
        assertEquals(42_000L, playing(42_000L, 0L, 1f, 180_000L).positionAt(9_999_000L));
    }

    @Test
    public void neverRunsBackwards() {
        // A reading from before the session's own - the two clocks need not be
        // read in that order.
        assertEquals(20_000L, playing(20_000L, 10_000L, 1f, 180_000L).positionAt(5_000L));
    }

    @Test
    public void getsThroughATrackOfUnknownLength() {
        assertEquals(30_000L, playing(20_000L, 1_000L, 1f, 0L).positionAt(11_000L));
    }
}
