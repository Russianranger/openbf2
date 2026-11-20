package com.mackillergr.bf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import org.libsdl.app.SDLActivity;
import java.util.HashMap;

/** On-screen controls. They feed SDL the same keys and mouse a PC player uses. */
public class TouchOverlay extends View {
    private static final int MOUSE_DOWN = 0, MOUSE_UP = 1, MOUSE_MOVE = 2, LEFT_BUTTON = 1;

    private static class Button {
        final String label; final int key; final float rx, ry; int pointer = -1;
        Button(String label, int key, float rx, float ry) { this.label = label; this.key = key; this.rx = rx; this.ry = ry; }
    }
    private final Button[] buttons = {
        new Button("Fire", 0, 0.90f, 0.62f),
        new Button("Jump", KeyEvent.KEYCODE_SPACE, 0.80f, 0.82f),
        new Button("Crouch", KeyEvent.KEYCODE_CTRL_LEFT, 0.90f, 0.86f),
        new Button("Sprint", KeyEvent.KEYCODE_SHIFT_LEFT, 0.70f, 0.86f),
        new Button("Reload", KeyEvent.KEYCODE_R, 0.80f, 0.60f),
        new Button("Use", KeyEvent.KEYCODE_E, 0.70f, 0.66f),
        new Button("Score", KeyEvent.KEYCODE_TAB, 0.05f, 0.35f),
        new Button("OK", KeyEvent.KEYCODE_ENTER, 0.95f, 0.38f),
        new Button("Menu", KeyEvent.KEYCODE_ESCAPE, 0.05f, 0.23f),
    };
    private final HashMap<Integer, float[]> look = new HashMap<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int stickPointer = -1;
    private float stickX, stickY, knobX, knobY;
    private boolean w, a, s, d;
    private boolean menu;
    private boolean controlsVisible = true;
    private boolean inputActive;
    private int phase = AndroidUiState.STARTING;
    private HandheldInput controller;
    private boolean cursorWasVisible;
    private final Path cursorShape = new Path();
    private int gameWidth = 1280;
    private int gameHeight = 720;
    private float lastTapX = -1, lastTapY = -1;
    private float menuX, menuY;
    public void setController(HandheldInput controller) {
        this.controller = controller;
        invalidate();
    }
    public void setControlsVisible(boolean visible) {
        if (controlsVisible == visible) return;
        releaseAll();
        controlsVisible = visible;
        lastTapX = lastTapY = -1;
        invalidate();
    }
    public void setInputActive(boolean active) {
        inputActive = active;
        removeCallbacks(watch);
        if (active && isAttachedToWindow()) post(watch);
        else releaseAll();
        invalidate();
    }
    public void setGameResolution(int width, int height) {
        if (width > 0 && height > 0) {
            gameWidth = width;
            gameHeight = height;
        }
        invalidate();
    }
    private float[] touchToSdl(float x, float y) {
        float[] p = MenuPointerMapper.map(x, y, getWidth(), getHeight(), gameWidth, gameHeight);
        if (controller != null && getWidth() > 0 && getHeight() > 0)
            controller.setPointerFraction(p[0] / getWidth(), p[1] / getHeight());
        return p;
    }
    private void traceTouch(String phase, float rawX, float rawY, float[] converted) {
        android.util.Log.i("BF2MenuTouch", phase + " raw=(" + rawX + "," + rawY
                + ") sdl=(" + converted[0] + "," + converted[1]
                + ") overlay=" + getWidth() + "x" + getHeight()
                + " game=" + gameWidth + "x" + gameHeight);
    }
    // Only an active, skippable native intro clip exposes the Escape action.
    private boolean introSkipVisible;
    private boolean introSkipHeld;
    private int introSkipPointer = -1;
    private long skipPressedAt = 0L;
    private final Runnable releaseSkip = () -> {
        if (introSkipPointer == -1 && introSkipHeld) {
            introSkipHeld = false;
            key(KeyEvent.KEYCODE_ESCAPE, false);
        }
    };
    static native boolean menuShown();
    private static native void toggleKeyboard();
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (!inputActive || !isAttachedToWindow()) return;
            refreshMode();
            boolean cursor = controllerCursorVisible();
            if (cursor || cursorWasVisible) invalidate();
            cursorWasVisible = cursor;
            postDelayed(this, cursor ? 16 : 250);
        }
    };
    private void refreshMode() {
        boolean now = menu;
        try { now = menuShown(); } catch (UnsatisfiedLinkError notLoaded) { }
        int state = AndroidUiState.snapshot();
        int nextPhase = AndroidUiState.phase(state);
        boolean skip = AndroidUiState.canSkipIntro(state);
        if (now != menu || phase != nextPhase || skip != introSkipVisible) {
            releaseAll();
            menu = now;
            phase = nextPhase;
            introSkipVisible = skip;
            invalidate();
        }
    }

    public TouchOverlay(Context context) {
        super(context);
        text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER); text.setFakeBoldText(true);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (inputActive) { removeCallbacks(watch); post(watch); }
    }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(watch);
        releaseAll();
        super.onDetachedFromWindow();
    }
    private void releaseAll() {
        for (Button b : buttons) if (b.pointer >= 0) {
            b.pointer = -1;
            if (b.key == KeyEvent.KEYCODE_CTRL_LEFT && controller != null) controller.cancelTouchCrouch();
            else press(b, false);
        }
        removeCallbacks(releaseSkip);
        introSkipPointer = -1;
        if (introSkipHeld) {
            introSkipHeld = false;
            key(KeyEvent.KEYCODE_ESCAPE, false);
        }
        if (menuPressed) SDLActivity.onNativeMouse(0, MOUSE_UP, menuX, menuY, false);
        menuPressed = false;
        menuPointer = -1;
        stickPointer = -1; setMove(0, 0); look.clear();
    }
    private float keyboardX() { return getWidth() * 0.04f; }
    private float keyboardY() { return getHeight() * 0.92f; }
    private int menuPointer = -1;
    private void menuTouch(MotionEvent e) {
        final int action = e.getActionMasked();
        final int index = e.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int id = e.getPointerId(index);
            if (menuPointer != -1) return;
            float x = e.getX(index), y = e.getY(index);
            if (controlsVisible && Math.hypot(x - keyboardX(), y - keyboardY()) < radius() * 1.1f) {
                toggleKeyboard();
                return;
            }
            float[] p = touchToSdl(x, y);
            menuX = p[0]; menuY = p[1];
            traceTouch("DOWN", x, y, p);
            menuPointer = id;
            lastTapX = x; lastTapY = y;
            SDLActivity.onNativeMouse(0, MOUSE_MOVE, p[0], p[1], false);
            SDLActivity.onNativeMouse(LEFT_BUTTON, MOUSE_DOWN, p[0], p[1], false);
            menuPressed = true;
            invalidate();
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (!menuPressed) return;
            int idx = e.findPointerIndex(menuPointer);
            if (idx < 0) return;
            float x = e.getX(idx), y = e.getY(idx);
            float[] p = touchToSdl(x, y);
            menuX = p[0]; menuY = p[1];
            lastTapX = x; lastTapY = y;
            SDLActivity.onNativeMouse(0, MOUSE_MOVE, p[0], p[1], false);
            invalidate();
        } else if (action == MotionEvent.ACTION_UP ||
                   action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!menuPressed) return;
            if (action != MotionEvent.ACTION_CANCEL && e.getPointerId(index) != menuPointer) return;
            int idx = action == MotionEvent.ACTION_CANCEL ? Math.max(0,e.findPointerIndex(menuPointer)) : index;
            float x = e.getX(idx), y = e.getY(idx);
            float[] p = touchToSdl(x, y);
            menuX = p[0]; menuY = p[1];
            traceTouch("UP", x, y, p);
            SDLActivity.onNativeMouse(0, MOUSE_MOVE, p[0], p[1], false);
            SDLActivity.onNativeMouse(0, MOUSE_UP, p[0], p[1], false);
            menuPointer = -1;
            menuPressed = false;
            invalidate();
        }
    }
    private boolean menuPressed;
    private float skipCenterX() { return getWidth() * 0.50f; }
    private float skipCenterY() { return getHeight() * 0.12f; }
    private float skipWidth() { return Math.max(160f, Math.min(260f, getWidth() * 0.22f)); }
    private float skipHeight() { return Math.max(40f, Math.min(64f, getHeight() * 0.09f)); }
    private boolean onSkipButton(float x, float y) {
        return Math.abs(x - skipCenterX()) < skipWidth() * 0.5f
                && Math.abs(y - skipCenterY()) < skipHeight() * 0.5f;
    }
    private void drawSkipButton(Canvas canvas) {
        float x = skipCenterX(), y = skipCenterY();
        float w = skipWidth(), h = skipHeight();
        fill.setColor(Color.argb(introSkipPointer >= 0 ? 240 : 210, 30, 37, 39));
        canvas.drawRoundRect(x - w * 0.5f, y - h * 0.5f,
                x + w * 0.5f, y + h * 0.5f, h * 0.19f, h * 0.19f, fill);
        fill.setColor(Color.rgb(215, 198, 154));
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Math.max(2f, h * 0.035f));
        canvas.drawRoundRect(x - w * 0.5f, y - h * 0.5f,
                x + w * 0.5f, y + h * 0.5f, h * 0.19f, h * 0.19f, fill);
        fill.setStyle(Paint.Style.FILL);
        text.setTextSize(h * 0.36f);
        canvas.drawText("SKIP INTRO", x, y + h * 0.13f, text);
    }
    private void releaseSkipTouch() {
        introSkipPointer = -1;
        long elapsed = android.os.SystemClock.uptimeMillis() - skipPressedAt;
        // Keep ESC down across at least a few SDL frames even for very quick taps.
        removeCallbacks(releaseSkip);
        postDelayed(releaseSkip, Math.max(0L, 180L - elapsed));
        invalidate();
    }
    private float radius() { return getHeight() * 0.075f; }
    private float stickRadius() { return getHeight() * 0.16f; }
    private float baseX() { return getWidth() * 0.14f; }
    private float baseY() { return getHeight() * 0.72f; }
    private boolean pointerMode() { return menu || phase == AndroidUiState.MENU; }
    private boolean loading() { return phase == AndroidUiState.STARTING || phase == AndroidUiState.LOADING; }
    private boolean controllerCursorVisible() {
        return inputActive && !loading() && phase != AndroidUiState.INTRO
                && controller != null && controller.cursorVisible();
    }
    private void drawControllerCursor(Canvas c) {
        if (!controllerCursorVisible()) return;
        float x = Math.max(0, Math.min(getWidth() - 1, controller.cursorFractionX() * getWidth()));
        float y = Math.max(0, Math.min(getHeight() - 1, controller.cursorFractionY() * getHeight()));
        float size = Math.max(18f, getHeight() * 0.025f);
        cursorShape.reset();
        cursorShape.moveTo(x, y);
        cursorShape.lineTo(x, y + size);
        cursorShape.lineTo(x + size * 0.28f, y + size * 0.72f);
        cursorShape.lineTo(x + size * 0.52f, y + size);
        cursorShape.lineTo(x + size * 0.70f, y + size * 0.90f);
        cursorShape.lineTo(x + size * 0.47f, y + size * 0.61f);
        cursorShape.lineTo(x + size * 0.85f, y + size * 0.59f);
        cursorShape.close();
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Math.max(2f, size * 0.13f));
        fill.setColor(Color.BLACK);
        c.drawPath(cursorShape, fill);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.WHITE);
        c.drawPath(cursorShape, fill);
    }

    @Override protected void onDraw(Canvas c) {
        drawTouchControls(c);
        drawControllerCursor(c);
    }
    private void drawTouchControls(Canvas c) {
        if (!inputActive || !controlsVisible || loading()) return;
        if (phase == AndroidUiState.INTRO) {
            if (introSkipVisible) drawSkipButton(c);
            return;
        }
        if (pointerMode()) {
            drawControl(c, "ABC", keyboardX(), keyboardY(), false);
            if (lastTapX >= 0) {
                fill.setColor(Color.argb(175, 215, 198, 154));
                c.drawCircle(lastTapX, lastTapY, Math.max(5f, radius()*0.09f), fill);
            }
            return;
        }
        fill.setColor(Color.argb(60, 23, 29, 32));
        c.drawCircle(baseX(), baseY(), stickRadius(), fill);
        fill.setStyle(Paint.Style.STROKE); fill.setStrokeWidth(Math.max(1.5f, radius() * .025f));
        fill.setColor(Color.argb(125, 215, 198, 154));
        c.drawCircle(baseX(), baseY(), stickRadius(), fill);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.argb(110, 168, 160, 132));
        c.drawCircle(stickPointer >= 0 ? knobX : baseX(), stickPointer >= 0 ? knobY : baseY(), radius() * 0.8f, fill);
        for (Button b : buttons) {
            float x = b.rx * getWidth(), y = b.ry * getHeight();
            drawControl(c, b.label.toUpperCase(java.util.Locale.ROOT), x, y, b.pointer >= 0);
        }
    }
    private void drawControl(Canvas c, String label, float x, float y, boolean pressed) {
        float rx = radius() * .9f, ry = radius() * .65f, corner = radius() * .08f;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(pressed ? Color.argb(180, 113, 99, 68) : Color.argb(105, 23, 29, 32));
        c.drawRoundRect(x-rx, y-ry, x+rx, y+ry, corner, corner, fill);
        fill.setStyle(Paint.Style.STROKE); fill.setStrokeWidth(Math.max(1.5f, radius() * .025f));
        fill.setColor(Color.argb(pressed ? 230 : 150, 215, 198, 154));
        c.drawRoundRect(x-rx, y-ry, x+rx, y+ry, corner, corner, fill);
        fill.setStyle(Paint.Style.FILL);
        text.setTextSize(radius() * .34f); text.setColor(Color.rgb(228, 231, 223));
        c.drawText(label, x, y + radius() * .12f, text);
    }
    private void key(int code, boolean down) {
        if (down) SDLActivity.onNativeKeyDown(code); else SDLActivity.onNativeKeyUp(code);
    }
    private void press(Button b, boolean down) {
        if (b.key == KeyEvent.KEYCODE_CTRL_LEFT && controller != null) {
            controller.touchCrouch(down);
            return;
        }
        // SDL takes the button state after the event, so a release carries 0.
        if (b.key == 0) SDLActivity.onNativeMouse(down ? LEFT_BUTTON : 0, down ? MOUSE_DOWN : MOUSE_UP, 0, 0, true);
        else key(b.key, down);
    }
    private void setMove(float x, float y) {
        boolean nw = y < -0.35f, ns = y > 0.35f, na = x < -0.35f, nd = x > 0.35f;
        if (nw != w) key(KeyEvent.KEYCODE_W, w = nw);
        if (ns != s) key(KeyEvent.KEYCODE_S, s = ns);
        if (na != a) key(KeyEvent.KEYCODE_A, a = na);
        if (nd != d) key(KeyEvent.KEYCODE_D, d = nd);
    }
    private Button hit(float x, float y) {
        for (Button b : buttons) {
            if (Math.hypot(x - b.rx * getWidth(), y - b.ry * getHeight()) < radius() * 1.1f) return b;
        }
        return null;
    }
    private void began(int id, float x, float y) {
        Button b = hit(x, y);
        if (b != null) { b.pointer = id; press(b, true); return; }
        if (stickPointer < 0 && Math.hypot(x - baseX(), y - baseY()) < stickRadius() * 1.5f) {
            stickPointer = id; stickX = baseX(); stickY = baseY(); moved(id, x, y); return;
        }
        look.put(id, new float[] {x, y, x, y, System.currentTimeMillis()});
    }
    private void moved(int id, float x, float y) {
        if (id == stickPointer) {
            float dx = x - stickX, dy = y - stickY, r = stickRadius(), len = (float) Math.hypot(dx, dy);
            if (len > r) { dx *= r / len; dy *= r / len; }
            knobX = stickX + dx; knobY = stickY + dy;
            setMove(dx / r, dy / r);
            return;
        }
        float[] p = look.get(id);
        if (p != null) {
            SDLActivity.onNativeMouse(0, MOUSE_MOVE, x - p[0], y - p[1], true);
            p[0] = x; p[1] = y;
        }
    }
    private void ended(int id) {
        for (Button b : buttons) if (b.pointer == id) { b.pointer = -1; press(b, false); }
        if (id == stickPointer) { stickPointer = -1; setMove(0, 0); }
        float[] p = look.remove(id);
        // A quick tap is a click on whatever is under it: the spawn screen, the HUD.
        if (p != null && System.currentTimeMillis() - p[4] < 250 && Math.hypot(p[0] - p[2], p[1] - p[3]) < 24) {
            SDLActivity.onNativeMouse(0, MOUSE_MOVE, p[2], p[3], false);
            SDLActivity.onNativeMouse(LEFT_BUTTON, MOUSE_DOWN, p[2], p[3], false);
            SDLActivity.onNativeMouse(0, MOUSE_UP, p[2], p[3], false);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!inputActive) return true;
        refreshMode();
        int action = e.getActionMasked(), index = e.getActionIndex();
        if (action == MotionEvent.ACTION_CANCEL) { releaseAll(); invalidate(); return true; }
        if (loading()) return true;
        if (!controlsVisible) {
            if (pointerMode() && phase != AndroidUiState.INTRO) menuTouch(e);
            return true;
        }
        // Floating actions are available only in their native phase, never over gameplay.
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int id = e.getPointerId(index);
            float x = e.getX(index), y = e.getY(index);
            if (introSkipVisible && introSkipPointer == -1 && onSkipButton(x, y)) {
                introSkipPointer = id;
                skipPressedAt = android.os.SystemClock.uptimeMillis();
                removeCallbacks(releaseSkip);
                if (!introSkipHeld) { introSkipHeld = true; key(KeyEvent.KEYCODE_ESCAPE, true); }
                invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int id = e.getPointerId(index);
            if (id == introSkipPointer) {
                releaseSkipTouch();
                return true;
            }
        }
        if (introSkipPointer != -1) return true;
        if (phase == AndroidUiState.INTRO) return true;
        if (pointerMode()) { menuTouch(e); return true; }
        switch (action) {
            case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_POINTER_DOWN:
                began(e.getPointerId(index), e.getX(index), e.getY(index)); break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); ++i) moved(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP:
                ended(e.getPointerId(index)); break;
            default: break;
        }
        invalidate();
        return true;
    }
}
