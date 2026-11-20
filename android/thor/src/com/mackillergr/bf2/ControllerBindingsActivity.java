package com.mackillergr.bf2;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;

/** Edits app controller mappings; BF2 menus retain their fixed pointer bindings. */
public final class ControllerBindingsActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(25,33,28), PANEL = Color.rgb(39,49,43);
    private static final int GOLD = Color.rgb(216,180,101), TEXT = Color.rgb(244,241,231);
    private ControllerBindings bindings;
    private LinearLayout rows;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        bindings = ControllerBindings.load(this);
        LinearLayout page = column();
        page.setBackgroundColor(BACKGROUND);
        page.setPadding(dp(20),dp(14),dp(20),dp(16));
        TextView title = text("CONTROLLER BINDINGS", 24);
        title.setTextColor(GOLD); title.setTypeface(null, Typeface.BOLD); page.addView(title);
        page.addView(text("Gameplay assignments · menu RT remains left click", 15));
        page.addView(text("B defaults to tap for crouch / stand and hold for prone. A hold runs after 0.45 seconds without running the tap first. Holding Start always opens the controller menu.", 15));
        LinearLayout commands = new LinearLayout(this);
        commands.addView(button("Add a button or key", this::chooseControl), new LinearLayout.LayoutParams(0,dp(54),1));
        commands.addView(button("Restore defaults", this::resetDefaults), new LinearLayout.LayoutParams(0,dp(54),1));
        commands.addView(button("Done", this::finish), new LinearLayout.LayoutParams(0,dp(54),.6f));
        page.addView(commands);
        status = text("", 14); status.setTextColor(GOLD); page.addView(status);
        ScrollView scroll = new ScrollView(this);
        rows = column(); scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(PadMenuFrame.wrap(page));
        refresh();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String label, int size) {
        TextView v = new TextView(this); v.setText(label); v.setTextSize(size); v.setTextColor(TEXT);
        v.setPadding(dp(4),dp(5),dp(4),dp(5)); return v;
    }
    private GradientDrawable panel(boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(focused ? Color.rgb(58,66,48) : PANEL);
        background.setCornerRadius(dp(7)); background.setStroke(dp(focused ? 3 : 1), focused ? GOLD : Color.rgb(68,80,70));
        return background;
    }
    private Button button(String label, Runnable action) {
        Button v = new Button(this); v.setText(label); v.setTextColor(TEXT); v.setTextSize(16); v.setAllCaps(false);
        v.setMinHeight(dp(54)); v.setPadding(dp(16),dp(9),dp(16),dp(9));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed},panel(true));
        background.addState(new int[]{android.R.attr.state_focused},panel(true));
        background.addState(new int[]{},panel(false)); v.setBackground(background);
        v.setOnClickListener(view -> action.run()); return v;
    }
    private static String summary(ControllerBindings.Binding binding) {
        String hold = binding.keyCode == KeyEvent.KEYCODE_BUTTON_START ? "Controller menu (fixed)" : binding.hold.label;
        return "Tap: " + binding.tap.label + "    ·    Hold: " + hold;
    }
    private void refresh() {
        rows.removeAllViews();
        status.setText(bindings.warning().isEmpty() ? "Changes are saved on this device and apply when you return to the game." : bindings.warning());
        for (ControllerBindings.Binding binding : bindings.list()) {
            Button row = button(ControllerBindings.keyName(binding.keyCode) + "\n" + summary(binding), () -> edit(binding));
            row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1,-2); layout.bottomMargin=dp(7);
            rows.addView(row,layout);
        }
        if (bindings.list().isEmpty()) rows.addView(text("No gameplay assignments. Add a button or restore defaults.",16));
    }
    private void show(AlertDialog dialog) {
        PadMenuFrame.show(dialog);
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}) {
            Button b = dialog.getButton(which); if (b != null) b.setTextColor(GOLD);
        }
    }
    private void resetDefaults() {
        show(new AlertDialog.Builder(this).setTitle("Restore controller defaults?")
                .setMessage("This replaces your saved gameplay assignments. B returns to tap crouch / stand and hold prone.")
                .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w) -> save(ControllerBindings.defaults())).create());
    }
    private boolean save(ControllerBindings next) {
        try { next.save(this); bindings=next; refresh(); return true; }
        catch (IOException failure) { status.setText("Could not save controller settings: " + failure.getMessage()); return false; }
    }
    private void chooseControl() {
        final int[] keys={KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_R1,KeyEvent.KEYCODE_BUTTON_L2,KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL,KeyEvent.KEYCODE_BUTTON_THUMBR,KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_ESCAPE};
        String[] choices=new String[keys.length+1]; choices[0]="Detect a physical button or key…";
        for(int i=0;i<keys.length;i++) choices[i+1]=ControllerBindings.keyName(keys[i]);
        show(new AlertDialog.Builder(this).setTitle("Choose a control").setItems(choices,(d,index) -> {
            if(index==0) new CaptureDialog().show(); else select(keys[index-1],false);
        }).setNegativeButton("Cancel",null).create());
    }
    private void select(int key, boolean detected) {
        ControllerBindings.Binding existing=bindings.get(key);
        if (existing != null && detected) {
            show(new AlertDialog.Builder(this).setTitle(ControllerBindings.keyName(key) + " is already assigned")
                    .setMessage(summary(existing) + "\n\nEach physical control has one tap/hold assignment. Edit this assignment instead?")
                    .setNegativeButton("Cancel",null).setPositiveButton("Edit assignment",(d,w) -> edit(existing)).create());
        } else edit(existing != null ? existing : new ControllerBindings.Binding(key,ControllerBindings.Action.NONE,ControllerBindings.Action.NONE));
    }
    private void edit(ControllerBindings.Binding initial) {
        ControllerBindings.Action[] selected={initial.tap,initial.hold};
        LinearLayout content=column(); content.setPadding(dp(16),dp(5),dp(16),dp(8));
        content.addView(text("Without a hold action, the tap action starts immediately and follows the button press. With a hold action, a quick release runs the tap; holding runs only the hold action.",15));
        if(initial.keyCode==KeyEvent.KEYCODE_CTRL_LEFT || initial.keyCode==KeyEvent.KEYCODE_CTRL_RIGHT)
            content.addView(text("A Ctrl tap runs on release. Using Ctrl with another key keeps the normal keyboard shortcut instead.",15));
        final Button[] pickers=new Button[2];
        for(int i=0;i<2;i++) {
            final int index=i;
            pickers[i]=button((i==0?"Tap: ":"Hold: ")+selected[i].label,() -> chooseAction(selected,index,pickers));
            content.addView(pickers[i],new LinearLayout.LayoutParams(-1,-2));
        }
        if(initial.keyCode==KeyEvent.KEYCODE_BUTTON_START) {
            pickers[1].setText("Hold: Controller menu (fixed)"); pickers[1].setEnabled(false);
        }
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(ControllerBindings.keyName(initial.keyCode))
                .setView(content).setNegativeButton("Cancel",null).setNeutralButton("Remove",null).setPositiveButton("Save",null).create();
        show(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ControllerBindings next=ControllerBindings.load(this);
            next.set(new ControllerBindings.Binding(initial.keyCode,selected[0],selected[1]));
            if(save(next))dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            ControllerBindings next=ControllerBindings.load(this); next.remove(initial.keyCode);
            if(save(next))dialog.dismiss();
        });
    }
    private void chooseAction(ControllerBindings.Action[] selected,int index,Button[] pickers) {
        ControllerBindings.Action[] actions=ControllerBindings.Action.values();
        String[] labels=new String[actions.length]; int current=0;
        for(int i=0;i<actions.length;i++) { labels[i]=actions[i].label; if(actions[i]==selected[index])current=i; }
        show(new AlertDialog.Builder(this).setTitle(index==0?"Tap action":"Hold action")
                .setSingleChoiceItems(labels,current,(d,which) -> {
                    selected[index]=actions[which]; pickers[index].setText((index==0?"Tap: ":"Hold: ")+selected[index].label); d.dismiss();
                }).setNegativeButton("Cancel",null).create());
    }

    /** Consumes the whole press/release so learning a button cannot activate the next dialog. */
    private final class CaptureDialog extends AlertDialog {
        private int pending=KeyEvent.KEYCODE_UNKNOWN;
        private int device=Integer.MIN_VALUE;
        private boolean axisCapture;
        CaptureDialog() {
            super(ControllerBindingsActivity.this);
            setTitle("Detect a button or key");
            setMessage("Press and release a physical button, D-pad direction, trigger or keyboard key. Sticks stay reserved for movement and look. Esc or Back cancels.");
            setButton(BUTTON_NEGATIVE,"Cancel",(d,w) -> dismiss());
        }
        private void begin(int key,int id,boolean axis) {
            if(pending!=KeyEvent.KEYCODE_UNKNOWN || !ControllerBindings.canAssign(key)) return;
            pending=key; device=id; axisCapture=axis;
            setMessage("Release " + ControllerBindings.keyName(key) + " to continue.");
        }
        private void finishCapture() { int key=pending; dismiss(); select(key,true); }
        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            int key=e.getKeyCode();
            if(key==KeyEvent.KEYCODE_ESCAPE || key==KeyEvent.KEYCODE_BACK) { if(e.getAction()==KeyEvent.ACTION_UP)dismiss(); return true; }
            if(!ControllerBindings.canAssign(key) || e.getDeviceId()<0) return super.dispatchKeyEvent(e);
            if(e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0)begin(key,e.getDeviceId(),false);
            if(e.getAction()==KeyEvent.ACTION_UP && pending==key && device==e.getDeviceId())finishCapture();
            return true;
        }
        @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
            if(!HandheldInput.controller(e) || e.getActionMasked()!=MotionEvent.ACTION_MOVE)return super.dispatchGenericMotionEvent(e);
            int h=HandheldInput.rightAxis(e,MotionEvent.AXIS_Z,MotionEvent.AXIS_RX);
            int v=HandheldInput.rightAxis(e,MotionEvent.AXIS_RZ,MotionEvent.AXIS_RY);
            float rt=HandheldInput.trigger(e,MotionEvent.AXIS_RTRIGGER,MotionEvent.AXIS_GAS,MotionEvent.AXIS_RZ,v);
            float lt=HandheldInput.trigger(e,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_BRAKE,MotionEvent.AXIS_Z,h);
            float hx=e.getAxisValue(MotionEvent.AXIS_HAT_X),hy=e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if(pending==KeyEvent.KEYCODE_UNKNOWN) {
                if(rt>.5f)begin(KeyEvent.KEYCODE_BUTTON_R2,e.getDeviceId(),true);
                else if(lt>.5f)begin(KeyEvent.KEYCODE_BUTTON_L2,e.getDeviceId(),true);
                else if(hx<-.5f)begin(KeyEvent.KEYCODE_DPAD_LEFT,e.getDeviceId(),true);
                else if(hx>.5f)begin(KeyEvent.KEYCODE_DPAD_RIGHT,e.getDeviceId(),true);
                else if(hy<-.5f)begin(KeyEvent.KEYCODE_DPAD_UP,e.getDeviceId(),true);
                else if(hy>.5f)begin(KeyEvent.KEYCODE_DPAD_DOWN,e.getDeviceId(),true);
            } else if(e.getDeviceId()==device && axisCapture) {
                boolean released=(pending==KeyEvent.KEYCODE_BUTTON_R2 && rt<.2f)||(pending==KeyEvent.KEYCODE_BUTTON_L2 && lt<.2f)
                    ||((pending==KeyEvent.KEYCODE_DPAD_LEFT || pending==KeyEvent.KEYCODE_DPAD_RIGHT)&&Math.abs(hx)<.2f)
                    ||((pending==KeyEvent.KEYCODE_DPAD_UP || pending==KeyEvent.KEYCODE_DPAD_DOWN)&&Math.abs(hy)<.2f);
                if(released)finishCapture();
            }
            return true;
        }
    }
}
