import pathlib
import sys
from array import array

import pytest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

from build_elevation import Grid, decode, encode, read_envi  # noqa: E402


def test_encode_round_trips_several_grids():
    grids = [
        Grid(32.7, -17.0, 0.00025, 2, 3, [0, 5, 1862, 1861, 0, -3]),
        Grid(33.05, -16.35, 0.00025, 1, 2, [517, 0]),
    ]
    back = decode(encode(grids))
    assert len(back) == 2
    for a, b in zip(grids, back):
        assert b.rows == a.rows and b.cols == a.cols
        assert b.heights == a.heights
        assert b.lat0 == pytest.approx(a.lat0, abs=1e-6)
        assert b.lon0 == pytest.approx(a.lon0, abs=1e-6)
        assert b.step == pytest.approx(a.step, abs=1e-6)


def test_flat_sea_costs_one_byte_per_cell():
    data = encode([Grid(32.7, -17.0, 0.00025, 100, 100, [0] * 10_000)])
    assert len(data) < 10_100


def test_read_envi_flips_to_south_first_and_clears_nodata(tmp_path):
    raw = array("h", [100, 200, -32768, 300, 400, 500])  # 2 rows, north first
    path = tmp_path / "t.bil"
    with path.open("wb") as f:
        raw.tofile(f)
    (tmp_path / "t.hdr").write_text(
        "ENVI\nsamples = 3\nlines   = 2\nbands   = 1\ndata type = 2\n"
        f"byte order = {0 if array('h', [1]).tobytes()[0] == 1 else 1}\n"
        "map info = {Geographic Lat/Lon, 1, 1, -17.0, 32.8, 0.25, 0.25, WGS-84}\n"
    )
    g = read_envi(path)
    assert (g.rows, g.cols) == (2, 3)
    assert g.heights == [300, 400, 500, 100, 200, 0]
    assert g.lat0 == pytest.approx(32.8 - 0.5 + 0.125)
    assert g.lon0 == pytest.approx(-17.0 + 0.125)
