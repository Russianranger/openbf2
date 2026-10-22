# Android milestone ledger

Baseline: `ccf30993258bfb9638db4aa7115c8a4c1a456e55`, assessed 2026-10-21.
This file is the milestone log requested by the owner. It is not a list of completed features.

## Status rules

Use `PLANNED`, `READY`, `ACTIVE`, `BLOCKED`, `CODE-VERIFIED`, `DEVICE-VERIFIED` or `DONE`.
`CODE-VERIFIED` means only the explicitly recorded compile/link/fixture/runtime checks passed.
Device-dependent gates cannot become `DONE` without their specified physical-device evidence.
A passing compile never upgrades a gameplay status. Record partial work and blockers separately.

All intentional implementation also depends on M01's design-discussion gate. Recovered work
depends on the relevant original-image evidence, even when the compact dependency column does
not repeat M01. Dependencies are capability gates, not a requirement to serialize unrelated work.
Independent work may continue when another lane is blocked; do not create parallel agents or
duplicate full builds merely because work can proceed independently.

## Ledger

| ID | Milestone | Dependencies | Initial status | Required acceptance |
| --- | --- | --- | --- | --- |
| M00 | Source/CI assessment and autonomous handoff | None | DONE (documentation) | Evidence, full work list, design and bundled device gates recorded. |
| M01 | Evidence, content and intentional-design prerequisites | M00 | BLOCKED for recovery/feature implementation | Required images/tools/content inventoried; linked design discussion established. Independent inventory remains ready. |
| M02 | Restore desktop baseline and capture honest gates | M00 | READY (build maintenance) | Relevant x86/x64/Linux gates pass; strict-link deficits retained as named work lists. |
| M03 | Reproducible Android project and ARM64 CI | M01, M02 | PLANNED | Pinned clean build, native load, metadata and Android branch coverage. |
| M04 | ARM64/Bionic core and ABI boundaries | M03 | PLANNED | No x86-only execution path; strict core link and meaningful platform/serialization tests. |
| M05 | Android embedded Python and dependencies | M03, M04 | PLANNED | Actual interpreter, game-script and archive imports pass on AArch64. |
| M06 | Owned content import and native VFS | M03 | PLANNED | Bounded, resumable/staged import; native loader finds correct assets. |
| M07 | Native dedicated-server production lifecycle | M04, M05, M06 | PLANNED | Real map/rules load, simulation, shutdown/reload and rebind. |
| M08 | Client completeness and desktop regression baseline | M01, M02 | PLANNED | Strict client work list resolved for supported paths; original contracts verified. |
| M09 | Native renderer feasibility and architecture decision | M03, M04, M06, M08 interfaces | PLANNED | Real BF2 shader/mesh/texture rendered; backend decision documented. |
| M10 | Complete native scene renderer | M09 | PLANNED | Representative terrain, world, soldiers/vehicles, effects and HUD compositing. |
| M11 | Native client orchestration, deployment and UI | M04, M05, M08, M09 | PLANNED | All supported start/join/spawn/settings/menu flows work; no fake callbacks. |
| M12 | Native audio and focus handling | M04, M06, M11 | PLANNED | Audible positional effects/music and stable lifecycle. |
| M13 | Thor and external controller integration | M03, M11 | PLANNED | Complete rebindable gameplay/menu/vehicle contexts and hotplug. |
| M14 | First native playable match | M07, M10, M11, M12, M13 | PLANNED | Spawn, fight, capture, respawn, end/reload, reconnect; T1 physical test. |
| M15 | ARM64 AI image and initialization closure | M01, M04, M07 | PLANNED | Strict AI link, correct registry/interfaces, real init/update/teardown. |
| M16 | Baseline infantry bot matches | M14, M15 | PLANNED | Real navigation/combat/objectives/death/respawn across full rounds. |
| M17 | Baseline vehicle, aircraft and squad AI | M16 | PLANNED | Vehicle roles/routes and commander/squad interaction on qualified maps. |
| M18 | Enhanced bots and scheduling | M16, M17 | PLANNED | Documented optional improvements, measurable behavior and bounded CPU. |
| M19 | Local identity and transactional career store | M03; M01 design | PLANNED | Durable schema, migrations, event dedupe, recovery and ruleset identity. |
| M20 | Authoritative score, rank, award and unlock integration | M05, M07, M19; M16 for bot acceptance | PLANNED | Real gameplay credits exactly once; earned transitions affect gameplay. |
| M21 | Career UI and complete offline session flow | M11, M14, M16, M20 | PLANNED | Airplane-mode rounds retain scores/ranks/unlocks after cold restart; T2. |
| M22 | LAN/co-op gameplay and compatibility | M07, M14, M16 | PLANNED | Independent peers join/play/reconnect/rotate; no online-service dependency. |
| M23 | Trusted LAN career reconciliation | M19, M20, M22 | PLANNED | Host receipts credited once; offline continuation works; untrusted totals isolated. |
| M24 | Android lifecycle and recovery | M07, M11, M12, M13, M19 | PLANNED | Pause/resume, process death and surface/controller loss handled honestly. |
| M25 | Backups, restores and upgrade migrations | M06, M19, M20, M24 | PLANNED | Consistent exports, transactional restore and install-over-old save preservation. |
| M26 | Thor performance, memory and thermal qualification | M14, M18, M24 | PLANNED | Measured sustainable profile with useful bot count; T3. |
| M27 | Content/map/mod capability matrix | M06, M16, M17, M18 | PLANNED | Qualified maps/modes and AI assets listed with visible unsupported states. |
| M28 | Release pipeline and usable delivery | M03, M25 | PLANNED | Reproducible signed/versioned APK, symbols, checksum and verified download. |
| M29 | Full product acceptance | M14–M28 | PLANNED | T1–T4 passed, or named unsupported extras excluded explicitly; core goals cannot be waived. |
| M30 | Wider internet/community integration | M29, separate design | OPTIONAL / PLANNED | Explicit service/protocol/trust qualification; never assumed from LAN success. |

