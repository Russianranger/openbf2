#!/usr/bin/env python3
"""Extract a v0x1a map's LOD input privately; no game data is checked into this test."""
import argparse
import hashlib
import struct
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('client_zip', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
with zipfile.ZipFile(args.client_zip) as archive:
    name = next(n for n in archive.namelist() if n.lower() == 'terraindata.raw')
    data = archive.read(name)
version, has_version = struct.unpack_from('<HH', data)
if not has_version or version != 0x1a:
    raise SystemExit(f'This regression needs v0x1a terrain; got {version:#x}.')
patch_size, = struct.unpack_from('<I', data, 40)
if patch_size != 128:
    raise SystemExit(f'The grid raster fixture expects patch size 128, got {patch_size}.')
# Header through patchColormapSize, then four LF-terminated texture basenames.
offset = 57
for _ in range(4):
    offset = data.index(b'\n', offset) + 1
# Far tiling (Vec2, two floats, Y offset), three colors, detail texture count.
offset += 8 + 4 + 4 + 4 + 36
detail_count, = struct.unpack_from('<I', data, offset)
offset += 4
for _ in range(detail_count):
    offset = data.index(b'\n', offset) + 1
    offset += 1 + 8 + 4 + 4 + 1
# 21 branch nodes contain 38 words each; 64 leaves contain six words each.
index_offset = offset + (21 * 38 + 64 * 6) * 4
index_count, = struct.unpack_from('<I', data, index_offset)
end = index_offset + 4 + index_count * 2 + 1  # compact-vertices flag
if end > len(data):
    raise SystemExit('Truncated terrain LOD data.')
args.output.write_bytes(data[offset:end])
print(f'version={version:#x} patch_size={patch_size} lod_offset={offset} '
      f'index_count={index_count} sha256={hashlib.sha256(data).hexdigest()}')
