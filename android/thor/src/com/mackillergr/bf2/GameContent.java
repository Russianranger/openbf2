package com.mackillergr.bf2;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class GameContent {
    private GameContent() {}
    static File child(File dir, String name) {
        File direct = new File(dir, name);
        if (direct.exists()) return direct;
        File[] entries = dir.listFiles();
        if (entries != null) for (File entry : entries)
            if (name.equalsIgnoreCase(entry.getName())) return entry;
        return direct;
    }

    static boolean hasCoop(File level) {
        return hasLayout(level, "gpm_coop");
    }
    static boolean hasLayout(File level, String mode) {
        if (!level.getName().matches("[A-Za-z0-9_-]+")) return false;
        String path = "GameModes/" + mode + "/16/GamePlayObjects.con";
        File loose = level;
        for (String segment : path.split("/")) loose = child(loose, segment);
        if (loose.isFile()) return true;
        try (ZipFile zip = new ZipFile(child(level, "server.zip"))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements())
                if (path.equalsIgnoreCase(entries.nextElement().getName().replace('\\','/'))) return true;
        } catch (IOException ignored) { }
        return false;
    }
}
