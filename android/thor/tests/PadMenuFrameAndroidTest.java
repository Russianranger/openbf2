package com.mackillergr.bf2;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest=Config.NONE)
public class PadMenuFrameAndroidTest {
    private Activity activity;
    private PadMenuFrame frame;
    private final List<Integer> touches = new ArrayList<>();
    private float clickX;
    @Before public void open() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        View receiver = new View(activity);
        receiver.setFocusableInTouchMode(true);
        receiver.setOnTouchListener((view,event) -> {
            touches.add(event.getActionMasked());
            clickX = event.getX();
            return true;
        });
        frame = PadMenuFrame.wrap(receiver);
        activity.setContentView(frame);
        receiver.requestFocus();
        frame.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, 600, 400);
    }
    private KeyEvent r2(int action) {
        return new KeyEvent(100, 100, action, KeyEvent.KEYCODE_BUTTON_R2, 0, 0, 42, 0, 0, InputDevice.SOURCE_GAMEPAD);
    }
    private MotionEvent stick(float x) {
        MotionEvent.PointerProperties props = new MotionEvent.PointerProperties(); props.id = 0;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.setAxisValue(MotionEvent.AXIS_RX, x);
        return MotionEvent.obtain(100, 100, MotionEvent.ACTION_MOVE, 1,
            new MotionEvent.PointerProperties[]{props}, new MotionEvent.PointerCoords[]{coords},
            0, 0, 1, 1, 42, 0, InputDevice.SOURCE_JOYSTICK, 0);
    }
    @Test public void rightStickMovesAndTriggerClicksWithoutTouch() {
        MotionEvent move = stick(1);
        assertTrue(activity.dispatchGenericMotionEvent(move));
        move.recycle();
        frame.doFrame(System.nanoTime());
        MotionEvent center = stick(0);
        assertTrue(activity.dispatchGenericMotionEvent(center));
        center.recycle();
        assertTrue(activity.dispatchKeyEvent(r2(KeyEvent.ACTION_DOWN)));
        activity.dispatchKeyEvent(r2(KeyEvent.ACTION_DOWN));
        assertTrue(activity.dispatchKeyEvent(r2(KeyEvent.ACTION_UP)));
        assertEquals(Arrays.asList(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), touches);
        assertTrue("pointer moved from center: "+clickX+" / "+frame.getWidth(),clickX > frame.getWidth()*.5f);
    }
    @Test public void focusLossCancelsHeldClick() {
        assertTrue(frame.dispatchKeyEvent(r2(KeyEvent.ACTION_DOWN)));
        frame.onWindowFocusChanged(false);
        frame.dispatchKeyEvent(r2(KeyEvent.ACTION_UP));
        assertEquals(Arrays.asList(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), touches);
    }
    @Test public void dialogHasIndependentPointerAndKeepsItsButtons() {
        AlertDialog d = new AlertDialog.Builder(activity).setTitle("Offline career").setMessage("Private")
                .setPositiveButton("Close", null).create();
        PadMenuFrame.show(d);
        FrameLayout root=d.findViewById(android.R.id.content);
        assertTrue(root.getChildAt(0) instanceof PadMenuFrame);
        assertEquals("Close",d.getButton(-1).getText());
        assertTrue(d.getButton(-1).performClick());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertFalse(d.isShowing());
    }
}
