#!/usr/bin/env bash
# Build the SDL engine and matching GPU loader hooks for the APK.
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
