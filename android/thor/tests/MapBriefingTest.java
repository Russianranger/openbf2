package com.mackillergr.bf2;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

/** Run with GameContent.java and MapBriefing.java on a host JDK. */
public final class MapBriefingTest {
    public static void main(String[] args) throws Exception {
        Path map = Files.createTempDirectory("briefing").resolve("test_map");
        Path info = Files.createDirectories(map.resolve("Info"));
        Files.write(info.resolve("TEST_MAP.desc"), "<map><name> Delta Crossing </name></map>".getBytes(StandardCharsets.UTF_8));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(map.resolve("Server.zip")))) {
            zip.putNextEntry(new ZipEntry("init.CON"));
            zip.write(("gameLogic.setTeamName 1 \"MEC\"\r\nGAMELOGIC.setTeamName 2 US\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
        }
        MapBriefing fromZip = new MapBriefing(map.toFile());
        if (!fromZip.title.equals("Delta Crossing") || !fromZip.sides[0].equals("MEC") ||
                !fromZip.sides[1].equals("US") || !fromZip.id.equals("test_map")) throw new AssertionError();
        Files.write(map.resolve("Init.CON"), "gameLogic.setTeamName 2 \"Custom Army\"".getBytes(StandardCharsets.US_ASCII));
        MapBriefing loose = new MapBriefing(map.toFile());
        if (!loose.sides[0].equals("Team 1") || !loose.sides[1].equals("Custom Army")) throw new AssertionError();
        Files.delete(info.resolve("TEST_MAP.desc"));
        if (!new MapBriefing(map.toFile()).title.equals("Test map")) throw new AssertionError();
    }
}
