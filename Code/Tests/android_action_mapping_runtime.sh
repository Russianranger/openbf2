#!/usr/bin/env bash
set -euo pipefail
if [ "$#" != 2 ] || [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "usage: ANDROID_NDK_HOME=... $0 ARM64_NATIVE_LIBRARIES OUTPUT_DIRECTORY" >&2
    exit 2
fi
root=$(cd "$(dirname "$0")/../.." && pwd)
libs=$(cd "$1" && pwd)
mkdir -p "$2"
out=$(cd "$2" && pwd)
compiler="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++"
"$compiler" -std=c++17 -DDEDICATED=0 -DBF2_PLUGIN_ABI_NATIVE=1 -D_LINUX \
    -include "$root/Code/BF2/Core/BuildConfig.h" -include "$root/Code/BF2/Core/PlatformCompat.h" \
    -I"$root/Code/BF2" -I"$root/Code/BF2/PortShim" \
    "$root/Code/Tests/android_action_mapping_runtime.cpp" -nostdlib++ \
    -L"$libs" -lmain -lc++_shared -lSDL3 -Wl,-rpath-link,"$libs" \
    -o "$out/android-action-mapping"
