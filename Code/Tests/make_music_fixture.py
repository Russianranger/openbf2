#!/usr/bin/env python3
"""Generate an Ogg test tone with the vendor tag required by BF2's sound reader."""
import pathlib
import struct
import subprocess
import sys
import tempfile

with tempfile.TemporaryDirectory(prefix="bf2-music-test-") as folder:
    encoded = pathlib.Path(folder) / "tone.ogg"
    subprocess.run(["ffmpeg", "-loglevel", "error", "-f", "lavfi", "-i",
                    "sine=frequency=440:duration=1", "-c:a", "libvorbis", str(encoded)], check=True)
    original = encoded.read_bytes()
result = bytearray()
offset = 0
changed = False
while offset < len(original):
    if original[offset:offset+4] != b"OggS":
        raise ValueError("Expected Ogg page")
    count = original[offset+26]
    sizes = list(original[offset+27:offset+27+count])
    end = offset + 27 + count + sum(sizes)
    header = bytearray(original[offset:offset+27])
    body = bytearray(original[offset+27+count:end])
    if body.startswith(b"\x03vorbis"):
        length = struct.unpack_from("<I", body, 7)[0]
        vendor = b"Xiph.Org libVorbis I 20030909"
        delta = len(vendor) - length
        if sizes[0] >= 255 or not 0 < sizes[0] + delta < 255:
            raise ValueError("Unexpected comment packet layout")
        body[7:11+length] = struct.pack("<I", len(vendor)) + vendor
        sizes[0] += delta
        changed = True
    page = header + bytes(sizes) + body
    page[22:26] = bytes(4)
    crc = 0
    for byte in page:
        crc ^= byte << 24
        for _ in range(8):
            crc = ((crc << 1) ^ 0x04c11db7 if crc & 0x80000000 else crc << 1) & 0xffffffff
    struct.pack_into("<I", page, 22, crc)
    result += page
    offset = end
if not changed:
    raise ValueError("Missing Vorbis comment packet")
pathlib.Path(sys.argv[1]).write_bytes(result)