## Work packages and exit criteria

### M01 — Prerequisites without repeated user interruptions

- Inventory reference images by image/architecture/hash, original content version, game Python,
  navigation data and available IDA/Ghidra sessions. Include client, server, renderer, AI and UI
  images only as needed by the active recovery boundary. Record storage locations privately.
- Put DESIGN's proposed Android/AI/career architecture into the required linked discussion
  through the authorized owner workflow. Issues were disabled; do not claim that prerequisite
  is satisfied. Read existing relevant upstream issues before touching their behavior.
- Exit: a requirements matrix names each dependency's verified availability, owner and affected
  milestones. Missing recovery evidence blocks affected recovery, not independent documentation
  or build work. No request for a small device smoke is needed.

### M02 — Repair the inherited build baseline

- Reproduce the observed x86 `WorldPref::mTickCount` fixture link and x64 VOIP link failures
  on current source. Inspect target manifests and ownership; link the correct production/test
  definitions without inventing engine bodies. Keep CMake and matching batch manifests aligned.
- Capture x86 focused checks, x64 strict server and Linux/Python results on the exact commit.
  Run `bf2_client_linkcheck` and an AI unresolved-symbol audit; inventory symbol identities and
  reachable frontiers. These may remain blockers for later recovery rather than being hidden.
- Exit: inherited build regressions fixed with log evidence; separate client/AI recovery list
  and scope remain honest. Do not downgrade required checks, add `/FORCE`, or delete tests.

### M03 — Android packaging and toolchain

- Add an isolated Android application, pinned wrapper/JDK/SDK/NDK and explicit ARM64 CMake
  targets for the client, server, AI and Python dependencies. Preserve desktop presets. Set
  stable application identity and establish a documented signing/version strategy.
- Add CI that actually covers the chosen port branch. Start with two compiler jobs, isolated
  outputs and serialized plugin builds. Produce APK/build metadata and symbols from one SHA.
- Exit: clean CI build/install/load with no unresolved shared-library dependencies. ABI/API and
  page-alignment checks pass. This is an internal artifact, not a user gameplay milestone.

