# Portable effect state commits

The Android portable D3DX runtime previously uploaded shader constants on `CommitChanges`
but left pass render states at their `BeginPass` values. The production path in
`RenderQueues.cpp` begins a pass before `RaShader::applyTemplateParameters` and
`applyInstanceParameters`. `RaShader::draw` then calls `stateManagerPreDrawApply`, whose
first step is `shaderManagerCommitCurrentEffects`.

Battlefield 2 1.50's `RaShaderBM.fx` binds `AlphaBlendEnable`, `AlphaTest`, `AlphaTestRef`
and `DepthWrite` to pass render states. The common effect starts with blending disabled
and depth writes enabled. Ignoring later material values can draw the transparent part
of a zoomed weapon's blur texture as an opaque rectangle. The Thor report showed this
rectangle, and its log confirmed that the M4 DXT5 blur texture loaded successfully.
This is a diagnosed portable-runtime defect consistent with that report; it is not a
claim that a complete match has been rerun on the handheld.

The intentional portable-runtime fix follows the documented
[ID3DXEffect::CommitChanges contract](https://learn.microsoft.com/en-us/windows/win32/direct3d9/id3dxeffect--commitchanges).
It resolves pass render, texture-stage, sampler, texture and explicit constant values
on commit. It submits only changes from the effect's previously applied values. This
distinction preserves direct transparent-queue overrides for unchanged effect states.
`BeginPass` always resets the comparison. Repeated assignments are resolved before
submission, so the final assignment to a device state wins. Shader compilation, shader
selection and reflected constant uploads retain their existing paths. No game file
format, network protocol or exported effect-vtable slot changes.

The fixture compiles the production `D3dxEffectPortable.cpp` and `D3dShaderAssembler.cpp`
and creates its effect through the public `D3DXCreateEffect` entry point. Its recording
state manager observes the same ABI used by the renderer. It verifies both directions
of the transparent/opaque material transition; alpha-test threshold and depth writes;
unchanged direct overrides; repeated assignments; samplers at multiple stages; texture
binding/unbinding; explicit constant registers; and pass restart/reset. No renderer or
effect behavior is substituted in production.

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r28c
export BF2_ANDROID_RUNNER=/path/to/android-run
export BFU_LIBS=/path/to/matching/libc++/directory
bash Code/Tests/portable_effect_states/run.sh \
    /path/to/renderer/deps /path/to/renderer/support-libs /path/to/isolated/output
```

`deps` is the dependency tree prepared by `android/build_native_renderer.sh` and must
contain DXVK's native D3D9 headers and `vkd3d_shader.h`. `support-libs` must contain the
matching Android `libvkd3d-shader.so`. The runner accepts an ARM64 Android executable and
uses `BFU_LIBS` for additional runtime library directories. `CXX` can override the NDK
compiler. `BF2_EFFECT_SOURCE` can select an unmodified baseline source for comparison.

Validation on Android 13 Bionic under AArch64 QEMU, compiled with NDK r28c/API29 at `-O1`:

| Production source | Checks | Failures |
| --- | ---: | ---: |
| `f542e5e` baseline | 40 | 7 |
| State-commit fix | 40 | 0 |

The baseline failures include alpha blend, alpha test, depth writes, sampler parameters,
texture-stage parameters, texture binding and explicit pass constants. This fixture
establishes the effect state boundary; it does not exercise the complete game loop,
retail map rendering, or physical Thor graphics driver. Unsupported effect expressions
and shader compiler diagnostics remain independent portable-renderer limitations.

An independent raster fixture also tested the production effect runtime with native
DXVK 2.7.1, Mesa lavapipe and the original EA 1.50 M4 DXT5 blur texture. After the same
`BeginPass -> SetBool -> CommitChanges` sequence, baseline left alpha blending disabled
and exposed zero pixels of the blue background. The fixed runtime enabled blending and
exposed 37,315 background pixels in the 256×256 render. The unchanged direct-renderer
override case exposed the same 37,315 pixels before and after the fix. The texture was
used only as a local fixture; it is not included in this repository or the APK.
