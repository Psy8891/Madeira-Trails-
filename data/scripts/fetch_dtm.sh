#!/usr/bin/env bash
# Downloads the DROTe terrain models of Madeira (10 m, PTRA08 / UTM 28N, EPSG:5016) and
# Porto Santo (10 m, EPSG:3061) from the regional WCS and cleans them (spec: Phase 1, build-maps).
# The Madeira coverage spans the whole sea area around the archipelago, so only the island is
# requested. Output: <dir>/madeira.tif and <dir>/portosanto.tif (Float32, no data -9999).
set -euo pipefail
dir=${1:-work}
mkdir -p "$dir"
here=$(cd "$(dirname "$0")" && pwd)

fetch() { # url out
  for attempt in 1 2 3; do
    if curl -sS --fail -m 1200 -A "Vereda map build (+https://github.com/Psy8891/Madeira-Trails-)" -o "$2" "$1"; then
      if gdalinfo "$2" >/dev/null 2>&1; then return 0; fi
      echo "Not a raster (attempt $attempt):"; head -c 400 "$2"; echo
    fi
    sleep $((attempt * 20))
  done
  echo "::error title=Terrain::Couldn't download $1"
  return 1
}

fetch "${MADEIRA_WCS}?service=WCS&version=2.0.1&request=GetCoverage&coverageId=${MADEIRA_COVERAGE}&format=image/tiff&subset=E(284000,349500)&subset=N(3608000,3643500)" "$dir/madeira-raw.tif"
fetch "${PORTOSANTO_WCS}?service=WCS&version=2.0.1&request=GetCoverage&coverageId=${PORTOSANTO_COVERAGE}&format=image/tiff" "$dir/portosanto-raw.tif"

for island in madeira portosanto; do
  /usr/bin/python3 "$here/clean_dtm.py" "$dir/$island-raw.tif" "$dir/$island.tif"
  rm -f "$dir/$island-raw.tif"
done

# Spot checks (spec: Phase 0): Pico Ruivo 1,862 m, Pico do Areeiro 1,818 m.
for p in "-16.9431 32.7587 Pico_Ruivo" "-16.9284 32.7357 Pico_do_Areeiro"; do
  set -- $p
  h=$(gdallocationinfo -valonly -wgs84 "$dir/madeira.tif" "$1" "$2")
  echo "$3: $h m"
  echo "::notice title=Spot height::$3 $h m"
done
