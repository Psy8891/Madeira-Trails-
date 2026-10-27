"""Cleans a downloaded terrain model before it is used (spec: Phase 1, build-maps).

The DROTe coverages arrive as 64-bit floats whose no-data value isn't always flagged. Anything
that isn't a plausible height on these islands (not finite, below -50 m or above 2,000 m) becomes
no data (-9999), and the result is written as a tiled, compressed 32-bit GeoTIFF.

Runs with the system Python and GDAL's bindings (apt: python3-gdal python3-numpy):

    /usr/bin/python3 clean_dtm.py raw.tif clean.tif
"""

from __future__ import annotations

import sys

import numpy as np

NODATA = -9999.0
LOWEST_M = -50.0
HIGHEST_M = 2000.0  # Pico Ruivo is 1,862 m


def clean(values: np.ndarray, nodata: float | None) -> np.ndarray:
    v = values.astype(np.float64, copy=True)
    bad = ~np.isfinite(v) | (v < LOWEST_M) | (v > HIGHEST_M)
    if nodata is not None and np.isfinite(nodata):
        bad |= v == nodata
    v[bad] = NODATA
    return v.astype(np.float32)


def main() -> None:
    from osgeo import gdal  # system package, not needed by the tests

    gdal.UseExceptions()
    src_path, out_path = sys.argv[1], sys.argv[2]
    src = gdal.Open(src_path)
    band = src.GetRasterBand(1)
    data = clean(band.ReadAsArray(), band.GetNoDataValue())
    driver = gdal.GetDriverByName("GTiff")
    out = driver.Create(out_path, src.RasterXSize, src.RasterYSize, 1, gdal.GDT_Float32,
                        options=["TILED=YES", "COMPRESS=DEFLATE", "PREDICTOR=3"])
    out.SetGeoTransform(src.GetGeoTransform())
    out.SetProjection(src.GetProjection())
    out_band = out.GetRasterBand(1)
    out_band.SetNoDataValue(NODATA)
    out_band.WriteArray(data)
    out_band.FlushCache()
    land = data[data > NODATA]
    print(f"{out_path}: {src.RasterXSize} x {src.RasterYSize}, {land.size} cells with data, "
          f"{land.min() if land.size else 0:.1f} to {land.max() if land.size else 0:.1f} m")
    if land.size == 0:
        raise SystemExit("no heights in the terrain model")


if __name__ == "__main__":
    main()
