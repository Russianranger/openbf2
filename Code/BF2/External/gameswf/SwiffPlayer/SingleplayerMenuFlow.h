// [CHOSEN, not recovered] Android first-run Singleplayer opens the stock map picker.
#pragma once

#include <cstddef>
#include <cstring>
#include <initializer_list>
#include "gameswf_action.h"
#include "gameswf_impl.h"

namespace openbf2 {

inline gameswf::movie* singleplayerMenuRoot(const gameswf::fn_call& fn)
{
    return fn.env && fn.env->get_target() ? fn.env->get_target()->get_root_movie() : nullptr;
}

inline void chooseSingleplayerMap(const gameswf::fn_call& fn)
{
    gameswf::movie* root = singleplayerMenuRoot(fn);
    if (!root) return;

    // The stock first-run button has already created/selected its offline profile.
    // Clear its quick-start latch before a profile transition reloads this movie.
    root->call_method("dice.bf2.Logic.setStorageString", "%s,%s", "noobieSP", "0");
    root->call_method("dice.bf2.Logic.setStorageString", "%s,%s", "loginType", "1");
    root->call_method("dice.bf2.Logic.setStorageString", "%s,%s", "currentFrameName", "trainingCreate");
    root->set_member("loginType", gameswf::as_value(1));
    // On the profile-reload path, the root's startup script performs one final gotoPage(nextPage).
    root->set_member("nextPage", gameswf::as_value("trainingCreate"));
    root->call_method("setSingleplayerSettings", "");
    root->call_method("gotoPage", "%s", "trainingCreate");
}

inline bool prepareSingleplayerMapMenu(const gameswf::fn_call& fn, const char* key, const char* value)
{
    if (std::strcmp(key, "noobieSP") != 0 || std::strcmp(value, "1") != 0) return false;
    gameswf::movie* root = singleplayerMenuRoot(fn);
    if (!root) return false;

    // Only adapt the known stock menu workflow. Do not change generic createServer,
    // command-line deployment, a mod's unrelated storage, or movies without this frontend.
    for (const char* method : {"createDefaultTraining", "setSingleplayerSettings", "gotoPage"})
    {
        gameswf::as_value function;
        if (!root->get_member(method, &function) || !function.is_function()) return false;
    }
    root->set_member("createDefaultTraining", gameswf::as_value(&chooseSingleplayerMap));
    return true;
}

} // namespace openbf2
