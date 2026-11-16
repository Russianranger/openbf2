#!/usr/bin/env python3
"""Build and sign the OpenBF2 Thor ARM64 client with existing SDK command-line tools."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from zipfile import ZipFile, ZIP_STORED

HERE = Path(__file__).resolve().parents[1]
ROOT = HERE.parents[1]
BUNDLE = "BF2_Stage3_Ready_For_APK_Build_mackillerGR.zip"


def run(*args):
    subprocess.run(args, check=True)


def build(args, out):
    jdk = Path(os.environ["JAVA_HOME"]) / "bin"
    sdk = Path(os.environ["ANDROID_HOME"])
    bt = sdk / "build-tools/35.0.0"
    jar = sdk / "platforms/android-35/android.jar"
    classes, dex = out / "classes", out / "dex"
    classes.mkdir()
    dex.mkdir()
    run(sys.executable, HERE / "tools/fetch_sdl_java.py", "--output", args.sdl_java)
    sources = sorted(HERE.joinpath("src").rglob("*.java")) + sorted(args.sdl_java.glob("*.java"))
    sources += sorted(args.career.joinpath("java").glob("*.java"))
    run(jdk / "javac", "-encoding", "UTF-8", "--release", "8",
        "-classpath", jar, "-d", classes, *sources)
    run(jdk / "jar", "cf", out / "classes.jar", "-C", classes, ".")
    run(bt / "d8", "--release", "--min-api", "29", "--lib", jar, "--output", dex, out / "classes.jar")
    base = out / "base.apk"
    run(bt / "aapt2", "link", "-o", base, "-I", jar, "--manifest", HERE / "AndroidManifest.xml",
        "-A", args.career / "assets")
    runtime = {f.name: f for f in args.native_dir.glob("*.so")}
    for required in ("libmain.so", "libaidll.so"):
        if required not in runtime:
            raise SystemExit("Missing rebuilt ARM64 runtime: " + required)
    if b"--coop\0" not in runtime["libmain.so"].read_bytes():
        raise SystemExit("The selected client is missing Android's --coop launch path")
    with ZipFile(args.bundle) as support, ZipFile(base, "a", compression=ZIP_STORED) as apk:
        prefix = "gameapp/src/main/jniLibs/arm64-v8a/"
        libs = {name.split("/")[-1]: name for name in support.namelist()
                if name.startswith(prefix) and name.endswith(".so")}
        for name in sorted(set(libs) | set(runtime)):
            image = runtime[name].read_bytes() if name in runtime else support.read(libs[name])
            if image[:6] != b"\x7fELF\x02\x01" or int.from_bytes(image[18:20], "little") != 183:
                raise SystemExit("Invalid ARM64 ELF: " + name)
            apk.writestr("lib/arm64-v8a/" + name, image)
        for f in sorted(dex.glob("*.dex")):
            apk.write(f, f.name)
    aligned = out / "aligned.apk"
    run(bt / "zipalign", "-f", "-P", "16", "4", base, aligned)
    signed = out / "signed.apk"
    key_password = ["--key-pass", "file:" + str(args.keypass)] if args.keypass else []
    run(bt / "apksigner", "sign", "--ks", args.keystore, "--ks-key-alias", args.alias,
        "--ks-pass", "file:" + str(args.storepass), *key_password,
        "--out", signed, aligned)
    run(bt / "apksigner", "verify", "--verbose", "--print-certs", signed)
    run(bt / "zipalign", "-c", "-P", "16", "4", signed)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(signed, args.output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-dir", required=True, type=Path,
                        help="Directory containing the rebuilt and ABI-matched ARM64 engine and plugins")
    parser.add_argument("--sdl-java", type=Path,
                        default=ROOT / "out/sdl-java/org/libsdl/app", help="Verified SDL 3.4.14 Java cache")
    parser.add_argument("--bundle", type=Path, default=ROOT / BUNDLE)
    parser.add_argument("--career", type=Path, default=ROOT / "android/offline-career")
    parser.add_argument("--keystore", required=True, type=Path)
    parser.add_argument("--alias", required=True)
    parser.add_argument("--storepass", required=True, type=Path)
    parser.add_argument("--keypass", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    options = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="openbf2-apk-") as temp:
        build(options, Path(temp))
