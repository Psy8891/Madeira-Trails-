import pathlib
import sys
from collections import Counter

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

import enrich  # noqa: E402
from geo_utils import haversine_m  # noqa: E402


# --- names ----------------------------------------------------------------------------------


def test_trail_names_are_cleaned():
    assert enrich.clean_trail_name("^Percurso de Treino") == "Percurso de Treino"
    assert enrich.clean_trail_name("Caminho Da Ribeira Das Lajes") == "Caminho da Ribeira das Lajes"
    assert enrich.clean_trail_name("Leveda do Lanço ou dos Barros") == "Levada do Lanço ou dos Barros"
    assert enrich.clean_trail_name("Levada do Norte (Nord)") == "Levada do Norte"
    assert enrich.clean_trail_name("  Vereda   do Pico  ") == "Vereda do Pico"
    # The first word keeps its capital even when it is a particle.
    assert enrich.clean_trail_name("Da Fonte ao Pico") == "Da Fonte ao Pico"


def test_portuguese_name_wins_over_a_foreign_one():
    tags = {"name": "Umgehungsvariante für Tunnel", "name:pt": "Variante do túnel"}
    assert enrich.clean_trail_name(tags["name"], tags) == "Variante do túnel"


def test_odd_names_are_flagged_for_the_report():
    assert enrich.looks_odd("Umgehungsvariante für Tunnel")
    assert enrich.looks_odd("Levada West")
    assert not enrich.looks_odd("Vereda do Pico Grande")
    assert not enrich.looks_odd("Caminho Real N°23")


NAMES = Counter(
    {
        "Túnel do Pico Grande": 1,
        "Vereda do Pico Grande": 1,
        "Caminho Pico Facho": 9,
        "Rua do Pico do Facho": 8,
        "Levada do Pico da Urze": 3,
        "Rua do Pico das Romeiras": 18,
        "Caminho Pico Romeiras": 5,
    }
)


def test_bare_peak_names_get_pico_and_the_usual_article():
    assert enrich.full_peak_name("Grande", NAMES) == "Pico Grande"
    assert enrich.full_peak_name("Urze", NAMES) == "Pico da Urze"
    assert enrich.full_peak_name("Romeiras", NAMES) == "Pico das Romeiras"
    # "Pico do Facho" is nearly as common as "Pico Facho", so the article is kept.
    assert enrich.full_peak_name("Facho", NAMES) == "Pico do Facho"
    # No other name mentions it: still "Pico …" rather than a bare word.
    assert enrich.full_peak_name("Gato", NAMES) == "Pico Gato"


def test_full_peak_names_stay_as_they_are():
    for name in ("Pico Ruivo", "Achada do Teixeira", "Cabeço da Lapa", "Pináculo", "Pico"):
        assert enrich.full_peak_name(name, NAMES) == name
    assert enrich.full_peak_name("do Areeiro", NAMES) == "Pico do Areeiro"


def test_peak_name_fixes_and_full_aliases_win():
    assert enrich.full_peak_name("Gato", NAMES, {"Gato": "Pico do Gato"}) == "Pico do Gato"
    assert enrich.full_peak_name("Ana Ferreira", NAMES, aliases=["Pico da Ana Ferreira", "Piano"]) == "Pico da Ana Ferreira"


# --- area -----------------------------------------------------------------------------------


def test_area_is_the_nearest_place_to_the_middle_of_the_line():
    area_of = enrich.area_finder(
        [
            {"name": "Near the start", "lat": 32.7000, "lon": -17.0000},
            {"name": "Near the middle", "lat": 32.7051, "lon": -17.0000},
            {"name": "Far away", "lat": 32.9000, "lon": -17.0000},
        ]
    )
    line = [(32.7000, -17.0000), (32.7100, -17.0000)]
    assert area_of(line) == "Near the middle"
    assert area_of([(32.6000, -16.5000), (32.6001, -16.5000)]) is None


# --- road access ----------------------------------------------------------------------------

# A trail end (node 1) joined by a path (1-2-3) to a road (3-4); a shorter unnamed path (1-5-6)
# reaches a car road further along, and a dead-end path (7-8) reaches nothing.
COORDS = {
    1: (32.7000, -17.0000),
    2: (32.7010, -17.0000),
    3: (32.7020, -17.0000),
    4: (32.7030, -17.0000),
    5: (32.6995, -17.0010),
    6: (32.6990, -17.0020),
    7: (32.8000, -17.1000),
    8: (32.8010, -17.1000),
    9: (32.6985, -17.0030),
}
WAYS = [
    {"id": 10, "nodes": [1, 2, 3], "highway": "path", "name": "Vereda Boa"},
    {"id": 11, "nodes": [3, 4], "highway": "tertiary", "name": "Estrada", "ref": "ER 1"},
    {"id": 12, "nodes": [7, 8], "highway": "path"},
    {"id": 13, "nodes": [1, 5, 6], "highway": "path"},
    {"id": 14, "nodes": [6, 9], "highway": "residential", "access": "private"},
]


