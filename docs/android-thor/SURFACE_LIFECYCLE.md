# Android surface pause mitigation

The November 19 device log ends in `rendererPresent`/DXVK after `VK_ERROR_SURFACE_LOST_KHR`. The log does not establish which Android callback preceded that crash. The inspected SDL Android code queues a pause before releasing its `ANativeWindow`; the explicit render-thread acknowledgement in that release path applies to OpenGL/EGL, while this client uses Vulkan through DXVK.

The Android activity now closes a shared native presentation gate before pause, focus loss and `surfaceDestroyed`. No new scene or normal client frame starts while that gate is closed. Every synchronous swap-chain Present, including a retry, checks and holds the same gate. Closing rejects new Present calls immediately and waits up to 250 ms for an existing call to drain. It logs a timeout instead of waiting indefinitely if a driver call stalls. This is a mitigation, not proof that every asynchronous GPU operation has completed; a timed-out Present and driver-side surface errors remain possible.

The gate reopens only for a resumed, focused activity after SDL has accepted a nonzero, valid replacement surface. Each Present additionally checks SDL's current Android native-window property. The SDL window object remains the same across surface recreation, while SDL installs the replacement `ANativeWindow`; DXVK's SDL surface callback reads that window again when its existing surface-loss path recreates the Vulkan surface. No raw native-window pointer is cached by this patch. There is no UI-thread wait inside SDL's event pump or device-loss recovery, and the bounded Present drain cannot create an indefinite UI/renderer lock cycle. SDL's ordinary blocking-on-pause behavior remains enabled.

Relevant primary code/documentation: [SDL Android lifecycle guidance](https://wiki.libsdl.org/SDL3/README-android), `SDL/src/core/android/SDL_android.c` (`onNativeSurfaceCreated`/`onNativeSurfaceDestroyed`), `SDL/android-project/app/src/main/java/org/libsdl/app/SDLSurface.java`, and DXVK `src/dxvk/dxvk_presenter.cpp` plus `src/wsi/sdl3/wsi_window_sdl3.cpp`.

## Regression coverage

- `Code/Tests/android_presentation_gate.cpp` compiles with C++17 and threads. It tests initial closure, rejection during an in-flight drain, completion acknowledgement, a bounded stalled driver fixture, and 500 pause/resume cycles. It also runs under Android 13 Bionic.
- `android/thor/tests/SurfaceLifecycleRuntimeTest.java` runs actual production `GameActivity` callbacks with Robolectric Android 13. SDL/native surface calls are observed fixture boundaries. It checks close-before-SDL callback ordering, no resume without a surface, invalid/zero-size rejection, and resume after a replacement surface. Use the compiled APK Java class directory (including SDL) as input:

  ```sh
  python3 android/thor/tests/run_surface_runtime.py \
    --classes /path/to/apk/classes --out /path/to/surface-tests \
    --android-sdk "$ANDROID_HOME"
  ```

These tests do not exercise an Android Vulkan driver. Physical acceptance remains: enter a match, open/cancel the log document picker, background/foreground repeatedly, lock/unlock, and confirm rendering, menu pointer, controls and audio recover without surface-loss crashes or drain timeouts. The device log should show `[surface] presentation suspended` and then `resumed`.
