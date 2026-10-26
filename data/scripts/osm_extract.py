"""Reads an OpenStreetMap file (.osm.pbf or .osm) into the same shape the Overpass queries in
build_catalog.py return, so the catalog can be built from a Geofabrik extract when the public
Overpass servers are busy (they ask automated jobs to avoid them).

Clip the extract to Madeira first, e.g.:
    osmium extract -b -17.30,32.55,-16.25,33.15 portugal-latest.osm.pbf -o madeira.osm.pbf
"""

from __future__ import annotations

import osmium

ROUTE_VALUES = {"hiking", "foot"}
PATH_VALUES = {"path", "footway", "steps", "track"}


class _RouteRelations(osmium.SimpleHandler):
    def __init__(self) -> None:
        super().__init__()
        self.relations: list[dict] = []

    def relation(self, r) -> None:
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


class _WaysAndPeaks(osmium.SimpleHandler):
    def __init__(self, member_way_ids: set[int], bbox: tuple[float, float, float, float]) -> None:
        super().__init__()
        self.member_way_ids = member_way_ids
        self.bbox = bbox
        self.member_geometry: dict[int, list[dict]] = {}
        self.paths: list[dict] = []
        self.peaks: list[dict] = []

    def _inside(self, lat: float, lon: float) -> bool:
        south, west, north, east = self.bbox
        return south <= lat <= north and west <= lon <= east

    def node(self, n) -> None:
        if n.tags.get("natural") != "peak" or "name" not in n.tags or not n.location.valid():
            return
        if self._inside(n.location.lat, n.location.lon):
            self.peaks.append(
                {"type": "node", "id": n.id, "lat": n.location.lat, "lon": n.location.lon, "tags": {t.k: t.v for t in n.tags}}
            )

    def way(self, w) -> None:
        is_member = w.id in self.member_way_ids
        is_named_path = w.tags.get("highway") in PATH_VALUES and "name" in w.tags
        if not (is_member or is_named_path):
            return
        geometry = [{"lat": n.location.lat, "lon": n.location.lon} for n in w.nodes if n.location.valid()]
        if len(geometry) < 2:
            return
        if is_member:
            self.member_geometry[w.id] = geometry
        if is_named_path and any(self._inside(g["lat"], g["lon"]) for g in geometry):
            self.paths.append({"type": "way", "id": w.id, "tags": {t.k: t.v for t in w.tags}, "geometry": geometry})


def _member_type(kind: str) -> str:
    return {"n": "node", "w": "way", "r": "relation"}.get(kind, kind)


def read(path: str, bbox: tuple[float, float, float, float]) -> dict:
    """bbox is (south, west, north, east). Returns {"elements": [...]} like Overpass `out geom`."""
    relations = _RouteRelations()
    relations.apply_file(path)
    member_ids = {m["ref"] for r in relations.relations for m in r["members"] if m["type"] == "way"}

    ways = _WaysAndPeaks(member_ids, bbox)
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
    return {"elements": elements}
