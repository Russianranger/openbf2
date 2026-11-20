// [TEMP] Actual armament/factory/pool code with typed engine collaborators.
#include "AI/AIDLL/AI/AIKnowledge/IPIArmamentReal.h"
#include "AI/AIDLL/AI/AIKnowledge/IPIArmamentKnown.h"
#include "AI/AIDLL/AI/AIKnowledge/InformationReal.h"
#include "AI/AIDLL/AI/AIKnowledge/InformationFactory.h"
#include "AI/AIDLL/AI/AIKnowledge/WeaponInfo.h"
#include "AI/AIDLL/AI/AIUtils/WeaponInfoManager.h"
#include "AI/AIDLL/AI/Main/CommonSingletonServer.h"
#include "AI/AIInterface/BFKnowledge/IAIObject.h"
#include "AI/AIInterface/BFKnowledge/IAIObjectPlugIn.h"
#include <cassert>
#include <climits>
#include <cstdio>
#include <cstdlib>
[[noreturn]] static void unexpected(const char* name)
{ std::fprintf(stderr, "Unexpected engine call: %s\n", name); std::abort(); }
#include "armament_observers.inl"
using namespace dice::hfe::ai;
namespace dice { namespace hfe { namespace world {
extern const unsigned int IID_IObjectComponent = 50043;
} } }
int WeaponObserver::getWeaponType() const { return 17; }
int WeaponObserver::getWeaponActivate() const { return 17; }
int WeaponObserver::getWeaponFire() const { return 19; }
IAIObjectArmament* EngineObject::getArmament() const { return armament; }
void EngineObject::update() { ++updates; armament->update(); }
void EngineArmament::update() { if (view) view->updateWeapons(); }
const std::vector<IWeapon*>& EngineArmament::getWeapons() const { return weapons; }
IWeapon* EngineArmament::getLastFiredWeapon() const { return last; }
int EngineArmament::getWeaponIdFromActivationId(int, int) const { return -1; }

int main()
{
    WeaponInfoManager manager;
    // Suppress unrelated singleton registration; the production factory handles every request.
    CommonSingletonServer::m_iInformationFactory = reinterpret_cast<void*>(1);
    InformationFactory factory;
    CommonSingletonServer::m_iInformationFactory = static_cast<IInformationFactory*>(&factory);
    EngineObject object;
    EngineArmament engine;
    object.armament = &engine;
    InformationReal information(&object, 0, 0, 0, 0);
    IPIArmamentReal real(&information);
    engine.view = &real;
    for (int index : {-1, INT_MIN, 0, 1, INT_MAX})
        assert(real.getWeapon(index) == 0xFFFFFFFFu);
    {
        IPIArmamentKnown known(&information);
        for (int index : {-1, INT_MIN, 0, 1, INT_MAX})
            assert(known.getWeapon(index) == 0xFFFFFFFFu);
    }
    WeaponObserver oldWeapon, newWeapon;
    engine.weapons.push_back(&oldWeapon);
    real.updateWeapons();
    const unsigned int oldHandle = real.getWeapon(0);
    assert(manager.getInformation(oldHandle)->getWeapon() == &oldWeapon);
    assert(real.getWeapon(-1) == 0xFFFFFFFFu && real.getWeapon(1) == 0xFFFFFFFFu);
    assert(manager.releaseWeapon(&oldWeapon) == 1);
    engine.weapons[0] = &newWeapon;
    const unsigned int newHandle = real.getWeapon(0);
    assert(object.updates == 1 && newHandle != oldHandle);
    assert(!manager.getInformation(oldHandle));
    assert(manager.getInformation(newHandle)->getWeapon() == &newWeapon);
    engine.last = &newWeapon;
    assert(real.getLastFiredWeapon() == nullptr); // engine activation mapping returned -1
    manager.releaseWeapon(&newWeapon);
    engine.weapons.clear();
    assert(!manager.getInformation(real.getWeapon(0))); // recycled slot disappeared
    manager.collectRetired();
    assert(manager.retiredCount() == 0);
    CommonSingletonServer::m_iInformationFactory = nullptr;
    std::puts("PASS: real/known invalid indices, stale slot refresh, removed slot, invalid last-fired mapping");
}
