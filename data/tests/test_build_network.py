import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

import build_network  # noqa: E402

# A path 1-2-3 crossed at 2 by a road 4-2-5, a private drive 3-6, and a motorway 5-7.
COORDS = {
    1: (32.7000, -17.0000),
    2: (32.7010, -17.0000),
    3: (32.7020, -17.0000),
    4: (32.7010, -17.0010),
    5: (32.7010, -16.9990),
    6: (32.7030, -17.0000),
    7: (32.7010, -16.9900),
    8: (32.70155, -17.00001),  # bend on the path between 2 and 3
}
WAYS = [
    {"id": 10, "nodes": [1, 2, 8, 3], "highway": "path", "sac": "demanding_mountain_hiking"},
    {"id": 11, "nodes": [4, 2, 5], "highway": "tertiary", "ref": "ER 1"},
    {"id": 12, "nodes": [3, 6], "highway": "service", "access": "private"},
    {"id": 13, "nodes": [5, 7], "highway": "trunk"},
    {"id": 14, "nodes": [6, 1], "highway": "footway", "foot": "no"},
]


def graph():
    return build_network.build({"ways": WAYS, "coords": COORDS}, {10: "Levada Velha"}, {10: "PR 99"})


def test_ways_are_cut_at_junctions_and_unwalkable_ones_left_out():
    g = graph()
    assert len(g["nodes"]) == 5  # 1, 2, 3, 4, 5 (6 and 7 only on excluded ways)
    assert len(g["edges"]) == 4  # path 1-2, path 2-3, road 4-2, road 2-5
    path_edges = [e for e in g["edges"] if e["kind"] == 0]
    assert [len(e["inner"]) for e in path_edges] == [0, 0]  # the 2 m bend is simplified away


def test_flags_names_and_official_trail():
    g = graph()
    path = next(e for e in g["edges"] if e["kind"] == 0)
    road = next(e for e in g["edges"] if e["kind"] == 6)
    flags = build_network
    assert path["flags"] & flags.F_ROUTE and path["flags"] & flags.F_HARD and path["flags"] & flags.F_LEVADA
    assert not path["flags"] & flags.F_CAR
    assert g["names"][path["name"]] == "Levada Velha"
    assert g["names"][path["official"]] == "PR 99"
    assert road["flags"] & flags.F_CAR and road["name"] == -1 and road["official"] == -1


def test_encode_decode_round_trip():
    g = graph()
    g["edges"][0]["inner"] = [(32.70051, -17.00002)]
    back = build_network.decode(build_network.encode(g))
    assert back["version"] == 1
    assert back["names"] == g["names"]
    assert back["nodes"] == [(round(a, 5), round(b, 5)) for a, b in g["nodes"]]
    assert len(back["edges"]) == len(g["edges"])
    for a, b in zip(back["edges"], g["edges"]):
        for key in ("from", "to", "lengthDm", "kind", "flags", "name", "official"):
            assert a[key] == b[key]
    assert back["edges"][0]["inner"] == [(32.70051, -17.00002)]
    assert abs(back["edges"][0]["lengthDm"] - 1112) < 3  # 111 m between 1 and 2
