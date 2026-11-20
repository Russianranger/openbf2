// [CHOSEN, not recovered] Coordinates Android SurfaceView lifetime with synchronous Present.
#ifndef BF2_ANDROID_PRESENTATION_GATE_H
#define BF2_ANDROID_PRESENTATION_GATE_H
#include <atomic>
#include <chrono>
#include <mutex>

namespace bf2android {
class PresentationGate {
    std::atomic<bool> ready_{false};
    std::timed_mutex present_;
public:
    bool ready() const { return ready_.load(std::memory_order_acquire); }
    void resume() { ready_.store(true, std::memory_order_release); }
    bool pause(std::chrono::milliseconds timeout) {
        // Reject new frames before waiting, including while a driver is stalled in Present.
        ready_.store(false, std::memory_order_release);
        if (!present_.try_lock_for(timeout)) return false;
        present_.unlock();
        return true;
    }
    bool begin() {
        if (!ready()) return false;
        present_.lock();
        if (ready()) return true;
        present_.unlock();
        return false;
    }
    void end() { present_.unlock(); }
};
}

// A single gate lives in libmain; renderer plugins must use it rather than a private copy.
extern "C" bool portablePresentationReady();
extern "C" bool portableBeginPresent();
extern "C" void portableEndPresent();
#endif
