package com.mackillergr.bf2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/** Real launcher UI and publication paths; minimal map files are discovery fixtures only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = "src/main/AndroidManifest.xml", qualifiers = "w850dp-h480dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
public final class TicketDeploymentTest {
    private ActivityController<LauncherActivity> controller;
    private LauncherActivity activity;
    private SharedPreferences prefs;

    private static View find(View view, String label) {
        if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof TextView && label.contentEquals(((TextView)view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static View required(View root, String label) {
        View found = find(root, label);
        assertNotNull("Missing control: " + label, found);
        return found;
    }
    private View root() { return activity.findViewById(android.R.id.content); }
    private void click(String label) { required(root(), label).performClick(); }
    private static void write(File base, String relative, byte[] data) throws Exception {
        File file = new File(base, relative);
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), data);
    }
    private static void script(File base, String relative, String text) throws Exception {
        write(base, relative, text.getBytes(StandardCharsets.US_ASCII));
    }
    private static void map(File base, String name) throws Exception {
        File map = new File(base, "Levels/" + name);
        String objects = "ObjectTemplate.create SpawnPoint flag\nObject.create flag\n";
        for (int size : new int[]{16, 32}) {
            script(map, "GameModes/gpm_coop/" + size + "/GamePlayObjects.con", objects);
            script(map, "GameModes/gpm_coop/" + size + "/AI/StrategicAreas.ai",
                    "aiStrategicArea.createFromControlPoint flag 100 50\n");
        }
        script(map, "GameModes/gpm_cq/64/GamePlayObjects.con", objects);
        script(map, "AI/AI.ai", "ai.init 2\n");
        write(map, "AIPathFinding/Infantry.qtr", ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1).putInt(3).putFloat(-50).putFloat(-50).putFloat(50).putFloat(50).putFloat(100).putInt(1).array());
        write(map, "AIPathFinding/Infantry.clb", new byte[8]);
        script(map, "init.con", "gameLogic.setTeamName 1 MEC\ngameLogic.setTeamName 2 US\n");
    }
    private void start() throws Exception {
        controller = Robolectric.buildActivity(LauncherActivity.class).setup();
        activity = controller.get();
        // Register the real packaged Python asset; this fixture does not replace OfflineCareer.prepare.
        java.lang.reflect.Method assets = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
        assets.setAccessible(true);
        assets.invoke(activity.getAssets(), new File("career-assets.apk").getAbsolutePath());
        try (java.io.InputStream script = activity.getAssets().open("bf2_offline_career.py")) {
            assertTrue("Career asset must be available for the real offline launch path", script.read() >= 0);
        }
        waitForIo();
    }
    private void waitForIo() throws Exception {
        java.lang.reflect.Field field = LauncherActivity.class.getDeclaredField("io");
        field.setAccessible(true);
        ((ExecutorService)field.get(activity)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Before public void setup() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("openbf2", 0);
        prefs.edit().clear().putString("map", "Alpha_Map").commit();
        File base = new File(app.getExternalFilesDir(null), "Game Files/mods/bf2");
        script(base, "ClientArchives.con", "");
        script(base, "ServerArchives.con", "");
        map(base, "Alpha_Map");
        map(base, "bravo_map");
        File libs = new File(app.getFilesDir(), "native-fixture");
        write(libs, "libaidll.so", new byte[]{0}); // Existence check only; no native image is loaded.
        app.getApplicationInfo().nativeLibraryDir = libs.getAbsolutePath();
        start();
    }
    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
        GameSession.release();
    }
    private AlertDialog tickets() {
        click("Starting tickets");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        return dialog;
    }
    private static EditText team(AlertDialog dialog, int team) {
        return (EditText)required(dialog.getWindow().getDecorView(), "Team " + team + " starting tickets");
    }
    private void save(String first, String second) {
        AlertDialog dialog = tickets();
        team(dialog, 1).setText(first); team(dialog, 2).setText(second);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse("Valid values should close the dialog", dialog.isShowing());
    }
    private void mapChoice(String title) {
        click("Select map");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        for (int i = 0; i < dialog.getListView().getCount(); i++) {
            if (title.equalsIgnoreCase(dialog.getListView().getItemAtPosition(i).toString())) {
                dialog.getListView().performItemClick(null, i, i); return;
            }
        }
        throw new AssertionError("Map not found: " + title);
    }
    private void sizeChoice(int current, String target) {
        click("Map size · " + current);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        for (int i = 0; i < dialog.getListView().getCount(); i++) {
            if (target.equals(dialog.getListView().getItemAtPosition(i).toString())) {
                dialog.getListView().performItemClick(null, i, i); return;
            }
        }
        throw new AssertionError("Layout not found: " + target);
    }
    private List<String> deploy(String label) {
        click(label);
        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        String message = "";
        try {
            java.lang.reflect.Field field = LauncherActivity.class.getDeclaredField("status");
            field.setAccessible(true);
            message = ((TextView)field.get(activity)).getText().toString();
        } catch (ReflectiveOperationException ignored) { }
        assertNotNull("No launch after " + label + ": " + message, intent);
        String[] args = intent.getStringArrayExtra("args");
        assertNotNull("Expected a new launch, not a resume", args);
        return Arrays.asList(args);
    }
    private static String flag(List<String> args, String name) {
        return args.contains(name) ? args.get(args.indexOf(name) + 1) : null;
    }

    @Test public void mapDefaultsRemainAbsentFromOfflineArguments() {
        AlertDialog dialog = tickets();
        assertEquals("", team(dialog, 1).getText().toString());
        assertEquals("", team(dialog, 2).getText().toString());
        assertEquals("Map default", team(dialog, 1).getHint().toString());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        List<String> args = deploy("Deploy");
        assertTrue(args.contains("--coop"));
        assertNull(flag(args, "--tickets-team1")); assertNull(flag(args, "--tickets-team2"));
    }

    @Test public void independentExactBoundsReachOfflineLaunch() {
        save("1", "9999");
        assertEquals(1, prefs.getInt("tickets.alpha_map.team1", 0));
        assertEquals(9999, prefs.getInt("tickets.alpha_map.team2", 0));
        List<String> args = deploy("Deploy");
        assertEquals("1", flag(args, "--tickets-team1"));
        assertEquals("9999", flag(args, "--tickets-team2"));
        assertEquals("8", flag(args, "--team1-bots"));
    }

    @Test public void mapSettingsFollowMapAcrossSizesAndActivityRecreation() throws Exception {
        save("650", "900");
        sizeChoice(16, "32-player layout  ·  Bots supported");
        AlertDialog dialog = tickets();
        assertEquals("650", team(dialog, 1).getText().toString());
        assertEquals("900", team(dialog, 2).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        mapChoice("Bravo map");
        dialog = tickets();
        assertEquals("", team(dialog, 1).getText().toString());
        assertEquals("", team(dialog, 2).getText().toString());
        team(dialog, 1).setText("1250");
        required(dialog.getWindow().getDecorView(), "Set both from team 1").performClick();
        assertEquals("1250", team(dialog, 2).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        controller.pause().stop().destroy(); controller = null;
        start();
        dialog = tickets();
        assertEquals("1250", team(dialog, 1).getText().toString());
        assertEquals("1250", team(dialog, 2).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        mapChoice("Alpha map");
        dialog = tickets();
        assertEquals("650", team(dialog, 1).getText().toString());
        assertEquals("900", team(dialog, 2).getText().toString());
    }

    @Test public void invalidSaveStaysOpenAndPreservesBothPreviousValues() {
        save("250", "500");
        AlertDialog dialog = tickets();
        team(dialog, 1).setText("0"); team(dialog, 2).setText("10000");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing());
        assertNotNull(team(dialog, 1).getError());
        assertEquals(250, prefs.getInt("tickets.alpha_map.team1", 0));
        assertEquals(500, prefs.getInt("tickets.alpha_map.team2", 0));
        team(dialog, 1).setText("100");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing()); assertNotNull(team(dialog, 2).getError());
        assertEquals(250, prefs.getInt("tickets.alpha_map.team1", 0));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        dialog = tickets();
        assertEquals("250", team(dialog, 1).getText().toString());
        assertEquals("500", team(dialog, 2).getText().toString());
    }

    @Test public void individualAndWholeMapDefaultsRemoveOnlySelectedMapOverrides() {
        save("350", "700");
        mapChoice("Bravo map"); save("900", "1100"); mapChoice("Alpha map");
        AlertDialog dialog = tickets();
        required(dialog.getWindow().getDecorView(), "Use map default for team 1").performClick();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(prefs.contains("tickets.alpha_map.team1"));
        assertEquals(700, prefs.getInt("tickets.alpha_map.team2", 0));
        dialog = tickets();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(prefs.contains("tickets.alpha_map.team1"));
        assertFalse(prefs.contains("tickets.alpha_map.team2"));
        assertEquals(900, prefs.getInt("tickets.bravo_map.team1", 0));
        assertEquals(1100, prefs.getInt("tickets.bravo_map.team2", 0));
        List<String> args = deploy("Deploy");
        assertNull(flag(args, "--tickets-team1")); assertNull(flag(args, "--tickets-team2"));
    }

    @Test public void zeroBotSoloLayoutRetainsIndependentOverride() {
        save("", "1725");
        sizeChoice(16, "64-player layout  ·  Solo exploration");
        List<String> args = deploy("Deploy");
        assertEquals("gpm_cq", flag(args, "--mode"));
        assertEquals("0", flag(args, "--team1-bots")); assertEquals("0", flag(args, "--team2-bots"));
        assertNull(flag(args, "--tickets-team1")); assertEquals("1725", flag(args, "--tickets-team2"));
    }

    @Test public void menuAndLanHostingDoNotInheritOfflineTicketSettings() throws Exception {
        save("1000", "1500");
        List<String> menu = deploy("Battlefield 2 main menu");
        assertNull(flag(menu, "--tickets-team1")); assertNull(flag(menu, "--tickets-team2"));
        controller.pause().resume();
        click("LAN"); click("Host LAN Conquest");
        waitForIo();
        AlertDialog maps = ShadowAlertDialog.getLatestAlertDialog();
        maps.getListView().performItemClick(null, 0, 0);
        Intent host = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(host);
        List<String> args = Arrays.asList(host.getStringArrayExtra("args"));
        assertTrue(args.contains("--hosted")); assertFalse(args.contains("--coop"));
        assertNull(flag(args, "--tickets-team1")); assertNull(flag(args, "--tickets-team2"));
    }

    @Test public void malformedTypedValuesAndCorruptPreferencesCannotBecomeOverrides() {
        for (String invalid : new String[]{"-1", "+100", "1.5", "1e3", "abc", "99999999999999999999999999"}) {
            try {
                OfflineTickets.parse(invalid);
                fail("Accepted malformed ticket input: " + invalid);
            } catch (IllegalArgumentException expected) { }
        }
        prefs.edit().putString("tickets.alpha_map.team1", "300").putInt("tickets.alpha_map.team2", -4).commit();
        AlertDialog dialog = tickets();
        assertEquals("", team(dialog, 1).getText().toString());
        assertEquals("", team(dialog, 2).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        prefs.edit().putInt("tickets.alpha_map.team1", 10000).putInt("tickets.alpha_map.team2", Integer.MAX_VALUE).commit();
        dialog = tickets();
        assertEquals("", team(dialog, 1).getText().toString());
        assertEquals("", team(dialog, 2).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        List<String> args = deploy("Deploy");
        assertNull(flag(args, "--tickets-team1")); assertNull(flag(args, "--tickets-team2"));
    }

    @Test public void controllerReachableStepsAreBoundedAndSaveIsExplicit() {
        AlertDialog dialog = tickets();
        View panel = dialog.getWindow().getDecorView();
        View increase = required(panel, "Increase team 1 tickets");
        View pointer = increase;
        while (!(pointer instanceof PadMenuFrame) && pointer.getParent() instanceof View) pointer = (View)pointer.getParent();
        assertTrue("Ticket dialog must retain the right-stick/RT pointer wrapper", pointer instanceof PadMenuFrame);
        assertTrue(increase.isFocusable()); assertTrue(increase.requestFocus());
        increase.performClick();
        assertEquals("100", team(dialog, 1).getText().toString());
        required(panel, "Decrease team 1 tickets").performClick();
        assertEquals("1", team(dialog, 1).getText().toString());
        required(panel, "Decrease team 1 tickets").performClick();
        assertEquals("1", team(dialog, 1).getText().toString());
        team(dialog, 1).setText("9990"); increase.performClick();
        assertEquals("9999", team(dialog, 1).getText().toString());
        required(panel, "Set both from team 1").performClick();
        assertEquals("9999", team(dialog, 2).getText().toString());
        assertFalse(prefs.contains("tickets.alpha_map.team1"));
        assertFalse(prefs.contains("tickets.alpha_map.team2"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(9999, prefs.getInt("tickets.alpha_map.team1", 0));
        assertEquals(9999, prefs.getInt("tickets.alpha_map.team2", 0));
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land")
    public void compactLandscapeKeepsTicketFieldsUsable() {
        AlertDialog dialog = tickets();
        View decor = dialog.getWindow().getDecorView();
        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round(560 * density), height = Math.round(330 * density);
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        for (int number = 1; number <= 2; number++) {
            EditText field = team(dialog, number);
            assertTrue("Ticket field should retain room for four digits", field.getWidth() >= 90 * density);
            assertTrue("Ticket field should retain touch-target height", field.getHeight() >= 44 * density);
        }
    }
}
