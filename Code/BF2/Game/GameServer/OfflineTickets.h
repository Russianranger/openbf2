#pragma once

#include "HostMapLayout.h"

namespace dice { namespace hfe { namespace offline_tickets {

// [CHOSEN, not recovered] Per-team starting tickets for a direct offline launch.
// Keep the authored defaults and replicated class layouts unchanged.
enum { maxTickets = 9999 }; // TeamScore::setTickets has the same upper limit.

inline void clear()
{
#if defined(__ANDROID__) || defined(BF2_OFFLINE_TICKETS_TEST)
    unsetenv("BF2_PORT_TEAM1_TICKETS");
    unsetenv("BF2_PORT_TEAM2_TICKETS");
#endif
}

inline bool parse(const char* text, unsigned int& result)
{
    if (!text || !*text) return false;
    unsigned int value = 0;
    for (const char* p = text; *p; ++p)
    {
        if (*p < '0' || *p > '9') return false;
        value = value * 10 + static_cast<unsigned int>(*p - '0');
        if (value > maxTickets) return false;
    }
    if (!value) return false;
    result = value;
    return true;
}

inline bool readOverride(unsigned int team, const char* level, const char* mode,
                         int size, unsigned int& tickets)
{
#if defined(__ANDROID__) || defined(BF2_OFFLINE_TICKETS_TEST)
    if (team - 1 > 1) return false;
    const char* value = std::getenv(team == 1 ? "BF2_PORT_TEAM1_TICKETS" : "BF2_PORT_TEAM2_TICKETS");
    if (!value) return false;
    const char* direct = std::getenv("BF2_PORT_DIRECT_START");
    const char* offline = std::getenv("BF2_PORT_LOCAL_COOP");
    return direct && std::strcmp(direct, "1") == 0 &&
        offline && std::strcmp(offline, "1") == 0 && size > 0 &&
        host_layout::selectedSize(level, mode) == size && parse(value, tickets);
#else
    return false;
#endif
}

} } }
