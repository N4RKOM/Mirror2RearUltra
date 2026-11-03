package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which release counts as an update, which file in it is the app, and how its notes read. */
public class UpdateCheckerTest {

    @Test
    public void newerPatchMinorAndMajorAreUpdates() {
        assertTrue(UpdateChecker.isNewer("1.29.1", "v1.29.2"));
        assertTrue(UpdateChecker.isNewer("1.29.1", "v1.30.0"));
        assertTrue(UpdateChecker.isNewer("1.29.1", "v2.0.0"));
    }

    @Test
    public void numbersAreComparedAsNumbersNotText() {
        assertTrue(UpdateChecker.isNewer("1.9.0", "v1.10.0"));
        assertFalse(UpdateChecker.isNewer("1.10.0", "v1.9.0"));
    }

    @Test
    public void sameOrOlderIsNotAnUpdate() {
        assertFalse(UpdateChecker.isNewer("1.29.1", "v1.29.1"));
        assertFalse(UpdateChecker.isNewer("1.29.1", "v1.29.0"));
        assertFalse(UpdateChecker.isNewer("1.30.0", "v1.29.9"));
    }

    @Test
    public void missingPartsCountAsZero() {
        assertFalse(UpdateChecker.isNewer("1.29.0", "v1.29"));
        assertTrue(UpdateChecker.isNewer("1.29", "v1.29.1"));
    }

    @Test
    public void suffixesAreIgnored() {
        assertFalse(UpdateChecker.isNewer("1.29.1", "v1.29.1-beta"));
        assertTrue(UpdateChecker.isNewer("1.29.1", "v1.30.0+build5"));
    }

    @Test
    public void unreadableVersionsAreNeverUpdates() {
        assertFalse(UpdateChecker.isNewer("1.29.1", "latest"));
        assertFalse(UpdateChecker.isNewer("1.29.1", ""));
        assertFalse(UpdateChecker.isNewer("1.29.1", "v1..2"));
        assertFalse(UpdateChecker.isNewer("1.29.1", null));
        assertFalse(UpdateChecker.isNewer(null, "v9.9.9"));
    }

    @Test
    public void pickApkSkipsTheModuleAndForeignLinks() {
        String[] names = {"Mirror2RearUltra-RearUnlock-module.apk", "notes.txt",
                "Mirror2RearUltra-v1.30.0.apk"};
        String[] urls = {"https://github.com/o/r/releases/download/v1.30.0/Mirror2RearUltra-RearUnlock-module.apk",
                "https://github.com/o/r/releases/download/v1.30.0/notes.txt",
                "https://github.com/o/r/releases/download/v1.30.0/Mirror2RearUltra-v1.30.0.apk"};
        assertEquals(2, UpdateChecker.pickApk(names, urls));
    }

    @Test
    public void pickApkRefusesLinksOutsideGithub() {
        String[] names = {"Mirror2RearUltra-v1.30.0.apk"};
        String[] urls = {"https://evil.example/Mirror2RearUltra-v1.30.0.apk"};
        assertEquals(-1, UpdateChecker.pickApk(names, urls));
    }

    @Test
    public void pickApkWithoutAnApkIsMinusOne() {
        assertEquals(-1, UpdateChecker.pickApk(new String[0], new String[0]));
    }

    @Test
    public void sha256ComesOutOfGithubDigest() {
        String hex = "39f46115878226987ee6d9fc28afc92a4a1a3c6fe1aea19b2dc2ce5596723558";
        assertEquals(hex, UpdateChecker.sha256Of("sha256:" + hex));
        assertEquals(hex, UpdateChecker.sha256Of("sha256:" + hex.toUpperCase()));
    }

    @Test
    public void unusableDigestsAreNull() {
        assertNull(UpdateChecker.sha256Of(null));
        assertNull(UpdateChecker.sha256Of(""));
        assertNull(UpdateChecker.sha256Of("md5:abcdef"));
        assertNull(UpdateChecker.sha256Of("sha256:abc"));
    }

    @Test
    public void notesLoseTheirMarkdown() {
        String body = "## What's Changed\n"
                + "* Rework the weather by @user in https://github.com/o/r/pull/5\n"
                + "- Add **update** check with `sheet`\n"
                + "\n"
                + "[Docs](https://example.com) are here\n"
                + "\n"
                + "**Full Changelog**: https://github.com/o/r/compare/v1...v2";
        assertEquals("What's Changed\n"
                + "• Rework the weather by @user in https://github.com/o/r/pull/5\n"
                + "• Add update check with sheet\n"
                + "\n"
                + "Docs are here", UpdateChecker.cleanNotes(body));
    }

    @Test
    public void aBodyWithOnlyTheCompareLinkHasNoNotes() {
        assertEquals("", UpdateChecker.cleanNotes("**Full Changelog**: https://github.com/o/r/commits/v1.29.1"));
        assertEquals("", UpdateChecker.cleanNotes(null));
        assertEquals("", UpdateChecker.cleanNotes("  \n\n"));
    }

    @Test
    public void longNotesAreCut() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            body.append("- change number ").append(i).append('\n');
        }
        String notes = UpdateChecker.cleanNotes(body.toString());
        assertTrue(notes.length() <= 4001);
        assertTrue(notes.endsWith("…"));
    }

    @Test
    public void versionDropsTheLeadingV() {
        UpdateChecker.Release release = new UpdateChecker.Release(
                "v1.30.0", "https://github.com/o/r", "", null, 0, null);
        assertEquals("1.30.0", release.version());
    }
}
