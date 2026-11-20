// Production pause/Present gate tests. No GPU or Android window is simulated here.
#include "../BF2/PortShim/AndroidPresentationGate.h"
#include <cassert>
#include <future>
#include <iostream>
#include <thread>
using namespace std::chrono;
int main() {
    bf2android::PresentationGate gate;
    assert(!gate.ready() && !gate.begin());
    assert(gate.pause(milliseconds(10)));
    gate.resume();
    assert(gate.ready() && gate.begin());
    auto pause = std::async(std::launch::async, [&] { return gate.pause(milliseconds(1000)); });
    const auto deadline = steady_clock::now() + seconds(2);
    while (gate.ready() && steady_clock::now() < deadline) std::this_thread::yield();
    assert(!gate.ready());
    assert(!gate.begin()); // Pause rejects later Present before the earlier call has drained.
    assert(pause.wait_for(milliseconds(10)) == std::future_status::timeout);
    gate.end();
    assert(pause.get());
    assert(!gate.ready() && !gate.begin());

    gate.resume();
    assert(gate.begin());
    auto stalled = std::async(std::launch::async, [&] {
        const auto start = steady_clock::now();
        assert(!gate.pause(milliseconds(30)));
        return steady_clock::now() - start;
    });
    assert(stalled.wait_for(seconds(1)) == std::future_status::ready);
    assert(stalled.get() < seconds(1)); // A stuck driver cannot indefinitely block the UI callback.
    assert(!gate.ready() && !gate.begin());
    gate.end();
    assert(gate.pause(milliseconds(10)));

    for (int i=0; i<500; ++i) {
        gate.resume();
        assert(gate.begin());
        gate.end();
        assert(gate.pause(milliseconds(10)));
        assert(!gate.begin());
    }
    std::cout << "Android presentation gate: pause/drain, bounded driver stall, and 500 resume cycles passed\n";
}
