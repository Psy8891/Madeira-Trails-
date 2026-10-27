import pathlib
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

from clean_dtm import NODATA, clean  # noqa: E402


def test_implausible_values_become_nodata():
    raw = np.array([[0.0, 1861.6, -3.4e38], [np.nan, -9999.0, 12.5], [2500.0, -20.0, 7.0]])
    out = clean(raw, nodata=7.0)
    assert out.dtype == np.float32
    assert out[0, 0] == 0.0
    assert out[0, 1] == np.float32(1861.6)
    assert out[1, 2] == np.float32(12.5)
    assert out[2, 1] == np.float32(-20.0)  # slightly below sea level is kept
    for r, c in [(0, 2), (1, 0), (1, 1), (2, 0), (2, 2)]:
        assert out[r, c] == NODATA
