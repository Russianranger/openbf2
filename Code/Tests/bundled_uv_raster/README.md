# Stock bundled-mesh UV regression

This checks the portable effect binding used by the M1A2's animated UV materials. It renders the
actual EA 1.50 M1A2 vertices, indices and diffuse DDS through the unchanged stock
`RaShaderBM.fx::calcUVRotation` / `getSkinnedUVMatrix` functions, the production portable effect
implementation, the pinned vkd3d HLSL compiler, native DXVK and Vulkan. No EA assets are included.

The stock `RaShaderBMCommon.fx` declares an anonymous `UserData` structure containing seven
`float4x4` UV matrices. Before the structured-parameter fix, the effect parser omitted this
parameter entirely. `SetValue("UserData", 448 bytes)` failed and the reflected vertex constants
were left untouched. This both freezes animation and permits constants left by another draw to
shift/stretch the hull texture.

## Scope and controls

- Original third-person geometry is selected from all four LODs. Five animated-UV materials use
  36,106 source vertices (the full mesh count), 56-byte source records and original 16-bit indices.
- The fixture appends a CPU reference UV to each original source record. It alternates between the
  stock GPU UV function and the independently computed affine reference, then reads back pixels.
- Identity matrices and an animated phase (wheel rotation plus independent track translation) are
  tested. The latter follows `BundledMesh::setUvRotation` and `setUvTranslation`'s matrix slots.
- A second run seeds the shared vertex-constant bank with a deterministic previous-draw transform.
  This is an intentionally controlled prior-state input, not a captured Thor frame. The effect
  must correctly bind its own values regardless of that prior state.
- A fixed camera, identity sub-part transforms, diffuse-only output and disabled face culling
  isolate UV correctness. These images are not complete assembled vehicles or a running map.
- This uses the original uncompressed source declaration plus the reference attribute. It does
  not exercise the production 28-byte compressed-vertex builder, world animation/physics, LOD
  selection, Android's windowing, or the Thor GPU. It does exercise the actual stock indexed
  material ranges at each LOD. No additional mesh-packing or LOD fault was established.

## Private input preparation

Supply files from a legitimate local BF2 installation or the public EA 1.50 patch. The patch
used for this check was `BF2_Patch_1_50_Full.exe`, MD5
`513a415dbca69f995e866a9dc7cbd305`. Extract `Objects_client.zip` and `Shaders_client.zip`
from its `Mods - bf2` / `Mods - xpack` InstallShield groups. Keep the full shader directory for
include resolution. The helper also supports the 48-byte Humvee source declaration for the
companion lighting test.

```sh
python3 Code/Tests/bundled_uv_raster/prepare_stock.py \
  "$assets/vehicles/Vehicles/Land/USTNK_M1A2/Meshes/ustnk_m1a2.bundledMesh" "$out/stock"
```

The helper follows the production mesh readers' field order, checks the complete file was
consumed, and emits private `vertices.bin`, `indices.bin`, `ranges.bin` and `mesh.json` files.
Its output includes each material's technique, bounds, vertex/index ranges and UV-index counts.
The live third-person M1A2 materials have all seven UV indices 0–6.

Verified SHA256 inputs:

| Input | SHA256 |
|---|---|
| `ustnk_m1a2.bundledMesh` | `24efbb160cc9dbacc8a85238e3a0444d6368c3009b93a0623ca4bf5263fdc4bb` |
| `USTNK_M1A2_c.dds` | `1abea01664cdbe6c75d7cc2c1c84f524d8a56f23c41cb36169b13296a28a7ed2` |
| xpack `RaShaderBM.fx` | `4b8dff9ec9cd310facfdc91e4a6c1c6ee3492391e407c853e2f86b0db2ab411c` |
| xpack `RaShaderBMCommon.fx` | `021652f05cbb0e3d37aaec10bf374aba07bc139aa530beba7eb1004aeb54a5a3` |

## Build and run

`build.sh` takes a local dependency root. The checked layout was:

