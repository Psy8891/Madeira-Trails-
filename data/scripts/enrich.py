"""Catalog enrichment added in Phase 0.6 (see docs/SPEC.md, *Data model*): full names, a trail's
area, the walk from each trail end to the nearest road, and points of interest.

Everything here is pure Python on plain data so it can be unit-tested with tiny fixtures.
"""

from __future__ import annotations

import heapq
import math
import re
from collections import Counter, defaultdict

from geo_utils import Point, haversine_m, normalize

# --- Names ----------------------------------------------------------------------------------

# First words that already make a full place name, so the peak isn't given "Pico" in front.
LANDFORM_WORDS = {
    "pico", "picos", "achada", "cabeco", "alto", "bica", "chao", "chiqueiros", "curral", "encumeada",
    "estacao", "fonte", "lapa", "penha", "poco", "volta", "topo", "ninho", "passada", "base", "serra",
    "morro", "ponta", "lombo", "lombada", "monte", "rocha", "cume", "malhada", "terreiro", "torre",
    "boca", "bico", "cerro", "outeiro", "ilheu", "cimo", "miradouro", "eira", "cova", "fajã", "faja",
}
# Single words that are a full name on their own (a pinnacle, a rock, an islet).
STANDALONE_PEAKS = {"pico", "pinaculo", "penedo", "farilhao"}
ARTICLES = ("do", "da", "dos", "das", "de")

# Words that show a trail name was written in another language.
FOREIGN_WORDS = re.compile(
    r"\b(fur|für|umgehung\w*|variante|weg|wanderweg|steig|nord|sud|süd|ost|west|trail|path|walk|"
    r"hike|route|the|promenade|chemin|sentier|randonnee)\b",
    re.IGNORECASE,
)
FOREIGN_PAREN = re.compile(r"\s*\((nord|north|süd|sud|south|ost|east|west|ouest|est)\)", re.IGNORECASE)
LOWER_PARTICLES = re.compile(r"(?<=\S )(Da|De|Do|Das|Dos|E)(?= )")


def clean_trail_name(name: str, tags: dict | None = None) -> str:
    """The name to show for a community trail (rules in docs/SPEC.md, *Data model*)."""
    tags = tags or {}
    pt = (tags.get("name:pt") or "").strip()
    if pt and pt != name:
        name = pt
    name = FOREIGN_PAREN.sub("", name)
    name = re.sub(r"^[^\wÀ-ÿ]+|[^\wÀ-ÿ).]+$", "", name)
    name = re.sub(r"\bLeveda\b", "Levada", name)
    name = re.sub(r"\s+", " ", name).strip()
    name = LOWER_PARTICLES.sub(lambda m: m.group(1).lower(), name)
    return name[:1].upper() + name[1:] if name else name


def looks_odd(name: str) -> bool:
    """Names the report should list for a human to check or fix in OSM."""
    return bool(FOREIGN_WORDS.search(name)) or name.isupper() or len(name) < 4 or not re.search(r"[A-Za-zÀ-ÿ]{3}", name)


