#pragma once
// [CHOSEN, not recovered] Android named controls feed the normal client input samples.
#include <atomic>
#include <cstdint>

namespace androidcontrols {
// Keep numeric IDs in sync with ControllerBindings.Action; persisted bindings use names.
enum Action { None, Fire, Aim, Jump, CrouchToggle, Prone, Sprint, Reload, Use,
    Weapon1, Weapon2, Weapon3, Weapon4, Weapon5, Weapon6, NextWeapon, PreviousWeapon,
    Scoreboard, Menu, Map, Squad, CommRose, TacticalRose, Weapon7, Weapon8, Weapon9,
    Seat1, Seat2, Seat3, Seat4, Seat5, Seat6, Seat7, Seat8,
    DropKit, FireMode, MapZoom, Map3d, ChatAll, ChatTeam, ChatSquad, ActionCount };
struct Sample { std::uint64_t held, pressed; bool crouch, stand, prone; };

class ActionState {
    std::atomic<std::uint64_t> held_{0}, pressed_{0};
    std::atomic<std::uint32_t> snapshot_{0};
    std::atomic<bool> reset_{false};
    std::uintptr_t owner_ = 0;
    unsigned epoch_ = 0;
    int kind_ = 0, pose_ = -1;
    bool menu_ = false, crouch_ = false, allowRose_ = false, text_ = false;
    static constexpr std::uint64_t roses = (1ull << CommRose) | (1ull << TacticalRose);
public:
    void input(int action, bool down) {
        if (action <= None || action >= ActionCount) return;
        const auto bit = std::uint64_t(1) << action;
        if (down) {
            if (!(held_.fetch_or(bit) & bit)) pressed_.fetch_or(bit);
        } else held_.fetch_and(~bit);
    }
    void reset() { held_.store(0); pressed_.store(0); reset_.store(true); }
    unsigned snapshot() const { return snapshot_.load(); }
    // kind: 0 dead/unavailable, 1 living infantry, 2 living vehicle occupant.
    void context(std::uintptr_t owner, int kind, int pose, bool menu, bool text = false, bool allowRose = false) {
        menu = menu || text;
        if (owner != owner_ || kind != kind_ || menu != menu_ || text != text_) {
            const bool roseOpened = menu && !text && allowRose && (held_.load() & roses)
                && owner == owner_ && kind == kind_;
            owner_ = owner; kind_ = kind; menu_ = menu; text_ = text;
            if (roseOpened) { held_.fetch_and(roses); pressed_.fetch_and(roses); crouch_ = false; }
            else { ++epoch_; reset(); }
        }
        if (reset_.exchange(false)) crouch_ = false;
        pose_ = pose;
        allowRose_ = allowRose && !text;
        if (kind_ != 1 || menu_ || pose < 0 || pose > 2) crouch_ = false;
        snapshot_.store((epoch_ << 8) | (text ? 128u : 0u) | (unsigned(kind_) << 4) | unsigned(pose + 1));
    }
    Sample sample() {
        Sample s{held_.load(), pressed_.exchange(0), false, false, false};
        if (kind_ == 0 || menu_) {
            const auto keep = kind_ != 0 && allowRose_ ? roses : 0ull;
            held_.fetch_and(keep);s.held &= keep;s.pressed &= keep;return s;
        }
        if (kind_ == 1 && pose_ >= 0 && pose_ <= 2) {
            if (s.pressed & (1u << CrouchToggle)) {
                s.stand = crouch_ || pose_ == 1 || pose_ == 2;
                crouch_ = !s.stand;
                s.prone = s.stand && pose_ == 2; // PILie toggles prone back to standing.
            }
            if (s.pressed & (1u << Prone)) {
                crouch_ = false; s.stand = true;
                s.prone = pose_ != 2; // A long press while already prone never stands up.
            }
            s.crouch = crouch_;
        }
        return s;
    }
};
}
