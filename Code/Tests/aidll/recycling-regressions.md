# Native AI recycling regressions

These tests exercise the production weapon manager, plan-variable ownership,
armament refresh and nav-mesh polygon code. Only engine collaborators are generated
observers; an unexpected engine call aborts. No game assets are required.

The fixes adapt eonflux7/openbf2-eon64 commits `255e6e25`, `dd8b056c` and `bb755a11`.
The production retirement scheme deliberately differs from the upstream retained-record
prototype: handles expire immediately, captured plan variables and change-weapon actions
pin records, invalid contexts stop dispatch, and zero-owner records are collected after
the AI action tick. Collection is not based on an arbitrary frame delay. At teardown,
a surviving plan owns its record until its last release. These are native safety choices,
not claims of recovered retail behavior. Engine and AIDLL must be rebuilt together because
`IAIInformationCourier::weaponDestroyed` appends a shared virtual interface slot.

## Host lifetime regression

Run from the repository root using GCC with ASan and UBSan:

```sh
ASAN_OPTIONS=detect_leaks=0 python3 Code/Tests/aidll/weapon_recycling_runtime.py \
  --out /tmp/bf-ai-lifetime
```

The runner compiles real manager, record and context units. It covers stale handles,
multiple plan owners, ownership across eight collection passes, no deletion on the last
same-tick release, 2,000 recycle cycles, replacement, reset, rejected stale writes and a
context that outlives its manager. Explicit destructor and retired-record counts must
balance. LeakSanitizer was disabled in the original execution environment because it
cannot run under ptrace; ASan/UBSan remained enabled.

## Android production-object regressions

Build the complete 447-source AI manifest first, with Android NDK r28c and API 28.
On the `android-thor` branch the checked-in Makefile provides that build; `SOURCE_ROOT`
may name the matching combined engine checkout:

```sh
make -f android/aidll/Makefile ANDROID_NDK_HOME="$ANDROID_NDK_HOME" \
  SOURCE_ROOT=/path/to/combined-engine OUT=/tmp/bf-ai-objects -j2
python3 Code/Tests/aidll/armament_recycling_runtime.py \
  --source-root /path/to/combined-engine --ai-out /tmp/bf-ai-objects \
  --ndk "$ANDROID_NDK_HOME" --out /tmp/bf-ai-android-tests
```

The Python runner links both executables against the actual compiled AI object manifest.
Run `/tmp/bf-ai-android-tests/armament-check` and
`/tmp/bf-ai-android-tests/nav-recycling-check` in an Android ARM64 runtime with the matching
NDK `libc++_shared.so` available. Android 13 Bionic under QEMU-user was used for validation.
These are native Android executables; a host Linux loader is not sufficient.

The armament fixture verifies real and known negative/out-of-range indices, a recycled
weapon slot that invokes the real armament refresh, a removed slot and an invalid
last-fired activation mapping. The navigation fixture creates two small polygon islands
and invokes real quad-tree geometry and material checks. It covers empty maps, four
invalid island indices, fallback success and failure, material rejection, explicit
material bypass and preservation of a valid island restriction.

The Makefile also offers `nav-recycling-check` with `TEST_ROOT` for a test checkout that
is separate from `SOURCE_ROOT`. Both fixtures enable assertions even when the production
AI objects use `NDEBUG`.

Passing these focused tests establishes the exercised safety properties. It does not
establish a playable match, bot tactical quality, rendering or offline career behavior;
those require independent integrated runtime tests.
