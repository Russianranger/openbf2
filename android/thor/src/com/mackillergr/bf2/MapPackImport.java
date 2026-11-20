package com.mackillergr.bf2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.CRC32;

/** Publishes a complete, app-owned map generation without modifying the base mod. */
final class MapPackImport {
    static final String PREFIX = "openbf2_maps_";
    private static final String MARKER = "openbf2-map-pack.owner";
    private static final String OWNER = "OpenBF2 map pack v1\n";
    static final class Entry {
        final String id, name;
        final boolean directory;
        final long size;
        Entry(String id, String name, boolean directory, long size) {
            this.id = id; this.name = name; this.directory = directory; this.size = size;
        }
    }
    interface Source {
        List<Entry> children(Entry directory) throws IOException;
        InputStream open(Entry file) throws IOException;
    }
    interface Publication { void publish(String basename) throws IOException; }
    interface Progress { void copied(int files, String name); }
    static final class Result {
        final String basename;
        final int maps, files, skipped;
        Result(String basename, int maps, int files, int skipped) {
            this.basename = basename; this.maps = maps; this.files = files; this.skipped = skipped;
        }
    }
    private final Source source;
    private final Progress progress;
    private final byte[] buffer = new byte[128 * 1024];
    private final Set<String> folders = new HashSet<>();
    private int files;

    MapPackImport(Source source, Progress progress) { this.source = source; this.progress = progress; }

