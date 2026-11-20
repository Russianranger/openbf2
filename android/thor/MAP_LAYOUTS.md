# Offline map layouts

Layout size selects authored map content; bot counts select the roster. A 64-player
layout can run with a small bot roster or with zero bots. Offline sessions retain
one human slot, loopback networking and the local career admin script.

The launcher scans `server.zip` and loose map files, case-insensitively. For each
of 16, 32 and 64 it prefers a complete `gpm_coop` layout, then an authored `sp1`
layout, then actual `gpm_cq` placements with zero bots. A layout needs real object
placements and spawn definitions. Bot layouts additionally need non-comment AI
strategic-area creation, level AI initialization, and a readable version-3
infantry navigation tree/cluster pair. Bounded relative `run` includes are read;
loose files override corresponding archive files. Empty editor placeholders do
not qualify.

| Selected content | Match rules | AI | Offline human slots |
| --- | --- | --- | --- |
| `gpm_coop/<size>` | Co-op | Selected exact roster | 1 |
| `sp1/<size>` | Co-op, only for this scoped offline launch | Selected exact roster | 1 |
| `gpm_cq/<size>` | Conquest | Disabled; counts must be 0/0 | 1 |

LAN Conquest uses its selected layout and human capacity, without the offline
scope. The native start passes both mode and size. Its `+loadLevel` map-list
insertion preserves that size; the old size-zero insertion would choose the
smallest available map from the one-human limit. An explicitly selected layout
that disappears is rejected instead of silently loading another size. Map
identity remains case-insensitive across imported folder names and engine paths.

The stock 1.50 dedicated content has 17 complete co-op layouts, all size 16.
Most stock maps also have genuine larger Conquest layouts. Their presence does
not establish bot support: many accompanying AI files are comment-only, and some
larger `sp1` folders contain no gameplay objects. The launcher offers those real
Conquest layouts with zero bots. Imported authored bot maps can expose larger
co-op or singleplayer sizes using their own strategic areas and navigation.

Discovery was checked against stock 1.50 server content and the authored 32/64
Gulf of Oman and Strike at Karkand layouts from the 20 Year Anniversary v1.3
pack. These authored layouts contain distinct placements and expanded navigation;
the launcher discovers both sizes. No game or mod assets are included in this
source change. File discovery checks prerequisites; runtime gameplay checks are
still needed for a particular mod's scripts, vehicles and navigation coverage.

Validation entry points:

- `android/thor/tests/GameLayoutsTest.java`: fixture edge cases, real stock
  inventory, and authored larger-map discovery.
- `android/thor/tests/LaunchPlanTest.java`: mode/size validation, Conquest zero-bot
  policy, LAN arguments, and all 2,080 legal bot-roster combinations.
- `Code/Tests/offline_coop_runtime/launch_args.py`: actual native argument,
  configuration and environment functions under ARM64/Bionic, including mixed
  case names, exact sizes, one-human start, AI disabled for Conquest, and scope
  reset on LAN join.

These are intentional launcher features, not changes to replicated structures,
AI plugin interfaces or serialized map formats.
