import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

import pytest  # noqa: E402

from build_catalog import build, check_overpass_answer  # noqa: E402
from geo_utils import decode_polyline  # noqa: E402


def geom(points):
    return [{"lat": lat, "lon": lon} for lat, lon in points]


def line(lat, lon0, n=12, step=0.001):
    return [(lat, lon0 + i * step) for i in range(n)]


def overpass_sample():
    pr14_a = line(32.80, -17.10, 6)
    pr14_b = line(32.80, pr14_a[-1][1], 6)
    pico_grande = line(32.70, -17.00, 15)
    levada_a = line(32.66, -17.05, 4)
    levada_b = line(32.66, levada_a[-1][1], 4)
    return {
        "elements": [
            {
                "type": "relation",
                "id": 2239199,
                "tags": {"route": "hiking", "ref": "PR14", "name": "PR14 - Levada dos Cedros"},
                "members": [
                    {"type": "way", "ref": 1, "role": "", "geometry": geom(pr14_b)},
                    {"type": "way", "ref": 2, "role": "", "geometry": geom(pr14_a)},
                ],
            },
            {
                "type": "relation",
                "id": 555,
                "tags": {"route": "hiking", "name": "Boca da Corrida - Pico Grande"},
                "members": [{"type": "way", "ref": 3, "role": "", "geometry": geom(pico_grande)}],
            },
            {
                "type": "relation",
                "id": 556,
                "tags": {"route": "hiking", "name": "Vereda do Pico Ruivo"},  # official, mapped without ref
                "members": [{"type": "way", "ref": 4, "role": "", "geometry": geom(line(32.76, -16.94, 5))}],
            },
            {"type": "way", "id": 3, "tags": {"highway": "path", "name": "Pico Grande"}, "geometry": geom(pico_grande)},
            {"type": "way", "id": 10, "tags": {"highway": "path", "name": "Levada Nova"}, "geometry": geom(levada_a)},
            {"type": "way", "id": 11, "tags": {"highway": "path", "name": "Levada Nova", "sac_scale": "hiking"}, "geometry": geom(levada_b)},
            {"type": "way", "id": 12, "tags": {"highway": "footway", "name": "Rua da Sé"}, "geometry": geom(line(32.65, -16.91, 8))},
            {"type": "way", "id": 13, "tags": {"highway": "track", "name": "Estrada Florestal"}, "geometry": geom(line(32.75, -16.95, 8))},
            {"type": "way", "id": 14, "tags": {"highway": "path", "name": "Atalho"}, "geometry": geom(line(32.75, -16.90, 2))},
            {"type": "node", "id": 99, "lat": 32.7146, "lon": -16.9930, "tags": {"natural": "peak", "name": "Pico Grande", "ele": "1654"}},
            {"type": "node", "id": 98, "lat": 33.08, "lon": -16.33, "tags": {"natural": "peak", "name": "Pico do Facho", "ele": "516 m"}},
        ]
    }


def by_id(catalog):
    return {t["id"]: t for t in catalog["trails"]}


def test_official_trail_gets_its_line_from_the_pr_relation():
    catalog, _ = build(overpass_sample())
    pr14 = by_id(catalog)["pr-14"]
    assert pr14["source"] == "official"
    assert pr14["osm"] == ["r2239199"]
    assert len(pr14["lines"]) == 1  # the two ways were chained into one line
    points = decode_polyline(pr14["lines"][0])
    assert points[0][1] < points[-1][1] or points[0][1] > points[-1][1]
    assert pr14["lengthM"] > 900


def test_every_official_trail_is_listed_even_without_a_line():
    catalog, report = build(overpass_sample())
    official = [t for t in catalog["trails"] if t["source"] == "official"]
    assert len(official) == 42
    assert by_id(catalog)["pr-6"]["lines"] == []
    assert "Missing official lines:" in report and "PR 6," in report


def test_community_relation_is_included_and_official_duplicates_are_not():
    catalog, _ = build(overpass_sample())
    trails = by_id(catalog)
    assert trails["osm-r555"]["name"] == "Boca da Corrida - Pico Grande"
    assert trails["osm-r555"]["source"] == "community"
    assert "osm-r556" not in trails


def test_official_trail_mapped_without_ref_is_matched_by_name():
    catalog, report = build(overpass_sample())
    pr12 = by_id(catalog)["pr-1.2"]
    assert pr12["osm"] == ["r556"]
    assert len(pr12["lines"]) == 1
    assert "PR 1.2," not in report


def test_overpass_errors_inside_a_200_answer_are_rejected():
    with pytest.raises(ValueError):
        check_overpass_answer({"elements": [], "remark": "runtime error: Query timed out in \"query\""})
    with pytest.raises(ValueError):
        check_overpass_answer({"version": 0.6})
    assert check_overpass_answer({"elements": [{"type": "node"}]})["elements"]


def test_named_paths_are_chained_and_urban_or_short_ones_dropped():
    catalog, _ = build(overpass_sample())
    names = {t["name"]: t for t in catalog["trails"]}
    assert names["Levada Nova"]["id"] == "osm-w10"
    assert len(names["Levada Nova"]["lines"]) == 1
    assert names["Levada Nova"]["sacScale"] == "hiking"
    assert "Rua da Sé" not in names
    assert "Estrada Florestal" not in names
    assert "Atalho" not in names
    # The way that belongs to the Pico Grande relation is not repeated as its own trail.
    assert "Pico Grande" not in names


def test_peaks_become_places_with_elevation():
    catalog, _ = build(overpass_sample())
    places = {p["name"]: p for p in catalog["places"]}
    assert places["Pico Grande"]["ele"] == 1654
    assert places["Pico do Facho"]["ele"] == 516


def test_report_checks_the_wanted_list():
    _, report = build(overpass_sample())
    assert "| Boca da Corrida – Pico Grande | yes | yes |" in report
