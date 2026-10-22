# Substantial AYN Thor test gates

The owner should not become the project's compile-test runner. There is no device test
needed for the initial assessment. Perform source, strict-link, fixture and available
emulator/runtime tests first, then bundle meaningful capabilities into the gates below.

## Before asking for any device test

- Build and qualify the exact commit; resolve relevant CI failures before delivery.
- Supply a direct verified APK download, filename, versionCode/versionName, SHA256 and signing
  identity. Confirm install-over-old behavior. Do not send an expired artifact URL as a release.
- State the supported content/map, what works, what is being tested and known limitations.
  Include a short start-to-finish checklist with expected visible results, typically 10–20
  minutes except the deliberate endurance gate. Provide one in-app export for evidence.
- Attach structured diagnostics: source SHA, build fingerprint, renderer/device/driver, map,
  content/rules hashes, controller identities, bot count, simulation tick timings, presented
  frame timings, peak/steady RSS by process, audio underruns and recent named frontier hits.
- Report native crashes/ANRs with symbols and bounded logs. Exclude account tokens, CD keys,
  passwords, arbitrary user files and unrelated network traffic from exported diagnostics.
- Keep prior working APK/profile backup available. Clearly label destructive fault tests and
  run them on an isolated QA soldier, not the owner's only career.
- A new user-facing test is justified by a meaningful capability bundle or a specific blocking
  device regression. Do not request repeated tests of the same unchanged commit.

## T1 — Native playable client, controls and presentation

Prerequisites: M03–M14 applicable gates, clean strict native links and agent-side session checks.
If baseline AI is already ready, include a small bot match rather than splitting the test.

1. Install, import/validate owned content, create a local soldier and start the supported map.
2. Use only the Thor controller for team/kit/spawn and all basic gameplay/menu actions.
3. Move and aim through 360 degrees; fire, reload, crouch/prone, jump, use equipment, enter and
   leave a vehicle, open the map/scoreboard, die/respawn and capture an objective.
4. Confirm audible weapons/vehicles, terrain/materials, player/vehicle models and readable HUD.
5. End/leave the round, reload and rejoin. Open/close settings without leaving input held.

Pass: real gameplay on the native client and on-device server, correct input/audio/presentation
and clean session cycling. At least 15 minutes without crash/ANR or reached mandatory frontier.
This does not yet qualify enhanced bots, sustained performance or saved career progression.

## T2 — Bot rounds and a persistent offline career

Prerequisites: M15, M16, M19–M21 plus the relevant lifecycle checkpoint/recovery implementation.
Use a dedicated QA soldier and ruleset for fast promotion/unlock tests; record its identity
and ensure accelerated thresholds are not enabled in the normal career.

1. Enable airplane mode, cold-start, select the QA soldier and play a full 1-human + 7-bot round.
2. Record starting career totals; earn a kill, capture and a supported team action. Observe
   bot movement/objectives/combat and at least one death/respawn. Check final round score.
3. Complete the round and verify saved delta, rank-up notification, earned unlock/award and
   the changed kit selection. Test legitimate negative penalties in an isolated fixture/scenario.
4. Completely close/restart the app while offline. The same soldier must retain committed
   totals, rank, selected unlock and history. Play another round; totals must accumulate once.
5. Use the supplied checkpoint scenario: terminate the QA session after a confirmed save,
   reopen and verify the durable totals. An interrupted round is identified and does not
   receive completion-only bonuses. Replaying a receipt/callback must not duplicate score.

Pass: multiple real rounds accrue into one durable offline career and unlocks affect play.
All acknowledged saves survive. Only the disclosed unacknowledged checkpoint interval may
be lost. Separately, agent-side egress tests with connectivity available confirm that offline
mode makes no external login/stats/master-server requests; airplane mode alone cannot prove it.

## T3 — Enhanced bots, vehicles, docking and endurance

