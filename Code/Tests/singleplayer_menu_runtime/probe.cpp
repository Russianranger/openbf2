// Runs unmodified, locally supplied menu scripts in the production gameswf VM.
#include "SwiffPlayer/SingleplayerMenuFlow.h"
#include "base/tu_file.h"
#include <map>
#include <string>
#include <cstdio>
#include <cstdlib>

namespace {
bool adapt = true;
int failures = 0, starts = 0, profileCreates = 0, mapAdds = 0;
std::string selected, frame, mode;
std::map<std::string,std::string> storage;
gameswf::movie* movie;
void check(bool ok, const char* label) { std::printf("%s %s\n",ok?"PASS":"FAIL",label); failures += !ok; }
void noop(const gameswf::fn_call& fn) { fn.result->set_bool(false); }
void createProfile(const gameswf::fn_call& fn) { ++profileCreates; fn.result->set_bool(true); }
void startServer(const gameswf::fn_call&) { ++starts; }
void clearMaps(const gameswf::fn_call&) { selected.clear(); }
void addMap(const gameswf::fn_call& fn) { ++mapAdds; selected=fn.arg(0).to_string(); }
void defaultMap(const gameswf::fn_call& fn) { fn.result->set_tu_string("gulf_of_oman"); }
void setMode(const gameswf::fn_call& fn) { mode=fn.arg(0).to_string(); }
void getFrame(const gameswf::fn_call& fn) { fn.result->set_tu_string(frame.c_str()); }
void setFrame(const gameswf::fn_call& fn) { frame=fn.arg(0).to_string(); }
void setStorage(const gameswf::fn_call& fn) {
    const std::string key=fn.arg(0).to_string(), value=fn.arg(1).to_string();
    storage[key]=value;
    if(adapt) openbf2::prepareSingleplayerMapMenu(fn,key.c_str(),value.c_str());
}
void getStorage(const gameswf::fn_call& fn) {
    const std::string key=fn.arg(0).to_string();
    const auto it=storage.find(key);
    const std::string value=it==storage.end()?fn.arg(1).to_string():it->second;
    if(adapt) openbf2::prepareSingleplayerMapMenu(fn,key.c_str(),value.c_str());
    fn.result->set_tu_string(value.c_str());
}
void trigger(const gameswf::fn_call& fn) {
    // The stock scheduler invokes these at the next update. Run queued work immediately
    // here so an explicit Start is observable without a rendering/update-loop fixture.
    movie->call_method(fn.arg(1).to_string(), "");
}
struct Namespace : gameswf::as_object {
    bool get_member(const tu_stringi& name, gameswf::as_value* value) override {
        if(gameswf::as_object::get_member(name,value)) return true;
        value->set_as_c_function_ptr(noop); return true;
    }
};
Namespace* child(gameswf::as_object_interface* parent,const char* name) {
    auto* result=new Namespace; parent->set_member(name,gameswf::as_value(result)); return result;
}
void bind(gameswf::as_object_interface* obj,const char* name,gameswf::as_c_function_ptr fn) {
    obj->set_member(name,gameswf::as_value(fn));
}
void setUp(gameswf::movie_definition* def) {
    auto* dice=child(gameswf::get_global_object(),"dice"); auto* bf2=child(dice,"bf2");
    auto* logic=child(bf2,"Logic"); bind(logic,"getStorageString",getStorage); bind(logic,"setStorageString",setStorage);
    bind(logic,"getDefaultLevel",defaultMap); bind(logic,"getFrameLabel",getFrame); bind(logic,"setFrameLabel",setFrame);
    auto* multi=child(bf2,"Multiplay"); bind(multi,"createServer",startServer); bind(multi,"clearMapList",clearMaps);
    bind(multi,"addMapToMapList",addMap); bind(multi,"setFilterGameMode",setMode);
    auto* profile=child(bf2,"Profile"); bind(profile,"createOfflineAccount",createProfile);
    for(const char* name:{"GeneralSettings","ServerSettings","Sound","AudioSettings","ControlSettings","VideoSettings"}) child(bf2,name);
    auto* instance=def->create_instance(); movie=instance->get_root_movie();
    for(const char* name:{"bg","popups","messageHandler"}) child(movie,name);
    gameswf::as_value messages; movie->get_member("messageHandler", &messages);
    child(messages.to_object(),"modalMessageHolder");
    child(child(movie,"footer"),"goFurther");
    auto* update=child(movie,"updateManager1"); bind(update,"registerTrigger",trigger);
    instance->advance(0.1f);
    gameswf::as_value firstRun;
    check(movie->get_member("noobieInit", &firstRun) && firstRun.is_function(), "stock functions loaded in production VM");
    movie->set_member("loginStatus",gameswf::as_value(profileCreates ? 1 : 0));
    movie->set_member("loginType",gameswf::as_value(0));
}
void chooseMap(const char* path) {
    auto* row=new gameswf::as_object;
    row->set_member("path",gameswf::as_value(path)); row->set_member("mapSize",gameswf::as_value(16));
    row->set_member("id",gameswf::as_value(0));
    gameswf::as_value method; movie->get_member("selectMap",&method);
    // The real selectMap belongs to the list component, whose parent owns a preview image.
    gameswf::as_value list(new gameswf::as_object);
    child(child(list.to_object(),"_parent"),"mapImage");
    gameswf::as_environment env; env.set_target(movie); env.push(gameswf::as_value(row));
    gameswf::as_value result;
    (*method.to_as_function())(gameswf::fn_call(&result,list.to_object(),&env,1,env.get_top_index())); env.drop(1);
}
}
int main(int argc,char** argv) {
    if(argc<2) return 2;
    gameswf::register_log_callback([](bool error,const char* text) { if(error) std::fprintf(stderr,"%s",text); });
    adapt = argc<3 || std::string(argv[2])!="--baseline";
    tu_file file(argv[1],"rb");
    auto* def=gameswf::create_movie_no_recurse(&file,gameswf::DO_NOT_LOAD_BITMAPS,gameswf::DO_NOT_LOAD_FONT_SHAPES);
    if(!def) return 2;
    setUp(def);
    gameswf::as_environment env; env.set_target(movie); gameswf::as_value result;
    const gameswf::fn_call call(&result,movie,&env,0,0);
    check(!openbf2::prepareSingleplayerMapMenu(call,"unrelated","1")
        && !openbf2::prepareSingleplayerMapMenu(call,"noobieSP","0"),
        "unrelated storage and a consumed shortcut do not install an override");
    movie->call_method("noobieInit","");
    check(profileCreates==1,"stock first-run action creates its offline profile");
    if(!adapt) {
        check(starts==1 && selected=="gulf_of_oman","baseline reproduces immediate default-map start");
        return failures?1:0;
    }
    check(starts==0 && mapAdds==0,"first-run Singleplayer neither chooses a map nor starts a match");
    check(frame=="trainingCreate" && movie->get_current_frame()==1,"stock navigation reaches the map picker");
    check(mode=="sp1" && storage["noobieSP"]=="0","singleplayer settings prepared and shortcut latch consumed");
    movie->call_method("gotoPage","%s","none");
    check(frame=="none" && starts==0,"Back/menu navigation does not start a match");
    movie->call_method("gotoPage","%s","trainingCreate");
    chooseMap("dragon_valley");
    check(selected=="dragon_valley" && starts==0,"stock map selection changes chosen map without starting");
    movie->call_method("explicitStart","");
    check(starts==1 && selected=="dragon_valley","stock explicit Start launches the chosen map once");
    // New movie redefines stock functions; the storage read must adapt them again.
    setUp(def); storage["noobieSP"]="1"; const int previous=starts;
    movie->call_method("dice.bf2.Logic.getStorageString","%s,%s","noobieSP","0");
    movie->call_method("createDefaultTraining","");
    check(starts==previous && frame=="trainingCreate","profile-reload shortcut also opens map selection");
    movie->call_method("dice.bf2.Multiplay.createServer","");
    check(starts==previous+1,"normal native createServer remains available");
    check(storage["currentFrameName"]=="trainingCreate","profile reload keeps selected menu page");
    std::printf("%d failures\n",failures); return failures?1:0;
}
