// [TEMP] Runtime ABI checks against the actual Android libmain, SwiffPlayer and RendDX9.
// Real engine ClassManager/EventManager/Settings/SwiffHost, with a content-free SWF and retry observer.
#include "Core/ClassManager.h"
#include "Core/EventManager.h"
#include "Core/SettingsRepostitory.h"
#include "Menu/SwiffHost.h"
#include "../../SwiffPlayerAbi/HiDpiGlyphs.h"
#include <dlfcn.h>
#include <cstdio>
#include <cmath>
#include <cstdlib>
#include <cstring>

extern "C" bool swiff_start();
extern "C" bool swiff_load(const char*);
extern "C" void swiff_drop_movie();
extern "C" bool swiff_pointer(void*,int,int,int,int,int);
extern "C" bool swiff_stop();
static int checks, failures, retries;
static void check(bool result, const char* name)
{
    ++checks; if (!result) ++failures;
    std::printf("%s: %s\n", result ? "PASS" : "FAIL", name);
}
static void bind(void* library, const char* name, void* value)
{
    void** slot = static_cast<void**>(dlsym(library,name));
    if (!slot) { std::fprintf(stderr,"missing %s: %s\n",name,dlerror()); std::exit(2); }
    *slot = value;
}
static void retry(void* owner) { ++*static_cast<int*>(owner); }
int main(int argc, char** argv)
{
    if (argc != 3) return 2;
    void* mainImage = dlopen("libmain.so",RTLD_NOW|RTLD_GLOBAL);
    void* swiff = dlopen("libSwiffPlayer.so",RTLD_NOW|RTLD_GLOBAL);
    void* renderer = dlopen(argv[2],RTLD_NOW|RTLD_GLOBAL);
    if (!mainImage || !swiff || !renderer) { std::fprintf(stderr,"dlopen: %s\n",dlerror()); return 2; }
    dice::hfe::ClassManager classes;
    dice::hfe::EventManager events;
    dice::hfe::SettingsRepostitory settings;
    for (void* lib : {mainImage,swiff,renderer}) {
        bind(lib,"_ZN4dice3hfe12classManagerE",&classes);
        bind(lib,"_ZN4dice3hfe12eventManagerE",&events);
        bind(lib,"_ZN4dice3hfe8settingsE",&settings);
    }
    settings.intSet("GSWindowSizeX",1920); settings.intSet("GSWindowSizeY",1080);
    auto* host = new dice::hfe::SwiffHost;
    check(swiff_start(),"production Swiff module initializes with actual EventManager");
    auto* glyphs = static_cast<openbf2::HiDpiGlyphs*>(classes.getSimpleSingleton(openbf2::glyphServiceName));
    check(glyphs != nullptr,"Swiff publishes glyph service through actual ClassManager ABI");
    if (!glyphs) return 1;
    check(glyphs->candidates("thor_test") == 0,"initial glyph registry empty");
    auto addPending = reinterpret_cast<void(*)(void*,void(*)(void*))>(
        dlsym(renderer,"_ZN4dice3hfe4geom15hidpiAddPendingEPvPFvS2_E"));
    auto removePending = reinterpret_cast<void(*)(void*)>(
        dlsym(renderer,"_ZN4dice3hfe4geom18hidpiRemovePendingEPv"));
    if (!addPending || !removePending) { std::fprintf(stderr,"pending exports: %s\n",dlerror()); return 2; }
    addPending(&retries,retry);
    auto* listener = static_cast<openbf2::GlyphListener*>(classes.getSimpleSingleton(openbf2::glyphListenerName));
    check(listener != nullptr,"renderer publishes real pending-font listener through same registry");
    if (!listener) return 1;
    listener->movieLoaded();
    check(retries == 0,"unchanged font generation does not retry");
    const unsigned generation = glyphs->generation();
    check(swiff_load(argv[1]),"real gameswf parser loads synthetic font-only 1024x768 SWF");
    check(glyphs->generation() > generation && glyphs->candidates("THOR_TEST") == 1,
          "parser registers exported outline under case-insensitive service key");
    check(std::strcmp(glyphs->name("thor_test",0),"ThorTest") == 0,"native glyph-service name call");
    float coverage[32*32];
    check(glyphs->raster("thor_test",0,'A',32,4,4,coverage,32,32,4),"native outline tessellation and raster callback");
    float area=0; for(float sample:coverage) area+=sample;
    check(std::fabs(area-256.0f)<1.0f,"square glyph coverage matches 16x16 pixels");
    check(!glyphs->raster("thor_test",0,'B',32,4,4,coverage,32,32,4),"missing glyph requests bitmap fallback");
    listener->movieLoaded(); listener->movieLoaded();
    check(retries == 1,"renderer retries exactly once for changed Swiff generation");
    auto createFontSystem = reinterpret_cast<dice::hfe::IBase*(*)(unsigned,dice::hfe::IBase*)>(
        dlsym(renderer,"_ZN4dice3hfe4geom16createFontSystemEjPNS0_5IBaseE"));
    auto* fontSystem = createFontSystem ? createFontSystem(1,nullptr) : nullptr;
    check(fontSystem != nullptr,"renderer creates production empty font cache");
    if (!fontSystem) return 1;
    unsigned size = (1920u << 16) | 1080;
    events.postEvent(static_cast<dice::hfe::EventCategory>(11),1,&size,0.0f);
    size = (2340u << 16) | 1080;
    events.postEvent(static_cast<dice::hfe::EventCategory>(11),2,&size,0.0f);
    check(fontSystem->getRef()==1,"empty font cache remains valid after real renderer resize dispatch");
    check(fontSystem->release()==0,"real font cache unregisters resize listener on release");
    events.postEvent(static_cast<dice::hfe::EventCategory>(11),1,&size,0.0f);
    check(swiff_pointer(host,960,540,1,512,384),"actual plugin center hit through actual host viewport");
    check(swiff_pointer(host,240,0,1,0,0),"actual plugin left menu edge");
    check(swiff_pointer(host,239,100,0,-32768,-32768),"actual plugin margin button release stays outside");
    settings.intSet("GSWindowSizeX",2340);
    check(swiff_pointer(host,1170,540,1,512,384),"actual plugin viewport follows host resolution change");
    removePending(&retries);
    check(classes.getSimpleSingleton(openbf2::glyphListenerName)==nullptr,"renderer removes listener after final pending owner");
    swiff_drop_movie();
    check(glyphs->candidates("thor_test")==0,"released movie leaves no stale outline reference");
    check(swiff_stop(),"production Swiff module unregisters from EventManager");
    check(classes.getSimpleSingleton(openbf2::glyphServiceName)==nullptr,"glyph service removed on module close");
    host->release();
    std::printf("Android UI runtime: %d/%d checks passed, raster area %.1f\n",checks-failures,checks,area);
    return failures ? 1 : 0;
}
