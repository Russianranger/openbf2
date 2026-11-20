#!/usr/bin/env bash
set -euo pipefail
if [ "$#" != 3 ]; then
    echo "usage: $0 SDL_SOURCE SDL_STATIC_LIBRARY OUTPUT_DIRECTORY" >&2
    exit 2
fi
root=$(cd "$(dirname "$0")/../.." && pwd)
mkdir -p "$3"
out=$(cd "$3" && pwd)
"${CXX:-c++}" -std=c++17 -DDEDICATED=0 -include "$root/Code/BF2/Core/BuildConfig.h" \
    -I"$1/include" "$root/Code/Tests/portable_pointer_runtime.cpp" "$2" \
    -lpthread -ldl -lm -o "$out/portable-pointer"
"$out/portable-pointer"
