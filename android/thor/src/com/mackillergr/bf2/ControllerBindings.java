package com.mackillergr.bf2;

import android.content.Context;
import android.util.AtomicFile;
import android.view.KeyEvent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/** App-owned controller assignments, re-read on resume by the separate game process. */
final class ControllerBindings {
    static final long HOLD_MILLIS = 450;
    private static final String FILE = "controller-bindings.json";
    private static final int FORMAT = 1;

    // Keep names and order stable: the gameplay bridge shares these action identifiers.
    enum Action {
        NONE("No action"), FIRE("Fire"), AIM("Aim down sights"), JUMP("Jump"),
        CROUCH_TOGGLE("Crouch / stand"), PRONE("Go prone"), SPRINT("Sprint"),
        RELOAD("Reload"), USE("Use / enter or leave vehicle"),
        WEAPON_1("Weapon slot 1"), WEAPON_2("Weapon slot 2"), WEAPON_3("Weapon slot 3"),
        WEAPON_4("Weapon slot 4"), WEAPON_5("Weapon slot 5"), WEAPON_6("Weapon slot 6"),
        WEAPON_NEXT("Next weapon"), WEAPON_PREVIOUS("Previous weapon"),
        SCOREBOARD("Scoreboard"), MENU("Game menu"), MAP("Map"), SQUAD("Squad / commander screen"),
        COMM_ROSE("Communication rose"), TACTICAL_ROSE("Tactical command rose"),
        WEAPON_7("Equipment slot 7"), WEAPON_8("Equipment slot 8"), WEAPON_9("Equipment slot 9 / parachute"),
        SEAT_1("Vehicle seat 1"), SEAT_2("Vehicle seat 2"), SEAT_3("Vehicle seat 3"), SEAT_4("Vehicle seat 4"),
        SEAT_5("Vehicle seat 5"), SEAT_6("Vehicle seat 6"), SEAT_7("Vehicle seat 7"), SEAT_8("Vehicle seat 8"),
        DROP_KIT("Drop / pick up kit"), FIRE_MODE("Change fire mode"), MAP_ZOOM("Map zoom"),
        MAP_3D("Toggle 3D map"), CHAT_ALL("Chat to all"), CHAT_TEAM("Chat to team"), CHAT_SQUAD("Chat to squad");
        final String label;
        Action(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    static final class Binding {
        final int keyCode;
        final Action tap, hold;
        Binding(int keyCode, Action tap, Action hold) {
            if (!canAssign(keyCode)) throw new IllegalArgumentException("This system key cannot be assigned.");
            if (tap == null || hold == null) throw new IllegalArgumentException("Choose an action.");
            if (keyCode == KeyEvent.KEYCODE_BUTTON_START && hold != Action.NONE)
                throw new IllegalArgumentException("Holding Start is reserved for the controller menu.");
            this.keyCode = keyCode; this.tap = tap; this.hold = hold;
        }
    }

    private final TreeMap<Integer, Binding> bindings = new TreeMap<>();
    private String warning = "";
    Binding get(int keyCode) { return bindings.get(keyCode); }
    List<Binding> list() { return Collections.unmodifiableList(new ArrayList<>(bindings.values())); }
    void set(Binding binding) { bindings.put(binding.keyCode, binding); }
    void remove(int keyCode) { bindings.remove(keyCode); }
    String warning() { return warning; }

    static ControllerBindings defaults() {
        ControllerBindings result = new ControllerBindings();
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_A, Action.JUMP, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_B, Action.CROUCH_TOGGLE, Action.PRONE));
        result.set(new Binding(KeyEvent.KEYCODE_CTRL_LEFT, Action.CROUCH_TOGGLE, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_Z, Action.PRONE, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_X, Action.RELOAD, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_Y, Action.WEAPON_3, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_L1, Action.SPRINT, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_R1, Action.USE, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_L2, Action.AIM, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_R2, Action.FIRE, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_START, Action.MENU, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BUTTON_SELECT, Action.SCOREBOARD, Action.NONE));
        result.set(new Binding(KeyEvent.KEYCODE_BACK, Action.MENU, Action.NONE));
        return result;
    }

    /** Reads disk every time, avoiding stale SharedPreferences in the :game process. */
    static ControllerBindings load(Context context) {
        AtomicFile file = file(context);
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return defaults();
        try (FileInputStream in = file.openRead()) {
            if (in.available() > 65536) throw new IOException("Controller settings are too large.");
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int size;
            while ((size = in.read(buffer)) != -1) {
                if (bytes.size() + size > 65536) throw new IOException("Controller settings are too large.");
                bytes.write(buffer, 0, size);
            }
            JSONObject data = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (data.getInt("format") != FORMAT) throw new IOException("Unsupported controller settings version.");
            JSONArray rows = data.getJSONArray("bindings");
            ControllerBindings result = new ControllerBindings();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Binding binding = new Binding(row.getInt("key"), Action.valueOf(row.getString("tap")),
                        Action.valueOf(row.getString("hold")));
                if (result.get(binding.keyCode) != null) throw new IOException("Duplicate controller button assignment.");
                result.set(binding);
            }
            return result;
        } catch (IOException | JSONException | IllegalArgumentException failure) {
            ControllerBindings result = defaults();
            result.warning = "Saved controller settings could not be read. Defaults are active; saving an edit replaces the invalid settings.";
            return result;
        }
    }

    void save(Context context) throws IOException {
        AtomicFile file = file(context);
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(signature().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
            warning = "";
        } catch (IOException failure) {
            if (out != null) file.failWrite(out);
            throw failure;
        }
    }

    /** Stable ordered content lets input owners detect changes without timestamp races. */
    String signature() {
        try {
            JSONArray rows = new JSONArray();
            for (Binding b : bindings.values()) rows.put(new JSONObject()
                    .put("key", b.keyCode).put("tap", b.tap.name()).put("hold", b.hold.name()));
            return new JSONObject().put("format", FORMAT).put("bindings", rows).toString();
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }

    private static AtomicFile file(Context context) { return new AtomicFile(new File(context.getFilesDir(), FILE)); }

    static boolean canAssign(int key) {
        if (key <= KeyEvent.KEYCODE_UNKNOWN || key > KeyEvent.getMaxKeyCode()) return false;
        switch (key) {
            case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP: case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE: case KeyEvent.KEYCODE_MUTE: case KeyEvent.KEYCODE_APP_SWITCH:
            case KeyEvent.KEYCODE_ASSIST: case KeyEvent.KEYCODE_VOICE_ASSIST: case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENDCALL: case KeyEvent.KEYCODE_BRIGHTNESS_UP: case KeyEvent.KEYCODE_BRIGHTNESS_DOWN:
                return false;
            default: return true;
        }
    }

    static String keyName(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "Left bumper";
            case KeyEvent.KEYCODE_BUTTON_R1: return "Right bumper";
            case KeyEvent.KEYCODE_BUTTON_L2: return "Left trigger";
            case KeyEvent.KEYCODE_BUTTON_R2: return "Right trigger";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "Left stick click";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "Right stick click";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
            case KeyEvent.KEYCODE_DPAD_UP: return "D-pad up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "D-pad down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "D-pad left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "D-pad right";
            case KeyEvent.KEYCODE_BACK: return "Back";
            default: return "Key " + KeyEvent.keyCodeToString(key).replace("KEYCODE_", "").replace('_', ' ');
        }
    }
}