def full_peak_name(
    name: str, names: Counter | dict, fixes: dict[str, str] | None = None, aliases: list[str] | None = None
) -> str:
    """'Grande' → 'Pico Grande', 'do Facho' → 'Pico do Facho'; full names stay as they are.

    The article ("do", "das", …) is taken from how other OSM names spell the peak
    ("Túnel do Pico Grande", "Levada do Pico da Urze"), when they agree.
    """
    fixes = fixes or {}
    if name in fixes:
        return fixes[name]
    words = normalize(name).split()
    if not words:
        return name
    # Another recorded name that is the full one ("Pico da Ana Ferreira" for "Ana Ferreira").
    for alias in aliases or []:
        alias_words = normalize(alias).split()
        if len(alias_words) > len(words) and alias_words[0] in LANDFORM_WORDS and alias_words[-len(words):] == words:
            return alias
    if words[0] in ARTICLES:
        return "Pico " + name
    if len(words) == 1 and words[0] in STANDALONE_PEAKS:
        return name
    if len(words) > 1 and words[0] in LANDFORM_WORDS:
        return name

    target = normalize(name)
    forms: Counter = Counter()
    for other, count in names.items():
        text = normalize(other)
        if "pico" not in text:
            continue
        for article in ("", *ARTICLES):
            phrase = f"pico {article + ' ' if article else ''}{target}"
            if re.search(r"(^| )" + re.escape(phrase) + r"( |$)", text):
                forms[article] += count
    if not forms:
        return name if len(words) > 1 else "Pico " + name
    top = max(forms.values())
    # Prefer the spelled-out article when it is common ("Pico do Facho" over "Pico Facho").
    with_article = [(c, a) for a, c in forms.items() if a and c * 10 >= top * 3]
    article = max(with_article)[1] if with_article else max((c, a) for a, c in forms.items())[1]
    return f"Pico {article + ' ' if article else ''}{name}"


# --- Spatial index ----------------------------------------------------------------------------


class Grid:
    """Points bucketed in ~500 m cells for nearest-neighbour lookups."""

    CELL_DEG = 0.005

    def __init__(self) -> None:
        self.cells: dict[tuple[int, int], list[tuple[Point, object]]] = defaultdict(list)

    def _key(self, p: Point) -> tuple[int, int]:
        return (math.floor(p[0] / self.CELL_DEG), math.floor(p[1] / self.CELL_DEG))

    def add(self, p: Point, value: object) -> None:
        self.cells[self._key(p)].append((p, value))

    def near(self, p: Point, radius_m: float):
        """(distance, point, value) within radius, nearest first."""
        rings = int(radius_m / 400) + 1
        ky, kx = self._key(p)
        found = []
        for dy in range(-rings, rings + 1):
            for dx in range(-rings, rings + 1):
                for q, value in self.cells.get((ky + dy, kx + dx), ()):
                    d = haversine_m(p, q)
                    if d <= radius_m:
                        found.append((d, q, value))
        found.sort(key=lambda t: t[0])
        return found

    def nearest(self, p: Point, radius_m: float):
        found = self.near(p, radius_m)
        return found[0] if found else None


def point_along(line: list[Point], fraction: float) -> Point:
    """The point a given fraction of the way along a line."""
    total = sum(haversine_m(a, b) for a, b in zip(line, line[1:]))
    goal = total * fraction
    walked = 0.0
    for a, b in zip(line, line[1:]):
        step = haversine_m(a, b)
        if walked + step >= goal and step > 0:
            t = (goal - walked) / step
            return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        walked += step
    return line[-1]


def area_finder(places: list[dict]):
    grid = Grid()
    for place in places:
        grid.add((place["lat"], place["lon"]), place["name"])

    def area_of(line: list[Point]) -> str | None:
        hit = grid.nearest(point_along(line, 0.5), 4000)
        return hit[2] if hit else None

    return area_of


# --- Road access ------------------------------------------------------------------------------

CAR_ROADS = {
    "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
    "unclassified", "residential", "living_street", "service",
}
NOT_WALKABLE = {"motorway", "motorway_link", "trunk", "trunk_link"}
NO_ACCESS = {"no", "private"}
HARD_SAC = {"demanding_mountain_hiking", "alpine_hiking", "demanding_alpine_hiking", "difficult_alpine_hiking"}


def walk_cost(way: dict, on_route: bool) -> float:
    """How much a metre of this way counts when choosing the approach: known trails and named
    paths first, unnamed and hard paths only when they save a lot."""
    factor = 1.0 if on_route or way.get("name") or way.get("highway") == "track" else 1.6
    if way.get("sac") in HARD_SAC:
        factor *= 2.0
    return factor
SNAP_M = 30
MAX_APPROACH_M = 5000


