"""Writes the walking network the app routes on (spec: Phase 0.7, *Walking network*).

Every walkable OpenStreetMap way, cut at junctions, in a compact binary file
(app/src/main/assets/network.bin). Format in docs/SPEC.md; read by :core:route NetworkReader.
"""

from __future__ import annotations

import struct
from collections import Counter

from geo_utils import Point, haversine_m, normalize, simplify

MAGIC = b"VRN1"
VERSION = 1
SCALE = 100_000  # 1e-5 degrees, about 1 m

KIND = {
    "path": 0, "bridleway": 0, "cycleway": 0,
    "footway": 1,
    "steps": 2,
    "track": 3,
    "pedestrian": 4, "living_street": 4,
    "residential": 5, "service": 5, "unclassified": 5, "road": 5,
    "tertiary": 6, "tertiary_link": 6, "secondary": 6, "secondary_link": 6, "primary": 6, "primary_link": 6,
}
CAR_KINDS = {5, 6}
HARD_SAC = {"demanding_mountain_hiking", "alpine_hiking", "demanding_alpine_hiking", "difficult_alpine_hiking"}
FOOT_ALLOWED = {"yes", "designated", "permissive"}

F_NAMED, F_ROUTE, F_HARD, F_TUNNEL, F_LEVADA, F_CAR = 1, 2, 4, 8, 16, 32


def walkable(way: dict) -> bool:
    if way.get("highway") not in KIND:
        return False
    if way.get("foot") == "no":
        return False
    if way.get("access") in ("private", "no") and way.get("foot") not in FOOT_ALLOWED:
        return False
    return True


def flags_of(way: dict, route_name: str | None, on_route: bool) -> int:
    name = way.get("name") or route_name
    kind = KIND[way["highway"]]
    flags = 0
    if name:
        flags |= F_NAMED
    if on_route:
        flags |= F_ROUTE
    if way.get("sac") in HARD_SAC:
        flags |= F_HARD
    if way.get("tunnel"):
        flags |= F_TUNNEL
    if name and normalize(name).startswith("levada"):
        flags |= F_LEVADA
    if kind in CAR_KINDS and way.get("access") not in ("private", "no"):
        flags |= F_CAR
    return flags


def build(network: dict, route_names: dict[int, str | None], official_ways: dict[int, str]) -> dict:
    """Junction graph as plain data: {"names", "nodes", "edges"} (edges carry their geometry)."""
    coords = {int(k): (v[0], v[1]) for k, v in network.get("coords", {}).items()}
    ways = [w for w in network.get("ways", []) if walkable(w)]

    uses: Counter = Counter()
    for way in ways:
        nodes = [n for n in way["nodes"] if n in coords]
        for n in nodes:
            uses[n] += 1
        if nodes:
            uses[nodes[0]] += 1  # way ends are always junctions
            uses[nodes[-1]] += 1

    names: list[str] = []
    name_index: dict[str, int] = {}

    def name_id(text: str | None) -> int:
        if not text:
            return -1
        if text not in name_index:
            name_index[text] = len(names)
            names.append(text)
        return name_index[text]

    node_index: dict[int, int] = {}
    node_points: list[Point] = []

    def node_id(osm_node: int) -> int:
        if osm_node not in node_index:
            node_index[osm_node] = len(node_points)
            node_points.append(coords[osm_node])
        return node_index[osm_node]

    edges: list[dict] = []
    for way in ways:
        nodes = [n for n in way["nodes"] if n in coords]
        if len(nodes) < 2:
            continue
        route_name = route_names.get(way["id"])
        flags = flags_of(way, route_name, way["id"] in route_names and route_name is not None)
        kind = KIND[way["highway"]]
        name = name_id(way.get("name") or route_name)
        official = name_id(official_ways.get(way["id"]))
        start = 0
        for i in range(1, len(nodes)):
            if uses[nodes[i]] >= 2 or i == len(nodes) - 1:
                piece = [coords[n] for n in nodes[start : i + 1]]
                length = sum(haversine_m(a, b) for a, b in zip(piece, piece[1:]))
                if nodes[start] != nodes[i] or length > 0:
                    inner = simplify(piece, 2.0)[1:-1]
                    edges.append(
                        {
                            "from": node_id(nodes[start]),
                            "to": node_id(nodes[i]),
                            "lengthDm": max(1, round(length * 10)),
                            "kind": kind,
                            "flags": flags,
                            "name": name,
                            "official": official,
                            "inner": inner,
                        }
                    )
                start = i
    return {"names": names, "nodes": node_points, "edges": edges}


def _varint(value: int, out: bytearray) -> None:
    if value < 0:
        raise ValueError("varint must be >= 0")
    while value >= 0x80:
        out.append((value & 0x7F) | 0x80)
        value >>= 7
    out.append(value)


def _zigzag(value: int) -> int:
    return (value << 1) ^ (value >> 63)


def _point(p: Point, previous: tuple[int, int], out: bytearray) -> tuple[int, int]:
    lat, lon = round(p[0] * SCALE), round(p[1] * SCALE)
    _varint(_zigzag(lat - previous[0]), out)
    _varint(_zigzag(lon - previous[1]), out)
    return lat, lon


def encode(graph: dict) -> bytes:
    out = bytearray(MAGIC)
    out += struct.pack("<H", VERSION)
    _varint(len(graph["names"]), out)
    for name in graph["names"]:
        data = name.encode("utf-8")
        _varint(len(data), out)
        out += data
    _varint(len(graph["nodes"]), out)
    previous = (0, 0)
    fixed: list[tuple[int, int]] = []
    for p in graph["nodes"]:
        previous = _point(p, previous, out)
        fixed.append(previous)
    _varint(len(graph["edges"]), out)
    for e in graph["edges"]:
        _varint(e["from"], out)
        _varint(e["to"], out)
        _varint(e["lengthDm"], out)
        out.append(e["kind"])
        out.append(e["flags"])
        _varint(e["name"] + 1, out)
        _varint(e["official"] + 1, out)
        _varint(len(e["inner"]), out)
        previous = fixed[e["from"]]
        for p in e["inner"]:
            previous = _point(p, previous, out)
    return bytes(out)


def decode(data: bytes) -> dict:
    """Reads network.bin back (used by the tests; the app has its own Kotlin reader)."""
    pos = 0

    def varint() -> int:
        nonlocal pos
        shift = result = 0
        while True:
            b = data[pos]
            pos += 1
            result |= (b & 0x7F) << shift
            if b < 0x80:
                return result
            shift += 7

    def zz() -> int:
        v = varint()
        return (v >> 1) ^ -(v & 1)

    assert data[:4] == MAGIC
    (version,) = struct.unpack_from("<H", data, 4)
    pos = 6
    names = []
    for _ in range(varint()):
        n = varint()
        names.append(data[pos : pos + n].decode("utf-8"))
        pos += n
    nodes = []
    lat = lon = 0
    for _ in range(varint()):
        lat += zz()
        lon += zz()
        nodes.append((lat, lon))
    edges = []
    for _ in range(varint()):
        e = {"from": varint(), "to": varint(), "lengthDm": varint()}
        e["kind"] = data[pos]
        e["flags"] = data[pos + 1]
        pos += 2
        e["name"] = varint() - 1
        e["official"] = varint() - 1
        lat, lon = nodes[e["from"]]
        inner = []
        for _ in range(varint()):
            lat += zz()
            lon += zz()
            inner.append((lat / SCALE, lon / SCALE))
        e["inner"] = inner
        edges.append(e)
    return {"version": version, "names": names, "nodes": [(a / SCALE, b / SCALE) for a, b in nodes], "edges": edges}
