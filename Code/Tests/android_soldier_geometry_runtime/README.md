# Android soldier geometry ABI regression

This checks the kit attachment bug reported in the Thor screenshots and log
`BF2_Android_Log_20261119_221511.txt`. `Soldier::pickUpKit` calls a local geometry
adapter. Its x86 fastcall signature included an unused EDX argument; carrying that
argument into ARM64 supplied a null matrix pointer and the pointer's low word as
the bone count. The body animation adapter in `SoldierAnimation.cpp` already used
the correct native signature, so the soldier could animate while its equipment
remained detached.

The source change is a narrow port of the native argument correction in
[kr4uzi/OpenBF2 d6d0ba713759](https://github.com/kr4uzi/OpenBF2/commit/d6d0ba7137593094e0c6ba913fd7ceb313460c22).
This Android renderer keeps the PE geometry slot order. No vtable, data layout,
file format, protocol, animation rule, or shader math changes are made.

The fixture compiles the complete production `Soldier.cpp` translation unit with
the configured client flags. Link-time garbage collection retains the four
adapter calls used by the fixture. It links the actual Android `libmain.so` and
`libRendDX9.so`; the latter's real SkinnedMesh constructor allocates its palettes
and its real `updateBonePalettes` combines the stock inverse-bind records with
the stock skeleton's live transforms. A controlled pose translation checks that
the borrowed matrices remain live after attachment. A null attachment checks the
existing reset behavior. Scalar receivers check FOV, first-person flag, and LOD
argument delivery. The only rendering singleton provider is the frame counter.

Use Android NDK r28c and the same configured builds as the libraries being tested.
The runner must execute ARM64 Android binaries with Bionic and honor `BFU_LIBS`
(the Android/QEMU runner used for the other Android fixtures does so).

```bash
export BF2_ANDROID_RUNNER=/path/to/android-run
python3 Code/Tests/android_soldier_geometry_runtime/run.py \
  /path/to/client/build /path/to/renderer/build \
  /path/to/matched/arm64-v8a /path/to/support-libs \
  /path/to/Objects_client.zip /path/to/isolated-results
```

The stock 1.50 archive supplies the 3p skeleton and all skinned US/MEC kit variants,
including helmet-only geometry. Ground-kit variants with no bones are skipped.
No game data is checked in. Exact compiler commands, input hashes, extracted
fixtures and individual results are saved in the output directory.

For a negative control, extract the old `Soldier.cpp` and add
`--soldier-source /path/to/Soldier-before.cpp`. The published `f542e5e` source
fails pointer, count, palette, first-person enable, and both nonzero/automatic LOD
checks: six of twelve checks per kit variant. The patched source passes.

This establishes the production host-to-renderer attachment and palette behavior
on Android/Bionic. It does not run a full kit pickup session, render the equipment
on an Adreno GPU, or establish that every visual issue in the screenshots is fixed.
