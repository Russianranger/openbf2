#!/usr/bin/env bash
# Build a strict ARM64 fixture and run it with an Android/Bionic runner.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
native=$(cd "${1:?rebuilt library directory}" && pwd)
support=$(cd "${2:?support library directory}" && pwd)
out=$(mkdir -p "${3:?isolated output directory}" && cd "$3" && pwd)
ndk=${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}
runner=${BF2_ANDROID_RUNNER:?Set BF2_ANDROID_RUNNER to an ARM64 Android/Bionic launcher}
cxx="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++"
"$cxx" -std=c++14 -fms-extensions -DDEDICATED=0 -DBF2_PLATFORM_POSIX \
    -DBF2_PLUGIN_ABI_NATIVE=1 -DBF2_IMAGE_SWIFFPLAYER=1 -DBF2_EDX_PARAM= \
    -include "$root/Code/BF2/Core/BuildConfig.h" \
    -include "$root/Code/BF2/Core/PlatformCompat.h" \
    -include "$root/Code/BF2/Core/PortableClient.h" \
    -I"$root/Code/BF2" -I"$root/Code/BF2/External" \
    -I"$root/Code/BF2/External/gameswf" -I"$root/Code/BF2/PortShim" \
    "$here/main.cpp" "$here/swiff_probe.cpp" \
    -L"$native" -lmain -lSwiffPlayer -ldl -Wl,-rpath-link,"$support" -o "$out/ui-check"
python3 "$here/make_fixture.py" "$out/font.swf"
sha256sum "$native/libmain.so" "$native/libSwiffPlayer.so" "$native/libRendDX9.so" > "$out/libraries.sha256"
BFU_LIBS="$native:$support" "$runner" "$out/ui-check" "$out/font.swf" "$native/libRendDX9.so" 2>&1 | tee "$out/result.log"
