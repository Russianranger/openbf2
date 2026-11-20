#!/usr/bin/env python3
"""Exercise the production ticket controls in an isolated Android 13 launcher harness.

Reuse a configured Robolectric harness's Maven dependencies and AndroidX jars.
The supplied SDL source directory must contain the real org/libsdl/app sources.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--harness", type=Path, required=True)
parser.add_argument("--sdl-java", type=Path, required=True)
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
java = out / "src/main/java"
for source_root in (tests.parent / "src", args.sdl_java):
    for source in source_root.rglob("*.java"):
        target = java / source.relative_to(source_root)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
package = Path("com/mackillergr/bf2")
shutil.copyfile(root / "android/offline-career/java/OfflineCareer.java", java / package / "OfflineCareer.java")
assets = out / "src/main/assets"
assets.mkdir(parents=True, exist_ok=True)
shutil.copyfile(root / "android/offline-career/assets/bf2_offline_career.py", assets / "bf2_offline_career.py")
with zipfile.ZipFile(out / "career-assets.apk", "w") as archive:
    archive.write(assets / "bf2_offline_career.py", "assets/bf2_offline_career.py")
(out / "src/main/AndroidManifest.xml").write_text(
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.mackillergr.bf2">'
    '<uses-sdk android:minSdkVersion="29" android:targetSdkVersion="35"/><application/></manifest>')
for name in ("TicketDeploymentTest", "DeploymentMenuTest"):
    target = out / "src/test/java" / package / (name + ".java")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(tests / (name + ".java"), target)
subprocess.run([args.mvn, "-f", str(out / "pom.xml"), *args.maven_arg,
                "-DargLine=--add-opens=java.base/java.io=ALL-UNNAMED",
                "-Dtest=TicketDeploymentTest,DeploymentMenuTest", "test"], check=True, cwd=out)
