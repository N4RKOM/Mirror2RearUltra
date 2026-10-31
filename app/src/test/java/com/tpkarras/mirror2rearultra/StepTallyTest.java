package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Today's steps, kept across a restart of the phone's own counter. */
public class StepTallyTest {
    @Test
    public void aNewDayStartsFromNothing() {
        assertEquals(0, StepTally.update(null, "d1", 5000).today);
        StepTally yesterday = StepTally.update(null, "d1", 5000);
        assertEquals(0, StepTally.update(yesterday, "d2", 7000).today);
    }

    @Test
    public void stepsAddUpWithinTheDay() {
        StepTally tally = StepTally.update(null, "d1", 5000);
        tally = StepTally.update(tally, "d1", 5300);
        assertEquals(300, tally.today);
    }

    @Test
    public void aRestartKeepsTheMorning() {
        StepTally tally = StepTally.update(null, "d1", 5000);
        tally = StepTally.update(tally, "d1", 5300);
        tally = StepTally.update(tally, "d1", 40);
        assertEquals(340, tally.today);
        tally = StepTally.update(tally, "d1", 100);
        assertEquals(400, tally.today);
    }
}
