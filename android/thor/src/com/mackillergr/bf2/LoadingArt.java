package com.mackillergr.bf2;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/** Resolves owner-imported loose loading artwork without changing game data. */
final class LoadingArt {
    private LoadingArt() {}
    static String levelName(String path) {
        String name = path.replace('\\', '/');
        while (name.endsWith("/")) name = name.substring(0, name.length()-1);
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash+1);
        return name.matches("[A-Za-z0-9_-]+") ? name : "";
    }
    static File find(File mod, String map) {
        String name = levelName(map);
        if (name.isEmpty()) return null;
        File info = GameContent.child(GameContent.child(GameContent.child(mod, "levels"), name), "info");
        File exact = GameContent.child(info, "loadmap.png");
        if (exact.isFile()) return exact;
        File[] files = info.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).matches("loadmap[^/]*\\.png"));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return files[0];
    }
}
