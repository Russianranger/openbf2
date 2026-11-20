# Android native build

The production `android/build_native_client.sh` command builds `libmain.so`, its GPU
loader hooks, `libRendDX9.so` and `libSwiffPlayer.so` from this checkout. The renderer
and Flash player link with `--no-undefined` against that rebuilt host. Use the root
repository's `android/build_thor.sh` to also build the matching AI library and assemble
the signed APK. The package rejects a missing rebuilt engine, AI, renderer or Flash player.

The tested toolchain is Android NDK r28c, CMake 3.31, Ninja, Android API 29 and
`arm64-v8a`, with the shared C++ runtime. APK assembly additionally needs a JDK,
Android SDK platform 35 and build-tools 35.0.0. Build tools and the game data are
separate inputs; the native build does not bundle game content.

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r28c
export CMAKE_BUILD_PARALLEL_LEVEL=2
android/build_native_client.sh /path/to/output/client
```

Outputs for packaging are in `client/arm64-v8a`. The three adjacent `*-build.json`
records identify the source commit and cleanliness, NDK version, dependency revisions,
linked engine hash and output hashes. Unstripped images remain in the respective
`build` directories for debugging. Keep the records beside the APK build evidence.

The command verifies the existing Stage 3 archive before extracting SDL and Python
runtime libraries. The renderer builder also verifies that archive for DXVK and
vkd3d-shader, fetches pinned headers, and builds a SHA-256-verified FFmpeg Bink decoder.
`BF2_STAGE3_BUNDLE` selects the archive when it is outside the checkout.

For focused compiler work, `BF2_NATIVE_HOST_ONLY=1` builds only the host and loader
hooks. That mode does not produce a complete package. The standalone plugin commands
require `BF2_ANDROID_ENGINE_LIBRARY` to name a matching rebuilt host; the Flash builder
uses `BF2_RENDER_DEPS` to locate the renderer's dependency cache. `BF2_RENDER_OUT` and
`BF2_SWIFF_OUT` can select separate output directories for the production command.
Use distinct build directories for different source checkouts and toolchains.

After changes to a shared interface, rebuild the host and every affected plugin from
the same integrated source. A successful strict link establishes symbol compatibility;
Android dynamic loading and service-handshake checks establish their tested runtime
boundaries. They do not establish rendered menus, input behavior or stable gameplay on
a physical device. Keep those acceptance results separate.
