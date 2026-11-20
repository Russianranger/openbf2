#!/usr/bin/env bash
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
dxvk=$(cd "${1:?DXVK source directory}" && pwd)
headers=$(cd "${2:?vkd3d include directory}" && pwd)
compiler=$(cd "${3:?vkd3d library directory}" && pwd)
native=$(cd "${4:?native dependencies prefix with usr/include/SDL2}" && pwd)
driver=$(cd "${5:?native DXVK D3D9 library directory}" && pwd)
out=$(mkdir -p "${6:?isolated output directory}" && cd "$6" && pwd)
effect=${BF2_EFFECT_SOURCE:-$root/Code/BF2/PortShim/D3dxEffectPortable.cpp}
sdl=${SDL2_LIBRARY:-$native/usr/lib/x86_64-linux-gnu/libSDL2.so}
"${CXX:-c++}" -std=c++14 -O1 -g -DDXVK_NATIVE -DDEDICATED=0 -DBF2_IMAGE_RENDDX9=1 \
    -include "$root/Code/BF2/Core/BuildConfig.h" \
    -I"$dxvk/include/native/windows" -I"$dxvk/include/native/directx" -I"$headers" \
    -I"$native/usr/include" -I"$native/usr/include/x86_64-linux-gnu" -I"$native/usr/include/SDL2" \
    -I"$root/Code/BF2/PortShim" -DBF2_EFFECT_SOURCE=\""$effect"\" \
    "$here/humvee_raster.cpp" "$root/Code/BF2/PortShim/D3dShaderAssembler.cpp" \
    -L"$driver" -ldxvk_d3d9 -L"$compiler" -lvkd3d-shader "$sdl" -o "$out/humvee-raster"
