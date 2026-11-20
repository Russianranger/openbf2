package com.mackillergr.bf2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** [CHOSEN, not recovered] Android session actions, independent of virtual game controls. */
final class GameOptionsView extends FrameLayout {
    interface Actions {
        void opened(boolean open);
        void exportLogs();
        void launcher();
    }
    private static final int INK = 0xff171d20, PANEL = 0xff252c2f, SAND = 0xffd7c69a;
    private final Actions actions;
    private final Button gear;
    private PadMenuFrame modal;
    private ScrollView card;
    private Button returnButton;
    private final ArrayList<Button> choices = new ArrayList<>();
    private int confirmKey = -1, dismissKey = -1;
    private boolean axisTriggerArmed;
    private int previousAccessibility;

    GameOptionsView(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        setClipChildren(false);
        gear = new Button(context);
        gear.setText("\u2699");
        gear.setTextSize(26);
        gear.setTextColor(SAND);
        gear.setPadding(0, 0, 0, 0);
        gear.setMinWidth(0); gear.setMinHeight(0);
        gear.setContentDescription("Game options");
        gear.setTooltipText("Game options · hold Start on controller");
        gear.setBackground(buttonBackground());
        gear.setAlpha(.82f);
        gear.setOnClickListener(v -> setOpen(!isOpen()));
        LayoutParams tab = new LayoutParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL);
        tab.leftMargin = dp(6);
        addView(gear, tab);
    }

    boolean isOpen() { return modal != null; }
    void toggle() { setOpen(!isOpen()); }

    void setOpen(boolean open) {
        if (open == isOpen()) return;
        if (!open) {
            // Detach the pointer wrapper to cancel a held RT and its frame callbacks.
            removeView(modal); modal = null; card = null; returnButton = null; choices.clear();
            gear.setContentDescription("Game options");
            gear.setAlpha(.82f);
            setImportantForAccessibility(previousAccessibility);
            actions.opened(false);
            return;
        }
        previousAccessibility = getImportantForAccessibility();
        confirmKey = dismissKey = -1; axisTriggerArmed = false;
        gear.setContentDescription("Close game options");
        gear.setAlpha(1f);
        modal = new PadMenuFrame(getContext());
        modal.setBackgroundColor(0x99090c0e);
        modal.setClickable(true);
        modal.setOnClickListener(v -> setOpen(false));
        card = new ScrollView(getContext());
        card.setFillViewport(false);
        card.setClickable(true); // Tapping the card itself must not click through to the scrim.
        card.setBackground(panelBackground());
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(18));
        content.addView(label("FIELD OPTIONS", 12, SAND, true));
        TextView title = label("OpenBF2", 25, Color.WHITE, true);
        title.setPadding(0, dp(4), 0, dp(6));
        content.addView(title);
        TextView note = label("Your match stays open when you return to the launcher.", 13, 0xffb6bdbd, false);
        note.setPadding(0, 0, 0, dp(14));
        content.addView(note);
        returnButton = action(content, "Return to game", () -> setOpen(false));
        action(content, "Export logs", actions::exportLogs);
        action(content, "Return to launcher", actions::launcher);
        TextView hint = label("D-pad + A · right stick + RT · B to return", 11, 0xffa4aaa8, false);
        hint.setPadding(0, dp(12), 0, 0);
        content.addView(hint);
        card.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        modal.addView(card, cardLayout());
        // Keep the gear above the expanded panel so the same touch target closes it.
        addView(modal, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        actions.opened(true);
        returnButton.requestFocus();
        announceForAccessibility("Game options");
    }

    private LayoutParams cardLayout() {
        int width = Math.min(dp(420), Math.max(dp(220), getWidth() - dp(40)));
        int height = Math.min(dp(358), Math.max(dp(120), getHeight() - dp(32)));
        return new LayoutParams(width, height, Gravity.CENTER);
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (card != null) card.setLayoutParams(cardLayout());
    }
    private TextView label(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(getContext());
        text.setText(value); text.setTextSize(sp); text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }
    private Button action(LinearLayout parent, String title, Runnable action) {
        Button button = new Button(getContext()) {
            @Override public boolean onTouchEvent(MotionEvent event) {
                // Keep controller focus visible without making the first touch/RT click merely
                // transfer focus (Android otherwise consumes that ACTION_UP without a click).
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) requestFocus();
                return super.onTouchEvent(event);
            }
        };
        button.setText(title); button.setAllCaps(false); button.setTextSize(16);
        button.setFocusableInTouchMode(true);
        button.setTextColor(0xffe4e7df); button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(18), 0, dp(18), 0); button.setBackground(buttonBackground());
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(50));
        row.topMargin = dp(7); parent.addView(button, row);
        choices.add(button);
        return button;
    }
    private GradientDrawable panelBackground() {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{PANEL, INK});
        background.setCornerRadius(dp(4)); background.setStroke(dp(1), 0xff847d67);
        return background;
    }
    private StateListDrawable buttonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, tile(0xff716344, SAND));
        states.addState(new int[]{android.R.attr.state_focused}, tile(0xff474b3f, SAND));
        states.addState(new int[]{}, tile(0xe6272f32, 0xff747563));
        return states;
    }
    private GradientDrawable tile(int color, int edge) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(3)); shape.setStroke(dp(1), edge); return shape;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    boolean key(KeyEvent event) {
        if (!isOpen()) return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE ||
                code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BUTTON_START) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) dismissKey = code;
            if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean dismiss = dismissKey == code && !event.isCanceled();
                dismissKey = -1;
                if (dismiss) setOpen(false);
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_DPAD_CENTER ||
                code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_SPACE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) confirmKey = code;
            if (event.getAction() == KeyEvent.ACTION_UP && confirmKey == code && !event.isCanceled()) {
                View focused = findFocus();
                if (focused != null) focused.performClick();
            }
            if (event.getAction() == KeyEvent.ACTION_UP) confirmKey = -1;
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP ||
                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_TAB) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int current = choices.indexOf(findFocus());
                boolean back = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT ||
                        (code == KeyEvent.KEYCODE_TAB && event.isShiftPressed());
                int next = current < 0 ? 0 : (current + (back ? choices.size()-1 : 1)) % choices.size();
                choices.get(next).requestFocus();
            }
            return true;
        }
        modal.dispatchKeyEvent(event);
        return true; // Keyboard/gamepad input never reaches SDL while this menu is open.
    }
    boolean motion(MotionEvent event) {
        if (!isOpen()) return false;
        if (HandheldInput.controller(event) && event.getActionMasked() == MotionEvent.ACTION_MOVE && !axisTriggerArmed) {
            int vertical = HandheldInput.rightAxis(event, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY);
            float rt = HandheldInput.trigger(event, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS,
                    MotionEvent.AXIS_RZ, vertical);
            if (rt > .25f) return true; // A trigger held while opening cannot activate a menu item.
            axisTriggerArmed = true;
        }
        modal.dispatchGenericMotionEvent(event);
        return true;
    }
}
