#!/usr/bin/env python3
"""Compile the actual Android launch/config functions with storage-only platform stand-ins.

No game/server or bot logic is mocked here: that is exercised by run.py. This
checks the separate SDL-argument -> command line/files/environment seam.
"""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source", type=lambda p: Path(p).resolve(), required=True)
parser.add_argument("--out", type=lambda p: Path(p).resolve(), required=True)
parser.add_argument("--compiler", required=True)
parser.add_argument("--runner", required=True)
args = parser.parse_args()
args.out.mkdir(parents=True, exist_ok=True)
source = (args.source / "Code/BF2/Game/Main/BF2.cpp").read_text()
tickets_source = (args.source / "Code/BF2/Game/GameLogic/ServerGameLogic.cpp").read_text()
def function(signature, text=source):
    start = text.index(signature)
    return text[start:text.index("\n}\n", start) + 3]
header = r'''
#include <string>
#include <vector>
#include <map>
#include <fstream>
#include <iterator>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <strings.h>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include "Code/BF2/Game/GameServer/OfflineCoopBots.h"
#include "Code/BF2/Game/GameServer/HostMapLayout.h"
#define BF2_OFFLINE_TICKETS_TEST 1
#include "Code/BF2/Game/GameServer/OfflineTickets.h"
#define BF2_OFFLINE_MAP_PACK_TEST 1
#include "Code/BF2/IO/File/OfflineMapPack.h"
static std::string storage;
static const char* SDL_GetAndroidExternalStoragePath() { return storage.c_str(); }
static void installAndroidCrashReport() {}
static void* adrenotools_open_libvulkan(int,int,int,const char*,const char*,const char*,int,int) { return 0; }
// [TEMP] Only the game identity queries needed by the actual ticket getters.
namespace offline_tickets = dice::hfe::offline_tickets;
struct TicketGame {
    std::string level="gulf_of_oman", mode="gpm_coop";
    int size=64, maxPlayers=64;
    const std::string& getCurrentMapName() const { return level; }
    const std::string& getCurrentMapGameMode() const { return mode; }
    int getCurrentMapSize() const { return size; }
    int getCurrentMapMaxPlayers() const { return maxPlayers; }
};
static TicketGame ticketGame;
static TicketGame* game=&ticketGame;
static int compareIgnoreCase(const char* a,const char* b) { return strcasecmp(a,b); }
static std::map<unsigned int,unsigned int> g_defaultTicketsBySize[3];
struct ServerGameLogic {
    unsigned int m_defaultNumberOfTickets[2]={0,0};
    void setDefaultNumberOfTickets(unsigned int,unsigned int);
    void setDefaultNumberOfTicketsEx(unsigned int,unsigned int,unsigned int);
    unsigned int getDefaultNumberOfTicketsEx(unsigned int,unsigned int);
    unsigned int getDefaultNumberOfTickets(unsigned int);
};
'''
main = r'''
static int checks = 0;
static void check(bool ok, const char* why) {
    ++checks;
    if (!ok) { std::fprintf(stderr,"FAIL %s\n",why); std::exit(1); }
}
static std::string read(const std::string& path) {
    std::ifstream f(path.c_str()); return std::string(std::istreambuf_iterator<char>(f),{});
}
static std::string launch(std::vector<std::string> a) {
    std::vector<char*> v; for (auto& s:a) v.push_back(&s[0]);
    int savedOut=dup(1), savedErr=dup(2);
    std::string result=androidCommandLine(int(v.size()),v.data());
    std::fflush(stdout); std::fflush(stderr);
    dup2(savedOut,1);dup2(savedErr,2);close(savedOut);close(savedErr);
    return result;
}
int main(int argc,char** argv) {
    if(argc!=2)return 2;
    storage=std::string(argv[1])+"/storage";mkdir(storage.c_str(),0700);
    const std::string game=std::string(argv[1])+"/game";mkdir(game.c_str(),0700);
    std::vector<std::string> base={"bf2","--mod",game+"/mods/bf2","--name","Player","--driver","system"};
    const int cases[][2]={{0,0},{0,5},{7,0},{3,5},{31,32},{63,0}};
    for(const auto& pair:cases) {
        auto a=base;a.insert(a.end(),{"--hosted","--coop","--level","strike_at_karkand",
            "--team1-bots",std::to_string(pair[0]),"--team2-bots",std::to_string(pair[1])});
        auto command=launch(a);auto settings=read(storage+"/coop-settings.con");
        check(command.find("+maxPlayers 1")!=std::string::npos,"reserve one human slot");
        check(command.find("+ai 1")!=std::string::npos,"enable AI");
        check(command.find("+gameMode gpm_coop")!=std::string::npos,"co-op mode");
        check(settings.find("sv.coopBotCount "+std::to_string(pair[0]+pair[1])+"\n")!=std::string::npos,"serialized total");
        check(settings.find("sv.autoBalanceTeam 0\n")!=std::string::npos,"disable automatic team changes");
        check(settings.find("sv.serverIP \"127.0.0.1\"")!=std::string::npos,"local listener");
        dice::hfe::offline_coop::BotCounts selected;
        check(dice::hfe::offline_coop::readBotCounts(selected),"valid native selection");
        check(selected.team1==pair[0]&&selected.team2==pair[1],"exact selected teams");
    }
    for(const auto& pair:std::vector<std::pair<std::string,std::string>>{{"-1","0"},{"0","64"},{"32","32"},{"2147483647","0"},{"x","1"}}) {
        auto a=base;a.insert(a.end(),{"--hosted","--coop","--team1-bots",pair.first,"--team2-bots",pair.second});
        check(launch(a).empty(),"reject invalid native bot selection");
    }
    for(const char* mode:{"gpm_coop","sp1","gpm_cq"}) for(const char* size:{"16","32","64"}) {
        auto a=base;a.insert(a.end(),{"--hosted","--coop","--level","strike_at_karkand",
            "--mode",mode,"--size",size,"--team1-bots","0","--team2-bots","0"});
        auto command=launch(a);
        check(command.find(std::string("+gameMode ")+mode)!=std::string::npos,"exact game mode");
        check(command.find("+maxPlayers 1")!=std::string::npos,"offline human limit independent of layout");
        check(command.find(std::strcmp(mode,"gpm_cq")==0?"+ai 0":"+ai 1")!=std::string::npos,"conquest disables AI");
        check(read(storage+"/coop-maplist.con")==std::string("mapList.append strike_at_karkand ")+mode+" "+size+"\n","exact map list size");
        check(dice::hfe::host_layout::selectedSize("strike_at_karkand",mode)==std::atoi(size),"size preserved through native +loadLevel insertion");
        check(read(storage+"/coop-settings.con").find("sv.numPlayersNeededToStart 1\n")!=std::string::npos,"one human starts offline match");
    }
    for(const char* size:{"0","17","128","-1","2147483647"}) {
        auto a=base;a.insert(a.end(),{"--hosted","--coop","--level","strike_at_karkand","--size",size});
        check(launch(a).empty(),"reject unavailable native layout size");
    }
    auto mixed=base;mixed.insert(mixed.end(),{"--hosted","--coop","--level","Gulf_of_Oman","--mode","gpm_coop","--size","64"});
    check(!launch(mixed).empty(),"mixed-case imported map launches");
    check(dice::hfe::host_layout::selectedSize("gulf_of_oman","GPM_COOP")==64,"case-insensitive exact map identity");
    check(dice::hfe::host_layout::selectedSize("gulf_of_oman_extra","gpm_coop")==0,"map identity cannot match a prefix");
    auto invalid=base;invalid.insert(invalid.end(),{"--hosted","--coop","--level","strike_at_karkand","--mode","gpm_cq","--size","64","--team1-bots","1","--team2-bots","0"});
    check(launch(invalid).empty(),"conquest refuses bots");
    auto menu=base;menu.push_back("--menu");
    const std::string menuCommand=launch(menu);
    check(!menuCommand.empty(),"interactive menu remains launchable");
    for(const char* option:{"+loadLevel","+hostServer","+joinServer","+ai 1"})
        check(menuCommand.find(option)==std::string::npos,"menu does not deploy a match");
    for(const char* flag:{"BF2_PORT_DIRECT_START","BF2_PORT_LOCAL_COOP",
                         "BF2_PORT_COOP_TEAM1_BOTS","BF2_PORT_COOP_TEAM2_BOTS",
                         "BF2_PORT_HOST_LEVEL","BF2_PORT_HOST_MODE","BF2_PORT_HOST_SIZE"})
        check(!std::getenv(flag),"menu clears previous direct deployment state");
    auto lan=base;lan.insert(lan.end(),{"--hosted","--level","strike_at_karkand"});
    check(launch(lan).find("+maxPlayers 16")!=std::string::npos,"ordinary host limit");
    check(!std::getenv("BF2_PORT_LOCAL_COOP")&&!std::getenv("BF2_PORT_COOP_TEAM1_BOTS")&&!std::getenv("BF2_PORT_COOP_TEAM2_BOTS"),"LAN clears offline scope");
    check(read(storage+"/serversettings.con").find("coopBotCount")==std::string::npos,"LAN config isolation");
    lan.insert(lan.end(),{"--size","64"});
    check(launch(lan).find("+maxPlayers 64")!=std::string::npos,"LAN host uses selected capacity");
    check(read(storage+"/maplist.con")=="mapList.append strike_at_karkand gpm_cq 64\n","LAN exact size");
    auto join=base;join.insert(join.end(),{"--connect","127.0.0.1:17667"});launch(join);
    check(!std::getenv("BF2_PORT_HOST_SIZE")&&!std::getenv("BF2_PORT_HOST_MODE")&&!std::getenv("BF2_PORT_HOST_LEVEL"),"join clears hosted layout scope");
    const std::string pack="openbf2_maps_0123456789abcdef0123456789abcdef";
    const std::string packLevel=game+"/mods/"+pack+"/levels/strike_at_karkand";
    for(const std::string& dir:{game+"/mods",game+"/mods/"+pack,game+"/mods/"+pack+"/levels",packLevel,packLevel+"/info"})
        mkdir(dir.c_str(),0700);
    for(const char* file:{"server.zip","client.zip","info/strike_at_karkand.desc"})
        std::ofstream(packLevel+"/"+file)<<"present";
    auto packed=base;packed.insert(packed.end(),{"--hosted","--coop","--level","Strike_At_Karkand",
        "--mode","gpm_coop","--size","64","--map-pack",pack});
    const auto packedCommand=launch(packed);
    check(packedCommand.find("+modPath mods/bf2 ")==0&&packedCommand.find(pack)==std::string::npos,"pack retains base mod and engine command line identity");
    check(std::getenv("BF2_PORT_MAP_PACK")&&pack==std::getenv("BF2_PORT_MAP_PACK"),"pack selection is local environment state");
    check(read(storage+"/coop-maplist.con")=="mapList.append Strike_At_Karkand gpm_coop 64\n","pack carries exactly one selected map and size");
    dice::hfe::offline_maps::Selection selected;
    check(dice::hfe::offline_maps::selection(selected)&&selected.level=="strike_at_karkand"&&selected.size==64,"pack canonicalizes map case without changing selected layout");
    for(const std::string& id:{std::string(""),std::string("../bf2"),pack+"a",pack.substr(1),std::string("openbf2_maps_0123456789ABCDEF0123456789abcdef")}) {
        auto a=packed;a.back()=id;
        check(launch(a).empty()&&!std::getenv("BF2_PORT_MAP_PACK"),"invalid pack id fails and clears overlay");
    }
    for(const std::vector<std::string>& extra:std::vector<std::vector<std::string>>{
        {"--map-pack",pack},{"--level","gulf_of_oman"},{"--size","32"},{"--menu"},{"--connect","10.0.0.1"}}) {
        auto a=packed;a.insert(a.end(),extra.begin(),extra.end());
        check(launch(a).empty()&&!std::getenv("BF2_PORT_MAP_PACK"),"duplicate or non-offline pack request rejected");
    }
    auto noSize=base;noSize.insert(noSize.end(),{"--hosted","--coop","--level","strike_at_karkand","--map-pack",pack});
    check(launch(noSize).empty(),"pack requires explicit size");
    auto incomplete=base;incomplete.push_back("--map-pack");check(launch(incomplete).empty(),"pack flag requires value");
    for(const char* level:{"../strike_at_karkand","strike_at_karkand;quit",""}) {
        auto a=base;a.insert(a.end(),{"--hosted","--coop","--level",level,"--size","64","--map-pack",pack});
        check(launch(a).empty(),"pack rejects invalid map id");
    }
    auto remote=base;remote.insert(remote.end(),{"--hosted","--level","strike_at_karkand","--size","64","--map-pack",pack});
    check(launch(remote).empty(),"LAN hosting cannot use optional offline overlay");
    for(const char* file:{"server.zip","client.zip","info/strike_at_karkand.desc"}) {
        const std::string path=packLevel+"/"+file;
        check(std::rename(path.c_str(),(path+".off").c_str())==0,"temporarily remove pack input");
        check(launch(packed).empty()&&!std::getenv("BF2_PORT_MAP_PACK"),"missing selected input refuses fallback");
        check(std::rename((path+".off").c_str(),path.c_str())==0,"restore pack input");
    }
    for(const auto& next:std::vector<std::vector<std::string>>{menu,lan,join,noSize}) {
        check(!launch(packed).empty(),"valid pack can launch again");
        launch(next);check(!std::getenv("BF2_PORT_MAP_PACK"),"next menu/LAN/join/invalid request clears pack state");
    }
    ServerGameLogic ticketLogic;
    ticketLogic.setDefaultNumberOfTickets(1,111);ticketLogic.setDefaultNumberOfTickets(2,222);
    check(ticketLogic.getDefaultNumberOfTickets(1)==111&&ticketLogic.getDefaultNumberOfTickets(2)==222,"flat asymmetric authored defaults");
    for(unsigned size:{16u,32u,64u}) {
        ticketLogic.setDefaultNumberOfTicketsEx(size,1,size*10);
        ticketLogic.setDefaultNumberOfTicketsEx(size,2,size*15);
    }
    auto ticketBase=base;ticketBase.insert(ticketBase.end(),{"--hosted","--coop","--level","gulf_of_oman","--size","64",
        "--team1-bots","0","--team2-bots","0"});
    for(const char* mode:{"gpm_coop","sp1","gpm_cq"}) {
        ticketGame.mode=mode;
        const unsigned authoredSize=std::strcmp(mode,"gpm_coop")==0?16:64;
        for(const auto& pair:std::vector<std::pair<std::string,std::string>>{{"",""},{"1",""},{"","9999"},{"200","450"},{"9999","1"}}) {
            auto a=ticketBase;a.insert(a.end(),{"--mode",mode});
            if(!pair.first.empty())a.insert(a.end(),{"--tickets-team1",pair.first});
            if(!pair.second.empty())a.insert(a.end(),{"--tickets-team2",pair.second});
            check(!launch(a).empty(),"valid per-team starting tickets launch");
            check(ticketLogic.getDefaultNumberOfTickets(1)==(pair.first.empty()?authoredSize*10:std::stoul(pair.first)),"team1 override or authored default");
            check(ticketLogic.getDefaultNumberOfTickets(2)==(pair.second.empty()?authoredSize*15:std::stoul(pair.second)),"team2 override or authored default");
            check(ticketLogic.getDefaultNumberOfTicketsEx(64,1)==640&&ticketLogic.getDefaultNumberOfTicketsEx(64,2)==960,"underlying authored defaults remain unchanged");
            check((read(storage+"/coop-settings.con").find("sv.ticketRatio 100\n")!=std::string::npos)==(!pair.first.empty()||!pair.second.empty()),"only custom starting counts pin the Python ratio");
        }
    }
    ticketGame.mode="gpm_coop";
    auto ticketed=ticketBase;ticketed.insert(ticketed.end(),{"--tickets-team1","321","--tickets-team2","654"});
    check(!launch(ticketed).empty(),"prepare scoped ticket selection");
    for(const char* flag:{"BF2_PORT_DIRECT_START","BF2_PORT_LOCAL_COOP"}) {
        unsetenv(flag);check(ticketLogic.getDefaultNumberOfTickets(1)==160,"non-direct/non-offline runtime ignores override");setenv(flag,"1",1);
    }
    ticketGame.level="another_map";check(ticketLogic.getDefaultNumberOfTickets(1)==160,"another map cannot inherit tickets");ticketGame.level="gulf_of_oman";
    ticketGame.size=32;check(ticketLogic.getDefaultNumberOfTickets(1)==160,"another size cannot inherit tickets");ticketGame.size=64;
    ticketGame.mode="sp1";check(ticketLogic.getDefaultNumberOfTickets(1)==640,"another mode cannot inherit tickets");ticketGame.mode="gpm_coop";
    check(ticketLogic.getDefaultNumberOfTickets(0)==0&&ticketLogic.getDefaultNumberOfTickets(3)==0,"invalid team indices remain rejected");
    for(const char* value:{"","0","-1","+1","10000","999999999999999999999999999999"," 2","2 ","1.5","NaN","1e3"}) {
        auto a=ticketBase;a.insert(a.end(),{"--tickets-team1",value});
        check(launch(a).empty()&&!std::getenv("BF2_PORT_TEAM1_TICKETS")&&!std::getenv("BF2_PORT_TEAM2_TICKETS"),"invalid count rejects and clears both ticket selections");
    }
    for(const auto& extra:std::vector<std::vector<std::string>>{{"--tickets-team1","2"},{"--tickets-team2","3"},{"--tickets-team2"},{"--menu"},{"--connect","127.0.0.1"}}) {
        auto a=ticketed;a.insert(a.end(),extra.begin(),extra.end());
        check(launch(a).empty(),"duplicate/missing/foreign-scope ticket request rejected");
    }
    for(const auto& next:std::vector<std::vector<std::string>>{menu,lan,join,ticketBase,noSize}) {
        check(!launch(ticketed).empty(),"valid tickets can launch again");launch(next);
        check(!std::getenv("BF2_PORT_TEAM1_TICKETS")&&!std::getenv("BF2_PORT_TEAM2_TICKETS"),"next default/menu/LAN/join/invalid launch clears overrides");
    }
    for(const auto& foreign:std::vector<std::vector<std::string>>{menu,lan,join}) {
        auto a=foreign;a.insert(a.end(),{"--tickets-team1","200"});check(launch(a).empty(),"ticket option forbidden outside direct offline deployment");
    }
    auto packedTickets=packed;packedTickets.insert(packedTickets.end(),{"--tickets-team2","890"});
    ticketGame.level="strike_at_karkand";
    check(!launch(packedTickets).empty()&&ticketLogic.getDefaultNumberOfTickets(2)==890,"map pack retains exact ticket selection");
    offline_tickets::clear();
    check(ticketLogic.getDefaultNumberOfTickets(1)==160&&ticketLogic.getDefaultNumberOfTickets(2)==240,"game shutdown clears overrides and restores asymmetric defaults");
    std::printf("PASS %d production Android launch/config checks\n",checks);
}
'''
generated = args.out / "launch_args.cpp"
generated.write_text(header + function("static void appendCommandLineArg(") +
                     function("static std::string androidCommandLine(") +
                     ''.join(function(signature,tickets_source) for signature in (
                         "void ServerGameLogic::setDefaultNumberOfTickets(",
                         "void ServerGameLogic::setDefaultNumberOfTicketsEx(",
                         "unsigned int ServerGameLogic::getDefaultNumberOfTicketsEx(",
                         "unsigned int ServerGameLogic::getDefaultNumberOfTickets(")) + main)
binary = args.out / "launch_args"
subprocess.run([args.compiler, "-std=c++14", "-O1", "-I" + str(args.source), str(generated), "-o", str(binary)], check=True)
subprocess.run([args.runner, str(binary), str(args.out)], check=True)
