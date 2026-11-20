package com.mackillergr.bf2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.*;
import android.widget.TextView;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;
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

    private static void write(File map, String path, byte[] content) throws Exception {
        File file = new File(map, path);
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), content);
    }
    private static void script(File map, String path, String text) throws Exception {
        write(map, path, text.getBytes(StandardCharsets.US_ASCII));
    }
    private static void waitForScan(LauncherActivity act) throws Exception {
        // Join the disk scan before driving its normal UI callback.
        java.lang.reflect.Field field = LauncherActivity.class.getDeclaredField("io");
        field.setAccessible(true);
        ((ExecutorService)field.get(act)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private static void navigation(File map) throws Exception {
        script(map, "AI/AI.ai", "ai.init 2\n");
        write(map, "AIPathFinding/Infantry.qtr", ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1).putInt(3).putFloat(-50).putFloat(-50).putFloat(50).putFloat(50).putFloat(100).putInt(1).array());
        write(map, "AIPathFinding/Infantry.clb", new byte[8]);
    }

    @Test public void botPackAndOriginalMapShareAChooser() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File mod = new File(app.getExternalFilesDir(null), "Game Files/mods/bf2");
        String packID = "openbf2_maps_0123456789abcdef0123456789abcdef";
        File pack = GameContent.mapPackDirectory(mod, packID);
        assertNull(GameContent.mapPackDirectory(mod, "../bf2"));
        String gameplay = "ObjectTemplate.create SpawnPoint alpha\nObject.create alpha\n";
        File original = new File(mod, "Levels/Gulf_of_Oman");
        script(original, "GameModes/gpm_cq/64/GamePlayObjects.con", gameplay);
        File added = new File(pack, "levels/gulf_of_oman");
        script(added, "GameModes/gpm_coop/64/GamePlayObjects.con", gameplay);
        script(added, "GameModes/gpm_coop/64/ai/StrategicAreas.ai",
                "aiStrategicArea.createFromControlPoint alpha 600 40\n");
        navigation(added);
        assertTrue(new File(mod, "ClientArchives.con").createNewFile());
        app.getSharedPreferences("openbf2", 0).edit().putString("mapPack", packID)
                .putString("map", "Gulf_of_Oman").commit();
        LauncherActivity act = Robolectric.buildActivity(LauncherActivity.class).setup().get();
        waitForScan(act);
        View main = act.findViewById(android.R.id.content);
        find(main, "Select map").performClick();
        AlertDialog maps = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(1, maps.getListView().getCount());
        maps.getListView().performItemClick(null, 0, 0);
        find(main, "Map size · 64").performClick();
        AlertDialog sizes = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(2, sizes.getListView().getCount());
        assertEquals("64-player layout  ·  Bots supported (map pack)", sizes.getListView().getItemAtPosition(0));
        assertEquals("64-player layout  ·  Solo exploration", sizes.getListView().getItemAtPosition(1));
        sizes.getListView().performItemClick(null, 0, 0);
        assertTrue(find(main, "Increase team 1 bots").isEnabled());
        assertEquals("pack/gpm_coop/64", app.getSharedPreferences("openbf2", 0).getString("layout.Gulf_of_Oman", ""));
        find(main, "Setup").performClick();
        assertNotNull(find(main, "Import bot map pack"));
    }
    @Test public void importedMapFactionsRosterAndBindingsReachable() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        File mod = new File(app.getExternalFilesDir(null), "Game Files/mods/bf2");
        File map = new File(mod, "Levels/test_map");
        String objects = "ObjectTemplate.create SpawnPoint base_spawn\nObject.create base_spawn\n";
        script(map, "GameModes/gpm_coop/16/GamePlayObjects.con", objects);
        script(map, "GameModes/gpm_coop/16/ai/StrategicAreas.ai", "aiStrategicArea.createFromControlPoint base 100 50\n");
        script(map, "GameModes/gpm_cq/64/GamePlayObjects.con", objects);
        navigation(map);
        assertTrue(new File(mod, "ServerArchives.con").createNewFile());
        assertTrue(new File(mod, "ClientArchives.con").createNewFile());
        Files.write(new File(map, "init.con").toPath(),
                "gameLogic.setTeamName 1 MEC\ngameLogic.setTeamName 2 US\n".getBytes(StandardCharsets.US_ASCII));
        LauncherActivity act = Robolectric.buildActivity(LauncherActivity.class).setup().get();
        waitForScan(act);
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
        find(main, "Map size · 16").performClick();
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("16-player layout  ·  Bots supported", dialog.getListView().getItemAtPosition(0));
        assertEquals("64-player layout  ·  Solo exploration", dialog.getListView().getItemAtPosition(1));
        dialog.getListView().performItemClick(null, 1, 1);
        assertFalse(find(main, "Increase team 1 bots").isEnabled());
        assertEquals("gpm_cq/64", act.getSharedPreferences("openbf2", 0).getString("layout.test_map", null));
        act.getApplicationInfo().nativeLibraryDir = new File(act.getFilesDir(), "lib").getPath();
        find(main, "Deploy").performClick();
        Intent deployment = Shadows.shadowOf(act).getNextStartedActivity();
        assertNotNull(deployment);
        List<String> args = Arrays.asList(deployment.getStringArrayExtra("args"));
        assertEquals("64", args.get(args.indexOf("--size") + 1));
        assertEquals("gpm_cq", args.get(args.indexOf("--mode") + 1));
        assertEquals("0", args.get(args.indexOf("--team1-bots") + 1));
        assertEquals("0", args.get(args.indexOf("--team2-bots") + 1));
        find(main, "Map size · 64").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 0, 0);
        assertTrue(find(main, "Decrease team 2 bots").isEnabled());
        assertNotNull(find(main, "16 bots  ·  1 human"));
        find(main, "Controls").performClick();
        find(main, "Controller bindings").performClick();
        assertEquals(ControllerBindingsActivity.class.getName(),
                Shadows.shadowOf(act).getNextStartedActivity().getComponent().getClassName());
    }
}
