// [CHOSEN, not recovered] A launcher-selected, read-only level overlay for offline play.
#pragma once

#include "../../Game/GameServer/HostMapLayout.h"
#include <string>
#include <vector>
#if defined(__ANDROID__) || defined(BF2_OFFLINE_MAP_PACK_TEST)
#include <sys/stat.h>
#endif

namespace dice { namespace hfe { namespace offline_maps {

inline bool validId(const std::string& id)
{
    const std::string prefix = "openbf2_maps_";
    if (id.size() != prefix.size() + 32 || id.compare(0, prefix.size(), prefix)) return false;
    for (std::size_t i = prefix.size(); i < id.size(); ++i)
        if (!((id[i] >= '0' && id[i] <= '9') || (id[i] >= 'a' && id[i] <= 'f'))) return false;
    return true;
}

inline bool validLevel(const std::string& level)
{
    if (level.empty() || level.size() > 80) return false;
    for (char c : level)
        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
              (c >= '0' && c <= '9') || c == '_' || c == '-')) return false;
    return true;
}

inline std::string lower(std::string text)
{
    for (char& c : text) if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
    return text;
}

struct Selection
{
    std::string id, level, mode;
    int size = 0;
    std::string levelDirectory() const { return "mods/" + id + "/levels/" + level + "/"; }
};

inline const char* requestedId()
{
#if defined(__ANDROID__) || defined(BF2_OFFLINE_MAP_PACK_TEST)
    return std::getenv("BF2_PORT_MAP_PACK");
#else
    return nullptr;
#endif
}

inline bool selection(Selection& out)
{
    const char* id = requestedId();
    if (!id) return false;
    const char* level = std::getenv("BF2_PORT_HOST_LEVEL");
    const char* mode = std::getenv("BF2_PORT_HOST_MODE");
    const char* offline = std::getenv("BF2_PORT_LOCAL_COOP");
    const char* direct = std::getenv("BF2_PORT_DIRECT_START");
    const int size = host_layout::parseSize(std::getenv("BF2_PORT_HOST_SIZE"));
    if (!id || !validId(id) || !level || !validLevel(level) || !host_layout::validMode(mode) ||
        !size || !offline || std::strcmp(offline, "1") || !direct || std::strcmp(direct, "1")) return false;
    out.id = id; out.level = lower(level); out.mode = mode; out.size = size;
    return true;
}

inline bool requiredFilesPresent(const Selection& selected)
{
#if defined(__ANDROID__) || defined(BF2_OFFLINE_MAP_PACK_TEST)
    for (const std::string& file : {std::string("server.zip"), std::string("client.zip"),
                                  "info/" + selected.level + ".desc"})
    {
        struct stat info;
        if (stat((selected.levelDirectory() + file).c_str(), &info) != 0 ||
            !S_ISREG(info.st_mode) || info.st_size <= 0) return false;
    }
    return true;
#else
    (void)selected;
    return false;
#endif
}

// Canonicalize virtual names before matching complete path components. A level
// script may legitimately traverse out to shared Objects/ or Common/ assets.
inline std::string normalized(std::string path)
{
    for (char& c : path) if (c == '\\') c = '/';
    const bool absolute = !path.empty() && path[0] == '/';
    std::vector<std::string> parts;
    for (std::size_t start = 0; start <= path.size();)
    {
        const std::size_t end = path.find('/', start);
        const std::string part = lower(path.substr(start, end == std::string::npos ? end : end - start));
        if (part == "..") { if (parts.empty()) return std::string(); parts.pop_back(); }
        else if (!part.empty() && part != ".") parts.push_back(part);
        if (end == std::string::npos) break;
        start = end + 1;
    }
    std::string result = absolute ? "/" : "";
    for (const std::string& part : parts) { if (!result.empty() && result.back() != '/') result += '/'; result += part; }
    return result;
}

// True means the path belongs to the selected level and must never fall back to
// loose stock-level files. Only imported archives and Info are loose overlay data;
// all other level data must be found inside those mounted archives.
inline bool resolve(const std::string& name, std::string& loose, std::string* logical = nullptr)
{
    loose.clear();
    if (logical) logical->clear();
    if (!requestedId() || name.empty()) return false;
    // Objects/Common dominate resource reads. Avoid allocations and environment
    // decoding unless this can be a level path, alias, or relative traversal.
    const auto starts = [&](const char* prefix) {
        for (std::size_t i = 0; prefix[i]; ++i)
        {
            if (i >= name.size()) return false;
            char c = name[i];
            if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
            if (c == '\\') c = '/';
            if (c != prefix[i]) return false;
        }
        return true;
    };
    if (name[0] != '/' && name[0] != '\\' && name[0] != '@' && name[0] != '.' &&
        !starts("levels/") && !starts("mods/") && name.find("..") == std::string::npos) return false;
    Selection selected;
    if (!selection(selected)) return false;
    std::string path = normalized(name);
    const char* root = std::getenv("BF2_ANDROID_GAME_ROOT");
    if (root && !path.empty() && path[0] == '/')
    {
        const std::string prefix = normalized(root) + "/";
        if (path.compare(0, prefix.size(), prefix) == 0) path.erase(0, prefix.size());
    }
    for (const char* prefix : {"@root@/", "@mod@/"})
        if (path.compare(0, std::strlen(prefix), prefix) == 0) path.erase(0, std::strlen(prefix));
    if (path.compare(0, 9, "mods/bf2/") == 0) path.erase(0, 9);
    const std::string packPrefix = "mods/" + selected.id + "/";
    if (path.compare(0, packPrefix.size(), packPrefix) == 0) path.erase(0, packPrefix.size());
    const std::string prefix = "levels/" + selected.level;
    if (path == prefix) { if (logical) *logical = path; return true; }
    if (path.compare(0, prefix.size() + 1, prefix + "/")) return false;
    if (logical) *logical = path;
    const std::string suffix = path.substr(prefix.size() + 1);
    if (suffix == "server.zip" || suffix == "client.zip" || suffix == "info" ||
        suffix.compare(0, 5, "info/") == 0)
        loose = selected.levelDirectory() + suffix;
    return true;
}

} } }
