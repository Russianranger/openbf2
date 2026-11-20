package com.mackillergr.bf2;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class LaunchPlanTest {
    public static void main(String[] roots) {
        List<String> coop = Arrays.asList(GameLaunchPlan.forCoop("/game/mods/bf2", "/native", 1920, 1080,
                "Player", "turnip", "strike_at_karkand"));
        if (!coop.subList(coop.size()-12,coop.size()).equals(
                Arrays.asList("--hosted", "--coop", "--level", "strike_at_karkand",
                        "--mode", "gpm_coop", "--size", "16",
                        "--team1-bots", "8", "--team2-bots", "8"))) throw new AssertionError(coop);
        int cases = 0;
        for (int first = 0; first <= GameLaunchPlan.MAX_COOP_BOTS; first++) {
            for (int second = 0; second <= GameLaunchPlan.MAX_COOP_BOTS - first; second++) {
                List<String> exact = Arrays.asList(GameLaunchPlan.forCoop("/game/mods/bf2", "/native",
                        1920, 1080, "Player", "system", "strike_at_karkand", first, second));
                if (!exact.get(exact.indexOf("--team1-bots") + 1).equals(Integer.toString(first))
                        || !exact.get(exact.indexOf("--team2-bots") + 1).equals(Integer.toString(second)))
                    throw new AssertionError(exact);
                cases++;
            }
        }
        for (int[] invalid : new int[][]{{-1,0},{0,-1},{64,0},{0,64},{32,32},
                {Integer.MAX_VALUE,Integer.MAX_VALUE},{Integer.MIN_VALUE,0}}) {
            try {
                GameLaunchPlan.forCoop("/game/mods/bf2", "/native", 1920, 1080,
                        "Player", "system", "strike_at_karkand", invalid[0], invalid[1]);
                throw new AssertionError("Accepted invalid bot counts " + Arrays.toString(invalid));
            } catch (IllegalArgumentException expected) { }
        }
        List<String> lan = Arrays.asList(GameLaunchPlan.forLan("/game/mods/bf2", "/native", 1920, 1080,
                "Player", "turnip", "127.0.0.1", 16567));
        if (lan.contains("--hosted") || lan.contains("--coop") || !lan.contains("127.0.0.1:16567"))
            throw new AssertionError(lan);
        for (String[] noOverride : new String[][] {
                coop.toArray(new String[0]), lan.toArray(new String[0]),
                GameLaunchPlan.forMenu("/game/mods/bf2", "/native", 1920, 1080, "Player", "system"),
                GameLaunchPlan.forHost("/game/mods/bf2", "/native", 1920, 1080,
                        "Player", "system", "strike_at_karkand", false, 64)}) {
            for (String arg : noOverride) if (arg.startsWith("--tickets-"))
                throw new AssertionError("Default/menu/LAN inherited ticket overrides");
        }
        int ticketCases = 0;
        for (String mode : new String[]{"gpm_coop", "sp1", "gpm_cq"}) {
            for (int first : new int[]{0, 1, 100, 9999}) for (int second : new int[]{0, 1, 200, 9999}) {
                List<String> exact = Arrays.asList(GameLaunchPlan.forCoop("/game/mods/bf2", "/native", 1920, 1080,
                        "Player", "system", "strike_at_karkand", mode, 64, 0, 0, first, second));
                for (int team = 1; team <= 2; team++) {
                    int expected = team == 1 ? first : second;
                    int flag = exact.indexOf("--tickets-team" + team);
                    if (expected == 0 ? flag != -1 : flag == -1 || !Integer.toString(expected).equals(exact.get(flag+1)))
                        throw new AssertionError(exact);
                }
                ticketCases++;
            }
        }
        for (int invalid : new int[]{-1, 10000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (int team = 1; team <= 2; team++) {
                try {
                    GameLaunchPlan.forCoop("/game/mods/bf2", "/native", 1920, 1080,
                            "Player", "system", "strike_at_karkand", "gpm_coop", 16, 8, 8,
                            team == 1 ? invalid : 0, team == 2 ? invalid : 0);
                    throw new AssertionError("Accepted invalid ticket count " + invalid);
                } catch (IllegalArgumentException expected) { }
            }
        }
        for (int size : new int[]{16,32,64}) {
            for (String mode : new String[]{"gpm_coop", "sp1", "gpm_cq"}) {
                List<String> layout = Arrays.asList(GameLaunchPlan.forCoop("/game/mods/bf2", "/native", 1920, 1080,
                        "Player", "system", "strike_at_karkand", mode, size, 0, 0));
                if (!layout.get(layout.indexOf("--mode")+1).equals(mode)
                        || !layout.get(layout.indexOf("--size")+1).equals(Integer.toString(size)))
                    throw new AssertionError(layout);
            }
            List<String> hosted = Arrays.asList(GameLaunchPlan.forHost("/game/mods/bf2", "/native", 1920, 1080,
                    "Player", "system", "strike_at_karkand", false, size));
            if (hosted.contains("--coop") || !hosted.get(hosted.indexOf("--size")+1).equals(Integer.toString(size)))
                throw new AssertionError(hosted);
        }
        for (String mode : new String[]{"gpm_cq", "other", "../gpm_coop"}) {
            try {
                GameLaunchPlan.forCoop("/game/mods/bf2", "/native", 1920, 1080,
                        "Player", "system", "strike_at_karkand", mode, 64, 1, 0);
                throw new AssertionError("Accepted bots for " + mode);
            } catch (IllegalArgumentException expected) { }
        }
        for (int size : new int[]{0,1,17,63,128,-1,Integer.MAX_VALUE}) {
            try {
                GameLaunchPlan.forHost("/game/mods/bf2", "/native", 1920, 1080,
                        "Player", "system", "strike_at_karkand", false, size);
                throw new AssertionError("Accepted layout " + size);
            } catch (IllegalArgumentException expected) { }
        }
        for (String dir : roots) {
            File levels = GameContent.child(new File(dir), "Levels");
            for (String name : new String[]{"strike_at_karkand", "dalian_plant"}) {
                if (!GameContent.hasCoop(GameContent.child(levels, name))) throw new AssertionError(name);
            }
        }
        System.out.println("Launch plan: " + cases + " exact rosters, " + ticketCases
                + " ticket combinations, invalid bounds and LAN isolation passed");
    }
}
