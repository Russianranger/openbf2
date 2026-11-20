#!/usr/bin/env python3
"""Run SAF/publication checks using an existing configured Robolectric harness.

Create the harness with run_controller_runtime.py first; its Maven dependencies
and AndroidX jars are reused, while these sources/tests use an isolated output.
"""
import argparse
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--harness", type=Path, required=True)
parser.add_argument("--out", type=Path, required=True)
parser.add_argument("--mvn", default="mvn")
parser.add_argument("--maven-arg", action="append", default=[])
args = parser.parse_args()
tests = Path(__file__).resolve().parent
root = tests.parents[2]
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=True)
shutil.copyfile(args.harness / "pom.xml", out / "pom.xml")
(out / "lib").mkdir(exist_ok=True)
for name in ("monitor.jar", "idling.jar"):
    shutil.copyfile(args.harness / "lib" / name, out / "lib" / name)
package = Path("com/mackillergr/bf2")
for name in ("GameDirectoryPicker", "MapPackImport", "GameSession", "GameContent"):
    target = out / "src/main/java" / package / (name + ".java")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(tests.parent / "src" / package / (name + ".java"), target)
shutil.copyfile(root / "android/offline-career/java/OfflineCareer.java",
                out / "src/main/java" / package / "OfflineCareer.java")
for name in ("MapPackPickerAndroidTest", "MapPackImportTest"):
    target = out / "src/test/java" / package / (name + ".java")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(tests / (name + ".java"), target)
subprocess.run([args.mvn, "-f", str(out / "pom.xml"), *args.maven_arg,
                "-DargLine=--add-opens=java.base/java.io=ALL-UNNAMED",
                "-Dtest=MapPackPickerAndroidTest", "test"], check=True)
