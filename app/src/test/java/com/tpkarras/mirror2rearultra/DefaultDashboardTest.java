package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Set;

public class DefaultDashboardTest {
    private static DefaultDashboard.Data shipped() throws Exception {
        File asset = new File("src/main/assets/" + DefaultDashboard.ASSET);
        try (InputStream input = new FileInputStream(asset)) {
            return DefaultDashboard.read(input);
        }
    }

    @Test
    public void theShippedArrangementReads() throws Exception {
        DefaultDashboard.Data data = shipped();

        assertEquals(DashboardSettings.Layout.FREE, data.arrangement);
        assertTrue(data.layout.size() > 100);
        // A value that would not decode is refused on reading, so every one
        // here is one the layout's own importer understands.
        for (String value : data.layout.values()) {
            assertNotNull(DashboardWidgetLayout.decodeStateValue(value));
        }
    }

    @Test
    public void everyAlwaysAvailableWidgetHasItsSwitchSaid() throws Exception {
        DefaultDashboard.Data data = shipped();

        for (DashboardWidgetLayout.Widget widget : DashboardWidgetLayout.Widget.values()) {
            if (!DashboardWidgetLayout.isExtraWidget(widget)) {
                assertTrue(widget + " has no stored switch", data.visible.containsKey(widget));
            }
        }
        assertEquals(Boolean.TRUE, data.visible.get(DashboardWidgetLayout.Widget.CLOCK));
    }

    @Test
    public void theOrderNamesOnlyWidgetsThatExist() throws Exception {
        Object order = DashboardWidgetLayout.decodeStateValue(shipped().layout.get("order"));

        assertTrue(order instanceof String);
        for (String name : ((String) order).split(",")) {
            DashboardWidgetLayout.Widget.valueOf(name);
        }
    }

    @Test
    public void theExtraWidgetsThatAreOnExist() throws Exception {
        Object extras = DashboardWidgetLayout.decodeStateValue(
                shipped().layout.get("extra_enabled"));

        assertTrue(extras instanceof Set);
        for (Object name : (Set<?>) extras) {
            DashboardWidgetLayout.Widget.valueOf((String) name);
        }
    }
}
