package com.mackillergr.bf2;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class OfflineCareerTest {
  private void write(File path, String value) throws Exception { Files.write(path.toPath(),value.getBytes(StandardCharsets.US_ASCII)); }
  @Test public void nativeLedgerReopenReplayAndRank() throws Exception {
    Context context=RuntimeEnvironment.getApplication();
    File store=new File(context.getFilesDir(),"offline-career");
    File receipts=new File(store,"receipts"); assertTrue(receipts.mkdirs());
    File first=new File(receipts,"00000000000000000000000000000001.round");
    write(first,"1 159 8 1\n");
    String expected=OfflineCareer.summary(context);
    assertTrue(expected,expected.contains("Private First Class · 159 career points"));
    assertTrue(expected,expected.contains("1 completed rounds · 8 kills · 1 deaths"));
    assertFalse(first.exists());
    assertEquals(expected,OfflineCareer.summary(context)); // close/reopen SQLiteDatabase
    write(first,"1 999 99 99\n");
    assertEquals(expected,OfflineCareer.summary(context)); // same ID may not change earned record
    assertFalse(first.exists());
    try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(new File(store,"career.db"),null);
        Cursor c=db.rawQuery("SELECT COUNT(*),SUM(score),SUM(kills),SUM(deaths) FROM rounds",null)) {
      assertTrue(c.moveToFirst()); assertEquals(1,c.getLong(0)); assertEquals(159,c.getLong(1));
      assertEquals(8,c.getLong(2)); assertEquals(1,c.getLong(3));
    }
    write(new File(receipts,"00000000000000000000000000000002.round"),"1 -10 0 1\n");
    String changed=OfflineCareer.summary(context);
    assertTrue(changed,changed.startsWith("Private · 149 career points"));
    assertTrue(changed,changed.contains("2 completed rounds · 8 kills · 2 deaths"));
    write(new File(receipts,"00000000000000000000000000000003.round"),"1 100 -2 0\n");
    assertTrue(OfflineCareer.summary(context).contains("A round receipt could not be read"));
    write(new File(store,"active"),"launch-test");
    assertTrue(OfflineCareer.summary(context).contains("Waiting for the offline server"));
    write(new File(store,"registered"),"launch-test");
    assertTrue(OfflineCareer.summary(context).contains("loaded career scoring"));
    OfflineCareer.disarm(context);
    assertFalse(OfflineCareer.summary(context).contains("loaded career scoring"));
  }
}
