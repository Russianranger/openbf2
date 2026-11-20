# Portable effect structures and bundled-mesh lighting

The Thor screenshots showed an entirely black Humvee and rifle while terrain, buildings and
the soldier's sleeve remained textured. Both accompanying logs confirm that the relevant
diffuse textures loaded. They also contain 264 reports that `UserData` was absent from the
effect, followed by 264 missing-constant reports for that same name.

The portable effect runtime discarded HLSL structure layouts. A named `Light Lights[1]`
became a single float, so `SetValue` retained only the first four bytes of the renderer's
light record. Its anonymous `UserData` structure was absent entirely. The original
`RaShaderBMCommon.fx` declares seven UV matrices inside that block. Light color, direction,
ambient intensity and animated UV matrices consequently never reached the expected registers.

This intentional Android/native correction implements the relevant
[D3DX SetValue contract](https://learn.microsoft.com/en-us/windows/win32/direct3d9/id3dxbaseeffect--setvalue)
and [constant-table layout](https://learn.microsoft.com/en-us/windows/win32/direct3d9/d3dxconstant-desc).
Named and anonymous numeric structures retain tightly packed CPU data. Structure members
and array elements share that data through ordinary parameter handles. Shader upload walks
the compiler's CTAB types, pads vector members to separate registers and respects each
matrix's row/column orientation. The original shader files, renderer light-record ABI,
serialized meshes and public effect-vtable slots are unchanged.

`main.cpp` includes the production portable effect source, compiles real HLSL with the same
vkd3d compiler used by the APK, and records ordinary state-manager constant uploads. It covers
the vanilla 60-byte light structure (the renderer supplies a 68-byte record), all seven
448-byte UV matrices, nested structures, two-element light arrays, row-major matrices,
member writes through array handles, vertex/pixel stages and the unchanged ordinary matrix path.

```bash
bash Code/Tests/portable_effect_structs/run.sh \
    /path/to/dxvk /path/to/vkd3d/include /path/to/vkd3d/libs /path/to/private/output
```

Use matching compiler headers and library. `CXX` can select the NDK ARM64 compiler;
`BF2_EFFECT_RUNNER` selects an Android runner accepting one executable argument, with library
directories in `BFU_LIBS`. `BF2_EFFECT_SOURCE` selects an unmodified baseline effect source.
The fixture returns failure on the baseline rather than accepting the broken result.

| Verification | Baseline `a060521a` | Structure fix |
| --- | ---: | ---: |
| Android 13 Bionic, NDK r28c, APK vkd3d | 11/64 | 64/64 |
| Native x86-64, pinned Wine vkd3d source | — | 64/64 |
| Existing Android effect-state suite | — | 40/40 |

`humvee_raster.cpp` additionally loads the original EA 1.50 Humvee mesh, its DXT5 diffuse
texture and the unmodified `RaShaderBM.fx` through production D3DX, native DXVK 2.7.1 and
Mesa lavapipe. It draws the third-person LOD0 body material with identity part transforms,
an orthographic view and directional/ambient lighting, using the uncompressed source vertex
declaration. The matrix, material and light setters use the production effect implementation;
no effect upload or shader function is replaced. It selects a non-hemi, non-bump lighting
permutation to isolate light delivery. It does not simulate the complete map lighting setup,
vehicle articulation, gameplay loop or physical handheld GPU.

The local inputs came from the EA 1.50 patch whose MD5 is
`513a415dbca69f995e866a9dc7cbd305`. No game content or generated screenshots are checked in.
Extract the private mesh payload with the companion
`Code/Tests/bundled_uv_raster/prepare_stock.py`; it emits `vertices.bin`, `indices.bin` and
provenance in `mesh.json`. The raster fixture checks the expected Humvee payload sizes.

```bash
bash Code/Tests/portable_effect_structs/build_raster.sh \
    /path/to/dxvk /path/to/vkd3d/include /path/to/vkd3d/libs \
    /path/to/native-dependencies /path/to/dxvk-d3d9/libs /path/to/private/output

DXVK_WSI_DRIVER=SDL2 SDL_VIDEODRIVER=x11 /path/to/private/output/humvee-raster \
    /path/to/stock/shaders/bf2 /path/to/private/humvee \
    /path/to/USJEP_HMMWV_c.dds /path/to/private/humvee.ppm
```

Supply an available X display, Vulkan ICD and matching libraries through the usual
`DISPLAY`, `VK_ICD_FILENAMES` and `LD_LIBRARY_PATH` environment variables. Repeat with the
stock `xpack` shader directory, which is the shader path reported by the Thor log. Its
expanded light structure is 68 bytes. Both shader families produced the same measurements:

| Humvee pixels | Baseline | Structure fix |
| --- | ---: | ---: |
| Covered by the body material | 100,854 | 100,854 |
| Completely black | 100,854 | 277 |
| Nonzero textured/illuminated color | 0 | 100,577 |

The baseline image is the reported black silhouette; the fixed image retains the same
geometry coverage and shows the original desert texture and directional shading. The
remaining black pixels include dark texels. The separate bundled-UV raster suite exercises
the tank's original mesh and shader UV functions. These tests establish the affected
rendering boundaries, not a full Gulf of Oman match on a Thor device. Existing unrelated
portable-effect limitations remain, including the xpack compiler's `$completeFog` diagnostic
in this isolated fixture and unsupported legacy assembly permutations.

## Checking the packaged library

`apk_probe.cpp` contains no effect implementation or renderer source. It loads the supplied
`libmain.so` and `libRendDX9.so`, resolves `D3DXCreateEffect` from the renderer and uses only
public effect-vtable calls. This permits checking the actual extracted APK library rather
than compiling the production source again.

```bash
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++" \
    -std=c++14 -O1 -g Code/Tests/portable_effect_structs/apk_probe.cpp -ldl -o "$out/apk-effect-probe"
BFU_LIBS="$extracted_libs" "$android_runner" "$out/apk-effect-probe" \
    "$extracted_libs/libmain.so" "$extracted_libs/libRendDX9.so"
```

The 53 checks cover vanilla and xpack light layouts, full structure writes, every light
member, both elements of a light array, all seven UV matrices, member updates sharing their
parent storage and array bounds. The portable runtime does not implement `GetValue`; the
probe instead reads integer-valued float fixtures losslessly through its supported public
`GetIntArray` on numeric member handles. It does not invoke that unsupported method or
replace any effect operation. It verifies the binary's parsing/storage ABI; the separate
compiler/upload and raster tests above establish shader-register and pixel behavior.

The rebuilt raw ARM64 renderer passed 53/53 checks on Android 13 Bionic. The previous APK's
renderer fails all four structured-layout descriptors and has no `UserData` handle, so the
same probe distinguishes the shipped defect from the rebuilt library. The final APK verifier
must repeat the probe against the signed APK's extracted bytes and record their hashes.
