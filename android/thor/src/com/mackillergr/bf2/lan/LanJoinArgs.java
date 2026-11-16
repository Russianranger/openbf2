package com.mackillergr.bf2.lan;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage-1 launcher integration for existing test13-style --connect argument.
 * This is not proof that the binary's network join can complete.
 */
public final class LanJoinArgs {
    private LanJoinArgs() { }

    public static String[] directIp(String[] base, String ipv4, int gamePort) {
        if (gamePort < 1 || gamePort > 65535) throw new IllegalArgumentException("Bad game port");
        // Require dotted IPv4 only. Prevent untrusted host text from reaching game CLI.
        if (!isIPv4(ipv4)) throw new IllegalArgumentException("Expected IPv4 address");
        List<String> args = new ArrayList<>();
        if (base != null) {
            for (int i = 0; i < base.length; i++) {
                String arg = base[i];
                if (arg == null) continue;
                if (arg.equals("--menu")) continue;
                if (arg.equals("--connect") || arg.equals("--level")) {
                    // These flags consume one argument in the test13 launcher pattern.
                    if (i + 1 < base.length) i++;
                    continue;
                }
                if (arg.equals("--hosted")) continue;
                args.add(arg);
            }
        }
        args.add("--connect");
        args.add(ipv4 + ":" + gamePort);
        return args.toArray(new String[0]);
    }

    public static boolean isIPv4(String host) {
        if (host == null || host.length() > 15) return false;
        String[] pieces = host.split("\\.", -1);
        if (pieces.length != 4) return false;
        for (String part : pieces) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < '0' || part.charAt(i) > '9') return false;
            }
            int octet;
            try { octet = Integer.parseInt(part); }
            catch (NumberFormatException e) { return false; }
            if (octet > 255) return false;
        }
        return true;
    }
}
