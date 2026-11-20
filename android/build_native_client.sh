#!/usr/bin/env bash
# Build the SDL engine, matching GPU loader hooks, renderer and Flash menu plugin for the APK.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=$(mkdir -p "${1:-$root/out/android-native}" && cd "${1:-$root/out/android-native}" && pwd)
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
: "${ndk:?Set ANDROID_NDK_HOME to the Android NDK (r28c tested)}"
archive=${BF2_STAGE3_BUNDLE:-$root/BF2_Stage3_Ready_For_APK_Build_mackillerGR.zip}
mkdir -p "$out/deps" "$out/support-libs"

# Use just the redistributable Android runtime binaries. The user imports the game data separately.
python3 - "$archive" "$out/support-libs" <<'PY'
import hashlib
import pathlib
import sys
import zipfile

archive = pathlib.Path(sys.argv[1])
expected = "6cd417b89b74600a0d898d2d92c56562169f2975058e4636e93f3ab463067375"
if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
    raise SystemExit("Unexpected Stage 3 bundle SHA-256")
with zipfile.ZipFile(archive) as source:
    for name in ("libSDL3.so", "libdice_py.so"):
        entry = "gameapp/src/main/jniLibs/arm64-v8a/" + name
        pathlib.Path(sys.argv[2], name).write_bytes(source.read(entry))
PY

fetch_revision() {
    local path=$1 url=$2 sha=$3
    if [[ ! -d "$path/.git" ]]; then
        git init --quiet "$path"
        git -C "$path" remote add origin "$url"
    fi
    if ! git -C "$path" cat-file -e "$sha^{commit}" 2>/dev/null; then
        git -C "$path" fetch --depth=1 origin "$sha"
    fi
    git -C "$path" checkout --quiet --detach "$sha"
}
fetch_revision "$out/deps/SDL" https://github.com/libsdl-org/SDL.git 147a8ee32dbf9ac02f3794964490687b6bbda1bc
fetch_revision "$out/deps/adrenotools" https://github.com/bylaws/libadrenotools.git 8fae8ce254dfc1344527e05301e43f37dea2df80
git -C "$out/deps/adrenotools" submodule update --init --recursive
cmake -S "$root" -B "$out/build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DCMAKE_CXX_FLAGS_RELEASE="-O1 -DNDEBUG" -DCMAKE_C_FLAGS_RELEASE="-O1 -DNDEBUG" \
    -DBF2_SDL3_INCLUDE="$out/deps/SDL/include" \
    -DBF2_ANDROID_LIBDIR="$out/support-libs" -DBF2_ADRENOTOOLS_ROOT="$out/deps/adrenotools"
cmake --build "$out/build" --parallel "${CMAKE_BUILD_PARALLEL_LEVEL:-2}" --target \
    bf2_android_client main_hook file_redirect_hook gsl_alloc_hook hook_impl

strip_tool=$(find "$ndk/toolchains/llvm/prebuilt" -path '*/bin/llvm-strip' -print -quit)
: "${strip_tool:?Missing NDK llvm-strip}"
mkdir -p "$out/arm64-v8a"
for library in "$out/build/libmain.so" "$out/build"/adrenotools/src/hook/lib*_hook.so "$out/build/adrenotools/src/hook/libhook_impl.so"; do
    "$strip_tool" --strip-unneeded -o "$out/arm64-v8a/${library##*/}" "$library"
done
python3 - "$root" "$ndk" "$out" <<'PY'
import hashlib
import json
import pathlib
import subprocess
import sys

root, ndk, out = map(pathlib.Path, sys.argv[1:])
def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
generated = [out / "build/libmain.so"]
generated += sorted((out / "build/adrenotools/src/hook").glob("lib*_hook.so"))
generated += [out / "build/adrenotools/src/hook/libhook_impl.so"]
record = {
    "source_commit": subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip(),
    "source_dirty": bool(subprocess.check_output(["git", "-C", str(root), "status", "--porcelain"], text=True)),
    "ndk": (ndk / "source.properties").read_text(),
    "abi": "arm64-v8a",
    "android_api": 29,
    "optimization": "-O1 -DNDEBUG -ffp-contract=off",
    "sdl_revision": "147a8ee32dbf9ac02f3794964490687b6bbda1bc",
    "adrenotools_revision": "8fae8ce254dfc1344527e05301e43f37dea2df80",
    "libraries": {p.name: sha256(out / "arm64-v8a" / p.name) for p in generated},
    "support_libraries": {p.name: sha256(p) for p in sorted((out / "support-libs").glob("*.so"))},
}
(out / "arm64-v8a/engine-build.json").write_text(json.dumps(record, indent=2) + "\n")
PY

# All production client plugins must follow this host revision. Host-only mode is for
# compiler diagnostics and never satisfies build_thor.sh's required four-image packaging gate.
if [[ ${BF2_NATIVE_HOST_ONLY:-0} != 1 ]]; then
    renderer_out=${BF2_RENDER_OUT:-$out/renderer}
    swiff_out=${BF2_SWIFF_OUT:-$out/swiff}
    BF2_ANDROID_ENGINE_LIBRARY="$out/build/libmain.so" \
        "$root/android/build_native_renderer.sh" "$renderer_out"
    BF2_ANDROID_ENGINE_LIBRARY="$out/build/libmain.so" BF2_RENDER_DEPS="$renderer_out/deps" \
        BF2_ANDROID_LIBDIR="$renderer_out/support-libs" \
        "$root/android/build_native_swiff.sh" "$swiff_out"
    cp "$renderer_out/arm64-v8a/libRendDX9.so" "$renderer_out/arm64-v8a/renderer-build.json" "$out/arm64-v8a/"
    cp "$swiff_out/arm64-v8a/libSwiffPlayer.so" "$swiff_out/arm64-v8a/swiff-build.json" "$out/arm64-v8a/"
fi
