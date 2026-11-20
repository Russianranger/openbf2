// [TEMP] [CHOSEN, not recovered] Bounded native HUD test. Real engine constructors,
// component factories and HUD methods run; a typed control/weapon observer supplies
// the selected weapon and its zoom state. No renderer, map or gameplay is simulated.
#include <cstdio>
#include <cstdlib>
#include "Core/EventManager.h"
#include "Core/Ids.h"
#include "Menu/HudAmmo.h"
#include "Menu/HudVehicleInformation.h"
#include "Menu/BF2HudManager.h"
#include "Game/IGameLogic.h"
#include "Game/Objects/IPlayer.h"
#include "Game/Objects/Object.h"
#include "Game/Objects/ObjectLayoutAccess.h"
#include "Game/Objects/GenericFireArmTemplate.h"
#include "Game/Objects/Weapons/GenericFireArm.h"
#include "Game/Objects/VehicleHudCompTemplate.h"
#include "Game/Objects/WeaponHudCompTemplate.h"
#include "Game/Objects/IVehicleHudComp.h"
#include "Game/Objects/IWeaponHudComp.h"
#include "Scene/Object/PlayerManager.h"
#include "Scene/Object/ObjectTypes/Game/IPlayerControlObject.h"

[[noreturn]] static void actionUnsupported(const char* name)
{
    std::fprintf(stderr, "[FRONTIER] unexpected ADS fixture call: %s\n", name);
    std::abort();
}
#include "hud_radio_action_observers.h"
#define fixtureUnsupported actionUnsupported
#include "hud_kit_observers.h"
#undef fixtureUnsupported
using namespace dice::hfe;
using namespace dice::hfe::world;
namespace dice { namespace hfe { extern hud::BF2HudManager* hudManager; } }

