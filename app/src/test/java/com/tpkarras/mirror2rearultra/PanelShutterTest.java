package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Finding a camera's shutter button in the accessibility dump of all windows.
 *
 * <p>The nodes are trimmed from a real dump of the phone: the camera's shutter
 * is an ImageButton that is not marked clickable (it takes touches through a
 * listener), a layer next to it has "capture" in its name, and the panel's own
 * window is in the same dump.
 */
public class PanelShutterTest {
    private static final String CAMERA = "com.shamim.cam";
    private static final String DUMP = String.join("\n",
            "<hierarchy rotation=\"0\">",
            "<window index=\"1\" title=\"Camera\" bounds=\"[0,0][1079,2399]\" active=\"true\">",
            node(CAMERA, "android.widget.FrameLayout", "com.shamim.cam:id/capture_overlay_layout",
                    "false", "[0,354][1080,1794]"),
            node(CAMERA, "android.widget.ImageButton", "com.shamim.cam:id/thumbnail_button",
                    "true", "[782,1834][1034,2086]"),
            node(CAMERA, "android.widget.ImageButton", "com.shamim.cam:id/shutter_button",
                    "false", "[406,1826][674,2094]"),
            "</window>",
            "<window index=\"0\" title=\"Mirror2RearUltra\" bounds=\"[0,0][294,126]\">",
            node("com.tpkarras.mirror2rearultra", "android.view.View",
                    "com.tpkarras.mirror2rearultra:id/shutter_flash", "true", "[0,0][294,126]"),
            "</window>",
            "</hierarchy>");

    private static String node(String pkg, String cls, String id, String clickable, String bounds) {
        return "<node index=\"0\" text=\"\" resource-id=\"" + id + "\" class=\"" + cls
                + "\" package=\"" + pkg + "\" content-desc=\"\" clickable=\"" + clickable
                + "\" enabled=\"true\" bounds=\"" + bounds + "\" />";
    }

    @Test
    public void theShutterIsFoundEvenWhenNotMarkedClickable() {
        assertArrayEquals(new int[] {540, 1960}, PanelShutter.shutterPointIn(DUMP, CAMERA));
    }

    @Test
    public void anAppWithNoShutterHasNoPoint() {
        assertNull(PanelShutter.shutterPointIn(DUMP, "com.google.android.youtube"));
    }

    @Test
    public void aPlainLayerWithTheWordInItsNameIsNotAShutter() {
        String onlyLayer = DUMP.replace("shutter_button", "preview_frame");
        assertNull(PanelShutter.shutterPointIn(onlyLayer, CAMERA));
    }

    @Test
    public void aButtonWithNoAreaIsNotPressed() {
        String empty = DUMP.replace("[406,1826][674,2094]", "[0,0][0,0]");
        assertNull(PanelShutter.shutterPointIn(empty, CAMERA));
    }
}
