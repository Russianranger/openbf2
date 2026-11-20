#!/usr/bin/env python3
"""Content-free SWF with one square glyph and an exported font for the real parser."""
import struct
import sys
from pathlib import Path


class Bits:
    def __init__(self): self.bits = ""
    def add(self, value, count): self.bits += format(value & ((1 << count) - 1), "0%db" % count)
    def bytes(self):
        value = self.bits + "0" * (-len(self.bits) % 8)
        return bytes(int(value[i:i + 8], 2) for i in range(0, len(value), 8))


def rect(x, y):
    b = Bits(); n = max(x.bit_length(), y.bit_length()) + 1
    b.add(n, 5)
    for v in (0, x, 0, y): b.add(v, n)
    return b.bytes()


def tag(kind, data=b""):
    return struct.pack("<H", kind << 6 | min(63, len(data))) + (
        struct.pack("<I", len(data)) if len(data) >= 63 else b"") + data


b = Bits(); b.add(1, 4); b.add(0, 4)  # one fill bit, no line bits
b.add(0, 1); b.add(5, 5)             # select fill 1 and move to (0,0)
b.add(1, 5); b.add(0, 1); b.add(0, 1); b.add(1, 1)
for vertical, delta in ((0, 512), (1, 512), (0, -512), (1, -512)):
    b.add(1, 1); b.add(1, 1); b.add(9, 4)  # straight edge, 11 signed bits
    b.add(0, 1); b.add(vertical, 1); b.add(delta, 11)
b.add(0, 6)
shape = b.bytes()
name = b"ThorTest"
font = struct.pack("<HBBB", 1, 0, 0, len(name)) + name + struct.pack("<HHH", 1, 4, 4 + len(shape)) + shape + b"A"
body = rect(20480, 15360) + struct.pack("<HH", 12 << 8, 1)
body += tag(48, font) + tag(56, struct.pack("<HH", 1, 1) + b"thor_test\0") + tag(1) + tag(0)
Path(sys.argv[1]).write_bytes(b"FWS\x07" + struct.pack("<I", 8 + len(body)) + body)
