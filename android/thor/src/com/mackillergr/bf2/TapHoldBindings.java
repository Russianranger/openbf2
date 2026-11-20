package com.mackillergr.bf2;

import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import static com.mackillergr.bf2.ControllerBindings.Action;

/** Combines duplicate key/axis reports and resolves tap versus hold before emitting an action. */
final class TapHoldBindings {
    interface Timer { void post(Runnable task, long delay); void remove(Runnable task); }
    interface Output { void action(Action action, boolean down); }
    static final int KEY = 1, AXIS = 2, TOUCH = 4;
    static final int TOUCH_CROUCH = -100;
    private final Timer timer;
    private final Output output;
    private final Map<Integer,Press> presses = new HashMap<>();
    private final Map<Action,Integer> outputs = new HashMap<>();
    private final Map<Runnable,Action> pulses = new HashMap<>();
    private ControllerBindings bindings;
    private Runnable gear;
    private boolean active = true;

    private final class Press {
        int sources;
        boolean blocked, held;
        Action emitting = Action.NONE;
        ControllerBindings.Binding binding;
        Runnable timeout;
    }
    TapHoldBindings(ControllerBindings bindings, Timer timer, Output output) {
        this.bindings=bindings; this.timer=timer; this.output=output;
    }
    void setGear(Runnable gear) { this.gear=gear; }
    private static boolean rose(Action action) { return action==Action.COMM_ROSE || action==Action.TACTICAL_ROSE; }
    boolean hasRose() { for(Action action:outputs.keySet())if(rose(action))return true;return false; }
    boolean roseControl(int code) { Press p=presses.get(code);return p!=null && rose(p.emitting); }
    void pointerContext() {
        for(Press p:presses.values()) {
            if(rose(p.emitting))continue;
            if(p.timeout!=null)timer.remove(p.timeout);
            p.timeout=null;p.emitting=Action.NONE;p.binding=null;p.blocked=p.sources!=0;
        }
        for(Runnable pulse:new ArrayList<>(pulses.keySet()))if(!rose(pulses.get(pulse))) {timer.remove(pulse);pulses.remove(pulse);}
        for(Action action:new ArrayList<>(outputs.keySet()))if(!rose(action)) {output.action(action,false);outputs.remove(action);}
    }
    void setBindings(ControllerBindings value) { cancel(false); bindings=value; }
    boolean assigned(int code) { return code==KeyEvent.KEYCODE_BUTTON_START || bindings.get(code)!=null; }
    boolean isDown(int code) { Press p=presses.get(code);return p!=null && p.sources!=0; }
    void cancelControl(int code) {
        Press p=presses.get(code);if(p==null)return;
        if(p.timeout!=null)timer.remove(p.timeout);
        if(p.emitting!=Action.NONE)emit(p.emitting,false);
        p.timeout=null;p.emitting=Action.NONE;p.binding=null;p.blocked=p.sources!=0;
    }
    private void emit(Action action, boolean down) {
        if(action==Action.NONE) return;
        int count=outputs.containsKey(action)?outputs.get(action):0;
        if(down) { outputs.put(action,count+1); if(count==0)output.action(action,true); }
        else if(count>0) { if(count==1) { outputs.remove(action);output.action(action,false); } else outputs.put(action,count-1); }
    }
    private void pulse(Action action) {
        if(action==Action.NONE)return;
        emit(action,true);
        Runnable end=new Runnable() { public void run() { pulses.remove(this);emit(action,false); } };
        pulses.put(end,action); timer.post(end,60);
    }
    void trigger(int code,float value) {
        Press p=presses.get(code);
        boolean down=p!=null && (p.sources&AXIS)!=0;
        physical(code,AXIS,value>(down?.30f:.40f));
    }
    void observe(int code,int source,boolean down) {
        Press p=presses.get(code);
        if(p==null) { p=new Press();presses.put(code,p); }
        p.sources=down?p.sources|source:p.sources&~source;
        p.blocked=p.sources!=0;
    }
    void observeTrigger(int code,float value) {
        Press p=presses.get(code);
        boolean down=p!=null && (p.sources&AXIS)!=0;
        observe(code,AXIS,value>(down?.30f:.40f));
    }
    void physical(int code,int source,boolean down) {
        Press p=presses.get(code);
        if(p==null) { p=new Press();presses.put(code,p); }
        int previous=p.sources;
        p.sources=down?p.sources|source:p.sources&~source;
        if(!active) { p.blocked=p.sources!=0;return; }
        if(p.blocked) { if(p.sources==0)p.blocked=false;return; }
        if(previous==0 && p.sources!=0) begin(code,p);
        else if(previous!=0 && p.sources==0) end(p);
    }
    private void begin(int code,Press p) {
        p.binding=code==TOUCH_CROUCH?new ControllerBindings.Binding(KeyEvent.KEYCODE_BUTTON_B,Action.CROUCH_TOGGLE,Action.PRONE):bindings.get(code);
        if(p.binding==null && code==KeyEvent.KEYCODE_BUTTON_START)
            p.binding=new ControllerBindings.Binding(code,Action.MENU,Action.NONE);
        if(p.binding==null)return;
        p.held=false;
        // A bare Ctrl tap runs on release. A later shortcut key can cancel it and
        // restore the raw modifier without first toggling the soldier's stance.
        if(p.binding.hold==Action.NONE && (code==KeyEvent.KEYCODE_CTRL_LEFT || code==KeyEvent.KEYCODE_CTRL_RIGHT))return;
        if(p.binding.hold==Action.NONE && code!=KeyEvent.KEYCODE_BUTTON_START) {
            p.emitting=p.binding.tap;emit(p.emitting,true);return;
        }
        p.timeout=() -> {
            p.timeout=null;
            if(!active || p.blocked || p.sources==0)return;
            p.held=true;
            if(code==KeyEvent.KEYCODE_BUTTON_START) { if(gear!=null)gear.run(); }
            else { p.emitting=p.binding.hold;emit(p.emitting,true); }
        };
        timer.post(p.timeout,ControllerBindings.HOLD_MILLIS);
    }
    private void end(Press p) {
        if(p.timeout!=null) { timer.remove(p.timeout);p.timeout=null; }
        if(p.emitting!=Action.NONE) { emit(p.emitting,false);p.emitting=Action.NONE; }
        else if(!p.held && p.binding!=null) pulse(p.binding.tap);
        p.binding=null;
    }
    void cancelTouch() {
        Press p=presses.get(TOUCH_CROUCH);
        if(p==null)return;
        if(p.timeout!=null)timer.remove(p.timeout);
        if(p.emitting!=Action.NONE)emit(p.emitting,false);
        presses.remove(TOUCH_CROUCH);
    }
    void cancel(boolean forgetDevices) {
        for(Press p:presses.values()) {
            if(p.timeout!=null)timer.remove(p.timeout);
            p.timeout=null;p.emitting=Action.NONE;p.binding=null;
            p.blocked=p.sources!=0;
        }
        for(Runnable pulse:pulses.keySet())timer.remove(pulse);
        pulses.clear();
        for(Action action:new ArrayList<>(outputs.keySet()))output.action(action,false);
        outputs.clear();
        if(forgetDevices)presses.clear();
    }
    void setActive(boolean enabled) { cancel(false);active=enabled; }
}
