#!/usr/bin/env bash
# Build the Android renderer against the matching rebuilt engine.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=$(mkdir -p "${1:-$root/out/android-renderer}" && cd "${1:-$root/out/android-renderer}" && pwd)
ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
: "${ndk:?Set ANDROID_NDK_HOME to the Android NDK (r28c tested)}"
: "${BF2_ANDROID_ENGINE_LIBRARY:?Set BF2_ANDROID_ENGINE_LIBRARY to the matching rebuilt libmain.so}"
archive=${BF2_STAGE3_BUNDLE:-$root/BF2_Stage3_Ready_For_APK_Build_mackillerGR.zip}
parallel=${CMAKE_BUILD_PARALLEL_LEVEL:-2}
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$out/deps" "$out/support-libs" "$out/arm64-v8a"

# The graphics backend binaries retain the tested Stage 3 Android integration.
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
    for name in ("libSDL3.so", "libdice_py.so", "libdxvk_d3d9.so", "libvkd3d-shader.so"):
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
fetch_revision "$out/deps/dxvk" https://github.com/doitsujin/dxvk.git ab3631f80d9cd493676d4342bf614028c72e7484
git -C "$out/deps/dxvk" submodule update --init --depth=1 include/native/directx
mkdir -p "$out/deps/vkd3d"
for header in vkd3d_shader.h vkd3d_types.h; do
    curl --fail --location --retry 2 \
        "https://raw.githubusercontent.com/wine-mirror/wine/9f8c4ae4d35c7ff4153267aba9618c026f5f5583/libs/vkd3d/include/$header" \
        --output "$out/deps/vkd3d/$header"
done

ffmpeg_archive="$out/deps/ffmpeg-7.1.1.tar.xz"
ffmpeg_sha=733984395e0dbbe5c046abda2dc49a5544e7e0e1e2366bba849222ae9e3a03b1
if [[ ! -f "$ffmpeg_archive" ]]; then
    curl --fail --location --retry 2 https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz \
        --output "$ffmpeg_archive"
fi
printf '%s  %s\n' "$ffmpeg_sha" "$ffmpeg_archive" | sha256sum --check
if [[ ! -d "$out/deps/ffmpeg-7.1.1" ]]; then
    tar -xJf "$ffmpeg_archive" -C "$out/deps"
fi
mkdir -p "$out/ffmpeg-build"
(
    cd "$out/ffmpeg-build"
    "$out/deps/ffmpeg-7.1.1/configure" --prefix="$out/deps/ffmpeg-install" \
        --target-os=android --arch=aarch64 --enable-cross-compile \
        --cc="$toolchain/aarch64-linux-android29-clang" --ar="$toolchain/llvm-ar" \
        --nm="$toolchain/llvm-nm" --ranlib="$toolchain/llvm-ranlib" --strip="$toolchain/llvm-strip" \
        --disable-autodetect --disable-everything --disable-programs --disable-doc --disable-network \
        --disable-avdevice --disable-avfilter --disable-swscale --disable-swresample \
        --enable-demuxer=bink --enable-decoder=bink,binkaudio_dct,binkaudio_rdft --enable-protocol=file \
        --enable-static --disable-shared --enable-pic
    make -j"$parallel"
    make install
)

test -f "$BF2_ANDROID_ENGINE_LIBRARY"
cmake -S "$root" -B "$out/build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DCMAKE_CXX_FLAGS_RELEASE="-O1 -DNDEBUG" -DCMAKE_C_FLAGS_RELEASE="-O1 -DNDEBUG" \
    -DBF2_ANDROID_ENGINE_LIBRARY="$BF2_ANDROID_ENGINE_LIBRARY" \
    -DBF2_ANDROID_LIBDIR="$out/support-libs" -DBF2_SDL3_LIBDIR="$out/support-libs" \
    -DBF2_SDL3_INCLUDE="$out/deps/SDL/include" -DBF2_DXVK_ROOT="$out/deps/dxvk" \
    -DBF2_DXVK_D3D9="$out/support-libs/libdxvk_d3d9.so" \
    -DBF2_VKD3D_SHADER="$out/support-libs/libvkd3d-shader.so" \
    -DBF2_VKD3D_INCLUDE="$out/deps/vkd3d" -DBF2_FFMPEG_ROOT="$out/deps/ffmpeg-install"
cmake --build "$out/build" --parallel "$parallel" --target bf2_renddx9_portable
"$toolchain/llvm-strip" --strip-unneeded -o "$out/arm64-v8a/libRendDX9.so" \
    "$out/build/bin_portable/libRendDX9.so"
python3 - "$root" "$ndk" "$out" "$BF2_ANDROID_ENGINE_LIBRARY" <<'PY'
import hashlib
import json
import pathlib
import subprocess
import sys

root, ndk, out, engine = map(pathlib.Path, sys.argv[1:])
def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

record = {
    "source_commit": subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip(),
    "source_dirty": bool(subprocess.check_output(["git", "-C", str(root), "status", "--porcelain"], text=True)),
    "ndk": (ndk / "source.properties").read_text(),
    "abi": "arm64-v8a",
    "android_api": 29,
    "optimization": "-O1 -DNDEBUG",
    "floating_point_contraction": "off",
    "engine_sha256": sha256(engine),
    "renderer_sha256": sha256(out / "arm64-v8a/libRendDX9.so"),
    "ffmpeg_source_sha256": sha256(out / "deps/ffmpeg-7.1.1.tar.xz"),
    "sdl_revision": "147a8ee32dbf9ac02f3794964490687b6bbda1bc",
    "dxvk_headers_revision": "ab3631f80d9cd493676d4342bf614028c72e7484",
    "vkd3d_headers_revision": "9f8c4ae4d35c7ff4153267aba9618c026f5f5583",
    "support_libraries": {p.name: sha256(p) for p in sorted((out / "support-libs").glob("*.so"))},
}
(out / "arm64-v8a/renderer-build.json").write_text(json.dumps(record, indent=2) + "\n")
PY
