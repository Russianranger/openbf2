# Exact packaged ARM64 library verification

`verify.py` checks the signed APK and executes fixtures against the libraries extracted from that
exact file. It does not build a replacement engine, renderer, UI module, ADS method, or effect
implementation. Keep the output directory fresh so a previous library cannot satisfy a load.

Requirements: Android NDK r28c, Android SDK build-tools 35, a JDK on `PATH`, the matching source
checkout, final stripped native build outputs, the pinned Stage3 support archive, and an ARM64
Android/Bionic runner honoring `BFU_LIBS`. The optional stock-kit check also requires the configured
host/renderer build directories, `ninja` on `PATH`, and a legitimate local `Objects_client.zip`.

```sh
python3 Code/Tests/android_apk_runtime/verify.py /path/to/OpenBF2-Thor-0.1.4-arm64.apk \
  --source /path/to/android-engine \
  --rebuilt /path/to/build/client/arm64-v8a \
  --bundle /path/to/android-engine/BF2_Stage3_Ready_For_APK_Build_mackillerGR.zip \
  --out /path/to/fresh-final-apk-results \
  --ndk /path/to/android-ndk-r28c \
  --build-tools /path/to/sdk/build-tools/35.0.0 \
  --runner /path/to/android-run \
  --host-build /path/to/build/client/build \
  --renderer-build /path/to/build/client/renderer/build \
  --stock-objects /path/to/private/Objects_client.zip
```

Before running code, the verifier:

1. Checks the APK signature, 16 KiB ZIP alignment and package `io.github.russianranger.openbf2`, versionCode 5.
2. Checks the support archive's pinned SHA256 and requires all four rebuilt core libraries.
3. Extracts every `lib/arm64-v8a/*.so`, checks ARM64 ELF headers, and compares its complete bytes
   against the final rebuilt output or pinned support archive. Unexpected/missing libraries fail.
4. Records the APK hash, per-library hashes and source revision. It checks the APK hash again after
   execution so a changing delivery file cannot silently invalidate the evidence.

The executed fixtures are:

| Check | Actual packaged code exercised | Boundaries |
|---|---|---|
| Native ADS | `HudAmmo`, `VehicleInformation`, `WeaponHud` from `libmain.so`; 20 checks | No candidate object/source override; controlled HUD/weapon state, no physical input |
| UI/Flash | Real host, event/class registries, Swiff parser/glyph service and renderer font lifecycle; 23 checks | Generated SWF; no GPU/window/full game |
| Effect state | Existing 40-check fixture linked directly to packaged `libRendDX9.so` | Recording state manager; no effect implementation compiled into test |
| Effect structures | Public exported effect ABI from packaged renderer; 53 checks | Named light/anonymous 7-matrix payload, descriptors/member reads/writes; no GPU or private effect internals |
| Plugin loader | Actual engine POSIX `LoadLibrary`/`GetProcAddress`/`FreeLibrary` paths; 3 plugins | Loads AI, renderer and Flash, verifies handshake exports, closes; never calls `initDll` |
| Stock kit, optional | Packaged Skeleton and SkinnedMesh renderer with real stock bind matrices; 12 checks per available stock kit (20 kits in the final 0.1.4 run) | Existing fixture recompiles the four canonical Soldier adapter seams; it does not override ADS or claim all host calls originate in packaged libmain |

The kit check's fixture seam is explicit: its four adapter wrappers are compiled using the actual
configured client flags, while the Skeleton and renderer implementations are loaded from the
extracted APK. Its source, build commands and input hashes are retained in the result directory.

Music validation is separate, via `verify_packaged_music_android.sh`, and can reuse the extracted
`libs` directory. These checks establish native ABI/runtime behavior and exact packaging; they do
not establish Android Activity launch, physical Thor input, Adreno rendering, or full gameplay.

Outputs include `report.json`, `libraries.sha256`, signature/badging records and individual logs.
The verifier exits nonzero on any mismatch or failing fixture. No original game assets or APK
libraries are included in this test directory.
