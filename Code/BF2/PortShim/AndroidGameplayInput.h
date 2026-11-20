#pragma once
#if defined(__ANDROID__) && !DEDICATED
namespace dice { namespace hfe { namespace io { class PlayerInput; class GameInput; } } }
namespace androidcontrols {
void updateContext();
void apply(dice::hfe::io::PlayerInput&, dice::hfe::io::GameInput&);
}
#endif
