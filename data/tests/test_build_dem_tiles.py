import io
import pathlib
import sys

import numpy as np
import pytest
from PIL import Image

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

import build_dem_tiles as dem  # noqa: E402
from pmtiles.reader import MmapSource, Reader  # noqa: E402

MADEIRA = (-17.30, 32.55, -16.25, 33.15)


def test_terrarium_round_trips_heights():
    heights = np.array([[0.0, 1861.6], [-3.25, np.nan]])
    back = dem.decode_terrarium(dem.terrarium(heights))
    assert back[0, 0] == pytest.approx(0.0)
    assert back[0, 1] == pytest.approx(1861.6, abs=1 / 256)
    assert back[1, 0] == pytest.approx(-3.25, abs=1 / 256)
    assert back[1, 1] == pytest.approx(0.0)


def test_lossless_webp_keeps_every_height():
    heights = np.linspace(0, 1862, 256 * 256).reshape(256, 256)
    data = dem.webp(dem.terrarium(heights))
    rgb = np.asarray(Image.open(io.BytesIO(data)).convert("RGB"))
    assert np.abs(dem.decode_terrarium(rgb) - heights).max() < 1 / 256 + 1e-9


def test_tile_range_covers_the_islands():
    r = dem.tile_range(MADEIRA, 14)
    assert (r.cols, r.rows) == (49, 34) or (48 <= r.cols <= 50 and 32 <= r.rows <= 35)
    minx, miny, maxx, maxy = r.mercator_bounds()
    assert maxx - minx == pytest.approx(r.cols * 2 * dem.ORIGIN / 2 ** 14)
    # Pico Ruivo is inside.
    x, y = dem.tile_xy(-16.9431, 32.7587, 14)
    assert r.x0 <= x <= r.x1 and r.y0 <= y <= r.y1
    low = dem.tile_range(MADEIRA, 8)
    assert low.cols <= 2 and low.rows <= 2


def test_sea_tiles_are_left_out_and_pack_writes_pmtiles(tmp_path):
    r = dem.TileRange(10, 460, 410, 461, 410)  # two tiles side by side
    heights = np.zeros((256, 512), dtype=np.float32)
    heights[100:150, 300:400] = 900.0  # land only in the second tile
    tiles = list(dem.tiles_of(heights, r))
    assert [(z, x, y) for z, x, y, _ in tiles] == [(10, 461, 410)]
    out = tmp_path / "dem.pmtiles"
    assert dem.pack(tiles, out, MADEIRA, "test") == 1
    with out.open("rb") as f:
        reader = Reader(MmapSource(f))
        assert reader.header()["max_zoom"] == 10
        assert reader.get(10, 461, 410) is not None
        assert reader.get(10, 460, 410) is None
        assert reader.metadata()["encoding"] == "terrarium"


def test_read_envi_float_marks_nodata(tmp_path):
    raw = np.array([[1.5, -9999.0], [2.0, 3.0]], dtype="<f4")
    path = tmp_path / "z.bil"
    raw.tofile(path)
    (tmp_path / "z.hdr").write_text("ENVI\nsamples = 2\nlines = 2\ndata type = 4\nbyte order = 0\n")
    data = dem.read_envi_float(path, 2, 2)
    assert data[0, 0] == pytest.approx(1.5)
    assert np.isnan(data[0, 1])
