package com.mackillergr.bf2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Keeps a keyboard/mouse action down until its last physical binding releases it. */
final class PadState {
    interface Output { void emit(int action, boolean down); }

    private final Output output;
    private final Map<Integer,Integer> held = new HashMap<>();
    private final HashSet<Integer> blocked = new HashSet<>();
    private int device = -1;

    PadState(Output output) { this.output = output; }

    boolean accept(int id, boolean deliberate) {
        if (id == device) return true;
        if (!deliberate) return false;
        reset();
        device = id;
        return true;
    }

    boolean isDevice(int id) { return device == id; }

    void bind(int control, int action, boolean down) {
        if (blocked.contains(control)) {
            if (!down) blocked.remove(control);
            return;
        }
        Integer old = held.get(control);
        if (old != null && (!down || old != action)) {
            held.remove(control);
            if (!held.containsValue(old)) output.emit(old, false);
        }
        if (down && !held.containsKey(control)) {
            boolean first = !held.containsValue(action);
            held.put(control, action);
            if (first) output.emit(action, true);
        }
    }

    void reset() {
        for (int action : new HashSet<>(held.values())) output.emit(action, false);
        held.clear();
        blocked.clear();
        device = -1;
    }

    void changeContext() {
        blocked.addAll(held.keySet());
        for (int action : new HashSet<>(held.values())) output.emit(action, false);
        held.clear();
    }

    void bindTrigger(int control, int action, float value) {
        // A little hysteresis prevents a lightly held analog trigger from sending
        // repeated mouse clicks as its value fluctuates around the press threshold.
        bind(control, action, value > (held.containsKey(control) || blocked.contains(control) ? .30f : .40f));
    }

    static float stick(float value, float flat) {
        if (!Float.isFinite(value) || Math.abs(value) <= flat) return 0f;
        return Math.max(-1f, Math.min(1f, value));
    }

    static float trigger(float value, float minimum, float maximum) {
        if (maximum <= minimum) return 0;
        return Math.max(0f, Math.min(1f, (value - minimum) / (maximum - minimum)));
    }

    static int rightAxis(boolean hasPreferred, boolean centeredPreferred, boolean hasAlternate) {
        // Android devices use either Z/RZ or RX/RY for right-stick input. A trigger-only Z/RZ
        // pair reports a nonnegative range and must not displace a centered RX/RY pair.
        return hasPreferred && (centeredPreferred || !hasAlternate) ? 0 : 1;
    }
}
