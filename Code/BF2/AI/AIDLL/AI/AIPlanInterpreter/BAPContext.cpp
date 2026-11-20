// [INFERRED] Source placement.

#include "BAPContext.h"

namespace dice {
namespace hfe {
namespace ai {

// one instruction in the binary - the vptr store C++ emits. The class owns nothing and declares
// nothing beyond this; see the note in BAPContext.h.
BotScriptVariable::~BotScriptVariable()
{
}

// `allocate(12)` self-linked is an empty `std::list` with a 4-byte element - a member declaration
// not a statement. The whole constructor is the list coming into existence.
BAPContext::BAPContext()
{
}

// [CHOSEN, not recovered] Release pins at bot teardown as well as explicit plan replacement.
BAPContext::~BAPContext()
{
    clear();
}

bool BAPContext::isValid() const
{
    for (std::list<BotScriptVariable*>::const_iterator it = m_variables.begin();
         it != m_variables.end(); ++it)
        if (*it && !(*it)->isValid())
            return false;
    return true;
}

// Deletes all owned script variables and empties the context.
void BAPContext::clear()
{
    for (std::list<BotScriptVariable*>::iterator it = m_variables.begin();
         it != m_variables.end(); ++it)
    {
        if (*it)
            delete *it;
    }

    m_variables.clear();
}

} // namespace ai
} // namespace hfe
} // namespace dice
