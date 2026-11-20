#!/usr/bin/env bash
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
deps=$(cd "${1:?renderer dependency directory (dxvk and SDL)}" && pwd)
out=$(mkdir -p "${2:?isolated output directory}" && cd "$2" && pwd)
source=${BF2_TERRAIN_SOURCE:-$root/Code/BF2/Geom/Terrain.cpp}
cxx=${CXX:-c++}
flags=(-std=c++14 -O1 -ffunction-sections -fdata-sections -fpermissive -w
 -DBF2_IMAGE_RENDDX9=1 -DBF2_PLUGIN_ABI_NATIVE=1 -DDEDICATED=0 -DDXVK_NATIVE -DBF2_EDX_PARAM=
 -include "$root/Code/BF2/Core/BuildConfig.h" -include "$root/Code/BF2/Core/PlatformCompat.h"
 -include "$root/Code/BF2/Core/PortableClient.h" -include "$root/Code/BF2/PortShim/RendCompat.h"
 -I"$deps/dxvk/include/native/windows" -I"$deps/dxvk/include/native/directx" -I"$deps/SDL/include"
 -I"$root/Code/BF2/PortShim" -I"$root/Code/BF2")
"$cxx" "${flags[@]}" -c "$source" -o "$out/terrain.o"
"$cxx" "${flags[@]}" "$here/main.cpp" "$out/terrain.o" -Wl,--gc-sections -o "$out/terrain-lod-check"
if [ -n "${BF2_ANDROID_RUNNER:-}" ]; then
 "$BF2_ANDROID_RUNNER" "$out/terrain-lod-check" "${@:3}"
else
 "$out/terrain-lod-check" "${@:3}"
fi
