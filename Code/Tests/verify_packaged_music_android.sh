#!/usr/bin/env bash
# [TEMP] Verify shipped music bodies. No production source/object overrides are linked.
# Usage: ANDROID_NDK_HOME=... script APK-or-native-dir android-run stock-mod-dir output-dir [Common_client.zip]
set -euo pipefail
if [[ $# -lt 4 || $# -gt 5 ]]; then
    echo "usage: $0 APK-or-native-dir android-run stock-mod-dir output-dir [Common_client.zip]" >&2
    exit 2
fi
repo=$(cd -- "$(dirname -- "$0")/../.." && pwd)
input=$(realpath -- "$1")
runner=$(realpath -- "$2")
stock=$(realpath -- "$3")
mkdir -p -- "$4"
out=$(realpath -- "$4")
toolchain="${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [[ -d "$input" ]]; then
    libs="$input"
else
    libs="$out/libs"
    # Require a fresh directory so an old plugin cannot fill a missing APK entry.
    mkdir -- "$libs"
    python3 - "$input" "$libs" <<'PY'
import pathlib, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as archive:
    for name in archive.namelist():
        if name.startswith('lib/arm64-v8a/') and name.endswith('.so'):
            (pathlib.Path(sys.argv[2]) / pathlib.Path(name).name).write_bytes(archive.read(name))
PY
fi
for lib in libmain.so libBF2Audio.so libopenal.so; do test -f "$libs/$lib"; done
sha256sum "$libs"/*.so > "$out/library-hashes.before"
flags=(-std=c++14 -O1 -DDEDICATED=0 -D_LINUX
       -include "$repo/Code/BF2/Core/BuildConfig.h"
       -include "$repo/Code/BF2/Core/PlatformCompat.h"
       -include "$repo/Code/BF2/Core/PortableClient.h"
       -I"$repo/Code/BF2" -I"$repo/Code/BF2/External/python-embedded"
       -I"$repo/Code/BF2/PortShim" -I"$repo/Code/BF2/External/zlib-1.2.3/contrib/minizip")
for test in music_player_runtime map_music_android music_fade_android; do
    "$toolchain/aarch64-linux-android29-clang++" "${flags[@]}" "$repo/Code/Tests/$test.cpp" \
        -L"$libs" -lmain -lBF2Audio -ldl -llog -Wl,--export-dynamic -o "$out/$test" \
        > "$out/$test.build.log" 2>&1
done
for test in music_player_runtime map_music_android; do
    "$toolchain/llvm-nm" -D -C --undefined-only "$out/$test" > "$out/$test.imports"
    grep -F ' U dice::hfe::MusicPlayer::MusicPlayer()' "$out/$test.imports"
    grep -F ' U dice::hfe::MusicPlayer::setState(' "$out/$test.imports"
    grep -F ' U dice::hfe::MusicPlayer::finishDirectLoading()' "$out/$test.imports"
    if "$toolchain/llvm-nm" -D -C --defined-only "$out/$test" | grep -F 'MusicPlayer::'; then
        echo "fixture unexpectedly defines a production MusicPlayer body" >&2; exit 1
    fi
done
python3 "$repo/Code/Tests/make_music_fixture.py" "$out/generated.ogg"
audio="$out/generated.ogg"
if [[ $# == 5 ]]; then audio=$(realpath -- "$5"); fi
python3 "$repo/Code/Tests/make_map_music_fixture.py" "$stock" "$out" "$audio" > "$out/fixtures.log"
export BFU_LIBS="$libs"
"$runner" "$out/music_player_runtime" > "$out/player.log" 2>&1
"$runner" "$out/map_music_android" "$out/MixedCase-loose" loose > "$out/map-loose.log" 2>&1
"$runner" "$out/map_music_android" "$out/MixedCase-zip" zip > "$out/map-zip.log" 2>&1
"$runner" "$out/music_fade_android" "$out/generated.ogg" > "$out/fade.log" 2>&1
sha256sum "$libs"/*.so > "$out/library-hashes.after"
cmp "$out/library-hashes.before" "$out/library-hashes.after"
python3 - "$input" "$libs" "$audio" "$out" <<'PY'
import hashlib, json, pathlib, sys
source, libs, audio, out = map(pathlib.Path, sys.argv[1:])
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
record = {'input': str(source), 'apk_sha256': digest(source) if source.is_file() else None,
          'libraries': {p.name: digest(p) for p in sorted(libs.glob('*.so'))},
          'audio_fixture': str(audio), 'audio_sha256': digest(audio),
          'audio_kind': 'supplied_archive' if audio.suffix.lower() == '.zip' else 'generated_tone',
          'production_source_overrides': False, 'status': 'passed'}
(out / 'verification.json').write_text(json.dumps(record, indent=2)+'\n')
PY
tail -n 1 "$out/player.log" "$out/map-loose.log" "$out/map-zip.log" "$out/fade.log"
