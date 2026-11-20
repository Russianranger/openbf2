# Native numeric policy

This tree integrates the intentional client policy from
[kr4uzi/OpenBF2 PR #51](https://github.com/kr4uzi/OpenBF2/pull/51), including its
signaling-NaN load fix, and the binary128 conversion prerequisite from the Linux
ARM64 work. These are imported platform choices, not new reconstruction claims.

`Platform/X87Compat.h` uses native x87 `long double` only where its significand has
64 bits. Other client targets use binary32 arithmetic with binary64 operands for
explicit exact loads. Dedicated and AI images use `ExtendedFloat`; the client can
request that path with `BF2X87_CLIENT_EXACT_EXTENDED`. Binary128 inputs are decoded
directly, preserving their precision, exponent range and conversion exceptions.
Selected load-only helpers quiet signaling NaNs explicitly, even when optimized.

The Android `Core/X87Emu.h` implementation and the renderer's existing direct-float
decal implementation are unchanged. This does not promise a frame-rate improvement
for those already-portable paths. Client exponent-range differences from x87 and
binary64 transcendental approximations remain part of the imported policy.

`Code/Tests/portable_x87.cpp` tests native, forced binary32 and forced software
extended modes. `Code/Tests/macos_numerics.cpp` also runs on Android despite its
historical name. `Code/Tests/linux_arm64_numerics/reference.cpp` produces an x86-64
GCC binary128-to-x87 reference corpus; `binary128.cpp` checks it on actual ARM64.
Compile engine tests with explicit `DEDICATED`, force-included `Core/BuildConfig.h`
and `Core/PlatformCompat.h`, and `-ffp-contract=off`.

The focused integration checks passed on host x86-64 and Android 13 Bionic under
QEMU: binary32 versus 699,999 x87 PC24 cases, 1,120,088 software-extended host
checks, and 33,344 binary128 conversion reference records covering all rounding
modes. Android's binary32, exact-client and AI-image portable suites passed, also
with the production client's `-O1` flags without `-frounding-math`. These checks
cover arithmetic contracts, not visual gameplay or device performance.
