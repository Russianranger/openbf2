#!/usr/bin/env python3
"""Extract unmodified menu ActionScript into a private headless regression movie.

Requires a user-supplied 1.50 mainMenu.swf (CWS/FWS) or Menu_client.zip. No assets
are downloaded or distributed. Only layout/decorations and initial login navigation
are omitted; the stock first-run, navigation, selection and Start action bytes run
in the production gameswf VM.
"""
import hashlib
import pathlib
import struct
import sys
import zipfile
import zlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / 'swiffplayer_host'))
from asdis import collect_blocks, decode_actions

def definitions(block):
    end = 0
    for offset, line in decode_actions(block):
        if line.startswith('ConstantPool') or line.startswith('DefineFunction'):
            continue
        if line == '}':
            end = offset
        elif not line.startswith('  '):
            break
    if not end:
        raise ValueError('No initial function definitions')
    return block[:end]

def tag(code, data=b''):
    return struct.pack('<HI', code << 6 | 63, len(data)) + data

def function(name, body):
    head = name.encode() + b'\0' + struct.pack('<HH', 0, len(body))
    return b'\x9b' + struct.pack('<H', len(head)) + head + body

src, dest = map(pathlib.Path, sys.argv[1:])
raw = src.read_bytes()
if zipfile.is_zipfile(src):
    with zipfile.ZipFile(src) as archive:
        names = [n for n in archive.namelist() if n.lower().endswith('/mainmenu.swf')]
        if len(names) != 1:
            raise ValueError('Expected one mainMenu.swf')
        raw = archive.read(names[0])
original_hash = hashlib.sha256(raw).hexdigest()
if raw[:3] == b'CWS':
    raw = b'FWS' + raw[3:8] + zlib.decompress(raw[8:])
assert raw[:3] == b'FWS'
header_end = 8 + (5 + 4 * (raw[8] >> 3) + 7) // 8 + 4
blocks = list(collect_blocks(raw, header_end, len(raw), 'root'))
def unique(predicate):
    found = [b for where, b in blocks if predicate(where, b)]
    if len(found) != 1:
        raise ValueError(f'Expected one matching stock block, got {len(found)}')
    return found[0]
root = unique(lambda where,b: where == 'root/DoAction' and b'createDefaultTraining\0' in b)
first_run = unique(lambda where,b: '/DoAction' in where and b'noobieInit\0' in b)
select_map = unique(lambda where,b: '/DoAction' in where and b'trainingDblClick\0' in b)
start = unique(lambda where,b: '/ClipEvent(0x800)' in where and b'dice.bf2.Multiplay.createServer\0' in b and b'setSingleplayerSettings\0' in b)
assert start[0] == 0x88
pool_end = 3 + struct.unpack_from('<H',start,1)[0]
# Each original action buffer owns a distinct constant pool in gameswf.
scripts = [definitions(root), definitions(first_run), definitions(select_map),
           start[:pool_end] + function('explicitStart', start[pool_end:])]
frames = ['none','trainingCreate','lanJoin','login','newbie']
tags = b''
for index, label in enumerate(frames):
    tags += tag(43, label.encode()+b'\0')
    if index == 0:
        for script in scripts:
            tags += tag(12, script+b'\x00')
    tags += tag(12,b'\x07\x00') + tag(1)
header = raw[:header_end-2] + struct.pack('<H', len(frames))
out = header + tags + tag(0)
out = out[:4] + struct.pack('<I',len(out)) + out[8:]
dest.parent.mkdir(parents=True, exist_ok=True)
dest.write_bytes(out)
print(f'Stock source SHA256 {original_hash}; private script fixture {dest}')
