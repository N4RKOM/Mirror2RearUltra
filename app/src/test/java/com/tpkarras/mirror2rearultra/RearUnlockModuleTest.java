package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RearUnlockModuleTest {
    @Test
    public void aStampFromThisBootMeansLoaded() {
        assertEquals(RearUnlockModule.State.LOADED,
                RearUnlockModule.classify("5:46", 46, 5));
    }

    @Test
    public void aStampFromAnEarlierBootDoesNotCount() {
        assertEquals(RearUnlockModule.State.NOT_LOADED,
                RearUnlockModule.classify("5:45", 46, 5));
    }

    @Test
    public void aLoadedModuleOlderThanTheMinimumAsksForAnUpdate() {
        assertEquals(RearUnlockModule.State.OUTDATED,
                RearUnlockModule.classify("4:46", 46, 4));
    }

    @Test
    public void anInstalledModuleWithoutAStampIsNotLoadedYet() {
        assertEquals(RearUnlockModule.State.NOT_LOADED,
                RearUnlockModule.classify(null, 46, 5));
    }

    @Test
    public void anOldInstalledModuleWritesNoStampSoItIsOutdated() {
        assertEquals(RearUnlockModule.State.OUTDATED,
                RearUnlockModule.classify(null, 46, 4));
    }

    @Test
    public void noPackageAndNoStampMeansNotInstalled() {
        assertEquals(RearUnlockModule.State.NOT_INSTALLED,
                RearUnlockModule.classify(null, 46, -1));
    }

    @Test
    public void aStampStillCountsAfterTheUninstallWithinTheSameBoot() {
        assertEquals(RearUnlockModule.State.LOADED,
                RearUnlockModule.classify("5:46", 46, -1));
    }

    @Test
    public void garbageIsNotAStamp() {
        assertEquals(RearUnlockModule.State.NOT_LOADED,
                RearUnlockModule.classify("5:", 46, 5));
        assertEquals(RearUnlockModule.State.NOT_LOADED,
                RearUnlockModule.classify("x:y", 46, 5));
        assertEquals(RearUnlockModule.State.NOT_LOADED,
                RearUnlockModule.classify(":46", 46, 5));
    }

    @Test
    public void anUnknownBootCountNeverMatches() {
        assertEquals(RearUnlockModule.State.NOT_LOADED,
                RearUnlockModule.classify("5:-1", -1, 5));
    }
}
