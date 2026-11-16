package com.mackillergr.bf2;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class LaunchPlanTest {
    public static void main(String[] roots) {
        List<String> coop = Arrays.asList(GameLaunchPlan.forCoop("/game/mods/bf2", "/native", 1920, 1080,
                "Player", "turnip", "strike_at_karkand"));
        if (!coop.subList(coop.size()-4,coop.size()).equals(
                Arrays.asList("--hosted", "--coop", "--level", "strike_at_karkand"))) throw new AssertionError(coop);
        List<String> lan = Arrays.asList(GameLaunchPlan.forLan("/game/mods/bf2", "/native", 1920, 1080,
                "Player", "turnip", "127.0.0.1", 16567));
        if (lan.contains("--hosted") || lan.contains("--coop") || !lan.contains("127.0.0.1:16567"))
            throw new AssertionError(lan);
        for (String dir : roots) {
            File levels = GameContent.child(new File(dir), "Levels");
            for (String name : new String[]{"strike_at_karkand", "dalian_plant"}) {
                if (!GameContent.hasCoop(GameContent.child(levels, name))) throw new AssertionError(name);
            }
        }
    }
}
