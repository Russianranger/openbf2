// [CHOSEN, not recovered] Android presentation state, separate from engine object layouts.
#ifndef BF2_ANDROID_UI_STATE_H
#define BF2_ANDROID_UI_STATE_H
#include <atomic>
#include <cmath>
#include <mutex>
#include <string>

namespace dice { namespace hfe { namespace androidui {
enum Phase { Starting = 0, Loading = 1, Intro = 2, Menu = 3, Game = 4 };
inline int pack(Phase phase, bool canSkip = false, int percent = -1)
{
    if (percent < -1) percent = -1;
    if (percent > 100) percent = 100;
    return static_cast<int>(phase) | (canSkip ? 256 : 0) | ((percent + 1) << 16);
}
struct State {
    std::atomic<int> snapshot{pack(Starting)};
    std::mutex levelMutex;
    std::string level;
};
inline State& state() { static State value; return value; }
inline void publish(Phase phase, bool canSkip = false)
{ state().snapshot.store(pack(phase, canSkip), std::memory_order_release); }
inline int snapshot() { return state().snapshot.load(std::memory_order_acquire); }
inline void beginLoading(const std::string& level)
{
    { std::lock_guard<std::mutex> guard(state().levelMutex); state().level = level; }
    publish(Loading);
}
inline std::string level()
{ std::lock_guard<std::mutex> guard(state().levelMutex); return state().level; }
class StartAttempt {
    bool pendingFrame = false;
public:
    StartAttempt() { beginLoading(""); }
    ~StartAttempt() { if (!pendingFrame) publish(Menu); }
    void awaitFrame() { pendingFrame = true; }
};
inline void progress(float percent)
{
    if ((snapshot() & 255) != Loading) return;
    const int whole = !std::isfinite(percent) || percent < 0 ? -1 :
        (percent > 100 ? 100 : static_cast<int>(percent));
    state().snapshot.store(pack(Loading, false, whole), std::memory_order_release);
}
inline void frame(bool movie, bool skippable, bool menu, bool loaded, bool hasGame)
{
    const Phase next = movie ? Intro : (loaded ? (menu ? Menu : Game) :
        (hasGame && (snapshot() & 255) == Loading ? Loading : Menu));
    if (next != Loading) publish(next, movie && skippable);
}
} } }
#endif
