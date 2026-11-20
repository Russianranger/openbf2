package com.mackillergr.bf2;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class MapPackPickerAndroidTest {
    private static final String AUTHORITY = "map.pack.fixture";
    public static class Host extends Activity {
        boolean failNextPreferenceWrite;
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            SharedPreferences real = super.getSharedPreferences(name, mode);
            if (!name.equals("openbf2")) return real;
            return (SharedPreferences) Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{SharedPreferences.class},
                    (proxy, method, args) -> {
                        Object value = method.invoke(real, args);
                        if (!method.getName().equals("edit")) return value;
                        SharedPreferences.Editor editor = (SharedPreferences.Editor)value;
                        return Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},
                                (p,m,a) -> {
                                    Object result = m.invoke(editor,a);
                                    if (m.getName().equals("commit") && failNextPreferenceWrite) {
                                        failNextPreferenceWrite = false; return false;
                                    }
                                    return result == editor ? p : result;
                                });
                    });
        }
    }
    public static class Provider extends ContentProvider {
        File root;
        CountDownLatch entered, release;
        int opened;
        public boolean onCreate() { return true; }
        private File file(Uri uri) { return new File(DocumentsContract.getDocumentId(uri)); }
        @Override public Cursor query(Uri uri,String[] columns,String selection,String[] args,String order) {
            if (entered != null) {
                entered.countDown();
                try { if (!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("fixture wait expired"); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
            }
            MatrixCursor result = new MatrixCursor(columns);
            File[] files = file(uri).listFiles();
            if (files != null) for (File file : files) {
                Object[] row = new Object[columns.length];
                for (int i=0;i<columns.length;i++) {
                    if (columns[i].equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID)) row[i]=file.getAbsolutePath();
                    else if (columns[i].equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) row[i]=file.getName();
                    else if (columns[i].equals(DocumentsContract.Document.COLUMN_MIME_TYPE)) row[i]=file.isDirectory()?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream";
                    else if (columns[i].equals(DocumentsContract.Document.COLUMN_SIZE)) row[i]=file.length();
                    else if (columns[i].equals(DocumentsContract.Document.COLUMN_LAST_MODIFIED)) row[i]=file.lastModified();
                }
                result.addRow(row);
            }
            return result;
        }
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
            opened++; return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
        }
        public String getType(Uri uri) { return DocumentsContract.Document.MIME_TYPE_DIR; }
        public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
        public int delete(Uri uri,String where,String[] args) { throw new UnsupportedOperationException(); }
        public int update(Uri uri,ContentValues values,String where,String[] args) { throw new UnsupportedOperationException(); }
    }
    private static class Status implements GameDirectoryPicker.Listener {
        String text="", path;
        public void onStatus(String value) { text=value; }
        public void onImported(String value) { path=value; }
    }
    private Host host;
    private Provider provider;
    private Status status;
    private GameDirectoryPicker picker;
    private Intent result;
    private File base;
    @Before public void before() throws Exception {
        host=Robolectric.buildActivity(Host.class).setup().get();
        provider=new Provider();
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority=AUTHORITY;
        provider.attachInfo(host,info);
        ShadowContentResolver.registerProviderInternal(AUTHORITY,provider);
        provider.root=MapPackImportTest.fixture(Files.createTempDirectory("saf-map-pack-").toFile(),"64coop20YA",true);
        result=new Intent().setData(DocumentsContract.buildTreeDocumentUri(AUTHORITY,provider.root.getAbsolutePath()));
        base=new File(host.getExternalFilesDir(null),"Game Files/mods/bf2"); base.mkdirs();
        Files.write(new File(base,"ClientArchives.con").toPath(),new byte[]{1});
        Files.write(new File(base,"stock-data").toPath(),new byte[]{3,1,4});
        status=new Status(); picker=new GameDirectoryPicker(host,status);
    }
    @After public void after() {
        if (provider.release != null) provider.release.countDown();
        GameSession.release();
    }
    private void done() throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while ((picker.isImporting() || !(status.text.startsWith("Imported") || status.text.startsWith("Map-pack import failed")))
                && System.nanoTime()<end) { Thread.sleep(5); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse("import did not complete: "+status.text,picker.isImporting());
    }
    @Test public void mapPickerUsesOwnRequestCodeAndReadGrant() {
        picker.pickMapPack();
        ShadowActivity.IntentForResult launch=Shadows.shadowOf(host).getNextStartedActivityForResult();
        assertEquals(GameDirectoryPicker.REQUEST_MAP_PACK,launch.requestCode);
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,launch.intent.getAction());
        assertTrue((launch.intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
    }
    @Test public void realSafResultPublishesAndKeepsBase() throws Exception {
        picker.onMapPackResult(Activity.RESULT_OK,result); done();
        String name=host.getSharedPreferences("openbf2",0).getString("mapPack",null);
        assertNotNull(status.text,name);
        assertTrue(name.matches("openbf2_maps_[0-9a-f]{32}"));
        assertTrue(new File(base.getParentFile(),name+"/levels/mixed_map/server.zip").isFile());
        assertEquals(base.getAbsolutePath(),status.path);
        assertArrayEquals(new byte[]{3,1,4},Files.readAllBytes(new File(base,"stock-data").toPath()));
        assertEquals(4,provider.opened);
    }
    @Test public void cancelledPickerMakesNoChange() {
        picker.onMapPackResult(Activity.RESULT_CANCELED,result);
        assertFalse(picker.isImporting()); assertEquals(0,provider.opened);
        assertNull(host.getSharedPreferences("openbf2",0).getString("mapPack",null));
    }
    @Test public void liveGameIsRecheckedWhenResultArrives() {
        GameSession.hold(host);
        picker.onMapPackResult(Activity.RESULT_OK,result);
        assertFalse(picker.isImporting()); assertEquals(0,provider.opened); assertNull(status.path);
    }
    @Test public void fullAndMapImportsShareExclusion() throws Exception {
        provider.entered=new CountDownLatch(1); provider.release=new CountDownLatch(1);
        picker.onMapPackResult(Activity.RESULT_OK,result);
        assertTrue(provider.entered.await(2,TimeUnit.SECONDS));
        Status other=new Status(); GameDirectoryPicker second=new GameDirectoryPicker(host,other);
        second.onResult(Activity.RESULT_OK,result);
        assertTrue(other.text.contains("already in progress"));
        assertTrue(second.isImporting());
        provider.release.countDown(); done(); assertNotNull(status.path);
    }
    @Test public void interruptedBaseImportBlocksPack() throws Exception {
        host.getSharedPreferences("import",0).edit().putBoolean("incomplete",true).commit();
        picker.onMapPackResult(Activity.RESULT_OK,result); done();
        assertNull(status.path); assertEquals(0,provider.opened);
        assertTrue(status.text.contains("Finish the interrupted base-game import"));
    }
    @Test public void failedPreferenceWriteRestoresPreviousSelection() throws Exception {
        picker.onMapPackResult(Activity.RESULT_OK,result); done();
        String previous=host.getSharedPreferences("openbf2",0).getString("mapPack",null);
        status.path=null; host.failNextPreferenceWrite=true;
        picker.onMapPackResult(Activity.RESULT_OK,result); done();
        assertNull(status.path);
        assertEquals(previous,host.getSharedPreferences("openbf2",0).getString("mapPack",null));
        assertTrue(new File(base.getParentFile(),previous).isDirectory());
        assertTrue(status.text.contains("Could not save"));
    }
}
