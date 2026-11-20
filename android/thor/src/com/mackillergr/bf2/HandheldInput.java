package com.mackillergr.bf2;

import android.app.Activity;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import org.libsdl.app.SDLActivity;

/** Maps handheld controls to the keyboard and mouse input consumed by the desktop client. */
final class HandheldInput implements Choreographer.FrameCallback, InputManager.InputDeviceListener {
    private static final int LEFT = -1, RIGHT = -2;
    private final Activity activity;
    private final InputManager devices;
    private final PadState held = new PadState(this::emit);
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final TapHoldBindings gameplay;
    private ControllerBindings bindings;
    private boolean inputActive = true, blockRt, blockLt, closed, textInput;
    private int lifeEpoch = -1;
    private boolean forwardedLeftCtrl, forwardedRightCtrl;
    private int width, height;
    private float lookX, lookY, x, y;
    private boolean framePending, menu, controllerPresent;
    private int mouseButtons;
    private long previousFrame;

    HandheldInput(Activity activity) {
        this.activity = activity;
        bindings = ControllerBindings.load(activity);
        gameplay = new TapHoldBindings(bindings, new TapHoldBindings.Timer() {
            public void post(Runnable task, long delay) { timer.postDelayed(task, delay); }
            public void remove(Runnable task) { timer.removeCallbacks(task); }
        }, this::action);
        devices = (InputManager) activity.getSystemService(Context.INPUT_SERVICE);
        devices.registerInputDeviceListener(this, null);
        refreshDevices();
        resize();
        timer.post(watchState);
    }
    private final Runnable watchState = new Runnable() {
        public void run() {
            if (closed) return;
            if (inputActive) syncMode();
            timer.postDelayed(this, 100);
        }
    };
    void setGearMenu(Runnable action) { gameplay.setGear(action); }
    void setInputActive(boolean active) {
        if (inputActive == active) return;
        reset(); inputActive = active; gameplay.setActive(active);
        if (active) {
            blockRt = blockLt = true;
            ControllerBindings saved = ControllerBindings.load(activity);
            if (!saved.signature().equals(bindings.signature())) {
                bindings = saved; gameplay.setBindings(saved);
            }
        }
    }
    void touchCrouch(boolean down) {
        syncMode();
        if (inputActive && !menu) gameplay.physical(TapHoldBindings.TOUCH_CROUCH, TapHoldBindings.TOUCH, down);
    }
    void cancelTouchCrouch() { gameplay.cancelTouch(); }
    private void action(ControllerBindings.Action action, boolean down) {
        if (action == ControllerBindings.Action.MENU) emit(KeyEvent.KEYCODE_ESCAPE, down);
        else GameplayActions.send(action, down);
    }
    private void releaseModifiers() {
        if(forwardedLeftCtrl)emit(KeyEvent.KEYCODE_CTRL_LEFT,false);
        if(forwardedRightCtrl)emit(KeyEvent.KEYCODE_CTRL_RIGHT,false);
        forwardedLeftCtrl=forwardedRightCtrl=false;
    }
    private void restoreCtrlChord() {
        if(!forwardedLeftCtrl && gameplay.isDown(KeyEvent.KEYCODE_CTRL_LEFT)) {
            gameplay.cancelControl(KeyEvent.KEYCODE_CTRL_LEFT);emit(KeyEvent.KEYCODE_CTRL_LEFT,true);forwardedLeftCtrl=true;
        }
        if(!forwardedRightCtrl && gameplay.isDown(KeyEvent.KEYCODE_CTRL_RIGHT)) {
            gameplay.cancelControl(KeyEvent.KEYCODE_CTRL_RIGHT);emit(KeyEvent.KEYCODE_CTRL_RIGHT,true);forwardedRightCtrl=true;
        }
    }
    private void resize() {
        int w = activity.getWindow().getDecorView().getWidth();
        int h = activity.getWindow().getDecorView().getHeight();
        if (w <= 0 || h <= 0 || (w == width && h == height)) return;
        x = width > 0 ? x * w / width : w / 2f;
        y = height > 0 ? y * h / height : h / 2f;
        width = w; height = h;
    }
    private void refreshDevices() {
        controllerPresent = false;
        for (int id : devices.getInputDeviceIds()) {
            InputDevice d = devices.getInputDevice(id);
            if (d != null && (d.supportsSource(InputDevice.SOURCE_GAMEPAD) || d.supportsSource(InputDevice.SOURCE_JOYSTICK))) {
                controllerPresent = true;
                break;
            }
        }
    }
    @Override public void onInputDeviceAdded(int id) { refreshDevices(); }
    @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }
    @Override public void onInputDeviceRemoved(int id) {
        if (held.isDevice(id)) { reset(); held.reset(); gameplay.cancel(true); }
        refreshDevices();
    }
    void close() { closed=true; reset();timer.removeCallbacks(watchState);devices.unregisterInputDeviceListener(this); }
    private void syncMode() {
        int nativeState=GameplayActions.state();
        textInput=(nativeState & 128)!=0;
        int epoch = nativeState >>> 8;
        if (lifeEpoch != epoch) {
            releaseModifiers();
            lifeEpoch = epoch; held.changeContext(); gameplay.cancel(false); GameplayActions.reset(); stopMotion();
        }
        boolean visible = menu();
        if (visible != menu) {
            releaseModifiers();
            held.changeContext(); // Release in the original context; require release before rebinding.
            if(visible && gameplay.hasRose()) gameplay.pointerContext();
            else { gameplay.cancel(false); GameplayActions.reset(); }
            stopMotion();
            menu = visible;
            if (menu && controllerPresent) {
                resize();
                SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, x, y, false);
            }
        }
        if (menu) resize();
    }
    boolean cursorVisible() { syncMode(); return inputActive && menu && controllerPresent; }
    float cursorFractionX() { return width > 0 ? x / width : .5f; }
    float cursorFractionY() { return height > 0 ? y / height : .5f; }
    void setPointerFraction(float nx, float ny) {
        resize();
        x = Math.max(0, Math.min(width - 1, nx * width));
        y = Math.max(0, Math.min(height - 1, ny * height));
    }
    private static native boolean pointerShown();
    private boolean menu() {
        try { return pointerShown(); }
        catch (UnsatisfiedLinkError unavailable) { return false; }
    }
    static boolean controller(InputEvent e) {
        if (e.isFromSource(InputDevice.SOURCE_GAMEPAD) || e.isFromSource(InputDevice.SOURCE_JOYSTICK)) return true;
        // Some handheld drivers label button events as KEYBOARD/DPAD even though
        // the originating input device advertises its gamepad axes separately.
        InputDevice d = e.getDevice();
        return d != null && (d.supportsSource(InputDevice.SOURCE_GAMEPAD) || d.supportsSource(InputDevice.SOURCE_JOYSTICK));
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
        if (e.getAction() != KeyEvent.ACTION_DOWN && e.getAction() != KeyEvent.ACTION_UP) return false;
        final int code=e.getKeyCode();
        boolean pad=controller(e), down=e.getAction()==KeyEvent.ACTION_DOWN;
        if(!pad && !down && ((code==KeyEvent.KEYCODE_CTRL_LEFT && forwardedLeftCtrl)
                || (code==KeyEvent.KEYCODE_CTRL_RIGHT && forwardedRightCtrl))) {
            emit(code,false);gameplay.physical(code,TapHoldBindings.KEY,false);
            if(code==KeyEvent.KEYCODE_CTRL_LEFT)forwardedLeftCtrl=false;else forwardedRightCtrl=false;
            return true;
        }
        if(!pad && down && e.isCtrlPressed() && code!=KeyEvent.KEYCODE_CTRL_LEFT && code!=KeyEvent.KEYCODE_CTRL_RIGHT)
            restoreCtrlChord();
        if (!pad && !gameplay.assigned(code)) {
            if(!down)gameplay.physical(code,TapHoldBindings.KEY,false);
            return false;
        }
        if (!inputActive) { gameplay.physical(code,TapHoldBindings.KEY,down); return false; }
        syncMode();
        if (!pad && (e.isCtrlPressed() || e.isAltPressed() || e.isMetaPressed())
                && code!=KeyEvent.KEYCODE_CTRL_LEFT && code!=KeyEvent.KEYCODE_CTRL_RIGHT
                && code!=KeyEvent.KEYCODE_ALT_LEFT && code!=KeyEvent.KEYCODE_ALT_RIGHT
                && code!=KeyEvent.KEYCODE_META_LEFT && code!=KeyEvent.KEYCODE_META_RIGHT) {
            if(!down)gameplay.physical(code,TapHoldBindings.KEY,false);
            return false;
        }
        if (textInput && code!=KeyEvent.KEYCODE_BUTTON_START) {
            gameplay.observe(code,TapHoldBindings.KEY,down);
            if(!pad)return false;
            int textKey=code==KeyEvent.KEYCODE_BUTTON_A?KeyEvent.KEYCODE_ENTER:
                    code==KeyEvent.KEYCODE_BUTTON_B?KeyEvent.KEYCODE_ESCAPE:0;
            if(textKey==0)return false;
            if(accept(e.getDeviceId(),down))held.bind(code,textKey,down);
            return true;
        }
        if (code==KeyEvent.KEYCODE_BUTTON_R2 && blockRt) { if(!down)blockRt=false;return true; }
        if (code==KeyEvent.KEYCODE_BUTTON_L2 && blockLt) { if(!down)blockLt=false;return true; }
        if (!menu || code==KeyEvent.KEYCODE_BUTTON_START || gameplay.roseControl(code)) {
            if (!gameplay.assigned(code)) return false;
            if (accept(e.getDeviceId(),down)) gameplay.physical(code,TapHoldBindings.KEY,down);
            return true;
        }
        if(!pad) { gameplay.observe(code,TapHoldBindings.KEY,down);return false; }
        int value;
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: value = LEFT; break;
            case KeyEvent.KEYCODE_BUTTON_B: value = KeyEvent.KEYCODE_ESCAPE; break;
            case KeyEvent.KEYCODE_BUTTON_L2: value = RIGHT; break;
            case KeyEvent.KEYCODE_BUTTON_R2: value = LEFT; break;
            case KeyEvent.KEYCODE_BACK:
                if(!pad)return false;
                value = KeyEvent.KEYCODE_ESCAPE; break;
            case KeyEvent.KEYCODE_DPAD_UP: value=KeyEvent.KEYCODE_DPAD_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: value=KeyEvent.KEYCODE_DPAD_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: value=KeyEvent.KEYCODE_DPAD_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: value=KeyEvent.KEYCODE_DPAD_RIGHT; break;
            default: return false;
        }
        if (accept(e.getDeviceId(), down)) {
            gameplay.observe(code,TapHoldBindings.KEY,down);
            held.bind(code, value, down);
        }
        return true;
    }
    private boolean accept(int id,boolean deliberate) {
        if (!held.isDevice(id) && deliberate) {
            stopMotion(); gameplay.cancel(true); GameplayActions.reset();
        }
        return held.accept(id,deliberate);
    }
    private static InputDevice.MotionRange range(MotionEvent e, int axis) {
        InputDevice d = e.getDevice();
        if (d == null) return null;
        InputDevice.MotionRange fromSource = d.getMotionRange(axis, e.getSource());
        return fromSource == null ? d.getMotionRange(axis) : fromSource;
    }
    static int rightAxis(MotionEvent e, int primary, int alternate) {
        InputDevice.MotionRange a = range(e, primary), b = range(e, alternate);
        return PadState.rightAxis(a != null, a != null && a.getMin() < 0, b != null)
                == 0 ? primary : alternate;
    }
    private static float triggerAxis(MotionEvent e, int axis) {
        InputDevice.MotionRange r = range(e, axis);
        return r == null ? 0 : PadState.trigger(e.getAxisValue(axis), r.getMin(), r.getMax());
    }
    static float trigger(MotionEvent e, int axis, int alias, int legacy, int usedLookAxis) {
        float value = Math.max(triggerAxis(e, axis), triggerAxis(e, alias));
        return legacy == usedLookAxis ? value : Math.max(value, triggerAxis(e, legacy));
    }
    static float stick(MotionEvent e, int axis) {
        InputDevice.MotionRange r = range(e, axis);
        return PadState.stick(e.getAxisValue(axis), r == null ? .16f : Math.max(.16f, r.getFlat()));
    }
    boolean motion(MotionEvent e) {
        if (!controller(e)) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (held.isDevice(e.getDeviceId())) reset();
            return true;
        }
        if (e.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        if(inputActive)syncMode();
        float leftX = e.getAxisValue(MotionEvent.AXIS_X), leftY = e.getAxisValue(MotionEvent.AXIS_Y);
        int rightHorizontal = rightAxis(e, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX);
        int rightVertical = rightAxis(e, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY);
        float mx = stick(e, rightHorizontal), my = stick(e, rightVertical);
        float rt = trigger(e, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS, MotionEvent.AXIS_RZ, rightVertical);
        float lt = trigger(e, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_Z, rightHorizontal);
        float hatX=e.getAxisValue(MotionEvent.AXIS_HAT_X),hatY=e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (!inputActive) {
            gameplay.trigger(KeyEvent.KEYCODE_BUTTON_R2,rt);gameplay.trigger(KeyEvent.KEYCODE_BUTTON_L2,lt);
            hats(hatX,hatY);return false;
        }
        if(blockRt) { if(rt<=.30f)blockRt=false;rt=0; }
        if(blockLt) { if(lt<=.30f)blockLt=false;lt=0; }
        boolean deliberate = Math.abs(leftX) > .30f || Math.abs(leftY) > .30f ||
                mx != 0 || my != 0 || rt > .40f || lt > .40f || Math.abs(hatX)>.5f || Math.abs(hatY)>.5f;
        if (!accept(e.getDeviceId(), deliberate)) return true;
        held.bind(-10, KeyEvent.KEYCODE_A, !menu && leftX < -0.30f);
        held.bind(-11, KeyEvent.KEYCODE_D, !menu && leftX > 0.30f);
        held.bind(-12, KeyEvent.KEYCODE_W, !menu && leftY < -0.30f);
        held.bind(-13, KeyEvent.KEYCODE_S, !menu && leftY > 0.30f);
        if(menu) {
            boolean roseRt=gameplay.roseControl(KeyEvent.KEYCODE_BUTTON_R2),roseLt=gameplay.roseControl(KeyEvent.KEYCODE_BUTTON_L2);
            if(roseRt)gameplay.trigger(KeyEvent.KEYCODE_BUTTON_R2,rt);else gameplay.observeTrigger(KeyEvent.KEYCODE_BUTTON_R2,rt);
            if(roseLt)gameplay.trigger(KeyEvent.KEYCODE_BUTTON_L2,lt);else gameplay.observeTrigger(KeyEvent.KEYCODE_BUTTON_L2,lt);
            boolean roseLeft=menuHat(KeyEvent.KEYCODE_DPAD_LEFT,hatX<-.5f),roseRight=menuHat(KeyEvent.KEYCODE_DPAD_RIGHT,hatX>.5f);
            boolean roseUp=menuHat(KeyEvent.KEYCODE_DPAD_UP,hatY<-.5f),roseDown=menuHat(KeyEvent.KEYCODE_DPAD_DOWN,hatY>.5f);
            held.bindTrigger(-14, LEFT, roseRt?0:rt);held.bindTrigger(-15, RIGHT, roseLt?0:lt);
            held.bind(-20,KeyEvent.KEYCODE_DPAD_LEFT,hatX<-.5f&&!roseLeft);held.bind(-21,KeyEvent.KEYCODE_DPAD_RIGHT,hatX>.5f&&!roseRight);
            held.bind(-22,KeyEvent.KEYCODE_DPAD_UP,hatY<-.5f&&!roseUp);held.bind(-23,KeyEvent.KEYCODE_DPAD_DOWN,hatY>.5f&&!roseDown);
        } else {
            gameplay.trigger(KeyEvent.KEYCODE_BUTTON_R2,rt);gameplay.trigger(KeyEvent.KEYCODE_BUTTON_L2,lt);hats(hatX,hatY);
        }
        lookX = mx;
        lookY = my;
        if (menu && lookX == 0 && lookY == 0) {
            lookX = stick(e, MotionEvent.AXIS_X);
            lookY = stick(e, MotionEvent.AXIS_Y);
        }
        if ((lookX != 0 || lookY != 0) && !framePending) {
            framePending = true; previousFrame = 0; Choreographer.getInstance().postFrameCallback(this);
        }
        return true;
    }
    private void hats(float x,float y) {
        gameplay.physical(KeyEvent.KEYCODE_DPAD_LEFT,TapHoldBindings.AXIS,x<-.5f);
        gameplay.physical(KeyEvent.KEYCODE_DPAD_RIGHT,TapHoldBindings.AXIS,y<-.5f);
        gameplay.physical(KeyEvent.KEYCODE_DPAD_UP,TapHoldBindings.AXIS,y<-.5f);
        gameplay.physical(KeyEvent.KEYCODE_DPAD_DOWN,TapHoldBindings.AXIS,y>.5f);
    }
    private boolean menuHat(int code,boolean down) {
        boolean rose=gameplay.roseControl(code);
        if(rose)gameplay.physical(code,TapHoldBindings.AXIS,down);
        else gameplay.observe(code,TapHoldBindings.AXIS,down);
        return rose;
    }
    @Override public void doFrame(long now) {
        framePending = false;
        syncMode();
        if (lookX == 0 && lookY == 0) return;
        float dt = previousFrame == 0 ? 1f / 60 : Math.min(.05f, (now - previousFrame) / 1e9f);
        previousFrame = now; float speed = dt * (menu ? width * .65f : 850f);
        float dx = lookX * speed, dy = lookY * speed;
        if (menu) {
            // Full window coordinates, like touch; SwiffPlayer maps Flash margins itself.
            // Keep HUD cursors full-width for spawn and commander screens.
            x = Math.max(0, Math.min(width - 1, x + dx)); y = Math.max(0, Math.min(height - 1, y + dy));
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, x, y, false);
        } else SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, dx, dy, true);
        framePending = true; Choreographer.getInstance().postFrameCallback(this);
    }
    void reset() {
        releaseModifiers();
        held.changeContext();
        gameplay.cancel(false);GameplayActions.reset();
        stopMotion();
    }
    private void stopMotion() {
        lookX = lookY = 0;
        previousFrame = 0;
        Choreographer.getInstance().removeFrameCallback(this); framePending = false;
    }
}
