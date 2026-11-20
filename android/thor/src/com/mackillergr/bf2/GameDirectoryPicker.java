package com.mackillergr.bf2;

import android.app.Activity;
import android.content.SharedPreferences;
import android.system.Os;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SAF directory picker and importer for the Android game client.
 * The native executable requires --mod with an actual filesystem directory;
 * content:// URIs cannot be passed directly to the BF2 filesystem.
 */
final class GameDirectoryPicker {
    static final int REQUEST_CODE = 4082;
    static final int REQUEST_MAP_PACK = 4083;
    interface Listener {
        void onStatus(String status);
        void onImported(String path);
    }
    private static final String DIR = DocumentsContract.Document.MIME_TYPE_DIR;
    private static final String[] REQUIRED = {
        "ClientArchives.con", "Objects_client.zip", "Common_client.zip",
        "Menu_client.zip", "Fonts_client.zip", "Shaders_client.zip", "Booster_client.zip"
    };
    private final Activity activity;
    private final Listener listener;
    private static final java.util.concurrent.atomic.AtomicBoolean IMPORTING =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final byte[] buffer = new byte[128 * 1024];
    private File stagedFile;
    private long lastProgress;


    GameDirectoryPicker(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    boolean isImporting() { return IMPORTING.get(); }

    void pick() {
        openPicker(REQUEST_CODE);
    }

    void pickMapPack() {
        openPicker(REQUEST_MAP_PACK);
    }

    private void openPicker(int request) {
        try {
            if (GameSession.isActive(activity)) {
                listener.onStatus("Close the current game before importing data."); return;
            }
        } catch (IOException e) { listener.onStatus("Unable to check game session: " + e.getMessage()); return; }
        if (IMPORTING.get()) {
            listener.onStatus("An import is already in progress.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, request);
    }

    void onResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            try {
                activity.getContentResolver().takePersistableUriPermission(
                        tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // The one-time read grant still permits an immediate import.
            }
            importInstallation(tree);
        } catch (Exception error) {
            listener.onStatus("Could not open the selected folder: " + error.getMessage());
        }
    }

    void onMapPackResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            try {
                activity.getContentResolver().takePersistableUriPermission(
                        tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { /* The immediate read grant is sufficient. */ }
            importMapPack(tree);
        } catch (Exception error) {
            listener.onStatus("Could not open the map-pack folder: " + error.getMessage());
        }
    }

    private boolean beginImport() {
        if (!IMPORTING.compareAndSet(false, true)) {
            listener.onStatus("An import is already in progress.");
            return false;
        }
        try {
            if (!GameSession.isActive(activity)) return true;
            listener.onStatus("Close the current game before importing data.");
        } catch (IOException | RuntimeException e) {
            listener.onStatus("Unable to check game session: " + e.getMessage());
        }
        IMPORTING.set(false);
        return false;
    }

    private void importMapPack(Uri tree) {
        if (!beginImport()) return;
        File files = activity.getExternalFilesDir(null);
        File gameRoot = new File(files != null ? files : activity.getFilesDir(), "Game Files");
        File baseMod = new File(gameRoot, "mods/bf2");
        listener.onStatus("Reading optional map pack…");
        new Thread(() -> {
            String message;
            boolean success = false;
            try {
                if (activity.getSharedPreferences("import", Activity.MODE_PRIVATE).getBoolean("incomplete", false))
                    throw new IOException("Finish the interrupted base-game import before adding a map pack.");
                MapPackImport.Source source = new MapPackImport.Source() {
                    public List<MapPackImport.Entry> children(MapPackImport.Entry directory) throws IOException {
                        List<MapPackImport.Entry> entries = new ArrayList<>();
                        for (Node node : GameDirectoryPicker.this.children(tree, directory.id))
                            entries.add(new MapPackImport.Entry(node.id, node.name, node.directory, node.size));
                        return entries;
                    }
                    public InputStream open(MapPackImport.Entry file) throws IOException {
                        return activity.getContentResolver().openInputStream(
                                DocumentsContract.buildDocumentUriUsingTree(tree, file.id));
                    }
                };
                MapPackImport importer = new MapPackImport(source, (count, name) -> {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - lastProgress >= 500) {
                        lastProgress = now;
                        activity.runOnUiThread(() -> listener.onStatus("Importing map-pack file #" + count + ": " + name));
                    }
                });
                MapPackImport.Entry selected = new MapPackImport.Entry(
                        DocumentsContract.getTreeDocumentId(tree), "", true, -1);
                SharedPreferences state = activity.getSharedPreferences("openbf2", Activity.MODE_PRIVATE);
                MapPackImport.Result result = importer.install(baseMod, selected, basename -> {
                    String previous = state.getString("mapPack", null);
                    if (!state.edit().putString("mapPack", basename).commit()) {
                        SharedPreferences.Editor rollback = state.edit();
                        if (previous == null) rollback.remove("mapPack"); else rollback.putString("mapPack", previous);
                        rollback.commit(); // Also restore SharedPreferences' in-memory value after a failed write.
                        throw new IOException("Could not save the map-pack selection");
                    }
                });
                message = "Imported " + result.maps + " optional bot maps; " + result.skipped
                        + " incomplete or unsupported maps skipped. Stock maps are still available.";
                success = true;
            } catch (Exception e) {
                message = "Map-pack import failed: " + e.getMessage();
            } finally {
                IMPORTING.set(false);
            }
            final String status = message;
            final boolean imported = success;
            activity.runOnUiThread(() -> {
                if (imported) listener.onImported(baseMod.getAbsolutePath());
                listener.onStatus(status);
            });
        }, "openbf2-map-pack-import").start();
    }

    private static final class Node {
        final String id;
        final String name;
        final boolean directory;
        final long size;
        final long modified;
        Node(String id, String name, boolean directory, long size, long modified) {
            this.id = id; this.name = name; this.directory = directory; this.size = size; this.modified = modified;
        }
    }

    private static final class Job {
        final Node folder;
        final File target;
        final int depth;
        Job(Node folder, File target, int depth) {
            this.folder = folder; this.target = target; this.depth = depth;
        }
    }

    private List<Node> children(Uri tree, String id) throws IOException {
        List<Node> result = new ArrayList<>();
        Uri uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        String[] cols = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor cursor = activity.getContentResolver().query(uri, cols, null, null, null)) {
            if (cursor == null) throw new IOException("The storage provider did not respond");
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                if (childId != null && name != null) {
                    result.add(new Node(childId, name, DIR.equals(cursor.getString(2)),
                            cursor.isNull(3) ? -1 : cursor.getLong(3), cursor.isNull(4) ? 0 : cursor.getLong(4)));
                }
            }
        } catch (SecurityException e) {
            throw new IOException("Cannot read folder", e);
        }
        return result;
    }

    private Node findChild(Uri tree, Node parent, String name, boolean directory) throws IOException {
        for (Node n : children(tree, parent.id)) {
            if (n.directory == directory && name.equalsIgnoreCase(n.name)) return n;
        }
        return null;
    }

    private void importInstallation(Uri tree) {
        if (!beginImport()) return;
        File files = activity.getExternalFilesDir(null);
        final File gameRoot = new File(files != null ? files : activity.getFilesDir(), "Game Files");
        final File dst = new File(gameRoot, "mods/bf2");
        stagedFile = new File(gameRoot.getParentFile(), "openbf2-import.partial");
        listener.onStatus("Reading Battlefield 2 installation…");
        new Thread(() -> {
            try {
                Node selected = new Node(DocumentsContract.getTreeDocumentId(tree), "", true, -1, 0);
                Node root = selected;
                Node mods = findChild(tree, root, "mods", true);
                if (mods == null) {
                    for (String wrapper : new String[] {"Game Files", "Battlefield 2"}) {
                        Node folder = findChild(tree, selected, wrapper, true);
                        if (folder != null && (mods = findChild(tree, folder, "mods", true)) != null) {
                            root = folder;
                            break;
                        }
                    }
                }
                Node mod = mods == null ? null : findChild(tree, mods, "bf2", true);
                if (mod == null || findChild(tree, mod, "ClientArchives.con", false) == null)
                    throw new IOException("Select the installation folder containing mods/bf2 and the game's Python files.");
                SharedPreferences state = activity.getSharedPreferences("import", Activity.MODE_PRIVATE);
                boolean reuse = tree.toString().equals(state.getString("tree", ""));
                state.edit().putBoolean("incomplete", true).putString("tree", tree.toString()).commit();
                int total = copy(tree, mod, dst, gameRoot, reuse);
                for (Node n : children(tree, root.id)) {
                    if (n.directory && (n.name.equalsIgnoreCase("python") || n.name.equalsIgnoreCase("admin")
                            || n.name.equalsIgnoreCase("profiles"))) {
                        total += copy(tree, n, new File(gameRoot, n.name.toLowerCase(java.util.Locale.ROOT)), gameRoot, reuse);
                    } else if (!n.directory && n.name.equalsIgnoreCase("pylib-2.3.4.zip")) {
                        copyFile(tree, n, new File(gameRoot, "pylib-2.3.4.zip"), reuse);
                    }
                }
                for (String name : REQUIRED)
                    if (!GameContent.child(dst,name).isFile())
                        throw new IOException("Missing " + name + " in the selected installation");
                if (stagedFile.exists()) stagedFile.delete();
                state.edit().putBoolean("incomplete", false).commit();
                final int completed = total;
                activity.runOnUiThread(() -> {
                    IMPORTING.set(false);
                    listener.onImported(dst.getAbsolutePath());
                    listener.onStatus("Imported " + completed + " game files.");
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    IMPORTING.set(false);
                    listener.onStatus("Import failed: " + e.getMessage());
                });
            }
        }, "openbf2-import").start();
    }
    private int copy(Uri tree, Node src, File root, File gameRoot, boolean reuse) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create the game data folder");
        ArrayDeque<Job> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(new Job(src, root, 0));
        String allowedRoot = root.getCanonicalPath() + File.separator;
        int count = 0;
        while (!queue.isEmpty()) {
            Job current = queue.removeFirst();
            if (current.depth > 24 || !seen.add(current.folder.id))
                throw new IOException("Invalid directory tree");
            if (!current.target.isDirectory() && !current.target.mkdirs())
                throw new IOException("Cannot create " + current.target);
            for (Node file : children(tree, current.folder.id)) {
                if (file.name.isEmpty() || ".".equals(file.name) || "..".equals(file.name) ||
                        file.name.contains("/") || file.name.contains("\\") || file.name.indexOf('\0') >= 0)
                    throw new IOException("Invalid file name");
                String name = OfflineCareer.importName(gameRoot, current.target, file.name);
                File output = new File(current.target, name);
                if (!output.getCanonicalPath().startsWith(allowedRoot))
                    throw new IOException("Invalid file path");
                if (file.directory) {
                    queue.addLast(new Job(file, output, current.depth + 1));
                    continue;
                }
                if (++count > 40000) throw new IOException("Too many files in one game directory");
                
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastProgress >= 500) {
                    lastProgress = now;
                    final int c = count;
                    final String n = file.name;
                    activity.runOnUiThread(() -> listener.onStatus("Importing #" + c + ": " + n));
                }
                copyFile(tree, file, output, reuse);
            }
        }
        return count;
    }
    private void copyFile(Uri tree, Node file, File output, boolean reuse) throws IOException {
        if (reuse && file.modified > 0 && output.isFile() && output.length() == file.size
                && output.lastModified() == file.modified) return;
        File part = stagedFile;
        try {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, file.id);
            try (InputStream in = activity.getContentResolver().openInputStream(doc);
                 FileOutputStream out = new FileOutputStream(part)) {
                if (in == null) throw new IOException("Unable to open " + file.name);
                for (int n; (n = in.read(buffer)) >= 0;) out.write(buffer, 0, n);
            }
            if (file.size >= 0 && part.length() != file.size) throw new IOException("Incomplete copy: " + file.name);
            if (file.modified > 0) part.setLastModified(file.modified);
            try { Os.rename(part.toString(), output.toString()); }
            catch (android.system.ErrnoException e) { throw new IOException("Unable to replace " + output, e); }
        } finally { part.delete(); }
    }
}
