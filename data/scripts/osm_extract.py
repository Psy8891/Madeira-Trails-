"""Reads an OpenStreetMap file (.osm.pbf or .osm) for the catalog build.

`read()` returns the same shape the Overpass queries in build_catalog.py return
({"elements": [...]}: hiking route relations with member geometry, named paths and named peaks),
plus what only a full extract can give (spec: Phase 0.6):

- "pois": points of interest (viewpoints, waterfalls, water, shelters, parking, …) as
  {"kind", "name"?, "lat", "lon", "ele"?, "tags"}; areas such as car parks become their centre.
- "places": villages, towns and named localities, for a trail's area.
- "network": every highway way (node ids, kind, name, ref, access) and the node coordinates,
  so the job can find the walk from a trail's end to the nearest road.
- "names": how often each name occurs, used to complete peak names ("Grande" → "Pico Grande").

Clip the extract to Madeira first, e.g.:
    osmium extract -b -17.30,32.55,-16.25,33.15 portugal-latest.osm.pbf -o madeira.osm.pbf
"""

from __future__ import annotations

from collections import Counter

import osmium

ROUTE_VALUES = {"hiking", "foot"}
PATH_VALUES = {"path", "footway", "steps", "track"}
# Highway values that are not ways you can walk or drive along.
NOT_NETWORK = {"construction", "proposed", "platform", "bus_stop", "elevator", "raceway", "corridor", "stop", "emergency_bay", "abandoned", "razed"}
PLACE_VALUES = {"city", "town", "village", "suburb", "hamlet", "locality", "neighbourhood", "isolated_dwelling"}
TUNNEL_VALUES = {"yes", "building_passage"}


def poi_kind(tags) -> str | None:
    """The catalog kind for an OSM object, or None. `tags` is anything with .get()."""
    natural = tags.get("natural")
    tourism = tags.get("tourism")
    amenity = tags.get("amenity")
    if natural == "peak":
        return "peak"
    if tourism == "viewpoint":
        return "viewpoint"
    if tags.get("waterway") == "waterfall" or natural == "waterfall":
        return "waterfall"
    if natural == "spring":
        return "spring"
    if amenity == "drinking_water" or tags.get("man_made") == "water_tap":
        return "water"
    if tourism in ("alpine_hut", "wilderness_hut"):
        return "hut"
    if amenity == "shelter" and tags.get("shelter_type") != "public_transport":
        return "shelter"
    if tourism == "picnic_site":
        return "picnic"
    if tourism == "camp_site":
        return "camp"
    if natural == "cave_entrance":
        return "cave"
    if tags.get("highway") == "trailhead":
        return "trailhead"
    if amenity == "toilets":
        return "toilets"
    if amenity == "parking" and tags.get("access") not in ("private", "no"):
        return "parking"
    if tags.get("highway") == "bus_stop" or (tags.get("public_transport") == "platform" and tags.get("bus") == "yes"):
        return "bus_stop"
    return None


def _ele(tags) -> int | None:
    value = tags.get("ele")
    if not value:
        return None
    try:
        return round(float(str(value).replace(",", ".").split()[0]))
    except ValueError:
        return None


class _RouteRelations(osmium.SimpleHandler):
    def __init__(self) -> None:
        super().__init__()
        self.relations: list[dict] = []
        self.names: Counter = Counter()

    def relation(self, r) -> None:
        if "name" in r.tags:
            self.names[r.tags["name"]] += 1
        if r.tags.get("route") not in ROUTE_VALUES:
            return
        self.relations.append(
            {
                "type": "relation",
                "id": r.id,
                "tags": {t.k: t.v for t in r.tags},
                "members": [{"type": _member_type(m.type), "ref": m.ref, "role": m.role} for m in r.members],
            }
        )


