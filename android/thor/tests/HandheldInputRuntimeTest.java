package com.mackillergr.bf2;

import android.app.Activity;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libsdl.app.SDLActivity;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowInputDevice;
import org.robolectric.shadows.ShadowInputManager;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import static org.junit.Assert.*;

/** Actual production decoder with Android 13 MotionEvent/InputDevice objects.
 * JNI input/output and device enumeration are the observed fixture boundaries. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE, shadows={HandheldInputRuntimeTest.Mode.class,
        HandheldInputRuntimeTest.Actions.class,
        HandheldInputRuntimeTest.Device.class, HandheldInputRuntimeTest.Devices.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class HandheldInputRuntimeTest {
    static final Map<Integer,InputDevice> connected = new HashMap<>();
    static boolean menu;
    static int listeners;
    static int nativeState;
    static final ArrayList<String> actions = new ArrayList<>();
    HandheldInput pad;
    Activity activity;

    @Implements(value=HandheldInput.class, isInAndroidSdk=false)
    public static class Mode {
        @Implementation protected static boolean pointerShown() { return menu; }
    }
    @Implements(value=GameplayActions.class,isInAndroidSdk=false)
    public static class Actions {
        @Implementation protected static void nativeAction(int id,boolean down) {
            actions.add(ControllerBindings.Action.values()[id].name()+(down?"+":"-"));
        }
        @Implementation protected static void nativeReset() { }
        @Implementation protected static int nativeState() { return nativeState; }
    }
    @Implements(InputDevice.class)
    public static class Device extends ShadowInputDevice {
        @Implementation protected static InputDevice getDevice(int id) { return connected.get(id); }
    }
    @Implements(InputManager.class)
    public static class Devices extends ShadowInputManager {
        @Implementation protected int[] getInputDeviceIds() { return connected.keySet().stream().mapToInt(i -> i).toArray(); }
        @Implementation protected InputDevice getInputDevice(int id) { return connected.get(id); }
        @Implementation protected void registerInputDeviceListener(InputManager.InputDeviceListener l, Handler h) { listeners++; }
        @Implementation protected void unregisterInputDeviceListener(InputManager.InputDeviceListener l) { listeners--; }
    }
    static InputDevice controller(int id, boolean alternate, boolean aliases) {
        // API 33 predates InputDevice.Builder. Populate an actual platform device,
        // then invoke its range constructor through the framework's private helper.
        InputDevice d=org.robolectric.shadow.api.Shadow.newInstanceOf(InputDevice.class);
        ReflectionHelpers.setField(d,"mId",id);
        ReflectionHelpers.setField(d,"mName","fixture handheld");
        ReflectionHelpers.setField(d,"mSources",InputDevice.SOURCE_JOYSTICK|InputDevice.SOURCE_GAMEPAD);
        ReflectionHelpers.setField(d,"mMotionRanges",new java.util.ArrayList<>());
        range(d,MotionEvent.AXIS_X,-1); range(d,MotionEvent.AXIS_Y,-1);
        range(d,alternate?MotionEvent.AXIS_RX:MotionEvent.AXIS_Z,-1);
        range(d,alternate?MotionEvent.AXIS_RY:MotionEvent.AXIS_RZ,-1);
        range(d,aliases?MotionEvent.AXIS_GAS:MotionEvent.AXIS_RTRIGGER,0);
        range(d,aliases?MotionEvent.AXIS_BRAKE:MotionEvent.AXIS_LTRIGGER,0);
        return d;
    }
    static void range(InputDevice d,int axis,float minimum) {
        ReflectionHelpers.callInstanceMethod(d,"addMotionRange",ClassParameter.from(int.class,axis),
                ClassParameter.from(int.class,InputDevice.SOURCE_JOYSTICK),ClassParameter.from(float.class,minimum),
                ClassParameter.from(float.class,1f),ClassParameter.from(float.class,minimum<0?.16f:0f),
                ClassParameter.from(float.class,0f),ClassParameter.from(float.class,0f));
    }
    @Before public void setup() {
        connected.clear(); connected.put(7,controller(7,false,false)); listeners=0; menu=true;
        actions.clear();nativeState=0x111;
        SDLActivity.clear();
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getWindow().getDecorView().layout(0,0,1920,1080);
        pad=new HandheldInput(activity);
    }
    @After public void teardown() { if(pad!=null)pad.close(); assertEquals(0,listeners); if(activity!=null)activity.finish(); }
    static MotionEvent event(int id,int source,int action,float... axes) {
        MotionEvent.PointerProperties p=new MotionEvent.PointerProperties(); p.id=0;
        MotionEvent.PointerCoords c=new MotionEvent.PointerCoords();
        for(int i=0;i<axes.length;i+=2) c.setAxisValue((int)axes[i],axes[i+1]);
        MotionEvent e=MotionEvent.obtain(0,1,action,1,new MotionEvent.PointerProperties[]{p},
                new MotionEvent.PointerCoords[]{c},0,0,1,1,id,0,source,0);
        ((org.robolectric.shadows.ShadowInputEvent)org.robolectric.shadow.api.Shadow.extract(e)).setDevice(connected.get(id));
        return e;
    }
    void move(int id,int source,float... axes) {
        MotionEvent e=event(id,source,MotionEvent.ACTION_MOVE,axes);
        try { assertTrue(pad.motion(e)); } finally { e.recycle(); }
    }
    void trigger(float value) { move(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.AXIS_RTRIGGER,value); }
    static int count(int action) { return (int)SDLActivity.mouse.stream().filter(e -> e.action==action).count(); }
    void advance(long ms) { org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
    void button(int action,int key,int source) {
        KeyEvent e=new KeyEvent(0,1,action,key,0,0,7,0,0,source);
        ((org.robolectric.shadows.ShadowInputEvent)org.robolectric.shadow.api.Shadow.extract(e)).setDevice(connected.get(7));
        assertTrue(pad.key(e));
    }

    @Test public void menuCursorStartsWithoutTouchAndRtHoldsOneClick() {
        assertTrue(pad.cursorVisible()); assertEquals(.5f,pad.cursorFractionX(),.001f);
        move(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.AXIS_Z,.25f,MotionEvent.AXIS_RZ,.5f);
        pad.doFrame(1000000000L);
        assertTrue(pad.cursorFractionX()>.5f && pad.cursorFractionY()>.5f);
        assertFalse(SDLActivity.lastMouse().relative);
        SDLActivity.clear();
        trigger(.7f); trigger(.6f); trigger(.35f);
        assertEquals(1,count(MotionEvent.ACTION_DOWN)); assertEquals(0,count(MotionEvent.ACTION_UP));
        trigger(.2f); assertEquals(1,count(MotionEvent.ACTION_UP));
        assertEquals(MotionEvent.BUTTON_PRIMARY,SDLActivity.mouse.get(0).buttons);
        assertEquals(0,SDLActivity.lastMouse().buttons);
    }
    @Test public void closeMenuReleasesAndRequiresTriggerReleaseBeforeFiring() {
        pad.cursorVisible(); trigger(.8f); SDLActivity.clear();
        menu=false; assertFalse(pad.cursorVisible());
        assertEquals(1,count(MotionEvent.ACTION_UP)); assertFalse(SDLActivity.lastMouse().relative);
        trigger(.8f); trigger(.35f); assertEquals(0,count(MotionEvent.ACTION_DOWN));
        trigger(0); trigger(.8f);
        assertEquals(0,count(MotionEvent.ACTION_DOWN)); assertTrue(actions.contains("FIRE+"));
        trigger(0); SDLActivity.clear();
        move(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.AXIS_X,-1,MotionEvent.AXIS_Z,1);
        pad.doFrame(2000000000L);
        assertTrue(SDLActivity.keys.contains("29+")); // Android KEYCODE_A remains movement.
        assertTrue(SDLActivity.lastMouse().relative && SDLActivity.lastMouse().x>0);
        menu=true; pad.cursorVisible();
        assertTrue(SDLActivity.keys.contains("29-"));
    }
    @Test public void alternateAxesGamepadMotionAndKeyboardLabeledButtons() {
        connected.put(7,controller(7,true,true)); pad.onInputDeviceChanged(7); pad.cursorVisible();
        move(7,InputDevice.SOURCE_GAMEPAD,MotionEvent.AXIS_RX,1,MotionEvent.AXIS_RY,-1);
        pad.doFrame(1000000000L);
        assertTrue(pad.cursorFractionX()>.5f && pad.cursorFractionY()<.5f);
        SDLActivity.clear();
        button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_R2,InputDevice.SOURCE_KEYBOARD);
        move(7,InputDevice.SOURCE_GAMEPAD,MotionEvent.AXIS_GAS,.9f);
        button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R2,InputDevice.SOURCE_KEYBOARD);
        assertEquals(1,count(MotionEvent.ACTION_DOWN)); assertEquals(0,count(MotionEvent.ACTION_UP));
        move(7,InputDevice.SOURCE_GAMEPAD,MotionEvent.AXIS_GAS,0);
        assertEquals(1,count(MotionEvent.ACTION_UP));
    }
    @Test public void disconnectCancelResizeAndTouchKeepCursorConsistent() {
        pad.cursorVisible(); pad.setPointerFraction(.8f,.3f);
        assertEquals(.8f,pad.cursorFractionX(),.001f);
        activity.getWindow().getDecorView().layout(0,0,2560,1440); pad.cursorVisible();
        assertEquals(.8f,pad.cursorFractionX(),.001f); assertEquals(.3f,pad.cursorFractionY(),.001f);
        trigger(.9f); connected.clear(); pad.onInputDeviceRemoved(7);
        assertFalse(pad.cursorVisible()); assertEquals(1,count(MotionEvent.ACTION_UP));
        connected.put(7,controller(7,false,false)); pad.onInputDeviceAdded(7);
        assertTrue(pad.cursorVisible()); trigger(.9f);
        MotionEvent cancel=event(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.ACTION_CANCEL);
        assertTrue(pad.motion(cancel)); cancel.recycle();
        assertEquals(2,count(MotionEvent.ACTION_UP));
        assertFalse(pad.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP)));
    }
    @Test public void defaultBTapAndHoldAreExclusiveAndTouchUsesSamePath() {
        menu=false;pad.cursorVisible();
        button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B,InputDevice.SOURCE_GAMEPAD);
        advance(200);assertTrue(actions.isEmpty());
        button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B,InputDevice.SOURCE_GAMEPAD);
        assertEquals(java.util.Arrays.asList("CROUCH_TOGGLE+"),actions);
        advance(60);assertEquals(java.util.Arrays.asList("CROUCH_TOGGLE+","CROUCH_TOGGLE-"),actions);
        actions.clear();
        button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B,InputDevice.SOURCE_GAMEPAD);
        advance(449);assertTrue(actions.isEmpty());advance(1);
        assertEquals(java.util.Arrays.asList("PRONE+"),actions);
        button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B,InputDevice.SOURCE_GAMEPAD);
        advance(300);assertEquals(1,actions.size());
        button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B,InputDevice.SOURCE_GAMEPAD);
        assertEquals(java.util.Arrays.asList("PRONE+","PRONE-"),actions);
        actions.clear();pad.touchCrouch(true);advance(100);pad.touchCrouch(false);advance(60);
        assertEquals(java.util.Arrays.asList("CROUCH_TOGGLE+","CROUCH_TOGGLE-"),actions);
        actions.clear();pad.touchCrouch(true);pad.cancelTouchCrouch();advance(500);
        assertTrue(actions.isEmpty());
    }
    @Test public void deathFocusAndGearCancelPendingOrHeldActions() {
        menu=false;pad.cursorVisible();
        button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B,InputDevice.SOURCE_GAMEPAD);
        nativeState=0x200;advance(500);assertTrue(actions.isEmpty());
        button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B,InputDevice.SOURCE_GAMEPAD);
        nativeState=0x311;pad.cursorVisible();
        final int[] gear={0};pad.setGearMenu(() -> {gear[0]++;pad.setInputActive(false);});
        button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_START,InputDevice.SOURCE_GAMEPAD);
        advance(450);assertEquals(1,gear[0]);assertFalse(SDLActivity.keys.contains("111+"));
        assertFalse(pad.key(new KeyEvent(0,1,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_START,0,0,7,0,0,InputDevice.SOURCE_GAMEPAD)));
        MotionEvent close=event(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.ACTION_MOVE,MotionEvent.AXIS_RTRIGGER,.9f);
        assertFalse(pad.motion(close));close.recycle();
        pad.setInputActive(true);trigger(.9f);assertTrue(actions.isEmpty());
        trigger(0);trigger(.9f);assertEquals(java.util.Arrays.asList("FIRE+"),actions);
        pad.setInputActive(false);assertEquals(java.util.Arrays.asList("FIRE+","FIRE-"),actions);
    }
    @Test public void savedKeyboardHatTriggerActionsAndDuplicateReports() throws Exception {
        ControllerBindings config=ControllerBindings.defaults();
        config.set(new ControllerBindings.Binding(KeyEvent.KEYCODE_Q,ControllerBindings.Action.WEAPON_NEXT,ControllerBindings.Action.NONE));
        config.set(new ControllerBindings.Binding(KeyEvent.KEYCODE_DPAD_UP,ControllerBindings.Action.MAP,ControllerBindings.Action.NONE));
        config.set(new ControllerBindings.Binding(KeyEvent.KEYCODE_BUTTON_X,ControllerBindings.Action.NONE,ControllerBindings.Action.RELOAD));
        config.save(activity);pad.setInputActive(false);pad.setInputActive(true);menu=false;pad.cursorVisible();trigger(0);
        assertTrue(pad.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_Q)));
        assertTrue(pad.key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_Q)));
        assertEquals(java.util.Arrays.asList("WEAPON_NEXT+","WEAPON_NEXT-"),actions);
        assertFalse(pad.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_T)));
        actions.clear();move(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.AXIS_HAT_Y,-1);
        move(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.AXIS_HAT_Y,0);
        assertEquals(java.util.Arrays.asList("MAP+","MAP-"),actions);
        actions.clear();button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_R2,InputDevice.SOURCE_GAMEPAD);
        trigger(.8f);button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R2,InputDevice.SOURCE_GAMEPAD);
        assertEquals(java.util.Arrays.asList("FIRE+"),actions);trigger(0);
        assertEquals(java.util.Arrays.asList("FIRE+","FIRE-"),actions);
        actions.clear();button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_X,InputDevice.SOURCE_GAMEPAD);
        advance(450);assertEquals(java.util.Arrays.asList("RELOAD+"),actions);
        button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_X,InputDevice.SOURCE_GAMEPAD);
        assertEquals(java.util.Arrays.asList("RELOAD+","RELOAD-"),actions);
    }
    @Test public void keyboardCtrlTogglePreservesShortcutsAndNativeTextEditing() {
        menu=false;pad.cursorVisible();
        assertTrue(pad.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_CTRL_LEFT)));
        advance(200);assertTrue(actions.isEmpty());
        assertTrue(pad.key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_CTRL_LEFT)));
        assertEquals(java.util.Arrays.asList("CROUCH_TOGGLE+"),actions);advance(60);actions.clear();
        assertTrue(pad.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_CTRL_LEFT)));
        KeyEvent shortcut=new KeyEvent(0,1,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_C,0,KeyEvent.META_CTRL_ON);
        assertFalse(pad.key(shortcut));assertTrue(SDLActivity.keys.contains(KeyEvent.KEYCODE_CTRL_LEFT+"+"));
        assertTrue(pad.key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_CTRL_LEFT)));
        advance(500);assertTrue(actions.isEmpty());assertTrue(SDLActivity.keys.contains(KeyEvent.KEYCODE_CTRL_LEFT+"-"));
        nativeState=0x291;pad.cursorVisible(); // Console/chat/squad name editor active.
        assertFalse(pad.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_CTRL_LEFT)));
        assertFalse(pad.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_Z)));
        assertFalse(pad.key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_Z)));
        assertFalse(pad.key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_CTRL_LEFT)));
        assertTrue(actions.isEmpty());
        nativeState=0x311;pad.cursorVisible();
        assertTrue(pad.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_Z)));
        assertTrue(pad.key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_Z)));
        assertEquals(java.util.Arrays.asList("PRONE+","PRONE-"),actions);
    }
    @Test public void commRoseHoldSurvivesPointerModeAndDpadRelease() throws Exception {
        ControllerBindings config=ControllerBindings.defaults();
        config.set(new ControllerBindings.Binding(KeyEvent.KEYCODE_DPAD_UP,ControllerBindings.Action.COMM_ROSE,ControllerBindings.Action.NONE));
        config.save(activity);pad.setInputActive(false);pad.setInputActive(true);menu=false;pad.cursorVisible();trigger(0);
        move(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.AXIS_HAT_Y,-1);
        assertEquals(java.util.Arrays.asList("COMM_ROSE+"),actions);
        menu=true;pad.cursorVisible(); // Native keeps the same life epoch for an active HUD rose.
        assertEquals(java.util.Arrays.asList("COMM_ROSE+"),actions);
        move(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.AXIS_HAT_Y,-1,MotionEvent.AXIS_Z,.5f);pad.doFrame(1000000000L);
        assertFalse(SDLActivity.lastMouse().relative);
        move(7,InputDevice.SOURCE_JOYSTICK,MotionEvent.AXIS_HAT_Y,0);
        assertEquals(java.util.Arrays.asList("COMM_ROSE+","COMM_ROSE-"),actions);
    }
    @Test public void shortRoseTapPulseEndsAfterPointerTransition() throws Exception {
        ControllerBindings config=ControllerBindings.defaults();
        config.set(new ControllerBindings.Binding(KeyEvent.KEYCODE_BUTTON_X,ControllerBindings.Action.COMM_ROSE,ControllerBindings.Action.USE));
        config.save(activity);pad.setInputActive(false);pad.setInputActive(true);menu=false;pad.cursorVisible();trigger(0);
        button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_X,InputDevice.SOURCE_GAMEPAD);
        button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_X,InputDevice.SOURCE_GAMEPAD);
        assertEquals(java.util.Arrays.asList("COMM_ROSE+"),actions);
        menu=true;pad.cursorVisible();advance(60);
        assertEquals(java.util.Arrays.asList("COMM_ROSE+","COMM_ROSE-"),actions);
    }
    @Test public void sharedActionBindingsDoNotReleaseEachOtherAndNoneIsConsumed() throws Exception {
        ControllerBindings config=ControllerBindings.defaults();
        config.set(new ControllerBindings.Binding(KeyEvent.KEYCODE_BUTTON_A,ControllerBindings.Action.FIRE,ControllerBindings.Action.NONE));
        config.set(new ControllerBindings.Binding(KeyEvent.KEYCODE_BUTTON_B,ControllerBindings.Action.NONE,ControllerBindings.Action.NONE));
        config.save(activity);pad.setInputActive(false);pad.setInputActive(true);menu=false;pad.cursorVisible();trigger(0);
        button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,InputDevice.SOURCE_GAMEPAD);trigger(.9f);
        button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A,InputDevice.SOURCE_GAMEPAD);
        assertEquals(java.util.Arrays.asList("FIRE+"),actions);trigger(0);
        assertEquals(java.util.Arrays.asList("FIRE+","FIRE-"),actions);actions.clear();
        button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B,InputDevice.SOURCE_GAMEPAD);advance(600);
        button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B,InputDevice.SOURCE_GAMEPAD);
        assertTrue(actions.isEmpty());
    }
}
