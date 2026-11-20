// [TEMP] Exercises the packaged BF2Audio/OpenAL libraries with a generated Ogg fixture.
// The null output driver avoids a device requirement; actual decode, worker and gains run.
#include "../BF2/Sound/NullSound.h"
#include "../BF2/Sound/SoundEngine/BfOpenAL.h"
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <thread>
#include <vector>

using namespace dice::hfe;
extern "C" ISoundEngine* getSoundEngine();
extern "C" void shutdownSoundEngine();
static PFN_alSourcef realGain;
static PFN_alSourceStop realStop;
static ALuint gameplaySource;
static std::mutex sampleMutex;
static std::vector<float> gains;
static std::atomic<int> musicStops{0}, gameplayStops{0};
static unsigned checks, failures;
static void check(bool ok, const char* what) {
    ++checks;
    if (!ok) { ++failures; std::fprintf(stderr, "FAIL %s\n", what); }
}
static void observeGain(ALuint source, ALenum key, float value) {
    if (key == AL_GAIN && source != gameplaySource) {
        std::lock_guard<std::mutex> lock(sampleMutex); gains.push_back(value);
    }
    realGain(source, key, value);
}
static void observeStop(ALuint source) {
    if (source == gameplaySource) ++gameplayStops;
    else ++musicStops;
    realStop(source);
}
static bool waitFor(bool stopped, int limitMillis) {
    for (int i=0; i<limitMillis/10; ++i) {
        if (stopped ? musicStops.load() != 0 : [&] {
            std::lock_guard<std::mutex> lock(sampleMutex); return gains.size() > 5;
        }()) return true;
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    return false;
}
int main(int argc, char** argv) {
    if (argc != 2) { std::fprintf(stderr, "usage: music_fade_android generated.ogg\n"); return 2; }
    setenv("ALSOFT_DRIVERS", "null", 1);
    ISoundEngine* sound = getSoundEngine();
    sound->setProperty("allow-any-system-openal", 1.0f);
    check(sound->initialize(static_cast<SoundQuality>(0), "System OpenAL", 32) == 0,
          "packaged OpenAL provider initializes");
    if (failures) return 1;
    sound->setVolumeForGroup(1, 1.0f);
    const float master = sound->getMasterVolume(), effects = sound->getVolumeForGroup(0);

    // A separately playing source represents gameplay audio and must survive the music fade.
    ALuint gameplayBuffer;
    short pcm[400] = {};
    al_GenBuffers(1, &gameplayBuffer);
    al_BufferData(gameplayBuffer, AL_FORMAT_MONO16, pcm, sizeof(pcm), 8000);
    al_GenSources(1, &gameplaySource);
    al_Sourcei(gameplaySource, AL_BUFFER, gameplayBuffer);
    al_Sourcei(gameplaySource, AL_LOOPING, 1);
    al_Sourcef(gameplaySource, AL_GAIN, .37f);
    al_SourcePlay(gameplaySource);
    check(al_GetError() == 0, "independent gameplay source starts");
    realGain = al_Sourcef; realStop = al_SourceStop;
    al_Sourcef = observeGain; al_SourceStop = observeStop;
    check(sound->loadMusic(argv[1]) && sound->playMusic(), "actual Ogg loading music starts");
    check(waitFor(false, 3000), "music worker publishes gain");
    const auto start = std::chrono::steady_clock::now();
    check(sound->stopMusic(5.0f), "five-second fade accepts the active track");
    check(waitFor(true, 10000), "music worker stops after fade");
    const double elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now()-start).count();
    sound->stopMusic(0.0f); // Join the completed worker before inspecting or restarting it.
    {
        std::lock_guard<std::mutex> lock(sampleMutex);
        check(gains.size() >= 500, "fade traverses the actual worker steps");
        check(!gains.empty() && gains.front() == 1.0f && gains.back() < .01f,
              "gain fades from full volume to silence");
        bool decreasing = true, midpoint = false;
        for (size_t i=1; i<gains.size(); ++i) {
            if (gains[i] > gains[i-1]+.00001f) decreasing = false;
            if (gains[i] > .45f && gains[i] < .55f) midpoint = true;
        }
        check(decreasing && midpoint, "music fades smoothly rather than cutting off");
    }
    check(elapsed >= 4.8 && elapsed < 10.0, "fade respects the requested duration");
    float gameplayGain = 0;
    ALint gameplayState = 0;
    al_GetSourcef(gameplaySource, AL_GAIN, &gameplayGain);
    al_GetSourcei(gameplaySource, AL_SOURCE_STATE, &gameplayState);
    check(std::fabs(gameplayGain-.37f) < .00001f && gameplayState == AL_PLAYING && gameplayStops == 0,
          "gameplay source keeps playing at its original volume");
    check(sound->getMasterVolume() == master && sound->getVolumeForGroup(0) == effects &&
          sound->getVolumeForGroup(1) == 1.0f, "fade leaves persistent group/master settings unchanged");
    {
        std::lock_guard<std::mutex> lock(sampleMutex); gains.clear();
    }
    musicStops = 0;
    check(sound->loadMusic(argv[1]) && sound->playMusic(), "next round restarts loading music");
    check(waitFor(false, 3000), "next round worker runs");
    sound->stopMusic(0.0f);
    {
        std::lock_guard<std::mutex> lock(sampleMutex);
        check(!gains.empty() && gains.front() == 1.0f && gains.back() == 1.0f,
              "next round has no leftover fade");
    }
    al_Sourcef = realGain; al_SourceStop = realStop;
    al_SourceStop(gameplaySource);
    al_DeleteSources(1, &gameplaySource); al_DeleteBuffers(1, &gameplayBuffer);
    shutdownSoundEngine();
    std::printf("Packaged music fade: %u/%u passed (%.3f seconds)\n", checks-failures, checks, elapsed);
    return failures ? 1 : 0;
}
