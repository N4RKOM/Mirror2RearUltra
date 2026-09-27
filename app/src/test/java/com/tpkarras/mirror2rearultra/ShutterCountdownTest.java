package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The countdown before a shot taken from the panel. */
public class ShutterCountdownTest {
    @Test
    public void theFigureRoundsUpSoTheShotComesAsTheOneGoes() {
        assertEquals(3, ShutterCountdown.secondsShown(3_000L));
        assertEquals(3, ShutterCountdown.secondsShown(2_001L));
        assertEquals(1, ShutterCountdown.secondsShown(1L));
        assertEquals(0, ShutterCountdown.secondsShown(0L));
        assertEquals(0, ShutterCountdown.secondsShown(-40L));
    }

    @Test
    public void eachTickLandsOnAWholeSecond() {
        assertEquals(1_000L, ShutterCountdown.untilNextSecond(3_000L));
        assertEquals(250L, ShutterCountdown.untilNextSecond(2_250L));
    }

    @Test
    public void theFigureTurnsLikeTheDashboardsPages() {
        assertEquals(0f, ShutterCountdown.rotationFor(0), 0f);
        assertEquals(90f, ShutterCountdown.rotationFor(1), 0f);
        assertEquals(270f, ShutterCountdown.rotationFor(3), 0f);
        assertEquals(0f, ShutterCountdown.rotationFor(PanelPostureSensor.TURN_UNKNOWN), 0f);
    }

    @Test
    public void onlyTheOfferedLengthsAreKept() {
        assertEquals(5, ShutterCountdown.normalized(5));
        assertEquals(0, ShutterCountdown.normalized(60));
        assertEquals(0, ShutterCountdown.normalized(-3));
    }
}
