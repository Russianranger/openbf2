// [TEMP] Android presentation lifecycle without any renderer or game-content substitute.
#include "Game/Main/AndroidUiState.h"
#include <cassert>
#include <limits>
using namespace dice::hfe::androidui;
int main() {
    assert(snapshot() == pack(Starting));
    frame(true, false, true, false, false);
    assert(snapshot() == pack(Intro));
    frame(true, true, true, false, false);
    assert(snapshot() == pack(Intro, true));
    frame(false, false, true, false, false);
    assert(snapshot() == pack(Menu));
    beginLoading("Levels/Gulf_of_Oman/");
    progress(42.9f);
    assert(level() == "Levels/Gulf_of_Oman/" && snapshot() == pack(Loading, false, 42));
    frame(false, false, false, false, true);
    assert(snapshot() == pack(Loading, false, 42));
    progress(100);
    assert((snapshot() & 255) == Loading); // Percent alone cannot release loading controls.
    frame(false, false, false, true, true);
    assert(snapshot() == pack(Game));
    progress(25); // A late callback after completion must not restore the loading view.
    assert(snapshot() == pack(Game));
    frame(false, false, true, true, true);
    assert(snapshot() == pack(Menu));
    beginLoading("Levels/strike_at_karkand/");
    assert(snapshot() == pack(Loading));
    progress(std::numeric_limits<float>::quiet_NaN());
    assert(snapshot() == pack(Loading));
    progress(200);
    assert(snapshot() == pack(Loading, false, 100));
    publish(Menu); // Failed/aborted load and shutdown publication.
    frame(false, false, false, false, true);
    assert(snapshot() == pack(Menu));
    progress(50);
    assert(snapshot() == pack(Menu));
    beginLoading("another_map");
    frame(false, false, false, false, false);
    assert(snapshot() == pack(Menu));
    // Early init/interface failures and a successful noAutoJoin return do not await a map.
    const auto noLoad = [](bool result) { StartAttempt attempt; return result; };
    assert(!noLoad(false) && snapshot() == pack(Menu));
    assert(noLoad(true) && snapshot() == pack(Menu));
    frame(false, false, false, false, true); // An allocated game alone must not restore Loading.
    assert(snapshot() == pack(Menu));
    {
        StartAttempt accepted;
        accepted.awaitFrame(); // Server successfully started, or client accepted for joining.
    }
    assert(snapshot() == pack(Loading));
    beginLoading("reload_map");
    progress(25);
    frame(false, false, false, true, true);
    assert(snapshot() == pack(Game));
}
