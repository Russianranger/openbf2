// Independent component QA. Real production constructors/getters/setters/reset;
// controlled GameServer identity + event sinks, with an empty real object world.
#include "Game/Main/BF2Engine.h"
#include "Game/GameServer/GameServer.h"
#include "Game/GameLogic/ServerGameLogic.h"
#include "Game/GameLogic/ScoreSystem/ScoreManager.h"
#include "Game/Common/GameEvent.h"
#include "Core/Modules.h"
#include "Core/ClassManager.h"
#include "Core/EventManager.h"
#include "Core/System.h"
#include "Core/SettingsRepostitory.h"
#include "Scene/Object/ObjectManager.h"
#include "Scene/Object/PlayerManager.h"
#include "IO/Network/NetworkManager.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <limits.h>
#include <unistd.h>
using namespace dice::hfe;
namespace dice { namespace hfe {
extern ClassManager* classManager;
namespace world { extern IBase* scoreManager; }
} }
static unsigned checks;
static void check(bool ok,const char* what) {
    ++checks;
    if(!ok) { std::fprintf(stderr,"FAIL %s\n",what); std::exit(1); }
}
static void origin(const char* symbol,const char* expected) {
    void* address=dlsym(RTLD_DEFAULT,symbol); Dl_info info{}; char actual[PATH_MAX];
    check(address && dladdr(address,&info) && info.dli_fname &&
          realpath(info.dli_fname,actual) && !std::strcmp(actual,expected),symbol);
    std::printf("ORIGIN %s %s\n",symbol,actual);
}
struct FixtureEvents : EventManager {
    unsigned int postEvent(EventCategory,unsigned int,void*,float) override { return 0; }
};
struct FixtureServer : GameServer {
    std::string map="gulf_of_oman", mode="gpm_coop";
    int size=64;
    void sendGameEventToAllPlaying(GameEvent* e) override { delete e; }
    void sendGameEventToAllConnected(GameEvent* e) override { delete e; }
    const std::string& getCurrentMapName() const override { return map; }
    const std::string& getCurrentMapGameMode() const override { return mode; }
    int getCurrentMapSize() const override { return size; }
    int getCurrentMapMaxPlayers() const override { return size; }
};
static void env(const char* name,const char* value) {
    if(value) setenv(name,value,1); else unsetenv(name);
}
static void selection(FixtureServer& server) {
    env("BF2_PORT_DIRECT_START","1");env("BF2_PORT_LOCAL_COOP","1");
    env("BF2_PORT_HOST_LEVEL",server.map.c_str());env("BF2_PORT_HOST_MODE",server.mode.c_str());
    env("BF2_PORT_HOST_SIZE",std::to_string(server.size).c_str());
}
int main(int argc,char**argv) {
    check(argc==2,"exact library path supplied"); char expected[PATH_MAX];
    check(realpath(argv[1],expected),"exact library path resolves");
    origin("_ZN4dice3hfe9BF2EngineC1EPNS0_3BF2E",expected);
    origin("_ZN4dice3hfe9BF2Engine12shutdownGameEb",expected);
    origin("_ZN4dice3hfe15ServerGameLogicC1Ev",expected);
    origin("_ZN4dice3hfe15ServerGameLogic25getDefaultNumberOfTicketsEj",expected);
    origin("_ZN4dice3hfe15ServerGameLogic27getDefaultNumberOfTicketsExEjj",expected);
    origin("_ZN4dice3hfe15ServerGameLogic5resetEv",expected);
    check(game == nullptr, "fixture begins without a game");
    auto* engine=new BF2Engine(nullptr);
    for (unsigned i = 0; i < 4; ++i) {
        env("BF2_PORT_TEAM1_TICKETS", "137");
        env("BF2_PORT_TEAM2_TICKETS", "281");
        env("BF2_TICKET_QA_UNRELATED", "retain");
        engine->shutdownGame((i & 1) != 0);
        check(!getenv("BF2_PORT_TEAM1_TICKETS") && !getenv("BF2_PORT_TEAM2_TICKETS"),
              "repeated no-game shutdown clears both overrides with either menu flag");
        check(!std::strcmp(getenv("BF2_TICKET_QA_UNRELATED"), "retain"),
              "shutdown preserves unrelated environment");
    }
    setDedicated(true); // Skip render-only constructor services; this is not a running match.
    g_system=new System;settings=new SettingsRepostitory;
    eventManager=new FixtureEvents;
    classManager->registerServer(new world::ObjectModule);
    world::objectManager=new world::ObjectManager;
    world::playerManager=new world::PlayerManager;
    gameEventRegistry=new GameEventRegistry;
    auto* server=new FixtureServer;game=server;
    io::networkManager=new io::NetworkManager;
    world::scoreManager=reinterpret_cast<IBase*>(new world::ScoreManager);
    auto* logic=new ServerGameLogic;
    std::puts("Constructed actual ServerGameLogic and empty production world services");
    logic->setDefaultNumberOfTicketsEx(16,1,111);logic->setDefaultNumberOfTicketsEx(16,2,173);
    logic->setDefaultNumberOfTicketsEx(64,1,401);logic->setDefaultNumberOfTicketsEx(64,2,527);
    selection(*server);
    env("BF2_PORT_TEAM1_TICKETS",nullptr);env("BF2_PORT_TEAM2_TICKETS",nullptr);
    check(logic->getDefaultNumberOfTickets(1)==111,"co-op authored team1 uses original 16-ticket table");
    check(logic->getDefaultNumberOfTickets(2)==173,"co-op authored asymmetry preserved");
    env("BF2_PORT_TEAM1_TICKETS","137");env("BF2_PORT_TEAM2_TICKETS","281");
    check(logic->getDefaultNumberOfTickets(1)==137,"exact custom team1");
    check(logic->getDefaultNumberOfTickets(2)==281,"exact custom team2");
    check(logic->getDefaultNumberOfTicketsEx(16,1)==111 && logic->getDefaultNumberOfTicketsEx(64,2)==527,"authored table is unchanged");
    logic->reset();
    check(logic->getDefaultNumberOfTickets(1)==137 && logic->getDefaultNumberOfTickets(2)==281,"actual empty-world round reset retains session overrides");
    env("BF2_PORT_TEAM1_TICKETS",nullptr);
    check(logic->getDefaultNumberOfTickets(1)==111 && logic->getDefaultNumberOfTickets(2)==281,"independent team1 omission");
    env("BF2_PORT_TEAM1_TICKETS","1");env("BF2_PORT_TEAM2_TICKETS","9999");
    check(logic->getDefaultNumberOfTickets(1)==1 && logic->getDefaultNumberOfTickets(2)==9999,"inclusive native limits");
    for(const char* invalid:{"0","10000","-1","1x"," 17",""}) {
        env("BF2_PORT_TEAM1_TICKETS",invalid);
        check(logic->getDefaultNumberOfTickets(1)==111,"malformed override preserves authored value");
    }
    env("BF2_PORT_TEAM1_TICKETS","137");
    env("BF2_PORT_DIRECT_START",nullptr);
    check(logic->getDefaultNumberOfTickets(1)==111,"menu/session identity rejected");
    env("BF2_PORT_DIRECT_START","1");env("BF2_PORT_LOCAL_COOP",nullptr);
    check(logic->getDefaultNumberOfTickets(1)==111,"LAN identity rejected");
    selection(*server);server->map="strike_at_karkand";
    check(logic->getDefaultNumberOfTickets(1)==111,"different map rejected");
    selection(*server);server->size=32;
    check(logic->getDefaultNumberOfTickets(1)==111,"different size rejected");
    selection(*server);server->mode="sp1";
    check(logic->getDefaultNumberOfTickets(1)==401,"different mode preserves authored selection");
    selection(*server);
    check(logic->getDefaultNumberOfTickets(1)==137,"direct solo sp1 override");
    check(logic->getDefaultNumberOfTickets(0)==0 && logic->getDefaultNumberOfTickets(3)==0,"invalid teams preserved");
    server->mode="gpm_cq";selection(*server);
    check(logic->getDefaultNumberOfTickets(1)==137,"direct local conquest override");
    game=nullptr;engine->shutdownGame(true);game=server;
    check(logic->getDefaultNumberOfTickets(1)==401 && logic->getDefaultNumberOfTickets(2)==527,"actual session shutdown removes overrides for next game");
    std::printf("PASS %u exact-library ARM64 getter/reset checks; controlled map identity and event sinks, real empty world, no assets or Python match\n",checks);
    std::fflush(stdout);
    // Full teardown expects a loaded game; the private process reclaims fixture services.
    _Exit(0);
}
