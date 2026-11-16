#!/usr/bin/env python3
"""Fetch the EXACT SDL3 Android Java glue version matching test13's libSDL3.so.

SDL release-3.4.14, file SHA1s pinned to upstream Git blobs. This intentionally
requires internet on the developer's PC; it is NOT required for pure-Java tests.
Upstream license: SDL zlib license, https://github.com/libsdl-org/SDL/blob/release-3.4.14/LICENSE.txt
"""
from __future__ import annotations

import argparse
import hashlib
import pathlib
import urllib.request

TAG = "release-3.4.14"
ROOT = "https://raw.githubusercontent.com/libsdl-org/SDL/" + TAG + "/android-project/app/src/main/java/org/libsdl/app/"
EXPECTED_SHA1 = {
    "HIDDevice.java": "f96095324b85858ab7e43fd5a0bab232f413eefe",
    "HIDDeviceBLESteamController.java": "bcd8806c4126ee6ccab84c39611369de2a1af796",
    "HIDDeviceManager.java": "9a874b2ef1f6c714665201d98a676c8ec4a05dea",
    "HIDDeviceUSB.java": "08875d2bb7cc0780e32c50be163b429e8d58f41e",
    "SDL.java": "d9650a72e4230ef666a4ff3a5b5cedb58413adc3",
    "SDLActivity.java": "18a2614cd14dd50bc1fc8f1a87e48eb3c689d4b6",
    "SDLAudioManager.java": "43706fa409811036312a0e9d865e8c93844440fd",
    "SDLControllerManager.java": "8681d050b72b618156ea36a6c8cea19f65a3f156",
    "SDLDummyEdit.java": "61d8fe8508214881653de177df377b3da05ddb6f",
    "SDLInputConnection.java": "fdc2994c151ab1cb5cac664fac9d3f89aee83dbc",
    "SDLSensorManager.java": "4ed4f012789147358f5c8ecf2c8db4710086a7d8",
    "SDLSurface.java": "e28870fac8d76d52171006eb28547e4b05b6fe76",
}

def git_blob_sha(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def run(out_dir: pathlib.Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, sha in EXPECTED_SHA1.items():
        target = out_dir / name
        if target.is_file() and git_blob_sha(target.read_bytes()) == sha:
            print("CACHED", name)
            continue
        with urllib.request.urlopen(ROOT + name, timeout=30) as resp:
            data = resp.read()
        actual = git_blob_sha(data)
        if actual != sha:
            raise RuntimeError(f"SHA mismatch for {name}: expected {sha}, got {actual}")
        target.write_bytes(data)
        print("VERIFIED", name, len(data), "bytes")
    print(f"SDL3 {TAG} Java bridge ready in {out_dir}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path,
                        default=pathlib.Path(__file__).resolve().parents[1] / "gameapp/src/main/java/org/libsdl/app")
    args = parser.parse_args()
    run(args.output)
