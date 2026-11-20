# Live offline ticket regression

`tickets.py` loads legally supplied BF2 1.50 dedicated-server content in a real
headless engine with its matching Python and AI libraries. Stock game-mode
scripts remain unchanged. The existing local-human fixture is described in
`LAYOUTS.md`; compile its current `local_human_module.cpp` rather than reusing an
older experimental extension. The QA executable needs `-Wl,--export-dynamic`.

For Linux testing, compile the production `ServerGameLogic.cpp` with
`BF2_OFFLINE_TICKETS_TEST=1`. Android selects the same ticket code normally.
`relink.py --tu Code/BF2/Game/GameLogic/ServerGameLogic.cpp --define
BF2_OFFLINE_TICKETS_TEST=1` can do this against a compatible base QA build.
It records source/object hashes and leaves the original binary untouched.

Compile the small action extension from the matching checkout:

```sh
c++ -shared -fPIC -std=gnu++14 -DDEDICATED=1 -D_LINUX -DBF2_EDX_PARAM= \
  -fms-extensions -fpermissive -I. -ICode/BF2 \
  -ICode/BF2/External/python-embedded/linux \
  -ICode/BF2/External/python-embedded/py-src-2.3.4/Include \
  -include Code/BF2/Core/BuildConfig.h -include Code/BF2/Core/PlatformCompat.h \
  Code/Tests/offline_coop_runtime/ticket_actions.cpp -o /tmp/ticket_actions.so

python3 Code/Tests/offline_coop_runtime/tickets.py \
  --binary /path/to/qa/bf2 --libs /path/to/matching/native \
  --base-game /path/to/stock-1.50-server \
  --human-module /tmp/layout_human.so --actions-module /tmp/ticket_actions.so \
  --tickets 456,789 --expect 456,789 --out /path/to/new-results
```

The extension calls the real local-player `gameLogic->suicide()` action used by
the HUD. It does not substitute a ticket method or manufacture a Python death
event. The probe requires an observed PlayerDeath and a subsequent team ticket
deduction. It then sets a controlled negative rate through the existing Python
API and checks that the native timed ticket update reduces the total. Finally,
setting tickets to zero must cause a real EndGame transition, the next round
must restore the selected totals, and `admin.restartMap` must restore them again.
Initial warning limits are checked as well, including the stock script's team-2
20% threshold using team-1's total. Transient Playing/PreGame pairs during reset
are allowed to settle before sampling the start.

For a supplied authored layout, add `--authored-levels /path/to/Levels --size 64`.
The private game root receives that map's server archive; this verifies authored
layout gameplay, not the Android pack-import UI or overlay selection. The test
asserts the native exact-layout marker and records input and executable hashes.
It copies mutable files and hard-links only immutable archives, never modifies
original content, and terminates only its own child. Use fresh output directories
and separate ports for concurrent runs; reserved port 16567 is rejected.

Verified native x86-64 runs using stock scripts and a genuine local fixture human:

| Content / mode | Requested team 1 / team 2 | Start and both resets | Death | Timed bleed |
| --- | --- | --- | --- | --- |
| Stock Karkand co-op16 | 456 / 789 | 456 / 789 | team 2: 789 → 788 | team 1: 456 → 452 |
| 20YA v1.3 Karkand co-op64 | default / 234 | 100 / 234 | team 2: 234 → 233 | team 1: 100 → 96 |

Co-op's existing native default lookup uses the map's 16-player ticket defaults
even on an authored 64-player layout. This behavior remains intact for an omitted
team. Blank/default settings are independent, and `sv.ticketRatio 100` makes
explicit totals exact. The harness also accepts `--scope lan` or `wrongmap` for
session-isolation checks, and raw invalid environment values for fallback checks.
When either initial total is at most 10, it tests zero/end/reset without the death
and bleed phases, which could otherwise end such a short round immediately.

This is real server/script lifecycle coverage, not a graphical Android match or
a claim about handheld performance. Exact signed-APK ticket getter/session checks
are separately covered by `Code/Tests/android_apk_runtime/tickets.py`.
