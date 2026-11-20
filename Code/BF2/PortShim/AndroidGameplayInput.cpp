// [CHOSEN, not recovered] Controller/touch actions enter existing BF2 input filtering,
// prediction and networking. JNI threads never inspect or mutate game objects.
#if defined(__ANDROID__) && !DEDICATED
#include "AndroidGameplayInput.h"
#include "AndroidActionState.h"
#include "../IO/Input/PlayerInput.h"
#include "../IO/Input/GameInput.h"
#include "../Scene/Object/PlayerManager.h"
#include "../Game/Objects/IPlayer.h"
#include "../Game/Objects/IObject.h"
#include "../Game/Objects/ISoldier.h"
#include "../Game/Main/BF2Engine.h"
#include "../Game/IGame.h"
#include "../Core/Ids.h"
#include "../Menu/BF2HudManager.h"
#include "../Game/Main/BF2ClientSeams.h"
#include "../IO/Console/Console.h"
#include <jni.h>

extern "C" bool portablePointerMode();
namespace androidcontrols {
static ActionState state;
void updateContext() {
    using namespace dice::hfe;
    auto* player = game && world::playerManager ? world::playerManager->getLocalHumanPlayer() : nullptr;
    auto* object = player ? player->getDefaultVehicle() : nullptr;
    auto* soldier = object ? static_cast<world::ISoldier*>(object->queryInterface(world::IID_ISoldier)) : nullptr;
    const bool alive = player && player->getIsAlive() && !player->getIsManDown();
    const bool infantry = alive && soldier && player->getVehicle() == object;
    int pose = -1;
    if (infantry) pose = soldier->getIsNewPoseRequested() ? soldier->getRequestedPose() : soldier->getPose();
    const bool text = (io::mainConsole && io::mainConsole->getConsoleActive())
        || (hudManager && (hudManager->isChatActive() || hudManager->isSquadCreateActive() || hudManager->isSquadRenameActive()));
    state.context(reinterpret_cast<std::uintptr_t>(object), alive ? (infantry ? 1 : 2) : 0,
                  pose, portablePointerMode(), text, !menuLogic && portablePointerMode());
}
// Separate sampling from mapping so the named-action contract can be checked without a live scene.
void applySample(const Sample& s, dice::hfe::io::PlayerInput& player, dice::hfe::io::GameInput& gameInput) {
    using namespace dice::hfe::io;
    auto down = [&](int action) { return ((s.held | s.pressed) & (1ull << action)) != 0; };
    auto edge = [&](int action) { return (s.pressed & (1ull << action)) != 0; };
    auto p = [&](PlayerInputMap action, bool enabled) {
        if (enabled) { player.setInputIsMapped(action, true); player.setInput(action, 1.0f); }
    };
    auto g = [&](GameInputMap action, bool enabled) {
        if (enabled) { gameInput.setInputIsMapped(action, true); gameInput.setInput(action, 1.0f); }
    };
    p(PIFire, down(Fire)); p(PIAltFire, down(Aim)); p(PIAction, down(Jump));
    p(PISprint, down(Sprint)); p(PIReload, edge(Reload)); p(PIUse, down(Use));
    for (int n = 0; n != 6; ++n) p(static_cast<PlayerInputMap>(PIWeaponSelect1 + n), edge(Weapon1 + n));
    p(PINextItem, edge(NextWeapon)); p(PIPrevItem, edge(PreviousWeapon));
    p(PIShowScoreBoard, down(Scoreboard)); g(GIShowScoreboard, down(Scoreboard));
    g(GIMapSize, edge(Map)); g(GILeader, edge(Squad));
    g(GIRadioComm, down(CommRose)); g(GITacticalComm, down(TacticalRose));
    for (int n = 0; n != 3; ++n) p(static_cast<PlayerInputMap>(PIWeaponSelect7 + n), edge(Weapon7 + n));
    for (int n = 0; n != 8; ++n) p(static_cast<PlayerInputMap>(PIPositionSelect1 + n), edge(Seat1 + n));
    p(PIDrop, edge(DropKit)); p(PIToggleFireRate, edge(FireMode));
    g(GIMapZoom, edge(MapZoom)); g(GI3dMap, edge(Map3d));
    g(GISayAll, edge(ChatAll)); g(GISayTeam, edge(ChatTeam)); g(GISaySquad, edge(ChatSquad));
    if (s.stand) { player.setInputIsMapped(PICrouch, true); player.setInput(PICrouch, 0.0f); }
    p(PICrouch, s.crouch); p(PILie, s.prone);
}
void apply(dice::hfe::io::PlayerInput& player, dice::hfe::io::GameInput& gameInput) {
    updateContext();
    applySample(state.sample(), player, gameInput);
}
}
extern "C" JNIEXPORT void JNICALL Java_com_mackillergr_bf2_GameplayActions_nativeAction(JNIEnv*, jclass, jint action, jboolean down) {
    androidcontrols::state.input(action, down);
}
extern "C" JNIEXPORT void JNICALL Java_com_mackillergr_bf2_GameplayActions_nativeReset(JNIEnv*, jclass) {
    androidcontrols::state.reset();
}
extern "C" JNIEXPORT jint JNICALL Java_com_mackillergr_bf2_GameplayActions_nativeState(JNIEnv*, jclass) {
    return androidcontrols::state.snapshot();
}
#endif
