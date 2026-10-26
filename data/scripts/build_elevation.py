"""Writes the elevation grid the app reads for heights, climb and the height graph
(spec: Phase 1, *Elevation grid*).

Input: one or more rasters already warped to WGS84 at a regular step and written as ENVI
(raw little-endian int16 plus a .hdr), e.g.

    gdalwarp -t_srs EPSG:4326 -tr 0.00025 0.00025 -te W S E N -r average -ot Int16 \
        -dstnodata -32768 -of ENVI madeira.tif madeira.bil

Output: app/src/main/assets/elevation.bin, read by :core:geo ElevationReader.

    "VRE1"  u16 version = 1
    varint grid count, then per grid:
        zigzag varint south-west cell centre lat, lon in microdegrees
        varint step in microdegrees, varint rows, varint cols
        rows × cols heights in whole metres, south row first, west to east, as zig-zag varint
        deltas from the previous cell (sea and no data are 0 m)
"""

from __future__ import annotations

import argparse
import re
import struct
import sys
from array import array
from dataclasses import dataclass
from pathlib import Path

MAGIC = b"VRE1"
VERSION = 1
NODATA = -32768


@dataclass
class Grid:
    lat0: float  # centre of the south-west cell
    lon0: float
    step: float
    rows: int
    cols: int
    heights: list[int]  # south row first

    def at(self, row: int, col: int) -> int:
        return self.heights[row * self.cols + col]


def varint(value: int) -> bytes:
    if value < 0:
        raise ValueError("varint must be non-negative")
    out = bytearray()
    while value >= 0x80:
        out.append((value & 0x7F) | 0x80)
        value >>= 7
    out.append(value)
    return bytes(out)


def zigzag(value: int) -> int:
    return (value << 1) ^ (value >> 63) if value < 0 else value << 1


def unzigzag(value: int) -> int:
    return (value >> 1) ^ -(value & 1)


def micro(deg: float) -> int:
    return int(round(deg * 1_000_000))


def encode(grids: list[Grid]) -> bytes:
    out = bytearray(MAGIC)
    out += struct.pack("<H", VERSION)
    out += varint(len(grids))
    for g in grids:
        if len(g.heights) != g.rows * g.cols:
            raise ValueError("grid size mismatch")
        out += varint(zigzag(micro(g.lat0)))
        out += varint(zigzag(micro(g.lon0)))
        out += varint(micro(g.step))
        out += varint(g.rows)
        out += varint(g.cols)
        previous = 0
        for h in g.heights:
            out += varint(zigzag(h - previous))
            previous = h
    return bytes(out)


def decode(data: bytes) -> list[Grid]:
    """For tests: the inverse of encode."""
    if data[:4] != MAGIC:
        raise ValueError("not an elevation file")
    pos = 6
    def read() -> int:
        nonlocal pos
        shift = result = 0
        while True:
            b = data[pos]
            pos += 1
            result |= (b & 0x7F) << shift
            if b < 0x80:
                return result
            shift += 7
    grids = []
    for _ in range(read()):
        lat0 = unzigzag(read()) / 1e6
        lon0 = unzigzag(read()) / 1e6
        step = read() / 1e6
        rows, cols = read(), read()
        heights = []
        previous = 0
        for _ in range(rows * cols):
            previous += unzigzag(read())
            heights.append(previous)
        grids.append(Grid(lat0, lon0, step, rows, cols, heights))
    return grids


def read_envi(path: Path) -> Grid:
    """An ENVI int16 raster from gdalwarp (north row first) as a Grid (south row first)."""
    header = path.with_suffix(".hdr").read_text()
    def field(name: str) -> str:
        match = re.search(rf"^{name}\s*=\s*(.+)$", header, re.MULTILINE | re.IGNORECASE)
        if not match:
            raise ValueError(f"{name} missing in {path}.hdr")
        return match.group(1).strip()
    cols = int(field("samples"))
    rows = int(field("lines"))
    if int(field("data type")) != 2:
        raise ValueError("expected int16 (ENVI data type 2)")
    big_endian = int(field("byte order")) == 1
    # map info = {Geographic Lat/Lon, 1, 1, west, north, step x, step y, WGS-84, ...}
    info = [part.strip() for part in field("map info").strip("{}").split(",")]
    west, north, step_x, step_y = (float(v) for v in info[3:7])
    if abs(step_x - step_y) > 1e-9:
        raise ValueError("expected square cells")
    values = array("h")
    with path.open("rb") as f:
        values.fromfile(f, rows * cols)
    if big_endian != (sys.byteorder == "big"):
        values.byteswap()
    heights: list[int] = []
    for r in range(rows - 1, -1, -1):  # south row first
        row = values[r * cols:(r + 1) * cols]
        heights.extend(0 if v == NODATA or v < 0 else v for v in row)
    south = north - rows * step_y
    return Grid(south + step_y / 2, west + step_x / 2, step_x, rows, cols, heights)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("rasters", nargs="+", type=Path, help="ENVI .bil files from gdalwarp")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    grids = [read_envi(p) for p in args.rasters]
    data = encode(grids)
    args.out.write_bytes(data)
    for p, g in zip(args.rasters, grids):
        print(f"{p.name}: {g.rows} x {g.cols} cells at {g.step}°, highest {max(g.heights)} m")
    print(f"wrote {args.out} ({len(data) / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
