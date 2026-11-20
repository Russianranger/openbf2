# Android UI plugin runtime check

This content-free fixture links to rebuilt ARM64 `libmain.so` and `libSwiffPlayer.so`
and loads rebuilt `libRendDX9.so` with `RTLD_NOW`. The tested code executes on
Android Bionic, including when using QEMU's user-mode ARM64 runner.

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r28c
export BF2_ANDROID_RUNNER=/path/to/android-bionic-runner
bash Code/Tests/android_ui_runtime/run.sh /path/to/rebuilt-libs /path/to/support-libs /path/to/output
```

The runner receives an executable and its arguments. It must supply the Android
dynamic linker/system libraries and prepend `BFU_LIBS` to the guest library search
path. Support libraries include the matching SDL3, Python, DXVK, vkd3d and libc++.
The output records exact tested library hashes and all assertions.

The fixture uses actual engine `ClassManager`, `EventManager`, `SettingsRepostitory`
and `SwiffHost` objects. It calls the production Swiff module's init/close methods;
it does not substitute a mock vtable or call the full game/plugin startup routine.
A generated SWF contains one exported square glyph, parsed and rasterized by the
actual Flash library. The renderer's real pending-font service receives an observer
callback to verify generation changes and cleanup. The real renderer font-cache
factory and event listener receive resolution events through the real event manager.
This covers the empty-cache lifecycle; populated-font reload and GPU texture creation
remain outside this fixture.

Pointer checks call the actual `SwiffPlayer::notifyMouse` and real host viewport
getter. This fixture accesses the production class's private fields to supply
synthetic movie dimensions and observe results, without replacing its methods or
inventing a layout. It supplies no display/movie instance for these pointer checks.

Validated on Android 13 Bionic/ARM64 under QEMU: 23/23 checks, including a 256-pixel
glyph coverage sum, generation-based retry, released movie references, menu edges,
margin button release, changed resolution, and service/event teardown. The fixture
uses no original game data, GPU or window. It does not validate final texture
upload, rendered pixels, Flash button actions, gameplay, or physical Thor input.