class RoadAccess:
    """Finds the walk from a trail end to the nearest point a car can reach."""

    def __init__(self, network: dict, route_names: dict[int, str | None] | None = None) -> None:
        """route_names: ways of hiking routes and named paths, with the route's name for ways
        that have none of their own (used in `via`)."""
        coords = network.get("coords", {})
        self.coords: dict[int, Point] = {int(k): (v[0], v[1]) for k, v in coords.items()}
        self.adjacent: dict[int, list[tuple[int, float, int]]] = defaultdict(list)
        self.road_at: dict[int, str] = {}
        self.car_nodes: set[int] = set()
        self.ways = network.get("ways", [])
        self.grid = Grid()
        indexed: set[int] = set()
        self.route_names = route_names or {}

        for index, way in enumerate(self.ways):
            highway = way.get("highway")
            if highway in NOT_WALKABLE:
                continue
            nodes = [n for n in way["nodes"] if n in self.coords]
            is_car = (highway in CAR_ROADS or (highway == "track" and way.get("tracktype") == "grade1")) and way.get(
                "access"
            ) not in NO_ACCESS
            for node in nodes:
                if node not in indexed:
                    indexed.add(node)
                    self.grid.add(self.coords[node], node)
            if is_car:
                label = way.get("ref") or way.get("name")
                for node in nodes:
                    self.car_nodes.add(node)
                    if label and (node not in self.road_at or way.get("ref")):
                        self.road_at[node] = label
                continue
            factor = walk_cost(way, way.get("id") in self.route_names)
            for a, b in zip(nodes, nodes[1:]):
                d = haversine_m(self.coords[a], self.coords[b])
                self.adjacent[a].append((b, d, factor, index))
                self.adjacent[b].append((a, d, factor, index))

    def find(self, end: Point, own_names: set[str] | None = None, snap_m: float = SNAP_M) -> dict:
        """{"approachM": metres or None, "access": point, "road", "via", "line"} for one trail end
        (or a peak, with a wider snap_m since summits are often a little off the path)."""
        own = {normalize(n) for n in (own_names or set())}
        hit = self.grid.nearest(end, snap_m)
        if not hit:
            return {"approachM": None}
        gap, _, start = hit
        result = self._walk(start, own)
        if result["approachM"] is not None and gap > 1:
            result["approachM"] = round(result["approachM"] + gap)
            result["line"] = [*result["line"], end] if result["line"] else [self.coords[start], end]
        return result

    def _road_label(self, node: int, own: set[str]) -> str | None:
        label = self.road_at.get(node)
        return None if label and normalize(label) in own else label

    def _walk(self, start: int, own: set[str]) -> dict:
        if start in self.car_nodes:
            return {"approachM": 0, "access": self.coords[start], "road": self._road_label(start, own), "via": [], "line": []}

        # Dijkstra on cost; metres walked are tracked alongside for the 5 km limit and the answer.
        # The adjacency holds only walkable ways, so a road is reached at a node it shares with one.
        cost = {start: 0.0}
        metres = {start: 0.0}
        previous: dict[int, tuple[int, int]] = {}
        queue = [(0.0, start)]
        while queue:
            c, node = heapq.heappop(queue)
            if c > cost.get(node, math.inf):
                continue
            if node in self.car_nodes:
                return self._result(node, metres[node], previous, own)
            for nxt, step, factor, way in self.adjacent.get(node, ()):
                walked = metres[node] + step
                if walked > MAX_APPROACH_M:
                    continue
                nc = c + step * factor
                if nc < cost.get(nxt, math.inf):
                    cost[nxt] = nc
                    metres[nxt] = walked
                    previous[nxt] = (node, way)
                    heapq.heappush(queue, (nc, nxt))
        return {"approachM": None}

    def _result(self, target: int, distance: float, previous: dict, own: set[str]) -> dict:
        nodes = [target]
        ways: list[int] = []
        while nodes[-1] in previous:
            node, way = previous[nodes[-1]]
            nodes.append(node)
            ways.append(way)
        # nodes run road → trail end
        via: list[str] = []
        for way in ways:
            name = self.ways[way].get("name") or self.route_names.get(self.ways[way].get("id"))
            if name and normalize(name) not in own and name not in via:
                via.append(name)
        return {
            "approachM": round(distance),
            "access": self.coords[target],
            "road": self._road_label(target, own),
            "via": via[:3],
            "line": [self.coords[n] for n in nodes],
        }


