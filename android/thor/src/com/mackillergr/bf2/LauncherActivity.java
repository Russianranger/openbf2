package com.mackillergr.bf2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LauncherActivity extends Activity {
    private static final int BROWSE = 311;
    private static final int INK = 0xff151d19, RAIL = 0xff101713, PANEL = 0xff27312b,
            EDGE = 0xff49554b, PAPER = 0xfff4f1e7, MUTED = 0xffbcc6b7, GOLD = 0xffd8b465;
    private static final String[] TABS = {"Deployment", "Controls", "Career", "LAN", "Setup"};
    private enum Mode { COOP, MENU, LAN, HOST }
    private GameDirectoryPicker picker;
    private TextView status, mapTitle, matchSize, career;
    private final TextView[] side = new TextView[2], number = new TextView[2];
    private final Button[] nav = new Button[TABS.length];
    private final int[] bots = {8, 8};
    private EditText player, endpoint, gameKey;
    private CheckBox turnip, touch;
    private Button deploy, mapPicker, finishMatch;
    private ImageView preview;
    private LinearLayout pages;
    private SharedPreferences prefs;
    private File mod;
    private List<MapBriefing> levels = Collections.emptyList();
    private MapBriefing selected;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private int artRequest, scanRequest;
    private boolean openingGame, live;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sessionCheck = this::updateSession;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("openbf2", MODE_PRIVATE);
        File files = getExternalFilesDir(null);
        mod = new File(files == null ? getFilesDir() : files, "Game Files/mods/bf2");
        bots[0] = bounded(prefs.getInt("bots1", 8), GameLaunchPlan.MAX_COOP_BOTS);
        bots[1] = bounded(prefs.getInt("bots2", 8), GameLaunchPlan.MAX_COOP_BOTS - bots[0]);
        getWindow().setStatusBarColor(RAIL);
        getWindow().setNavigationBarColor(RAIL);
        LinearLayout shell = new LinearLayout(this);
        shell.setBackgroundColor(INK);
        LinearLayout rail = column();
        rail.setBackgroundColor(RAIL);
        rail.setPadding(dp(10), dp(7), dp(10), dp(6));
        ScrollView railScroll = new ScrollView(this);
        railScroll.setBackgroundColor(RAIL);
        railScroll.setFillViewport(true);
        railScroll.addView(rail);
        shell.addView(railScroll, new LinearLayout.LayoutParams(dp(compact() ? 112 : 145), -1));
        TextView brand = text(rail, "", compact() ? 19 : 24, PAPER, true);
        android.text.SpannableString wordmark = new android.text.SpannableString("OPENBF2");
        wordmark.setSpan(new android.text.style.ForegroundColorSpan(GOLD), 4, 7, 0);
        brand.setText(wordmark);
        brand.setSingleLine(true);
        brand.setLetterSpacing(.025f);
        brand.setPadding(dp(5), dp(8), 0, dp(12));
        View stripe = new View(this);
        stripe.setBackgroundColor(GOLD);
        rail.addView(stripe, new LinearLayout.LayoutParams(dp(35), dp(3)));
        space(rail, 12);
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            nav[i] = button(rail, TABS[i], () -> showPage(index), false);
            nav[i].setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        }
        LinearLayout main = column();
        main.setPadding(dp(compact() ? 12 : 22), dp(7), dp(compact() ? 12 : 22), dp(5));
        shell.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        pages = column();
        main.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        status = text(main, "", 12, MUTED, false);
        status.setPadding(0, dp(3), 0, dp(2));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        picker = new GameDirectoryPicker(this, new GameDirectoryPicker.Listener() {
            public void onStatus(String message) { status.setText(message); }
            public void onImported(String path) { refreshMaps(); }
        });
        makeDeployment(page("Offline co-op"));
        makeControls(page("Controls"));
        career = text(page("Offline career"), "", 18, PAPER, false);
        makeLan(page("Local network"));
        makeSetup(page("Game setup"));
        setContentView(PadMenuFrame.wrap(shell));
        showPage(state == null ? 0 : bounded(state.getInt("tab"), TABS.length - 1));
        refreshMaps();
    }

    private static int bounded(int n, int max) { return Math.max(0, Math.min(n, max)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private boolean compact() { return getResources().getConfiguration().screenWidthDp < 700; }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private void space(LinearLayout box, int height) { box.addView(new View(this), 1, dp(height)); }
    private LinearLayout page(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        pages.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout box = column();
        scroll.addView(box);
        TextView heading = text(box, title.toUpperCase(Locale.ROOT), compact() ? 22 : 26, PAPER, true);
        heading.setLetterSpacing(.045f);
        heading.setPadding(0, dp(4), 0, dp(8));
        View rule = new View(this);
        rule.setBackgroundColor(EDGE);
        box.addView(rule, -1, dp(1));
        space(box, 10);
        return box;
    }
    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(color); t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        parent.addView(t);
        return t;
    }
    private GradientDrawable panel(int fill, int edge) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill); bg.setCornerRadius(dp(2)); bg.setStroke(dp(1), edge);
        return bg;
    }
    private StateListDrawable buttonBg(boolean accent) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, panel(0xff857443, PAPER));
        s.addState(new int[]{android.R.attr.state_focused}, panel(accent ? 0xffeccb88 : 0xff505b45, GOLD));
        s.addState(new int[]{android.R.attr.state_selected}, panel(0xff394336, GOLD));
        s.addState(new int[]{}, panel(accent ? GOLD : PANEL, accent ? GOLD : EDGE));
        return s;
    }
    private Button button(LinearLayout parent, String label, Runnable run, boolean accent) {
        Button b = new Button(this);
        b.setAllCaps(false); b.setText(label); b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        b.setTextColor(accent ? INK : PAPER); b.setBackground(buttonBg(accent));
        b.setPadding(dp(9), 0, dp(9), 0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.bottomMargin = dp(4);
        parent.addView(b, lp);
        return b;
    }
    private void showPage(int tab) {
        for (int i = 0; i < nav.length; i++) {
            nav[i].setSelected(i == tab);
            pages.getChildAt(i).setVisibility(i == tab ? View.VISIBLE : View.GONE);
        }
        if (tab == 2) career.setText(OfflineCareer.summary(this));
    }

    private void makeDeployment(LinearLayout page) {
        LinearLayout row = new LinearLayout(this);
        boolean stack = getResources().getConfiguration().screenWidthDp < 520;
        row.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        page.addView(row);
        LinearLayout map = column(), roster = column();
        LinearLayout.LayoutParams mapParams = stack ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1.3f);
        if (!stack) mapParams.rightMargin = dp(12);
        row.addView(map, mapParams);
        row.addView(roster, stack ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
        roster.setBackground(panel(0xff202923, EDGE));
        roster.setPadding(dp(compact() ? 7 : 12), dp(10), dp(compact() ? 7 : 12), dp(7));
        FrameLayout art = new FrameLayout(this);
        art.setBackground(panel(0xff323e30, EDGE));
        int screenHeight = getResources().getConfiguration().screenHeightDp;
        map.addView(art, -1, dp(Math.max(95, Math.min(265, Math.round(screenHeight * .42f)))));
        preview = new ImageView(this);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(preview, -1, -1);
        LinearLayout caption = column();
        caption.setGravity(Gravity.BOTTOM);
        caption.setPadding(dp(11), 0, dp(10), dp(7));
        caption.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00101713, 0x10101713, 0xbf101713}));
        art.addView(caption, -1, -1);
        text(caption, "MAP", 12, GOLD, true).setLetterSpacing(.12f);
        mapTitle = text(caption, "Select a map", compact() ? 19 : 24, PAPER, true);
        art.setOnClickListener(v -> chooseCoopMap());
        space(map, 5);
        mapPicker = button(map, "Select map", this::chooseCoopMap, false);
        button(map, "Battlefield 2 main menu", () -> launch(Mode.MENU), false);

        text(roster, "BOT ROSTER", 14, GOLD, true).setLetterSpacing(.10f);
        for (int i = 0; i < 2; i++) {
            final int team = i;
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            roster.addView(line, -1, dp(48));
            side[i] = text(line, "Team " + (i + 1), 16, PAPER, true);
            side[i].setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            side[i].setMaxLines(2);
            Button minus = button(line, "−", () -> adjust(team, -1), false);
            minus.setLayoutParams(new LinearLayout.LayoutParams(dp(compact() ? 34 : 42), dp(44)));
            minus.setContentDescription("Decrease team " + (i+1) + " bots");
            number[i] = text(line, String.valueOf(bots[i]), 19, PAPER, true);
            number[i].setGravity(Gravity.CENTER);
            number[i].setLayoutParams(new LinearLayout.LayoutParams(dp(31), -2));
            Button plus = button(line, "+", () -> adjust(team, 1), false);
            plus.setLayoutParams(new LinearLayout.LayoutParams(dp(compact() ? 34 : 42), dp(44)));
            plus.setContentDescription("Increase team " + (i+1) + " bots");
        }
        matchSize = text(roster, "", 13, MUTED, false);
        updateRoster();
        space(roster, 8);
        deploy = button(roster, "Deploy", () -> launch(Mode.COOP), true);
        finishMatch = button(roster, "End current match", this::endMatch, false);
        finishMatch.setVisibility(View.GONE);
    }
    private void adjust(int team, int step) {
        int count = bots[team] + step;
        if (count < 0) return;
        if (count + bots[1-team] > GameLaunchPlan.MAX_COOP_BOTS) {
            status.setText("The two teams can have up to " + GameLaunchPlan.MAX_COOP_BOTS + " bots combined.");
            return;
        }
        bots[team] = count;
        prefs.edit().putInt(team == 0 ? "bots1" : "bots2", count).apply();
        updateRoster();
    }
    private void updateRoster() {
        for (int i = 0; i < 2; i++) number[i].setText(String.valueOf(bots[i]));
        matchSize.setText((bots[0] + bots[1]) + " bots  ·  1 human");
    }
    private void makeControls(LinearLayout box) {
        button(box, "Controller bindings", () -> startActivity(new Intent(this, ControllerBindingsActivity.class)), true);
        touch = check(box, "Show touch controls", "touch");
        text(box, "The gear menu stays available during play. Hold Start to open it on a controller.", 15, MUTED, false);
    }
    private void makeLan(LinearLayout box) {
        endpoint = field(box, "Server address", prefs.getString("endpoint", ""));
        endpoint.setHint("192.168.1.10:16567");
        gameKey = field(box, "Battlefield 2 product key", "");
        gameKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        gameKey.setSaveEnabled(false); gameKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        gameKey.setHint(GameKey.saved(this) ? "Saved on this device" : "Required to join a LAN server");
        button(box, "Find LAN servers", () -> startActivityForResult(new Intent(this, LanBrowserActivity.class), BROWSE), false);
        button(box, "Join LAN server", () -> launch(Mode.LAN), true);
        button(box, "Host LAN Conquest", this::chooseHostMap, false);
    }
    private void makeSetup(LinearLayout box) {
        player = field(box, "Player name", prefs.getString("player", "Player"));
        turnip = check(box, "Use bundled Turnip driver", "turnip");
        button(box, "Import Battlefield 2 installation", () -> picker.pick(), false);
        button(box, "Export game logs", () -> BF2LogExporter.request(this), false);
    }
    private EditText field(LinearLayout box, String label, String value) {
        TextView desc = text(box, label, 15, MUTED, false);
        EditText e = new EditText(this);
        e.setId(View.generateViewId()); desc.setLabelFor(e.getId());
        e.setTextColor(PAPER); e.setSingleLine(true); e.setText(value);
        box.addView(e); return e;
    }
    private CheckBox check(LinearLayout box, String label, String key) {
        CheckBox c = new CheckBox(this);
        c.setTextColor(PAPER); c.setText(label); c.setChecked(prefs.getBoolean(key, true));
        c.setOnCheckedChangeListener((view, on) -> prefs.edit().putBoolean(key, on).apply());
        box.addView(c); return c;
    }
    private void refreshMaps() {
        final int request = ++scanRequest;
        io.execute(() -> {
            List<MapBriefing> found = new ArrayList<>();
            File[] dirs = GameContent.child(mod, "Levels").listFiles();
            if (dirs != null) for (File entry : dirs) {
                if (entry.isDirectory() && GameContent.hasCoop(entry)) found.add(new MapBriefing(entry));
            }
            found.sort(Comparator.comparing(map -> map.title, String.CASE_INSENSITIVE_ORDER));
            boolean ready = GameContent.child(mod, "ClientArchives.con").isFile();
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing() || request != scanRequest) return;
                levels = found;
                String prior = prefs.getString("map", "strike_at_karkand");
                MapBriefing pick = found.isEmpty() ? null : found.get(0);
                for (MapBriefing map : found) if (map.id.equalsIgnoreCase(prior)) pick = map;
                selectMap(pick);
                status.setText(!ready ? "Import your Battlefield 2 1.50 installation in Setup to play."
                        : found.size() + " offline maps installed");
                updateSession();
            });
        });
    }
    private void selectMap(MapBriefing map) {
        selected = map;
        int request = ++artRequest;
        preview.setImageDrawable(null);
        mapTitle.setText(map == null ? "No co-op maps" : map.title);
        for (int i = 0; i < 2; i++) side[i].setText(map == null ? "Team " + (i+1) : map.sides[i]);
        mapPicker.setText(map == null ? "Import game files" : "Select map");
        if (map == null) return;
        prefs.edit().putString("map", map.id).apply();
        io.execute(() -> {
            Bitmap image = null;
            try { image = LoadingBitmap.read(LoadingArt.find(mod, map.id)); }
            catch (IOException | RuntimeException ex) { android.util.Log.w("OpenBF2", "Map preview unavailable", ex); }
            final Bitmap bitmap = image;
            runOnUiThread(() -> {
                if (!isDestroyed() && request == artRequest) preview.setImageBitmap(bitmap);
                else if (bitmap != null) bitmap.recycle();
            });
        });
    }
    private void chooseCoopMap() {
        if (levels.isEmpty()) { picker.pick(); return; }
        String[] choices = new String[levels.size()];
        for (int i = 0; i < choices.length; i++) choices[i] = levels.get(i).title;
        PadMenuFrame.show(new AlertDialog.Builder(this).setTitle("Select co-op map")
            .setSingleChoiceItems(choices, levels.indexOf(selected), (dialog, i) -> {
                selectMap(levels.get(i)); dialog.dismiss();
            }).setNegativeButton("Cancel", null).create());
    }
    private void updateSession() {
        try { live = GameSession.isActive(this); }
        catch (IOException e) { status.setText("Unable to check game session: " + e.getMessage()); return; }
        handler.removeCallbacks(sessionCheck);
        long remaining = prefs.getLong("endingUntil", 0) - System.currentTimeMillis();
        boolean closing = live && remaining > 0 && remaining <= 15000;
        deploy.setEnabled(!closing);
        finishMatch.setEnabled(!closing);
        deploy.setText(closing ? "Ending match…" : live ? "Resume match" : "Deploy");
        finishMatch.setVisibility(live ? View.VISIBLE : View.GONE);
        if (closing) {
            status.setText("Closing the current match…");
            handler.postDelayed(sessionCheck, 250);
        } else if (live) {
            status.setText("Match in progress. Map and roster selections apply to the next match.");
        } else {
            if (prefs.contains("endingUntil"))
                prefs.edit().remove("endingUntil").apply();
            String message = status.getText().toString();
            if (message.startsWith("Match in progress.") || message.startsWith("Closing the current match"))
                status.setText(levels.size() + " offline maps installed");
        }
    }
    private void endMatch() {
        PadMenuFrame.show(new AlertDialog.Builder(this).setTitle("End current match?")
                .setMessage("The current round will end before the results screen.")
                .setNegativeButton("Keep playing", null)
                .setPositiveButton("End match", (dialog, which) -> {
                    updateSession();
                    if (!live) return;
                    prefs.edit().putLong("endingUntil", System.currentTimeMillis() + 15000).apply();
                    startActivity(new Intent(this, GameActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("endSession", true));
                }).create());
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
                if (mode != Mode.COOP) {
                    status.setText("End the current match in Deployment to start another game mode.");
                    return;
                }
                Intent resume = new Intent(this, GameActivity.class);
                resume.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                resume.putExtra("touch", touch.isChecked());
                openingGame = true;
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
        String map = selected == null ? null : selected.id;
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
                    args = GameLaunchPlan.forCoop(mod.getPath(), libs.getPath(), width, height, name, driver, map, bots[0], bots[1]);
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
        updateSession();
    }
    @Override protected void onPause() {
        prefs.edit().putString("player", player.getText().toString().trim())
                .putString("endpoint", endpoint.getText().toString().trim()).apply();
        handler.removeCallbacks(sessionCheck);
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        for (int i = 0; i < nav.length; i++) if (nav[i].isSelected()) state.putInt("tab", i);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        ++artRequest;
        ++scanRequest;
        handler.removeCallbacks(sessionCheck);
        io.shutdownNow();
        super.onDestroy();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (BF2LogExporter.handleResult(this, request, result, data)) return;
        if (request == GameDirectoryPicker.REQUEST_CODE) picker.onResult(result, data);
        else if (request == BROWSE && result == RESULT_OK && data != null)
            endpoint.setText(data.getStringExtra("ip") + ":" + data.getIntExtra("gamePort", 16567));
    }
}