Prerequisites: M17, M18, M24, M26 and candidate map profiles from M27; T1/T2 regressions pass.

1. Play an infantry and a vehicle/air map. Verify player-issued squad orders, useful support
   actions, vehicle boarding/combat, aircraft behavior and map capability reporting.
2. Run the candidate default 16-total-participant profile (1 human + 15 bots) for 30 minutes.
   Start timing after map load and initial shader warmup; record a separate loading timeline.
3. Inspect bot objective participation/stuck counts and simulation budget as well as FPS.
   Repeat a short 32-total-player candidate only if agent profiling supports it. A 64-total
   participant mode stays experimental until it passes equivalent tests.
4. Connect an external pad, switch player 1, and play using saved bindings. Disconnect and
   continue with the Thor. Exercise docking/resolution changes, suspend/resume and audio focus.
5. Export the one diagnostic bundle and a consistent QA career backup.

Proposed acceptance targets, to be frozen in the test plan before measuring:

| Measure | Initial target / interpretation |
| --- | --- |
| Presentation | 1280×720 internal render resolution, stable 30 FPS cap; actual presented frames measured. |
| Steady gameplay frame time | Median near 33.3 ms, p95 ≤ 40 ms and p99 ≤ 66.7 ms; separately report loading/shader stalls. |
| Long stalls | Explain and eliminate recurring >250 ms gameplay stalls; report individual outliers, not just average FPS. |
| Simulation/AI | p95 tick work within configured tick budget, no sustained tick backlog or permanently starved bot tasks. |
| Memory | Set aggregate client/server/cache and import peaks before the run using measured device headroom; no OOM, rising per-round baseline or unbounded queues. |
| Thermal endurance | Record ambient conditions, power/fan mode and identified sensor names; supported profile maintains the frame/tick targets after warmup. |
| Stability | No native crash, ANR, broken audio, stuck controls, corrupt saves or missing mandatory game objects during the 30-minute run and map rotation. |

These are engineering targets, not measured claims or a guaranteed 60 FPS result. If they
are missed, optimize or explicitly select a lower sustainable profile and retest that profile;
do not quietly redefine the thresholds after seeing results. Record settings changes between
runs and do not compare unrelated temperature sensors as one number.

## T4 — LAN/co-op, recovery, updates and release acceptance

Prerequisites: M22, M23, M25, M27, M28 and earlier test gates. Prepare a second independent
test endpoint; the agent should provide/test it where possible. Do not require the owner to
build server binaries or install tooling on a work computer. If no second endpoint exists,
record the exact missing coverage rather than claiming local loopback proves Wi-Fi multiplayer.

1. Two peers join a private LAN match with bots. Play a full round, reconnect one peer and
   rotate maps. Verify correct team/player identity and no duplicated participants.
2. A trusted match result updates the soldier once. Retry its delivery; score stays unchanged.
   Switch to airplane mode and continue the same soldier in another offline bot match.
3. Export a consistent backup. Install the candidate APK over the previous qualified version;
   content references, soldier ID, scores, ranks, unlocks and both controller profiles survive.
4. Restore into an isolated QA profile/install and verify equivalence. Attempt an invalid or
   too-new backup; the prior valid state remains intact and the error is actionable.
5. Exercise background/foreground, screen lock, surface recreation and a deliberate QA server
   process termination. Return to a recoverable menu/session with acknowledged career data intact.

Pass: the declared native LAN topology works, career continuity is demonstrated, upgrades and
restore preserve state, downloads are usable and all mandatory product requirements are met.
Retail-client or public-server compatibility remains unverified unless separately tested.

## Evidence and completion

Record each gate's artifact, device, duration, map, ruleset, participant count, exact checklist
outcomes, diagnostic hash and remaining limitations in MILESTONES. Link source and CI evidence
to the same SHA. A user saying the menu looks good cannot close bot, networking or persistence
milestones. A successful port release requires actual functional outcomes, not only screenshots.