def roads(route_names=None):
    return enrich.RoadAccess({"ways": WAYS, "coords": COORDS}, route_names)


def test_the_walk_to_the_road_follows_paths():
    found = roads().find(COORDS[1], {"Some Trail"})
    assert found["road"] == "ER 1"
    assert found["access"] == COORDS[3]
    assert found["via"] == ["Vereda Boa"]
    assert found["line"][0] == COORDS[3] and found["line"][-1] == COORDS[1]
    assert abs(found["approachM"] - haversine_m(COORDS[1], COORDS[3])) < 1


def test_an_end_on_a_road_needs_no_walk():
    found = roads().find(COORDS[3])
    assert found["approachM"] == 0
    assert found["road"] == "ER 1"


def test_no_road_nearby_and_no_network_nearby():
    assert roads().find(COORDS[7])["approachM"] is None
    assert roads().find((33.0, -16.3))["approachM"] is None


def test_the_trail_own_name_is_not_listed_as_via_or_road():
    found = roads().find(COORDS[1], {"Vereda Boa"})
    assert found["via"] == []


def test_peaks_snap_from_further_away_and_count_the_gap():
    summit = (32.7000, -17.0012)  # about 110 m west of node 1
    assert roads().find(summit)["approachM"] is None
    found = roads().find(summit, snap_m=150)
    assert found["approachM"] > haversine_m(COORDS[1], COORDS[3]) + 100
    assert found["line"][-1] == summit


def test_unnamed_route_ways_take_the_route_name():
    ways = [{"id": 20, "nodes": [1, 2], "highway": "path"}, {"id": 21, "nodes": [2, 3], "highway": "tertiary"}]
    found = enrich.RoadAccess({"ways": ways, "coords": COORDS}, {20: "Vereda da Fajã Escura"}).find(COORDS[1])
    assert found["via"] == ["Vereda da Fajã Escura"]


def test_trail_ends_use_the_longest_line_and_merge_loops():
    short = [(32.0, -17.0), (32.001, -17.0)]
    long = [(32.1, -17.0), (32.11, -17.0), (32.12, -17.0)]
    assert enrich.trail_ends([short, long]) == [long[0], long[-1]]
    loop = [(32.1, -17.0), (32.11, -17.0), (32.1002, -17.0)]
    assert enrich.trail_ends([loop]) == [loop[0]]
    assert enrich.trail_ends([]) == []


# --- points of interest ---------------------------------------------------------------------


def test_town_pois_are_kept_only_near_trails_and_duplicates_dropped():
    trail = [(32.70, -17.00), (32.70, -16.99)]
    pois = [
        {"kind": "viewpoint", "id": "n1", "lat": 32.80, "lon": -17.10, "name": "Miradouro"},
        {"kind": "parking", "id": "n2", "lat": 32.7005, "lon": -16.995},
        {"kind": "parking", "id": "n3", "lat": 32.75, "lon": -16.995},
        {"kind": "bus_stop", "id": "n4", "lat": 32.7002, "lon": -16.9902, "name": "Paragem"},
        {"kind": "bus_stop", "id": "n5", "lat": 32.7002, "lon": -16.9950},
        {"kind": "waterfall", "id": "n6", "lat": 32.71, "lon": -17.0},
        {"kind": "waterfall", "id": "n7", "lat": 32.7101, "lon": -17.0, "name": "Cascata"},
    ]
    kept = enrich.select_pois(pois, enrich.densify([trail]), [trail[-1]])
    got = sorted((p["kind"], p.get("name"), p["lat"]) for p in kept)
    assert got == [
        ("bus_stop", "Paragem", 32.7002),  # near the trail end; n5 is mid-trail
        ("parking", None, 32.7005),
        ("viewpoint", "Miradouro", 32.8),
        ("waterfall", "Cascata", 32.7101),  # the named one of two at the same spot
    ]


def test_densify_fills_long_segments():
    points = enrich.densify([[(32.70, -17.00), (32.71, -17.00)]], step_m=100)
    assert len(points) >= 11
    assert all(haversine_m(a, b) <= 101 for a, b in zip(points, points[1:]))


def test_community_copies_of_an_official_trail_are_found():
    official = [("PR 28", [[(32.70, -17.00), (32.70, -16.98)]])]
    copy = [(32.7001, -16.999), (32.7001, -16.982)]
    branch = [(32.70, -16.99), (32.72, -16.99)]  # shares one point, then leaves
    found = enrich.official_duplicates(official, [("osm-r1", [copy]), ("osm-r2", [branch])])
    assert found == {"osm-r1": "PR 28"}
