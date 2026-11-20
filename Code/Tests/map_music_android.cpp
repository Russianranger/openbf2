// [TEMP] [CHOSEN, not recovered] Real ModInfo/XML -> MusicPlayer -> BF2Audio ->
// BfSound file callbacks -> loose/ZIP VFS. Caller supplies private stock metadata/audio.
#include "../BF2/Core/ClassManager.h"
#include "../BF2/Core/EventManager.h"
#include "../BF2/Core/SettingsRepostitory.h"
#include "../BF2/Game/ModInfo.h"
#include "../BF2/IO/File/FileManager.h"
#include "../BF2/IO/File/UnixFileSystem.h"
#include "../BF2/IO/File/ZipArchive.h"
#include "../BF2/IO/Stream/MemStream.h"
#include "../BF2/Sound/BfSound.h"
#include "../BF2/Sound/BfSound/MusicPlayer.h"
#include "../BF2/Sound/SoundEngine/BfOpenAL.h"
#include "SwiffHostStandIn.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unistd.h>

using namespace dice::hfe;
namespace dice { namespace hfe { extern ClassManager* classManager; } }
extern "C" ISoundEngine* getSoundEngine();
extern "C" void shutdownSoundEngine();
static unsigned checks, failures;
static void check(bool ok, const char* label) {
    ++checks; if (!ok) { ++failures; std::fprintf(stderr, "FAIL %s\n", label); }
}
static int state(MusicPlayer& player) {
    int out; std::memcpy(&out, reinterpret_cast<char*>(&player)+sizeof(void*), sizeof(out));
    return out;
}
struct Host : SwiffHostStandIn {
    ModInfo info;
    IBase* getModInfo() override { return &info; }
};
struct Streams : IClassServer {
    void addRef() override {}
    int getRef() override { return 1; }
    int release() override { return 1; }
    void* queryInterface(unsigned) const override { return nullptr; }
    void getClassList(std::vector<ClassRegInfo>& out) override {
        out.push_back({io::CID_MemStream, "dice.hfe.io.Stream.Memory", &io::MemStream::create});
    }
    void getSingletonList(std::vector<SingletonRegInfo>&) override {}
};
struct Files : BfSound {
    std::string opened;
    unsigned opens = 0;
    void* open(const char* path) override {
        opened = path; ++opens;
        void* handle = BfSound::open(path);
        std::fprintf(stderr, "VFS audio open: %s = %s\n", path, handle ? "ok" : "missing");
        return handle;
    }
};
struct Sound : BfSoundNull {
    ISoundEngine* actual;
    ISoundEngine* engine() const override { return actual; }
};
int main(int argc, char** argv) {
    if (argc != 3) {
        std::fprintf(stderr, "usage: map_music_android fixture-root loose|zip\n"); return 2;
    }
    setenv("BF2_ANDROID_GAME_ROOT", argv[1], 1);
    setenv("BF2_PORT_DIRECT_START", "1", 1);
    setenv("ALSOFT_DRIVERS", "null", 1);
    if (chdir(argv[1])) return 2;
    ClassManager registry;
    Streams streamFactories;
    registry.registerServer(&streamFactories);
    EventManager events;
    SettingsRepostitory settings0;
    classManager = &registry; eventManager = &events; settings = &settings0;
    io::FileManager files;
    io::fileManager = &files;
    io::UnixFileSystem* disk = new io::UnixFileSystem;
    files.setBaseSystem(disk); disk->release();
    files.addPath("mods/bf2");
    if (!std::strcmp(argv[2], "zip")) {
        io::ZipArchiveHandler* zip = new io::ZipArchiveHandler;
        files.addArchiveHandler(zip); zip->release();
        files.permitMountArchives(true);
        check(files.mountArchive("mods/bf2/common_client.zip", "Common", false) != nullptr,
              "actual ZIP archive mounts");
    }
    Host host;
    check(host.info.init(), "actual ModInfo discovers stock descriptors");
    IBase* host0 = &host;
    SingletonRegInfo registration = {"SwiffHost", &host0, 0, 0, 4};
    registry.registerSingleton(registration);
    // BfSound teardown requires the complete game's system/handler lifecycle, which this
    // narrow fixture does not start. Keep its real file-callback object alive until exit.
    Files* callbacks = new Files;
    Sound sound;
    sound.actual = getSoundEngine();
    sound.actual->setProperty("allow-any-system-openal", 1.0f);
    check(sound.actual->initialize(static_cast<SoundQuality>(0), "System OpenAL", 32) == 0,
          "actual OpenAL engine initializes");
    sound.actual->setSoundFileIO(callbacks);
    sound.actual->setVolumeForGroup(1, 1.0f);
    g_bfSound = &sound;
    MusicPlayer player;
    const char* maps[] = {"strike_at_karkand", "gulf_of_oman", "road_to_jalalabad",
                         "dalian_plant", "midnight_sun", "strike_at_karkand"};
    const char* factions[] = {"MEC", "MEC", "MEC", "CH", "US", "MEC"};
    for (unsigned i=0; i<6; ++i) {
        const IModInfo::Map* metadata = host.info.findMapFromCurrentMod(maps[i]);
        const std::string expected = std::string("common/sound/menu/music/load_")+factions[i]+"_music.ogg";
        check(metadata && metadata->music == expected, "real stock XML selects expected theme");
        std::string level = std::string("Levels/")+maps[i]+"/";
        const unsigned before = callbacks->opens;
        events.postEvent(static_cast<EventCategory>(10), 3, &level, 0.0f);
        check(state(player) == 2, "actual decoder/worker starts map music");
        check(callbacks->opens > before && callbacks->opened == expected,
              "real audio opens precisely the descriptor resource");
        const unsigned playingOpens = callbacks->opens;
        player.setState(0, nullptr);
        check(callbacks->opens == playingOpens && state(player) == 2,
              "late menu activation cannot replace the map track");
        player.finishDirectLoading();
        check(state(player) == 0, "completion retires loading music state");
        // Next event stops/joins any remaining fade before the next round's stream starts.
    }
    sound.actual->stopMusic(0.0f);
    sound.actual->setSoundFileIO(nullptr);
    shutdownSoundEngine();
    events.unregisterEventHandler(static_cast<EventCategory>(10), &player);
    registry.unregisterSingleton("SwiffHost");
    registry.unregisterServer(&streamFactories);
    std::printf("Stock metadata/audio %s: %u/%u passed\n", argv[2], checks-failures, checks);
    return failures ? 1 : 0;
}
