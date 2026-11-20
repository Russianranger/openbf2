// Variable scope for a bot action plan. [INFERRED] Source placement.
// Owns its variables, including weapon references held across AI ticks.

#ifndef AI_AIDLL_AI_AIPLANINTERPRETER_BAPCONTEXT_H       // [INFERRED] guard style
#define AI_AIDLL_AI_AIPLANINTERPRETER_BAPCONTEXT_H

#include <list>

namespace dice {
namespace hfe {
namespace ai {

// Base for owned script variables; deletion uses the virtual destructor.
class BotScriptVariable
{
public:
    virtual ~BotScriptVariable();       // - the vptr store, nothing more
    // [CHOSEN, not recovered] Invalid engine dependencies stop a retained plan before dispatch.
    virtual bool isValid() const { return true; }
};

class BAPContext
{
public:
    BAPContext();
    ~BAPContext();
    void clear();
    bool isValid() const;

    // [CHOSEN, not recovered] added by ai-w1-e-fire (coord/ai-requests.md 2026-11-01); the owner may
    // rename it. Every plan variable the fire patterns create is put at the front of the list:
    // BBPFireInfantery::createPlanInternal calls `std::list<BotScriptVariable*>::insert(begin(), &v)`
    // right after each inlined BAPVariable construction (0x2654C6, 0x265547, ... 0x27127A).
    void addVariable(BotScriptVariable* variable) { m_variables.insert(m_variables.begin(), variable); }

private:
    /* 0 */ std::list<BotScriptVariable*> m_variables;   // the whole object
};

} // namespace ai
} // namespace hfe
} // namespace dice

#endif // AI_AIDLL_AI_AIPLANINTERPRETER_BAPCONTEXT_H