### M04 — Core portability

- Audit `Core`, `Math`, `Physics`, `IO`, entry points and plugin ABI for x86 assembly, pointer
  truncation, raw vtables, packing, signedness, long/wchar widths, C runtime and OS calls.
  Replace platform-specific mechanisms through typed seams, preserving known wire/file formats.
- Provide Bionic-compatible clocks, threads, locks, filesystem, sockets, dynamic loading and
  diagnostics. Audit the actual `rdtsc` caller contract before replacing it with a clock value.
- Exit: strict links; meaningful serialization roundtrips/golden bytes and ownership/lifecycle
  checks on native widths; relevant sanitizer tests pass. No suppression of known UB to claim parity.

### M05 — Python and third-party dependencies

- Derive a new Android/AArch64 `pyconfig.h` and source manifest for Python 2.3.4. Do not reuse
  x86-64 glibc assumptions. Preserve required legacy scripts, zlib and the existing ZIP fix.
  Audit required modules and intentional unavailable facilities explicitly.
- Compile compatible compression/XML/network/audio dependencies and record versions/notices.
  Keep native extension loading scoped to packaged components, not arbitrary imported binaries.
- Exit: initialize/finalize, callbacks, reference ownership, compressed standard-library and game
  imports, config paths and local UDP work under ARM64; no dependency on a host Python installation.

### M06 — Content import and VFS

- Support owner-selected directory/archive import with metadata, version and resource checks;
  stage to an app-readable layout or implement the URI/VFS bridge. Validate case sensitivity,
  archive order, loose overrides, scripts, map data, texture formats and SD-card access.
- Bound buffers, report progress/free space, cancel cleanly and recover interrupted imports.
  Avoid path traversal, duplicate-path ambiguity and archive expansion exceeding storage budgets.
- Exit: production loaders resolve a qualified stock map from imported content; cancellation,
  revoked access, low-space and malformed archives preserve the prior valid install.

### M07 — Real native server

- Adapt the production `BF2::init -> BF2Engine::startGame -> GameServer` path into an owned
  Android service/process. Implement explicit stop and error propagation to the launcher.
  Start with no bots and no public matchmaking; retain real local networking and scripts.
- Exercise map/rules initialization, ticks, ticket/capture state, unload/reload, thread shutdown
  and immediate port reuse. Never use port 16567 in automated tests.
- Exit: real content-backed production sessions pass repeated map cycles without leaked threads,
  listeners or increasing memory. A direct loader fixture or log line is insufficient.

### M08 — Client closure and compatibility baseline

- Audit strict client links and the startup-to-spawn dependency chain, including menu/profile,
  object/HUD, player-event and renderer host interfaces. Recover unknowns only with image evidence.
- Preserve the original desktop target while isolating chosen native presentation/platform
  changes. Retain named unsupported boundaries; remove harness overrides that shadow real bodies.
- Exit: every mandatory Android-play path has a real implementation and known interfaces; the
  corresponding strict target resolves symbols and regression tests pass. Record desktop runtime
  paths actually exercised separately from link results.

### M09 — Renderer decision gate

- Map the renderer interface and state/shader/material contract. Prototype a native surface,
  one actual BF2 mesh, a representative terrain/material shader, compressed texture fallback,
  depth/blend state and presentation. Use a bounded source fixture plus owner content privately.
- Compare correctness and complexity of a typed Vulkan backend with any credible alternate.
  Pin the selected route and shader tooling, including runtime/import conversion strategy.
- Exit: genuine BF2 render inputs produce correct geometry/materials, not just a triangle or
  clear color. Save capture, shader diagnostics and limits; no request to bless a decorative APK.

### M10 — Scene renderer

- Port terrain, static meshes, skeletal soldiers, weapons, vehicles, LOD/culling, lightmaps,
  shadows, water, particles, sky/fog, transparency and HUD compositing needed by qualified maps.
  Eliminate pointer-bearing x86 overlays from live ARM64 paths and manage resource lifetime.
