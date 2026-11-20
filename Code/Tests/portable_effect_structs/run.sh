#!/usr/bin/env bash
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
dxvk=$(cd "${1:?DXVK source directory}" && pwd)
headers=$(cd "${2:?vkd3d include directory}" && pwd)
libraries=$(cd "${3:?vkd3d library directory}" && pwd)
out=$(mkdir -p "${4:?isolated output directory}" && cd "$4" && pwd)
cxx=${CXX:-c++}
flags=()
if [[ -n ${BF2_EFFECT_CPPFLAGS:-} ]]; then read -r -a flags <<< "$BF2_EFFECT_CPPFLAGS"; fi
if [[ -n ${BF2_EFFECT_SOURCE:-} ]]; then flags+=("-DBF2_EFFECT_SOURCE=\"$BF2_EFFECT_SOURCE\""); fi
"$cxx" -std=c++14 -O1 -g -DDEDICATED=0 -DBF2_IMAGE_RENDDX9=1 -DDXVK_NATIVE \
    -include "$root/Code/BF2/Core/BuildConfig.h" \
    -I"$dxvk/include/native/directx" -I"$dxvk/include/native/windows" -I"$headers" \
    -I"$root/Code/BF2/PortShim" "${flags[@]}" "$here/main.cpp" \
    "$root/Code/BF2/PortShim/D3dShaderAssembler.cpp" \
    -L"$libraries" -lvkd3d-shader -o "$out/effect-struct-check"
if [[ -n ${BF2_EFFECT_RUNNER:-} ]]; then
    BFU_LIBS="$libraries:${BFU_LIBS:-}" "$BF2_EFFECT_RUNNER" "$out/effect-struct-check" 2>&1 | tee "$out/result.log"
else
    LD_LIBRARY_PATH="$libraries:${LD_LIBRARY_PATH:-}" "$out/effect-struct-check" 2>&1 | tee "$out/result.log"
fi
