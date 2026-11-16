package com.mackillergr.bf2;

import com.mackillergr.bf2.lan.LanJoinArgs;
import java.util.ArrayList;
import java.util.List;

/** Converts launcher selections to the portable client's SDL arguments. */
public final class GameLaunchPlan {
    private GameLaunchPlan() {}

    public static String[] forMenu(String modDir, String libDir, int width,
                                   int height, String player, String driver) {
        List<String> args = common(modDir, libDir, width, height, player, driver);
        args.add("--menu");
        return args.toArray(new String[0]);
    }

    public static String[] forLan(String modDir, String libDir, int width,
                                  int height, String player, String driver,
                                  String ipv4, int port) {
        List<String> args = common(modDir, libDir, width, height, player, driver);
        String[] safe = LanJoinArgs.directIp(args.toArray(new String[0]), ipv4, port);
        return safe;
    }

    public static String[] forCoop(String modDir, String libDir, int width, int height,
                                   String player, String driver, String level) {
        return forHost(modDir, libDir, width, height, player, driver, level, true);
    }

    public static String[] forHost(String modDir, String libDir, int width, int height,
                                   String player, String driver, String level, boolean coop) {
        if (level == null || !level.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid map name");
        List<String> args = common(modDir, libDir, width, height, player, driver);
        args.add("--hosted");
        if (coop) args.add("--coop");
        add(args, "--level", level);
        return args.toArray(new String[0]);
    }

    static List<String> common(String modDir, String libDir, int width,
                               int height, String player, String driver) {
        if (modDir == null || !modDir.startsWith("/") || modDir.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid absolute BF2 mod path");
        if (libDir == null || !libDir.startsWith("/") || libDir.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid native library path");
        if (width < 640 || width > 7680 || height < 480 || height > 4320)
            throw new IllegalArgumentException("Invalid resolution");
        if (player == null || !player.matches("[A-Za-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("Use 1–32 letters, numbers, underscores or hyphens for the player name");
        if (!"system".equals(driver) && !"turnip".equals(driver))
            throw new IllegalArgumentException("Driver must be system or turnip");
        List<String> args = new ArrayList<>();
        add(args, "--mod", modDir);
        add(args, "--width", Integer.toString(width));
        add(args, "--height", Integer.toString(height));
        add(args, "--name", player);
        add(args, "--libdir", libDir);
        add(args, "--driver", driver);
        return args;
    }
    private static void add(List<String> target, String flag, String value) {
        target.add(flag); target.add(value);
    }
}
