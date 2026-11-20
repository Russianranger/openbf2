package com.mackillergr.bf2;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Discovery fixtures check file contracts; real map arguments provide authored-content coverage. */
public final class GameLayoutsTest {
    private static int checks;
    private static final String GPO = "ObjectTemplate.create SpawnPoint base_spawn\nObject.create base_spawn\n";
    private static void check(boolean condition, String reason) {
        checks++; if (!condition) throw new AssertionError(reason);
    }
    private static GameContent.Layout size(List<GameContent.Layout> values, int size) {
        for (GameContent.Layout value : values) if (value.size == size) return value;
        throw new AssertionError("Missing size " + size + " in " + values);
    }
    private static void write(File level, String name, byte[] bytes) throws IOException {
        Path path = level.toPath().resolve(name); Files.createDirectories(path.getParent()); Files.write(path, bytes);
    }
    private static void write(File level, String name, String text) throws IOException {
        write(level, name, text.getBytes(StandardCharsets.ISO_8859_1));
    }
    private static void fixtures() throws Exception {
        File level = Files.createTempDirectory("bf2_layout_").resolve("mixed_map").toFile(); level.mkdirs();
        ByteBuffer tree = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        tree.putInt(1).putInt(3).putFloat(-50).putFloat(-50).putFloat(50).putFloat(50).putFloat(100).putInt(1);
        Map<String,byte[]> files = new LinkedHashMap<>();
        files.put("AIPathFinding/Infantry.qtr", tree.array()); files.put("AIPathFinding/Infantry.clb", new byte[8]);
        files.put("AI/AI.ai", "ai.init 2\n".getBytes(StandardCharsets.ISO_8859_1));
        for (int count : new int[]{16,32,64}) {
            files.put("GameModes/gpm_cq/"+count+"/GamePlayObjects.con", GPO.getBytes(StandardCharsets.ISO_8859_1));
            files.put("GameModes/gpm_coop/"+count+"/GamePlayObjects.con", GPO.getBytes(StandardCharsets.ISO_8859_1));
        }
        files.put("GameModes/gpm_coop/16/AI/StrategicAreas.ai", "aiStrategicArea.create alpha 0/0 0/0 10 1\n".getBytes(StandardCharsets.ISO_8859_1));
        files.put("GameModes/gpm_coop/32/AI/StrategicAreas.ai", "aiStrategicArea.createFromControlPoint alpha\n".getBytes(StandardCharsets.ISO_8859_1));
        files.put("GameModes/gpm_coop/64/AI/StrategicAreas.ai", "rem *** Create strategic areas ***\n".getBytes(StandardCharsets.ISO_8859_1));
        files.put("GameModes/sp1/64/GamePlayObjects.con", "rem placeholders\nif v_arg1 == host\nendIf\n".getBytes(StandardCharsets.ISO_8859_1));
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(new File(level,"server.zip")))) {
            for (Map.Entry<String,byte[]> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        List<GameContent.Layout> result = GameContent.offlineLayouts(level);
        check(result.size()==3,"all real sizes");
        check(size(result,16).botsSupported,"create strategic command");
        check(size(result,32).botsSupported,"createFromControlPoint strategic command");
        check(!size(result,64).botsSupported && size(result,64).mode.equals("gpm_cq"),"comment-only AI is not bot support");
        check(GameContent.layouts(level,"sp1").isEmpty(),"placeholder gameplay is excluded");
        write(level,"gamemodes/GPM_COOP/32/ai/strategicareas.ai","rem loose override removes AI\n");
        check(!size(GameContent.offlineLayouts(level),32).botsSupported,"loose case-insensitive override wins");
        write(level,"gamemodes/sp1/64/GamePlayObjects.con","run ../../../Shared/gameplay.con\n");
        write(level,"Shared/gameplay.con",GPO);
        write(level,"gamemodes/sp1/64/AI/StrategicAreas.ai","run ../../../../Shared/strategy.ai\n");
        write(level,"Shared/strategy.ai","aiStrategicArea.createFromControlPoint alpha\nrun strategy.ai\n");
        check(size(GameContent.offlineLayouts(level),64).mode.equals("sp1"),"authored SP1 and bounded include cycle");
        write(level,"aipathfinding/infantry.qtr",new byte[32]);
        check(!size(GameContent.offlineLayouts(level),16).botsSupported,"invalid navigation header rejects bot capability");
        check(GameContent.layouts(level,"../gpm_cq").isEmpty(),"invalid mode rejected");
    }
    private static void stock(File levels) {
        int botLayouts=0;
        File[] maps=levels.listFiles(); if(maps==null)throw new AssertionError(levels);
        Arrays.sort(maps,Comparator.comparing(File::getName));
        for(File map:maps) if(map.isDirectory()) {
            List<GameContent.Layout> found=GameContent.offlineLayouts(map);
            System.out.println("stock "+map.getName()+" "+found);
            for(GameContent.Layout layout:found) if(layout.botsSupported) {
                botLayouts++;check(layout.size==16,"stock larger layout falsely advertised: "+map);
            }
        }
        check(botLayouts==17,"stock co-op layouts expected17, found"+botLayouts);
        for(String name:new String[]{"strike_at_karkand","gulf_of_oman"}) {
            List<GameContent.Layout> found=GameContent.offlineLayouts(GameContent.child(levels,name));
            check(size(found,16).botsSupported,name+"16 bot layout");
            check(!size(found,32).botsSupported&&!size(found,64).botsSupported,name+"32/64 human-only");
        }
        check(GameContent.layouts(GameContent.child(levels,"dragon_valley"),"sp1").size()==1,"stock SP1 placeholders excluded");
    }
    private static void authored(File levels) {
        for(String name:new String[]{"strike_at_karkand","gulf_of_oman"}) {
            List<GameContent.Layout> found=GameContent.offlineLayouts(GameContent.child(levels,name));
            System.out.println("authored "+name+" "+found);
            for(int count:new int[]{32,64}) check(size(found,count).botsSupported&&size(found,count).mode.equals("gpm_coop"),name+count+" authored bot layout");
        }
    }
    public static void main(String[] args) throws Exception {
        fixtures();
        if(args.length>0)stock(new File(args[0]));
        if(args.length>1)authored(new File(args[1]));
        System.out.println("PASS "+checks+" layout discovery checks");
    }
}
