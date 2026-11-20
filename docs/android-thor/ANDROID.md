# OpenBF2 for the AYN Thor

The ARM64 test application combines the Android SDL/Vulkan client from mackillerGR with kr4uzi's Battlefield 2 1.50 bot implementation. The package is `io.github.russianranger.openbf2`. Setup requires a complete, installed Battlefield 2 1.50 game-data folder. The APK includes the engine and support libraries. Bring the game content from your own installation.

## Update an existing installation

Install 0.1.4 (version code 5) over 0.1.3 or an earlier signed Thor build. Android checks the original signing identity before installing the update. An in-place update preserves imported game files and the private Offline career database.

## Play an offline round

1. Open **Setup** to choose your player name. On a first installation, choose **Import Battlefield 2 installation** and select the folder containing `mods/bf2`, `python` and `pylib-2.3.4.zip`. Existing imports appear automatically.
2. In **Deployment**, choose **Select map**, then **Map size**. Each entry identifies the installed layout and whether it supports bots. Choose the two faction counts independently on a bot-supported layout. The default is eight per side, in addition to your soldier. **Solo exploration** opens an installed Conquest layout with zero bots. Your selected roster returns when you switch back to a bot-supported layout.
3. Choose **Deploy**. At the team, kit and spawn screens, move the right stick and click with RT. The local server uses 50% bot difficulty. The game reads the music named in the map descriptor and fades it as gameplay starts.
4. Under **Controls → Controller bindings**, assign a Battlefield action to a button or key. You can also assign an action to a hold. With the default Xbox layout, tap B to crouch, hold B for prone, then tap B to stand. Crouch also toggles on the touchscreen. The launcher remembers your **Show touch controls** setting.
5. Tap the gear or hold Start to open the game options. **Return to game** closes the panel, **Export logs** opens the Android save dialog, and **Return to launcher** leaves the current match available. Use **Resume match** to return with updated controls. To switch maps, use **End current match**, confirm, then deploy again.
6. Finish a round and check **Career** for completed rounds, score, kills, deaths and rank. LAN sessions and unfinished rounds do not earn offline career credit.

