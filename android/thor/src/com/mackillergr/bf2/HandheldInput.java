package com.mackillergr.bf2;

import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.HashMap;
import java.util.Map;
import org.libsdl.app.SDLActivity;

/** Maps handheld controls to the keyboard and mouse input consumed by the desktop client. */
final class HandheldInput implements Choreographer.FrameCallback {
    private static final int LEFT = -1, RIGHT = -2;
    private final int width, height;
    private final Map<Integer,Integer> held = new HashMap<>();
    private float lookX, lookY, x, y;
    private boolean framePending, menu;
    private int mouseButtons;
    private long previousFrame;

    HandheldInput(int width, int height) {
        this.width = width; this.height = height; x = width / 2f; y = height / 2f;
    }
    private boolean menu() {
        try { return TouchOverlay.menuShown(); }
        catch (UnsatisfiedLinkError unavailable) { return false; }
    }
    private void emit(int key, boolean down) {
        if (key == LEFT || key == RIGHT) {
            int bit = key == LEFT ? MotionEvent.BUTTON_PRIMARY : MotionEvent.BUTTON_SECONDARY;
            mouseButtons = down ? mouseButtons | bit : mouseButtons & ~bit;
            SDLActivity.onNativeMouse(mouseButtons, down ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP,
                    menu ? x : 0, menu ? y : 0, !menu);
        } else if (down) SDLActivity.onNativeKeyDown(key);
        else SDLActivity.onNativeKeyUp(key);
    }
    private void bind(int id, int value, boolean down) {
        Integer old = held.get(id);
        if (old != null && (!down || old != value)) {
            held.remove(id); if (!held.containsValue(old)) emit(old, false);
        }
        if (down && !held.containsKey(id)) {
            boolean first = !held.containsValue(value);
            held.put(id, value); if (first) emit(value, true);
        }
    }
    boolean key(KeyEvent e) {
        if (!e.isFromSource(InputDevice.SOURCE_GAMEPAD) && !e.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false;
        menu = menu();
        int value;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_A: value = menu ? LEFT : KeyEvent.KEYCODE_SPACE; break;
            case KeyEvent.KEYCODE_BUTTON_B: value = menu ? KeyEvent.KEYCODE_ESCAPE : KeyEvent.KEYCODE_CTRL_LEFT; break;
            case KeyEvent.KEYCODE_BUTTON_X: value = KeyEvent.KEYCODE_R; break;
            case KeyEvent.KEYCODE_BUTTON_Y: value = KeyEvent.KEYCODE_3; break;
            case KeyEvent.KEYCODE_BUTTON_L1: value = KeyEvent.KEYCODE_SHIFT_LEFT; break;
            case KeyEvent.KEYCODE_BUTTON_R1: value = KeyEvent.KEYCODE_E; break;
            case KeyEvent.KEYCODE_BUTTON_L2: value = RIGHT; break;
            case KeyEvent.KEYCODE_BUTTON_R2: value = LEFT; break;
            case KeyEvent.KEYCODE_BUTTON_START: value = KeyEvent.KEYCODE_ESCAPE; break;
            case KeyEvent.KEYCODE_BUTTON_SELECT: value = KeyEvent.KEYCODE_TAB; break;
            default: return false;
        }
        bind(e.getKeyCode(), value, e.getAction() == KeyEvent.ACTION_DOWN);
        return true;
    }
    private static float axis(MotionEvent e, int primary, int alternate) {
        InputDevice d = e.getDevice();
        int axis = d != null && d.getMotionRange(primary, e.getSource()) == null ? alternate : primary;
        float v = e.getAxisValue(axis);
        return Math.abs(v) < 0.16f ? 0 : v;
    }
    boolean motion(MotionEvent e) {
        if (!e.isFromSource(InputDevice.SOURCE_JOYSTICK) || e.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        menu = menu();
        float leftX = e.getAxisValue(MotionEvent.AXIS_X), leftY = e.getAxisValue(MotionEvent.AXIS_Y);
        bind(-10, KeyEvent.KEYCODE_A, !menu && leftX < -0.30f);
        bind(-11, KeyEvent.KEYCODE_D, !menu && leftX > 0.30f);
        bind(-12, KeyEvent.KEYCODE_W, !menu && leftY < -0.30f);
        bind(-13, KeyEvent.KEYCODE_S, !menu && leftY > 0.30f);
        bind(-14, LEFT, axis(e, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS) > 0.40f);
        bind(-15, RIGHT, axis(e, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE) > 0.40f);
        lookX = axis(e, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX);
        lookY = axis(e, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY);
        if (menu && lookX == 0 && lookY == 0) {
            lookX = Math.abs(leftX) > 0.16f ? leftX : 0;
            lookY = Math.abs(leftY) > 0.16f ? leftY : 0;
        }
        if ((lookX != 0 || lookY != 0) && !framePending) {
            framePending = true; previousFrame = 0; Choreographer.getInstance().postFrameCallback(this);
        }
        return true;
    }
    @Override public void doFrame(long now) {
        framePending = false;
        if (lookX == 0 && lookY == 0) return;
        float dt = previousFrame == 0 ? 1f / 60 : Math.min(.05f, (now - previousFrame) / 1e9f);
        previousFrame = now; float speed = dt * (menu ? width * .65f : 850f);
        float dx = lookX * speed, dy = lookY * speed;
        if (menu) {
            x = Math.max(0, Math.min(width - 1, x + dx)); y = Math.max(0, Math.min(height - 1, y + dy));
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, x, y, false);
        } else SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, dx, dy, true);
        framePending = true; Choreographer.getInstance().postFrameCallback(this);
    }
    void reset() {
        for (int key : new java.util.HashSet<>(held.values())) emit(key, false);
        held.clear(); lookX = lookY = 0;
        Choreographer.getInstance().removeFrameCallback(this); framePending = false;
    }
}