- Handle shader cache invalidation, map streaming, dynamic buffers and surface loss. Identify
  any optional visual feature absent from the first playable profile in the capability matrix.
- Exit: reference scenes have correct transforms, animation, materials and depth; full maps load
  and unload repeatedly. Mandatory gameplay visibility cannot be waived as an optional effect.

### M11 — Client and menu flow

- Build the native client entry and attach it to actual game/session objects. Implement local
  soldier, map/team/kit/spawn, squad, settings, pause, scoreboard and return/disconnect flows.
- Choose and document Android presentation versus ported SWF components. Every active UI action
  must reach validated behavior, and unsupported actions must say so without crashing the session.
- Exit: automated navigation can start/join/deploy/end/rejoin and change supported settings;
  HUD state is real. No claimed success from unimplemented Swiff/profile callbacks.

### M12 — Audio

- Replace Windows-only audio device glue with a native backend and compatible codecs. Wire
  positional sources, listener, music, weapons, vehicles, explosions and looping emitters.
- Handle buffer sizes, focus, device changes, suspend/resume and bounded decode/mix work.
- Exit: test scenes verify source lifecycle and channels, then T1 verifies audible output;
  long sessions show no repeated underruns or accumulating voices. VOIP is a separate capability.

### M13 — Controls and docking

- Implement DESIGN's action mapping and all gameplay/menu contexts, calibration, rebinding,
  persistence and usable button prompts. Audit engine action names before selecting identifiers.
- Normalize trigger/hat variants, stick ranges, held-action release and controller connection
  changes. Preserve Thor and external presets and switch player 1 without clearing either.
- Exit: synthetic sequences cover chords/hold/context transitions and neutralization; T1/T3
  verify real analog feel, vehicles, docking and reconnection. Touch/keyboard remain available.

### M14 — Native playable vertical slice

- Combine actual server/client paths on one device, initially without AI if AI is still pending.
  Exercise deployment, movement, damage/fire, capture/tickets, death/respawn and real vehicles
  available on the test map, then round end, return, reload and reconnect.
- Exit: no Wine/PRoot/x86 dependency or missing-body abort on the supported slice. Agent-side
  runtime checks and artifact verification pass before requesting the T1 Thor session.

### M15 — AI image and core lifecycle

- Add the ARM64 AI library/image manifest and correct loader/registry ownership, independently
  of the engine manifest. Validate singleton interfaces and all init/teardown callbacks.
- Recover the reachable AI initialization, LOD, scheduler, event courier and bot lifecycle
  closure; `AILODManager::updateAllPlayers` is one concrete current blocker. Track reachable
  frontier names, not just a decreasing count.
- Exit: real AI initializes, advances ticks and clears/reloads repeatedly with strict linkage
  and no reached frontier. A loaded shared library alone is not an AI acceptance result.

### M16 — Infantry bots

- Load real navmesh/strategic data; spawn teams, sense enemies, select paths and objectives,
  move, attack, capture, die, revive where supported and respawn. Test disconnected navigation,
  obstacles and map reload rather than only stationary firing.
- Exit: at least 1 human + 7 bots completes three seeded production rounds on a qualified map,
  with reproducible event/capture/death records and clean teardown. Integrate with T2 career tests.

### M17 — Vehicle and strategic baseline

- Recover ground-vehicle pathing/occupancy, boarding/seats/turrets, air roles and squad/commander
  communications. Inventory maps with suitable vehicle/air navigation rather than forcing all maps.
- Exit: repeatable scenarios prove navigation, objective participation, vehicle switching,
  safe teardown and player orders. Aircraft takeoff/attack/return or landing must be demonstrated
  on a supported map; labels/menu settings are not evidence of functional aircraft AI.

### M18 — Enhanced bots

- Add the issue-discussed enhancements in DESIGN behind a named policy: role balance, objective
  priorities, team ratio, human replacement, squad orders, support actions and fair combat tuning.
- Add per-tick sensing/path/decision budgets, stuck diagnostics and bounded recovery. Compare
  baseline and enhanced scenarios with the same maps/seeds and report measurable outcomes.
