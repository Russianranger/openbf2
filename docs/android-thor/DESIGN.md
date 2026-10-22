# Proposed Android, controls, bots and career design

All new policies in this document are **[CHOSEN, not recovered]**. This is the design brief
to put into the discussion required by `AGENTS.md` before intentional implementation. It
does not establish original signatures or satisfy that discussion prerequisite by itself.

## Product contract

Primary target: owner's AYN Thor, Android 13, Snapdragon 8 Gen 2 / Adreno 740, ARM64.
Record actual OS, RAM, display, driver, storage and controller identities in the first device
report rather than assuming every unit is identical. The MVP uses the main game display;
second-display telemetry, gyro and rumble are optional refinements after core qualification.

First-run flow: select owned game data, validate/import it, create a local soldier, choose
an offline match, play and see the retained career. The normal screen exposes Play Offline,
LAN/Co-op, Career and Settings. Proven renderer/runtime defaults are automatic; diagnostics
and unusual tuning live in advanced settings. Do not make the player assemble runtimes.

The minimum playable loop includes team/kit/spawn selection, movement/aim/fire, damage,
death/respawn, capture/tickets, vehicles on the qualified maps, round end, map rotation and
return to menu. Full bot capability and durable progression are required product features,
not placeholders labelled complete after a map viewer.

## Native processes and build boundaries

- Android launcher/activity plus a native client library. Prefer GameActivity for native
  lifecycle and input integration after validating it against the engine's entry needs.
- A separate `:server` app process hosts the native server, game scripts and native AI.
  This avoids placing two copies of engine globals in one runtime. Package code as built
  native libraries in the APK; do not depend on executing imported binaries from shared storage.
- Package native Python and shared dependencies built for the same ABI/toolchain. Keep
  `DEDICATED=0/1`, `BF2_IMAGE_AIDLL` and explicit source ownership intact. Do not use a glob
  that accidentally links an AI DLL entry point into the engine.
- The application starts/stops only its own server. Use explicit loopback addresses and an
  unused configured port for offline play. Agent automation must never use reserved 16567.
  User-selected LAN hosting enables the appropriate network interface deliberately.
- One writer owns career transactions. The client requests read models; gameplay is the
  source of authoritative events. Avoid competing database writes from activities, callbacks
  and server ticks. Define explicit session/process IDs and lifecycle acknowledgments.
- Use one pinned NDK/Clang, JDK, Gradle and Android plugin combination. Keep a documented API
  floor compatible with Android 13 and verify all packaged libraries and page alignment.
  Build desktop regression lanes separately; do not use retail class-layout checks as ARM64
  ABI checks. Preserve wire/file integer widths regardless of native pointer size.

## Renderer and native client

The proposed renderer direction is Vulkan through typed engine render interfaces. M09 must
first prove a real mesh/material and representative BF2 shader, texture and depth semantics
on an ARM64 Android surface. Evaluate a translation backend only if its native integration,
dependency footprint, correctness and measured performance are better. Record the decision
and implement one production route before supporting multiple backends.

Inventory D3D9 state transitions, D3DX effects/permutations, vertex declarations, skinning,
terrain layers, lightmaps, shadows, particles, fog, water, UI and texture-atlas dependencies.
Make projection/depth conventions, matrix order, winding, packed vertex data and alpha/blend
semantics explicit. Migrate raw x86 offsets and vc71 container views to typed native ownership
at their source boundaries; a 64-bit renderer must not reinterpret old pointer-bearing blobs.

Inspect sampled texture formats on the actual Adreno driver. Supply a bounded conversion/cache
path for unsupported imported DDS compression, with mips and alpha preserved. Compile/cache
shaders with content, compiler and backend version keys; handle failed compilation visibly.
Shader/asset conversion must be possible through the app or prepared tools, not a mandatory
Windows workstation. Restrict loading work and uploads so import and map loads have bounded RAM.

Separate Android menu/career overlays may replace desktop menu presentation intentionally,
while reusing validated gameplay/session interfaces. Preserve the in-match information needed
for deployment, squads, health, ammo, tickets, map, comms and scoreboard. Do not call a full
SwiffPlayer port mandatory if a documented native UI implements the complete supported flow;
do not bypass an unknown gameplay callback by fabricating success.

Port audio to a native output path (evaluate OpenAL Soft or an appropriate native mixer).
Validate spatial mixing, attenuation, loop lifecycle, codecs, buffer underruns and focus changes.
Treat intro-video playback and Novint hardware as explicit optional capabilities; essential
assets/audio must not silently disappear. Keep desktop behavior intact behind platform seams.

## Controller contract

Use Android's controller APIs/Game Controller library to normalize hardware, then feed
semantic engine actions through `ControlMap`/`PlayerInput`. Native analog movement and
time-scaled look are preferred over per-frame synthetic mouse deltas. The input sample has
one consumer so events are not delivered through both the activity and view path.

