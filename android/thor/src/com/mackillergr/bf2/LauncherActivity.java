package com.mackillergr.bf2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.widget.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class LauncherActivity extends Activity {
    private static final int BROWSE = 311;
    private enum Mode { COOP, MENU, LAN, HOST }
    private GameDirectoryPicker picker;
    private TextView status;
    private EditText player, endpoint, gameKey;
    private CheckBox turnip, touch;
    private Spinner maps;
    private SharedPreferences prefs;
    private File mod;
    private LinearLayout root;
    private boolean openingGame;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("openbf2", MODE_PRIVATE);
        File files = getExternalFilesDir(null);
        if (files == null) files = getFilesDir();
        mod = new File(files, "Game Files/mods/bf2");
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(PadMenuFrame.wrap(scroll));
        label("OpenBF2", 26);
        status = label("", 15);
        picker = new GameDirectoryPicker(this, new GameDirectoryPicker.Listener() {
            public void onStatus(String message) { status.setText(message); }
            public void onImported(String path) { refreshMaps(); }
        });
        button("Import Battlefield 2 installation", () -> picker.pick());
        label("Player name", 16);
        player = field(prefs.getString("player", "Player"));
        label("Offline co-op map", 16);
        maps = new Spinner(this) {
            @Override public boolean performClick() {
                if (getAdapter() == null || getCount() == 0) return super.performClick();
                String[] choices = new String[getCount()];
                for (int i = 0; i < choices.length; i++) choices[i] = getItemAtPosition(i).toString();
                PadMenuFrame.show(new AlertDialog.Builder(LauncherActivity.this)
                        .setTitle("Offline co-op map").setSingleChoiceItems(choices, getSelectedItemPosition(),
                            (dialog, index) -> { setSelection(index); dialog.dismiss(); }).create());
                return true;
            }
        };
        root.addView(maps);
        touch = check("Show touch controls", "touch", true);
        touch.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean("touch", checked).apply());
        label("Turn off the on-screen buttons for controller play. Touchscreen menu taps still work.", 14);
        button("Play offline with bots", () -> launch(Mode.COOP));
        button("Offline career", () -> PadMenuFrame.show(new AlertDialog.Builder(this).setTitle("Offline career")
            .setMessage(OfflineCareer.summary(this)).setPositiveButton("Close", null).create()));
        button("Open Battlefield 2 menu", () -> launch(Mode.MENU));
        label("LAN server address", 16);
        endpoint = field(prefs.getString("endpoint", ""));
        endpoint.setHint("192.168.1.10:16567");
        label("Battlefield 2 product key for LAN", 16);
        gameKey = field("");
        gameKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        gameKey.setSaveEnabled(false);
        gameKey.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        gameKey.setHint(GameKey.saved(this) ? "Saved on this device" : "Optional for offline play");
        button("Find LAN servers", () -> startActivityForResult(new Intent(this, LanBrowserActivity.class), BROWSE));
        button("Join LAN server", () -> launch(Mode.LAN));
        button("Host LAN Conquest", this::chooseHostMap);
        turnip = check("Use bundled Turnip driver", "turnip", true);
        button("Export game logs", () -> BF2LogExporter.request(this));
        refreshMaps();
    }
    private TextView label(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); root.addView(t); return t;
    }
    private EditText field(String value) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setText(value); root.addView(e); return e;
    }
    private CheckBox check(String label, String pref, boolean initial) {
        CheckBox c = new CheckBox(this); c.setText(label); c.setChecked(prefs.getBoolean(pref,initial));
        root.addView(c); return c;
    }
    private void button(String label, Runnable run) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(label); b.setOnClickListener(v -> run.run());
        root.addView(b);
    }
    private void refreshMaps() {
        List<String> levels = new ArrayList<>();
        File levelDir = GameContent.child(mod, "Levels");
        File[] entries = levelDir.listFiles();
        if (entries != null) for (File item : entries)
            if (item.isDirectory() && GameContent.hasCoop(item)) levels.add(item.getName());
        Collections.sort(levels, String.CASE_INSENSITIVE_ORDER);
        maps.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, levels));
        String previous = prefs.getString("map", "strike_at_karkand");
        int selected = Collections.binarySearch(levels, previous, String.CASE_INSENSITIVE_ORDER);
        if (selected >= 0) maps.setSelection(selected);
        status.setText(!GameContent.child(mod, "ClientArchives.con").isFile()
            ? "Select the folder containing mods/bf2 from your Battlefield 2 1.50 installation."
            : levels.size() + " maps have a 16-player co-op layout.");
    }
    private void chooseHostMap() {
        File[] entries = GameContent.child(mod, "Levels").listFiles();
        List<String> choices = new ArrayList<>();
        if (entries != null) for (File level : entries)
            if (level.isDirectory() && GameContent.hasLayout(level, "gpm_cq")) choices.add(level.getName());
        if (choices.isEmpty()) { status.setText("Import a map with a 16-player Conquest layout."); return; }
        Collections.sort(choices, String.CASE_INSENSITIVE_ORDER);
        String[] items = choices.toArray(new String[0]);
        PadMenuFrame.show(new AlertDialog.Builder(this).setTitle("Host LAN Conquest").setItems(items,
                (dialog, i) -> launch(Mode.HOST, items[i])).create());
    }
    private void launch(Mode mode) { launch(mode, null); }
    private void launch(Mode mode, String hostMap) {
        if (openingGame) return;
        try {
            if (GameSession.isActive(this)) {
                Intent resume = new Intent(this, GameActivity.class);
                resume.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                resume.putExtra("touch", touch.isChecked());
                startActivity(resume);
                return;
            }
        } catch (IOException e) { status.setText("Unable to check game session: " + e.getMessage()); return; }
        if (picker.isImporting()) { status.setText("The game data import is still running."); return; }
        if (!GameContent.child(mod, "ClientArchives.con").isFile()) { picker.pick(); return; }
        if (getSharedPreferences("import", MODE_PRIVATE).getBoolean("incomplete", false)) {
            status.setText("The previous import was interrupted. Select the installation folder again to finish."); return;
        }
        File libs = new File(getApplicationInfo().nativeLibraryDir);
        Point screen = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(screen);
        int width = Math.max(screen.x, screen.y), height = Math.min(screen.x, screen.y);
        String name = player.getText().toString().trim();
        String driver = turnip.isChecked() ? "turnip" : "system";
        String map = (String)maps.getSelectedItem();
        String host = endpoint.getText().toString().trim();
        String[] args;
        try {
            String entered = gameKey.getText().toString().trim();
            if (!entered.isEmpty()) {
                GameKey.save(this, entered);
                gameKey.setText("");
                gameKey.setHint("Saved on this device");
            }
            if (mode == Mode.LAN && GameKey.read(this).isEmpty())
                throw new IOException("Enter your Battlefield 2 product key before joining a LAN server.");
            switch (mode) {
                case COOP:
                    if (map == null) throw new IOException("Import a map with a 16-player co-op layout.");
                    if (!new File(libs, "libaidll.so").isFile())
                        throw new IOException("This build lacks the ARM64 bot runtime. The game menu and LAN client are available.");
                    if (!GameContent.child(mod, "ServerArchives.con").isFile())
                        throw new IOException("ServerArchives.con is missing. Import the complete installation.");
                    args = GameLaunchPlan.forCoop(mod.getPath(), libs.getPath(), width, height, name, driver, map);
                    OfflineCareer.prepare(this, mod);
                    break;
                case HOST:
                    args = GameLaunchPlan.forHost(mod.getPath(), libs.getPath(), width, height, name, driver, hostMap, false);
                    break;
                case LAN:
                    int split = host.lastIndexOf(':');
                    int port = split < 0 ? 16567 : Integer.parseInt(host.substring(split + 1));
                    String ip = split < 0 ? host : host.substring(0, split);
                    args = GameLaunchPlan.forLan(mod.getPath(), libs.getPath(), width, height, name, driver, ip, port);
                    break;
                default:
                    args = GameLaunchPlan.forMenu(mod.getPath(), libs.getPath(), width, height, name, driver);
            }
            if (mode != Mode.COOP) OfflineCareer.disarm(this);
            prefs.edit().putString("player", name).putString("map", map).putString("endpoint", host)
                  .putBoolean("turnip", turnip.isChecked()).putBoolean("touch", touch.isChecked()).apply();
            Intent i = new Intent(this, GameActivity.class);
            i.putExtra("args", args); i.putExtra("gameWidth", width); i.putExtra("gameHeight", height);
            i.putExtra("touch", touch.isChecked()); openingGame = true; startActivity(i);
        } catch (IOException | IllegalArgumentException ex) { status.setText(ex.getMessage()); }
    }
    @Override protected void onResume() {
        super.onResume();
        openingGame = false;
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (BF2LogExporter.handleResult(this, request, result, data)) return;
        if (request == GameDirectoryPicker.REQUEST_CODE) picker.onResult(result, data);
        else if (request == BROWSE && result == RESULT_OK && data != null)
            endpoint.setText(data.getStringExtra("ip") + ":" + data.getIntExtra("gamePort", 16567));
    }
}
