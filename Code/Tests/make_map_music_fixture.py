#!/usr/bin/env python3
"""Stage private stock descriptors and supplied audio for map_music_android.

Usage: make_map_music_fixture.py bf2-mod-directory output-directory audio.ogg|Common_client.zip
An Ogg input is a generated test substitute for every theme, not stock game audio.
Nothing produced by this helper belongs in the repository or APK.
"""
import pathlib
import shutil
import sys
import zipfile

stock, output, audio = map(pathlib.Path, sys.argv[1:])
maps = ["strike_at_karkand", "gulf_of_oman", "road_to_jalalabad", "dalian_plant", "midnight_sun"]
tracks = {faction: "Sound/Menu/Music/load_" + faction + "_music.ogg"
          for faction in ("MEC", "CH", "US")}
if zipfile.is_zipfile(audio):
    with zipfile.ZipFile(audio) as archive:
        names = {name.lower(): name for name in archive.namelist()}
        clips = {faction: archive.read(names[path.lower()]) for faction, path in tracks.items()}
else:
    clips = {faction: audio.read_bytes() for faction in tracks}

for mode in ("loose", "zip"):
    mod = output / ("MixedCase-" + mode) / "mods/bf2"
    mod.mkdir(parents=True, exist_ok=True)
    shutil.copy2(stock / "mod.desc", mod / "mod.desc")
    for name in maps:
        target = mod / "levels" / name / "info" / (name + ".desc")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(stock / "levels" / name / "info" / target.name, target)
    if mode == "zip":
        with zipfile.ZipFile(mod / "common_client.zip", "w", zipfile.ZIP_DEFLATED) as archive:
            for faction, path in tracks.items():
                archive.writestr(path, clips[faction])
    else:
        for faction, path in tracks.items():
            target = mod / "common" / path.lower()
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(clips[faction])
    print(mod.parent.parent)
