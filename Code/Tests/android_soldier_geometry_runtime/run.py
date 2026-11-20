#!/usr/bin/env python3
"""Build with the production host/renderer command lines, then run on Android/Bionic."""
import argparse
import hashlib
import json
import os
import pathlib
import shlex
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("host_build", type=pathlib.Path)
parser.add_argument("renderer_build", type=pathlib.Path)
parser.add_argument("native", type=pathlib.Path)
parser.add_argument("support", type=pathlib.Path)
parser.add_argument("objects_client_zip", type=pathlib.Path)
parser.add_argument("output", type=pathlib.Path)
parser.add_argument("--soldier-source", type=pathlib.Path, help="Baseline source for a negative control")
args = parser.parse_args()
here = pathlib.Path(__file__).resolve().parent
root = here.parents[2]
args.output.mkdir(parents=True, exist_ok=True)
out = args.output.resolve()
native, support = args.native.resolve(), args.support.resolve()
runner = os.environ["BF2_ANDROID_RUNNER"]


def compile_flags(build, target, source):
    lines = subprocess.check_output(["ninja", "-C", str(build), "-t", "commands", target], text=True)
    line = next(line for line in lines.splitlines() if " -c " in line and source in line)
    original = shlex.split(line)[-1]
    prefix = original[:original.index("/Code/BF2/")]
    return shlex.split(line[:line.index(" -MD ")].replace(prefix, str(root)))


host = compile_flags(args.host_build, "bf2_android_client", "/Game/Objects/Game/Soldier.cpp")
host += ["-w", "-fvisibility=hidden"]
if args.soldier_source:
    host += [f'-DBF2_SOLDIER_SOURCE="{args.soldier_source.resolve()}"',
             "-I" + str(root / "Code/BF2/Game/Objects/Game")]
host += ["-c", str(here / "host_calls.cpp"), "-o", str(out / "host_calls.o")]
render = compile_flags(args.renderer_build, "bf2_renddx9_portable", "/Geom/SkinnedMesh.cpp")
render += [str(here / "main.cpp"), str(out / "host_calls.o"), "-L" + str(native),
           "-lRendDX9", "-lmain", "-ldl", "-Wl,--gc-sections", "-Wl,-rpath-link," + str(support),
           "-o", str(out / "kit-check")]
(out / "commands.json").write_text(json.dumps([host, render], indent=2) + "\n")
subprocess.run(host, check=True)
subprocess.run(render, check=True)
fixtures = out / "fixtures"
subprocess.run(["python3", str(here / "extract.py"), str(args.objects_client_zip), str(fixtures)], check=True)
hashes = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
          for p in [native / "libmain.so", native / "libRendDX9.so"]}
hashes["Soldier.cpp"] = hashlib.sha256((args.soldier_source or root / "Code/BF2/Game/Objects/Game/Soldier.cpp").read_bytes()).hexdigest()
(out / "inputs.sha256.json").write_text(json.dumps(hashes, indent=2) + "\n")
environment = dict(os.environ, BFU_LIBS=f"{native}:{support}")
failures = 0
for fixture in sorted(fixtures.glob("*.bind")):
    result = subprocess.run([runner, str(out / "kit-check"), str(native / "libRendDX9.so"),
                             str(fixture), str(fixtures / "3p_setup.ske")],
                            env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (out / (fixture.stem + ".log")).write_text(result.stdout)
    failures += result.returncode != 0
    print(f"{fixture.stem}: exit={result.returncode}; {result.stdout.splitlines()[-1]}", flush=True)
raise SystemExit(1 if failures else 0)
