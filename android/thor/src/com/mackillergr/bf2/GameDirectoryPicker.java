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
    private boolean importing;

    GameDirectoryPicker(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    boolean isImporting() { return importing; }

    void pick() {
        if (importing) {
            listener.onStatus("A game data import is already in progress.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST_CODE);
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
        if (importing) return;
        File files = activity.getExternalFilesDir(null);
        final File gameRoot = new File(files != null ? files : activity.getFilesDir(), "Game Files");
        final File dst = new File(gameRoot, "mods/bf2");
        importing = true;
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
                int total = copy(tree, mod, dst, reuse);
                for (Node n : children(tree, root.id)) {
                    if (n.directory && (n.name.equalsIgnoreCase("python") || n.name.equalsIgnoreCase("admin")
                            || n.name.equalsIgnoreCase("profiles"))) {
                        total += copy(tree, n, new File(gameRoot, n.name.toLowerCase(java.util.Locale.ROOT)), reuse);
                    } else if (!n.directory && n.name.equalsIgnoreCase("pylib-2.3.4.zip")) {
                        copyFile(tree, n, new File(gameRoot, "pylib-2.3.4.zip"), reuse);
                    }
                }
                for (String name : REQUIRED)
                    if (!GameContent.child(dst,name).isFile())
                        throw new IOException("Missing " + name + " in the selected installation");
                state.edit().putBoolean("incomplete", false).commit();
                final int completed = total;
                activity.runOnUiThread(() -> {
                    importing = false;
                    listener.onImported(dst.getAbsolutePath());
                    listener.onStatus("Imported " + completed + " game files.");
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    importing = false;
                    listener.onStatus("Import failed: " + e.getMessage());
                });
            }
        }, "openbf2-import").start();
    }
    private int copy(Uri tree, Node src, File root, boolean reuse) throws IOException {
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
                File output = new File(current.target, file.name);
                if (!output.getCanonicalPath().startsWith(allowedRoot))
                    throw new IOException("Invalid file path");
                if (file.directory) {
                    queue.addLast(new Job(file, output, current.depth + 1));
                    continue;
                }
                if (++count > 40000) throw new IOException("Too many files in one game directory");
                
                if (count % 5 == 1 || file.size > 25000000) {
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
        File part = File.createTempFile("bf2-import-", ".partial", output.getParentFile());
        try {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, file.id);
            try (InputStream in = activity.getContentResolver().openInputStream(doc);
                 FileOutputStream out = new FileOutputStream(part)) {
                if (in == null) throw new IOException("Unable to open " + file.name);
                byte[] buffer = new byte[131072];
                for (int n; (n = in.read(buffer)) >= 0;) out.write(buffer, 0, n);
            }
            if (file.size >= 0 && part.length() != file.size) throw new IOException("Incomplete copy: " + file.name);
            if (file.modified > 0) part.setLastModified(file.modified);
            try { Os.rename(part.toString(), output.toString()); }
            catch (android.system.ErrnoException e) { throw new IOException("Unable to replace " + output, e); }
        } finally { part.delete(); }
    }
}