Proposed infantry defaults use Xbox-style logical labels; show the actual detected labels.
Everything is rebindable, including press/hold actions, without editing configuration files.

| Control | Proposed infantry action |
| --- | --- |
| Left stick | Analog movement |
| Right stick | Aim/look with independent horizontal/vertical sensitivity |
| RT / LT | Fire / aim-down-sights |
| A | Jump; context parachute action when airborne |
| B tap / hold | Crouch / prone, with explicit conflict-free hold timing |
| X tap / hold | Reload / interact or enter-exit vehicle |
| Y tap / hold | Cycle primary/secondary / equipment selection wheel |
| LB | Quick grenade action, configurable |
| RB tap / hold | Spot target / squad and command wheel |
| L3 / R3 | Sprint / knife selection |
| D-pad | Four configurable equipment, fire-mode or kit actions |
| Select tap / hold | Map / scoreboard |
| Start | Pause/settings; in multiplayer, menu only |

Provide distinct Infantry, Ground Vehicle, Aircraft/Helicopter, Gunner, Commander and Menu
contexts. A menu context uses focus navigation, confirm/back and optional pointer mode.
Aircraft mappings expose pitch/roll/yaw/throttle with inversion and analog-trigger calibration;
ground vehicles expose steering/throttle/brake and independent turret aim. Context transitions
release prior actions before activating the next map. Radial menus must remain usable without
a keyboard and cannot leave firing/steering held when dismissed.

Support radial deadzones, anti-drift calibration, response curves, ADS sensitivity, invert-Y,
trigger thresholds, hold/toggle options and clear reset-to-default. Save profiles by stable
hardware identity and mapping version, not transient Android device ID. A newly connected
external controller can become player 1 while keeping Thor bindings saved; disconnecting it
restores the Thor without clearing/rebinding. Neutralize held actions on disconnect, focus
loss, pause, surface changes and device swaps. Unknown devices get a capability-based fallback
and a local calibration UI. Docked resolution/safe areas and button prompts update together.

## Baseline and enhanced bots

Preserve the reconstructed baseline until its behavior and shutdown paths work. Establish
the reachable dependency closure for AI init, map/navmesh loading, spawn, senses, decisions,
movement, attacks, death and teardown. Existing frontier aborts cannot be silenced to admit
more bots. Reference images and owned game AI data are prerequisites for recovery.

After that baseline, add optional versioned enhanced-bot policies:

- Population: configurable total participants, bot slots, team ratio, difficulty and bot
  replacement when humans join/leave. Define clearly whether the slider includes the human.
- Tactical play: objective selection, defend/capture, squad cohesion and player-issued orders;
  useful medic revival/healing, support resupply and engineer repairs where supported.
- Combat: perceptual limits, reaction time, aim error and burst behavior. Difficulty must not
  rely on hidden perfect knowledge or unlimited shots through occlusion.
- Movement: queued path searches, infantry/vehicle traversal, occupancy handling, stuck
  detection and bounded recovery; map capability checks before offering an AI mode.
- Vehicles: boarding/seat assignment, driving, weapon use, safe dismount, aircraft navigation,
  takeoff/landing where the map provides the required data, and team-role balancing.
- Scheduling: explicit per-tick work budgets for sensing, planning and pathfinding; fair
  queues so far-away bots are not indefinitely starved. Profile decisions instead of merely
  multiplying update frequency. Keep simulation time independent of renderer FPS.

Start qualification with 1 human + 7 bots, then 1 + 15 and 1 + 31. A 64-total-participant
profile is an experimental stretch goal until measured stable. Increasing capacity must not
silently change scoring mid-round. Record seed, map, rules, AI version and work-budget settings.
Support optional enhanced rules without requiring third-party mod packs for the baseline.

## Durable offline career

Use an app-private SQLite database (or a demonstrably equivalent transactional store), one
writer, a bounded queue and explicit schema migrations. The UI always distinguishes current
round score from saved career score. Profile names may change; immutable soldier IDs may not.

Proposed logical records (types/keys finalized in the issue discussion):

| Record | Essential fields and invariants |
| --- | --- |
| Soldier | Stable UUID, display name, creation time, schema/rules compatibility. Local identity does not require a GameSpy password. |
| Ruleset | ID/version/hash; score weights, rank thresholds/requirements, award predicates, unlock catalogue, eligible modes. Immutable once a match uses it. |
| Match | UUID, authority ID, map/content hash, mode, AI/rules versions, seed, start/end, outcome and completion state. |
| Participant | Match ID + soldier/bot ID; stable participant identity survives reconnect. Bots never inherit the human career. |
| Event/checkpoint | Match ID + sequence + participant, type, bounded payload, monotonic simulation time; durable unique key prevents replay/double credit. |
| Career totals | Materialized counters derived from credited authoritative events; signed penalties and wide integer counters preserved. |
| Award/unlock | Soldier ID + award/unlock ID + version; recorded grant with prerequisites and source match. Selection is durable and idempotent. |
| Import receipt | Authority + match + participant + ruleset hash + result digest; duplicate and conflicting results handled explicitly. |

