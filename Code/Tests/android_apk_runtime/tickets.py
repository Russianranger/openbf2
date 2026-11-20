#!/usr/bin/env python3
"""Exercise the exact packaged ARM64 ticket getter and session/round reset exports."""

import argparse
import hashlib
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
for name in ("source", "ndk", "library", "out", "runner"):
    parser.add_argument("--" + name, type=Path, required=True)
args = parser.parse_args()
for key, value in vars(args).copy().items():
    setattr(args, key, value.resolve())
args.out.mkdir(parents=True, exist_ok=True)
assert args.library.name == "libmain.so", "Expected the packaged libmain.so"
bf = args.source / "Code/BF2"
tools = args.ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
compiler = tools / "aarch64-linux-android29-clang++"
executable = args.out / "packaged-tickets"
flags = [
    "-std=c++14", "-O1", "-g", "-fPIC", "-fvisibility-inlines-hidden",
    "-DDEDICATED=0", "-D_LINUX", "-DBF2_PLUGIN_ABI_NATIVE=1",
    "-DBF2_EDX_PARAM=", "-D_ITERATOR_DEBUG_LEVEL=0", "-fms-extensions",
    "-fno-strict-aliasing", "-fwrapv",
]
for include in (bf, bf / "PortShim", bf / "PortShim/android"):
    flags += ["-I" + str(include)]
for include in ("Core/BuildConfig.h", "Core/PlatformCompat.h", "Core/PortableClient.h"):
    flags += ["-include", str(bf / include)]
subprocess.run(
    [str(compiler), *flags,
     str(args.source / "Code/Tests/android_apk_runtime/tickets.cpp"),
     str(args.library), "-nostdlib++", "-L" + str(args.library.parent),
     "-lc++_shared", "-Wl,-rpath-link," + str(args.library.parent), "-ldl",
     "-o", str(executable)],
    check=True,
)
symbols = subprocess.check_output(
    [str(tools / "llvm-nm"), "-D", "--defined-only", str(executable)], text=True
)
# The observer subclasses must not replace any production implementation/global.
assert not any(name in symbols for name in (
    "9BF2Engine", "15ServerGameLogic", "15offline_tickets", "4gameE"
)), symbols
(args.out / "dynamic-symbols.txt").write_text(symbols)
before = hashlib.sha256(args.library.read_bytes()).hexdigest()
print("Exact libmain.so SHA256 " + before, flush=True)
env = os.environ.copy()
env["BFU_LIBS"] = str(args.library.parent)
result = subprocess.run(
    [str(args.runner), str(executable), str(args.library)], env=env
)
assert before == hashlib.sha256(args.library.read_bytes()).hexdigest(), "Library changed during QA"
raise SystemExit(result.returncode)
