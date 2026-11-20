package com.mackillergr.bf2;

/** Named actions reach the normal native BF2 input samples, including stance constraints. */
final class GameplayActions {
    private GameplayActions() {}
    static native void nativeAction(int action, boolean down);
    static native void nativeReset();
    static native int nativeState();
    static void send(ControllerBindings.Action action, boolean down) {
        try { nativeAction(action.ordinal(), down); } catch (UnsatisfiedLinkError loading) { }
    }
    static void reset() { try { nativeReset(); } catch (UnsatisfiedLinkError loading) { } }
    static int state() { try { return nativeState(); } catch (UnsatisfiedLinkError loading) { return 0; } }
}
