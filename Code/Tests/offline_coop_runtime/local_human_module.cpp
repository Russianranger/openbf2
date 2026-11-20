// [CHOSEN] QA-only Python extension. Finds the fixture local human and requests
// a real spawn on the server main thread; it does not model a network connection.
#include <Python.h>
#include <execinfo.h>
#include <signal.h>
#include <unistd.h>
#include "Code/BF2/Game/IGame.h"
#include "Code/BF2/Game/IGameServer.h"
#include "Code/BF2/Game/IGameLogic.h"
#include "Code/BF2/Game/GameLogic/ServerGameLogic.h"
#include "Code/BF2/Game/Objects/IPlayer.h"
#include "Code/BF2/Game/GameLogic/SpawnSystem/SpawnManager.h"
#include "Code/BF2/Game/GameLogic/SpawnSystem/SpawnGroup.h"
#include "Code/BF2/Scene/Object/PlayerManager.h"
#include "Code/BF2/Core/Ids.h"

namespace dice { namespace hfe { extern IBase* spawnManager; } }

static PyObject* enter(PyObject*, PyObject*)
{
    using namespace dice::hfe;
    if (!world::playerManager) return PyInt_FromLong(-1);
    world::IPlayer* player = world::playerManager->getLocalHumanPlayer();
    return PyInt_FromLong(player ? player->getId() : -1);
}

static PyObject* spawn(PyObject*, PyObject* args)
{
    using namespace dice::hfe;
    int id;
    if (!PyArg_ParseTuple(args, const_cast<char*>("i"), &id)) return 0;
    world::IPlayer* player = world::playerManager->getPlayerFromId(id);
    if (!player || gameLogic->getGameStatus() != 1 || player->getIsAlive())
        return PyInt_FromLong(0);
    SpawnManager* spawns = static_cast<SpawnManager*>(spawnManager);
    const std::list<int>& groups = spawns->getGroupsForTeam(2);
    for (std::list<int>::const_iterator it = groups.begin(); it != groups.end(); ++it)
    {
        ISpawnGroup* group = spawns->getSpawnGroup(*it);
        if (!group || !group->getActive(false) || !group->getSpawnPoint(false)) continue;
        player->setTeam(2, false);
        player->setKit(0);
        player->setSpawnGroup(*it);
        static_cast<ServerGameLogic*>(gameLogic)->spawnPlayer(player, 0);
        return PyInt_FromLong(player->getIsAlive());
    }
    return PyInt_FromLong(0);
}

static PyMethodDef methods[] = {
    {const_cast<char*>("enter"), enter, METH_NOARGS, 0},
    {const_cast<char*>("spawn"), spawn, METH_VARARGS, 0}, {0, 0, 0, 0}
};
static void crash(int signum)
{
    void* stack[40];
    const int n = backtrace(stack, 40);
    backtrace_symbols_fd(stack, n, STDERR_FILENO);
    _exit(128 + signum);
}
extern "C" void initlayout_human()
{
    signal(SIGSEGV, crash);
    Py_InitModule(const_cast<char*>("layout_human"), methods);
}