- `dxvk/`: DXVK v2.7.1 source with its pinned native DirectX headers.
- `dxvk-build/src/d3d9/`: native SDL2-enabled `libdxvk_d3d9.so`.
- `vkd3d-wine/include/`: headers pinned by `android/build_native_renderer.sh` at Wine
  `9f8c4ae4d35c7ff4153267aba9618c026f5f5583`.
- `vkd3d-1.17/.libs/`: host `libvkd3d-shader.so`, built with the exact pinned Wine shader/compiler
  sources using the standalone 1.17 configure harness. Host adaptations only rename Wine header
  includes to the standalone `vkd3d_*` wrappers and retain the standalone COM blob glue; no HLSL
  compiler logic is changed. The soname reflects the harness, not a 1.17 shader-code baseline.
- `sysroot/usr/`: SDL2/Vulkan development headers, Xvfb and Mesa lavapipe 25.0.7 dependencies.
  The SDL2 runtime is the host `/usr/lib/x86_64-linux-gnu/libSDL2-2.0.so.0`.

```sh
bash Code/Tests/bundled_uv_raster/build.sh "$qa" "$out/tank-fixed"
# To compare a source file from another checkout, set BF2_EFFECT_SOURCE when building.
BF2_EFFECT_SOURCE="$baseline/Code/BF2/PortShim/D3dxEffectPortable.cpp" \
  bash Code/Tests/bundled_uv_raster/build.sh "$qa" "$out/tank-baseline"

bash Code/Tests/bundled_uv_raster/run.sh "$qa" "$out/tank-fixed" \
  "$assets/shaders/xpack" "$out/stock/vertices.bin" "$out/stock/indices.bin" \
  "$out/stock/ranges.bin" \
  "$assets/vehicles/Vehicles/Land/USTNK_M1A2/textures/USTNK_M1A2_c.dds" \
  "$out/fixed-prior" 1
```

The last argument is `0` for a clean constant bank or `1` for controlled prior-draw constants.
Run each binary in both modes. The fixed implementation must exit 0; the baseline is expected
to exit 1. Each run emits per-LOD/per-phase GPU and CPU PPM readbacks, pixel error counts,
constant-register diagnostics and animation-change counts. Use a different `BFU_DISPLAY_NUMBER`
if the host has a conflicting X display. The runner starts and stops its own Xvfb in the same
process namespace.

## Observed result, 2026-11-20

The comparison uses the 0.1.3 renderer at `a060521a` and the structured effect parameters in this revision.
Vulkan device: Mesa lavapipe; shader pipeline: production portable effect → pinned vkd3d →
DXVK 2.7.1 → Vulkan. A pixel is a mismatch when any color channel differs by more than two.

| Check | Baseline | Fixed |
|---|---:|---:|
| `UserData` registered / 448-byte `SetValue` | absent / E_FAIL | present / success |
| Animated LOD0, clean bank: mismatched pixels | 11,917 | 0 |
| Animated LOD1, clean bank: mismatched pixels | 12,384 | 0 |
| LOD0 identity, prior bank: mismatched / visible pixels | 51,502 / 51,589 | 0 / 51,589 |
| LOD1 identity, prior bank: mismatched / visible pixels | 51,066 / 51,176 | 0 / 51,176 |
| LOD2 identity, prior bank: mismatched / visible pixels | 55,846 / 56,032 | 0 / 56,032 |
| LOD3 identity, prior bank: mismatched / visible pixels | 56,844 / 56,984 | 0 / 56,984 |
| LOD0 pixels changed by UV animation | 0 | 11,974 |

The corrected GPU result matches the CPU reference exactly (maximum channel error zero) for
both phases at all four LODs, with either prior-state mode. The prior-state baseline visibly
stretches the stock hull texture into bands; the corrected result retains the expected texture.
Clean initial state alone does not reproduce the hull bands because most hull vertices have a
zero rotation-center offset. Device confirmation of the final APK remains necessary.

The same test on the stock `RUTNK_T90` mesh and diffuse texture also matched the
reference exactly across all four LODs, both UV phases and both prior states.
With stale constants the baseline disagreed on 39,295 of 39,450 covered pixels
at LOD0. Correct UV animation changed 6,730 LOD0 pixels. This test exercises the
texture coordinates at a fixed camera and fixed subpart transforms.
