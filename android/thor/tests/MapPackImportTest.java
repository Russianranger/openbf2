package com.mackillergr.bf2;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Production importer with filesystem-backed directory/stream observers; no SAF or game simulation. */
public final class MapPackImportTest {
    private static int checks;
    private static final String GPO = "ObjectTemplate.create SpawnPoint base_spawn\nObject.create base_spawn\n";
    private static void check(boolean value, String label) {
        ++checks; if (!value) throw new AssertionError(label);
    }
    private static final class Pointer implements MapPackImport.Publication {
        String selected;
        int writes;
        public void publish(String basename) { selected = basename; ++writes; }
    }
    private static class DiskSource implements MapPackImport.Source {
        final List<String> opened = new ArrayList<>();
        MapPackImport.Entry entry(File file) {
            return new MapPackImport.Entry(file.getAbsolutePath(), file.getName(), file.isDirectory(), file.length());
        }
        public List<MapPackImport.Entry> children(MapPackImport.Entry parent) throws IOException {
            File[] files = new File(parent.id).listFiles();
            if (files == null) throw new IOException("Cannot read fixture folder");
            Arrays.sort(files, Comparator.comparing(File::getName));
            List<MapPackImport.Entry> entries = new ArrayList<>();
            for (File file : files) entries.add(entry(file));
            return entries;
        }
        public InputStream open(MapPackImport.Entry file) throws IOException {
            opened.add(file.id);
            return new FileInputStream(file.id);
        }
    }
    private static void write(File root, String path, byte[] bytes) throws IOException {
        File file = new File(root, path); file.getParentFile().mkdirs();
        Files.write(file.toPath(), bytes);
    }
    private static void write(File root, String path, String text) throws IOException {
        write(root, path, text.getBytes(StandardCharsets.UTF_8));
    }
    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String,byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    private static byte[] client() throws IOException {
        return zip(Collections.singletonMap("Terrain/Heightmap.raw", new byte[] {1,2,3,4}));
    }
    private static byte[] server(boolean bots) throws IOException {
        Map<String,byte[]> entries = new LinkedHashMap<>();
        entries.put("GameModes/gpm_coop/64/GamePlayObjects.con", GPO.getBytes(StandardCharsets.UTF_8));
        entries.put("GameModes/gpm_coop/64/AI/StrategicAreas.ai",
                "aiStrategicArea.createFromControlPoint base\n".getBytes(StandardCharsets.UTF_8));
        if (bots) {
            entries.put("AI/AI.ai", "ai.init 2\n".getBytes(StandardCharsets.UTF_8));
            ByteBuffer tree = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
            tree.putInt(1).putInt(3).putFloat(-50).putFloat(-50).putFloat(50).putFloat(50).putFloat(100).putInt(1);
            entries.put("AIPathFinding/Infantry.qtr", tree.array());
            entries.put("AIPathFinding/Infantry.clb", new byte[8]);
        }
        return zip(entries);
    }
    static File fixture(File root, String folder, boolean bots) throws IOException {
        File mod = new File(root, folder);
        write(mod, "LeVeLs/Mixed_Map/CLIENT.ZIP", client());
        write(mod, "LeVeLs/Mixed_Map/Server.Zip", server(bots));
        write(mod, "LeVeLs/Mixed_Map/INFO/Mixed_Map.DESC", "<map><name>Fixture</name></map>");
        write(mod, "LeVeLs/Mixed_Map/INFO/Loading/Screen.PNG", new byte[] {7,8});
        write(mod, "LeVeLs/Mixed_Map/Init.con", "do not copy loose gameplay");
        write(mod, "Objects_Server.zip", "do not import global mod assets");
        write(mod, "LeVeLs/notes/readme.txt", "unrelated directory");
        return mod;
    }
    private static MapPackImport.Result install(File base, File source, DiskSource files, MapPackImport.Publication pointer)
            throws IOException {
        return new MapPackImport(files, null).install(base, files.entry(source), pointer);
    }
    private static File selected(File base, Pointer pointer) { return new File(base.getParentFile(), pointer.selected); }
    private static void fails(File base, File input, DiskSource source, Pointer pointer, String reason) throws Exception {
        String old = pointer.selected; int writes = pointer.writes;
        try { install(base, input, source, pointer); throw new AssertionError("Expected refusal: " + reason); }
        catch (IOException expected) { System.out.println("Refused " + reason + ": " + expected.getMessage()); }
        check(Objects.equals(old, pointer.selected) && writes == pointer.writes, reason + " preserves pointer");
        check(new File(selected(base, pointer), "levels/mixed_map/server.zip").isFile(), reason + " retains previous pack");
        for (File child : base.getParentFile().listFiles())
            check(!child.getName().endsWith(".staging"), reason + " cleans unpublished staging");
    }
    private static void fixtures(File root) throws Exception {
        File base = new File(root, "Game Files/mods/bf2");
        write(base, "ClientArchives.con", "original archive definitions");
        write(base, "levels/mixed_map/server.zip", "original stock map");
        write(base, "objects_client.zip", "original game assets");
        Pointer pointer = new Pointer();
        DiskSource source = new DiskSource();
        File pack = fixture(root, "64coop20YA", true);
        MapPackImport.Result result = install(base, pack, source, basename -> {
            File ready = new File(base.getParentFile(), basename);
            check(ready.isDirectory(), "generation exists before pointer publication");
            check(GameContent.hasCoop(new File(ready, "levels/mixed_map")), "published generation has bot layout");
            check(new File(ready, "levels/mixed_map/info/mixed_map.desc").isFile(), "descriptor exists before publication");
            pointer.publish(basename);
        });
        check(result.maps == 1 && result.files == 4, "only complete map files and Info copied");
        check(pointer.selected.matches("openbf2_maps_[0-9a-f]{32}"), "exact native generation basename");
        File first = selected(base, pointer);
        check(new File(first, "levels/mixed_map/info/loading/screen.png").isFile(), "Info recursively canonicalized");
        check(!new File(first, "objects_server.zip").exists(), "global mod assets excluded");
        check(!new File(first, "levels/mixed_map/init.con").exists(), "loose map overrides excluded");
        check(source.opened.size() == 4, "source reader opens only selected map archive and Info files");
        check(new String(Files.readAllBytes(new File(base,"levels/mixed_map/server.zip").toPath()),StandardCharsets.UTF_8)
                .equals("original stock map"), "stock map retained byte-for-byte");

        File noServer = fixture(root, "missing-pair", true);
        new File(noServer, "LeVeLs/Mixed_Map/Server.Zip").delete();
        fails(base, noServer, new DiskSource(), pointer, "missing archive pair");
        File noDescriptor = fixture(root, "missing-descriptor", true);
        new File(noDescriptor, "LeVeLs/Mixed_Map/INFO/Mixed_Map.DESC").delete();
        fails(base, noDescriptor, new DiskSource(), pointer, "missing descriptor");
        File noBots = fixture(root, "no-bots", false);
        fails(base, noBots, new DiskSource(), pointer, "missing authored navigation");
        File shortZip = fixture(root, "truncated-zip", true);
        write(shortZip, "LeVeLs/Mixed_Map/CLIENT.ZIP", Arrays.copyOf(client(), 20));
        fails(base, shortZip, new DiskSource(), pointer, "truncated archive");
        File badCrc = fixture(root, "bad-crc", true);
        byte[] bytes = client();
        for (int i=0; i+20<bytes.length; ++i) if (bytes[i]==0x50 && bytes[i+1]==0x4b && bytes[i+2]==1 && bytes[i+3]==2) {
            bytes[i+16] ^= 1; break; // Change only the central-directory CRC; ZipFile alone does not validate it.
        }
        write(badCrc, "LeVeLs/Mixed_Map/CLIENT.ZIP", bytes);
        fails(base, badCrc, new DiskSource(), pointer, "incorrect archive CRC");
        File badPath = fixture(root, "bad-archive-path", true);
        write(badPath, "LeVeLs/Mixed_Map/CLIENT.ZIP", zip(Collections.singletonMap("../outside", new byte[] {1})));
        fails(base, badPath, new DiskSource(), pointer, "archive traversal path");
        File collision = fixture(root, "case-conflict", true);
        write(collision, "LeVeLs/Mixed_Map/INFO/mixed_map.desc", "different descriptor");
        fails(base, collision, new DiskSource(), pointer, "case-colliding Info files");
        DiskSource interrupted = new DiskSource() {
            public InputStream open(MapPackImport.Entry file) throws IOException {
                if (file.name.equalsIgnoreCase("server.zip")) return new FilterInputStream(super.open(file)) {
                    public int read(byte[] b, int o, int n) throws IOException { throw new IOException("provider disconnected"); }
                };
                return super.open(file);
            }
        };
        fails(base, pack, interrupted, pointer, "provider interruption");
        DiskSource shortCopy = new DiskSource() {
            public InputStream open(MapPackImport.Entry file) throws IOException {
                if (file.name.equalsIgnoreCase("server.zip")) return new ByteArrayInputStream(new byte[1]);
                return super.open(file);
            }
        };
        fails(base, pack, shortCopy, pointer, "provider short read");
        DiskSource invalidName = new DiskSource() {
            public List<MapPackImport.Entry> children(MapPackImport.Entry folder) throws IOException {
                List<MapPackImport.Entry> entries = super.children(folder);
                if (folder.name.equals("INFO")) entries.add(new MapPackImport.Entry("invalid", "../escape", false, 1));
                return entries;
            }
        };
        fails(base, pack, invalidName, pointer, "provider traversal name");
        String oldPointer = pointer.selected;
        try {
            install(base, pack, new DiskSource(), basename -> { throw new IOException("preference disk failure"); });
            throw new AssertionError("Publication should fail");
        } catch (IOException expected) { /* The previous pointer remains authoritative. */ }
        check(pointer.selected.equals(oldPointer) && first.isDirectory(), "publication failure retains prior generation");
        File unowned = new File(base.getParentFile(), "openbf2_maps_00000000000000000000000000000000");
        write(unowned, "user-data", "not importer-owned");
        File lookalike = new File(base.getParentFile(), "openbf2_maps_backup");
        write(lookalike, "user-data", "not a generation");
        MapPackImport.Result second = install(base, new File(pack, "LeVeLs"), new DiskSource(), pointer);
        check(second.maps == 1 && !second.basename.equals(result.basename), "direct Levels selection creates new generation");
        check(!first.exists(), "prior owned generation collected only after publication");
        check(unowned.isDirectory() && lookalike.isDirectory(), "unowned folders are never removed");
        long owned = Arrays.stream(base.getParentFile().listFiles()).filter(f -> new File(f,"openbf2-map-pack.owner").isFile()).count();
        check(owned == 1, "orphan from failed publication collected after successful replacement");
        check(new File(base,"ClientArchives.con").isFile() && new File(base,"objects_client.zip").length() == 20,
                "base installation retained after replacement and cleanup");
        File mixed = fixture(root, "mixed-pack", true);
        write(mixed, "LeVeLs/NoBots/client.zip", client());
        write(mixed, "LeVeLs/NoBots/server.zip", server(false));
        write(mixed, "LeVeLs/NoBots/Info/nobots.desc", "<map><name>Unsupported</name></map>");
        write(mixed, "LeVeLs/Partial/client.zip", client());
        DiskSource mixedSource = new DiskSource();
        MapPackImport.Result mixedResult = install(base, mixed, mixedSource, pointer);
        check(mixedResult.maps == 1 && mixedResult.skipped == 2, "mixed pack imports supported map and counts unsupported/incomplete maps");
        check(!new File(selected(base,pointer),"levels/nobots").exists()
                && !new File(selected(base,pointer),"levels/partial").exists(), "skipped maps are absent from published generation");
        check(mixedSource.opened.stream().noneMatch(p -> p.contains("NoBots/client.zip") || p.contains("Partial/client.zip")),
                "unsupported/incomplete maps never read their large client archives");
        write(mixed, "LeVeLs/BadBot/client.zip", bytes);
        write(mixed, "LeVeLs/BadBot/server.zip", server(true));
        write(mixed, "LeVeLs/BadBot/Info/badbot.desc", "<map><name>Corrupt</name></map>");
        fails(base,mixed,new DiskSource(),pointer,"corrupt complete bot map in mixed pack");
    }
    private static byte[] hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[128*1024];
        try (InputStream in = new FileInputStream(file)) { for (int n; (n=in.read(bytes))!=-1;) digest.update(bytes,0,n); }
        return digest.digest();
    }
    private static void actual(File sourceRoot, File output) throws Exception {
        File base = new File(output, "Game Files/mods/bf2");
        write(base, "ClientArchives.con", "stock fixture base");
        write(base, "levels/strike_at_karkand/stock-preserved", "original stock files");
        byte[] before = hash(new File(base, "levels/strike_at_karkand/stock-preserved"));
        Pointer pointer = new Pointer();
        MapPackImport.Result result = install(base, sourceRoot, new DiskSource(), pointer);
        check(result.maps >= 2, "genuine community pack imported the expected complete maps");
        File generation = selected(base, pointer);
        for (String name : new String[] {"gulf_of_oman", "strike_at_karkand"}) {
            File original = new File(GameContent.child(sourceRoot,"Levels"),name);
            if (!original.isDirectory()) original = new File(sourceRoot,name);
            File copied = new File(generation,"levels/"+name);
            for (String archive : new String[] {"client.zip","server.zip"})
                check(Arrays.equals(hash(GameContent.child(original,archive)),hash(new File(copied,archive))),
                        name+" "+archive+" copied byte-for-byte after full member CRC validation");
            List<GameContent.Layout> layouts = GameContent.offlineLayouts(copied);
            Set<Integer> sizes = new HashSet<>();
            for (GameContent.Layout layout : layouts) if (layout.botsSupported) sizes.add(layout.size);
            check(sizes.contains(32) && sizes.contains(64), name+" actual larger bot layouts remain discoverable");
            System.out.println("Imported genuine "+name+" bot sizes "+sizes);
        }
        check(Arrays.equals(before,hash(new File(base,"levels/strike_at_karkand/stock-preserved"))), "actual pack import retains base files");
        System.out.println("Actual pack generation: "+generation.getAbsolutePath());
        System.out.println("Actual pack: "+result.maps+" maps imported, "+result.skipped+" skipped");
        Files.write(new File(output,"mapPack.txt").toPath(),(pointer.selected+"\n").getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        File fixtureRoot = Files.createTempDirectory("bf2-map-pack-").toFile();
        fixtures(fixtureRoot);
        if (args.length == 2) actual(new File(args[0]),new File(args[1]));
        System.out.println("Map pack importer: "+checks+" checks passed");
    }
}
