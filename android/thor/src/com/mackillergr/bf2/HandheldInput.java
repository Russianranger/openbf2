package com.mackillergr.bf2;

import android.app.Activity;
import android.content.Context;
import android.hardware.input.InputManager;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import org.libsdl.app.SDLActivity;

/** Maps handheld controls to the keyboard and mouse input consumed by the desktop client. */
final class HandheldInput implements Choreographer.FrameCallback, InputManager.InputDeviceListener {
    private static final int LEFT = -1, RIGHT = -2;
    private final Activity activity;
    private final InputManager devices;
    private final PadState held = new PadState(this::emit);
    private int width, height;
    private float lookX, lookY, x, y;
    private boolean framePending, menu;
    private int mouseButtons;
    private long previousFrame;

    HandheldInput(Activity activity) {
        this.activity = activity;
        devices = (InputManager) activity.getSystemService(Context.INPUT_SERVICE);
        devices.registerInputDeviceListener(this, null);
        resize();
    }
    private void resize() {
        int w = activity.getWindow().getDecorView().getWidth();
        int h = activity.getWindow().getDecorView().getHeight();
        if (w <= 0 || h <= 0 || (w == width && h == height)) return;
        x = width > 0 ? x * w / width : w / 2f;
        y = height > 0 ? y * h / height : h / 2f;
        width = w; height = h;
    }
    @Override public void onInputDeviceAdded(int id) {}
    @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }
    @Override public void onInputDeviceRemoved(int id) { if (held.isDevice(id)) reset(); }
    void close() { reset(); devices.unregisterInputDeviceListener(this); }
    private void syncMode() {
        boolean visible = menu();
        if (visible != menu) {
            reset(); // Release buttons in their original input context.
            menu = visible;
        }
        if (menu) resize();
    }
    private static native boolean pointerShown();
    private boolean menu() {
        try { return pointerShown(); }
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
    boolean key(KeyEvent e) {
        if (!e.isFromSource(InputDevice.SOURCE_GAMEPAD) && !e.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false;
        syncMode();
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
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_START: value = KeyEvent.KEYCODE_ESCAPE; break;
            case KeyEvent.KEYCODE_BUTTON_SELECT: value = KeyEvent.KEYCODE_TAB; break;
            default: return false;
        }
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (down && !held.isDevice(e.getDeviceId())) stopMotion();
        if (held.accept(e.getDeviceId(), down)) held.bind(e.getKeyCode(), value, down);
        return true;
    }
    private static InputDevice.MotionRange range(MotionEvent e, int axis) {
        InputDevice d = e.getDevice();
        if (d == null) return null;
        InputDevice.MotionRange fromSource = d.getMotionRange(axis, e.getSource());
        return fromSource == null ? d.getMotionRange(axis) : fromSource;
    }
    private static int rightAxis(MotionEvent e, int primary, int alternate) {
        InputDevice.MotionRange a = range(e, primary), b = range(e, alternate);
        return PadState.rightAxis(a != null, a != null && a.getMin() < 0, b != null)
                == 0 ? primary : alternate;
    }
    private static float triggerAxis(MotionEvent e, int axis) {
        InputDevice.MotionRange r = range(e, axis);
        return r == null ? 0 : PadState.trigger(e.getAxisValue(axis), r.getMin(), r.getMax());
    }
    private static float trigger(MotionEvent e, int axis, int alias, int legacy, int usedLookAxis) {
        float value = Math.max(triggerAxis(e, axis), triggerAxis(e, alias));
        return legacy == usedLookAxis ? value : Math.max(value, triggerAxis(e, legacy));
    }
    private static float deadzone(float v) { return Math.abs(v) < .16f ? 0f : v; }
    boolean motion(MotionEvent e) {
        if (!e.isFromSource(InputDevice.SOURCE_JOYSTICK) || e.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        syncMode();
        float leftX = e.getAxisValue(MotionEvent.AXIS_X), leftY = e.getAxisValue(MotionEvent.AXIS_Y);
        int rightHorizontal = rightAxis(e, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX);
        int rightVertical = rightAxis(e, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY);
        float mx = deadzone(e.getAxisValue(rightHorizontal)), my = deadzone(e.getAxisValue(rightVertical));
        float rt = trigger(e, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS, MotionEvent.AXIS_RZ, rightVertical);
        float lt = trigger(e, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_Z, rightHorizontal);
        boolean deliberate = Math.abs(leftX) > .30f || Math.abs(leftY) > .30f ||
                Math.abs(mx) > .30f || Math.abs(my) > .30f || rt > .40f || lt > .40f;
        if (!held.isDevice(e.getDeviceId()) && deliberate) stopMotion();
        if (!held.accept(e.getDeviceId(), deliberate)) return true;
        held.bind(-10, KeyEvent.KEYCODE_A, !menu && leftX < -0.30f);
        held.bind(-11, KeyEvent.KEYCODE_D, !menu && leftX > 0.30f);
        held.bind(-12, KeyEvent.KEYCODE_W, !menu && leftY < -0.30f);
        held.bind(-13, KeyEvent.KEYCODE_S, !menu && leftY > 0.30f);
        held.bind(-14, LEFT, rt > 0.40f);
        held.bind(-15, RIGHT, lt > 0.40f);
        lookX = mx;
        lookY = my;
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
        syncMode();
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
        held.reset();
        stopMotion();
    }
    private void stopMotion() {
        lookX = lookY = 0;
        previousFrame = 0;
        Choreographer.getInstance().removeFrameCallback(this); framePending = false;
    }
}
