#!/usr/bin/env python3
"""Exercise the production overlay with recorded SDL calls and a small Android view fixture.

This is a JVM input-state check, not an Android window or rendered-game test.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    "android/content/Context.java": "package android.content; public class Context {}",
    "android/util/Log.java": "package android.util; public class Log { public static int i(String a,String b){return 0;} }",
    "android/os/SystemClock.java": "package android.os; public class SystemClock { public static long uptimeMillis(){return 1000;} }",
    "android/graphics/Color.java": "package android.graphics; public class Color { public static final int WHITE=-1,BLACK=0; public static int argb(int a,int r,int g,int b){return 0;} public static int rgb(int r,int g,int b){return 0;} }",
    "android/graphics/Path.java": "package android.graphics; public class Path { public void reset(){} public void moveTo(float x,float y){} public void lineTo(float x,float y){} public void close(){} }",
    "android/graphics/Paint.java": """package android.graphics;
public class Paint { public static final int ANTI_ALIAS_FLAG=1; public enum Align {CENTER}
public enum Style {STROKE,FILL} public Paint(int flags){} public void setColor(int c){}
public void setTextAlign(Align a){} public void setFakeBoldText(boolean b){}
public void setTextSize(float s){} public void setStyle(Style s){} public void setStrokeWidth(float f){} }
""",
    "android/graphics/Canvas.java": """package android.graphics;
public class Canvas { public java.util.List<String> labels=new java.util.ArrayList<>(); public int shapes,paths;
public void drawPath(Path path,Paint p){shapes++;paths++;}
public void drawText(String s,float x,float y,Paint p){labels.add(s);}
public void drawCircle(float x,float y,float r,Paint p){shapes++;}
public void drawRoundRect(float a,float b,float c,float d,float e,float f,Paint p){shapes++;} }
""",
    "android/view/KeyEvent.java": """package android.view;
public class KeyEvent { public static final int KEYCODE_SPACE=62,KEYCODE_CTRL_LEFT=113,
KEYCODE_SHIFT_LEFT=59,KEYCODE_R=46,KEYCODE_E=33,KEYCODE_TAB=61,KEYCODE_ENTER=66,
KEYCODE_ESCAPE=111,KEYCODE_W=51,KEYCODE_A=29,KEYCODE_S=47,KEYCODE_D=32; }
""",
    "android/view/MotionEvent.java": """package android.view;
public class MotionEvent { public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,
ACTION_CANCEL=3,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6;
private final int action; private final float x,y;
public MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
public int getActionMasked(){return action;} public int getActionIndex(){return 0;}
public int getPointerId(int i){return 7;} public int getPointerCount(){return 1;}
public int findPointerIndex(int id){return id==7?0:-1;}
public float getX(int i){return x;} public float getY(int i){return y;} }
""",
    "android/view/View.java": """package android.view;
public class View { private final android.content.Context c; private boolean attached;
private final java.util.List<Runnable> tasks=new java.util.ArrayList<>();
public View(android.content.Context c){this.c=c;} public android.content.Context getContext(){return c;}
public int getWidth(){return 1920;} public int getHeight(){return 1080;}
public boolean isAttachedToWindow(){return attached;} public void invalidate(){}
public boolean post(Runnable r){tasks.add(r);return true;}
public boolean postDelayed(Runnable r,long t){tasks.add(r);return true;}
public boolean removeCallbacks(Runnable r){return tasks.removeIf(v->v==r);}
protected void onAttachedToWindow(){attached=true;} protected void onDetachedFromWindow(){attached=false;}
protected void onDraw(android.graphics.Canvas c){} public boolean onTouchEvent(MotionEvent e){return false;}
public void runNext(){if(!tasks.isEmpty())tasks.remove(0).run();}
public int pending(){return tasks.size();} }
""",
    "org/libsdl/app/SDLActivity.java": """package org.libsdl.app;
public class SDLActivity {
public static class Event { public int key,button,action; public boolean down,relative; public float x,y;
Event(int key,boolean down){this.key=key;this.down=down;}
Event(int b,int a,float x,float y,boolean r){button=b;action=a;this.x=x;this.y=y;relative=r;} }
public static final java.util.List<Event> events=new java.util.ArrayList<>();
public static void onNativeKeyDown(int k){events.add(new Event(k,true));}
public static void onNativeKeyUp(int k){events.add(new Event(k,false));}
public static void onNativeMouse(int b,int a,float x,float y,boolean r){events.add(new Event(b,a,x,y,r));} }
""",
    "com/mackillergr/bf2/AndroidUiState.java": """package com.mackillergr.bf2;
