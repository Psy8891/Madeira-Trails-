import pathlib
import sys

import pytest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

osmium = pytest.importorskip("osmium")

import osm_extract  # noqa: E402
from build_catalog import build  # noqa: E402

FIXTURE = str(pathlib.Path(__file__).parent / "fixtures" / "tiny.osm")
BBOX = (32.55, -17.30, 33.15, -16.25)


def elements_by(data, kind):
    return {e["id"]: e for e in data["elements"] if e["type"] == kind}


def test_extract_has_the_overpass_shape():
    data = osm_extract.read(FIXTURE, BBOX)
    relations = elements_by(data, "relation")
    assert set(relations) == {4444478}  # bus route and the mainland route are left out
    members = relations[4444478]["members"]
    way = next(m for m in members if m["type"] == "way")
    assert way["ref"] == 100
    assert way["geometry"][0] == {"lat": 32.71, "lon": -16.99}
    assert len(way["geometry"]) == 3
    assert relations[4444478]["tags"]["name"] == "Vereda do Pico Grande"


def test_extract_keeps_named_paths_and_peaks_inside_madeira():
    data = osm_extract.read(FIXTURE, BBOX)
    ways = elements_by(data, "way")
    assert set(ways) == {100, 101, 106}  # not the mainland path, not the street
    peaks = elements_by(data, "node")
    assert set(peaks) == {8}  # the unnamed peak is left out
    assert peaks[8]["tags"]["ele"] == "1654"


def test_extract_has_pois_places_and_the_path_network():
    data = osm_extract.read(FIXTURE, BBOX)
    kinds = {(p["kind"], p.get("name")) for p in data["pois"]}
    assert ("viewpoint", "Miradouro do Teste") in kinds
    assert ("parking", None) in kinds  # a car park area becomes its centre
    assert ("tunnel", "Túnel do Teste") in kinds  # both ends of the tunnel
    assert sum(1 for p in data["pois"] if p["kind"] == "tunnel") == 2
    assert [p["name"] for p in data["places"]] == ["Aldeia do Teste"]
    network = {w["id"]: w for w in data["network"]["ways"]}
    assert {100, 101, 103, 104, 106} <= set(network)
    assert 102 not in network  # outside Madeira
    assert network[104]["ref"] == "ER 999"
    assert data["network"]["coords"][3] == (32.712, -16.986)
    assert data["names"]["Vereda do Pico Grande"] >= 2


def test_catalog_builds_from_the_extract():
    catalog, report = build(osm_extract.read(FIXTURE, BBOX))
    trails = {t["id"]: t for t in catalog["trails"]}
    assert trails["osm-r4444478"]["name"] == "Vereda do Pico Grande"
    assert trails["osm-r4444478"]["aliases"] == ["Boca da Corrida – Pico Grande"]
    assert "Pico Grande" in [p.get("name") for p in catalog["places"]]
    assert "| Boca da Corrida – Pico Grande | yes | yes |" in report


def test_extract_build_adds_area_ends_and_pois():
    catalog, report = build(osm_extract.read(FIXTURE, BBOX))
    pico = {t["id"]: t for t in catalog["trails"]}["osm-r4444478"]
    assert pico["area"] == "Aldeia do Teste"
    start, end = pico["ends"]
    assert (start["lat"], start["lon"], start["approachM"]) == (32.712, -16.986, 0)
    assert start["road"] == "ER 999"
    assert start["parking"]  # the car park is next to the road point
    assert end["approachM"] > 200  # the far end walks back along the trail itself
    kinds = [p["kind"] for p in catalog["places"]]
    assert "viewpoint" in kinds and "parking" in kinds
    assert "bus_stop" not in kinds  # the bus stop is nowhere near a trail
    assert "Trail starts:" in report
