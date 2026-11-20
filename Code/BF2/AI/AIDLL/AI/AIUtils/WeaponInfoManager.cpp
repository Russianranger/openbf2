// Source placement: AIDLL_r.dll __FILE__ "Code\BF2\AI\AIDLL\AI\AIUtils\WeaponInfoManager.cpp" (0x1007D910).

#include "WeaponInfoManager.h"

#include "checkPointer.h"
#include "../AIKnowledge/WeaponInfo.h"

namespace dice {
namespace hfe {
namespace ai {

WeaponInfoManager* WeaponInfoManager::m_instance = 0;

// Creates the weapon-information handle pool and checks allocation before use.
WeaponInfoManager::WeaponInfoManager()
{
    m_pool = new Pool();
    checkPointer(m_pool);

    if (!m_instance)
        m_instance = this;
}

// AI teardown normally destroys bots first; any remaining plan pin owns its retired record.
WeaponInfoManager::~WeaponInfoManager()
{
    m_pool->releaseIf([this](WeaponInfo* info) { retire(info); return true; });
    collectRetired();
    for (WeaponInfo* info : m_retired)
        info->releaseManagerOwnership();
    if (m_pool)
        operator delete(m_pool);

    if (m_instance == this)
        m_instance = 0;
}

// Returns 0 when the pool is full - the one failure this class reports.
WeaponInfoManager::Handle WeaponInfoManager::registerInformation(WeaponInfo* info)
{
    if (!info || info->isRetired())
        return 0;
    const Handle handle = m_pool->acquire(info);
    if (!handle)
        retire(info);
    return handle;
}

// Null for a stale or zero handle; there is no separate validity query.
WeaponInfo* WeaponInfoManager::getInformation(Handle handle) const
{
    return m_pool->get(handle);
}

// 0x36D3A0, 56 B. Return type [UNRESOLVED]; `void` because every path leaves a different leftover
// in eax rather than one materialised value. See Core/SpHandlePool.h::set.
void WeaponInfoManager::setInformation(Handle handle, WeaponInfo* info)
{
    WeaponInfo* const previous = m_pool->get(handle);
    if (!previous || !m_pool->trySet(handle, info))
    {
        retire(info);
        return;
    }
    if (previous != info)
        retire(previous);
}

// Bumps the generation, so every outstanding handle to this slot goes stale.
// Return type [UNRESOLVED]; `void` for the same reason as setInformation.
void WeaponInfoManager::freeInformation(Handle handle)
{
    WeaponInfo* const info = m_pool->get(handle);
    if (!info)
        return;
    m_pool->release(handle);
    retire(info);
}

// setInformation again, with a bool. Both ship; see the header.
bool WeaponInfoManager::changeInformation(Handle handle, WeaponInfo* info)
{
    if (!m_pool->get(handle))
        return false;
    setInformation(handle, info);
    return true;
}

// Both handles validated before either store, so no half-swap is possible.
bool WeaponInfoManager::swapInformation(Handle a, Handle b)
{
    return m_pool->swap(a, b);
}

// [CHOSEN, not recovered] Release records before rebuilding the free chain; old handles stay stale.
bool WeaponInfoManager::reset()
{
    m_pool->releaseIf([this](WeaponInfo* info) { retire(info); return true; });
    return m_pool->reset();
}

// [CHOSEN, not recovered] Adapted from eonflux7/openbf2-eon64 dd8b056c.
// Invalidate handles immediately, but pin records for plan owners and local tick borrowers.
int WeaponInfoManager::releaseWeapon(const IWeapon* weapon)
{
    return m_pool->releaseIf([this, weapon](WeaponInfo* info) {
        if (info->getWeapon() != weapon)
            return false;
        retire(info);
        return true;
    });
}

void WeaponInfoManager::retire(WeaponInfo* info)
{
    if (!info || info->isRetired())
        return;
    info->retire();
    m_retired.push_back(info);
}

void WeaponInfoManager::collectRetired()
{
    std::size_t keep = 0;
    for (WeaponInfo* info : m_retired)
    {
        if (info->planReferences())
            m_retired[keep++] = info;
        else
            delete info;
    }
    m_retired.resize(keep);
}

} // namespace ai
} // namespace hfe
} // namespace dice
