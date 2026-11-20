// Handle pool for weapon information. The singleton keeps the first instance.
// setInformation and changeInformation perform the same guarded store; only changeInformation returns success.
// Header placement [INFERRED]: beside its .cpp, whose path AIDLL_r.dll confirms (AIUtils/WeaponInfoManager.cpp).

#ifndef AI_AIDLL_AI_AIKNOWLEDGE_WEAPONINFOMANAGER_H
#define AI_AIDLL_AI_AIKNOWLEDGE_WEAPONINFOMANAGER_H

#include "../../../../Core/SpHandlePool.h"
#include <vector>

namespace dice {
namespace hfe {
namespace ai {

// The recovered pooled type is defined in WeaponInfo.h. The manager stores only WeaponInfo pointers
// so the forward declaration remains sufficient here.
class WeaponInfo;
class IWeapon;

// Native owner of live and retired weapon records; rebuilt AI only.
class WeaponInfoManager
{
public:
    typedef SpHandlePool<WeaponInfo, 65535> Pool;
    typedef Pool::Handle                    Handle;

    WeaponInfoManager();
    ~WeaponInfoManager();                                   // not virtual

    Handle      registerInformation(WeaponInfo* info);
    WeaponInfo* getInformation(Handle handle) const;
    void        setInformation(Handle handle, WeaponInfo* info);
    void        freeInformation(Handle handle);
    bool        changeInformation(Handle handle, WeaponInfo* info);
    bool        swapInformation(Handle a, Handle b);
    bool        reset();
    // [CHOSEN, not recovered] Invalidates every record built on the given engine weapon.
    int         releaseWeapon(const IWeapon* weapon);
    // Run only after an AI update returns, when temporary borrowed pointers are gone.
    void        collectRetired();
    unsigned int retiredCount() const { return static_cast<unsigned int>(m_retired.size()); }

    static WeaponInfoManager* m_instance;

private:
    /* +0x00 */ Pool* m_pool;
    std::vector<WeaponInfo*> m_retired;
    void retire(WeaponInfo* info);
};

} // namespace ai
} // namespace hfe
} // namespace dice

#endif // AI_AIDLL_AI_AIKNOWLEDGE_WEAPONINFOMANAGER_H
