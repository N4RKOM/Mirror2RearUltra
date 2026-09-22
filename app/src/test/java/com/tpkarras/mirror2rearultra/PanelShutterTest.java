package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Finding a camera's shutter button in the system's dump of what is on top.
 *
 * <p>The lines are a real dump from the phone, trimmed to the button and its
 * parents. Each view is placed within its parent, so the answer is only right
 * if every corner along the way is added up: the bottom bar at 0,1794, the row
 * inside it at 286,32, the holder at 120,0, and a 268-wide button at 0,0.
 */
public class PanelShutterTest {
    private static final String CAMERA = "com.shamim.cam";
    private static final String DUMP = String.join("\n",
            "  ACTIVITY com.shamim.cam/com.android.camera.CameraLauncher 2566cda pid=30940"
                    + " userId=0 uid=10295 displayId=0(type=INTERNAL)",
            "    View Hierarchy:",
            "      DecorView@5e30cb9[CameraLauncher]",
            "        android.widget.LinearLayout{b6a7e80 V.E...... ........ 0,0-1080,2400}",
            "          android.widget.FrameLayout{a1b0f03 V.E...... ........ 0,0-1080,2400}",
            "            android.support.v7.widget.FitWindowsLinearLayout{31753b2 V.E......"
                    + " ........ 0,0-1080,2400 #7f0b0039 app:id/action_bar_root}",
            "              android.support.v7.widget.ContentFrameLayout{62211bd V.E......"
                    + " ........ 0,0-1080,2400 #1020002 android:id/content}",
            "                com.google.android.apps.camera.ui.views.MainActivityLayout{5c62514"
                    + " V.E...... ........ 0,0-1080,2400 #7f0b0053 app:id/activity_root_view}",
            "                  com.google.android.apps.camera.bottombar.BottomBar{e515df0"
                    + " V.E...... ........ 0,1794-1080,2127 #7f0b007d app:id/bottom_bar}",
            "                    android.widget.RelativeLayout{b06691c V.E...... ........"
                    + " 286,32-795,300 #7f0b03b9 app:id/zoom_lock_view_parent}",
            "                      android.widget.FrameLayout{5b7d28f V.E...... ........"
                    + " 120,0-388,268 #7f0b00a9 app:id/center_placeholder}",
            "                        com.google.android.apps.camera.ui.shutterbutton"
                    + ".ShutterButton{79dfdee VFED..C.. ........ 0,0-268,268 #7f0b02a1"
                    + " app:id/shutter_button}",
            "  ACTIVITY com.tpkarras.mirror2rearultra/.Mirror 396c283 pid=11703 userId=0"
                    + " uid=10259 displayId=2(type=INTERNAL)",
            "    View Hierarchy:",
            "      DecorView@1234567[Mirror]",
            "        android.widget.FrameLayout{2222222 V.E...... ........ 0,0-126,294}",
            "          android.view.View{383237f V.ED..C.. ........ 0,0-126,294 #7f090240"
                    + " app:id/shutter_flash}");

    @Test
    public void theButtonIsWhereItsParentsPutIt() {
        assertArrayEquals(new int[] {540, 1960}, PanelShutter.shutterPointIn(DUMP, CAMERA));
    }

    @Test
    public void anAppWithNoShutterHasNoPoint() {
        assertNull(PanelShutter.shutterPointIn(DUMP, "com.google.android.youtube"));
    }

    @Test
    public void thePanelsOwnViewsAreNotMistakenForAShutter() {
        // The panel is in the same dump, it is on another display, and it has
        // a view whose name ends in the very word being looked for.
        assertNull(PanelShutter.shutterPointIn(DUMP, "com.tpkarras.mirror2rearultra"));
    }

    @Test
    public void aButtonThatIsNotOnScreenIsNotPressed() {
        // A gone view keeps its place in the dump and its name with it.
        String hidden = DUMP.replace("VFED..C..", "GFED..C..");
        assertNull(PanelShutter.shutterPointIn(hidden, CAMERA));
    }
}
