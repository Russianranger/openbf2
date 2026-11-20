#!/usr/bin/env bash
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
qa=$(cd "${1:?QA dependencies root}" && pwd)
out=${2:?Output binary}
mkdir -p "$(dirname "$out")"
effect=${BF2_EFFECT_SOURCE:-$root/Code/BF2/PortShim/D3dxEffectPortable.cpp}
${CXX:-c++} -std=c++14 -O1 -DDXVK_NATIVE -DDEDICATED=0 -DBF2_IMAGE_RENDDX9=1 \
 -include "$root/Code/BF2/Core/BuildConfig.h" \
 -I"$qa/dxvk/include/native/windows" -I"$qa/dxvk/include/native/directx" \
 -I"$qa/vkd3d-wine/include" -I"$qa/sysroot/usr/include" -I"$qa/sysroot/usr/include/SDL2" -I"$qa/sysroot/usr/include/x86_64-linux-gnu" \
 -DBFU_EFFECT_SOURCE=\""$effect"\" "$here/raster.cpp" "$root/Code/BF2/PortShim/D3dShaderAssembler.cpp" \
 -L"$qa/dxvk-build/src/d3d9" -ldxvk_d3d9 -L"$qa/vkd3d-1.17/.libs" -lvkd3d-shader /usr/lib/x86_64-linux-gnu/libSDL2-2.0.so.0 \
 -Wl,-rpath,"$qa/dxvk-build/src/d3d9" -Wl,-rpath,"$qa/vkd3d-1.17/.libs" -o "$out"
