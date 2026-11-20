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
def function(signature):
    start = source.index(signature)
    return source[start:source.index("\n}\n", start) + 3]
header = r'''
#include <string>
#include <vector>
#include <fstream>
#include <iterator>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include "Code/BF2/Game/GameServer/OfflineCoopBots.h"
static std::string storage;
static const char* SDL_GetAndroidExternalStoragePath() { return storage.c_str(); }
static void installAndroidCrashReport() {}
static void* adrenotools_open_libvulkan(int,int,int,const char*,const char*,const char*,int,int) { return 0; }
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
    auto lan=base;lan.insert(lan.end(),{"--hosted","--level","strike_at_karkand"});
    check(launch(lan).find("+maxPlayers 16")!=std::string::npos,"ordinary host limit");
    check(!std::getenv("BF2_PORT_LOCAL_COOP")&&!std::getenv("BF2_PORT_COOP_TEAM1_BOTS")&&!std::getenv("BF2_PORT_COOP_TEAM2_BOTS"),"LAN clears offline scope");
    check(read(storage+"/serversettings.con").find("coopBotCount")==std::string::npos,"LAN config isolation");
    std::printf("PASS %d production Android launch/config checks\n",checks);
}
'''
generated = args.out / "launch_args.cpp"
generated.write_text(header + function("static void appendCommandLineArg(") +
                     function("static std::string androidCommandLine(") + main)
binary = args.out / "launch_args"
subprocess.run([args.compiler, "-std=c++14", "-O1", "-I" + str(args.source), str(generated), "-o", str(binary)], check=True)
subprocess.run([args.runner, str(binary), str(args.out)], check=True)
