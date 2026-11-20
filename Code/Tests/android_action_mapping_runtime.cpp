// [TEMP] Actual named-action mapping into native PlayerInput/GameInput; no live scene or GPU.
#include "PortShim/AndroidActionState.h"
#include "IO/Input/PlayerInput.h"
#include "IO/Input/GameInput.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
namespace androidcontrols { void applySample(const Sample&,dice::hfe::io::PlayerInput&,dice::hfe::io::GameInput&); }
using namespace androidcontrols;
using namespace dice::hfe::io;
static unsigned checks=0;
static void check(bool pass,const char* label) {
    ++checks;if(!pass) {std::fprintf(stderr,"FAIL %s\n",label);std::exit(1);}
}
int main() {
    struct Mapping {Action action;int player,game;bool held;};
    const Mapping map[]={
        {Fire,PIFire,-1,true},{Aim,PIAltFire,-1,true},{Jump,PIAction,-1,true},
        {Sprint,PISprint,-1,true},{Reload,PIReload,-1,false},{Use,PIUse,-1,true},
        {Weapon1,PIWeaponSelect1,-1,false},{Weapon2,PIWeaponSelect2,-1,false},
        {Weapon3,PIWeaponSelect3,-1,false},{Weapon4,PIWeaponSelect4,-1,false},
        {Weapon5,PIWeaponSelect5,-1,false},{Weapon6,PIWeaponSelect6,-1,false},
        {Weapon7,PIWeaponSelect7,-1,false},{Weapon8,PIWeaponSelect8,-1,false},{Weapon9,PIWeaponSelect9,-1,false},
        {Seat1,PIPositionSelect1,-1,false},{Seat2,PIPositionSelect2,-1,false},
        {Seat3,PIPositionSelect3,-1,false},{Seat4,PIPositionSelect4,-1,false},
        {Seat5,PIPositionSelect5,-1,false},{Seat6,PIPositionSelect6,-1,false},
        {Seat7,PIPositionSelect7,-1,false},{Seat8,PIPositionSelect8,-1,false},
        {NextWeapon,PINextItem,-1,false},{PreviousWeapon,PIPrevItem,-1,false},
        {Scoreboard,PIShowScoreBoard,GIShowScoreboard,true},{Map,-1,GIMapSize,false},
        {Squad,-1,GILeader,false},{CommRose,-1,GIRadioComm,true},{TacticalRose,-1,GITacticalComm,true},
        {DropKit,PIDrop,-1,false},{FireMode,PIToggleFireRate,-1,false},
        {MapZoom,-1,GIMapZoom,false},{Map3d,-1,GI3dMap,false},
        {ChatAll,-1,GISayAll,false},{ChatTeam,-1,GISayTeam,false},{ChatSquad,-1,GISaySquad,false}
    };
    for(const auto& item:map)for(int kind=0;kind<2;++kind) {
        PlayerInput p{};GameInput g{};
        const auto bit=1ull<<item.action;
        Sample s{kind?bit:0,kind?0:bit,false,false,false};applySample(s,p,g);
        const bool expected=!kind||item.held;
        bool exact=true;
        for(int i=0;i<64;++i)if(p.inputs[i]!=(expected&&i==item.player?1.0f:0.0f))exact=false;
        for(int i=0;i<74;++i)if(g.inputs[i]!=(expected&&i==item.game?1.0f:0.0f))exact=false;
        check(exact,"each named action affects only its attested BF2 slots, with edge/hold semantics");
        if(expected&&item.player>=0)check((p.mappingFlags&(1ull<<item.player))!=0,"player mapping enabled from an initially unmapped sample");
        if(expected&&item.game>=0)check((g.mappingFlags&(1ull<<item.game))!=0,"game mapping enabled from an initially unmapped sample");
    }
    PlayerInput p{};GameInput g{};applySample(Sample{0,0,true,false,false},p,g);
    check(p.inputs[PICrouch]==1&&p.inputs[PILie]==0,"crouch latch uses only crouch input");
    applySample(Sample{0,0,false,true,true},p,g);
    check(p.inputs[PICrouch]==0&&p.inputs[PILie]==1,"prone exit never emits a crouch press");
    p={};g={};applySample(Sample{1ull<<Menu,1ull<<Menu,false,false,false},p,g);
    check(p.mappingFlags==0&&g.mappingFlags==0,"menu remains the separate Java Escape route");
    std::printf("android named-action mapping: %u checks passed\n",checks);
}