    Result install(File baseMod, Entry selected, Publication publication) throws IOException {
        if (!GameContent.child(baseMod, "ClientArchives.con").isFile())
            throw new IOException("Import the Battlefield 2 installation before adding a map pack.");
        File mods = baseMod.getCanonicalFile().getParentFile();
        String basename = PREFIX + UUID.randomUUID().toString().replace("-", "");
        File stage = new File(mods, basename + ".staging");
        File published = new File(mods, basename);
        if (!stage.mkdir()) throw new IOException("Cannot create map-pack staging folder");
        boolean renamed = false;
        try {
            try (FileOutputStream out = new FileOutputStream(new File(stage, MARKER))) {
                out.write((OWNER + basename + "\n").getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            Entry levels = child(selected, "levels", true);
            if (levels == null) levels = selected; // The picker may point directly at Levels.
            File outputLevels = new File(stage, "levels");
            if (!outputLevels.mkdir()) throw new IOException("Cannot create pack levels folder");
            List<Entry> candidates = list(levels);
            Set<String> names = new HashSet<>();
            int maps = 0, skipped = 0;
            for (Entry entry : candidates) {
                if (!entry.directory) continue;
                List<Entry> content = list(entry);
                Entry client = find(content, "client.zip", false);
                Entry server = find(content, "server.zip", false);
                Entry info = find(content, "info", true);
                if (client == null && server == null && info == null) continue;
                if (!entry.name.matches("[A-Za-z0-9_-]{1,80}")) { ++skipped; continue; }
                String map = entry.name.toLowerCase(Locale.ROOT);
                if (!names.add(map)) throw new IOException("Duplicate map folder: " + entry.name);
                if (client == null || server == null || info == null) { ++skipped; continue; }
                Entry desc = child(info, map + ".desc", false);
                if (desc == null || desc.size == 0) { ++skipped; continue; }
                File target = new File(outputLevels, map);
                if (!target.mkdir()) throw new IOException("Cannot create map folder: " + map);
                copy(server, new File(target, "server.zip"));
                if (progress != null) progress.copied(files, "Checking " + map + " bot support");
                validateArchive(new File(target, "server.zip"));
                boolean bots = false;
                for (GameContent.Layout layout : GameContent.offlineLayouts(target))
                    if (layout.botsSupported) { bots = true; break; }
                if (!bots) { removeTree(target); ++skipped; continue; }
                copy(client, new File(target, "client.zip"));
                copyInfo(info, new File(target, "info"), 0);
                if (progress != null) progress.copied(files, "Checking " + map + " client archive");
                validateArchive(new File(target, "client.zip"));
                File descriptor = new File(target, "info/" + map + ".desc");
                if (!descriptor.isFile() || descriptor.length() == 0) { removeTree(target); ++skipped; continue; }
                ++maps;
            }
            if (maps == 0)
                throw new IOException("No complete bot-ready maps found (" + skipped + " skipped). Select a map-pack mod folder or its Levels folder.");
            if (published.exists() || !stage.renameTo(published))
                throw new IOException("Cannot publish completed map pack");
            renamed = true;
            publication.publish(basename); // Last: readers never see an unfinished generation.
            cleanupOld(mods, basename);
            return new Result(basename, maps, files, skipped);
        } finally {
            // A failed preference write may have reached disk: keep that complete generation.
            // The next successful import can safely collect it after publishing its own pointer.
            if (!renamed) removeTree(stage);
        }
    }

    private List<Entry> list(Entry folder) throws IOException {
        if (folders.size() >= 20000 || !folders.add(folder.id)) throw new IOException("Invalid map-pack directory tree");
        List<Entry> entries = source.children(folder);
        Set<String> names = new HashSet<>();
        for (Entry entry : entries) {
            safeName(entry.name);
            if (!names.add(entry.name.toLowerCase(Locale.ROOT)))
                throw new IOException("Conflicting file names in " + folder.name);
        }
        return entries;
    }
    private Entry child(Entry parent, String name, boolean directory) throws IOException {
        // This probe is reused by the actual traversal; do not mark it visited yet.
        return find(source.children(parent), name, directory);
    }
    private static Entry find(List<Entry> entries, String name, boolean directory) throws IOException {
        Entry found = null;
        for (Entry entry : entries) if (name.equalsIgnoreCase(entry.name) && entry.directory == directory) {
            if (found != null) throw new IOException("Conflicting file names: " + name);
            found = entry;
        }
        return found;
    }
    private void copyInfo(Entry sourceDir, File target, int depth) throws IOException {
        if (depth > 8) throw new IOException("Info folder is too deeply nested");
        if (!target.mkdir()) throw new IOException("Cannot create map Info folder");
        for (Entry entry : list(sourceDir)) {
            File output = new File(target, entry.name.toLowerCase(Locale.ROOT));
            if (entry.directory) copyInfo(entry, output, depth + 1);
            else copy(entry, output);
        }
    }
    private void copy(Entry entry, File output) throws IOException {
        if (++files > 40000) throw new IOException("Too many map-pack files");
        try (InputStream in = source.open(entry); FileOutputStream out = new FileOutputStream(output)) {
            if (in == null) throw new IOException("Unable to open " + entry.name);
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.getFD().sync();
        }
        if (entry.size >= 0 && output.length() != entry.size)
            throw new IOException("Incomplete copy: " + entry.name);
        if (progress != null) progress.copied(files, entry.name);
    }
    private static void safeName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || name.indexOf('\0') >= 0)
            throw new IOException("Invalid file name in map pack");
    }
    private static void validateArchive(File archive) throws IOException {
        Set<String> names = new HashSet<>();
        byte[] buffer = new byte[128 * 1024];
        int files = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0)
                    throw new IOException("Invalid archive path in " + archive.getName());
                for (String part : name.split("/")) if (part.equals(".."))
                    throw new IOException("Invalid archive path in " + archive.getName());
                if (!entry.isDirectory()) {
                    if (!names.add(name.toLowerCase(Locale.ROOT)))
                        throw new IOException("Conflicting archive paths in " + archive.getName());
                    if (++files > 100000) throw new IOException("Too many archive members");
                    CRC32 crc = new CRC32();
                    long size = 0;
                    try (InputStream in = zip.getInputStream(entry)) {
                        int n;
                        while ((n = in.read(buffer)) != -1) { crc.update(buffer, 0, n); size += n; }
                    }
                    if (entry.getSize() != size || entry.getCrc() != crc.getValue())
                        throw new IOException("Damaged " + archive.getName() + " member: " + entry.getName());
                }
            }
        }
        if (files == 0) throw new IOException("Empty map archive: " + archive.getName());
    }
    private static boolean owned(File folder, String basename) {
        if (!basename.matches("openbf2_maps_[0-9a-f]{32}")) return false;
        try {
            if (!folder.getCanonicalFile().getParentFile().equals(folder.getParentFile().getCanonicalFile())) return false;
            File marker = new File(folder, MARKER);
            byte[] expected = (OWNER + basename + "\n").getBytes(StandardCharsets.UTF_8);
            if (marker.length() != expected.length) return false;
            byte[] actual = new byte[expected.length];
            try (DataInputStream in = new DataInputStream(new FileInputStream(marker))) { in.readFully(actual); }
            return Arrays.equals(actual, expected);
        } catch (IOException e) { return false; }
    }
    private static void cleanupOld(File mods, String keep) {
        File[] entries = mods.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            String name = entry.getName();
            if (name.equals(keep)) continue;
            String basename = name.endsWith(".staging") ? name.substring(0, name.length() - 8) : name;
            if (name.equals(basename) || name.equals(basename + ".staging")) removeOwned(entry, basename);
        }
    }
    private static void removeOwned(File directory, String basename) {
        if (owned(directory, basename)) removeTree(directory);
    }
    private static void removeTree(File file) {
        // Imported archives are never unpacked, and SAF copies cannot create symlinks.
        try {
            if (!file.getCanonicalFile().equals(file.getAbsoluteFile())) { file.delete(); return; }
            File[] children = file.listFiles();
            if (children != null) for (File child : children) removeTree(child);
            file.delete();
        } catch (IOException ignored) { /* A later successful import can retry cleanup. */ }
    }
}
