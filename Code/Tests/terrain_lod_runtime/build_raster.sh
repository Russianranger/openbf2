#!/usr/bin/env bash
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
renderer=$(cd "${1:?renderer dependencies (dxvk and vkd3d headers)}" && pwd)
native=$(cd "${2:?native dependency root (usr/include/SDL2, usr/lib/x86_64-linux-gnu)}" && pwd)
dxvk=$(cd "${3:?native DXVK D3D9 library directory}" && pwd)
out=$(mkdir -p "${4:?output directory}" && cd "$4" && pwd)
effect=${BF2_EFFECT_SOURCE:-$root/Code/BF2/PortShim/D3dxEffectPortable.cpp}
sdl2=(-lSDL2)
if [ -n "${SDL2_LIBRARY:-}" ]; then sdl2=("$SDL2_LIBRARY"); fi
"${CXX:-c++}" -std=c++14 -O1 -DDXVK_NATIVE -DDEDICATED=0 -DBF2_IMAGE_RENDDX9=1 \
 -include "$root/Code/BF2/Core/BuildConfig.h" \
 -I"$renderer/dxvk/include/native/windows" -I"$renderer/dxvk/include/native/directx" \
 -I"$renderer/vkd3d" -I"$native/usr/include" -I"$native/usr/include/SDL2" \
 -DBFU_EFFECT_SOURCE=\""$effect"\" "$here/raster.cpp" "$root/Code/BF2/PortShim/D3dShaderAssembler.cpp" \
 -L"$dxvk" -ldxvk_d3d9 -L"$native/usr/lib/x86_64-linux-gnu" -lvkd3d-shader "${sdl2[@]}" \
 -Wl,-rpath,"$dxvk" -Wl,-rpath,"$native/usr/lib/x86_64-linux-gnu" -o "$out/terrain-raster-check"
