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

These checks cover publication, decoding, drawing and lifecycle boundaries. A physical
Thor run is still required to confirm the complete loading-to-deployment transition.
