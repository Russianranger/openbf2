#!/usr/bin/env python3
"""Compile the actual Android logger and fault only isolated QA child processes."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--compiler", required=True)
parser.add_argument("--runner", required=True)
parser.add_argument("--out", required=True, type=lambda p: Path(p).resolve())
args = parser.parse_args()
here = Path(__file__).resolve().parent
root = here.parents[2]
args.out.mkdir(parents=True, exist_ok=True)
source = (root / "Code/BF2/Game/Main/BF2.cpp").read_text()
begin = source.index("namespace {\nstruct UnwindCursor")
end = source.index("\n}\n\n// [CHOSEN, not recovered] The launcher's arguments", begin) + 2
(args.out / "logger.inc").write_text(source[begin:end])
flags = [args.compiler, "-std=c++14", "-O1", "-g", "-fno-omit-frame-pointer", "-funwind-tables",
         "-DDEDICATED=0", "-include", str(root / "Code/BF2/Core/BuildConfig.h")]
library = args.out / "libcrash-context-fault.so"
subprocess.run(flags + ["-shared", "-fPIC", str(here / "fault.cpp"), "-o", str(library)], check=True)
for name, extra in [("production", []), ("termination-without-legacy-unwind", ["-DBF2_CRASH_TEST_SKIP_UNWIND=1"])]:
    binary = args.out / ("crash-context-" + name)
    subprocess.run(flags + extra + ["-I", str(args.out), str(here / "main.cpp"), "-ldl", "-o", str(binary)], check=True)
    result = subprocess.run([args.runner, str(binary), str(library)], stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=45)
    (args.out / (name + ".log")).write_text(result.stdout)
    print(name + ":\n" + result.stdout, end="")
    if result.returncode:
        raise SystemExit(result.returncode)