struct Control : RadioPcoBoundary {
    IObject* active = nullptr;
    IObject* getActiveWeapon(int index) const override {
        if (index != 0) actionUnsupported("secondary weapon");
        return active;
    }
    IPlayer* getOccupingPlayer() const override { actionUnsupported("occupant"); }
    std::map<int,IPlayerControlObject*>* getPcos() override { actionUnsupported("child controls"); }
    bool isChildSoldierActive() const override { actionUnsupported("child soldier"); }
    int getAmmoStatus() override { actionUnsupported("ammo status"); }
    ICameraObject* getActiveCamera() override { return nullptr; }
};
struct SceneObject : world::Object {
    SceneObject() : world::Object(nullptr) {}
    Control* control = nullptr;
    void* queryInterface(unsigned id) const override {
        if (id == IID_IPlayerControlObject) return control;
        return world::Object::queryInterface(id);
    }
};
struct WeaponTemplate : GenericFireArmTemplate {
    void objectCreated(IObject* object) override {
        // Inactive fixture objects need no world scheduling manager.
        object_detail::LayoutAccess::flags(object) |= 1;
    }
};
struct Weapon : GenericFireArm {
    explicit Weapon(const GenericFireArmTemplate* objectTemplate) : GenericFireArm(objectTemplate) {}
    int zoom = 0;
    int getZoom() override { return zoom; }
};
struct LocalPlayer : PlayerBoundary {
    IObject* soldier = nullptr;
    IObject* getVehicle() const override { return soldier; }
    IObject* getDefaultVehicle() override { return soldier; }
    bool getIsAlive() const override { return true; }
    int getTeam() const override { actionUnsupported("team"); }
    int getSquadId() const override { actionUnsupported("squad"); }
    bool getIsSquadLeader() const override { actionUnsupported("leader"); }
    int getKit() const override { actionUnsupported("kit"); }
    int getChosenUnlockLevel(int,int) const override { actionUnsupported("unlock"); }
    void setChosenUnlockLevel(int,int,int) override { actionUnsupported("set unlock"); }
    bool isUnlocked(int,int,int) override { actionUnsupported("unlocked"); }
    float getCrossHairFadeValue() const override { return .5f; }
};
struct Players : PlayerManager {
    IPlayer* local = nullptr;
    IPlayer* getLocalHumanPlayer() override { return local; }
};
struct Hud : hud::BF2HudManager {
    hud::HudVehicleInformation* info = nullptr;
    Kit* getPlayerKit(IPlayer*) override { return nullptr; }
    hud::HudVehicleInformation* getVehicleInformation() override { return info; }
};
static int checks;
static void check(bool result, const char* label)
{
    ++checks;
    if (!result) { std::fprintf(stderr, "FAIL %s\n", label); std::exit(1); }
}
namespace dice { namespace hfe { namespace hud {
struct HudVehicleInformationFixture {
    static void enable(HudVehicleInformation& info) {
        info.m_field170 = 1;
        info.m_guiIndex = info.m_field0a4 = 4; // previously selected hip-fire GUI
    }
    static void timed(HudVehicleInformation& info, float time, int mode) {
        info.m_field0b4 = time;
        info.m_crossHairMode = mode;
    }
};
struct HudAmmoFixture {
    static void run()
    {
        EventManager events;
        eventManager = &events;
        Players players;
        playerManager = &players;
        HudAmmo ammo(nullptr);
        auto* soldier = new SceneObject;
        auto* kit = new SceneObject;
        auto* vehicle = new SceneObject;
        Control control;
        soldier->control = &control;
        kit->control = &control;
        auto* weaponTemplate = new WeaponTemplate;
        auto* weapon = new Weapon(weaponTemplate);
        // Constructors remain disabled for scheduling; selection reads the live flag.
        object_detail::LayoutAccess::flags(weapon) &= ~1u;
        control.active = weapon;
        auto* kitHudTemplate = new VehicleHudCompTemplate;
        kitHudTemplate->setShowCrossHair(true);
        auto* kitHud = kitHudTemplate->createComponent();
        kitHud->setObject(kit);
        object_detail::LayoutAccess::vehicleHudComponent(kit) = kitHud;
        object_detail::LayoutAccess::vehicleHudComponent(soldier) = kitHud;
        auto* vehicleHudTemplate = new VehicleHudCompTemplate;
        vehicleHudTemplate->setShowCrossHair(true);
        auto* vehicleHud = vehicleHudTemplate->createComponent();
        vehicleHud->setObject(vehicle);
        object_detail::LayoutAccess::vehicleHudComponent(vehicle) = vehicleHud;
        auto* scopeTemplate = new WeaponHudCompTemplate;
        scopeTemplate->setGuiIndex(4);
        scopeTemplate->setAltGuiIndex(53);
        scopeTemplate->setSwitchGuiOnCamera(false);
        auto* scope = static_cast<IWeaponHudComp*>(scopeTemplate->createComponent());
        scope->setObject(weapon);
        object_detail::LayoutAccess::weaponHudComponent(weapon) = scope;
        auto* info = new HudVehicleInformation;
        HudVehicleInformationFixture::enable(*info);
        auto* hud = new Hud;
        hud->info = info;
        hudManager = hud;
        LocalPlayer player;
        player.soldier = soldier;
        players.local = &player;
        ammo.m_field0e9 = true;
        ammo.m_field0fc = .5f;
        ammo.updateInfantryCrosshair(soldier, kit);
        check(ammo.m_field0e8, "native hip-fire crosshair visible");
        check(scope->getGuiIndex(false) == 4, "native base weapon GUI retained");
        check(info->getGuiIndex() == 4, "native HUD owner publishes authored hip-fire GUI");
        weapon->zoom = 1;
        ammo.updateInfantryCrosshair(soldier, kit);
        check(!ammo.m_field0e8, "native primary ADS crosshair hidden");
        check(ammo.m_field0eb && ammo.m_field0fc == .5f, "native hit feedback unchanged in ADS");
        check(scope->getGuiIndex(false) == 53, "native alternate optic GUI retained in ADS");
        check(info->getGuiIndex() == 53, "native HUD owner refreshes stale GUI to authored optic");
        weapon->zoom = 2;
        ammo.updateInfantryCrosshair(soldier, kit);
        check(!ammo.m_field0e8 && scope->getGuiIndex(false) == 53, "native second zoom preserves optic GUI");
        weapon->zoom = 0;
        ammo.m_field0ea = true;
        ammo.updateInfantryCrosshair(soldier, kit);
        check(ammo.m_field0e8, "native zoom-out restores crosshair despite cached ammo zoom");
        check(scope->getGuiIndex(false) == 4, "native zoom-out restores base weapon GUI");
        check(info->getGuiIndex() == 4, "native HUD owner restores hip-fire GUI on zoom-out");
        HudVehicleInformationFixture::timed(*info, 1, 0);
        weapon->zoom = 1;
        ammo.updateInfantryCrosshair(soldier, kit);
        check(info->getGuiIndex() == 4, "native timed override is retained");
        HudVehicleInformationFixture::timed(*info, 0, 1);
        ammo.updateInfantryCrosshair(soldier, kit);
        check(info->getGuiIndex() == 4, "native weapon feedback is retained");
        HudVehicleInformationFixture::timed(*info, 0, 0);
        ammo.updateInfantryCrosshair(soldier, kit);
        check(info->getGuiIndex() == 53, "native authored GUI refresh resumes after feedback");
        weapon->zoom = 1;
        ammo.updateInfantryCrosshair(soldier, kit);
        control.active = nullptr;
        ammo.updateInfantryCrosshair(soldier, kit);
        check(ammo.m_field0e8, "native missing weapon does not retain previous zoom");
        kitHudTemplate->setShowCrossHair(false);
        ammo.updateInfantryCrosshair(soldier, kit);
        check(!ammo.m_field0e8, "native authored hidden kit crosshair retained");
        ammo.updateCrosshair(vehicle);
        check(ammo.m_field0e8, "native vehicle crosshair unaffected by weapon zoom");
        check(vehicleHudTemplate->getShowCrossHair(), "native vehicle configuration unchanged");
        control.active = weapon;
        scopeTemplate->setGuiIndex(5);
        scopeTemplate->setAltGuiIndex(52);
        weapon->zoom = 0;
        ammo.updateInfantryCrosshair(soldier, kit);
        check(info->getGuiIndex() == 5, "native generic rifle selects stock hip-fire GUI");
        weapon->zoom = 1;
        ammo.updateInfantryCrosshair(soldier, kit);
        check(info->getGuiIndex() == 52 && ammo.m_field0eb,
              "native generic rifle selects stock ADS GUI and preserves hit feedback");
        delete info;
        hud->info = nullptr;
        // World objects are retained: this fixture does not start or tear down a map.
        // HudAmmo and PlayerManager do run their real bounded destructors.
    }
};
} } }
int main()
{
    dice::hfe::hud::HudAmmoFixture::run();
    std::printf("Native ADS HUD and optic selection: %d checks passed\n", checks);
}
