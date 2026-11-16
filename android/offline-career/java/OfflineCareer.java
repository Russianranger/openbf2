package com.mackillergr.bf2;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.AtomicFile;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Local, device-wide soldier career. Only the app's offline listen server produces receipts. */
public final class OfflineCareer {
    private static final String MODULE = "bf2_offline_career.py";
    private static final String HEADER = "# OpenBF2 app-managed career module\n";
    private static final int[] POINTS = {0, 150, 500, 800, 2500, 5000, 8000, 20000, 50000};
    private static final String[] RANKS = {"Private", "Private First Class", "Lance Corporal",
        "Corporal", "Sergeant", "Staff Sergeant", "Gunnery Sergeant", "Master Sergeant",
        "Master Gunnery Sergeant"};

    private OfflineCareer() {}

    private static File home(Context context) {
        return new File(context.getFilesDir(), "offline-career");
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory())
            throw new IOException("Could not create " + directory.getName());
    }

    private static void replace(File file, byte[] prefix, InputStream remainder) throws IOException {
        AtomicFile target = new AtomicFile(file);
        FileOutputStream output = target.startWrite();
        try {
            output.write(prefix);
            if (remainder != null) {
                byte[] buffer = new byte[8192];
                for (int n; (n = remainder.read(buffer)) != -1;) output.write(buffer, 0, n);
            }
            target.finishWrite(output);
        } catch (IOException | RuntimeException ex) {
            target.failWrite(output);
            throw ex;
        }
    }

    /** Run before starting offline co-op. The server config selects sv.adminScript bf2_offline_career. */
    public static synchronized void prepare(Context context, File modDir) throws IOException {
        File external = context.getExternalFilesDir(null);
        if (external == null) throw new IOException("The imported game folder is unavailable");
        File base = new File(external.getCanonicalFile(), "Game Files");
        File expected = new File(base, "mods/bf2");
        if (!expected.equals(modDir.getCanonicalFile()))
            throw new IOException("Import Battlefield 2 into the app to enable its offline career");
        File module = new File(new File(base, "admin"), MODULE);
        if (!module.equals(module.getCanonicalFile()))
            throw new IOException("Unexpected career module location");
        ensureDirectory(module.getParentFile());
        if (module.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(module), StandardCharsets.UTF_8))) {
                if (!HEADER.trim().equals(reader.readLine()))
                    throw new IOException("A different admin script already uses " + MODULE);
            }
        }
        File store = home(context).getCanonicalFile();
        ensureDirectory(new File(store, "receipts"));
        // Android data directories contain package names, but still quote them as Python literals.
        String path = store.getPath().replace("\\", "\\\\").replace("'", "\\'");
        String prefix = HEADER + "CAREER_HOME = '" + path + "'\n";
        try (InputStream script = context.getAssets().open(MODULE)) {
            replace(module, prefix.getBytes(StandardCharsets.UTF_8), script);
        }
        replace(new File(store, "active"), UUID.randomUUID().toString()
                .getBytes(StandardCharsets.US_ASCII), null);
    }

    /** Retire the offline launch before entering an unrelated menu or LAN session. */
    public static synchronized void disarm(Context context) throws IOException {
        File active = new File(home(context), "active");
        if (active.exists() && !active.delete()) throw new IOException("Could not close offline career");
    }

    private static void importReceipt(SQLiteDatabase db, File receipt) throws IOException {
        if (receipt.length() > 128) throw new IOException("Oversized offline round receipt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(receipt), StandardCharsets.US_ASCII))) {
            String line = reader.readLine();
            String[] fields = line == null ? new String[0] : line.split(" ", -1);
            if (fields.length != 4 || !"1".equals(fields[0]) || reader.readLine() != null)
                throw new IOException("Unrecognized offline round receipt");
            int score = Integer.parseInt(fields[1]);
            int kills = Integer.parseInt(fields[2]);
            int deaths = Integer.parseInt(fields[3]);
            if (kills < 0 || deaths < 0) throw new IOException("Invalid offline combat totals");
            // The completed round is both the receipt and the ledger entry. Its primary key
            // makes a retry harmless if the process exits after commit but before file removal.
            db.execSQL("INSERT OR IGNORE INTO rounds (id,score,kills,deaths) VALUES (?,?,?,?)",
                    new Object[]{receipt.getName(), score, kills, deaths});
        }
    }

    /** Imports completed rounds into the private SQLite ledger, then summarizes the local score ladder. */
    public static synchronized String summary(Context context) {
        try {
            File store = home(context);
            ensureDirectory(store);
            try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(new File(store, "career.db"), null)) {
                db.execSQL("CREATE TABLE IF NOT EXISTS rounds (id TEXT PRIMARY KEY, score INTEGER NOT NULL,"
                        + " kills INTEGER NOT NULL, deaths INTEGER NOT NULL)");
                File folder = new File(store, "receipts");
                File[] pending = folder.listFiles((dir, name) -> name.matches("[0-9a-f]{32}\\.round"));
                boolean invalid = false;
                if (pending != null) for (File receipt : pending) {
                    try {
                        importReceipt(db, receipt);
                        // A failed removal leaves an already credited receipt for the next pass.
                        receipt.delete();
                    } catch (IOException | NumberFormatException ex) {
                        invalid = true;
                    }
                }
                try (Cursor c = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(score),0),"
                        + " COALESCE(SUM(kills),0), COALESCE(SUM(deaths),0) FROM rounds", null)) {
                    c.moveToFirst();
                    long score = c.getLong(1);
                    int rank = 0;
                    while (rank + 1 < POINTS.length && score >= POINTS[rank + 1]) rank++;
                    String next = rank + 1 < POINTS.length ? "\nNext: " + RANKS[rank + 1]
                            + " at " + POINTS[rank + 1] + " points" : "";
                    return RANKS[rank] + " · " + score + " career points\n" + c.getLong(0)
                            + " completed rounds · " + c.getLong(2) + " kills · " + c.getLong(3)
                            + " deaths" + next + (invalid ? "\nA round receipt could not be read." : "");
                }
            }
        } catch (IOException | RuntimeException ex) {
            return "Offline career unavailable: " + ex.getMessage();
        }
    }
}
