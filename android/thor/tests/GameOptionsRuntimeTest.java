package com.mackillergr.bf2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import static org.junit.Assert.*;

/** Actual Android view/focus/input behavior; callbacks stand in for Activity session actions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE, qualifiers="w1280dp-h720dp-land-mdpi", shadows={GameOptionsRuntimeTest.Axis.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GameOptionsRuntimeTest {
    // The controller decoder has its own real-device/range suite. Here inject its normalized
    // trigger output while testing the real modal view and Android pointer dispatch.
    @Implements(value=HandheldInput.class,isInAndroidSdk=false)
    public static class Axis {
        @Implementation protected static float trigger(MotionEvent e,int axis,int alias,int legacy,int look) {
            return e.getAxisValue(axis);
        }
    }
    final List<String> events = new ArrayList<>();
    GameOptionsView options;
    Activity activity;
    @Before public void setup() {
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        options=new GameOptionsView(activity,new GameOptionsView.Actions() {
            public void opened(boolean open) { events.add("open:"+open); }
            public void exportLogs() { events.add("export"); }
            public void launcher() { events.add("launcher"); }
        });
        activity.setContentView(options);
        layout(1280,720);
    }
    void layout(int width,int height) {
        options.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        options.layout(0,0,width,height);
    }
    Button button(View view,String text) {
        if(view instanceof Button && ((Button)view).getText().toString().equals(text))return (Button)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            Button found=button(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
    void key(int action,int code) { assertTrue(options.key(new KeyEvent(action,code))); }
    void press(int code) { key(KeyEvent.ACTION_DOWN,code);key(KeyEvent.ACTION_UP,code); }
    @Test public void collapsedGearIsAccessibleAndDoesNotCaptureGameTouches() {
        View gear=options.getChildAt(0);
        assertEquals("Game options",gear.getContentDescription());
        assertTrue(gear.isFocusable()&&gear.isClickable());
        assertTrue(gear.getWidth()>=48&&gear.getHeight()>=48);
        MotionEvent tap=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,640,360,0);
        assertFalse(options.dispatchTouchEvent(tap));tap.recycle();
        gear.performClick();layout(1280,720);
        assertTrue(options.isOpen());assertEquals("open:true",events.get(0));
        assertNotNull(button(options,"Return to game"));
        assertEquals(View.VISIBLE, gear.getVisibility());
        assertEquals("Close game options", gear.getContentDescription());
        int x=gear.getLeft()+gear.getWidth()/2, y=gear.getTop()+gear.getHeight()/2;
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent press=MotionEvent.obtain(100,100+action,action,x,y,0);
            assertTrue(options.dispatchTouchEvent(press));
            press.recycle();
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(options.isOpen());
        assertEquals("Game options", gear.getContentDescription());
    }
    @Test public void openingStartReleaseDoesNotDismissAndFreshBackDoes() {
        options.setOpen(true);layout(1280,720);
        key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_START);
        assertTrue(options.isOpen());
        press(KeyEvent.KEYCODE_BUTTON_B);
        assertFalse(options.isOpen());assertEquals("open:false",events.get(events.size()-1));
    }
    @Test public void controllerFocusCanReachAllThreeActions() {
        options.setOpen(true);layout(1280,720);
        assertEquals(button(options,"Return to game"),options.findFocus());
        press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_BUTTON_A);
        assertTrue(events.contains("export"));assertTrue(options.isOpen());
        press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_BUTTON_A);
        assertTrue(events.contains("launcher"));assertTrue(options.isOpen());
        press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_BUTTON_A);
        assertFalse(options.isOpen());
    }
    @Test public void closingRemovesPointerLayerAndSmallWindowStillScrolls() {
        options.setOpen(true);layout(640,320);
        assertEquals(2,options.getChildCount());
        press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(button(options,"Return to launcher"),options.findFocus());
        press(KeyEvent.KEYCODE_ESCAPE);
        assertEquals(1,options.getChildCount());
        assertEquals(View.VISIBLE,options.getChildAt(0).getVisibility());
    }
    @Test public void heldOpeningTriggerCannotClickUntilNeutral() {
        options.setOpen(true);layout(1280,720);
        trigger(1);trigger(1);
        assertEquals(1,events.size()); // Only opened(true); the held trigger did not click.
        trigger(0);trigger(1);trigger(0);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(events.contains("export")); // The centered pointer is over Export logs.
        assertTrue(options.isOpen());
    }
    void trigger(float value) {
        MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();p.id=0;p.toolType=MotionEvent.TOOL_TYPE_UNKNOWN;
        MotionEvent.PointerCoords c=new MotionEvent.PointerCoords();c.setAxisValue(MotionEvent.AXIS_RTRIGGER,value);
        MotionEvent e=MotionEvent.obtain(0,1,MotionEvent.ACTION_MOVE,1,new MotionEvent.PointerProperties[]{p},
                new MotionEvent.PointerCoords[]{c},0,0,1,1,7,0,android.view.InputDevice.SOURCE_JOYSTICK,0);
        try { assertTrue(options.motion(e)); } finally {e.recycle();}
    }
    @Test public void rendersActualMenu() throws Exception {
        String folder=System.getProperty("bf2.preview");
        if(folder==null)return;
        options.setBackgroundColor(0xff394244);
        write(new File(folder,"gear-closed.png"));
        options.setOpen(true);layout(1280,720);
        write(new File(folder,"gear-open.png"));
    }
    @Test public void rendersTouchControlsWithClearHudCorners() throws Exception {
        String folder=System.getProperty("bf2.preview");
        if(folder==null)return;
        new File(folder).mkdirs();
        android.widget.FrameLayout frame=new android.widget.FrameLayout(activity);
        frame.setBackgroundColor(0xff394244);
        TouchOverlay touch=new TouchOverlay(activity);
        org.robolectric.util.ReflectionHelpers.setField(touch,"phase",AndroidUiState.GAME);
        touch.setInputActive(true);
        frame.addView(touch,new ViewGroup.LayoutParams(-1,-1));
        activity.setContentView(frame);
        frame.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));
        frame.layout(0,0,1280,720);
        Bitmap bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);
        frame.draw(new Canvas(bitmap));
        try(FileOutputStream out=new FileOutputStream(new File(folder,"touch-controls.png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        bitmap.recycle();
        touch.setInputActive(false);
    }
    void write(File file) throws Exception {
        file.getParentFile().mkdirs();
        Bitmap bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);
        options.draw(new Canvas(bitmap));
        try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        bitmap.recycle();
    }
}
