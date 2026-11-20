# Android loading presentation

The direct-start Android path deliberately bypasses the Flash frontend. In
`ModuleGame::handleEvent`, `BF2_PORT_DIRECT_START` suppresses the loading menu events
that would construct and draw `endOfRound.swf`. That explains the blank SDL surface
while a direct offline map was loading. Removing that guard also changes the
BetweenRoundsLogic input-map and Swiff ownership lifecycle; this change preserves it.
Menu-based launches continue to use their existing Flash presentation.

The Android view instead displays the owner's imported map artwork while the native
game thread loads. It searches loose `Levels/<map>/Info/loadmap.png` case-insensitively,
then `loadmap*.png`. No game data or artwork is bundled, copied into the APK, or modified.
The official BF2 demo's `loadmap.png` is actually a 512×512 DDS/DXT1 file. The loader
checks file magic and supports DDS DXT1, DXT3 and DXT5, plus ordinary PNG/custom images
through Android's bitmap decoder. A missing or unsupported image leaves the map name
and loading status visible. Decoding runs off the UI thread.

`AndroidUiState` publishes a snapshot from the game thread. The UI does not read native
game pointers. Loading percentage is the same measured value produced by the existing
`GameLogic` load callback, with unknown progress represented explicitly. Reaching 100%
does not remove the loading screen: the first normal frame with a loaded level does.
Failure, abort, shutdown, and a subsequent map load update the state independently.
The native level name also changes the displayed artwork on reload.
Early failed starts and `noAutoJoin` returns clear loading even when a game object
has already been allocated. Only an accepted join or successful server start keeps
the loading state pending for the next normal frame.

The low eight bits encode starting, loading, intro, menu, or gameplay. Bit eight says
whether the current intro clip is skippable; the upper word stores percentage plus one
(zero means unknown). Loading input is suppressed. Skip Intro appears only for a real
skippable movie, and Log is limited to intro/menu screens. The loading view paints and
intercepts nothing after loading or for `--menu` launches, so it cannot cover deployment
or gameplay input. Activity pause/focus/destroy callbacks stop polling and release images.
The transparent view does not redraw during gameplay. Direct launches poll state every
250 ms outside loading to detect the next round, and every 100 ms while loading; only
visibility, percentage, map, or artwork changes invalidate it. Flash launches do not poll.

Direct loading also needs to finish its music lifecycle. The map's `.desc` selects the
loading track (the stock Karkand descriptor names `common/sound/menu/music/load_MEC_music.ogg`).
`MusicPlayer` starts it on `ECMenu` event 3. The usual event 5 comes from the Flash
remove-loading command, which the direct launch does not execute. At the first normal
frame whose published phase is gameplay, Android now calls `MusicPlayer::finishDirectLoading`.
It uses the existing five-second `stopMusic` fade and returns the player to idle. Idle
frames do nothing; the next map event can start and fade its own track. The hook does not
change master, effects, voice, or stored music volume, and Flash launches retain their flow.

Direct Android launches keep track selection inside the current mod's exact map metadata.
They normalize the level-path separators and trim the descriptor's music path, then use the
existing mounted filesystem to open it. A later menu-music request cannot replace that map
track. Missing metadata, missing audio, or a failed playback start leaves the player idle and
stops any previous round's track; no faction theme or menu song is guessed. The log now names
the map, requested resource, and whether its stream started. This is intentional direct-mode
policy, not a change to the desktop/Flash state table or to the sound plugin ABI.

Stock maps share faction themes. The official 1.50 dedicated-server descriptors name the same
`load_MEC_music.ogg` for Strike at Karkand, Gulf of Oman, and Road to Jalalabad. Dalian Plant
names `load_CH_music.ogg`; Midnight Sun names `load_US_music.ogg`. Hearing the same MEC track
on the first three maps is therefore expected. The resource bytes still come from the
owner-imported active mod, and no game music is included in the APK.

