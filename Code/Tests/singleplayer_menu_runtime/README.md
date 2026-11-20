# First-run Singleplayer menu regression

The stock BF2 1.50 first-run Singleplayer button sets `noobieSP=1`, creates an
offline profile, and calls `createDefaultTraining`. That helper inserts the
startup map and calls `Multiplay.createServer` immediately. Its normal
`trainingCreate` page already contains the map picker and explicit Start action.

The Android bridge intentionally replaces only this first-run helper with map
selection. It recognizes the existing `noobieSP` storage exchange and verifies
that the stock navigation/settings functions are present. Both the initial
button and movie reload after a profile transition are covered. The generic
server-start binding and direct Android Deploy path are unchanged.

## Run

Supply your installed BF2 1.50 `Menu_client.zip` or its `mainMenu.swf`:

```sh
python3 Code/Tests/singleplayer_menu_runtime/prepare_stock.py \
  /path/to/Menu_client.zip /tmp/menu-flow.swf
cmake -S Code/Tests/singleplayer_menu_runtime -B /tmp/menu-runtime -G Ninja \
  -DCMAKE_BUILD_TYPE=Release
cmake --build /tmp/menu-runtime --parallel 2
/tmp/menu-runtime/singleplayer_menu_runtime /tmp/menu-flow.swf --baseline
/tmp/menu-runtime/singleplayer_menu_runtime /tmp/menu-flow.swf
python3 Code/Tests/offline_coop_runtime/launch_args.py \
  --source "$PWD" --out /tmp/menu-launch --compiler g++ --runner /usr/bin/env
```

The extractor copies original ActionScript function/action bytes into a private
headless fixture. It preserves separate constant pools and rejects ambiguous
matches. It downloads nothing; neither the generated movie nor any game assets
belong in this repository. The 1.50 movie used during validation had SHA-256
`2a41749716493d0418823f81ec06da78b025efa859df473986d2ca2952def684`.

The fixture compiles the actual gameswf VM and production menu adapter. It runs
the stock first-run function, navigation, map-selection handler, and explicit
Start button action. Baseline execution demonstrates immediate Gulf of Oman
startup. With the adapter, it verifies profile creation, arrival at the map
picker without hosting, navigation away, choosing Dragon Valley without
hosting, one start after explicit Start, and the profile-reload path. The argv
regression exercises the production Android parser: menu entry clears previous
co-op/direct-start state while existing direct Deploy behavior still passes.

Engine settings, profile creation, and host-server calls are observers in this
focused fixture; the stock deferred trigger scheduler is drained immediately.
Layout, list rendering, thumbnails, and other decorative clips are omitted.
This is a runtime script/bridge regression, not a full APK screenshot test or
an actual hosted-match test. No renderer or engine test substitutes are shipped
in the application.
