# Real map-layout regression

`layouts.py` runs a native dedicated engine plus its matching AI/Python libraries
against legally supplied stock server content and authored map archives. It
passes `+loadLevel` as the Android launch does, so the test covers the formerly
size-zero map-list insertion as well as exact mode/size validation.

The probe reads real Python host player state each second: round state, AI flag,
team, AI/local/remote flags, life state, vehicle position and kills. It checks the
requested roster, an alive local human, the exact native layout marker, and a
stable playing interval. Bot cases with a human additionally require observed
movement. It writes the executable hash, sampled observations and result JSON.
This is server gameplay coverage; it does not simulate the retail network login,
rendering, touch input or handheld performance.

The optional local entrant is deliberately a dedicated-only fixture. Apply
`local_human_fixture.patch` to a disposable source checkout, not the production
Android source. It creates the human at the existing listen-server boundary,
after loading and before the first AI wave. It also skips the unavailable client
input manager in the dedicated vehicle path. This preserves the genuine player,
spawn, physics, AI and game-mode implementations. The Python extension finds that
human, selects a real active spawn group and requests a normal server spawn. No
bot first-spawn override is needed. Link the QA executable with
`-Wl,--export-dynamic` so the extension can reach its real engine globals.

Compile the extension from the matching engine checkout (adjust output path):

```sh
c++ -shared -fPIC -std=gnu++14 -DDEDICATED=1 -D_LINUX -DBF2_EDX_PARAM= \
  -fms-extensions -fpermissive -I. -ICode/BF2 \
  -ICode/BF2/External/python-embedded/linux \
  -ICode/BF2/External/python-embedded/py-src-2.3.4/Include \
  -include Code/BF2/Core/BuildConfig.h -include Code/BF2/Core/PlatformCompat.h \
  Code/Tests/offline_coop_runtime/local_human_module.cpp -o /tmp/layout_human.so

python3 Code/Tests/offline_coop_runtime/layouts.py \
  --binary /path/to/qa/bf2 --libs /path/to/matching/native \
  --base-game /path/to/stock-1.50-server \
  --authored-levels /path/to/authored/Levels \
  --human-module /tmp/layout_human.so --local-human \
  --case gulf64 --out /path/to/results
```

Use separate output directories/ports for concurrent runs. The script copies
mutable files and hard-links only immutable archives into disposable game roots;
replacement archives are unlinked before copying. It never edits original game
assets and terminates only its own child server. Port 16567 is rejected.

Other cases are `gulf32`, `karkand64`, `sp1` (stock Karkand 16), `cq64` (stock
Gulf 64 with one human and no AI), and `missing` (stock Gulf co-op 64, which must
be rejected). `--mixed-case` launches a mixed-case map identity over canonical lower-case
content paths (the importer normalizes physical content names). `--map-pack /path/to/openbf2_maps_<id>` instead stages an
importer's immutable generation over the stock base and selects the native pack
VFS; both original stock archives and pack archives remain present.

For a desktop-only pack integration check, build FileManager.cpp, ModInfo.cpp and
BF2Engine.cpp with `BF2_OFFLINE_MAP_PACK_TEST=1`. Android selects the same code via
`__ANDROID__`. `relink.py --tu <relative path> --define <definition>` supports
focused private relinking without replacing the base objects or executable.

Checked with stock BF2 1.50 dedicated server data and the published 20 Year
Anniversary v1.3 map pack (64coop20YA.3.zip). Direct-layout results from the native
x86-64 QA engine, each including one alive local fixture human:

| Content | Mode / size | Bots team 1 / 2 | Bots observed moving | Kills observed |
| --- | --- | --- | --- | --- |
| Authored Gulf | gpm_coop / 32 | 8 / 8 | 16 | 1 |
| Authored Gulf | gpm_coop / 64 | 8 / 8 | 16 | 2 |
| Authored Karkand | gpm_coop / 64 | 8 / 8 | 16 | 0 |
| Stock Karkand | sp1 / 16, offline co-op rules | 3 / 5 | 8 | 0 |
| Stock Gulf | gpm_cq / 64 | 0 / 0 | 0 | 0 |

The stock Conquest run had AI disabled. A request for absent stock Gulf co-op64
was explicitly rejected rather than loading 16. Kills and movement are short
runtime observations, not a claim about complete map coverage or bot skill.

The overlay in this revision was also exercised using the actual
importer's generation `openbf2_maps_698c7a61052f4a37815e286ef985305a` over an
unchanged stock base. Mixed-case `Gulf_Of_Oman` loaded exact co-op64, with an alive
local human, 8/8 bots, all 16 bots moving and one kill across 46 samples. The
stock and imported runtime server archives retained their respective original
SHA-256 hashes and were distinct from one another. The same mixed-case identity
also passed the stock Conquest64 zero-bot test. Physical content paths remain
canonical lower-case, as normalized by the importer.
