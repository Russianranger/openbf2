# OpenBF2 Android / AYN Thor handoff

Assessment date: 2026-10-21 (America/Chicago).
Assessed engine baseline: `ccf30993258bfb9638db4aa7115c8a4c1a456e55` on `Russianranger/openbf2/main`.

## Goal and current state

Deliver a native ARM64 Android app for the AYN Thor that runs a playable BF2-compatible
client and local server, supports the built-in and external controllers, provides capable
infantry/vehicle bots, and retains multiplayer-style scores, ranks, awards and unlocks
through offline sessions. Add LAN/co-op using the same career where the server is trusted.
Offline play must not require an account service, DNS, a PC, root, Wine or PRoot.

The repository is an incomplete engine reconstruction, not an existing Android game.
No Android implementation milestone is complete. This handoff is a source and CI assessment;
no APK, local engine build or gameplay test was performed. See [assessment](ASSESSMENT.md).

The owner requested this assessment and authorized pushing the handoff to this fork. They
want implementation work to proceed with routine decisions made autonomously and only
substantial device tests requested. This document records the intended workflow; it does
not grant permissions beyond the owner's applicable instructions or claim future work ran.

## Read on every continuation

1. Read [AGENTS.md](../../AGENTS.md) completely, then this file and the current ledger in
   [MILESTONES.md](MILESTONES.md). Read the relevant design and source, not the entire tree.
2. Fetch the actual remote branch head. Record its SHA, `git status --short`, branch,
   existing work, active CI and changed handoff entries. Never restore this old baseline
   over newer work. Preserve other work and monotonically increasing app versions.
3. Read [development](../development.md), [building](../building.md), and the affected
   [architecture](../architecture.md) sections. Identify the image and explicit manifest.
4. Pick the earliest dependency-ready milestone. Check its acceptance criteria and blockers.
   Finish a bounded, useful implementation slice, validate it, and persist the result before
   advancing. Keep independent work moving when one recovery dependency is unavailable.
5. Follow [DESIGN.md](DESIGN.md) and [DEVICE-TESTS.md](DEVICE-TESTS.md). They contain proposed
   intentional designs, not claims of recovered BF2 behavior.

## Prerequisites and authority

- This assessment changes documentation only. It leaves canonical agent instructions intact.
- For recovered engine behavior, `AGENTS.md` requires accessible IDA/Ghidra with the matching
  original 1.50 images and direct instruction/caller inspection. No such session was available
  during this assessment. Source comments are not a substitute. Obtain the evidence before
  recovering any missing body, signature, layout or protocol transition.
- Intentional non-retail implementation requires the pre-existing linked design discussion
  specified by `AGENTS.md`. GitHub Issues were disabled in this fork at assessment time.
  [DESIGN.md](DESIGN.md) is an issue-ready proposal, **not a claim that the issue gate is met**.
  Resolve that prerequisite through the owner's normal workflow; do not silently enable repo
  settings, weaken the rule, or present a documentation commit as an approved issue discussion.
  Documentation, inventory and independent build maintenance may proceed meanwhile.
- Agents must not open PRs or answer reviewers under the current repository policy. The owner
  expressly authorized this handoff push. For subsequent implementation, use the branch/push
  authorization actually present in that session; otherwise prepare validated local commits
  for the owner. Do not infer authorization to publish releases, change repo settings or merge
  into upstream from this assessment request.
- Expected external inputs are an owner-supplied BF2 1.50 content installation, compatible game
  Python scripts and AI map data, plus reference images for recovery. Do not commit those
  assets, accounts, keys, private captures or disassembler databases. Inventory available
  inputs first and consolidate any genuinely unavailable prerequisites into one precise request.

## Architecture decisions to carry forward

- Native Android client and native server share reviewed source but run in separate Android
  processes to isolate engine globals and lifecycle ownership. The server owns authoritative
  match events; one career writer owns durable local progression.
