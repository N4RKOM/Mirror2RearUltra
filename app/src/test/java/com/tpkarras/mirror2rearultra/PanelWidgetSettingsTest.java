package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;
import android.media.AudioManager;

import org.junit.Test;

import java.util.List;
import java.util.TimeZone;

/** The sound mode, second time zone and QR code widgets' own logic. */
public class PanelWidgetSettingsTest {
    // Sound mode

    @Test
    public void doNotDisturbOutranksARingingPhone() {
        assertEquals(SoundModeReading.Mode.DO_NOT_DISTURB, SoundModeReading.modeOf(
                AudioManager.RINGER_MODE_NORMAL, NotificationManager.INTERRUPTION_FILTER_PRIORITY));
        assertEquals(SoundModeReading.Mode.DO_NOT_DISTURB, SoundModeReading.modeOf(
                AudioManager.RINGER_MODE_NORMAL, NotificationManager.INTERRUPTION_FILTER_NONE));
    }

    @Test
    public void withoutItTheRingerDecides() {
        int all = NotificationManager.INTERRUPTION_FILTER_ALL;
        assertEquals(SoundModeReading.Mode.SOUND,
                SoundModeReading.modeOf(AudioManager.RINGER_MODE_NORMAL, all));
        assertEquals(SoundModeReading.Mode.VIBRATE,
                SoundModeReading.modeOf(AudioManager.RINGER_MODE_VIBRATE, all));
        assertEquals(SoundModeReading.Mode.SILENT,
                SoundModeReading.modeOf(AudioManager.RINGER_MODE_SILENT, all));
    }

    @Test
    public void anUnknownFilterIsNotDoNotDisturb() {
        assertEquals(SoundModeReading.Mode.SOUND, SoundModeReading.modeOf(
                AudioManager.RINGER_MODE_NORMAL, NotificationManager.INTERRUPTION_FILTER_UNKNOWN));
    }

    @Test
    public void volumeIsAShareOfTheStreamsMaximum() {
        assertEquals(60, SoundModeReading.percentOf(9, 15));
        assertEquals(0, SoundModeReading.percentOf(3, 0));
        assertEquals(100, SoundModeReading.percentOf(20, 15));
    }

    // Second time zone

    @Test
    public void differencesAreHoursWhereTheyCan() {
        assertEquals("0", WorldClock.differenceLabel(0));
        assertEquals("+6", WorldClock.differenceLabel(360));
        assertEquals("−3", WorldClock.differenceLabel(-180));
        assertEquals("+5:30", WorldClock.differenceLabel(330));
        assertEquals("−2:30", WorldClock.differenceLabel(-150));
    }

    @Test
    public void utcItselfIsJustUtc() {
        assertEquals("UTC", WorldClock.utcLabel(0));
        assertEquals("UTC+5:45", WorldClock.utcLabel(345));
    }

    @Test
    public void aZoneWithoutACityFallsBackToItsName() {
        assertEquals("Buenos Aires", WorldClock.fallbackName("America/Argentina/Buenos_Aires"));
        assertEquals("UTC", WorldClock.fallbackName("UTC"));
    }

    @Test
    public void theListRunsWestToEast() {
        long now = 1_790_473_500_000L;
        List<String> zones = WorldClock.sortedZones(now);
        assertTrue(zones.contains(WorldClock.DEFAULT_ZONE));
        for (int index = 1; index < zones.size(); index++) {
            assertTrue(TimeZone.getTimeZone(zones.get(index - 1)).getOffset(now)
                    <= TimeZone.getTimeZone(zones.get(index)).getOffset(now));
        }
    }

    // QR code

    @Test
    public void aNetworkWithAPasswordIsWpa() {
        assertEquals("WIFI:T:WPA;S:Home;P:secret;;", QrContent.wifi("Home", "secret"));
    }

    @Test
    public void aNetworkWithoutOneIsOpen() {
        assertEquals("WIFI:T:nopass;S:Cafe;;", QrContent.wifi("Cafe", ""));
    }

    @Test
    public void separatorsInsideAFieldAreEscaped() {
        assertEquals("WIFI:T:WPA;S:a\\;b;P:c\\:d\\\\e;;", QrContent.wifi("a;b", "c:d\\e"));
    }

    @Test
    public void aWifiCodeIsSmallEnoughForThePanel() {
        boolean[][] modules = QrContent.encode(QrContent.wifi("Home-5G", "correcthorse"));
        assertNotNull(modules);
        // Version 2 or 3: 25 or 29 modules a side, three pixels each on a
        // 126 pixel panel with the margin.
        assertTrue(modules.length <= 29);
        assertEquals(modules.length, modules[0].length);
        // The finder pattern's corner is dark.
        assertTrue(modules[0][0]);
        assertFalse(modules[1][1]);
    }

    @Test
    public void nothingToEncodeIsNoCode() {
        assertNull(QrContent.encode(""));
    }
}
