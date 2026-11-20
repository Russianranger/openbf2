"""Extract stock bind records for the Android geometry ABI regression; no game data is vendored."""
import pathlib
import struct
import sys
import zipfile

archive, output = map(pathlib.Path, sys.argv[1:])
output.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(archive) as source:
    names = {name.lower(): name for name in source.namelist()}
    rig = "soldiers/common/animations/3p_setup.ske"
    (output / "3p_setup.ske").write_bytes(source.read(names[rig]))
    for nation in ("mec", "us"):
        name = f"kits/{nation}/meshes/{nation}_kits.skinnedmesh"
        data = source.read(names[name])
        offset = 0

        def read(fmt):
            global offset
            result = struct.unpack_from("<" + fmt, data, offset)
            offset += struct.calcsize("<" + fmt)
            return result

        header = read("5iB")
        if header[1] != 11:
            raise ValueError(f"Expected stock 1.50 mesh version 11: {name}")
        geoms, = read("I")
        lods = read(f"{geoms}I")
        attributes, = read("I")
        offset += attributes * 8
        primitive, stride, vertices = read("3I")
        offset += stride * vertices
        indices, = read("I")
        offset += indices * 2
        for geom, levels in enumerate(lods):
            for lod in range(levels):
                read("6f")  # version-11 min/max bounds
                palettes, = read("I")
                if not 1 <= palettes <= 4:
                    raise ValueError("Unexpected number of kit palettes")
                records = bytearray()
                total_bones = 0
                for palette in range(palettes):
                    bones, = read("I")
                    if not 0 <= bones <= 26:
                        raise ValueError("Unexpected number of kit bones")
                    total_bones += bones
                    records += struct.pack("<I", bones)
                    records += data[offset:offset + bones * 68]
                    offset += bones * 68
                # Dropped-kit geometries have no skinning palette and are outside attachment.
                if lod == 0 and total_bones:
                    (output / f"{nation}-{geom:02d}.bind").write_bytes(records)
        print(f"Extracted {geoms} {nation.upper()} kit geometries")
