#!/usr/bin/env bash
# Build the ARM64 game, AI, renderer and Flash player, then sign the Android application.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
port="$root/ports/android-engine"
if [[ $# == 0 || ${1:-} == --help ]]; then
    echo 'Usage: android/build_thor.sh --keystore FILE --alias NAME --storepass FILE [--keypass FILE] [--previous-apk FILE.apk] --output FILE.apk'
    exit 0
fi
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK r28c directory}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the SDK with platform android-35 and build-tools 35.0.0}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or 21}"
build=$(mkdir -p "${BF2_THOR_BUILD_DIR:-$root/out/android-thor}" && cd "${BF2_THOR_BUILD_DIR:-$root/out/android-thor}" && pwd)
jobs=${CMAKE_BUILD_PARALLEL_LEVEL:-2}
git -C "$root" submodule update --init -- ports/android-engine
BF2_NATIVE_HOST_ONLY=0 "$port/android/build_native_client.sh" "$build/client"
make -f "$root/android/aidll/Makefile" -j"$jobs" SOURCE_ROOT="$port" OUT="$build/ai" all
strip="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
"$strip" --strip-unneeded -o "$build/client/arm64-v8a/libaidll.so" "$build/ai/libaidll.so"
python3 "$port/android/thor/tools/build_apk.py" --native-dir "$build/client/arm64-v8a" "$@"
