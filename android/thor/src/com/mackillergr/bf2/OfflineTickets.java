package com.mackillergr.bf2;

import android.content.SharedPreferences;
import java.util.Locale;

/** [CHOSEN, not recovered] Per-map offline starting values, independent of layout size. */
final class OfflineTickets {
    private OfflineTickets() {}

    static int parse(String input) {
        String value = input.trim();
        if (value.isEmpty()) return 0;
        if (!value.matches("[0-9]{1,4}")) throw invalid();
        int count = Integer.parseInt(value);
        if (count < 1 || count > GameLaunchPlan.MAX_STARTING_TICKETS) throw invalid();
        return count;
    }

    static String label(int value) { return value == 0 ? "Map default" : Integer.toString(value); }

    static int read(SharedPreferences prefs, String map, int team) {
        if (map == null) return 0;
        try {
            int count = prefs.getInt(key(map, team), 0);
            return count >= 1 && count <= GameLaunchPlan.MAX_STARTING_TICKETS ? count : 0;
        } catch (ClassCastException corruptPreference) { return 0; }
    }

    static void save(SharedPreferences prefs, String map, int first, int second) {
        if (first < 0 || first > GameLaunchPlan.MAX_STARTING_TICKETS
                || second < 0 || second > GameLaunchPlan.MAX_STARTING_TICKETS) throw invalid();
        SharedPreferences.Editor edit = prefs.edit();
        for (int team = 1; team <= 2; team++) {
            int value = team == 1 ? first : second;
            if (value == 0) edit.remove(key(map, team)); else edit.putInt(key(map, team), value);
        }
        edit.apply();
    }

    private static String key(String map, int team) {
        // Preference keys are not filesystem paths. An unlaunchable folder must not crash map discovery.
        if (map == null || map.isEmpty() || team < 1 || team > 2)
            throw new IllegalArgumentException("Invalid map or team for starting tickets");
        return "tickets." + map.toLowerCase(Locale.ROOT) + ".team" + team;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Enter 1–9999, or leave blank for Map default");
    }
}