The changed lifecycle lives in `BF2Engine.cpp` and `Sound/BfSound/MusicPlayer.cpp`, both
members of `bf2_client_objects` linked into the rebuilt APK's `libmain.so`. The existing
`libBF2Audio.so` owns the Ogg worker and source-specific fade; it is intentionally unchanged.
`Sound/SoundEngine/Music.cpp` is guarded by `BF2_IMAGE_BF2AUDIO`, so changing that file in
the host-only build would not change the packaged audio plugin.

This is an intentional Android presentation change requested for the direct offline
workflow, not a reconstruction of the retail loading movie or a claim that the Flash
direct-start lifecycle has been validated.

## Checks

- `Code/Tests/android_loading_state.cpp`: native startup, intro, progress, completion,
  late callbacks, failed/aborted loading, shutdown and reload transitions.
- `android/thor/tests/LoadingArtTest.java`: DXT1/3/5 color and alpha branches, normal PNG
  magic, truncated/oversized files, mixed-case lookup and preferred artwork selection.
  Optional arguments are an owner-provided DDS image and an output RGBA file.
- `android/thor/tests/GameLoadingViewTest.java`: Robolectric 4.14.1, Android 13 with
  native Canvas/bitmap rendering. Verifies asynchronous PNG decoding, the measured bar,
  unobstructed gameplay/Flash launches, quiet unchanged frames, redraws on reload/progress,
  polling cancellation and bitmap cleanup.
- The genuine demo DDS was privately compared against Pillow's independent decoder:
  all 1,048,576 RGBA bytes matched. Its artwork is not part of the regression fixtures.
- The three modified native translation units compiled with production Android ARM64
  flags; the combined launcher compiled against Android SDK 35.
- `Code/Tests/music_player_runtime.cpp`: Android native player/event tests include direct
  completion from idle/menu/map music, no repeated fade requests and subsequent rounds.
  The same fixture can link against packaged `libmain.so` without compiling `MusicPlayer.cpp`
  to verify the delivered body. Its Android additions do not change the existing Windows checks.
- `Code/Tests/map_music_android.cpp`: actual ModInfo/XML, map events, MusicPlayer, BF2Audio/OpenAL,
  BfSound file callbacks, and loose/ZIP filesystems verify the map-resource route across six
  rounds (MEC, MEC, MEC, CH, US, MEC). `make_map_music_fixture.py` accepts a private stock
  `mods/bf2` directory and either its owned Common_client.zip or a generated compatible Ogg.
  It stages mixed-case parent paths, lowercase loose assets as imported on Android, and
  mixed-case ZIP entries. Generated audio proves routing/decoding but is not evidence about
  the contents of a retail soundtrack. Link the executable to `libmain` and `libBF2Audio`,
  then run it with the absolute staged root and `loose` or `zip` argument.
- `Code/Tests/music_fade_android.cpp`: links to packaged `libBF2Audio.so`; the real Ogg worker
  and OpenAL null output driver verify a five-second decreasing gain, final stop, unchanged
  independent gameplay source/master/group volumes, and full-volume playback on the next round.
  Supply an owned BF2-compatible Ogg or generate a non-game tone with
  `python3 Code/Tests/make_music_fixture.py /absolute/path/tone.ogg` (requires ffmpeg).
  The generator adjusts the Vorbis vendor string and Ogg checksum because the reader rejects
  other vendor strings. The generated tone is not game content and is never packaged.
- `Code/Tests/verify_packaged_music_android.sh` repeats all three native fixtures against an
  APK's extracted libraries or a supplied native directory. Set `ANDROID_NDK_HOME` and pass
  the APK/directory, Android ARM64 runner, private stock `mods/bf2`, and fresh output directory;
  an optional final argument supplies owned Common_client.zip audio. The script compiles only
  test sources, verifies that player methods are imported from the shared host, and hashes
  the libraries before and after execution. Its JSON result records the audio provenance and
  deliberately distinguishes generated decoder input from a supplied soundtrack archive.

These checks cover publication, decoding, drawing and lifecycle boundaries. A physical
Thor run is still required to confirm the complete loading-to-deployment transition.
