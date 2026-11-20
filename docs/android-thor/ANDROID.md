# OpenBF2 for the AYN Thor

The ARM64 test application combines the Android SDL/Vulkan client from mackillerGR with kr4uzi's Battlefield 2 1.50 bot implementation. The package is `io.github.russianranger.openbf2`. Setup requires a complete, installed Battlefield 2 1.50 game-data folder. The APK includes the engine and support libraries. Bring the game content from your own installation.

## Update an existing installation

Install 0.1.1 (version code 2) over 0.1.0. Android checks the original signing identity before installing the update. An in-place update preserves imported game files and the private Offline career database.

## Play an offline round

1. Install the APK. Open **OpenBF2**, choose **Import Battlefield 2 installation**, and select the folder that contains `mods/bf2`, `python`, and `pylib-2.3.4.zip`. Keep the app open while the initial import finishes.
2. Choose a player name and a map in **Offline co-op map**. Start with **Strike at Karkand** if it appears in your installation. The list is filtered for maps containing 16-player co-op placement data.
3. Turn on airplane mode and tap **Play offline with bots**. Choose a team, kit, and spawn point if prompted. The local server requests 16 bots, an even team split, and 50% difficulty. The round can start on the first human spawn.
4. Play to a round result. Back at the launcher, open **Offline career**. Check your score, kills, deaths, completed rounds and rank. Close and reopen the app and check again. The nine local ranks use cumulative score. LAN sessions and unfinished rounds receive no career credit.
5. Start a second offline match to check round restart. **Export game logs** makes a support file if the game stops.

Use the Thor's Xbox controller mode for the button names in the [controller layout](https://github.com/Russianranger/openbf2/blob/android-engine/docs/android-thor/CONTROLS.md). For local network play, use **Host LAN** for a Conquest server or find/join a compatible server. LAN joining can request the product key from your own BF2 installation. Offline co-op uses a local player.

## Build and sign

Use a Linux x86-64 host, Git, Python 3, Make, CMake, Ninja, JDK 17 or 21, Android SDK platform 35, build-tools 35.0.0, and NDK r28c. Set `JAVA_HOME`, `ANDROID_HOME` and `ANDROID_NDK_HOME`. The first build fetches the pinned SDL, adrenotools, DXVK headers and FFmpeg sources. Run from the repository root.

```sh
android/build_thor.sh \
  --keystore /private/keys/openbf2.p12 --alias openbf2-thor \
  --storepass /private/keys/store.pass --previous-apk /private/releases/OpenBF2-Thor-0.1.0-arm64.apk \
  --output out/OpenBF2-Thor-arm64.apk
```

The build checks out the pinned Android engine under `ports/android-engine`. It builds `libmain.so`, the AI plug-in, the Flash player, the renderer and GPU loader hooks from source. The Stage 3 bundle supplies the Android graphics backend, SDL, Python and audio support libraries. The packager verifies the APK signature, native ZIP alignment and compatibility with the previous APK when one is supplied.

## Source revisions

| Input | Revision |
| --- | --- |
| Russianranger Thor design baseline | `4af39ef4157bc1566a32bbb4021ad2f2c3c8a239` |
| kr4uzi gameplay and bot engine | `02cc5ac818582689efe3452755f40e5f55edc2c7` |
| mackillerGR Stage 3.7 Android branch | `aefe0f802d9969fa03c0ebad9c9af742dc6231fd` |

## 0.1.1 changes

