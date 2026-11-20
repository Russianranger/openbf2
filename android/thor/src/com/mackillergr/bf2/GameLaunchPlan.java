package com.mackillergr.bf2;

import com.mackillergr.bf2.lan.LanJoinArgs;
import java.util.ArrayList;
import java.util.List;

/** Converts launcher selections to the portable client's SDL arguments. */
public final class GameLaunchPlan {
    public static final int MAX_COOP_BOTS = 63;
    public static final int MAX_STARTING_TICKETS = 9999;
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
        return forCoop(modDir, libDir, width, height, player, driver, level, 8, 8);
    }

    public static String[] forCoop(String modDir, String libDir, int width, int height,
                                   String player, String driver, String level, int team1, int team2) {
        return forCoop(modDir, libDir, width, height, player, driver, level, "gpm_coop", 16, team1, team2);
    }

    public static String[] forCoop(String modDir, String libDir, int width, int height,
                                   String player, String driver, String level, String mode, int size,
                                   int team1, int team2) {
        return forCoop(modDir, libDir, width, height, player, driver, level, mode, size,
                team1, team2, 0, 0);
    }

    /** Zero leaves that team's authored map value unchanged; overrides are absolute, not percentages. */
    public static String[] forCoop(String modDir, String libDir, int width, int height,
                                   String player, String driver, String level, String mode, int size,
                                   int team1, int team2, int tickets1, int tickets2) {
        if (tickets1 < 0 || tickets1 > MAX_STARTING_TICKETS || tickets2 < 0 || tickets2 > MAX_STARTING_TICKETS)
            throw new IllegalArgumentException("Use Map default or 1–9999 starting tickets for each team");
        if (team1 < 0 || team1 > MAX_COOP_BOTS || team2 < 0 || team2 > MAX_COOP_BOTS
                || team1 + team2 > MAX_COOP_BOTS)
            throw new IllegalArgumentException("Use 0–63 bots total; one slot is reserved for you");
        if (!GameContent.validMode(mode) || !GameContent.validSize(size))
            throw new IllegalArgumentException("Select an available 16, 32 or 64 player layout");
        if ("gpm_cq".equals(mode) && team1 + team2 != 0)
            throw new IllegalArgumentException("This conquest layout requires zero bots; select an authored co-op layout for bots");
        List<String> args = host(modDir, libDir, width, height, player, driver, level, true, mode, size);
        add(args, "--team1-bots", Integer.toString(team1));
        add(args, "--team2-bots", Integer.toString(team2));
        if (tickets1 != 0) add(args, "--tickets-team1", Integer.toString(tickets1));
        if (tickets2 != 0) add(args, "--tickets-team2", Integer.toString(tickets2));
        return args.toArray(new String[0]);
    }

    public static String[] forHost(String modDir, String libDir, int width, int height,
                                   String player, String driver, String level, boolean coop) {
        return forHost(modDir, libDir, width, height, player, driver, level, coop, 16);
    }

    public static String[] forHost(String modDir, String libDir, int width, int height,
                                   String player, String driver, String level, boolean coop, int size) {
        if (coop) return forCoop(modDir, libDir, width, height, player, driver, level, "gpm_coop", size, 8, 8);
        return host(modDir, libDir, width, height, player, driver, level, false, "gpm_cq", size).toArray(new String[0]);
    }

    private static List<String> host(String modDir, String libDir, int width, int height,
                                    String player, String driver, String level, boolean coop, String mode, int size) {
        if (level == null || !level.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid map name");
        if (!GameContent.validSize(size)) throw new IllegalArgumentException("Invalid map layout size");
        List<String> args = common(modDir, libDir, width, height, player, driver);
        args.add("--hosted");
        if (coop) args.add("--coop");
        add(args, "--level", level);
        add(args, "--mode", mode);
        add(args, "--size", Integer.toString(size));
        return args;
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
