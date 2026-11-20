#!/usr/bin/env bash
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
deps=$(cd "${1:?renderer dependency directory}" && pwd)
support=$(cd "${2:?renderer support library directory}" && pwd)
out=$(mkdir -p "${3:?isolated output directory}" && cd "$3" && pwd)
effect=${BF2_EFFECT_SOURCE:-$root/Code/BF2/PortShim/D3dxEffectPortable.cpp}
cxx=${CXX:-${ANDROID_NDK_HOME:?}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++}
"$cxx" -std=c++14 -O1 -DDEDICATED=0 -DBF2_IMAGE_RENDDX9=1 -DDXVK_NATIVE \
    -include "$root/Code/BF2/Core/BuildConfig.h" \
    -I"$deps/dxvk/include/native/directx" -I"$deps/dxvk/include/native/windows" -I"$deps/vkd3d" \
    -I"$root/Code/BF2/PortShim" "$here/main.cpp" "$effect" \
    "$root/Code/BF2/PortShim/D3dShaderAssembler.cpp" \
    -L"$support" -lvkd3d-shader -o "$out/effect-state-check"
BFU_LIBS="$support:${BFU_LIBS:-}" "${BF2_ANDROID_RUNNER:?Android/Bionic runner}" "$out/effect-state-check" 2>&1 | tee "$out/result.log"
