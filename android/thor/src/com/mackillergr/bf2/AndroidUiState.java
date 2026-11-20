package com.mackillergr.bf2;

/** Game-thread publication; the Android UI never dereferences live engine objects. */
final class AndroidUiState {
    static final int STARTING = 0, LOADING = 1, INTRO = 2, MENU = 3, GAME = 4;
    private AndroidUiState() {}
    private static native int nativeSnapshot();
    private static native String nativeLevel();
    static int snapshot() {
        try { return nativeSnapshot(); }
        catch (UnsatisfiedLinkError notLoaded) { return STARTING; }
    }
    static String level() {
        try { return nativeLevel(); }
        catch (UnsatisfiedLinkError notLoaded) { return ""; }
    }
    static int phase(int snapshot) { return snapshot & 255; }
    static boolean isLoading(int snapshot) { return phase(snapshot) <= LOADING; }
    static boolean canSkipIntro(int snapshot) { return phase(snapshot) == INTRO && (snapshot & 256) != 0; }
    static int progress(int snapshot) { return (snapshot >>> 16) - 1; }
}
