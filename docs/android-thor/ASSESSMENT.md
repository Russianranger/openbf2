# Source and build assessment

Assessed 2026-10-21 (America/Chicago), against commit
[`ccf30993258bfb9638db4aa7115c8a4c1a456e55`](https://github.com/Russianranger/openbf2/commit/ccf30993258bfb9638db4aa7115c8a4c1a456e55).

## Conclusion

This is a credible source base for a substantial native-port project, not a near-complete
Android app. The useful foundation is the shared engine, native Linux dedicated-server
target, input/control abstractions, AI source tree and persistence/HUD interfaces. The
largest risks are unfinished engine/AI reconstruction and the Windows/x86 client renderer.
Controller configuration and a launcher cannot resolve those missing implementations.

A playable offline vertical slice must precede claims about 32/64-player bot battles,
stable frame rates or full BF2 feature parity. No completion percentage or delivery date is
supported by the evidence collected here. Counts of source files or frontier markers are
inventory measures, not estimates of remaining effort.

## Exact repository state

- Default and only returned branch: `main`; the checked-out tree was clean.
- Fork of `B2BF/OpenBF2`. The cross-repository comparison to upstream `main` was identical
  at the assessed commit (0 ahead, 0 behind). Refresh before implementation.
- No open pull requests and no workflow runs in this fork were returned at assessment time.
- Issues were disabled. No GitHub Issues or GitHub Milestone objects were created; the
  requested milestone log is the versioned [repository ledger](MILESTONES.md).
- No Android manifest, Gradle build or Gradle wrapper was found in the complete repository
  tree. CMake presets are `msvc-x86`, `msvc-x64` and `linux-x64`.
- `.github/workflows/ci.yml` triggers pushes only for `main`, plus PR events and manual
  dispatch. The existing CI guide's broader branch/tag wording is not the actual trigger.

## Observed CI, not fresh local qualification

The latest upstream CI run on the same engine SHA was
[39227840384](https://github.com/B2BF/OpenBF2/actions/runs/39227840384).
Its inspected job attempt completed on October 21; the run was initially created October 19.

| Job | Observed outcome | Meaning |
| --- | --- | --- |
| [Linux x64](https://github.com/B2BF/OpenBF2/actions/runs/39227840384/job/119163819704) | Success | Existing Linux build and Python check passed that upstream job. It does not prove map load, bots, gameplay or Android. |
| [Windows x86](https://github.com/B2BF/OpenBF2/actions/runs/39227840384/job/119163820198) | Failure in engine/framework checks | `bf2_runtime_pair.exe` fails to link `WorldPref::mTickCount`, referenced by `grenade_trace::log` in `PointPhysicsNode.cpp.obj`. Later client/AI build stages were not qualified by this job. |
| [Windows x64](https://github.com/B2BF/OpenBF2/actions/runs/39227840384/job/119163820064) | Failure linking dedicated server | Six unresolved `BF2VoipServer::CBF2VoipServer` symbols: constructor, destructor, `Initialize`, `CreateInstance`, `Update`, `Shutdown`. Inspect the VOIP source/link manifest. |

The Windows x86 engine/client/editor and AI targets retain diagnostic `/FORCE` links.
`bf2_client_linkcheck` is a separate strict check and is not a required step in the current
build workflow. Therefore even future green inherited CI would not establish a complete
client or AI implementation. See [build contracts](../building.md) and the actual CMake file.

No local configure/build was run: `cmake` and `ninja` were not available on this assessment
host's PATH. The environment had no physical Thor or owner game-content installation, and
no connected original-image disassembler session was used. This assessment did not execute
the game, measure FPS, validate packet compatibility, or test saved progression.

## Source findings and port implications

All paths below are relative to the repository root at the assessed SHA.

| Area | Concrete source evidence | Required work |
| --- | --- | --- |
| Build system | `CMakeLists.txt` selects MSVC or Linux GCC/Clang; Android is rejected by the platform selection. `bf2_aidll` and client images are under the non-x64 branch. | Add real Android image manifests and strict shared-library links; preserve the existing images. |
| Platform/CPU | `Code/BF2/Core/System.cpp` uses `rdtsc` in the non-Windows implementation. `Core/PlatformCompat.h` targets GCC/glibc spellings. | Audit x86 assembly, clocks, atomics, FPU behavior, Bionic APIs, filesystem semantics and native pointer widths. |
| Python | `External/python-embedded/linux/CMakeLists.txt` explicitly rejects non-Linux and non-x86-64 targets. Its README documents architecture-specific `pyconfig.h`. | Generate and validate Android/AArch64 configuration for embedded Python 2.3.4; retain compatible game-script behavior and ZIP imports. Do not merely rename the Linux preset. |
| Graphics | `RendDX9/RendDX9Init.cpp` includes Win32/D3D9; `RendDX9/Effect.cpp` uses D3DX effects. `RendDX9Base.h` still contains layout views and `vc71` types. | Typed native renderer interfaces, material/shader migration, textures, geometry and complete scene/HUD rendering. A simple DX9-name replacement is insufficient. |
| Input | `IO/Input/GameControllerDevice.cpp` directly uses Win32/DirectInput; `DX8InputDeviceManager.cpp` creates those devices. `ControlMap`, `PlayerInput` and named maps provide useful higher-level seams. | Android gamepad backend feeding engine actions, context maps, menus, rebinding, device hotplug and per-device presets. |
| Client UI | `Menu/SwiffHost.cpp` has live named aborts, including `playNow` and `parseResolution`; `External/gameswf/SwiffPlayer/SwiffPlayer.cpp` is the movie plugin. | Native-compatible menu/HUD path with explicit scope and complete session/settings/career actions. Preserve desktop frontiers until actually recovered. |
| AI | `AI/AIDLL/AI/Main/AILODManager.cpp`: constructor calls `updateAllPlayers`, which invokes an aborting frontier. `BotManager.cpp` and `AIBot/BotNavigation.cpp` retain lifecycle/navigation/vehicle stops. | Restore baseline AI end to end, port its plugin and callbacks, then implement enhanced bot behavior. |
| AI scale | Source inventory finds 284 `.h`/`.cpp` files in `Code/BF2/AI`, 100 containing `[FRONTIER` text, and 868 textual `BF2_AI_FRONTIER(` call occurrences in `.cpp` files. | Classify actual reachable blockers by scenario. These are not 868 independently counted features and not a runtime hit count. |
| Stats transport | `Game/Persistence/StatsManager.cpp` connects to `gamestats.bf2hub.p.bf2.org`; `HTTPRequest.cpp` and `PlayerStatsManager.cpp` use BF2Hub HTTP paths. | Explicit local persistence provider, independent of network availability; keep remote adapters separate. |
| Stats semantics | `StatsManager::sendSnapshot` only admits `gpm_coop` through its comparison; the source labels this an observed defect. `PlayerStats` retrieves HTTP records. | Audit actual score events and game-mode scripts. Do not mistake remote response parsing or round counters for a durable offline career. |
| Rank/unlocks | `Game/Python/PythonHost.cpp`, `Game/GameLogic/ScoreSystem`, `Menu/SwiffHost.cpp`, `BF2HudManagerHudEvents.cpp`, and `Game/Persistence` expose useful score/rank/unlock data and notifications. | Connect authoritative gameplay events to a durable ledger and validated rule evaluator; wire real persisted results into the UI and kit permissions. |
| Runtime content | Build docs require an owner BF2 1.50 installation, game Python and map resources; Windows client docs list D3DX25, Bink, Novint and OpenAL companions. | Asset inventory and native replacements/explicit optional capability handling. Windows binaries cannot become ARM64 dependencies just by packaging them. |

The source inventory can be reproduced without game data:

```sh
git rev-parse HEAD
git status --short
rg --files Code/BF2/AI | rg '\.(cpp|h)$'
rg -l '\[FRONTIER' Code/BF2/AI -g '*.cpp' -g '*.h'
rg -o 'BF2_AI_FRONTIER\(' Code/BF2/AI -g '*.cpp'
rg -n 'CMAKE_SYSTEM_NAME|CMAKE_SYSTEM_PROCESSOR|FATAL_ERROR' CMakeLists.txt Code/BF2/External/python-embedded/linux/CMakeLists.txt
```

## Scope choices

The proposed product is an offline-first native Android port with LAN/co-op capability.
An online-style career means locally retained score, ranks, awards and unlocks earned in
bot matches and trusted multiplayer sessions. It does not mean synchronizing offline points
into BF2Hub or any other third-party ranking service. Such interoperability requires a
separate protocol/trust assessment and is optional M30.

First prove one supported map with a modest bot count. Expand content and population only
after measuring CPU simulation, navigation, render cost, memory and thermal behavior. No
64-bot or 60 FPS promise is made for the Thor by this plan.

See [milestones](MILESTONES.md) for the complete work list, [design](DESIGN.md) for intentional
architecture decisions, and [device tests](DEVICE-TESTS.md) for bundled acceptance tests.
