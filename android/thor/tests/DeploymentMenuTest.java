package com.mackillergr.bf2;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.view.*;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, qualifiers = "w850dp-h480dp-land")
public class DeploymentMenuTest {
    private View find(View node, String value) {
        if (node instanceof TextView && value.contentEquals(((TextView)node).getText())) return node;
        if (node.getContentDescription() != null && value.contentEquals(node.getContentDescription())) return node;
        if (node instanceof ViewGroup) {
            ViewGroup box = (ViewGroup)node;
            for (int i = 0; i < box.getChildCount(); i++) {
                View match = find(box.getChildAt(i), value);
                if (match != null) return match;
            }
        }
        return null;
    }

    @Test public void importedMapFactionsRosterAndBindingsReachable() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File mod = new File(app.getExternalFilesDir(null), "Game Files/mods/bf2");
        File map = new File(mod, "Levels/test_map");
        File layout = new File(map, "GameModes/gpm_coop/16/GamePlayObjects.con");
        assertTrue(layout.getParentFile().mkdirs());
        assertTrue(layout.createNewFile());
        assertTrue(new File(mod, "ClientArchives.con").createNewFile());
        Files.write(new File(map, "init.con").toPath(),
                "gameLogic.setTeamName 1 MEC\ngameLogic.setTeamName 2 US\n".getBytes(StandardCharsets.US_ASCII));
        LauncherActivity act = Robolectric.buildActivity(LauncherActivity.class).setup().get();
        // Join the disk scan before driving its normal UI callback.
        java.lang.reflect.Field field = LauncherActivity.class.getDeclaredField("io");
        field.setAccessible(true);
        ((ExecutorService)field.get(act)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View main = act.findViewById(android.R.id.content);
        assertNotNull(find(main, "MEC"));
        assertNotNull(find(main, "US"));
        find(main, "Select map").performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("Test map", dialog.getListView().getItemAtPosition(0));
        dialog.getListView().performItemClick(null, 0, 0);
        find(main, "Increase team 1 bots").performClick();
        find(main, "Decrease team 2 bots").performClick();
        assertEquals(9, act.getSharedPreferences("openbf2", 0).getInt("bots1", 0));
        assertEquals(7, act.getSharedPreferences("openbf2", 0).getInt("bots2", 0));
        find(main, "Controls").performClick();
        find(main, "Controller bindings").performClick();
        assertEquals(ControllerBindingsActivity.class.getName(),
                Shadows.shadowOf(act).getNextStartedActivity().getComponent().getClassName());
    }
}