- Invalid navigation-island IDs now trigger an all-island search. Recycled firearms invalidate their AI handles. Retired weapon records remain alive while a bot plan holds them, and weapon-dependent actions stop when their target weapon disappears. [Eon64 AI fixes](https://github.com/eonflux7/openbf2-eon64/blob/main/docs/eon64/upstream-fixes.md)
- The Android renderer reads the native lightmap and vegetation fields using the ARM64 layout. The game registers the stock `rotateUV` property for vehicle tracks. [Android lighting fix](https://github.com/mackillerGR/OpenBF2/commit/f858672137ac4d6e60bf8f118202a095bf33a7ba) · [Track fix](https://github.com/kr4uzi/OpenBF2/commit/399de762c433d7d417734e38715707ace7ac0816)
- The Flash menus, font renderer and pointer use the same centered 4:3 viewport. The stock HUD and field of view support wide displays. [Menu and HUD update](https://github.com/kr4uzi/OpenBF2/pull/50)
- The shared ARM numeric routines use the upstream client arithmetic policy with signaling-NaN handling and retain the exact path for the AI and dedicated server. The existing Android decal code already uses direct float operations. [ARM arithmetic](https://github.com/kr4uzi/OpenBF2/pull/51)

## 0.1.1 validation

The signed ARM64 APK is 50,034,322 bytes, SHA-256 `9d97f398e7965ef2a2f2371e13e8cfb957789ca5629ead38d0e7869a07b2357e`. Its package name and signing certificate match the signed 0.1.0 APK. Version code 2, the v3 signature and 16 KiB native ZIP alignment passed verification. All 16 packaged libraries match the build outputs or pinned support bundle. The four rebuilt images use the Android engine at [f542e5ea](https://github.com/Russianranger/openbf2/commit/f542e5ea674a59b56b57b3dde378729dd6b295ee).

- **Offline bot match.** An ARM64 dedicated server loaded Strike at Karkand with the AI extracted from the signed APK. The process mapping matched its SHA-256. A test-only first-spawn override started eight bots per team. A five-ticket round ended and a second round entered play with all 16 bots. Tracing confirmed that recycled firearms released their AI records. The same APK's engine and AI also registered and removed 298 AI console commands on each of two initialization cycles.
- **Menu and graphics code.** The extracted engine, renderer, Flash player, AI, audio and Python libraries loaded under Android 13 Bionic. All 23 UI checks passed, including glyph rasterization, renderer callbacks, menu-edge coordinates and release events in the widescreen margins. The fixtures run without a GPU.
- **Offline career.** The APK's ARM64 Python and career script processed a controlled human result through the original 1.50 scoring and co-op scripts. Repeated round-end notifications produced one receipt. A separate Android 13 native SQLite launcher test awarded Private First Class at 159 points, retained it after closing and reopening, and counted a replayed receipt only once.
- **Regression coverage.** The weapon-lifetime test passed 2,000 recycle cycles. The Android navigation test exercised empty and invalid islands, fallback polygon selection and material rejection. The armament test covered recycled and removed weapon slots. See the [AI tests](../../Code/Tests/aidll/recycling-regressions.md) and [numeric policy tests](../native-numeric-policy.md).

Physical Thor testing is still needed to verify a human can finish a full client match, the baked lighting and tank tracks look correct, and the controller feels right. Frame rate and thermals have not been measured. The existing nine-rank career tracks local score. Awards and weapon unlock progression remain future work.

## 0.1.0 validation

The signed v0.1.0 APK is 49,985,170 bytes. Its SHA-256 is `f5bdc2d222f8b8087b44ca3f9c495d83a8b41e045694c43cba990a980894ab3f`. The package identity, CRCs, APK v3 signature and native ZIP alignment passed verification. Its ARM64 engine, Python, AI, Flash menu, renderer and audio libraries loaded under Android 13 Bionic. The engine registered and removed all 298 AI console commands across two load/unload cycles.

An ARM64 dedicated server loaded EA's public 1.50 server data for Strike at Karkand with the AI library extracted from the signed APK. The process's mapped file confirmed the library actually loaded. Eight bots spawned on each team with a test-only override allowing the first spawn without a human. The scoreboard recorded a bot kill, the five-ticket round ended, and a second live round started with 16 bots. A longer server run also recorded movement, multiple kills, deaths, respawns and ticket changes. The Android client uses its own listen server for human play.

The packaged Python interpreter and career hook processed a controlled local human result through the original scoring and co-op scripts. In an Android 13 Robolectric test using native SQLite, the production launcher read the resulting receipt and displayed Private First Class, 159 points, one completed round, eight kills and one death. Closing and reopening the launcher retained the result. Replaying the same receipt left one database row. Separate tests excluded bots and remote players and exercised the folder picker and LAN browser buttons.

Physical Thor rendering, controls, LAN peer connectivity and a player completing a round with the full retail client remain to be verified. The 0.1.0 Stage 3 renderer could omit baked lighting on static objects. The first handheld check is a Karkand round in airplane mode followed by reopening **Offline career**. This build implements nine local score-based ranks. Battlefield HQ awards and weapon unlocks are future work.