The [controller guide](https://github.com/Russianranger/openbf2/blob/android-engine/docs/android-thor/CONTROLS.md) uses the logical button names from the Thor's Xbox mode. **LAN** hosts Conquest at the chosen map size or joins a compatible server. Joining may require a product key from your own installation.

## Build and sign

Use a Linux x86-64 host, Git, Python 3, Make, CMake, Ninja, JDK 17 or 21, Android SDK platform 35, build-tools 35.0.0, and NDK r28c. Set `JAVA_HOME`, `ANDROID_HOME` and `ANDROID_NDK_HOME`. The first build fetches the pinned SDL, adrenotools, DXVK headers and FFmpeg sources. Run from the repository root.

```sh
android/build_thor.sh \
  --keystore /private/keys/openbf2.p12 --alias openbf2-thor \
  --storepass /private/keys/store.pass --previous-apk /private/releases/OpenBF2-Thor-0.1.3-arm64.apk \
  --output out/OpenBF2-Thor-arm64.apk
```

The build checks out the pinned Android engine under `ports/android-engine`. It builds `libmain.so`, the AI plug-in, the Flash player, the renderer and GPU loader hooks from source. The Stage 3 bundle supplies the Android graphics backend, SDL, Python and audio support libraries. The packager verifies the APK signature, native ZIP alignment and compatibility with the previous APK when one is supplied.

## Source revisions

| Input | Revision |
| --- | --- |
| Russianranger Thor design baseline | `4af39ef4157bc1566a32bbb4021ad2f2c3c8a239` |
| kr4uzi gameplay and bot engine | `02cc5ac818582689efe3452755f40e5f55edc2c7` |
| mackillerGR Stage 3.7 Android branch | `aefe0f802d9969fa03c0ebad9c9af742dc6231fd` |

## 0.1.4 map sizes, vehicles and Singleplayer

The signed ARM64 update is 50,198,162 bytes with SHA-256 `c17dbf4c2451343a2391b1d1e3e07c336bfea95138e112779c4b3325abf79ece`. It uses the original 0.1.3 signing certificate and passed Android v3 signature verification and 16 KiB native ZIP alignment. This branch pins the [released Android engine](https://github.com/Russianranger/openbf2/commit/dd739a2e14c754f40d5bd43ce728d16c5a580c29).

Stock Battlefield 2 1.50 includes bot navigation and co-op placements for the 16-player versions of Karkand and Gulf of Oman. The stock 32- and 64-player Conquest versions can be explored alone. To play a larger layout with bots:

1. On a device with Internet access, download a pack with authored co-op maps, such as [Battlefield 2 – 64 Coop – 20 Year Anniversary v1.3](https://www.moddb.com/mods/battlefield-2-64-coop-20-year-anniversary/downloads/battlefield-2-64-coop-20-year-anniversary). Extract it and make its `Levels` folder available on the Thor.
2. Open **Setup → Import bot map pack**. Select the extracted pack folder containing `Levels`, or the `Levels` folder itself. The importer copies complete level archives and their map information into a separate location. Your base Battlefield installation is still required.
3. Open **Deployment → Select map → Map size**. Choose a size marked **Bots supported (map pack)**. The base game's layouts remain available. The importer supports self-contained map data using base BF2 assets. A mod requiring additional global objects or scripts may also need those dependencies.

The importer accepted all 22 maps in the published v1.3 pack (archive MD5 `65720fea72218852962463ffedb69d65`). In headless matches, all 16 bots moved on the 32-player Gulf of Oman and 64-player Karkand layouts alongside a local test soldier. With the imported pack mounted alongside stock, Gulf of Oman 64 ran with one local test soldier and eight bots per team. All 16 bots moved and one kill was recorded. Stock Karkand Singleplayer also spawned its selected three and five bots, and stock 64-player Conquest spawned the test soldier with no bots.

The stock map descriptors for Karkand, Gulf of Oman and Road to Jalalabad all request the MEC theme. Dalian Plant requests the China theme and Midnight Sun requests the US theme. The exported log identifies the requested map, the music resource and playback errors.

The first-run **Singleplayer** button now opens BF2's map selection screen. Select a map and press **Start**. **Deploy** in the Android launcher still starts the selected match directly. On foot, using a weapon's sights switches to the authored aim-down-sights HUD and hides the hip-fire reticle. The M4 optic dot, hit feedback and vehicle reticles are preserved.

The Android effect loader now uploads the complete vehicle lighting structure and all seven texture-coordinate matrices. In a software Vulkan test, the old shader drew 100,854 black Humvee pixels. The corrected shader lit 100,577 pixels across the same model silhouette. With the original M1A2 and T-90 meshes and textures, both animated UV phases matched the CPU reference at all four detail levels, including draws following stale texture transforms. Extracted APK libraries passed 20 aim-down-sights checks, 88 map-file and metadata checks, and 240 stock soldier-kit checks. Loading music tests used the original map descriptors, generated Ogg samples and the packaged audio engine to check track selection and fade. Physical rendering and full-match play with the new APK remain handset checks.

## 0.1.3 deployment and terrain

The 0.1.3 APK is 50,124,434 bytes. Its SHA-256 is `095d81a57f758b965e37bba9ceb09357dba961e5670fd37b756168a24da87dab`. The package passed APK v3 signing verification with the 0.1.2 certificate and 16 KiB ZIP alignment. The released [engine revision](https://github.com/Russianranger/openbf2/commit/a060521a026b9e7fe3e91a83de7bf47eefa5db72) is pinned in this branch.

The Thor owner confirmed that 0.1.2 corrected the weapon sights and soldier models. The new Karkand screenshots showed adjacent patches of textured ground and empty backdrop. The ARM64 terrain loader was reading the detailed tiles at a 400-byte stride while their native records occupy 416 bytes. The loader now uses the same native stride for the parent records and their children.

Against stock Battlefield 2 1.50 Operation Road Rage data, the old loader corrupted four child links and gave 49 of the 64 detailed tiles an empty draw count. The corrected ARM64 loader preserved all links and counts. In a software Vulkan render of four adjacent tiles through the stock terrain shader, all four rendered after the change. One of those four rendered before the fix. Physical Karkand rendering remains a device check.

The launcher uses the imported map's display name, preview and team names. The offline roster supports zero through 63 bots combined, reserving one player slot for the human. Deployment retains the map's 16-player placement layout. On Android 13, the actual launcher selected a second imported map, saved different counts for the two factions and opened the binding editor. Headless Karkand tests queried the live roster for 0/0, 0/5, 7/0, 3/5, 31/32 and 63/0 bots. The engine also honored the launcher when a test installation supplied conflicting AI population settings. The 63-bot capacity test did not measure handheld frame rate.

Direct-start games now ask the BF2 music player to fade the loading track on the first ready game frame. The stock audio library faded a test track over five seconds, kept an independent playing source at its original gain, and restarted the next round's music at normal volume.

The delivered controller bridge passed 124 native action-mapping checks. Android 13 tests covered button capture, tap/hold discrimination, remapping across the launcher and game processes, the gear touch target and returning to the same session. The final APK payloads matched those exercised by the runtime tests. The packaged AI completed a headless Karkand round and entered a second one. The existing 20-kit soldier-pose and material-state regressions also passed.

## 0.1.2 handheld fixes

The 0.1.2 ARM64 APK is 50,054,802 bytes, SHA-256 `758320d3a25f7292f7f01af951155c79d05db133a00037640d71c8fc4fc7f13c`. Android package identity, the 0.1.1 signing certificate, APK v3 verification and native 16 KiB ZIP alignment were checked against the final package. The [Android engine revision](https://github.com/Russianranger/openbf2/commit/c4a565fb0ef7e10f7bfb8f7a59a5ab95c7a7871e) supplies the game, renderer and input changes.

The previous build reached a live match on a physical AYN Thor. Device screenshots showed a black map load, a dark rectangle over the M4 sight, and equipment floating above a soldier. The renderer log identified the bundled Turnip driver at 1920×1080. This update retains that resolution.

| Area | Change and validation |
| --- | --- |
| Deployment, pause and Flash menus | SDL uses absolute mouse input when BF2 shows the pointer. The right stick moves a visible cursor and RT clicks. Returning to the game restores relative aiming. Android 13 input tests cover movement, a held trigger, mode changes and disconnects. |
| Android launcher and touch controls | The right stick and RT also work in the launcher, its map selector, the career dialog and the LAN browser. The launcher saves **Show touch controls** immediately. Turning it off hides the virtual buttons and leaves menu touch and the controller cursor available. |
| Map loading | Direct offline matches display the selected map’s imported artwork and available engine progress. The loader recognizes the DDS data inside stock `Info/loadmap.png` files. Independent decoding of a stock 512×512 file matched all pixels. The loading layer clears when the client reaches deployment or gameplay. |
| Weapon sights | The Android effects implementation now applies material blend and depth changes made after a rendering pass starts. A DXVK software-Vulkan test used the stock 1.50 M4 sight. The broken path exposed zero scene pixels. The corrected path exposed the same 37,315 scene pixels as an explicit blend-state override in a 256×256 test. |
| Soldier equipment | The ARM64 client now passes the live soldier bone array to the carried kit. The old x86 calling convention put an empty pointer in that argument on ARM64. Against the actual ARM64 renderer, all 20 stock US and MEC skinned kit variants followed the supplied pose. The test checked 167 bone matrices. |

The Thor log also recorded a Vulkan surface loss during frame presentation. The Android activity now stops new frame presentation when the surface becomes unavailable and resumes after SDL receives a valid surface. Physical background and foreground behavior still needs a Thor retest.

The extracted renderer and engine passed 23 menu, font and coordinate checks, and all 240 soldier attachment checks using 20 kit variants. Two startup and teardown cycles each registered and removed the 298 AI commands. Android 13 tests covered a saved touch setting, stick and trigger behavior, loading presentation, Android surface callbacks, and reopening the career database after a promotion. The physical Thor check below remains open.

For the handset check, launch Strike at Karkand in airplane mode. Look for the map art before deployment. Use the right stick and RT to choose a spawn point, aim through an M4 sight, and inspect a nearby soldier's helmet and kit. Logs can be exported from the launcher while touch controls are hidden.

## 0.1.1 changes

- Invalid navigation-island IDs now trigger an all-island search. Recycled firearms invalidate their AI handles. Retired weapon records remain alive while a bot plan holds them, and weapon-dependent actions stop when their target weapon disappears. [Eon64 AI fixes](https://github.com/eonflux7/openbf2-eon64/blob/main/docs/eon64/upstream-fixes.md)
- The Android renderer reads the native lightmap and vegetation fields using the ARM64 layout. The game registers the stock `rotateUV` property for vehicle tracks. [Android lighting fix](https://github.com/mackillerGR/OpenBF2/commit/f858672137ac4d6e60bf8f118202a095bf33a7ba) · [Track fix](https://github.com/kr4uzi/OpenBF2/commit/399de762c433d7d417734e38715707ace7ac0816)
- The Flash menus, font renderer and pointer use the same centered 4:3 viewport. The stock HUD and field of view support wide displays. [Menu and HUD update](https://github.com/kr4uzi/OpenBF2/pull/50)
- The shared ARM numeric routines use the upstream client arithmetic policy with signaling-NaN handling and retain the exact path for the AI and dedicated server. The existing Android decal code already uses direct float operations. [ARM arithmetic](https://github.com/kr4uzi/OpenBF2/pull/51)

## 0.1.1 validation

The signed ARM64 APK is 50,034,322 bytes, SHA-256 `9d97f398e7965ef2a2f2371e13e8cfb957789ca5629ead38d0e7869a07b2357e`. Its package name and signing certificate match the signed 0.1.0 APK. Version code 2, the v3 signature and 16 KiB native ZIP alignment passed verification. All 16 packaged libraries match the build outputs or pinned support bundle. The four rebuilt images use the Android engine at [f542e5ea](https://github.com/Russianranger/openbf2/commit/f542e5ea674a59b56b57b3dde378729dd6b295ee).

- **Offline bot match.** An ARM64 dedicated server loaded Strike at Karkand with the AI extracted from the signed APK. The process mapping matched its SHA-256. A test-only first-spawn override started eight bots per team. A five-ticket round ended and a second round entered play with all 16 bots. A separate run used the full ticket setting. The co-op game script recorded three bot-on-bot kills by two attackers, and team tickets changed from 100/110 to 100/105. Tracing confirmed that recycled firearms released their AI records. The same APK's engine and AI also registered and removed 298 AI console commands on each of two initialization cycles.
- **Menu and graphics code.** The extracted engine, renderer, Flash player, AI, audio and Python libraries loaded under Android 13 Bionic. All 23 UI checks passed, including glyph rasterization, renderer callbacks, menu-edge coordinates and release events in the widescreen margins. The fixtures run without a GPU.
- **Offline career.** The APK's ARM64 Python and career script processed a controlled human result through the original 1.50 scoring and co-op scripts. Repeated round-end notifications produced one receipt. A separate Android 13 native SQLite launcher test awarded Private First Class at 159 points, retained it after closing and reopening, and counted a replayed receipt only once.
- **Regression coverage.** The weapon-lifetime test passed 2,000 recycle cycles. The Android navigation test exercised empty and invalid islands, fallback polygon selection and material rejection. The armament test covered recycled and removed weapon slots. See the [AI tests](../../Code/Tests/aidll/recycling-regressions.md) and [numeric policy tests](../native-numeric-policy.md).

Physical Thor testing is still needed to verify a human can finish a full client match, the baked lighting and tank tracks look correct, and the controller feels right. Frame rate and thermals have not been measured. The existing nine-rank career tracks local score. Awards and weapon unlock progression remain future work.

## 0.1.0 validation

The signed v0.1.0 APK is 49,985,170 bytes. Its SHA-256 is `f5bdc2d222f8b8087b44ca3f9c495d83a8b41e045694c43cba990a980894ab3f`. The package identity, CRCs, APK v3 signature and native ZIP alignment passed verification. Its ARM64 engine, Python, AI, Flash menu, renderer and audio libraries loaded under Android 13 Bionic. The engine registered and removed all 298 AI console commands across two load/unload cycles.

An ARM64 dedicated server loaded EA's public 1.50 server data for Strike at Karkand with the AI library extracted from the signed APK. The process's mapped file confirmed the library actually loaded. Eight bots spawned on each team with a test-only override allowing the first spawn without a human. The scoreboard recorded a bot kill, the five-ticket round ended, and a second live round started with 16 bots. A longer server run also recorded movement, multiple kills, deaths, respawns and ticket changes. The Android client uses its own listen server for human play.

The packaged Python interpreter and career hook processed a controlled local human result through the original scoring and co-op scripts. In an Android 13 Robolectric test using native SQLite, the production launcher read the resulting receipt and displayed Private First Class, 159 points, one completed round, eight kills and one death. Closing and reopening the launcher retained the result. Replaying the same receipt left one database row. Separate tests excluded bots and remote players and exercised the folder picker and LAN browser buttons.

Physical Thor rendering, controls, LAN peer connectivity and a player completing a round with the full retail client remain to be verified. The 0.1.0 Stage 3 renderer could omit baked lighting on static objects. The first handheld check is a Karkand round in airplane mode followed by reopening **Offline career**. This build implements nine local score-based ranks. Battlefield HQ awards and weapon unlocks are future work.