def trail_ends(chains: list[list[Point]]) -> list[Point]:
    """The ends of a trail's main (longest) line; one point for a loop."""
    if not chains:
        return []
    main = max(chains, key=lambda c: sum(haversine_m(a, b) for a, b in zip(c, c[1:])))
    first, last = main[0], main[-1]
    if haversine_m(first, last) < 60:
        return [first]
    return [first, last]


# --- Points of interest -----------------------------------------------------------------------

NEAR_TRAIL_KINDS = {"parking", "toilets", "water"}
NEAR_END_KINDS = {"bus_stop"}
NEAR_M = 400
DEDUPE_M = 30


def select_pois(pois: list[dict], trail_points: list[Point], end_points: list[Point]) -> list[dict]:
    """POIs worth drawing: everything outdoors, and town things only close to trails."""
    near_trail = Grid()
    for p in trail_points:
        near_trail.add(p, None)
    near_end = Grid()
    for p in end_points:
        near_end.add(p, None)

    kept: list[dict] = []
    seen = Grid()
    for poi in sorted(pois, key=lambda p: (0 if p.get("name") else 1, p.get("id", ""))):
        point = (poi["lat"], poi["lon"])
        kind = poi["kind"]
        if kind in NEAR_TRAIL_KINDS and not near_trail.nearest(point, NEAR_M):
            continue
        if kind in NEAR_END_KINDS and not near_end.nearest(point, NEAR_M):
            continue
        if any(other == kind for _, _, other in seen.near(point, DEDUPE_M)):
            continue
        seen.add(point, kind)
        entry = {"kind": kind, "lat": round(poi["lat"], 6), "lon": round(poi["lon"], 6)}
        if poi.get("name"):
            entry["name"] = poi["name"]
        if poi.get("ele") is not None:
            entry["ele"] = poi["ele"]
        kept.append(entry)
    return kept


DUPLICATE_SHARE = 0.8
DUPLICATE_M = 30


def official_duplicates(official: list[tuple[str, list[list[Point]]]], community: list[tuple[str, list[list[Point]]]]):
    """Community trails that mostly run on an official trail's line: {community id: official code}.

    OSM sometimes has a second, unofficial relation for the same walk ("Levada da Rocha Vermelha A"
    on PR 28); listing both would show the trail twice, once without its status.
    """
    grid = Grid()
    for code, lines in official:
        for p in densify(lines, 25):
            grid.add(p, code)
    found: dict[str, str] = {}
    for trail_id, lines in community:
        points = densify(lines, 25)
        if not points:
            continue
        hits = Counter()
        for p in points:
            near = grid.nearest(p, DUPLICATE_M)
            if near:
                hits[near[2]] += 1
        if hits:
            code, count = hits.most_common(1)[0]
            if count >= DUPLICATE_SHARE * len(points):
                found[trail_id] = code
    return found


def densify(lines: list[list[Point]], step_m: float = 100) -> list[Point]:
    """Points along lines at most step_m apart, for 'near a trail' checks."""
    points: list[Point] = []
    for line in lines:
        for a, b in zip(line, line[1:]):
            points.append(a)
            d = haversine_m(a, b)
            for i in range(1, int(d // step_m) + 1):
                t = i * step_m / d
                points.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
        if line:
            points.append(line[-1])
    return points
