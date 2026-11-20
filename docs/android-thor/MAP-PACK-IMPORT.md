# Optional map-pack import

The Setup map-pack picker accepts an extracted map-pack mod folder containing
`Levels`, or the `Levels` folder itself. Source names are matched without regard to
case. Only each map's complete `client.zip`, `server.zip`, and `Info` files are
copied. Global mod archives, Python code, settings, and loose gameplay overrides
are not imported by this maps-only path.

The importer checks server archive CRCs and sizes, then discovers authored bot
layouts using `GameContent` before copying a potentially large client archive.
Client archive members also receive complete CRC and size checks. Map IDs must
match `[A-Za-z0-9_-]{1,80}` and have a nonempty `Info/<map>.desc`. Incomplete and
unsupported maps are skipped and counted; no pack is published if none are bot
ready. Corrupt complete bot maps, short copies, and provider failures abort the
import and retain the previous selection.

Each import creates a new app-owned generation named
`mods/openbf2_maps_<32 lowercase UUID hexadecimal digits>`. Its asset paths are
canonicalized as `levels/<lowercase map>/info/...`. The complete staging directory
is renamed before the synchronous `openbf2` preference `mapPack` is updated. The
preference contains only that validated basename, never a source URI or arbitrary
path. The base `mods/bf2` installation is untouched.

After successful publication, older generations carrying the importer's ownership
marker are eligible for cleanup. Unmarked folders and the base game are never
removed. An interrupted copy leaves the previous pointer active; an interrupted
publication may leave a complete unused generation, collected by a later successful
import. A reported preference failure also restores the prior in-memory selection.

Both full and map imports use the same process-wide exclusion flag, and the session
is checked again after a picker result arrives. Launcher actions must reject launches
while `picker.isImporting()` is true. The importer does not impersonate an active
game by taking the game-session lock.

Integration API: `pickMapPack()`, `REQUEST_MAP_PACK` (4083), and
`onMapPackResult(resultCode, data)`. Successful publication calls the existing
`Listener.onImported` with the base mod path so the launcher can refresh its map list.

Validation uses `android/thor/tests/MapPackImportTest.java` against the production
copy/publication helper, with optional real extracted pack and output directories.
`run_map_pack_runtime.py` runs Android 13 Robolectric checks through the actual SAF
picker callback, content-provider streams, SharedPreferences rollback, session
refusal, and exclusion with the full importer. These checks validate import and
discovery; they do not establish rendered gameplay for a community map.