- ARM64/Bionic targets are new targets. Linux x86-64 support does not establish Android support.
- Use a bounded renderer feasibility milestone, then one production backend. Vulkan is the
  proposed first candidate. D3D9/D3DX semantics, shaders, texture formats and 32-bit renderer
  overlays require explicit migration. A clear-color screen is not a rendered BF2 map.
- Port baseline bots before adding advanced behavior. Increasing a bot-count setting does
  not implement pathfinding, squad logic, vehicles or the AI scheduler.
- Use a local, transactional career store with versioned rules and idempotent match accounting.
  Offline and trusted LAN/co-op may share that career; third-party competitive rankings do not.
- Preserve existing desktop fidelity targets and protocol/file-format widths. Port internals
  may use native pointers and typed ownership. Never force x86 layouts onto AArch64.
- A Wine/translation experiment, if independently justified and authorized, is a separate
  compatibility track. It cannot satisfy a native-port milestone or replace missing AI bodies.

## Immediate next implementation batch

1. M01: inventory evidence/content/design prerequisites and record exactly what exists.
2. M02: repair and rerun the inherited baseline gates. The inspected upstream run has an x86
   `WorldPref::mTickCount` fixture link failure and six Windows x64 VOIP unresolved symbols.
   Inspect current manifests before changing them; do not hide the failures with stubs or `/FORCE`.
3. M03: once the intentional-design prerequisite is satisfied, establish the isolated Android
   project, pinned toolchain, explicit ARM64 source manifests and CI artifact metadata.
4. Start dependency-ready M04/M05 platform and Python work and M08/M09 client/renderer assessment.
   Do not ask the owner to install a shell-only APK. Keep it as a CI artifact.

This is a suggested batch, not permission to skip prerequisite gates or to overwrite newer work.

## Autonomy, memory and durable progress

- Prefer a dedicated `android-thor` integration branch for future code, created from the actual
  current `main`; record any different owner-selected branch. This documentation push does not
  create that branch. Current CI only auto-runs on pushes to `main`, so add intentional branch
  coverage or explicit dispatch before depending on CI for a port branch.
- Use one build owner and at most two compiler jobs initially. Serialize the current Windows
  client/plugin builds because their output paths are shared. Use unique build directories;
  never delete or reuse another task's outputs. If memory pressure appears, reduce to one job.
- Do not start concurrent full build/sanitizer matrices on the same host. Split fixtures by
  subsystem, save full logs to artifacts, and report only the first actionable failure and links.
  Do not paste recursive trees, entire headers or complete logs into the working conversation.
- Before a long compile, save the intended change, exact command, log path and next action.
  Check resource use and progress at bounded intervals. A run with no new output is not by
  itself proof of a stall; inspect process/CI state before restarting anything.
- Commit a coherent slice and update the ledger after each validated milestone or useful
  blocked checkpoint. Use only authorized pushes. Do not wait until the end of a huge session.
- Use the tested commit's artifacts; never rebuild an old tag and label it a newer version.
  Record source SHA, APK hash, version, signing-certificate fingerprint and test provenance.
- Every milestone entry must distinguish source audit, compile, strict link, fixture,
  emulator, production runtime and physical Thor evidence. Missing evidence stays missing.
- Do not stop merely because a device gate is pending. Continue independent testable work.
  Ask for device testing only when a bundle in DEVICE-TESTS is ready and its prerequisites pass.

## Completion and next-session report

Use the completion log template in MILESTONES. Keep this section short when updating it:

- Last completed: M00, assessment and implementation plan; documentation only.
- Active: none; no implementation work started by this assessment.
- Next: M01 prerequisite inventory and M02 baseline build repairs.
- User test needed now: none.
- Engine/AI/device claims: unverified beyond the source and upstream CI evidence in ASSESSMENT.
- Delivery target: all required M01–M29 gates, with the actual device coverage recorded.
- Optional later work: M30, wider internet/community interoperability; not an MVP dependency.
