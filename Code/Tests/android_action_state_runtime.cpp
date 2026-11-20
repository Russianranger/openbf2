// [TEMP] Tests the production Android intent state without fabricating a scene or player.
#include "PortShim/AndroidActionState.h"
#include <cstdio>
#include <cstdlib>
using namespace androidcontrols;
static unsigned checks=0;
static void check(bool ok,const char* label) {
    ++checks;
    if(!ok) { std::fprintf(stderr,"FAIL: %s\n",label); std::exit(1); }
}
static void tap(ActionState& state,Action action) { state.input(action,true);state.input(action,false); }
static bool empty(const Sample& sample) { return !sample.held&&!sample.pressed&&!sample.crouch&&!sample.stand&&!sample.prone; }
int main() {
    ActionState state;
    state.context(1,1,0,false);
    check((state.snapshot()&0xff)==0x11,"live standing infantry snapshot uses actual pose values");
    tap(state,CrouchToggle);auto sample=state.sample();
    check(sample.crouch&&!sample.stand&&!sample.prone,"standing tap requests only crouch");
    state.context(1,1,1,false);sample=state.sample();
    check(sample.crouch&&!sample.pressed,"crouch toggle stays held on later input samples");
    tap(state,CrouchToggle);sample=state.sample();
    check(sample.stand&&!sample.crouch&&!sample.prone,"crouched tap releases crouch to stand");
    state.context(1,1,2,false);tap(state,CrouchToggle);sample=state.sample();
    check(sample.stand&&!sample.crouch&&sample.prone,"prone tap requests a single Lie edge to stand");
    check(empty(state.sample()),"prone-to-stand edge is not repeated");

    state.context(1,1,0,false);state.input(Prone,true);sample=state.sample();
    check(!sample.crouch&&sample.stand&&sample.prone,"hold-prone action directly requests prone without crouch");
    state.context(1,1,2,false);sample=state.sample();
    check(!sample.prone&&!sample.crouch&&!sample.stand&&(sample.held&(1u<<Prone)),"continued hold cannot repeatedly toggle prone");
    state.input(Prone,false);tap(state,Prone);sample=state.sample();
    check(!sample.prone&&!sample.crouch&&sample.stand,"another hold while prone never asks Lie to stand");
    state.context(1,1,0,false);tap(state,CrouchToggle);state.sample();
    state.context(1,1,1,false);tap(state,Prone);sample=state.sample();
    check(sample.prone&&!sample.crouch,"prone replaces a latched crouch");

    state.input(Fire,true);state.input(Fire,true);sample=state.sample();
    check((sample.held&(1u<<Fire))&&(sample.pressed&(1u<<Fire)),"first held fire has one press edge");
    sample=state.sample();check((sample.held&(1u<<Fire))&&!(sample.pressed&(1u<<Fire)),"held fire is continuous without a duplicate edge");
    state.input(Fire,false);sample=state.sample();check(!(sample.held&(1u<<Fire))&&!(sample.pressed&(1u<<Fire)),"fire release clears continuous action");
    tap(state,Reload);sample=state.sample();check(!sample.held&&(sample.pressed&(1u<<Reload)),"short tap survives between engine input samples");
    check(empty(state.sample()),"edge action drains once");
    state.input(None,true);state.input(-1,true);state.input(ActionCount,true);
    check(empty(state.sample()),"invalid action IDs cannot shift or emit inputs");

    for(int event=0;event<5;event++) {
        state.context(1,1,0,false);tap(state,CrouchToggle);state.sample();state.input(Fire,true);
        unsigned before=state.snapshot();
        if(event==0)state.context(1,0,-1,false); // death
        if(event==1)state.context(1,2,-1,false); // enter vehicle
        if(event==2)state.context(1,1,0,true);  // pointer menu
        if(event==3)state.context(2,1,0,false); // replace local soldier
        if(event==4) {state.reset();state.context(1,1,0,false);} // focus/lifecycle reset
        check(empty(state.sample()),"context/focus change clears held fire and crouch latch");
        if(event!=4)check((before>>8)!=(state.snapshot()>>8),"life/vehicle/menu/owner transition advances generation");
    }
    state.context(3,0,-1,false);state.input(Fire,true);tap(state,CrouchToggle);
    check(empty(state.sample()),"dead/unavailable player cannot acquire gameplay actions");
    state.context(3,1,0,true);state.input(Fire,true);tap(state,Prone);
    check(empty(state.sample()),"pointer UI cannot acquire gameplay actions");
    state.context(3,2,-1,false);tap(state,CrouchToggle);tap(state,Prone);sample=state.sample();
    check(!sample.crouch&&!sample.stand&&!sample.prone,"vehicle occupant cannot acquire infantry stance requests");
    state.context(3,1,0,false);state.input(CommRose,true);state.input(Fire,true);state.sample();
    const unsigned roseEpoch=state.snapshot()>>8;
    state.context(3,1,0,true,false,true);sample=state.sample();
    check(sample.held==(1ull<<CommRose)&&!sample.crouch,"opening a HUD rose retains only its held UI action");
    check((state.snapshot()>>8)==roseEpoch,"rose pointer transition preserves its Java hold owner");
    state.context(3,1,0,true,true,true);sample=state.sample();
    check(empty(sample)&&(state.snapshot()>>8)!=roseEpoch,"opening text entry inside a pointer screen clears the rose and advances context");
    state.input(CommRose,false);check(empty(state.sample()),"rose closes on physical release");
    state.context(3,1,0,false);tap(state,ChatSquad);sample=state.sample();
    check(sample.pressed==(1ull<<ChatSquad),"action IDs above 31 retain their full mask bit");
    state.context(3,1,0,false,true);state.input(Fire,true);sample=state.sample();
    check(empty(sample)&&(state.snapshot()&128),"text entry is published and suppresses gameplay");
    state.context(3,1,3,false);tap(state,CrouchToggle);sample=state.sample();
    check(!sample.crouch&&!sample.prone,"swimming never latches a future crouch");
    std::printf("android action state: %u checks passed\n",checks);
}
