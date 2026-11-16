# OpenBF2 for the AYN Thor

The ARM64 test application combines the Android SDL/Vulkan client from mackillerGR with kr4uzi's Battlefield 2 1.50 bot implementation. The package is `io.github.russianranger.openbf2`. Setup requires a complete, installed Battlefield 2 1.50 game-data folder. The APK includes the engine and support libraries. Bring the game content from your own installation.

## Play an offline round

1. Install the APK. Open **OpenBF2**, choose **Import Battlefield 2 installation**, and select the folder that contains `mods/bf2`, `python`, and `pylib-2.3.4.zip`. Keep the app open while the initial import finishes.
2. Choose a player name and a map in **Offline co-op map**. Start with **Strike at Karkand** if it appears in your installation. The list is filtered for maps containing 16-player co-op placement data.
3. Turn on airplane mode and tap **Play offline with bots**. Choose a team, kit, and spawn point if prompted. The local server requests 16 bots, an even team split, and 50% difficulty. The round can start on the first human spawn.
4. Play to a round result. Back at the launcher, open **Offline career**. Check your score, kills, deaths, completed rounds and rank. Close and reopen the app and check again. The nine local ranks use cumulative score. LAN sessions and unfinished rounds receive no career credit.
5. Start a second offline match to check round restart. **Export game logs** makes a support file if the game stops.

Use the Thor's Xbox controller mode for the button names in the [controller layout](https://github.com/Russianranger/openbf2/blob/android-engine/docs/android-thor/CONTROLS.md). For local network play, use **Host LAN** for a Conquest server or find/join a compatible server. LAN joining can request the product key from your own BF2 installation. Offline co-op uses a local player.

## Build and sign

Use a Linux x86-64 host, Git, Python 3, Make, CMake, Ninja, JDK 17, Android SDK platform 35, build-tools 35.0.0, and NDK r28c. Set `JAVA_HOME`, `ANDROID_HOME` and `ANDROID_NDK_HOME`. The first build fetches the pinned SDL and adrenotools sources. Run from the repository root.

```sh
android/build_thor.sh \
  --keystore /private/keys/openbf2.p12 --alias openbf2-thor \
  --storepass /private/keys/store.pass --output out/OpenBF2-Thor-arm64.apk
```

The build initializes the pinned Android engine from the fork’s `android-engine` branch on first use. Both source histories live in this fork. The Android build uses the platform-specific engine tree under `ports/android-engine` and builds the AI plug-in from the top-level `Code/Build/aidll_sources.txt`. The client and AI share the recovered host interfaces and the Android C++ runtime. The hash-pinned Stage 3 bundle supplies the existing renderer, embedded Python and related platform libraries. The game engine, gamepad bindings and AI integration are built from source. The packaging script verifies the signed APK and native ZIP alignment. Use the original signing identity for update installs to preserve the app's private SQLite career.

## Source revisions

| Input | Revision |
| --- | --- |
| Russianranger Thor design baseline | `4af39ef4157bc1566a32bbb4021ad2f2c3c8a239` |
| kr4uzi gameplay and bot engine | `02cc5ac818582689efe3452755f40e5f55edc2c7` |
| mackillerGR Stage 3.7 Android branch | `aefe0f802d9969fa03c0ebad9c9af742dc6231fd` |

## Validation

The signed v0.1.0 APK is 49,985,170 bytes. Its SHA-256 is `f5bdc2d222f8b8087b44ca3f9c495d83a8b41e045694c43cba990a980894ab3f`. The package identity, CRCs, APK v3 signature and native ZIP alignment passed verification. Its ARM64 engine, Python, AI, Flash menu, renderer and audio libraries loaded under Android 13 Bionic. The engine registered and removed all 298 AI console commands across two load/unload cycles.

An ARM64 dedicated server loaded EA's public 1.50 server data for Strike at Karkand with the AI library extracted from the signed APK. The process's mapped file confirmed the library actually loaded. Eight bots spawned on each team with a test-only override allowing the first spawn without a human. The scoreboard recorded a bot kill, the five-ticket round ended, and a second live round started with 16 bots. A longer server run also recorded movement, multiple kills, deaths, respawns and ticket changes. The Android client uses its own listen server for human play.

The packaged Python interpreter and career hook processed a controlled local human result through the original scoring and co-op scripts. In an Android 13 Robolectric test using native SQLite, the production launcher read the resulting receipt and displayed Private First Class, 159 points, one completed round, eight kills and one death. Closing and reopening the launcher retained the result. Replaying the same receipt left one database row. Separate tests excluded bots and remote players and exercised the folder picker and LAN browser buttons.

Physical Thor rendering, controls, LAN peer connectivity and a player completing a round with the full retail client remain to be verified. The pinned Stage 3 renderer may omit baked lighting on static objects. The first handheld check is a Karkand round in airplane mode followed by reopening **Offline career**. This build implements nine local score-based ranks. Battlefield HQ awards and weapon unlocks are future work.
