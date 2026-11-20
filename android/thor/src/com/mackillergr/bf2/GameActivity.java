package com.mackillergr.bf2;

import android.os.Bundle;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import android.view.ViewGroup;
import org.libsdl.app.SDLActivity;

/** Runs the SDL game in the manifest's dedicated :game process. */
public final class GameActivity extends SDLActivity {
    private HandheldInput pad;
    private TouchOverlay overlay;
    private GameLoadingView loading;
    private boolean showTouchControls = true;
    private boolean resumed;
    private boolean surfaceReady;
    private static native void nativePresentationReady(boolean ready);
    private void updatePresentation(boolean focused) {
        if (!mBrokenLibraries) nativePresentationReady(resumed && focused && surfaceReady);
    }
    @Override protected org.libsdl.app.SDLSurface createSDLSurface(android.content.Context context) {
        return new org.libsdl.app.SDLSurface(context) {
            @Override public void surfaceCreated(android.view.SurfaceHolder holder) {
                surfaceReady = false;
                updatePresentation(hasWindowFocus());
                super.surfaceCreated(holder);
            }
            @Override public void surfaceChanged(android.view.SurfaceHolder holder, int format, int width, int height) {
                super.surfaceChanged(holder, format, width, height);
                // The SDL native window must be installed before a renderer can resume.
                surfaceReady = mIsSurfaceReady && width > 0 && height > 0 && holder.getSurface().isValid();
                updatePresentation(hasWindowFocus());
            }
            @Override public void surfaceDestroyed(android.view.SurfaceHolder holder) {
                surfaceReady = false;
                updatePresentation(false); // Close/drain Present before SDL releases the native window.
                super.surfaceDestroyed(holder);
            }
        };
    }
    private android.net.wifi.WifiManager.MulticastLock lanLock;
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        return pad != null && pad.key(e) || super.dispatchKeyEvent(e);
    }
    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        return pad != null && pad.motion(e) || super.dispatchGenericMotionEvent(e);
    }
    @Override protected void onPause() {
        resumed = false;
        updatePresentation(false);
        if (pad != null) pad.reset();
        setUiActive(false);
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        updatePresentation(hasWindowFocus());
        setUiActive(hasWindowFocus());
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        updatePresentation(focused);
        if (!focused && pad != null) pad.reset();
        setUiActive(resumed && focused);
        super.onWindowFocusChanged(focused);
    }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("touch")) {
            showTouchControls = intent.getBooleanExtra("touch", true);
            // Keep the original launch arguments when the launcher resumes this :game process.
            getIntent().putExtra("touch", showTouchControls);
            if (overlay != null) overlay.setControlsVisible(showTouchControls);
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("touch", showTouchControls);
        super.onSaveInstanceState(state);
    }
    private void setUiActive(boolean active) {
        if (overlay != null) overlay.setInputActive(active);
        if (loading != null) loading.setActive(active);
    }
    @Override protected void onDestroy() {
        resumed = false;
        updatePresentation(false);
        try {
            if (pad != null) { pad.close(); pad = null; }
            if (overlay != null) { overlay.setInputActive(false); overlay = null; }
            if (loading != null) { loading.close(); loading = null; }
            super.onDestroy();
        } finally { LanWifi.release(lanLock); GameSession.release(); }
    }
    private static final String TAG = "BF2Stage3";

    @Override protected String[] getLibraries() {
        // Original test13 DEX, GameActivity.getLibraries(): filled-new-array/range v0..v5.
        // Keep 'main' last; SDLActivity's default getMainSharedObject() uses last entry.
        return new String[] {"SDL3", "main"}; // Native linker resolves transitive dependencies.
    }

    @Override public void loadLibraries() {
        File folder = getExternalFilesDir(null);
        File errorFile = folder == null ? null : new File(folder, "native_loader_failure.txt");
        if (errorFile != null && errorFile.exists()) errorFile.delete();
        try {
            for (String name : getLibraries()) {
                File file = new File(getApplicationInfo().nativeLibraryDir, "lib" + name + ".so");
                Log.i(TAG, "Native library " + name + ": present=" + file.isFile()
                        + " bytes=" + (file.isFile() ? file.length() : -1));
            }
            super.loadLibraries();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library load failed", e);
            if (errorFile != null) {
                try (FileOutputStream out = new FileOutputStream(errorFile)) {
                    String message = "Native loader: " + e.getMessage()
                            + "\nlibdir=" + getApplicationInfo().nativeLibraryDir + "\n";
                    out.write(message.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
            }
            throw e;
        }
    }

    @Override protected String[] getArguments() {
        String[] args = getIntent().getStringArrayExtra("args");
        return args == null ? new String[0] : GameKey.append(this, args);
    }

    public void exportGameLog() {
        BF2LogExporter.request(this);
    }

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        BF2LogExporter.handleResult(this, request, result, data);
    }

    @Override public void onCreate(Bundle state) {
        String[] args = getIntent().getStringArrayExtra("args");
        // Always call Activity.onCreate before finish() to avoid SuperNotCalledException.
        if (args == null || args.length == 0) {
            Log.e(TAG, "No arguments supplied; refusing game start");
            super.onCreate(state);
            finish();
            return;
        }
        GameSession.hold(this);
        java.util.List<String> options = java.util.Arrays.asList(args);
        if (options.contains("--hosted") && !options.contains("--coop")) lanLock = LanWifi.acquire(this);
        // Only log selected mode; path/player may be private, CD key must never be logged.
        String mode = "UNKNOWN";
        for (String a : args) {
            if ("--coop".equals(a)) mode = "OFFLINE_COOP";
            if ("--menu".equals(a)) mode = "MENU";
            if ("--connect".equals(a)) mode = "DIRECT_LAN";
        }
        Log.i(TAG, "Launching native BF2: " + mode + " with " + args.length + " arguments");
        super.onCreate(state);
        showTouchControls = state != null && state.containsKey("touch")
                ? state.getBoolean("touch") : getIntent().getBooleanExtra("touch", true);
        // Feed touchscreen taps to SDL3 as PC mouse moves/clicks.
        // menuShown() switches automatically between Flash-menu pointing and FPS controls.
        if (!mBrokenLibraries) pad = new HandheldInput(this);
        if (!mBrokenLibraries) {
            loading = new GameLoadingView(this, args);
            addContentView(loading, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // The transparent pointer layer stays present when its virtual buttons are hidden.
            overlay = new TouchOverlay(this);
            overlay.setController(pad);
            overlay.setControlsVisible(showTouchControls);
            int gameWidth = getIntent().getIntExtra("gameWidth", 1280);
            int gameHeight = getIntent().getIntExtra("gameHeight", 720);
            overlay.setGameResolution(gameWidth, gameHeight);
            Log.i("BF2MenuTouch", "gameWidth=" + gameWidth + " gameHeight=" + gameHeight);
            addContentView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }
}
