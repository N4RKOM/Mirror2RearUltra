package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The barometer's recent readings, kept for the trend. */
public class PressureHistoryTest {
    private static final long HOUR = 3_600_000L;
    private static final long NOW = 100 * HOUR;

    private static List<long[]> samples(long[]... pairs) {
        List<long[]> list = new ArrayList<>();
        for (long[] pair : pairs) list.add(pair);
        return list;
    }

    @Test
    public void storedSamplesComeBackAsTheyWent() {
        List<long[]> written = samples(new long[]{1L, 10132L}, new long[]{2L, 10128L});
        List<long[]> read = PressureHistory.parse(PressureHistory.format(written));
        assertEquals(2, read.size());
        assertEquals(10128L, read.get(1)[1]);
    }

    @Test
    public void aDamagedSampleIsDroppedAndTheRestKept() {
        assertEquals(1, PressureHistory.parse("1:10132,x:y,:5").size());
        assertEquals(0, PressureHistory.parse("").size());
    }

    @Test
    public void theTrendLooksBackThreeHours() {
        List<long[]> history = samples(
                new long[]{NOW - 4 * HOUR, 10000L},
                new long[]{NOW - 3 * HOUR - 10 * 60_000L, 10100L},
                new long[]{NOW - HOUR, 10200L});
        assertEquals(1010f, PressureHistory.threeHoursBefore(history, NOW), 0.01f);
    }

    @Test
    public void tooShortAHistoryHasNoTrendYet() {
        List<long[]> history = samples(new long[]{NOW - HOUR, 10200L});
        assertNull(PressureHistory.threeHoursBefore(history, NOW));
    }

    @Test
    public void onlyTheLastFourHoursAreKept() {
        List<long[]> history = samples(
                new long[]{NOW - 5 * HOUR, 1L},
                new long[]{NOW - 2 * HOUR, 2L});
        assertEquals(1, PressureHistory.trimmed(history, NOW).size());
    }
}
