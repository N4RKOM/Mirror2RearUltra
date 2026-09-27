package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The window is the one piece of trigger logic that is easy to get wrong. */
public class PanelTriggersTest {

    private static int at(int hour, int minute) {
        return hour * 60 + minute;
    }

    @Test
    public void daytimeWindowHoldsBetweenItsEnds() {
        int from = at(9, 0);
        int to = at(17, 30);
        assertFalse(PanelTriggers.within(at(8, 59), from, to));
        assertTrue(PanelTriggers.within(at(9, 0), from, to));
        assertTrue(PanelTriggers.within(at(17, 29), from, to));
        assertFalse(PanelTriggers.within(at(17, 30), from, to));
    }

    @Test
    public void nightWindowRunsPastMidnight() {
        int from = at(22, 0);
        int to = at(7, 0);
        assertTrue(PanelTriggers.within(at(22, 0), from, to));
        assertTrue(PanelTriggers.within(at(23, 59), from, to));
        assertTrue(PanelTriggers.within(at(0, 0), from, to));
        assertTrue(PanelTriggers.within(at(3, 39), from, to));
        assertTrue(PanelTriggers.within(at(6, 59), from, to));
        assertFalse(PanelTriggers.within(at(7, 0), from, to));
        assertFalse(PanelTriggers.within(at(12, 0), from, to));
        assertFalse(PanelTriggers.within(at(21, 59), from, to));
    }

    @Test
    public void equalEndsAreNoWindowRatherThanTheWholeDay() {
        int both = at(8, 0);
        assertFalse(PanelTriggers.within(at(8, 0), both, both));
        assertFalse(PanelTriggers.within(at(20, 0), both, both));
    }

    @Test
    public void aPageSlotNamesItsPage() {
        assertEquals(3, PanelTriggers.pageOf(PanelTriggers.pageSlot(3)));
    }

    @Test
    public void templateSlotsAndNonsenseNameNoPage() {
        assertEquals(0, PanelTriggers.pageOf(null));
        assertEquals(0, PanelTriggers.pageOf("T_1789707125"));
        assertEquals(0, PanelTriggers.pageOf("page:"));
        assertEquals(0, PanelTriggers.pageOf("page:x"));
        assertEquals(0, PanelTriggers.pageOf("page:-2"));
    }

    @Test
    public void theFirstThatHoldsWinsInOrder() {
        // Bluetooth, charger, Wi-Fi, clock.
        assertEquals("car", PanelTriggers.pick(new String[]{"car", "charge", "home", "night"}));
        assertEquals("charge", PanelTriggers.pick(new String[]{null, "charge", "home", "night"}));
        assertEquals("home", PanelTriggers.pick(new String[]{null, null, "home", "night"}));
        assertEquals(null, PanelTriggers.pick(new String[]{null, null, null, null}));
    }

    @Test
    public void aChosenDeviceMustBeTheOneConnected() {
        java.util.Set<String> connected = new java.util.HashSet<>(
                java.util.Arrays.asList("AA:BB", "CC:DD"));
        assertTrue(PanelTriggers.bluetoothMatches("CC:DD", connected));
        assertFalse(PanelTriggers.bluetoothMatches("EE:FF", connected));
    }

    @Test
    public void anyDeviceNeedsOneToBeConnected() {
        assertTrue(PanelTriggers.bluetoothMatches("",
                java.util.Collections.singleton("AA:BB")));
        assertFalse(PanelTriggers.bluetoothMatches("", java.util.Collections.emptySet()));
    }

    @Test
    public void aChosenNetworkMustBeTheOneJoined() {
        assertTrue(PanelTriggers.wifiMatches("Home", true, "Home"));
        assertFalse(PanelTriggers.wifiMatches("Home", true, "Cafe"));
        // On Wi-Fi without leave to know which: not the chosen one.
        assertFalse(PanelTriggers.wifiMatches("Home", true, null));
        assertFalse(PanelTriggers.wifiMatches("Home", false, null));
    }

    @Test
    public void anyNetworkNeedsOnlyWifi() {
        assertTrue(PanelTriggers.wifiMatches("", true, null));
        assertFalse(PanelTriggers.wifiMatches("", false, null));
    }

    @Test
    public void networkNamesLoseTheirQuotes() {
        assertEquals("Home", PanelSurroundings.cleanNetworkName("\"Home\""));
        assertEquals(null, PanelSurroundings.cleanNetworkName("<unknown ssid>"));
        assertEquals(null, PanelSurroundings.cleanNetworkName(""));
    }
}
