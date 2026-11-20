#!/usr/bin/env python3
"""Recompile changed production TUs against an existing ABI-compatible server build.

This incremental runtime regression fixture does not replace the full Android
client build. The base build's objects and executable are never changed.
"""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import shlex
import subprocess

PATHS = (
    "Code/BF2/Game/GameServer/GameServer.cpp",
    "Code/BF2/Game/GameServer/ServerSettings.cpp",
    "Code/BF2/AI/AIInterface/BFKnowledge/BFGameEffector.cpp",
    "Code/BF2/Game/GameLogic/GameLogic.cpp",
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "build", "out"):
        parser.add_argument("--" + name, type=lambda s: Path(s).resolve(), required=True)
    parser.add_argument("--ninja", default="ninja")
    parser.add_argument("--tu", action="append", help="Source-relative TU; repeat to override the default set")
    parser.add_argument("--define", action="append", default=[], help="Additional QA compiler definition")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    def ninja(*command):
        return subprocess.check_output([args.ninja, "-C", str(args.build), "-t", *command], text=True)
    compdb = json.loads(ninja("compdb"))
    def compile_one(path):
        entry = next(e for e in compdb if e.get("file", "").endswith("/" + path)
                     and "bf2_engine_objects" in e["command"])
        command = shlex.split(entry["command"])
        command[1:1] = ["-D" + value for value in args.define]
        original = command[command.index("-o") + 1]
        output = args.out / (Path(path).stem + ".o")
        for flag in ("-MF", "-MT", "-o"):
            command[command.index(flag) + 1] = str(output) + (".d" if flag == "-MF" else "")
        command[command.index("-c") + 1] = str(args.source / path)
        # Android client build defines the native no-EDX bridge explicitly.
        # Older dedicated configs omit this otherwise unused macro.
        if any("aarch64" in value for value in command):
            command.insert(1, "-DBF2_EDX_PARAM=")
        with (args.out / (Path(path).stem + ".build.log")).open("w") as log:
            subprocess.run(command, cwd=args.build, stdout=log, stderr=log, check=True)
        return dict(original=original, output=str(output), source=path,
                    source_sha256=hashlib.sha256((args.source / path).read_bytes()).hexdigest(),
                    command=command)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        compiled = list(pool.map(compile_one, args.tu or PATHS))
    link = shlex.split(ninja("commands", "bf2").splitlines()[-1])
    if link[:2] == [":", "&&"]:
        link = link[2:]
    if link[-2:] == ["&&", ":"]:
        link = link[:-2]
    assert "&&" not in link and ";" not in link, "Unexpected linker command"
    replacements = {item["original"]: item["output"] for item in compiled}
    link = [replacements.get(value, value) for value in link]
    link = ["-Wl,-Map=" + str(args.out / "bf2.map") if value.startswith("-Wl,-Map=")
            else "--dependency-file=" + str(args.out / "link.d") if value.startswith("--dependency-file=")
            else value for value in link]
    binary = args.out / "bf2"
    link[link.index("-o") + 1] = str(binary)
    with (args.out / "link.log").open("w") as log:
        subprocess.run(link, cwd=args.build, stdout=log, stderr=log, check=True)
    objects = {str((args.build / value).resolve()): hashlib.sha256((args.build / value).read_bytes()).hexdigest()
               for value in link if value.endswith(".o")}
    manifest = dict(base_build=str(args.build), source=str(args.source), compiled=compiled,
                    link=link, objects_sha256=objects, binary_sha256=hashlib.sha256(binary.read_bytes()).hexdigest())
    (args.out / "build.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(binary)


if __name__ == "__main__":
    main()
