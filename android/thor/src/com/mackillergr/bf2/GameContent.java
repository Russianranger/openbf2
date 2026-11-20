package com.mackillergr.bf2;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Discovers authored layouts rather than inferring them from player limits. */
final class GameContent {
    private static final int[] SIZES = {16, 32, 64};
    private static final int SCRIPT_LIMIT = 2 * 1024 * 1024;
    private static final Pattern OBJECT = Pattern.compile("(?im)^\\s*object\\.create\\s+\\S+");
    private static final Pattern SPAWN = Pattern.compile("(?im)^\\s*objecttemplate\\.create\\s+spawnpoint\\s+\\S+");
    private static final Pattern STRATEGY = Pattern.compile("(?im)^\\s*aistrategicarea\\.(?:create|createfromcontrolpoint)\\s+\\S+");
    private static final Pattern AI_INIT = Pattern.compile("(?im)^\\s*ai\\.init\\s+\\S+");
    private static final Pattern RUN = Pattern.compile("(?im)^\\s*run\\s+(?:\"([^\"\\r\\n]+)\"|([^\\s\"]+))");

    static final class Layout {
        final String mode;
        final int size;
        final boolean botsSupported;
        final String reason;
        Layout(String mode, int size, boolean botsSupported, String reason) {
            this.mode = mode; this.size = size; this.botsSupported = botsSupported; this.reason = reason;
        }
        @Override public String toString() {
            String kind = "sp1".equals(mode) ? "Singleplayer" : "gpm_coop".equals(mode) ? "Co-op" : "Conquest";
            return size + " — " + kind + (botsSupported ? "" : " (no bots)");
        }
    }

    private GameContent() {}
    static File mapPackDirectory(File baseMod, String name) {
        if (baseMod == null || name == null || !name.matches("openbf2_maps_[0-9a-f]{32}")) return null;
        return new File(baseMod.getParentFile(), name);
    }
    static File child(File dir, String name) {
        File direct = new File(dir, name);
        if (direct.exists()) return direct;
        File[] entries = dir.listFiles();
        if (entries != null) for (File entry : entries)
            if (name.equalsIgnoreCase(entry.getName())) return entry;
        return direct;
    }

    /** Prefer a real bot layout for each size; otherwise offer real conquest with zero bots. */
    static List<Layout> offlineLayouts(File level) {
        if (!validLevel(level)) return Collections.emptyList();
        try (Content content = new Content(level)) {
            List<Layout> result = new ArrayList<>();
            for (int size : SIZES) {
                Layout pick = inspect(content, "gpm_coop", size);
                if (pick == null || !pick.botsSupported) pick = inspect(content, "sp1", size);
                if (pick == null || !pick.botsSupported) pick = inspect(content, "gpm_cq", size);
                if (pick != null) result.add(pick);
            }
            return Collections.unmodifiableList(result);
        } catch (IOException ignored) { return Collections.emptyList(); }
    }

    /** Actual placements for a mode, used by LAN hosting without requiring bot data. */
    static List<Layout> layouts(File level, String mode) {
        if (!validLevel(level) || !validMode(mode)) return Collections.emptyList();
        try (Content content = new Content(level)) {
            List<Layout> result = new ArrayList<>();
            for (int size : SIZES) {
                Layout layout = inspect(content, mode, size);
                if (layout != null) result.add(layout);
            }
            return Collections.unmodifiableList(result);
        } catch (IOException ignored) { return Collections.emptyList(); }
    }

    static boolean hasCoop(File level) {
        for (Layout layout : offlineLayouts(level)) if (layout.botsSupported) return true;
        return false;
    }
    static boolean hasLayout(File level, String mode) { return !layouts(level, mode).isEmpty(); }
    static boolean validSize(int size) { return size == 16 || size == 32 || size == 64; }
    static boolean validMode(String mode) { return "gpm_coop".equals(mode) || "sp1".equals(mode) || "gpm_cq".equals(mode); }
    private static boolean validLevel(File level) { return level != null && level.getName().matches("[A-Za-z0-9_-]+"); }

    private static Layout inspect(Content content, String mode, int size) throws IOException {
        String base = "GameModes/" + mode + "/" + size + "/";
        String objects = content.script(base + "GamePlayObjects.con");
        // Several stock archives contain only editor-generated comment/if/endIf placeholders.
        if (!OBJECT.matcher(objects).find() || !SPAWN.matcher(objects).find()) return null;
        if ("gpm_cq".equals(mode))
            return new Layout(mode, size, false, "Conquest terrain is playable offline with 0 bots; this layout has no verified bot support.");
        if (!STRATEGY.matcher(content.script(base + "AI/StrategicAreas.ai")).find())
            return new Layout(mode, size, false, "This layout has no authored AI strategic areas.");
        if (!AI_INIT.matcher(content.script("AI/AI.ai")).find() || !content.infantryNavigation())
            return new Layout(mode, size, false, "The map's AI initialization or infantry navigation is missing.");
        return new Layout(mode, size, true, "Authored gameplay, AI strategic areas and infantry navigation are present.");
    }

