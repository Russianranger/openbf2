package com.mackillergr.bf2;

import android.view.Surface;
import android.view.SurfaceHolder;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Executes production GameActivity surface/lifecycle callbacks. SDL/JNI and the native Surface
 * validity are fixture boundaries; this does not exercise a Vulkan driver. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE, shadows={SurfaceLifecycleRuntimeTest.NativeGate.class,
        SurfaceLifecycleRuntimeTest.SdlActivity.class, SurfaceLifecycleRuntimeTest.SdlSurface.class,
        SurfaceLifecycleRuntimeTest.NativeSurface.class})
public class SurfaceLifecycleRuntimeTest {
    static final List<String> calls = new ArrayList<>();
    static boolean valid;
    GameActivity activity;
    SDLSurface view;
    SurfaceHolder holder;
    @Implements(value=GameActivity.class, isInAndroidSdk=false)
    public static class NativeGate extends SdlActivity {
        @Implementation protected static void nativePresentationReady(boolean ready) { calls.add("gate:"+ready); }
    }
    @Implements(value=SDLActivity.class, isInAndroidSdk=false)
    public static class SdlActivity extends org.robolectric.shadows.ShadowActivity {
        @Implementation protected void onResume() { calls.add("sdl:resume"); }
        @Implementation protected void onPause() { calls.add("sdl:pause"); }
        @Implementation protected void onWindowFocusChanged(boolean focused) { calls.add("sdl:focus:"+focused); }
    }
    @Implements(value=SDLSurface.class, isInAndroidSdk=false)
    public static class SdlSurface extends org.robolectric.shadows.ShadowSurfaceView {
        @RealObject SDLSurface view;
        @Implementation protected void surfaceCreated(SurfaceHolder h) { calls.add("sdl:create"); }
        @Implementation protected void surfaceChanged(SurfaceHolder h, int f, int w, int v) {
            ReflectionHelpers.setField(view,"mIsSurfaceReady",true); calls.add("sdl:change");
        }
        @Implementation protected void surfaceDestroyed(SurfaceHolder h) {
            ReflectionHelpers.setField(view,"mIsSurfaceReady",false); calls.add("sdl:destroy");
        }
    }
    @Implements(Surface.class)
    public static class NativeSurface {
        @Implementation protected boolean isValid() { return valid; }
    }
    @Before public void setup() {
        activity=Robolectric.buildActivity(GameActivity.class).get();
        SDLActivity.mBrokenLibraries=false;
        view=activity.createSDLSurface(activity);
        Surface surface=org.robolectric.shadow.api.Shadow.newInstanceOf(Surface.class);
        holder=(SurfaceHolder)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SurfaceHolder.class},
                (proxy,method,args) -> method.getName().equals("getSurface") ? surface : null);
        valid=true; calls.clear();
    }
    @Test public void requiresResumedAndValidSurface() {
        activity.onResume();
        activity.onWindowFocusChanged(true);
        assertFalse(calls.contains("gate:true")); // Focus alone does not provide a native window.
        view.surfaceCreated(holder);
        assertEquals(Arrays.asList("gate:false","sdl:create"),calls.subList(calls.size()-2,calls.size()));
        view.surfaceChanged(holder,0,1920,1080);
        activity.onWindowFocusChanged(true);
        assertEquals("gate:true",calls.get(calls.size()-2));
    }
    @Test public void pausesBeforeSdlAndResumesAfterReplacement() {
        activity.onResume(); view.surfaceCreated(holder); view.surfaceChanged(holder,0,1920,1080);
        activity.onWindowFocusChanged(true); calls.clear();
        activity.onPause();
        assertEquals(Arrays.asList("gate:false","sdl:pause"),calls);
        calls.clear(); view.surfaceDestroyed(holder);
        assertEquals(Arrays.asList("gate:false","sdl:destroy"),calls);
        calls.clear(); activity.onResume(); activity.onWindowFocusChanged(true);
        assertFalse(calls.contains("gate:true"));
        view.surfaceCreated(holder); view.surfaceChanged(holder,0,1920,1080);
        activity.onWindowFocusChanged(true);
        assertEquals("gate:true",calls.get(calls.size()-2));
    }
    @Test public void invalidOrZeroSurfaceCannotResume() {
        activity.onResume(); valid=false;
        view.surfaceChanged(holder,0,1920,1080); activity.onWindowFocusChanged(true);
        assertFalse(calls.contains("gate:true"));
        valid=true; calls.clear();
        view.surfaceChanged(holder,0,0,1080); activity.onWindowFocusChanged(true);
        assertFalse(calls.contains("gate:true"));
    }
    @Test public void focusLossClosesBeforeSdlCallback() {
        activity.onResume(); view.surfaceChanged(holder,0,1920,1080); activity.onWindowFocusChanged(true);
        calls.clear(); activity.onWindowFocusChanged(false);
        assertEquals(Arrays.asList("gate:false","sdl:focus:false"),calls);
    }
}