// Native publisher fixture; the production overlay still chooses every input/drawing action.
public class AndroidUiState { public static final int STARTING=0,LOADING=1,INTRO=2,MENU=3,GAME=4;
public static int state; public static int snapshot(){return state;}
public static int phase(int value){return value&255;}
public static boolean canSkipIntro(int value){return phase(value)==INTRO && (value&256)!=0;} }
""",
    "com/mackillergr/bf2/GameActivity.java": """package com.mackillergr.bf2;
public class GameActivity extends android.content.Context { public int exports; public void exportGameLog(){exports++;} }
""",
    "com/mackillergr/bf2/HandheldInput.java": """package com.mackillergr.bf2;
// Controller/JNI fixture: draw/input coordination is exercised in the actual TouchOverlay.
public class HandheldInput { public boolean visible; public float x=.5f,y=.5f; public int crouchDown,crouchUp,crouchCancel;
public boolean cursorVisible(){return visible;} public float cursorFractionX(){return x;}
public float cursorFractionY(){return y;} public void setPointerFraction(float x,float y){this.x=x;this.y=y;}
public void touchCrouch(boolean down){if(down)crouchDown++;else crouchUp++;} public void cancelTouchCrouch(){crouchCancel++;} }
""",
}

TEST = r"""package com.mackillergr.bf2;
import android.graphics.Canvas;
import android.view.MotionEvent;
import org.libsdl.app.SDLActivity;
public final class TouchOverlayTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class Overlay extends TouchOverlay {
        Overlay(GameActivity c){super(c);setGameResolution(1920,1080);onAttachedToWindow();setInputActive(true);}
        Canvas draw(){Canvas c=new Canvas();onDraw(c);return c;}
        void detach(){onDetachedFromWindow();}
    }
    static void phase(Overlay o,int phase){AndroidUiState.state=phase;o.onTouchEvent(new MotionEvent(3,0,0));SDLActivity.events.clear();}
    static void touch(Overlay o,int action,float x,float y){o.onTouchEvent(new MotionEvent(action,x,y));}
    static SDLActivity.Event last(){return SDLActivity.events.get(SDLActivity.events.size()-1);}
    static boolean key(int code,boolean down){for(SDLActivity.Event e:SDLActivity.events)if(e.key==code&&e.down==down)return true;return false;}
    public static void main(String[] args)throws Exception {
        GameActivity context=new GameActivity();Overlay o=new Overlay(context);
        phase(o,AndroidUiState.STARTING);
        touch(o,0,1728,670);
        check(SDLActivity.events.isEmpty(),"startup cannot fire");
        check(o.draw().shapes==0,"startup is reserved for loading view");
        phase(o,AndroidUiState.GAME);
        touch(o,0,1728,670);
        check(last().button==1&&last().action==0&&last().relative,"visible fire press");
        o.setControlsVisible(false);
        check(last().button==0&&last().action==1,"hiding releases fire");
        check(o.draw().shapes==0&&o.draw().labels.isEmpty(),"hidden overlay draws no buttons or marks");
        SDLActivity.events.clear();touch(o,0,1728,670);touch(o,1,1728,670);
        check(SDLActivity.events.isEmpty(),"hidden gameplay button cannot fire or click");
        o.setControlsVisible(true);touch(o,0,268.8f,650);
        check(key(51,true),"touch stick moves forward");
        o.setInputActive(false);check(key(51,false),"pause releases stick");
        SDLActivity.events.clear();touch(o,0,1728,670);
        check(SDLActivity.events.isEmpty(),"inactive overlay cannot inject input");
        o.setInputActive(true);phase(o,AndroidUiState.GAME);
        touch(o,0,1728,670);touch(o,3,1728,670);
        check(SDLActivity.events.size()==2&&last().action==1,"cancel releases fire without synthetic click");
        phase(o,AndroidUiState.MENU);o.setControlsVisible(false);
        touch(o,0,1728,129.6f);touch(o,1,1728,129.6f);
        check(SDLActivity.events.size()==4,"hidden LOG area is a normal menu tap");
        check(!last().relative&&last().action==1&&last().x==1728,"absolute mouse up uses full window coordinates");
        check(context.exports==0,"hidden LOG action cannot export");
        SDLActivity.events.clear();touch(o,0,76.8f,993.6f);touch(o,1,76.8f,993.6f);
        check(SDLActivity.events.size()==4,"hidden ABC area forwards menu clicks without toggling keyboard");
        HandheldInput controller=new HandheldInput();controller.visible=true;o.setController(controller);
        check(o.draw().paths==2&&o.draw().labels.isEmpty(),"controller cursor stays visible when touch buttons are off");
        SDLActivity.events.clear();touch(o,0,480,270);touch(o,1,480,270);
        check(controller.x==.25f&&controller.y==.25f,"touch and controller share the pointer position");
        o.setGameResolution(1280,720);touch(o,0,480,270);touch(o,1,480,270);
        check(controller.x==.25f&&controller.y==.25f,"touch and gamepad agree at a different drawable size");
        o.setGameResolution(1920,1080);
        controller.visible=false;check(o.draw().paths==0,"disconnect hides the controller cursor");
        o.setControlsVisible(true);SDLActivity.events.clear();touch(o,0,300,300);touch(o,2,330,350);
        o.setControlsVisible(false);
        check(last().action==1&&last().x==330&&last().y==350,"hiding releases drag at its last cursor position");
        phase(o,AndroidUiState.GAME);
        check(o.draw().paths==0,"gameplay has no controller cursor");
        java.lang.reflect.Field menu=TouchOverlay.class.getDeclaredField("menu");menu.setAccessible(true);menu.setBoolean(o,true);
        touch(o,0,400,400);touch(o,1,400,400);
        check(SDLActivity.events.size()==4,"spawn menu touch works with gameplay phase and hidden controls");
        menu.setBoolean(o,false);o.setControlsVisible(true);phase(o,AndroidUiState.INTRO|256);
        check(o.draw().labels.contains("SKIP INTRO")&&!o.draw().labels.contains("Fire"),"skippable intro has no gameplay buttons");
        touch(o,0,960,129.6f);touch(o,1,960,129.6f);
        check(key(111,true)&&!key(111,false),"quick skip tap keeps Escape until scheduled release");
        o.setControlsVisible(false);
        check(key(111,false),"hiding releases pending Escape immediately");
        int count=SDLActivity.events.size();o.runNext();o.runNext();
        check(SDLActivity.events.size()==count,"cancelled delayed skip release cannot leak into controller input");
        o.setControlsVisible(true);phase(o,AndroidUiState.INTRO);
        check(!o.draw().labels.contains("SKIP INTRO")&&!o.draw().labels.contains("LOG"),"unskippable clip hides skip and logs are only in gear menu");
        phase(o,AndroidUiState.GAME);
        check(!o.draw().labels.contains("SKIP INTRO")&&!o.draw().labels.contains("LOG"),"gameplay has no stale diagnostic or intro actions");
        touch(o,0,1728,928.8f);touch(o,1,1728,928.8f);
        check(controller.crouchDown==1&&controller.crouchUp==1,"touch crouch uses the shared tap/hold action");
        touch(o,0,1728,928.8f);o.setInputActive(false);
        check(controller.crouchCancel==1&&controller.crouchUp==1,"menu/focus cancellation never toggles crouch");
        check(o.draw().shapes==0,"inactive overlay does not draw controls behind gear menu");
        o.setInputActive(true);
        touch(o,0,1728,670);o.detach();
        check(last().action==1&&o.pending()==0,"detaching releases input and removes callbacks");
        System.out.println("Touch overlay: "+checks+" checks passed");
    }
}
"""


def main():
    java_home = os.environ.get("JAVA_HOME")
    javac = str(Path(java_home) / "bin/javac") if java_home else "javac"
    java = str(Path(java_home) / "bin/java") if java_home else "java"
    with tempfile.TemporaryDirectory(prefix="bf2-touch-check-") as temp:
        work = Path(temp)
        for name, source in STUBS.items():
            dest = work / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(source)
        test = work / "com/mackillergr/bf2/TouchOverlayTest.java"
        test.write_text(TEST)
        sources = list(work.rglob("*.java"))
        sources.extend(ROOT / "src/com/mackillergr/bf2" / name
                       for name in ("TouchOverlay.java", "MenuPointerMapper.java"))
        subprocess.run([javac, "--release", "8", "-d", str(work), *map(str, sources)], check=True)
        subprocess.run([java, "-cp", str(work), "com.mackillergr.bf2.TouchOverlayTest"], check=True)


if __name__ == "__main__":
    main()
