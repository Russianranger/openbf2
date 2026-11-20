# Native terrain LOD regression

`Terrain::loadLodTablesAndBaseGrid` previously used 416-byte records for branch nodes on a
64-bit target, but retained a 400-byte stride for leaf nodes. Loading leaf 21 started inside
branch 20. The resulting table had four damaged child pointers and invalid leaf draw ranges;
most LOD-0 leaves had no indices to draw. The fix uses `kTerrainLodRecordBytes` for both loops.
This agrees with upstream kr4uzi/OpenBF2 commit
`95c170955c8e48ec61b4dc4635eb11db1bb0ac58`'s native terrain table fix. No shader, culling,
geometry precision, depth state, or quality setting changes are required.

The test compiles the complete production `Terrain.cpp` and links its real loader using section
GC. Only the input stream and GPU buffer/declaration allocation seams are fixtures. A zeroed,
aligned Terrain state block supplies the fields used by this nonvirtual loader; it does not
construct a complete renderer or execute the Terrain vtable. The independent expected table is
a plain `85 x 96` integer array. The test checks every record, every child link, and the exact
index-buffer bytes. Its allocator retains Android's existing capacity padding, so this is a
stride regression test, not an artificial allocation failure.

## Loader check

From the engine root, with the renderer dependencies fetched:

```sh
Code/Tests/terrain_lod_runtime/run.sh "$RENDERER_DEPS" /tmp/terrain-lod
```

This defaults to a generated input with distinct values in every populated table cell and needs
no game data. `CXX` selects the compiler. To run on Android, select the NDK's ARM64 Clang and set
`BF2_ANDROID_RUNNER` to an executable accepting the ELF path and arguments. The runner must
supply Bionic and `libc++_shared.so`. `BF2_TERRAIN_SOURCE` can point at the unchanged baseline
`Terrain.cpp`; baseline execution should exit 1.

For a stock v0x1a map, prepare private input and optionally capture its loaded index ranges:

```sh
python3 Code/Tests/terrain_lod_runtime/prepare_stock.py /path/to/client.zip /tmp/map-lod.bin
Code/Tests/terrain_lod_runtime/run.sh "$RENDERER_DEPS" /tmp/terrain-lod \
  /tmp/map-lod.bin /tmp/map-ranges.bin
```

The preparer rejects other versions; the public 2005 Gulf of Oman demo uses v0x19 and does not
exercise the faulty v0x1a loop. No EA map or shader data belongs in the repository.

## Real raster check

`raster.cpp` compiles the production portable effect, loads unmodified stock `TerrainShader.fx`
and its includes, and executes its `Hi_Terrain` depth/color pass on native DXVK D3D9 and Vulkan.
A flat 129 x 129 grid uses the production 24-byte terrain vertex declaration. The four sibling
leaf ranges numbered 4 through 7 are drawn in their normal grid positions. This isolates a
visible subsection, so oversized, unrelated corrupt ranges cannot hide its missing triangles.
The stock clockwise culling, depth test, and depth writes remain enabled.

```sh
Code/Tests/terrain_lod_runtime/build_raster.sh "$RENDERER_DEPS" "$NATIVE_DEPS_ROOT" \
  "$NATIVE_DXVK_LIBRARY_DIR" /tmp/terrain-raster
# Use an available X display (Xvfb works), SDL2, and a Vulkan implementation.
export SDL_VIDEODRIVER=x11 DXVK_WSI_DRIVER=SDL2
/tmp/terrain-raster/terrain-raster-check /path/to/stock/shaders /tmp/result /tmp/map-ranges.bin
```

`NATIVE_DEPS_ROOT` supplies `usr/include/SDL2` and `usr/lib/x86_64-linux-gnu` with SDL2 and
libvkd3d-shader. `SDL2_LIBRARY` may name a specific SDL2 shared library if the development
symlink is unavailable. For a software implementation, select its ICD through
`VK_ICD_FILENAMES` and include its libraries in `LD_LIBRARY_PATH`. The test writes
`/tmp/result-terrain.ppm` and reports colored pixels, triangle counts, and empty leaves.
Baseline captures should exit 1; fixed captures should exit 0.

## Recorded validation, 2026-11-20

Production baseline: `c4a565fb`; fix: `21ee7be2`. Real input: Operation Road Rage's
`client.zip` from the publicly distributed EA BF2 1.50 patch. Client archive SHA256:
`dfbbc778b8076430fd6125bc270cce106798dd7b269dcaeb14d67e349c489131`.
Uncompressed `terraindata.raw` SHA256:
`51da69389cc0c56226650fc2e339580ebb8a46f5582faba25adff0ad04b2bac8`.
Its LOD section starts at byte 658 and contains 388,608 16-bit indices.

| Check | Baseline | Fixed |
| --- | ---: | ---: |
| Incorrect records of 85 | 65 | 0 |
| Incorrect leaf records of 64 | 64 | 0 |
| Leaves with zero LOD-0 count | 49 | 0 |
| Incorrect child links | 4 | 0 |
| Index buffer matches input | Yes | Yes |
| Four-leaf subsection: empty leaves | 3 | 0 |
| Four-leaf subsection: submitted triangles | 512 | 2,048 |
| Four-leaf subsection: colored pixels | 13,456 | 53,361 |

The loader results reproduced on x86-64 Linux and ARM64 Android 13 Bionic under QEMU, using NDK
r28c. The fixed generated input also passes. The raster results used native DXVK 2.7.1 and
lavapipe (LLVM 19.1.7), with stock 1.50 shaders. These prove the corrupted production ranges
cause visible missing terrain and the fix repairs them. They are not a complete gameplay run
on Thor or a Karkand map capture; matching Karkand client terrain was unavailable. The user
screenshots and successful terrain loads are consistent with this defect, and real-device
validation remains necessary for the entire scene.
