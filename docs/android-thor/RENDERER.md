# Android renderer build

Build the engine and renderer from the same integrated source revision. The renderer
links strictly against that engine's `libmain.so` and uses the same Android NDK and
shared C++ runtime. NDK r28c, Android API 29 and `arm64-v8a` are the tested settings.

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r28c
BF2_NATIVE_HOST_ONLY=1 android/build_native_client.sh out/android-native
export BF2_ANDROID_ENGINE_LIBRARY="$PWD/out/android-native/build/libmain.so"
android/build_native_renderer.sh out/android-renderer
```

The renderer script fetches pinned SDL and DXVK headers, pinned Wine vkd3d headers,
and a SHA-256-verified FFmpeg 7.1.1 archive. It builds the static Bink decoder and
demuxer with network support disabled. SDL3, DXVK and vkd3d-shader runtime libraries
come from the SHA-256-verified Stage 3 bundle already used by the Android client.
Set `BF2_STAGE3_BUNDLE` to use that archive from another path.

The stripped library is `out/android-renderer/arm64-v8a/libRendDX9.so`. Its adjacent
`renderer-build.json` records the source commit, source cleanliness, NDK, dependency
revisions and engine, renderer and runtime library hashes. Keep the unstripped
`build/bin_portable/libRendDX9.so` for symbolized crash reports. `BF2_RENDER_DEPS` in
the Swiff build can point at `out/android-renderer/deps` to reuse its headers.

The lighting change follows mackillerGR's commit
`f858672137ac4d6e60bf8f118202a095bf33a7ba`. Static meshes read the finished, water and
vegetation fields through their C++ types, allowing their offsets to follow the
native architecture. The shared structures and serialized files retain their
existing declarations.

The menu rendering follows [kr4uzi PR 50](https://github.com/kr4uzi/OpenBF2/pull/50).
The existing Android plugin wrappers retain their entry points and calling
conventions. Font atlases use movie outlines when available, preserve the authored
layout, and retain the original bitmap when a matching outline is unavailable.
The font resize event locates its owning object through the native member offset.

Strict linking and Android dynamic-loading checks establish dependency and symbol
compatibility. Full qualification also requires actual GPU rendering, menu and HUD
inspection, resolution changes and a session exit/reload on the target device.