Trace actual game/Python scoring producers for kills, deaths, team kills/penalties, captures,
defense, assists, healing, revives, repair, resupply, squad/commander contributions, round wins,
play time, kit/weapon/vehicle counters, accuracy and medals. Record which are implemented and
which BF2 content scripts supply them. Do not infer the authoritative source from a HUD label.

Choose one accounting path: incremental authoritative events with unique sequence IDs, or
monotonic authoritative checkpoints converted to deltas. Never add both a running snapshot
and the full final snapshot. Test negative adjustments, overflows, reconnects and duplicate
callbacks. Mutating the UI or editing a local rank label is not gameplay integration.

At durable checkpoints, commit credited events and derived totals atomically. The initial
target is a checkpoint at most every five seconds plus clean round-end/exit flushes, subject
to measured storage cost. After process death, all acknowledged durable checkpoints remain;
at most the unacknowledged interval can be lost. Mark the match interrupted and withhold
completion-only win/award bonuses. Resuming an identical world state is not required for
career persistence. The app must never imply that an uncommitted checkpoint is saved.

Ship a versioned BF2-style career ruleset whose thresholds, time/award prerequisites and
unlock costs are verified against available content/reference behavior. Do not invent exact
retail thresholds in advance. Where original upper ranks depended on global leaderboards,
define an explicit achievable offline substitute. Show that difference in the ruleset and
career UI. Optional faster progression is a separate named ruleset, never an unnoticed
change to an existing career. Fixture-only accelerated rules must not leak into releases.

Earned unlocks must affect permitted kit selection on the next spawn and remain present
after a cold restart. Rank-up notifications must derive from the committed transition and
not replay every launch. Backups include rules/version metadata, soldier identity and the
transactionally consistent database. Use SQLite backup/checkpoint facilities, not a blind
copy of an open main file while its WAL is still live. Stage and validate restore before
switching, keep rollback, and distinguish replace from merge. Never sum imported totals.

## Offline, LAN and internet boundaries

Offline mode starts directly using the local provider and must issue no external stats,
login, master-server or telemetry requests, including while Wi-Fi is enabled. Test both
airplane mode and a network sandbox/capture; absence of connectivity alone does not prove
absence of network attempts. Loopback game traffic is expected.

LAN discovery/direct join is a separate user-selected mode. Qualify native-to-native first;
retail Windows 1.50 interoperability is a separate matrix and must not be assumed. Preserve
known retail packet widths/events when compatible; version any intentional extension and
negotiate capabilities without sending unknown packets to older clients.

The local career can credit matches hosted by this app and explicitly trusted co-op hosts.
A peer server is authoritative for its match, not for arbitrary lifetime totals. Return a
versioned result receipt with host identity, match/player IDs, score deltas, ruleset hash and
digest. Pin/trust a host identity before importing results and validate it on reconnection.
Signatures/digests can detect tampering/replay but cannot make a dishonest host honest.
Untrusted/public-server results remain in a separate history and cannot overwrite the
offline career. Interrupted transfers retry idempotently. Do not automatically upload or
merge the career into third-party rankings.

## Lifecycle, storage and delivery

Use Android's document picker for owner-selected source directories/archives and exports.
A document URI is not a POSIX path: stream into an app-managed content store or implement a
tested provider-backed VFS. Persist grants, detect revoked access, preflight free space,
normalize paths/case carefully and stage imports atomically. Reject traversal, oversized
expansion, symlink escapes and conflicting duplicate paths. Never unpack a multi-GB archive
fully into memory. Keep immutable content, derived caches, saves and config in separate roots.

Handle surface recreation, audio focus, lock/unlock, controller hotplug, server death, low
memory and storage exhaustion. Offline pause/background policy should checkpoint and pause
or stop deliberately. LAN hosts need an explicit running-server lifecycle and an appropriate
foreground service while active; the app must not promise indefinite survival after force-stop.
Observe Android's service restrictions instead of requiring root or disabling OS protections.

Release artifacts need monotonically increasing versionCode, stable application ID and signing
identity, source/build metadata, dependency notices, APK SHA256 and a retained symbols archive.
Install-over-old-version and career migration are mandatory acceptance tests. Content and
private profile data stay outside source/binary release artifacts. Deliver stable downloadable
assets and verify the actual download, not just the presence of an Actions artifact URL.

## Official Android references

Consulted during assessment; pin concrete dependency versions when implementing M03.

- [GameActivity](https://developer.android.com/games/agdk/game-activity/get-started)
- [Game Controller overview](https://developer.android.com/games/sdk/game-controller/overview)
- [Controller lifecycle and events](https://developer.android.com/games/sdk/game-controller/controller)
- [Controller testing](https://developer.android.com/games/sdk/game-controller/testing_controller)
- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [Native library page sizes](https://developer.android.com/guide/practices/page-sizes)
