package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

/** The registry has to know every widget exactly once. */
public class WidgetSpecsTest {
    @Test
    public void everyWidgetHasExactlyOneSpec() {
        Set<DashboardWidgetLayout.Widget> seen = EnumSet.noneOf(DashboardWidgetLayout.Widget.class);
        for (WidgetSpec spec : WidgetSpecs.all()) {
            assertTrue("two specs for " + spec.widget(), seen.add(spec.widget()));
        }
        assertEquals("a widget has no spec",
                EnumSet.allOf(DashboardWidgetLayout.Widget.class), seen);
    }

    @Test
    public void weatherSourcesAreDeclaredByEveryWidgetThatNeedsTheForecast() {
        EnumSet<DashboardWidgetLayout.Widget> wantsWeather = EnumSet.of(
                DashboardWidgetLayout.Widget.WEATHER,
                DashboardWidgetLayout.Widget.FULLSCREEN_WEATHER,
                DashboardWidgetLayout.Widget.SUN,
                DashboardWidgetLayout.Widget.UV_INDEX,
                DashboardWidgetLayout.Widget.AIR_QUALITY);
        for (WidgetSpec spec : WidgetSpecs.all()) {
            assertEquals(spec.widget().name(), wantsWeather.contains(spec.widget()),
                    spec.sources().contains(WidgetSpec.Source.WEATHER));
        }
    }
}
