#pragma once

#include <cstdlib>
#include <cstring>

namespace dice { namespace hfe { namespace host_layout {

// [CHOSEN, not recovered] Native launcher selection; no replicated class or
// protocol layout changes. Map size is independent of the human/player limit.
inline int parseSize(const char* text)
{
    if (!text) return 0;
    if (std::strcmp(text, "16") == 0) return 16;
    if (std::strcmp(text, "32") == 0) return 32;
    if (std::strcmp(text, "64") == 0) return 64;
    return 0;
}

inline bool validMode(const char* mode)
{
    return mode && (std::strcmp(mode, "gpm_coop") == 0 ||
                    std::strcmp(mode, "sp1") == 0 || std::strcmp(mode, "gpm_cq") == 0);
}

inline bool equalAsciiNoCase(const char* left, const char* right)
{
    if (!left || !right) return false;
    while (*left && *right)
    {
        unsigned char a = static_cast<unsigned char>(*left++);
        unsigned char b = static_cast<unsigned char>(*right++);
        if (a >= 'A' && a <= 'Z') a += 'a' - 'A';
        if (b >= 'A' && b <= 'Z') b += 'a' - 'A';
        if (a != b) return false;
    }
    return *left == *right;
}

inline int selectedSize(const char* level, const char* mode)
{
    const char* selectedLevel = std::getenv("BF2_PORT_HOST_LEVEL");
    const char* selectedMode = std::getenv("BF2_PORT_HOST_MODE");
    if (!level || !mode || !selectedLevel || !validMode(selectedMode) ||
        !equalAsciiNoCase(level, selectedLevel) || !equalAsciiNoCase(mode, selectedMode))
        return 0;
    return parseSize(std::getenv("BF2_PORT_HOST_SIZE"));
}

} } }
