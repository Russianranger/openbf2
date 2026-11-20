
#include "IPIArmamentReal.h"

#include "Information.h"
#include "WeaponInfo.h"
#include "../AIUtils/WeaponInfoManager.h"
#include "../../../AIInterface/BFKnowledge/IAIObject.h"
#include "../../../AIInterface/BFKnowledge/IAIObjectPlugIn.h"

namespace dice {
namespace hfe {
namespace ai {

IPIArmamentReal::IPIArmamentReal(Information* information)
    : IPIArmament(information)
{
    updateWeapons();
}

IPIArmamentReal::~IPIArmamentReal()
{
}

// Retail: `return m_weapons[index]`, unchecked.
// [CHOSEN, not recovered] pr/x64-local:
// - index -1 (getActiveWeapon() with nothing in hand) or out of range answers the invalid handle
//   instead of reading outside the vector (harmless garbage on x86, an AV on x64);
// - a record released because its engine weapon was destroyed (WeaponInfoManager::releaseWeapon)
//   while the engine still lists a weapon in that slot triggers the kit-pickup refresh
//   (IAIObject::update: re-traverse the engine weapons, rebuild the records) and answers the new
//   handle. Retail code rarely null-checks the WeaponInfo it gets from this.
unsigned int IPIArmamentReal::getWeapon(int index)
{
    if (index < 0 || static_cast<unsigned int>(index) >= m_weapons.size())
        return 0xFFFFFFFFu;
    if (!WeaponInfoManager::m_instance->getInformation(m_weapons[index]))
    {
        IAIObject* const object = m_information->getAIObject();
        const std::vector<IWeapon*>& engineWeapons = object->getArmament()->getWeapons();
        if (static_cast<unsigned int>(index) < engineWeapons.size() && engineWeapons[index])
        {
            object->update();
            if (static_cast<unsigned int>(index) >= m_weapons.size())
                return 0xFFFFFFFFu;
        }
    }
    return m_weapons[index];
}

std::vector<unsigned int>& IPIArmamentReal::getWeapons()
{
    return m_weapons;
}

void IPIArmamentReal::updateWeapons()
{
    updateWeaponsInternal(true);
}

} // namespace ai
} // namespace hfe
} // namespace dice
