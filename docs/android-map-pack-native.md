# Offline map-pack overlay

This is an intentional Android feature, not recovered retail behavior. It adds
no fields or virtual functions to engine interfaces and changes no network or
archive formats. The optional importer stores only level archives and Info
under an immutable app-owned generation:

```
Game Files/mods/openbf2_maps_<32 lowercase hexadecimal characters>/levels/<map>/
    server.zip
    client.zip
    info/<map>.desc
```

The canonical native launcher arguments are:

```
--mod <Game Files>/mods/bf2 --hosted --coop
--level <map> --mode gpm_coop --size 64
--map-pack openbf2_maps_<32 lowercase hexadecimal characters>
```

`--mode` retains the existing `gpm_coop`, `sp1`, or zero-bot `gpm_cq` contract.
Pack requests require exactly one pack ID, one map ID and one explicit size;
menu, LAN-host and remote-connect requests are rejected. Map IDs contain 1–80
ASCII letters, digits, underscores or hyphens and are folded to lowercase for
pack storage. A new launch clears previous pack selection before validation.
No network packet, server address, console variable or downloaded path selects
an overlay.

The selected pack ID is carried in `BF2_PORT_MAP_PACK` alongside the existing
local-co-op, direct-start, map, mode and size launch scope. These are local
process state, not engine command-line `+` options. `GSModDirectory`, Python,
career configuration and the VFS write root remain `mods/bf2`.

At startup the native parser requires both nonempty archives and the selected
map descriptor. Engine initialization then opens both actual ZIPs and requires
the exact `GameModes/<mode>/<size>/GamePlayObjects.con` in the pack server ZIP.
The existing exact-layout guard also checks the mounted layout during loading.
A missing pack, corrupt archive, missing descriptor or missing selected layout
fails instead of using a stock map or a different size. The importer performs
the additional authored gameplay and bot-navigation checks before publication.

FileManager remaps only the selected level. Its server/client ZIPs mount at the
ordinary `levels/<map>` location; its Info files are read from the selected
pack first. All other selected-level data must come from those mounted
archives. There is no fallback to loose stock-level data when an entry is
missing. Shared assets outside the selected level, such as Objects and Common,
continue to resolve from the base game. Ordinary unrelated resource reads take
a fast rejection path without allocating/normalizing the pack selection.

Writes, truncation, deletion, moves and copies involving the selected level
are refused, including its physical pack path. Ordinary settings, profiles and
progression writes retain the original base/home destinations. Archive unmount
resolves the same pack filename so repeated rounds release their mounts.

ModInfo reads the selected pack descriptor before game startup and attaches it
to the base mod. It replaces a case-insensitive stock-name match exactly once,
or adds a new map name; other maps retain their own metadata. Only the selected
pack map is exposed. Pack generations never become executable mods. Map names,
mode rows, sizes, loading text and music metadata come from that descriptor.
The pack generation itself is never inserted as a general search/write path.

## Focused validation

`Code/Tests/offline_coop_runtime/launch_args.py` executes the production Android
argument/configuration function. The extended suite covers pack grammar,
duplicate options, required files, exact size, scope rejection, environment
clearing, base-mod identity and existing launch behavior.

`Code/Tests/offline_map_pack_runtime/run.py` compiles current FileManager and
ModInfo for Android arm64 and links the matching engine library for its real
ZIP, Unix filesystem, XML, settings, memory-stream and registry services. It
creates synthetic archives and descriptors; no game data is distributed.

```sh
BFU_LIBS=/path/to/native-libraries python3 Code/Tests/offline_map_pack_runtime/run.py \
  --source "$PWD" --out /tmp/map-pack-runtime \
  --ndk "$ANDROID_NDK_HOME" --library /path/to/libmain.so \
  --sdl-include /path/to/SDL/include --runner /path/to/android-run
```

The runner can be the project's QEMU/Bionic wrapper or an equivalent Android
runner. It verifies mixed-case/root-qualified reads, both mounted archives,
stock-name and new-name metadata, exact unmount/remount, write isolation,
missing-layout/descriptor/archive behavior, no-pack restoration, and unchanged
pack hashes. This focused test does not simulate a complete match.

Add `--linked-only` with `--library` pointing to `libmain.so` extracted from the
final APK to test shipped native code. This mode compiles only the probe, checks
that it exports no replacement FileManager, ModInfo or map-pack helper symbols,
places the exact library directory first in the runner's `BFU_LIBS`, and verifies
the loaded production function's origin with `dladdr`. It reports the library's
SHA256. The same VFS, archive, metadata and save checks then call that library.

For a desktop headless integration fixture only, compile FileManager.cpp,
ModInfo.cpp and BF2Engine.cpp with `BF2_OFFLINE_MAP_PACK_TEST=1` to enable the same
local selection state. Production desktop behavior stays unchanged; Android
builds enable it naturally. Live match validation must use an isolated test
port and real paired server/client content, and report its own duration and
observed bot activity separately from these VFS checks.
