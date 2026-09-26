package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * How the panel's own settings file is written into a backup.
 *
 * <p>The export and import themselves need SharedPreferences, which these
 * plain JVM tests do not have, so the value encoding they share is tested.
 */
public class DashboardLayoutStateTest {

    private static Object roundTrip(Object value) {
        return DashboardWidgetLayout.decodeStateValue(
                DashboardWidgetLayout.encodeStateValue(value));
    }

    @Test
    public void switchesSurviveABackup() {
        assertEquals(Boolean.TRUE, roundTrip(true));
        assertEquals(Boolean.FALSE, roundTrip(false));
        assertEquals("b:true", DashboardWidgetLayout.encodeStateValue(true));
    }

    @Test
    public void theOtherTypesStillRoundTrip() {
        assertEquals(3, roundTrip(3));
        assertEquals("FREE", roundTrip("FREE"));
        assertEquals("", roundTrip(""));
        Set<String> set = new HashSet<>(Arrays.asList("TIMER", "STEPS"));
        assertEquals(set, roundTrip(set));
        assertEquals(new HashSet<String>(), roundTrip(new HashSet<String>()));
    }

    @Test
    public void unknownTypesAndTextAreLeftOut() {
        assertNull(DashboardWidgetLayout.encodeStateValue(1.5f));
        assertNull(DashboardWidgetLayout.decodeStateValue("x:1"));
        assertNull(DashboardWidgetLayout.decodeStateValue("i:not a number"));
    }
}