    /** Loose files override the same case-insensitive paths in server.zip. */
    private static final class Content implements AutoCloseable {
        final File level;
        final ZipFile zip;
        final Map<String, ZipEntry> entries = new HashMap<>();
        Content(File level) throws IOException {
            this.level = level;
            File archive = child(level, "server.zip");
            zip = archive.isFile() ? new ZipFile(archive) : null;
            if (zip != null) for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                ZipEntry entry = e.nextElement();
                String key = normalize(entry.getName());
                if (key != null && !entry.isDirectory()) entries.put(key, entry);
            }
        }
        public void close() throws IOException { if (zip != null) zip.close(); }
        byte[] read(String path, int limit) throws IOException {
            String key = normalize(path);
            if (key == null) return null;
            File loose = level;
            for (String part : key.split("/")) loose = child(loose, part);
            if (loose.isFile()) {
                if (loose.length() > limit) return null;
                try (InputStream in = new FileInputStream(loose)) { return bounded(in, limit); }
            }
            ZipEntry entry = entries.get(key);
            if (entry == null || entry.getSize() > limit) return null;
            try (InputStream in = zip.getInputStream(entry)) { return bounded(in, limit); }
        }
        byte[] prefix(String path, int count) throws IOException {
            String key = normalize(path);
            if (key == null) return null;
            File loose = level;
            for (String part : key.split("/")) loose = child(loose, part);
            ZipEntry entry = entries.get(key);
            if (!loose.isFile() && entry == null) return null;
            try (InputStream in = loose.isFile() ? new FileInputStream(loose) : zip.getInputStream(entry)) {
                byte[] result = new byte[count]; int used = 0, n;
                while (used < count && (n = in.read(result, used, count - used)) > 0) used += n;
                return used == count ? result : null;
            }
        }
        boolean infantryNavigation() throws IOException {
            byte[] tree = prefix("AIPathFinding/Infantry.qtr", 32);
            if (tree == null || prefix("AIPathFinding/Infantry.clb", 8) == null) return false;
            ByteBuffer data = ByteBuffer.wrap(tree).order(ByteOrder.LITTLE_ENDIAN);
            // The runtime reads one version-3 tree, then bounds and the node count.
            if (data.getInt() != 1 || data.getInt() != 3) return false;
            float minX = data.getFloat(), minZ = data.getFloat(), maxX = data.getFloat(), maxZ = data.getFloat();
            float edge = data.getFloat(); int nodes = data.getInt();
            return Float.isFinite(minX) && Float.isFinite(minZ) && Float.isFinite(maxX) && Float.isFinite(maxZ)
                    && Float.isFinite(edge) && maxX > minX && maxZ > minZ && edge > 0 && nodes > 0;
        }
        String script(String path) throws IOException {
            StringBuilder text = new StringBuilder();
            expand(path, text, new HashSet<String>(), 0);
            return text.toString();
        }
        void expand(String path, StringBuilder text, Set<String> seen, int depth) throws IOException {
            String key = normalize(path);
            if (key == null || depth > 8 || !seen.add(key) || text.length() >= SCRIPT_LIMIT) return;
            byte[] bytes = read(key, SCRIPT_LIMIT - text.length());
            if (bytes == null) return;
            String body = new String(bytes, StandardCharsets.ISO_8859_1);
            text.append('\n').append(body);
            Matcher include = RUN.matcher(body);
            String directory = key.substring(0, key.lastIndexOf('/') + 1);
            while (include.find()) {
                String relative = include.group(1) == null ? include.group(2) : include.group(1);
                String target = normalize(directory + relative);
                if (target != null && read(target, SCRIPT_LIMIT) != null) expand(target, text, seen, depth + 1);
                else expand(relative, text, seen, depth + 1);
            }
        }
    }

    private static byte[] bounded(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > limit) return null;
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
    private static String normalize(String path) {
        if (path == null || path.startsWith("/") || path.indexOf(':') >= 0 || path.indexOf('\0') >= 0) return null;
        List<String> parts = new ArrayList<>();
        for (String part : path.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (parts.isEmpty()) return null; parts.remove(parts.size() - 1); }
            else parts.add(part.toLowerCase(Locale.ROOT));
        }
        return String.join("/", parts);
    }
}
