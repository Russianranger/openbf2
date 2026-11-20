package com.mackillergr.bf2;

public final class PadStateTest {
    static void check(boolean passed) { if (!passed) throw new AssertionError(); }
    public static void main(String[] args) {
        StringBuilder log = new StringBuilder();
        PadState pad = new PadState((key, pressed) -> log.append(key).append(pressed ? "+" : "-"));
        check(!pad.accept(5, false)); // Neutral noise from an idle device cannot disconnect another.
        check(pad.accept(7, true));
        pad.bind(1, 50, true); pad.bind(1, 50, true);
        pad.bind(2, 50, true); pad.bind(1, 50, false);
        check(log.toString().equals("50+")); // A trigger can send a button and an axis.
        check(!pad.accept(5, false) && pad.isDevice(7));
        check(pad.accept(5, true) && log.toString().equals("50+50-"));
        pad.bind(3, 42, true); pad.bind(3, 48, true); pad.reset(); pad.reset();
        check(log.toString().equals("50+50-42+42-48+48-"));
        check(!pad.isDevice(5));
        check(PadState.trigger(-1,-1,1) == 0 && PadState.trigger(1,-1,1) == 1);
        check(PadState.trigger(0,0,1) == 0 && PadState.trigger(.7f,0,1) == .7f);
        check(PadState.rightAxis(false,false,true) == 1);
        check(PadState.rightAxis(true,false,true) == 1);
        check(PadState.rightAxis(true,true,true) == 0);
        log.setLength(0); pad.accept(7,true);
        pad.bindTrigger(14,-1,.8f); pad.bindTrigger(14,-1,.35f);
        check(log.toString().equals("-1+"));
        pad.changeContext(); pad.bindTrigger(14,-1,.8f);
        check(log.toString().equals("-1+-1-"));
        pad.bindTrigger(14,-1,.35f); pad.bindTrigger(14,-1,0);
        pad.bindTrigger(14,-1,.8f); pad.reset();
        check(log.toString().equals("-1+-1--1+-1-"));
        check(PadState.stick(.12f,.16f)==0 && PadState.stick(Float.NaN,.16f)==0);
        check(PadState.stick(.25f,.16f)==.25f && PadState.stick(2,.16f)==1);
    }
}
