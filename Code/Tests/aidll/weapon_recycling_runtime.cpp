// Exercise real weapon records and plan contexts with an engine weapon observer.
#include "AI/AIDLL/AI/AIKnowledge/WeaponInfoReal.h"
#include "AI/AIDLL/AI/AIUtils/WeaponInfoManager.h"
#include "AI/AIDLL/AI/AIBotActionPlan/BAPVariable.h"
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <memory>

namespace dice { namespace hfe { namespace world {
// Engine identifier needed by the real component base; the observer overrides the lookup.
extern const unsigned int IID_IObjectComponent = 50043;
} } }

[[noreturn]] static void unexpected(const char* name)
{
    std::fprintf(stderr, "Unexpected engine call: %s\n", name);
    std::abort();
}
#include "weapon_observer.inl"
using namespace dice::hfe::ai;
int WeaponObserver::getWeaponType() const { return 17; }

static unsigned int destroyed;
class TrackedInfo : public WeaponInfoReal
{
public:
    explicit TrackedInfo(IWeapon* weapon) : WeaponInfoReal(weapon) {}
    ~TrackedInfo() override { ++destroyed; }
};

int main()
{
    WeaponInfoManager manager;
    WeaponObserver* weapon = new WeaponObserver;
    TrackedInfo* info = new TrackedInfo(weapon);
    const unsigned int handle = manager.registerInformation(info);
    assert(manager.getInformation(handle)->getWeaponType() == 17);
    BAPContext context;
    context.addVariable(new BAPVariable<WeaponInfo*>(nullptr, info));
    info->retainPlanReference(); // independent BAPAChangeWeapon owner
    assert(context.isValid() && info->planReferences() == 2);
    assert(manager.releaseWeapon(weapon) == 1);
    delete weapon;
    assert(!manager.getInformation(handle) && !context.isValid());
    assert(info->isRetired() && destroyed == 0); // same-tick local borrower remains alive
    for (int tick = 0; tick != 8; ++tick) manager.collectRetired();
    assert(destroyed == 0 && manager.retiredCount() == 1); // long-lived plan, not N-frame grace
    context.clear();
    manager.collectRetired();
    assert(destroyed == 0); // separate action still owns the record
    info->releasePlanReference();
    assert(destroyed == 0); // final release during a tick still defers destruction
    manager.collectRetired();
    assert(destroyed == 1 && manager.retiredCount() == 0);

    WeaponObserver replacement;
    for (unsigned int cycle = 0; cycle != 2000; ++cycle)
    {
        TrackedInfo* fresh = new TrackedInfo(&replacement);
        const unsigned int current = manager.registerInformation(fresh);
        assert(current && current != handle && !manager.getInformation(handle));
        assert(fresh->getWeaponType() == 17);
        {
            BAPContext owner;
            owner.addVariable(new BAPVariable<WeaponInfo*>(nullptr, fresh));
            manager.freeInformation(current);
            manager.freeInformation(current); // repeated stale release is harmless
            assert(!owner.isValid() && manager.retiredCount() == 1);
        } // context destruction releases its pin even without explicit clear
        manager.collectRetired();
        assert(manager.retiredCount() == 0);
    }
    assert(destroyed == 2001);

    TrackedInfo* replacementInfo = new TrackedInfo(&replacement);
    const unsigned int current = manager.registerInformation(replacementInfo);
    manager.setInformation(current, new TrackedInfo(&replacement));
    assert(replacementInfo->isRetired());
    manager.collectRetired();
    manager.reset();
    assert(!manager.getInformation(current));
    manager.collectRetired();
    assert(manager.retiredCount() == 0);
    manager.setInformation(current, new TrackedInfo(&replacement)); // rejected stale overwrite
    manager.collectRetired();

    // A context surviving manager teardown keeps an invalid record, then releases it safely.
    BAPContext lateContext;
    const unsigned int before = destroyed;
    {
        WeaponInfoManager temporary;
        TrackedInfo* last = new TrackedInfo(&replacement);
        temporary.registerInformation(last);
        lateContext.addVariable(new BAPVariable<WeaponInfo*>(nullptr, last));
    }
    assert(!lateContext.isValid() && destroyed == before);
    lateContext.clear();
    assert(destroyed == before + 1);
    std::puts("PASS: stale handles, multi-tick owners, deferred collection, 2000 recycles, reset and teardown");
    return 0;
}