- Exit: improvements work without changing unrelated retail behavior; 8/16/32-total-player
  candidates have measured behavior and cost. T3 selects the highest sustainable supported preset.

### M19 — Local career foundation

- Implement stable local identity, schema/versioned rules, single writer, migrations, unique
  event/match identities, transactional totals and an inspectable saved-state indicator.
- Inject failure around transactions, duplicate/reordered checkpoints, disk-full and DB reopen.
  Test rank boundaries and rule evaluator separately with synthetic fixtures clearly labelled.
- Exit: only committed events survive; retry cannot double credit; corrupted/unsupported data
  fails visibly while preserving recoverable prior state. No game runtime claim is made yet.

### M20 — Actual scoring, promotions, awards and unlocks

- Trace authoritative score producers and Python bindings. Implement the chosen event/checkpoint
  adapter and versioned rank/award rules; include team penalties and interruption policy.
  Keep final snapshots from crediting already counted totals.
- Use real gameplay scenarios for kills, assists, captures, support actions, deaths, wins,
  rank changes and an earned unlock. Evaluate upper-rank prerequisites explicitly for offline play.
- Exit: match summary equals credited deltas, notifications occur once and persisted unlocks
  change kit availability. Reconnect/reload/duplicate callbacks cannot reset or multiply progress.

### M21 — Offline career experience

- Integrate profile/career screens, progress to next rank, awards, unlock selection and match
  history. Show saved versus pending progress and interrupted versus completed rounds.
- Remove offline-mode dependence on external login/stats/master-server paths through an explicit
  local provider. Test with connectivity enabled while external egress is observed/blocked.
- Exit: T2 completes multiple actual bot rounds in airplane mode, cold restarts and continues
  the same soldier with identical committed score/rank/unlocks. No network fallback is required.

### M22 — LAN/co-op

- Provide direct join and local discovery with native peers; validate host/client lifecycle,
  settings/map compatibility, spawn and bots, packet parsing and reconnects. Avoid external
  authentication in the intentionally local mode without weakening separate online modes.
- Test independent processes/devices, dropped/reordered traffic, duplicate joins, graceful
  version rejection and map rotation. Record native/native separately from retail compatibility.
- Exit: two independent peers complete rounds and rejoin; malformed input fails safely. T4
  verifies actual Thor Wi-Fi/controller play where a second endpoint is available.

### M23 — LAN-to-offline career continuity

- Implement trusted-authority identities, scoped result receipts and ruleset compatibility.
  Persist received receipts before acknowledgement; import deltas transactionally and once.
- Test duplicate upload/retry, conflicting result hash, interrupted transfer, reconnect,
  untrusted host and two different matches with the same display name.
- Exit: a trusted co-op match updates the same career once, then airplane-mode play continues;
  untrusted server state cannot replace local totals or grant arbitrary unlocks silently.

### M24 — Lifecycle/recovery

- Test main/server process termination independently, screen off/on, activity recreation,
  surface loss, audio focus, controller loss, low-memory callbacks and service shutdown.
- Make pause/background behavior explicit; checkpoint before deliberate stop and recover
  interrupted sessions from durable career state without claiming saved world-state resumption.
- Exit: no stuck input, duplicate server, orphan listener, silent save loss or forced reimport;
  repeated cycles release resources and preserve every acknowledged checkpoint.

### M25 — Backup and upgrade durability

- Add streamed content/profile exports with scope/size estimates and cancellation. Back up the
  database consistently with its WAL and rules. Validate staged restore and preserve rollback.
- Maintain stable application/signing identity, monotonic versionCode and non-destructive
  schema migrations. Reject unsupported downgrade/import versions clearly.
- Exit: export/restore across installs and install-over-old APK preserve soldier IDs, totals,
  unlocks, bindings and content references. Reimporting the same backup cannot multiply scores.

### M26 — Sustainable Thor performance

