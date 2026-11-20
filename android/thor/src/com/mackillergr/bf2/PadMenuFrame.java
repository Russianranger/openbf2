package com.mackillergr.bf2;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** A right-stick pointer for the app's own Android menus. Android owns the system file picker. */
final class PadMenuFrame extends FrameLayout implements InputManager.InputDeviceListener, Choreographer.FrameCallback {
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final InputManager devices;
    private int controller = -1;
    private float x, y, dx, dy;
    private long lastFrame, pressTime;
    private boolean visible, keyPressed, axisPressed, touching, framePending;

    PadMenuFrame(Context context) {
        super(context);
        devices = context.getSystemService(InputManager.class);
        float dp = getResources().getDisplayMetrics().density;
        shape.moveTo(0, 0);
        shape.lineTo(3 * dp, 15 * dp);
        shape.lineTo(6 * dp, 10 * dp);
        shape.lineTo(12 * dp, 9 * dp);
        shape.close();
        arrow.setStrokeWidth(2 * dp);
        arrow.setStrokeJoin(Paint.Join.ROUND);
    }

    static PadMenuFrame wrap(View content) {
        PadMenuFrame frame = new PadMenuFrame(content.getContext());
        frame.addView(content, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        return frame;
    }

    static void show(AlertDialog dialog) {
        dialog.show();
        FrameLayout content = dialog.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() != 1) return;
        View panel = content.getChildAt(0);
        ViewGroup.LayoutParams layout = panel.getLayoutParams();
        content.removeView(panel);
        PadMenuFrame frame = new PadMenuFrame(content.getContext());
        frame.addView(panel, LayoutParams.MATCH_PARENT, layout.height);
        content.addView(frame, layout);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        x = oldW > 0 ? x * w / oldW : w * .5f;
        y = oldH > 0 ? y * h / oldH : h * .5f;
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!visible) return;
        int save = canvas.save();
        canvas.translate(x, y);
        arrow.setStyle(Paint.Style.FILL);
        arrow.setColor(0xffffffff);
        canvas.drawPath(shape, arrow);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setColor(0xff242424);
        canvas.drawPath(shape, arrow);
        canvas.restoreToCount(save);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!HandheldInput.controller(event)) return super.dispatchGenericMotionEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && event.getDeviceId() == controller) {
            resetPointer();
            return true;
        }
        if (event.getActionMasked() != MotionEvent.ACTION_MOVE) return super.dispatchGenericMotionEvent(event);
        int h = HandheldInput.rightAxis(event, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX);
        int v = HandheldInput.rightAxis(event, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY);
        float sx = HandheldInput.stick(event, h), sy = HandheldInput.stick(event, v);
        float rt = HandheldInput.trigger(event, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS,
                                         MotionEvent.AXIS_RZ, v);
        boolean down = rt > (axisPressed ? .25f : .45f);
        boolean relevant = sx != 0 || sy != 0 || down;
        if (event.getDeviceId() != controller && !relevant) return super.dispatchGenericMotionEvent(event);
        if (relevant) activate(event.getDeviceId());
        boolean handled = relevant || dx != 0 || dy != 0 || axisPressed;
        dx = sx;
        dy = sy;
        axisPressed = down;
        updatePress();
        if ((dx != 0 || dy != 0) && !framePending) {
            framePending = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
        return handled || super.dispatchGenericMotionEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (!HandheldInput.controller(e) || e.getKeyCode() != KeyEvent.KEYCODE_BUTTON_R2)
            return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            activate(e.getDeviceId());
            keyPressed = true;
        } else if (e.getDeviceId() == controller && e.getAction() == KeyEvent.ACTION_UP) {
            keyPressed = false;
        }
        updatePress();
        return true;
    }

    private void activate(int device) {
        if (controller != device) {
            resetPointer();
            controller = device;
        }
        if (!visible) { visible = true; invalidate(); }
    }

    private void updatePress() {
        boolean down = keyPressed || axisPressed;
        if (down == touching) return;
        touching = down;
        if (down) pressTime = SystemClock.uptimeMillis();
        sendTouch(down ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP);
    }

    private void sendTouch(int action) {
        MotionEvent e = MotionEvent.obtain(pressTime, SystemClock.uptimeMillis(), action, x, y, 0);
        try { super.dispatchTouchEvent(e); }
        finally { e.recycle(); }
    }

    @Override public void doFrame(long now) {
        if (dx == 0 && dy == 0) { framePending = false; lastFrame = 0; return; }
        float seconds = lastFrame == 0 ? 1f / 60 : Math.min(.05f, (now - lastFrame) / 1e9f);
        float distance = seconds * .65f * getWidth();
        lastFrame = now;
        x = Math.max(0, Math.min(getWidth() - 1, x + dx * distance));
        y = Math.max(0, Math.min(getHeight() - 1, y + dy * distance));
        if (touching) sendTouch(MotionEvent.ACTION_MOVE);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) resetPointer();
        return super.dispatchTouchEvent(event);
    }

    private void resetPointer() {
        Choreographer.getInstance().removeFrameCallback(this);
        framePending = false;
        lastFrame = 0;
        dx = dy = 0;
        if (touching) sendTouch(MotionEvent.ACTION_CANCEL);
        keyPressed = axisPressed = touching = visible = false;
        controller = -1;
        invalidate();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) resetPointer();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        devices.registerInputDeviceListener(this, null);
    }
    @Override protected void onDetachedFromWindow() {
        devices.unregisterInputDeviceListener(this);
        resetPointer();
        super.onDetachedFromWindow();
    }
    @Override public void onInputDeviceAdded(int id) {}
    @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }
    @Override public void onInputDeviceRemoved(int id) { if (controller == id) resetPointer(); }
}
