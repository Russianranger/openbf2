# Exact offline co-op rosters

The launcher uses `GameLaunchPlan.forCoop(..., level, team1, team2)` and permits
0–63 bots combined, reserving the 64th player slot for the human. These are map
team IDs, not allies/enemies: stock Karkand team 1 is MEC and team 2 is US.
The old overload retains 8/8 as its default.

The Android entry validates both integer arguments, writes the ordinary co-op
count/ratio settings, and enables exact team quotas only for its local co-op
session. The ordinary dedicated/LAN setting still retains its original 1–48
clamp. Bot quotas count AI players only, so the human's selected side does not
reduce either quota. No replicated object or AI plugin interface layout changes.

`run.py` runs the real dedicated server and AI plugin with stock Karkand server
assets. It waits for a live round, queries the real GameSpy player table, checks
every player's team and the engine's live player total (including zero), and
requires the exact roster to remain stable for at least
12 seconds. Its default cases cover 0/0, both one-sided configurations, an uneven
split, 31/32 and 63/0. It verifies the loaded AI plugin path and records SHA-256
hashes and every sampled roster. It stops only its own child process and creates
then removes private configuration files. Default test ports start at 18667;
the reserved 16567 production listener is rejected.

Example, with an ARM64 server built from these production sources and existing
Android 13/Bionic runner/runtime plus legally supplied BF2 dedicated content:

```sh
python3 Code/Tests/offline_coop_runtime/run.py \
  --runner /path/to/android-run --binary /path/to/bf2 \
  --game /path/to/server/stock --libs /path/to/native --out /path/to/results
```

`BF2_AI_SPAWN_WITHOUT_HUMAN=1` is the existing headless harness option; the real
launcher does not set it. Human input and graphics are outside this test's scope.
The explicit launcher population is applied after `AIDefault.ai` and menu settings
are read, so an imported AI configuration that overrides the retail menu cannot
silently reduce the requested total or count the human against a bot quota.
Other AI behavior settings retain the content's values.

`android/thor/tests/LaunchPlanTest.java` independently checks all 2,080 legal
selections, malformed bounds (including integer extremes), the 8/8 default, and
LAN argument isolation. The native Android entry is compiled in the normal client
build; the dedicated runtime exercises the same quota and server-settings code.

For incremental checks against an existing compatible dedicated build,
`relink.py --source /path/to/engine --build /path/to/server-build --out /path/to/check`
recompiles the four changed production translation units using that build's
compiler flags, then links its unaffected objects into a private executable. It
records every command, source hash, object hash and resulting binary hash in
`build.json`. It does not alter the base build. This requires the same engine/AI
ABI; it is a focused runtime regression, not a claim that an older server build
is the production Android client. The full Android client must still be built.

Validation on Android 13 Bionic under AArch64 QEMU (stock Karkand 16 co-op):

| Content population settings | Requested team 1 / team 2 | Result |
| --- | --- | --- |
| Stock | 0/0, 0/5, 7/0, 3/5, 31/32, 63/0 | All exact live rosters passed |
| Private AIDefault override: menu override on, max bots 1, humans included, auto-spawn off | 0/0, 0/5, 31/32 | All exact live rosters passed |
| Unchanged server, stock content | 0/0 | Correctly failed: live engine contained 1 bot |
| Before the GameLogic population override, private content | 0/5 | Correctly failed: live engine contained 0 bots |

Both `BF2.cpp` and `GameLogic.cpp` compiled with the normal ARM64 client build
flags. Java launch-plan validation passed all 2,080 allowed combinations. These
checks establish population/configuration behavior; they do not measure 63-bot
rendering performance on the handheld.

`launch_args.py` extracts the unchanged production `androidCommandLine` and
`appendCommandLineArg` functions from `BF2.cpp` and runs them under ARM64/Bionic.
Its platform stand-ins supply only the app's storage path and unused crash/GPU
setup; actual argument parsing, environment changes and configuration writes run
unchanged. All 56 checks passed: selected teams, total count, one human slot,
co-op/AI flags, invalid inputs and clearing offline scope when hosting LAN.
Run it with `--source`, `--out`, `--compiler` (NDK ARM64 clang++) and `--runner`.
The final null-guarded source also passed another real 0/5 roster check with the
private overriding AI content.
