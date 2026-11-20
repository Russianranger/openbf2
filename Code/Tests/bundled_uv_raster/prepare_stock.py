#!/usr/bin/env python3
"""Extract a private, test-only bundled-mesh payload. No assets are redistributed.
Follows MeshTemplate::readGeometry/readTrailer, BundledMeshTemplate::readLods/readMaterials.
"""

import pathlib, struct, sys, json, collections, hashlib

p = pathlib.Path(sys.argv[1])
out = pathlib.Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
b = p.read_bytes()
at = 0


def read(fmt):
    global at
    v = struct.unpack_from("<" + fmt, b, at)
    at += struct.calcsize("<" + fmt)
    return v if len(v) > 1 else v[0]


def string():
    global at
    n = read("I")
    v = b[at : at + n].decode()
    at += n
    return v


head, version = read("2I")
assert version >= 10
reserved = read("3IB")
geoms = read("I")
lods = [read("I") for _ in range(geoms)]
decl = [read("HHBBBB") for _ in range(read("I"))]
primitive, stride, count = read("3I")
assert primitive == 4 and stride in (48, 56)
verts = b[at : at + stride * count]
at += len(verts)
index_count = read("I")
indices = b[at : at + index_count * 2]
at += len(indices)
trailer = read("I")
records = []
for g, ls in enumerate(lods):
    for l in range(ls):
        bounds = read("6f")
        parts = read("I")
        records.append(dict(geom=g, lod=l, bounds=bounds, parts=parts))
for record in records:
    mats = []
    for m in range(read("I")):
        kind = read("I")
        effect = string()
        tech = string()
        textures = [string() for _ in range(read("I"))]
        start, index, n, vertex_count, u1, u2 = read("6I")
        mats.append(
            dict(
                kind=kind,
                effect=effect,
                tech=tech,
                textures=textures,
                start=start,
                index=index,
                n=n,
                vertex_count=vertex_count,
            )
        )
    record["materials"] = mats
assert at == len(b), (at, len(b))
(out / "mesh.json").write_text(
    json.dumps(
        dict(
            source=p.name,
            sha256=hashlib.sha256(b).hexdigest(),
            head=head,
            version=version,
            lods=lods,
            decl=decl,
            stride=stride,
            vertices=count,
            indices=index_count,
            records=records,
        ),
        indent=2,
    )
)
(out / "vertices.bin").write_bytes(verts)
(out / "indices.bin").write_bytes(indices)
print("mesh", version, "geoms", lods, "decl", decl, "vertices", count, "stride", stride)
for rec in records:
    print("g/l", rec["geom"], rec["lod"], "parts", rec["parts"])
    for i, m in enumerate(rec["materials"]):
        cnt = collections.Counter(
            verts[j * stride + 27]
            for j in range(m["start"], m["start"] + m["vertex_count"])
        )
        print(" material", i, m, "UV indices", dict(cnt))
ranges = []
for rec in records:
    if rec["geom"] == 1:
        for m in rec["materials"]:
            if m["tech"].startswith("AnimatedUV"):
                ranges.append(
                    (rec["lod"], m["start"], m["index"], m["n"], m["vertex_count"])
                )
(out / "ranges.bin").write_bytes(
    struct.pack("<I", len(ranges)) + b"".join(struct.pack("<5I", *r) for r in ranges)
)
