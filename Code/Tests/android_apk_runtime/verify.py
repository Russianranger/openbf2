#!/usr/bin/env python3
"""Verify the signed APK's exact ARM64 payload and run public/runtime ABI regressions."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument(
    "--source", type=Path, required=True, help="Matching canonical engine checkout"
)
parser.add_argument(
    "--rebuilt", type=Path, required=True, help="Final stripped arm64-v8a build output"
)
parser.add_argument(
    "--bundle", type=Path, required=True, help="Pinned Stage3 support archive"
)
parser.add_argument("--out", type=Path, required=True)
parser.add_argument("--ndk", type=Path, required=True)
parser.add_argument("--build-tools", type=Path, required=True)
parser.add_argument("--runner", type=Path, required=True)
parser.add_argument("--version-code", default="5")
parser.add_argument("--stock-objects", type=Path)
parser.add_argument("--host-build", type=Path)
parser.add_argument("--renderer-build", type=Path)
args = parser.parse_args()
for key, value in vars(args).copy().items():
    if isinstance(value, Path):
        setattr(args, key, value.resolve())
out = args.out
out.mkdir(parents=True, exist_ok=True)
libs = out / "libs"
libs.mkdir(exist_ok=True)
if any(libs.iterdir()):
    raise SystemExit("Use a fresh output directory: extracted libraries already exist")
source = args.source
compiler = (
    args.ndk
    / "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++"
)
env = dict(
    os.environ,
    ANDROID_NDK_HOME=str(args.ndk),
    BF2_ANDROID_RUNNER=str(args.runner),
    BFU_LIBS=str(libs),
)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def command(argv, **kwargs):
    return subprocess.run(
        [str(a) for a in argv],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        **kwargs,
    )


apk_hash = digest(args.apk.read_bytes())
report = {"apk": str(args.apk), "apk_sha256": apk_hash, "libraries": [], "checks": []}
report["fixture_source_commit"] = command(
    ["git", "-C", source, "rev-parse", "HEAD"], check=True
).stdout.strip()
report["fixture_tracked_changes"] = command(
    ["git", "-C", source, "status", "--porcelain", "--untracked-files=no"], check=True
).stdout
identity = command(
    [args.build_tools / "aapt2", "dump", "badging", args.apk], check=True
).stdout
(out / "badging.txt").write_text(identity)
match = re.search(
    r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",
    identity,
    re.M,
)
assert (
    match
    and match.group(1) == "io.github.russianranger.openbf2"
    and match.group(2) == args.version_code
), identity[:500]
report["package"], report["version_code"], report["version_name"] = match.groups()
signature = command(
    [args.build_tools / "apksigner", "verify", "--verbose", "--print-certs", args.apk],
    check=True,
).stdout
(out / "signature.txt").write_text(signature)
alignment = command(
    [args.build_tools / "zipalign", "-c", "-P", "16", "4", args.apk], check=True
).stdout
(out / "alignment.txt").write_text(alignment)
assert (
    digest(args.bundle.read_bytes())
    == "6cd417b89b74600a0d898d2d92c56562169f2975058e4636e93f3ab463067375"
), "Support bundle hash"
with zipfile.ZipFile(args.bundle) as bundle:
    prefix = "gameapp/src/main/jniLibs/arm64-v8a/"
    expected = {
        Path(n).name: bundle.read(n)
        for n in bundle.namelist()
        if n.startswith(prefix) and n.endswith(".so")
    }
for p in args.rebuilt.glob("*.so"):
    expected[p.name] = p.read_bytes()
for required in ("libmain.so", "libRendDX9.so", "libSwiffPlayer.so", "libaidll.so"):
    assert (args.rebuilt / required).is_file(), "Missing rebuilt " + required
with zipfile.ZipFile(args.apk) as apk:
    names = [n for n in apk.namelist() if n.startswith("lib/") and n.endswith(".so")]
    assert len(names) == len(set(names)), "Duplicate library entries"
    assert all(
        n.startswith("lib/arm64-v8a/") and len(Path(n).parts) == 3 for n in names
    ), "Unexpected ABI/path"
    assert {Path(n).name for n in names} == set(
        expected
    ), "Unexpected/missing APK libraries"
    for name in sorted(names):
        data = apk.read(name)
        leaf = Path(name).name
        assert (
            data[:6] == b"\x7fELF\x02\x01"
            and int.from_bytes(data[18:20], "little") == 183
        ), (leaf + " not ARM64")
        assert data == expected[leaf], (
            leaf + " differs from exact rebuilt/support payload"
        )
        (libs / leaf).write_bytes(data)
        report["libraries"].append(
            {
                "name": leaf,
                "bytes": len(data),
                "sha256": digest(data),
                "source": (
                    "rebuilt" if (args.rebuilt / leaf).exists() else "pinned support"
                ),
            }
        )
(out / "libraries.sha256").write_text(
    "".join(f"{r['sha256']}  {r['name']}\n" for r in report["libraries"])
)
print(
    f"Extracted and matched {len(report['libraries'])} ARM64 libraries from APK {apk_hash}",
    flush=True,
)


def run_check(name, commands):
    log = out / (name + ".log")
    messages = []
    code = 0
    for argv in commands:
        messages.append("$ " + json.dumps([str(a) for a in argv]) + "\n")
        result = command(argv)
        messages.append(result.stdout)
        if result.returncode:
            code = result.returncode
            break
    log.write_text("".join(messages))
    report["checks"].append(
        {
            "name": name,
            "exit": code,
            "log": str(log),
            "commands": [[str(a) for a in argv] for argv in commands],
        }
    )
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(f"{name}: {'PASS' if code == 0 else 'FAIL'} ({code})", flush=True)


ads = out / "ads"
run_check(
    "native-ads",
    [
        ["bash", source / "Code/Tests/android_ads_crosshair_native.sh", libs, ads],
        [args.runner, ads / "android-ads-crosshair"],
    ],
)
run_check(
    "native-ui-flash",
    [["bash", source / "Code/Tests/android_ui_runtime/run.sh", libs, libs, out / "ui"]],
)
common = [
    compiler,
    "-std=c++14",
    "-O1",
    "-nostdlib++",
    "-L" + str(libs),
    "-Wl,-rpath-link," + str(libs),
]
# This fixture is linked to the packaged renderer; no portable effect implementation is compiled.
run_check(
    "packaged-effect-states",
    [
        common
        + [
            source / "Code/Tests/portable_effect_states/main.cpp",
            "-lRendDX9",
            "-lmain",
            "-lc++_shared",
            "-o",
            out / "effect-states",
        ],
        [args.runner, out / "effect-states"],
    ],
)
run_check(
    "packaged-effect-structs",
    [
        [
            compiler,
            "-std=c++14",
            "-O1",
            source / "Code/Tests/portable_effect_structs/apk_probe.cpp",
            "-ldl",
            "-o",
            out / "effect-structs",
        ],
        [
            args.runner,
            out / "effect-structs",
            libs / "libmain.so",
            libs / "libRendDX9.so",
        ],
    ],
)
run_check(
    "packaged-plugin-loader",
    [
        common
        + [
            Path(__file__).with_name("plugin_load.cpp"),
            "-lmain",
            "-lc++_shared",
            "-ldl",
            "-o",
            out / "plugins",
        ],
        [args.runner, out / "plugins"],
    ],
)
if args.stock_objects:
    assert (
        args.host_build and args.renderer_build
    ), "Stock kit test requires configured host/renderer builds"
    run_check(
        "stock-kit-renderer",
        [
            [
                sys.executable,
                source / "Code/Tests/android_soldier_geometry_runtime/run.py",
                args.host_build,
                args.renderer_build,
                libs,
                libs,
                args.stock_objects,
                out / "kits",
            ]
        ],
    )
report["apk_unchanged"] = digest(args.apk.read_bytes()) == apk_hash
report["passed"] = report["apk_unchanged"] and all(
    c["exit"] == 0 for c in report["checks"]
)
(out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
print(
    "Final APK library verification:",
    "PASS" if report["passed"] else "FAIL",
    flush=True,
)
raise SystemExit(0 if report["passed"] else 1)
