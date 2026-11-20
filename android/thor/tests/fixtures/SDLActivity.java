package org.libsdl.app;

import java.util.ArrayList;
import java.util.List;

/** Test-only observer for the production controller's SDL JNI output boundary. */
public final class SDLActivity {
    public static final class Mouse {
        public final int buttons,action;
        public final float x,y;
        public final boolean relative;
        Mouse(int buttons,int action,float x,float y,boolean relative) {
            this.buttons=buttons;this.action=action;this.x=x;this.y=y;this.relative=relative;
        }
    }
    public static final List<Mouse> mouse=new ArrayList<>();
    public static final List<String> keys=new ArrayList<>();
    public static void onNativeMouse(int buttons,int action,float x,float y,boolean relative) {
        mouse.add(new Mouse(buttons,action,x,y,relative));
    }
    public static void onNativeKeyDown(int key) { keys.add(key+"+"); }
    public static void onNativeKeyUp(int key) { keys.add(key+"-"); }
    public static Mouse lastMouse() { return mouse.get(mouse.size()-1); }
    public static void clear() { mouse.clear();keys.clear(); }
}
