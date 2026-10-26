"""Builds the trail catalog bundled in the app from OpenStreetMap (ODbL).

- Official trails: hiking route relations whose ref (or name) is a PR code from data/official.json.
- Community trails: other named hiking route relations, plus named footpaths that are not part of
  any route, chained into continuous lines.
- Places: named peaks, so searches like "Pico Grande" always land somewhere on the map, and
  (from an extract) points of interest.
- From an extract, also each trail's area and the walk from its ends to the nearest road
  (Phase 0.6, see enrich.py).

Writes app/src/main/assets/catalog.json and data/catalog-report.md.
Usage: python data/scripts/build_catalog.py [--pbf madeira.osm.pbf | --input overpass.json]
Without options it asks the public Overpass servers.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
import time
from collections import Counter, defaultdict

import requests

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from geo_utils import (  # noqa: E402
    chain_lines,
    encode_polyline,
    length_m,
    normalize,
    parse_pr_code,
    simplify,
)
import enrich  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
OFFICIAL = ROOT / "data" / "official.json"
WANTED = ROOT / "data" / "wanted.json"
NAME_FIXES = ROOT / "data" / "name-fixes.json"
OUT_CATALOG = ROOT / "app" / "src" / "main" / "assets" / "catalog.json"
OUT_REPORT = ROOT / "data" / "catalog-report.md"

BBOX = "32.55,-17.30,33.15,-16.25"  # Madeira + Porto Santo (south, west, north, east)
MIN_ROUTES_IN_EXTRACT = 15  # fewer means the extract doesn't cover Madeira
PORTO_SANTO_MIN_LAT = 32.95
MIN_COMMUNITY_LENGTH_M = 400
ENDPOINTS = [
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
]
USER_AGENT = "VeredaCatalog/1.0 (+https://github.com/Psy8891/Madeira-Trails-)"

# Three small queries instead of one big one: public Overpass servers are often busy and
# reject or time out large requests.
QUERIES = {
    "routes": f'[out:json][timeout:180];relation["route"~"^(hiking|foot)$"]({BBOX});out geom;',
    "paths": f'[out:json][timeout:180];way["highway"~"^(path|footway|steps|track)$"]["name"]({BBOX});out geom;',
    "peaks": f'[out:json][timeout:60];node["natural"="peak"]["name"]({BBOX});out;',
}

# Named footways that are streets or urban paths, not trails.
URBAN_NAME = re.compile(
    r"^(rua|travessa|beco|largo|avenida|estrada|calcada|escadinhas|praca|caminho municipal)\b"
)
TRAIL_WORDS = re.compile(r"\b(levada|vereda|caminho|trilho|percurso|pico|ponta)\b")


def check_overpass_answer(data: dict) -> dict:
    """Overpass reports timeouts and overload inside a 200 answer; partial data must not be used."""
    remark = data.get("remark", "")
    if "error" in remark.lower():
        raise ValueError(f"Overpass remark: {remark}")
    if "elements" not in data:
        raise ValueError("Overpass answer has no elements")
    return data


def fetch_query(name: str, query: str) -> dict:
    last_error: Exception | None = None
    for attempt in range(4):
        for url in ENDPOINTS:
            try:
                response = requests.post(url, data={"data": query}, headers={"User-Agent": USER_AGENT}, timeout=240)
                response.raise_for_status()
                data = check_overpass_answer(response.json())
                print(f"Overpass {name}: {len(data['elements'])} elements from {url}")
                return data
            except Exception as error:  # noqa: BLE001 - try the next mirror
                last_error = error
                print(f"Overpass {name} via {url} failed: {str(error)[:200]}", file=sys.stderr)
        time.sleep(45 * (attempt + 1))
    raise SystemExit(f"Overpass unavailable for {name}: {last_error}")


def fetch_overpass() -> dict:
    elements: list[dict] = []
    for name, query in QUERIES.items():
        elements.extend(fetch_query(name, query)["elements"])
    return {"elements": elements}


def read_extract(path: str) -> dict:
    import osm_extract  # needs pyosmium; only this path uses it

    bbox = tuple(float(v) for v in BBOX.split(","))
    data = osm_extract.read(path, bbox)
    routes = sum(1 for e in data["elements"] if e["type"] == "relation")
    print(f"Extract {path}: {routes} route relations, {len(data['elements'])} elements")
    if routes < MIN_ROUTES_IN_EXTRACT:
        raise SystemExit(f"Extract has only {routes} hiking routes; it probably doesn't cover Madeira")
    return data


def way_points(element: dict) -> list[tuple[float, float]]:
    return [(g["lat"], g["lon"]) for g in element.get("geometry", []) if g]


def centre_lat(lines: list[list[tuple[float, float]]]) -> float:
    lats = [p[0] for line in lines for p in line]
    return (min(lats) + max(lats)) / 2 if lats else 0.0


def encode_lines(lines: list[list[tuple[float, float]]]) -> list[str]:
    return [encode_polyline(simplify(line, 3.0)) for line in lines if len(line) >= 2]


def island_of(lines) -> str:
    return "porto_santo" if centre_lat(lines) > PORTO_SANTO_MIN_LAT else "madeira"


def community_relation(el: dict, lines: list) -> dict | None:
    """A named route relation as a community trail, or None when it is too short."""
    tags = el.get("tags", {})
    chains = chain_lines(lines)
    total = sum(length_m(c) for c in chains)
    if total < MIN_COMMUNITY_LENGTH_M:
        return None
    return {
        "id": f"osm-r{el['id']}",
        "source": "community",
        "name": enrich.clean_trail_name(tags["name"], tags),
        "from": tags.get("from"),
        "to": tags.get("to"),
        "island": island_of(chains),
        "lengthM": round(total),
        "sacScale": tags.get("sac_scale"),
        "osm": [f"r{el['id']}"],
        "lines": encode_lines(chains),
        "_chains": chains,
    }


def place_aliases(tags: dict) -> list[str]:
    """Other names OSM records for a place (Madeira peaks are often named without "Pico")."""
    names: list[str] = []
    for key in ("official_name", "alt_name", "name:pt", "loc_name", "old_name"):
        for value in str(tags.get(key, "")).split(";"):
            value = value.strip()
            if value and value != tags.get("name") and value not in names:
                names.append(value)
    return names


def build(data: dict) -> tuple[dict, str]:
    official_list = json.loads(OFFICIAL.read_text(encoding="utf-8"))
    official_by_code = {o["code"]: o for o in official_list}
    official_by_name = {normalize(o["name"]): o["code"] for o in official_list}

    official_lines: dict[str, list] = defaultdict(list)
    official_osm: dict[str, list[str]] = defaultdict(list)
    community: list[dict] = []
    route_way_ids: set[int] = set()
    route_way_names: dict[int, str | None] = {}
    places: list[dict] = []
    path_ways: list[dict] = []
    name_matches: dict[str, list] = defaultdict(list)

    for el in data.get("elements", []):
        tags = el.get("tags", {})
        if el["type"] == "relation":
            members = [m for m in el.get("members", []) if m.get("type") == "way" and m.get("geometry")]
            lines = [[(g["lat"], g["lon"]) for g in m["geometry"] if g] for m in members]
            lines = [line for line in lines if len(line) >= 2]
            if not lines:
                continue
            route_way_ids.update(m["ref"] for m in members)
            for m in members:
                route_way_names.setdefault(m["ref"], tags.get("name"))
            porto_santo = centre_lat(lines) > PORTO_SANTO_MIN_LAT
            code = parse_pr_code(tags.get("ref"), porto_santo) or parse_pr_code(tags.get("name"), porto_santo)
            if code in official_by_code:
                official_lines[code].extend(lines)
                official_osm[code].append(f"r{el['id']}")
                continue
            name = tags.get("name")
            if not name:
                continue
            name_code = official_by_name.get(normalize(name))
            if name_code:
                # Same name as an official trail but no PR ref: decided after the loop, because
                # names repeat (there is a Levada do Moinho in Porto Moniz and one in Ponta do Sol).
                name_matches[name_code].append((el, lines))
                continue
            entry = community_relation(el, lines)
            if entry:
                community.append(entry)
        elif el["type"] == "way":
            path_ways.append(el)
        elif el["type"] == "node" and tags.get("name"):
            ele = tags.get("ele")
            try:
                ele_m = round(float(str(ele).replace(",", ".").split()[0])) if ele else None
            except ValueError:
                ele_m = None
            place = {"name": tags["name"], "kind": "peak", "lat": round(el["lat"], 6), "lon": round(el["lon"], 6), "ele": ele_m}
            aliases = place_aliases(tags)
            if aliases:
                place["aliases"] = aliases
            places.append(place)

    # An official trail mapped without its PR ref is used only when no relation carries that ref;
    # otherwise the namesake is a different walk and stays a community trail.
    matched_by_ref = {code for code, lines in official_lines.items() if lines}
    for code, matches in name_matches.items():
        for el, lines in matches:
            if code in matched_by_ref:
                entry = community_relation(el, lines)
                if entry:
                    community.append(entry)
            else:
                official_lines[code].extend(lines)
                official_osm[code].append(f"r{el['id']}")

    # Named paths that are not already part of a route relation, grouped by name.
    by_name: dict[str, list[dict]] = defaultdict(list)
    for way in path_ways:
        if way["id"] in route_way_ids:
            continue
        name = way["tags"]["name"]
        key = normalize(name)
        if URBAN_NAME.match(key):
            continue
        if way["tags"].get("highway") == "track" and not TRAIL_WORDS.search(key):
            continue
        by_name[name].append(way)

    for name, ways in by_name.items():
        chains = chain_lines([way_points(w) for w in ways])
        total = sum(length_m(c) for c in chains)
        if total < MIN_COMMUNITY_LENGTH_M:
            continue
        sac = next((w["tags"].get("sac_scale") for w in ways if w["tags"].get("sac_scale")), None)
        community.append(
            {
                "id": f"osm-w{min(w['id'] for w in ways)}",
                "source": "community",
                "name": enrich.clean_trail_name(name, ways[0]["tags"]),
                "from": None,
                "to": None,
                "island": island_of(chains),
                "lengthM": round(total),
                "sacScale": sac,
                "osm": [f"w{w['id']}" for w in sorted(ways, key=lambda w: w["id"])][:20],
                "lines": encode_lines(chains),
                "_chains": chains,
            }
        )

    trails = []
    missing = []
    for o in official_list:
        code = o["code"]
        chains = chain_lines(official_lines.get(code, []))
        if not chains:
            missing.append(code)
        trails.append(
            {
                "id": code.lower().replace(" ", "-"),
                "source": "official",
                "code": code,
                "name": o["name"],
                "island": o["island"],
                "lengthM": round(sum(length_m(c) for c in chains)) if chains else None,
                "osm": official_osm.get(code, []),
                "lines": encode_lines(chains),
                "_chains": chains,
            }
        )

    fixes = json.loads(NAME_FIXES.read_text(encoding="utf-8")) if NAME_FIXES.exists() else {}
    for t in community:
        fixed = fixes.get("trails", {}).get(t["id"])
        if fixed:
            t["name"] = fixed

    # Names people search for that OSM doesn't use ("Boca da Corrida – Pico Grande").
    # Also a short note where the OSM line needs context (e.g. only part of the walk is mapped).
    wanted = json.loads(WANTED.read_text(encoding="utf-8"))["trails"]
    for t in community:
        key = normalize(t["name"])
        matches = [w for w in wanted if any(normalize(m) in key for m in w["match"])]
        aliases = [w["searchAlias"] for w in matches if w.get("searchAlias")]
        if aliases:
            t["aliases"] = aliases
        for w in matches:
            if w.get("noteEn"):
                t["noteEn"] = w["noteEn"]
                t["notePt"] = w.get("notePt", w["noteEn"])
                break

    community.sort(key=lambda t: normalize(t["name"]))
    trails.extend(community)

    # Peaks get the name people use ("Grande" → "Pico Grande"); OSM's name stays searchable.
    names = Counter(data.get("names") or {}) or Counter(
        el.get("tags", {}).get("name") for el in data.get("elements", []) if el.get("tags", {}).get("name")
    )
    renamed: list[tuple[str, str]] = []
    for place in places:
        full = enrich.full_peak_name(place["name"], names, fixes.get("peaks"), place.get("aliases"))
        if full != place["name"]:
            place["aliases"] = [place["name"], *[a for a in place.get("aliases", []) if a not in (full, place["name"])]]
            renamed.append((place["name"], full))
            place["name"] = full

    extra = enrich_trails(trails, data, route_way_names)
    places.extend(extra.pop("pois"))
    add_place_access(places, extra.pop("roads"), extra.pop("parking"))
    kind_order = {"peak": 0}
    places.sort(key=lambda p: (kind_order.get(p["kind"], 1), normalize(p.get("name", "")), p["lat"], p["lon"]))

    catalog = {
        "schema": 1,
        "generatedAt": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "attribution": "© OpenStreetMap contributors (ODbL)",
        "trails": trails,
        "places": places,
    }
    return catalog, report(catalog, missing, renamed, extra)


def enrich_trails(trails: list[dict], data: dict, route_way_names: dict[int, str | None] | None = None) -> dict:
    """Adds `area` and `ends` to every trail with a line and picks the POIs to keep.

    Needs the extra data only an extract gives (places, network, pois); with Overpass data the
    trails are left as they are.
    """
    # Ways of hiking routes and named paths are preferred for the approach.
    route_names = dict(route_way_names or {})
    for w in data.get("elements", []):
        if w["type"] == "way":
            route_names.setdefault(w["id"], w.get("tags", {}).get("name"))
    access = enrich.RoadAccess(data["network"], route_names) if data.get("network") else None
    area_of = enrich.area_finder(data["places"]) if data.get("places") else None
    parking = enrich.Grid()
    for poi in data.get("pois", []):
        if poi["kind"] == "parking":
            parking.add((poi["lat"], poi["lon"]), None)

    trail_points: list = []
    end_points: list = []
    stats: Counter = Counter()
    for t in trails:
        chains = t.pop("_chains", None) or []
        if not chains:
            continue
        trail_points.extend(enrich.densify([simplify(c, 3.0) for c in chains]))
        if area_of:
            area = area_of(max(chains, key=length_m))
            if area:
                t["area"] = area
        if not access:
            continue
        ends = []
        for point in enrich.trail_ends(chains):
            found = access.find(point, {t["name"], *t.get("aliases", [])})
            end = {"lat": round(point[0], 6), "lon": round(point[1], 6), **access_entry(found, parking)}
            end_points.append(point)
            if found["approachM"] is not None:
                end_points.append(found["access"])
            ends.append(end)
        ends.sort(key=lambda e: (e["approachM"] is None, e["approachM"] or 0))
        t["ends"] = ends
        start = ends[0]["approachM"] if ends else None
        stats["on a road" if start == 0 else "walk to a road" if start is not None else "no road within 5 km"] += 1

    pois = enrich.select_pois(data.get("pois", []), trail_points, end_points)
    return {"pois": pois, "access": stats, "hasExtract": access is not None, "roads": access, "parking": parking}


def access_entry(found: dict, parking: "enrich.Grid") -> dict:
    """The catalog form of a RoadAccess result (shared by trail ends and places)."""
    entry: dict = {"approachM": found["approachM"]}
    if found["approachM"] is None:
        return entry
    road_point = found["access"]
    entry["access"] = [round(road_point[0], 6), round(road_point[1], 6)]
    if found.get("road"):
        entry["road"] = found["road"]
    if found.get("via"):
        entry["via"] = found["via"]
    if len(found.get("line", [])) >= 2 and found["approachM"] > 0:
        entry["line"] = encode_polyline(simplify(found["line"], 3.0))
    nearby = parking.nearest(road_point, 300)
    if nearby:
        entry["parking"] = [round(nearby[1][0], 6), round(nearby[1][1], 6)]
    return entry


PLACE_ACCESS_KINDS = {"peak", "waterfall", "viewpoint", "cave", "spring"}
PLACE_SNAP_M = 150


def add_place_access(places: list[dict], roads, parking) -> None:
    """How to reach a peak or other walk-to place from the road (Pico Alto, waterfalls, …)."""
    if roads is None:
        return
    for place in places:
        if place["kind"] not in PLACE_ACCESS_KINDS:
            continue
        found = roads.find((place["lat"], place["lon"]), {place.get("name", "")}, snap_m=PLACE_SNAP_M)
        if found["approachM"] is not None:
            place["access"] = access_entry(found, parking)


def report(catalog: dict, missing: list[str], renamed: list | None = None, extra: dict | None = None) -> str:
    trails = catalog["trails"]
    official = [t for t in trails if t["source"] == "official"]
    community = [t for t in trails if t["source"] == "community"]
    peaks = [p for p in catalog["places"] if p["kind"] == "peak"]
    pois = [p for p in catalog["places"] if p["kind"] != "peak"]
    extra = extra or {}
    # Only community trails count: an official trail can share a name with a different walk.
    names = " | ".join(normalize(t["name"]) for t in community)
    # OSM names many peaks without "Pico" ("Grande"), so check "pico <name>" and other names too.
    place_names = " | ".join(
        normalize(name)
        for p in catalog["places"]
        if p.get("name")
        for name in [p["name"], "Pico " + p["name"], *p.get("aliases", [])]
    )

    lines = [
        "# Catalog report",
        "",
        f"Generated {catalog['generatedAt']} from OpenStreetMap.",
        "",
        f"- Official trails with a line: **{len(official) - len(missing)} of {len(official)}**",
        f"- Missing official lines: {', '.join(missing) if missing else 'none'}",
        f"- Community trails: **{len(community)}**",
        f"- Named peaks: **{len(peaks)}**",
        f"- Other points of interest: **{len(pois)}** ("
        + ", ".join(f"{kind} {count}" for kind, count in sorted(Counter(p["kind"] for p in pois).items()))
        + ")",
    ]
    if extra.get("hasExtract"):
        access = extra.get("access", Counter())
        lines.append(
            "- Trail starts: "
            + ", ".join(f"{label} {access.get(label, 0)}" for label in ("on a road", "walk to a road", "no road within 5 km"))
        )
    else:
        lines.append("- Built from Overpass: no areas, road access or points of interest this time.")

    lines += [
        "",
        "## Popular unofficial trails (data/wanted.json)",
        "",
        "| Trail | In OpenStreetMap as a trail | Peak/place found |",
        "| --- | --- | --- |",
    ]
    wanted = json.loads(WANTED.read_text(encoding="utf-8"))["trails"]
    for w in wanted:
        terms = [normalize(m) for m in w["match"]]
        as_trail = any(term in names for term in terms)
        as_place = any(term in place_names for term in terms)
        lines.append(f"| {w['name']} | {'yes' if as_trail else 'no'} | {'yes' if as_place else 'no'} |")

    official_far = [
        f"{t['code']} ({t['ends'][0]['approachM']} m)" if t["ends"][0]["approachM"] is not None else f"{t['code']} (none)"
        for t in official
        if t.get("ends") and (t["ends"][0]["approachM"] or 0) > 0 or (t.get("ends") and t["ends"][0]["approachM"] is None)
    ]
    if extra.get("hasExtract"):
        lines += [
            "",
            "## Official trails whose nearest end is off the road",
            "",
            ", ".join(official_far) if official_far else "None.",
        ]

    odd = sorted({t["name"] for t in community if enrich.looks_odd(t["name"])})
    lines += ["", "## Community names to check", ""]
    lines += [f"- {name}" for name in odd] or ["None."]
    lines += ["", "Fix them in OpenStreetMap, or by trail id in `data/name-fixes.json`."]

    if renamed:
        lines += ["", "## Peaks given their full name", ""]
        lines.append(", ".join(f"{a} → {b}" for a, b in sorted(renamed)))
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", help="Use a saved Overpass JSON response instead of downloading")
    parser.add_argument("--pbf", help="Read an OpenStreetMap extract (.osm.pbf) instead of asking Overpass")
    args = parser.parse_args()
    if args.pbf:
        data = read_extract(args.pbf)
    elif args.input:
        data = json.loads(pathlib.Path(args.input).read_text())
    else:
        data = fetch_overpass()

    catalog, report_md = build(data)
    OUT_CATALOG.parent.mkdir(parents=True, exist_ok=True)
    OUT_CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    OUT_REPORT.write_text(report_md, encoding="utf-8")
    print(report_md)
    print(f"catalog.json: {OUT_CATALOG.stat().st_size / 1024:.0f} KiB")


if __name__ == "__main__":
    main()
