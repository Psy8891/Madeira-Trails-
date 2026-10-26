"""Builds the terrain tiles MapLibre shades the map from (spec: Phase 1, `build-maps`).

For each zoom the DTM is warped with GDAL to Web Mercator on the exact tile grid, then cut into
256 px Terrarium-encoded lossless WebP tiles and packed into one PMTiles file
(`madeira-dem.pmtiles`). Tiles that are all sea are left out.

    python build_dem_tiles.py --bbox -17.30,32.55,-16.25,33.15 --minzoom 8 --maxzoom 14 \
        --out madeira-dem.pmtiles madeira.tif portosanto.tif
"""

from __future__ import annotations

import argparse
import io
import math
import re
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image
from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer

ORIGIN = 20037508.342789244
TILE = 256
SEA_M = 0.5


@dataclass(frozen=True)
class TileRange:
    z: int
    x0: int
    y0: int
    x1: int  # inclusive
    y1: int

    @property
    def cols(self) -> int:
        return self.x1 - self.x0 + 1

    @property
    def rows(self) -> int:
        return self.y1 - self.y0 + 1

    def mercator_bounds(self) -> tuple[float, float, float, float]:
        """minx, miny, maxx, maxy in EPSG:3857 of the whole range."""
        size = 2 * ORIGIN / (1 << self.z)
        return (
            -ORIGIN + self.x0 * size,
            ORIGIN - (self.y1 + 1) * size,
            -ORIGIN + (self.x1 + 1) * size,
            ORIGIN - self.y0 * size,
        )


def tile_xy(lon: float, lat: float, z: int) -> tuple[int, int]:
    n = 1 << z
    x = int((lon + 180.0) / 360.0 * n)
    lat_r = math.radians(lat)
    y = int((1.0 - math.asinh(math.tan(lat_r)) / math.pi) / 2.0 * n)
    return min(max(x, 0), n - 1), min(max(y, 0), n - 1)


def tile_range(bbox: tuple[float, float, float, float], z: int) -> TileRange:
    west, south, east, north = bbox
    x0, y0 = tile_xy(west, north, z)
    x1, y1 = tile_xy(east, south, z)
    return TileRange(z, x0, y0, x1, y1)


def terrarium(heights: np.ndarray) -> np.ndarray:
    """Heights in metres (NaN = no data, read as 0) to Terrarium RGB."""
    h = np.nan_to_num(heights.astype(np.float64), nan=0.0)
    v = np.clip(h + 32768.0, 0, 65535.99)
    r = np.floor(v / 256.0)
    g = np.floor(v) - r * 256.0
    b = np.floor((v - np.floor(v)) * 256.0)
    return np.stack([r, g, b], axis=-1).astype(np.uint8)


def decode_terrarium(rgb: np.ndarray) -> np.ndarray:
    rgb = rgb.astype(np.float64)
    return rgb[..., 0] * 256.0 + rgb[..., 1] + rgb[..., 2] / 256.0 - 32768.0


def webp(rgb: np.ndarray) -> bytes:
    buf = io.BytesIO()
    Image.fromarray(rgb, "RGB").save(buf, "WEBP", lossless=True, quality=100, method=4)
    return buf.getvalue()


def read_envi_float(path: Path, rows: int, cols: int) -> np.ndarray:
    header = path.with_suffix(".hdr").read_text()
    order = re.search(r"byte order\s*=\s*(\d)", header)
    dtype = np.dtype(">f4" if order and order.group(1) == "1" else "<f4")
    data = np.fromfile(path, dtype=dtype, count=rows * cols).reshape(rows, cols)
    data = data.astype(np.float32)
    data[data <= -9000] = np.nan
    return data


def tiles_of(heights: np.ndarray, r: TileRange):
    """(z, x, y, webp bytes) for every tile with land in it."""
    for ty in range(r.rows):
        for tx in range(r.cols):
            block = heights[ty * TILE:(ty + 1) * TILE, tx * TILE:(tx + 1) * TILE]
            if not np.any(np.nan_to_num(block, nan=0.0) > SEA_M):
                continue
            yield r.z, r.x0 + tx, r.y0 + ty, webp(terrarium(block))


def warp(sources: list[Path], r: TileRange, out: Path) -> np.ndarray:
    minx, miny, maxx, maxy = r.mercator_bounds()
    resampling = "bilinear" if r.z >= 13 else "average"
    subprocess.run(
        [
            "gdalwarp", "-q", "-overwrite", "-t_srs", "EPSG:3857",
            "-te", str(minx), str(miny), str(maxx), str(maxy),
            "-ts", str(r.cols * TILE), str(r.rows * TILE),
            "-r", resampling, "-ot", "Float32", "-dstnodata", "-9999", "-of", "ENVI",
            *map(str, sources), str(out),
        ],
        check=True,
    )
    return read_envi_float(out, r.rows * TILE, r.cols * TILE)


def pack(tiles, out: Path, bbox: tuple[float, float, float, float], attribution: str) -> int:
    ordered = sorted(tiles, key=lambda t: zxy_to_tileid(t[0], t[1], t[2]))
    if not ordered:
        raise SystemExit("no land tiles: is the DTM empty?")
    with out.open("wb") as f:
        writer = Writer(f)
        for z, x, y, data in ordered:
            writer.write_tile(zxy_to_tileid(z, x, y), data)
        west, south, east, north = bbox
        writer.finalize(
            {
                "tile_type": TileType.WEBP,
                "tile_compression": Compression.NONE,
                "min_lon_e7": int(west * 1e7), "min_lat_e7": int(south * 1e7),
                "max_lon_e7": int(east * 1e7), "max_lat_e7": int(north * 1e7),
                "center_zoom": 10,
                "center_lon_e7": int((west + east) / 2 * 1e7), "center_lat_e7": int((south + north) / 2 * 1e7),
            },
            {"name": "Vereda terrain", "encoding": "terrarium", "attribution": attribution},
        )
    return len(ordered)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("sources", nargs="+", type=Path)
    parser.add_argument("--bbox", required=True, help="west,south,east,north")
    parser.add_argument("--minzoom", type=int, default=8)
    parser.add_argument("--maxzoom", type=int, default=14)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--attribution", default="© DROTe (Direção Regional do Ordenamento do Território)")
    args = parser.parse_args()
    bbox = tuple(float(v) for v in args.bbox.split(","))
    tiles = []
    with tempfile.TemporaryDirectory() as tmp:
        for z in range(args.minzoom, args.maxzoom + 1):
            r = tile_range(bbox, z)
            heights = warp(args.sources, r, Path(tmp) / f"z{z}.bil")
            before = len(tiles)
            tiles.extend(tiles_of(heights, r))
            print(f"z{z}: {r.cols}x{r.rows} tiles in range, {len(tiles) - before} with land")
    count = pack(tiles, args.out, bbox, args.attribution)
    print(f"wrote {args.out}: {count} tiles, {args.out.stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
