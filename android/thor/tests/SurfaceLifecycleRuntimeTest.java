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
        @Implementation protected void onCreate(android.os.Bundle state) { calls.add("sdl:create-activity"); }
        @Implementation protected void onDestroy() { calls.add("sdl:destroy-activity"); }
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
    @Test public void optionsMenuSuspendsPresentationAndResumeKeepsOriginalArgs() {
        android.content.Intent original = new android.content.Intent();
        original.putExtra("args", new String[]{"--coop","--level","strike_at_karkand"});
        activity.setIntent(original);
        GameOptionsView menu = new GameOptionsView(activity, new GameOptionsView.Actions() {
            public void opened(boolean open) {
                ReflectionHelpers.callInstanceMethod(activity,"optionsChanged",
                        ReflectionHelpers.ClassParameter.from(boolean.class,open));
            }
            public void exportLogs() {}
            public void launcher() {}
        });
        ReflectionHelpers.setField(activity,"optionsMenu",menu);
        activity.onResume(); view.surfaceChanged(holder,0,1920,1080); activity.onWindowFocusChanged(true);
        calls.clear(); menu.setOpen(true);
        assertEquals("gate:false",calls.get(calls.size()-1));
        activity.onNewIntent(new android.content.Intent().putExtra("touch",false));
        assertFalse(menu.isOpen());assertFalse(activity.isFinishing());
        assertArrayEquals(original.getStringArrayExtra("args"),activity.getIntent().getStringArrayExtra("args"));
    }
    @Test public void launcherReturnPreservesSessionButExplicitEndClosesAndTerminatesGameProcess() throws Exception {
        org.robolectric.shadows.ShadowProcess.setPid(4242);
        org.robolectric.shadows.ShadowProcess.clearKilledProcesses();
        GameSession.hold(activity);
        ReflectionHelpers.callInstanceMethod(activity,"returnToLauncher");
        android.content.Intent first = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(LauncherActivity.class.getName(),first.getComponent().getClassName());
        assertTrue((first.getFlags() & android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)!=0);
        assertFalse(activity.isFinishing());assertFalse(org.robolectric.shadows.ShadowProcess.wasKilled(4242));
        assertNotNull(ReflectionHelpers.getStaticField(GameSession.class,"owner"));
        activity.onNewIntent(new android.content.Intent().putExtra("endSession",true));
        assertTrue(activity.isFinishing());
        assertEquals(LauncherActivity.class.getName(),org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        calls.clear();activity.onDestroy();
        assertTrue(calls.contains("sdl:destroy-activity"));
        assertFalse(GameSession.isActive(activity));
        assertTrue(org.robolectric.shadows.ShadowProcess.wasKilled(4242));
    }
    @Test public void staleArgumentlessResumeLeavesFreshProcessForNextDeployment() {
        org.robolectric.shadows.ShadowProcess.setPid(4243);
        org.robolectric.shadows.ShadowProcess.clearKilledProcesses();
        activity.setIntent(new android.content.Intent());
        activity.onCreate(null);
        assertTrue(activity.isFinishing());
        assertEquals(LauncherActivity.class.getName(),org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        activity.onDestroy();
        assertTrue(org.robolectric.shadows.ShadowProcess.wasKilled(4243));
    }
}
