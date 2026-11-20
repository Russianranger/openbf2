#pragma once

#include <cstdlib>
#include <cstring>

namespace dice { namespace hfe { namespace offline_coop {

// [CHOSEN, not recovered] Launcher-only exact rosters. Keep this policy outside
// replicated classes: their layouts are also consumed by the AI plugin.
enum { maxBots = 63 }; // The 64th player slot belongs to the local human.
struct BotCounts {
    int team1;
    int team2;
    int total() const { return team1 + team2; }
};

inline bool parseCount(const char* text, int& result)
{
    if (!text || !*text) return false;
    int value = 0;
    for (const char* p = text; *p; ++p) {
        if (*p < '0' || *p > '9') return false;
        value = value * 10 + (*p - '0');
        if (value > maxBots) return false;
    }
    result = value;
    return true;
}

inline bool readBotCounts(BotCounts& counts)
{
    const char* enabled = std::getenv("BF2_PORT_LOCAL_COOP");
    return enabled && std::strcmp(enabled, "1") == 0 &&
        parseCount(std::getenv("BF2_PORT_COOP_TEAM1_BOTS"), counts.team1) &&
        parseCount(std::getenv("BF2_PORT_COOP_TEAM2_BOTS"), counts.team2) &&
        counts.total() <= maxBots;
}

// Humans never consume either team's bot quota. Fill the first team before the
// second, just like the stock ratio path, without floating point rounding.
inline int nextBotTeam(const BotCounts& counts, int team1Bots, int team2Bots)
{
    if (team1Bots < counts.team1) return 1;
    if (team2Bots < counts.team2) return 2;
    return 0;
}

} } }
