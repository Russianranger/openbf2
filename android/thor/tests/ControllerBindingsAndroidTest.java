package com.mackillergr.bf2;

import android.app.AlertDialog;
import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowInputEvent;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import static org.junit.Assert.*;

/** Android 13 tests of the shipped persistence model and actual editor dialogs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ControllerBindingsAndroidTest {
    @Test public void atomicPersistenceUniqueKeysAndInvalidSettings() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        ControllerBindings before=ControllerBindings.load(c);
        assertEquals(ControllerBindings.Action.CROUCH_TOGGLE,before.get(KeyEvent.KEYCODE_BUTTON_B).tap);
        assertEquals(ControllerBindings.Action.PRONE,before.get(KeyEvent.KEYCODE_BUTTON_B).hold);
        String original=before.signature();
        before.set(new ControllerBindings.Binding(KeyEvent.KEYCODE_Q,ControllerBindings.Action.RELOAD,ControllerBindings.Action.USE));
        before.save(c);
        ControllerBindings reopened=ControllerBindings.load(c);
        assertEquals(before.signature(),reopened.signature()); assertNotEquals(original,reopened.signature());
        int count=reopened.list().size();
        reopened.set(new ControllerBindings.Binding(KeyEvent.KEYCODE_Q,ControllerBindings.Action.JUMP,ControllerBindings.Action.NONE));
        assertEquals(count,reopened.list().size()); reopened.save(c);
        assertEquals(ControllerBindings.Action.JUMP,ControllerBindings.load(c).get(KeyEvent.KEYCODE_Q).tap);
        reopened.remove(KeyEvent.KEYCODE_Q); reopened.save(c);
        assertNull(ControllerBindings.load(c).get(KeyEvent.KEYCODE_Q));
        try { new ControllerBindings.Binding(KeyEvent.KEYCODE_VOLUME_UP,ControllerBindings.Action.FIRE,ControllerBindings.Action.NONE); fail(); }
        catch(IllegalArgumentException expected) { }
        try { new ControllerBindings.Binding(KeyEvent.KEYCODE_BUTTON_START,ControllerBindings.Action.MENU,ControllerBindings.Action.PRONE); fail(); }
        catch(IllegalArgumentException expected) { }
        File settings=new File(c.getFilesDir(),"controller-bindings.json");
        Files.write(settings.toPath(),"broken settings".getBytes(StandardCharsets.UTF_8));
        ControllerBindings fallback=ControllerBindings.load(c);
        assertFalse(fallback.warning().isEmpty()); assertEquals(original,fallback.signature());
        assertEquals("broken settings",new String(Files.readAllBytes(settings.toPath()),StandardCharsets.UTF_8));
    }

    private static Button button(View view,String starts) {
        if(view instanceof Button && ((Button)view).getText().toString().startsWith(starts))return (Button)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            Button result=button(((ViewGroup)view).getChildAt(i),starts); if(result!=null)return result;
        }
        return null;
    }
    private static Button button(ControllerBindingsActivity a,String starts) {
        Button result=button(a.getWindow().getDecorView(),starts); assertNotNull(starts,result); return result;
    }
    private static void choose(AlertDialog dialog,int index) {
        assertNotNull(dialog.getListView());
        dialog.getListView().performItemClick(null,index,dialog.getListView().getAdapter().getItemId(index));
    }
    private static AlertDialog latest() { return ShadowAlertDialog.getLatestAlertDialog(); }
    private static void setTap(AlertDialog editor,ControllerBindings.Action action) {
        Button tap=button(editor.getWindow().getDecorView(),"Tap:"); assertNotNull(tap); tap.performClick();
        choose(latest(),action.ordinal());
    }
    private static KeyEvent key(int action,int key) {
        return new KeyEvent(0,10,action,key,0,0,15,0,0,InputDevice.SOURCE_GAMEPAD);
    }
    private static MotionEvent motion(float trigger,float hat) {
        InputDevice d=Shadow.newInstanceOf(InputDevice.class);
        ReflectionHelpers.setField(d,"mId",15);
        ReflectionHelpers.setField(d,"mSources",InputDevice.SOURCE_JOYSTICK|InputDevice.SOURCE_GAMEPAD);
        ReflectionHelpers.setField(d,"mMotionRanges",new java.util.ArrayList<>());
        for(int axis:new int[]{MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,MotionEvent.AXIS_RTRIGGER})
            ReflectionHelpers.callInstanceMethod(d,"addMotionRange",ClassParameter.from(int.class,axis),
                ClassParameter.from(int.class,InputDevice.SOURCE_JOYSTICK),ClassParameter.from(float.class,axis==MotionEvent.AXIS_RTRIGGER?0f:-1f),
                ClassParameter.from(float.class,1f),ClassParameter.from(float.class,0f),ClassParameter.from(float.class,0f),ClassParameter.from(float.class,0f));
        MotionEvent.PointerProperties p=new MotionEvent.PointerProperties(); p.id=0;
        MotionEvent.PointerCoords c=new MotionEvent.PointerCoords(); c.setAxisValue(MotionEvent.AXIS_RTRIGGER,trigger);c.setAxisValue(MotionEvent.AXIS_HAT_X,hat);
        MotionEvent e=MotionEvent.obtain(0,10,MotionEvent.ACTION_MOVE,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{c},0,0,1,1,15,0,InputDevice.SOURCE_JOYSTICK,0);
        ((ShadowInputEvent)Shadow.extract(e)).setDevice(d); return e;
    }
    private static AlertDialog capture(ControllerBindingsActivity a) {
        button(a,"Add a button").performClick(); choose(latest(),0); return latest();
    }

    @Test public void actualEditorChangesReopensRemovesAndRestores() {
        ActivityController<ControllerBindingsActivity> owner=Robolectric.buildActivity(ControllerBindingsActivity.class).setup();
        ControllerBindingsActivity a=owner.get();
        button(a,"B\n").performClick(); AlertDialog editor=latest();
        setTap(editor,ControllerBindings.Action.JUMP);
        editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(ControllerBindings.Action.JUMP,ControllerBindings.load(a).get(KeyEvent.KEYCODE_BUTTON_B).tap);
        owner.pause().stop().destroy(); owner=Robolectric.buildActivity(ControllerBindingsActivity.class).setup(); a=owner.get();
        assertTrue(button(a,"B\n").getText().toString().contains("Jump"));
        button(a,"B\n").performClick();latest().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        assertNull(ControllerBindings.load(a).get(KeyEvent.KEYCODE_BUTTON_B));
        button(a,"Restore defaults").performClick();latest().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(ControllerBindings.Action.CROUCH_TOGGLE,ControllerBindings.load(a).get(KeyEvent.KEYCODE_BUTTON_B).tap);
        button(a,"Start\n").performClick();assertFalse(button(latest().getWindow().getDecorView(),"Hold:").isEnabled()); latest().dismiss();
        owner.pause().stop().destroy();
    }

    @Test public void learningKeysAndTriggersWaitsForReleaseAndShowsCollision() {
        ActivityController<ControllerBindingsActivity> owner=Robolectric.buildActivity(ControllerBindingsActivity.class).setup();
        ControllerBindingsActivity a=owner.get(); AlertDialog capture=capture(a);
        assertTrue(capture.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_Q))); assertTrue(capture.isShowing());
        capture.dispatchKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_Q)); assertFalse(capture.isShowing());
        AlertDialog editor=latest(); setTap(editor,ControllerBindings.Action.RELOAD);editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(ControllerBindings.Action.RELOAD,ControllerBindings.load(a).get(KeyEvent.KEYCODE_Q).tap);
        // Robolectric does not implement nativeKeyCodeToString; the prefix still
        // distinguishes a keyboard key from the identically named controller button.
        assertTrue(ControllerBindings.keyName(KeyEvent.KEYCODE_Q).startsWith("Key "));
        capture=capture(a); MotionEvent down=motion(.8f,0);capture.dispatchGenericMotionEvent(down);down.recycle();
        assertTrue(capture.isShowing());MotionEvent up=motion(0,0);capture.dispatchGenericMotionEvent(up);up.recycle();
        assertFalse(capture.isShowing());AlertDialog collision=latest();
        assertEquals("Edit assignment",collision.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        collision.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        capture=capture(a);capture.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_R2));
        MotionEvent neutral=motion(0,0);capture.dispatchGenericMotionEvent(neutral);neutral.recycle();assertTrue(capture.isShowing());
        capture.dispatchKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R2));assertFalse(capture.isShowing());latest().dismiss();
        owner.pause().stop().destroy();
    }

    @Test public void learningHatAddsCanonicalDpadDirection() {
        ActivityController<ControllerBindingsActivity> owner=Robolectric.buildActivity(ControllerBindingsActivity.class).setup();
        ControllerBindingsActivity a=owner.get(); AlertDialog capture=capture(a);
        MotionEvent down=motion(0,-1);capture.dispatchGenericMotionEvent(down);down.recycle();assertTrue(capture.isShowing());
        MotionEvent up=motion(0,0);capture.dispatchGenericMotionEvent(up);up.recycle();assertFalse(capture.isShowing());
        AlertDialog editor=latest();setTap(editor,ControllerBindings.Action.WEAPON_1);editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(ControllerBindings.Action.WEAPON_1,ControllerBindings.load(a).get(KeyEvent.KEYCODE_DPAD_LEFT).tap);
        owner.pause().stop().destroy();
    }
}
