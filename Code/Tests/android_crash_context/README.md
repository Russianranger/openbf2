# Android original crash context

This fixture extracts the production crash logger from `BF2.cpp`, builds it for
ARM64, and deliberately faults child processes using Android/Bionic. A private QA
shared library labels its exact faulting instruction, so the checks compare the
kernel-supplied PC with that instruction and its module-relative address. It also
checks LR and raw-record ordering. The first pass uses the unchanged production
logger and a real SIGSEGV. The second pass bypasses only the legacy unwinder in
the fixture and checks signal termination for both SIGSEGV and SIGABRT. This is
necessary because the NDK unwinder faults on QEMU's signal trampoline, also with
the unmodified logger. The second pass proves the logger's reset/re-raise path,
not unwind safety. No fault injection or bypass enters the game.

```sh
python3 Code/Tests/android_crash_context/run.py \
  --compiler "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang++" \
  --runner "$BF2_ANDROID_RUNNER" --out /tmp/bf2-crash-context-check
```

The runner must execute ARM64 Android binaries and supply matching Bionic and
`libc++_shared.so`, for example an isolated QEMU launcher. Native Android can use a
runner that simply executes its arguments.

The production raw PC/LR record uses a fixed stack buffer and `write` before any
symbol lookup. Module names/ELF-relative offsets and the old stack trace remain
best effort: `dladdr`, stdio, and unwinding are not async-signal-safe and can block
if a fault interrupts their locks. This diagnostic does not fix that pre-existing
limitation or the fault itself. LR is a return address and may carry authentication
bits; its original value is retained even if symbol lookup fails. Resetting the
handler and raising the original signal retains the existing termination path.
