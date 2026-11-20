package com.mackillergr.bf2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowView;
import static org.junit.Assert.*;

/** [TEMP] Android 13 native Canvas and bitmap lifecycle; no engine/graphics driver is simulated. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GameLoadingViewTest {
    private static Field field(String name) throws Exception {
        Field value=GameLoadingView.class.getDeclaredField(name);value.setAccessible(true);return value;
    }
    @Test public void redrawOnlyWhenLoadingPresentationChanges() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        GameLoadingView view=new GameLoadingView(activity,new String[]{"--coop"});
        ShadowView shadow=Shadows.shadowOf(view);
        view.updateState(AndroidUiState.GAME,"");
        assertTrue("Loading layer clears once",shadow.wasInvalidated());shadow.clearWasInvalidated();
        for(int i=0;i<100;i++) view.updateState(AndroidUiState.GAME,"");
        view.updateState(AndroidUiState.MENU,"");
        view.updateState(AndroidUiState.INTRO|256,"");
        assertFalse("Transparent view stays idle",shadow.wasInvalidated());
        view.updateState(AndroidUiState.LOADING|(11<<16),"Levels/Next_Map/");
        assertTrue("Reload appears",shadow.wasInvalidated());shadow.clearWasInvalidated();
        for(int i=0;i<100;i++) view.updateState(AndroidUiState.LOADING|(11<<16),"Levels/Next_Map/");
        assertFalse("Unchanged loading progress stays idle",shadow.wasInvalidated());
        view.updateState(AndroidUiState.LOADING|(21<<16),"Levels/Next_Map/");
        assertTrue("Measured progress redraws",shadow.wasInvalidated());shadow.clearWasInvalidated();
        field("artworkDirty").setBoolean(view,true);
        view.updateState(AndroidUiState.LOADING|(21<<16),"Levels/Next_Map/");
        assertTrue("Artwork finished while inactive redraws on resume",shadow.wasInvalidated());shadow.clearWasInvalidated();
        view.updateState(AndroidUiState.MENU,"");
        assertTrue("Failed load clears",shadow.wasInvalidated());shadow.clearWasInvalidated();
        view.updateState(AndroidUiState.MENU,"");assertFalse(shadow.wasInvalidated());
        view.close();
        GameLoadingView menu=new GameLoadingView(activity,new String[]{"--menu"});
        ShadowView menuShadow=Shadows.shadowOf(menu);menuShadow.clearWasInvalidated();
        menu.updateState(AndroidUiState.LOADING|(43<<16),"another_map");
        assertFalse("Flash launch never invalidates native loading view",menuShadow.wasInvalidated());
        menu.setActive(true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("No polling for Flash launch",AndroidUiState.LOADING|(43<<16),field("snapshot").getInt(menu));
        menu.close();activity.finish();
    }
    @Test public void importedArtProgressThenUnobstructedGameplay() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        File mod=Files.createTempDirectory("bf2-loading-view-").toFile();
        File info=new File(mod,"LeVeLs/Test_Map/Info");assertTrue(info.mkdirs());
        File image=new File(info,"LOADMAP.PNG");
        Bitmap original=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);original.eraseColor(Color.BLUE);
        try(FileOutputStream out=new FileOutputStream(image)) {assertTrue(original.compress(Bitmap.CompressFormat.PNG,100,out));}
        original.recycle();
        GameLoadingView view=new GameLoadingView(activity,new String[]{"--mod",mod.getPath(),"--level","test_map","--coop"});
        activity.setContentView(view);view.layout(0,0,512,512);
        for(int i=0;i<500 && field("artwork").get(view)==null;i++) {
            Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertNotNull("Imported PNG decoded asynchronously",field("artwork").get(view));
        view.layout(0,0,512,512);
        field("snapshot").setInt(view,AndroidUiState.LOADING|(43<<16));
        Bitmap output=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(output);
        view.draw(canvas);
        assertEquals(Color.BLUE,output.getPixel(256,256));
        assertEquals(Color.rgb(218,181,69),output.getPixel(100,484));
        assertEquals(Color.rgb(55,60,64),output.getPixel(400,484));
        field("snapshot").setInt(view,AndroidUiState.GAME);
        output.eraseColor(Color.MAGENTA);view.draw(canvas);
        assertEquals(Color.MAGENTA,output.getPixel(256,256));
        assertFalse(view.onTouchEvent(null));
        view.setActive(true);view.setActive(false);Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(AndroidUiState.GAME,field("snapshot").getInt(view));
        view.close();assertNull(field("artwork").get(view));assertFalse(field("active").getBoolean(view));
        GameLoadingView stockMenu=new GameLoadingView(activity,new String[]{"--menu"});
        stockMenu.layout(0,0,512,512);field("snapshot").setInt(stockMenu,AndroidUiState.LOADING);
        output.eraseColor(Color.MAGENTA);stockMenu.draw(canvas);
        assertEquals(Color.MAGENTA,output.getPixel(256,256));assertFalse(stockMenu.onTouchEvent(null));
        stockMenu.close();output.recycle();activity.finish();
        image.delete();info.delete();info.getParentFile().delete();info.getParentFile().getParentFile().delete();mod.delete();
    }
}
