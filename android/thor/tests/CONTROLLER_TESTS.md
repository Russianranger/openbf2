# Controller regression checks

These checks cover the intentional Android controller behavior. They do not claim
retail input recovery, physical Thor testing, GPU drawing, or complete playable-client validation.

The Android framework fixture compiles the production `HandheldInput` and `PadState`
sources and runs on Robolectric's Android 13 framework. It observes the SDL JNI boundary;
the native pointer-state getter and hardware enumeration are fixture-controlled.

```sh
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk
python3 android/thor/tests/run_controller_runtime.py --out /tmp/controller-robo
```

The SDK must include platform 35; the runtime tests use API 33. Maven, JUnit 4.13.2,
Robolectric 4.14.1, AndroidX monitor 1.7.2 and Espresso idling 3.6.1 are used. The runner
downloads the two AndroidX AARs on first use, or accepts a directory of their extracted
`monitor.jar` and `idling.jar` files with `--androidx-jars`. Use `--maven-arg=-o` with
an existing dependency cache. All generated files go under the supplied output directory.

Coverage includes the initial menu cursor without touch, right-stick movement,
RT press/hold/release, release-before-rebind when changing menus/gameplay,
continued relative gameplay aiming and WASD movement, Z/RZ and RX/RY stick layouts,
GAS/BRAKE aliases, keyboard-labeled gamepad button events, duplicate button/axis
trigger reports, disconnect/cancel cleanup, resolution changes and mixed touch input.

The native check uses the actual portable Win32/DirectInput bridge and a static build
of the pinned SDL source fetched by `android/build_native_engine.sh`. Its dummy window
reproduces SDL's dropped absolute motion in relative mode, then verifies the corrected
menu mode, position scaling, button route, reacquisition, focus release and gameplay deltas.

```sh
cmake -S /path/to/SDL -B /tmp/controller-sdl -G Ninja \
  -DSDL_SHARED=OFF -DSDL_STATIC=ON -DSDL_TESTS=OFF \
  -DSDL_UNIX_CONSOLE_BUILD=ON -DSDL_X11=OFF -DSDL_WAYLAND=OFF
cmake --build /tmp/controller-sdl
bash Code/Tests/portable_pointer_runtime.sh /path/to/SDL \
  /tmp/controller-sdl/libSDL3.a /tmp/controller-native
```

This native fixture uses SDL's real internal mouse entry points that Android's
`Android_OnMouse` calls. It must link the matching SDL static library. It does not
emulate an Android display or test the cursor's final pixels on the device.