class _WaysAndNodes(osmium.SimpleHandler):
    def __init__(self, member_way_ids: set[int], bbox: tuple[float, float, float, float]) -> None:
        super().__init__()
        self.member_way_ids = member_way_ids
        self.bbox = bbox
        self.member_geometry: dict[int, list[dict]] = {}
        self.paths: list[dict] = []
        self.peaks: list[dict] = []
        self.pois: list[dict] = []
        self.places: list[dict] = []
        self.network_ways: list[dict] = []
        self.coords: dict[int, tuple[float, float]] = {}
        self.names: Counter = Counter()

    def _inside(self, lat: float, lon: float) -> bool:
        south, west, north, east = self.bbox
        return south <= lat <= north and west <= lon <= east

    def node(self, n) -> None:
        if not n.tags or not n.location.valid():
            return
        lat, lon = n.location.lat, n.location.lon
        if not self._inside(lat, lon):
            return
        tags = n.tags
        if "name" in tags:
            self.names[tags["name"]] += 1
        if tags.get("natural") == "peak" and "name" in tags:
            self.peaks.append({"type": "node", "id": n.id, "lat": lat, "lon": lon, "tags": {t.k: t.v for t in tags}})
        kind = poi_kind(tags)
        if kind and kind != "peak":
            self.pois.append(_poi(kind, f"n{n.id}", lat, lon, tags))
        if tags.get("place") in PLACE_VALUES and "name" in tags:
            self.places.append({"name": tags["name"], "kind": tags["place"], "lat": lat, "lon": lon})

    def way(self, w) -> None:
        tags = w.tags
        if "name" in tags:
            self.names[tags["name"]] += 1
        highway = tags.get("highway")
        is_member = w.id in self.member_way_ids
        is_named_path = highway in PATH_VALUES and "name" in tags
        kind = poi_kind(tags)
        is_network = highway is not None and highway not in NOT_NETWORK and tags.get("area") != "yes"
        is_tunnel = tags.get("tunnel") in TUNNEL_VALUES and (highway in PATH_VALUES or tags.get("waterway") == "canal")
        if not (is_member or is_named_path or kind or is_network or is_tunnel):
            return
        nodes = [(nd.ref, nd.location.lat, nd.location.lon) for nd in w.nodes if nd.location.valid()]
        if len(nodes) < 2:
            return
        geometry = [{"lat": lat, "lon": lon} for _, lat, lon in nodes]
        if is_member:
            self.member_geometry[w.id] = geometry
        if not any(self._inside(lat, lon) for _, lat, lon in nodes):
            return
        if is_named_path:
            self.paths.append({"type": "way", "id": w.id, "tags": {t.k: t.v for t in tags}, "geometry": geometry})
        if kind and kind != "peak":
            lat = sum(p[1] for p in nodes) / len(nodes)
            lon = sum(p[2] for p in nodes) / len(nodes)
            self.pois.append(_poi(kind, f"w{w.id}", lat, lon, tags))
        if is_tunnel:
            name = tags.get("name")
            for _, lat, lon in (nodes[0], nodes[-1]):
                self.pois.append({"kind": "tunnel", "id": f"w{w.id}", "lat": lat, "lon": lon, **({"name": name} if name else {}),
                                  "tags": {"highway": highway or "", "waterway": tags.get("waterway", "")}})
        if is_network:
            self.network_ways.append(
                {
                    "id": w.id,
                    "nodes": [ref for ref, _, _ in nodes],
                    "highway": highway,
                    "name": tags.get("name"),
                    "ref": tags.get("ref"),
                    "access": tags.get("access") or tags.get("motor_vehicle"),
                    "tracktype": tags.get("tracktype"),
                    "sac": tags.get("sac_scale"),
                }
            )
            for ref, lat, lon in nodes:
                self.coords[ref] = (lat, lon)


def _poi(kind: str, osm_id: str, lat: float, lon: float, tags) -> dict:
    poi = {"kind": kind, "id": osm_id, "lat": lat, "lon": lon}
    if tags.get("name"):
        poi["name"] = tags.get("name")
    ele = _ele(tags)
    if ele is not None:
        poi["ele"] = ele
    return poi


def _member_type(kind: str) -> str:
    return {"n": "node", "w": "way", "r": "relation"}.get(kind, kind)


def read(path: str, bbox: tuple[float, float, float, float]) -> dict:
    """bbox is (south, west, north, east)."""
    relations = _RouteRelations()
    relations.apply_file(path)
    member_ids = {m["ref"] for r in relations.relations for m in r["members"] if m["type"] == "way"}

    ways = _WaysAndNodes(member_ids, bbox)
    ways.apply_file(path, locations=True)

    elements: list[dict] = []
    for rel in relations.relations:
        members = []
        for m in rel["members"]:
            if m["type"] == "way" and m["ref"] in ways.member_geometry:
                members.append({**m, "geometry": ways.member_geometry[m["ref"]]})
            else:
                members.append(m)
        # Overpass `relation(bbox)` keeps relations with a member inside the box.
        if any(
            ways._inside(g["lat"], g["lon"])  # noqa: SLF001
            for m in members
            for g in m.get("geometry", [])
        ):
            elements.append({**rel, "members": members})
    elements.extend(ways.paths)
    elements.extend(ways.peaks)
    names = ways.names + relations.names
    return {
        "elements": elements,
        "pois": ways.pois,
        "places": ways.places,
        "network": {"ways": ways.network_ways, "coords": ways.coords},
        "names": dict(names),
    }
