#!/usr/bin/env bash
# Build the Flash menu plugin against the rebuilt host and the pinned renderer dependencies.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=$(mkdir -p "${1:-$root/out/android-swiff}" && cd "${1:-$root/out/android-swiff}" && pwd)
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
: "${ndk:?Set ANDROID_NDK_HOME to the Android NDK (r28c tested)}"
: "${BF2_ANDROID_ENGINE_LIBRARY:?Set BF2_ANDROID_ENGINE_LIBRARY to the matching rebuilt libmain.so}"
deps=${BF2_RENDER_DEPS:-$root/out/android-renderer/deps}
support=${BF2_ANDROID_LIBDIR:-$(dirname "$deps")/support-libs}
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"

for input in "$BF2_ANDROID_ENGINE_LIBRARY" "$support/libSDL3.so" \
    "$deps/SDL/include/SDL3/SDL.h" "$deps/dxvk/include/native/windows/windows.h" \
    "$deps/dxvk/include/native/directx/d3d9.h" "$deps/vkd3d/vkd3d_shader.h"; do
    if [[ ! -f "$input" ]]; then
        echo "Missing build input: $input. Run build_native_client.sh and build_native_renderer.sh first." >&2
        exit 1
    fi
done

cmake -S "$root" -B "$out/build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DCMAKE_CXX_FLAGS_RELEASE="-O1 -DNDEBUG" -DCMAKE_C_FLAGS_RELEASE="-O1 -DNDEBUG" \
    -DBF2_ANDROID_ENGINE_LIBRARY="$BF2_ANDROID_ENGINE_LIBRARY" \
    -DBF2_SDL3_LIBDIR="$support" -DBF2_SDL3_INCLUDE="$deps/SDL/include" \
    -DBF2_DXVK_ROOT="$deps/dxvk" -DBF2_VKD3D_INCLUDE="$deps/vkd3d"
cmake --build "$out/build" --parallel "${CMAKE_BUILD_PARALLEL_LEVEL:-2}" --target bf2_swiff_portable
mkdir -p "$out/arm64-v8a"
"$toolchain/llvm-strip" --strip-unneeded -o "$out/arm64-v8a/libSwiffPlayer.so" \
    "$out/build/bin_portable/libSwiffPlayer.so"
python3 - "$root" "$ndk" "$out" "$BF2_ANDROID_ENGINE_LIBRARY" "$deps" "$support" <<'PY'
import hashlib
import json
import pathlib
import subprocess
import sys

root, ndk, out, engine, deps, support = map(pathlib.Path, sys.argv[1:])
def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def revision(path):
    return subprocess.check_output(["git", "-C", str(path), "rev-parse", "HEAD"], text=True).strip()

record = {
    "source_commit": revision(root),
    "source_dirty": bool(subprocess.check_output(["git", "-C", str(root), "status", "--porcelain"], text=True)),
    "ndk": (ndk / "source.properties").read_text(),
    "abi": "arm64-v8a",
    "android_api": 29,
    "optimization": "-O1 -DNDEBUG -ffp-contract=off",
    "engine_sha256": sha256(engine),
    "swiff_sha256": sha256(out / "arm64-v8a/libSwiffPlayer.so"),
    "sdl_revision": revision(deps / "SDL"),
    "dxvk_headers_revision": revision(deps / "dxvk"),
    "vkd3d_headers": {p.name: sha256(p) for p in sorted((deps / "vkd3d").glob("*.h"))},
    "sdl_library_sha256": sha256(support / "libSDL3.so"),
}
(out / "arm64-v8a/swiff-build.json").write_text(json.dumps(record, indent=2) + "\n")
PY
