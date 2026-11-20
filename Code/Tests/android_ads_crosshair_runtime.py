#!/usr/bin/env python3
"""Run the production crosshair method bodies with bounded world observers.

This host check exercises HUD visibility, not the renderer or Android ABI. Compile
HudAmmo.cpp with the NDK separately to validate the real interface declarations.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile


def stock_layout(archive):
    """Verify the real shipped node gates without redistributing game assets."""
    with ZipFile(archive) as zip_file:
        nodes = {}
        for filename in ("HudElementsGenericWeapon.con", "HudElementsM4.con"):
            text = zip_file.read("HUD/HudSetup/Weapons/" + filename).decode("latin1")
            current = None
            for line in text.splitlines():
                parts = line.split()
                if not parts:
                    continue
                if parts[0] in ("hudBuilder.createSplitNode", "hudBuilder.createPictureNode"):
                    current = {"parent": parts[1], "gates": []}
                    nodes[parts[2]] = current
                elif current is not None and parts[0] == "hudBuilder.setNodeLogicShowVariable":
                    assert parts[2] == "GuiIndex"
                    current["gates"].append((parts[1], int(parts[3])))
                elif current is not None and parts[0] == "hudBuilder.setPictureNodeTexture":
                    current["texture"] = parts[1]

    def visible(name, gui):
        if name not in nodes:
            return True
        node = nodes[name]
        shown = True
        for operation, expected in node["gates"]:
            if operation == "EQUAL":
                shown = gui == expected
            elif operation == "OR":
                shown = shown or gui == expected
            else:
                raise AssertionError(operation)
        return shown and visible(node["parent"], gui)

    assert visible("GenericCrosshairUp", 5) and not visible("GenericCrosshairUp", 52)
    assert visible("GenericHitIndication", 5) and visible("GenericHitIndication", 52)
    assert visible("M4CrosshairUp", 4) and not visible("M4CrosshairUp", 53)
    assert visible("M4AltCrosshair", 53) and not visible("M4AltCrosshair", 4)
    assert visible("M4HitIndication", 4) and visible("M4HitIndication", 53)
    assert nodes["M4AltCrosshair"]["texture"].endswith("/AimPoint.tga")
    print("Stock HUD scripts: rifle arms hidden in ADS; M4 optic dot and hit feedback retained")


def method(source, signature):
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


FIXTURE = r'''
// [TEMP] [CHOSEN, not recovered] Narrow world observers. These are not ABI
// substitutes or production implementations. The three HUD methods below are
// extracted unchanged from HudAmmo.cpp; no alternate visibility policy is used.
#include <cstdio>
#include <cstdlib>
namespace world {
enum { IID_IWeaponObject = 1, IID_IPlayerControlObject = 2 };
struct IObject;
struct IWeaponObject { virtual int getZoom() = 0; };
struct IWeaponHudComp { virtual int getGuiIndex(bool) = 0; };
struct IPlayerControlObject { virtual IObject* getActiveWeapon(int) const = 0; };
struct VehicleHudInfo { bool showCrossHair = true; };
struct IVehicleHudComp {
    VehicleHudInfo info;
    VehicleHudInfo* getHudInfo() { return &info; }
};
struct IObject {
    enum { OFIsDisabled = 1 };
    unsigned flags = 0;
    IVehicleHudComp* m_vehicleHudComponent = nullptr;
    IWeaponHudComp* m_weaponHudComponent = nullptr;
    IPlayerControlObject* control = nullptr;
    IWeaponObject* weapon = nullptr;
    IObject* m_parent = nullptr;
    virtual void* queryInterface(int id) {
        return id == IID_IWeaponObject ? static_cast<void*>(weapon)
                                      : static_cast<void*>(control);
    }
    unsigned getFlags() { return flags; }
};
struct IPlayer {
    float fade = .75f;
    float getCrossHairFadeValue() { return fade; }
};
struct PlayerManager {
    IPlayer* local = nullptr;
    IPlayer* getLocalHumanPlayer() { return local; }
};
PlayerManager* playerManager;
}
struct HudVehicleInformation {
    int m_crossHairMode = 0, gui = 4, desired = 4, updates = 0;
    float m_field0b4 = 0;
    bool m_field244 = false, m_field2ac = true;
    int getGuiIndex() { return gui; }
    void updateGuiIndex() { gui = desired; ++updates; }
};
struct HudManager {
    HudVehicleInformation info;
    HudVehicleInformation* getVehicleInformation() { return &info; }
};
HudManager* hudManager = nullptr;
struct HudAmmo {
    bool m_field0e8 = false, m_field0e9 = true, m_field0ea = false, m_field0eb = false;
    float m_field0fc = 0;
    void updateCrosshair(world::IObject*);
    void updateInfantryCrosshair(world::IObject*, world::IObject*);
    static world::IPlayerControlObject* findControlParent(world::IObject*);
};
@METHODS@
struct Weapon : world::IWeaponObject {
    int zoom = 0, reads = 0;
    int getZoom() override { ++reads; return zoom; }
};
struct WeaponHud : world::IWeaponHudComp {
    Weapon* weapon = nullptr;
    bool selected = false;
    int getGuiIndex(bool value) override {
        selected = value;
        return weapon->zoom ? 53 : 4;
    }
};
struct Control : world::IPlayerControlObject {
    world::IObject* active = nullptr;
    mutable int reads = 0;
    world::IObject* getActiveWeapon(int channel) const override {
        if (channel != 0) std::abort();
        ++reads;
        return active;
    }
};
int checks = 0;
void check(bool ok, const char* text) {
    ++checks;
    if (!ok) { std::fprintf(stderr, "FAIL: %s\n", text); std::exit(1); }
}
int main() {
    world::PlayerManager players;
    world::IPlayer player;
    players.local = &player;
    world::playerManager = &players;
    world::IVehicleHudComp kitHud, vehicleHud;
    world::IObject kit, soldier, weaponObject, otherWeaponObject, vehicle;
    Weapon weapon, otherWeapon;
    Control control;
    kit.m_vehicleHudComponent = &kitHud;
    vehicle.m_vehicleHudComponent = &vehicleHud;
    soldier.control = &control;
    weaponObject.weapon = &weapon;
    otherWeaponObject.weapon = &otherWeapon;
    control.active = &weaponObject;
    HudAmmo ammo;
    auto update = [&] { ammo.updateInfantryCrosshair(&soldier, &kit); };
    update();
    check(ammo.m_field0e8, "hip-fire retains the authored crosshair");
    check(ammo.m_field0eb && ammo.m_field0fc == .75f, "normal hit feedback is published");
    weapon.zoom = 1;
    update();
#if defined(__ANDROID__)
    check(!ammo.m_field0e8, "real primary weapon ADS hides only generic crosshair");
    check(ammo.m_field0eb && ammo.m_field0fc == .75f, "ADS preserves hit feedback");
    check(ammo.m_field0e9, "ADS does not change global crosshair activation");
    check(weapon.reads == 2 && control.reads == 2, "current primary weapon is sampled on each frame");
    update();
    check(!ammo.m_field0e8, "latched zoom remains hidden without an aim-button event");
    weapon.zoom = 0;
    update();
    check(ammo.m_field0e8, "zoom-out restores the current kit crosshair");
    ammo.m_field0ea = true;
    update();
    check(ammo.m_field0e8, "stale ammo zoom cannot hide an unzoomed weapon");
    ammo.m_field0ea = false;
    weapon.zoom = 2;
    update();
    check(!ammo.m_field0e8, "all active zoom stages hide crosshair despite stale ammo state");
    control.active = &otherWeaponObject;
    update();
    check(ammo.m_field0e8, "switching to an unzoomed weapon restores crosshair");
    control.active = nullptr;
    update();
    check(ammo.m_field0e8, "missing active weapon never reuses the previous zoom");
    control.active = &vehicle;
    update();
    check(ammo.m_field0e8, "non-weapon active object keeps normal kit policy");
    soldier.control = nullptr;
    update();
    check(ammo.m_field0e8, "missing control keeps normal kit policy");
    world::IObject parent;
    parent.control = &control;
    soldier.m_parent = &parent;
    control.active = &weaponObject;
    update();
    check(!ammo.m_field0e8, "parent control lookup uses the current primary weapon");
    soldier.m_parent = nullptr;
    soldier.control = &control;
    ammo.m_field0e9 = false;
    const int priorReads = weapon.reads;
    update();
    check(!ammo.m_field0e8 && weapon.reads == priorReads, "hidden HUD is never re-enabled by ADS policy");
    ammo.m_field0e9 = true;
    kitHud.info.showCrossHair = false;
    weapon.zoom = 0;
    update();
    check(!ammo.m_field0e8 && weapon.reads == priorReads, "authored hidden crosshair remains hidden");
    kitHud.info.showCrossHair = true;
    weapon.zoom = 1;
    player.fade = 0;
    update();
    check(!ammo.m_field0eb, "ADS clears expired hit feedback through the original update");
    player.fade = .25f;
    update();
    check(ammo.m_field0eb && ammo.m_field0fc == .25f, "new hit feedback remains visible while aiming");
    ammo.updateCrosshair(&vehicle);
    check(ammo.m_field0e8, "vehicle HUD crosshair is unaffected by infantry zoom");
    vehicleHud.info.showCrossHair = false;
    ammo.updateCrosshair(&vehicle);
    check(!ammo.m_field0e8, "vehicle authored hidden crosshair remains hidden");
    players.local = nullptr;
    weapon.zoom = 0;
    update();
    check(ammo.m_field0e8 && ammo.m_field0fc == .25f, "absent local player preserves original crosshair fade behavior");
    HudManager hud;
    hudManager = &hud;
    WeaponHud weaponHud;
    weaponHud.weapon = &weapon;
    weaponObject.m_weaponHudComponent = &weaponHud;
    control.active = &weaponObject;
    weapon.zoom = 1;
    hud.info.desired = 53;
    update();
    check(hud.info.gui == 53 && hud.info.updates == 1, "ADS refreshes stale authored optic GUI through its owner");
    update();
    check(hud.info.updates == 1, "matching GUI does not request redundant refresh");
    weapon.zoom = 0;
    hud.info.desired = 4;
    update();
    check(hud.info.gui == 4 && hud.info.updates == 2, "zoom-out refreshes the authored hip-fire GUI");
    weapon.zoom = 1;
    hud.info.desired = 53;
    hud.info.m_field0b4 = 1;
    update();
    check(hud.info.gui == 4 && hud.info.updates == 2, "timed HUD overrides are not cancelled");
    hud.info.m_field0b4 = 0;
    for (int feedback = 1; feedback <= 2; ++feedback) {
        hud.info.m_crossHairMode = feedback;
        update();
        check(hud.info.updates == 2, "weapon feedback animation is not cancelled");
    }
    hud.info.m_crossHairMode = 0;
    kitHud.info.showCrossHair = false;
    update();
    check(hud.info.gui == 53 && hud.info.updates == 3, "stock GUI refresh does not depend on unused icon visibility binding");
    weapon.zoom = 0;
    hud.info.desired = 4;
    hud.info.m_field244 = true;
    update();
    check(weaponHud.selected, "GUI query preserves sprint/selection state");
    weapon.zoom = 1;
    hud.info.desired = 53;
    weaponObject.flags = world::IObject::OFIsDisabled;
    const int beforeDisable = hud.info.updates;
    update();
    check(hud.info.gui == 4 && hud.info.updates == beforeDisable,
          "disabled weapons never refresh the active GUI");
#else
    check(ammo.m_field0e8 && weapon.reads == 0, "non-Android behavior remains unchanged");
#endif
    std::printf("ADS crosshair: %d checks passed\n", checks);
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--menu-archive", type=Path, help="optional stock BF2 menu_server.zip")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    source = (root / "Code/BF2/Menu/HudAmmo.cpp").read_text()
    bodies = "\n\n".join(method(source, signature) for signature in (
        "void HudAmmo::updateCrosshair(",
        "void HudAmmo::updateInfantryCrosshair(",
        "world::IPlayerControlObject* HudAmmo::findControlParent("))
    frame = method(source, "void HudAmmo::update(float delta)")
    assert "if (soldier) updateInfantryCrosshair(defaultVehicle, soldier->getKit());" in frame
    assert "updateCrosshair(local->getVehicle());" in frame
    assert frame.count("updateInfantryCrosshair(") == 1
    output = args.out or Path(tempfile.mkdtemp(prefix="bf2-ads-test-"))
    output.mkdir(parents=True, exist_ok=True)
    unit = output / "hud_ads_under_test.cpp"
    unit.write_text(FIXTURE.replace("@METHODS@", bodies))
    for name, defines in (("android", ["-D__ANDROID__"]), ("other-client", [])):
        binary = output / name
        subprocess.run([os.environ.get("CXX", "c++"), "-std=c++14", "-Wall", "-Wextra",
                        *defines, str(unit), "-o", str(binary)], check=True)
        subprocess.run([str(binary)], check=True)
    print("Integration: infantry helper only; vehicle path unchanged")
    if args.menu_archive:
        stock_layout(args.menu_archive)


if __name__ == "__main__":
    main()