- Profile render, simulation, AI, I/O, JNI, audio, memory and frame presentation separately.
  Use actual presented-frame timing rather than a menu/render-loop counter. Optimize measured
  bottlenecks; do not change simulation speed to inflate FPS.
- Initial targets: a 1280×720 internal resolution profile, a stable 30 FPS cap and useful 16-total
  participant bot play; 60 FPS and 32/64-total-player presets require separate measurement.
  See DEVICE-TESTS for proposed timing/endurance gates and budget-setting rules.
- Exit: T3 records a sustainable supported profile after thermal warmup with no OOM/ANR,
  unbounded memory or AI starvation. Lower-quality fallback remains playable and clearly labelled.

### M27 — Supported content matrix

- Inventory stock maps, sizes and game modes with infantry/vehicle/air navigation. Validate
  at least an infantry-focused and a vehicle/air map, map rotation and differing teams/kits.
- Add a compatibility manifest for content hashes, script versions, renderer features and AI
  data. Custom maps/mods are opt-in and evaluated independently; optional enhancements have
  versioned rules and do not silently mutate baseline assets.
- Exit: supported combinations have replayable evidence; missing AI data produces actionable
  messages. Do not claim all BF2 maps/mods work because one stock map loads.

### M28 — Build and release delivery

- Enforce strict native links, required fixtures, dependency/artifact checks and tested-source
  metadata. Create symbols and checksums alongside the APK; retain crash symbolization inputs.
- Prepare versioned release notes listing supported maps/bots/controls, known limitations,
  exact source SHA, upgrade steps and what changed since the previous test.
- Exit: under applicable publication authorization, deliver an APK that downloads without an
  expired Actions session; verify bytes/hash/install/version. Preserve the previous working build.
  No requirement to ask the owner to compile/install developer tooling on a PC.

### M29 — Product acceptance

- Execute T1–T4 with exact artifacts and the declared supported content/performance profile.
  Finish the critical regression list and audit unresolved frontiers reachable in those flows.
- Exit: install/import, controller-only offline bot rounds, vehicle/squad behavior, saved career,
  rank/unlock effects, restart/recovery, backup/upgrade and LAN/co-op continuity are demonstrated.
  Core goals cannot be reclassified as optional to close the milestone. Document remaining
  optional visuals, high-bot-count modes, VOIP or unsupported mods separately.

### M30 — Optional internet/community services

- Only after a separate design and authorization: assess external server discovery/accounts,
  service compatibility, NAT/firewall behavior, abuse handling and ranking trust requirements.
- Exit: demonstrated interoperability for the explicitly supported service. No automatic
  import/export of offline career scores into third-party competitive rankings.

## Completion log

### M00 — Assessment and planning

- Status: DONE, documentation only.
- Assessed source: `ccf30993258bfb9638db4aa7115c8a4c1a456e55`.
- Work: source/build/AI/input/stats assessment, dependency-ordered ledger, intentional design,
  autonomous continuation instructions and substantial device-test bundles.
- Evidence: source paths and upstream workflow/job links in ASSESSMENT; local documentation
  links and ledger consistency checked before the handoff push.
- Not tested: engine build, Android build, actual original images, runtime content, device FPS,
  bots, LAN, ranks, saves or APK installation. No implementation completion is implied.
- Next action: M01 inventory/design prerequisites and M02 inherited build repairs.

### Template for every subsequent checkpoint

```text
Milestone / status:
Branch / exact tested source SHA:
Change and reason:
Design issue or original-image evidence (image/hash/function/callers):
Files / affected images and manifests:
Tests actually run (command, environment, duration, result, artifact/log):
Production paths exercised:
Unexercised paths and remaining reachable frontiers:
Device / map / bot count / rules / APK hash, if applicable:
Blocker and smallest next action:
User test needed: none, or T1/T2/T3/T4 with prerequisites and instructions:
Commit/push/publication status and URLs:
```

Update this ledger and HANDOFF's current checkpoint as part of each coherent milestone change.
Add newly discovered submilestones (for example M10a) rather than hiding work or renumbering
completed IDs. Save evidence as bounded artifacts; do not commit full raw game data or logs.
