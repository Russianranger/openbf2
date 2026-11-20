package com.mackillergr.bf2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.*;
import java.util.zip.*;

/** Display names come from the owner's map. Folder identifiers are used for launch. */
final class MapBriefing {
    final String id, title;
    final String[] sides = {"Team 1", "Team 2"};
    private static final Pattern TITLE = Pattern.compile("<name>\\s*([^<>]{1,100}?)\\s*</name>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEAM = Pattern.compile(
        "^\\s*gameLogic\\.setTeamName\\s+([12])\\s+(?:\"([^\"\\r\\n]{1,48})\"|([A-Za-z0-9_-]{1,48}))(?:\\s.*)?$", Pattern.CASE_INSENSITIVE);

    MapBriefing(File level) {
        id = level.getName();
        File descriptor = GameContent.child(GameContent.child(level, "info"), id + ".desc");
        String fallback = id.replace('_', ' ');
        String name = fallback.isEmpty() ? fallback : fallback.substring(0, 1).toUpperCase(Locale.ROOT) + fallback.substring(1);
        if (descriptor.isFile() && descriptor.length() <= 65536) {
            try {
                Matcher match = TITLE.matcher(new String(java.nio.file.Files.readAllBytes(descriptor.toPath()), StandardCharsets.UTF_8));
                if (match.find()) name = match.group(1).trim().replace("&amp;", "&");
            } catch (IOException ignored) {}
        }
        title = name;
        File init = GameContent.child(level, "Init.con");
        try {
            if (init.isFile()) {
                try (InputStream in = new FileInputStream(init)) { readTeams(in); }
            } else {
                try (ZipFile zip = new ZipFile(GameContent.child(level, "server.zip"))) {
                    for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                        ZipEntry entry = e.nextElement();
                        if (!entry.getName().equalsIgnoreCase("Init.con") || entry.getSize() > 262144) continue;
                        try (InputStream in = zip.getInputStream(entry)) { readTeams(in); }
                        break;
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private void readTeams(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
        String line;
        int seen = 0;
        while (seen < 262144 && (line = reader.readLine()) != null) {
            seen += line.length() + 1;
            Matcher match = TEAM.matcher(line);
            if (match.matches()) sides[Integer.parseInt(match.group(1)) - 1] =
                    match.group(2) == null ? match.group(3) : match.group(2).trim();
        }
    }
}
