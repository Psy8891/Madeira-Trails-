"""Small geometry and text helpers shared by the data jobs. Pure functions, unit-tested."""

from __future__ import annotations

import math
import re
import unicodedata

EARTH_RADIUS_M = 6_371_008.8

Point = tuple[float, float]  # (lat, lon)


def normalize(text: str) -> str:
    """Lower-case, strip accents, collapse punctuation to single spaces (same rules as the app)."""
    decomposed = unicodedata.normalize("NFD", text)
    stripped = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return re.sub(r"[^a-z0-9.]+", " ", stripped.lower()).strip()


_CODE = re.compile(r"^(PS\s*)?PR\s*(\d+(?:\.\d+)?)\b", re.IGNORECASE)


def parse_pr_code(text: str | None, porto_santo: bool = False) -> str | None:
    """'PR14', 'PR 6.1 - Levada do Risco', 'PS PR1' -> 'PR 14', 'PR 6.1', 'PS PR 1'."""
    if not text:
        return None
    match = _CODE.match(text.strip())
    if not match:
        return None
    number = match.group(2)
    if match.group(1) or porto_santo:
        return f"PS PR {number}"
    return f"PR {number}"


def haversine_m(a: Point, b: Point) -> float:
    phi1, phi2 = math.radians(a[0]), math.radians(b[0])
    d_phi = phi2 - phi1
    d_lambda = math.radians(b[1] - a[1])
    h = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(min(1.0, max(0.0, h))))


def length_m(line: list[Point]) -> float:
    return sum(haversine_m(a, b) for a, b in zip(line, line[1:]))


def _same(a: Point, b: Point) -> bool:
    return abs(a[0] - b[0]) < 1e-7 and abs(a[1] - b[1]) < 1e-7


def chain_lines(lines: list[list[Point]]) -> list[list[Point]]:
    """Join lines that share end points into as few lines as possible."""
    pending = [list(line) for line in lines if len(line) >= 2]
    chains: list[list[Point]] = []
    while pending:
        chain = pending.pop(0)
        grown = True
        while grown:
            grown = False
            for i, line in enumerate(pending):
                if _same(chain[-1], line[0]):
                    chain = chain + line[1:]
                elif _same(chain[-1], line[-1]):
                    chain = chain + line[-2::-1]
                elif _same(chain[0], line[-1]):
                    chain = line[:-1] + chain
                elif _same(chain[0], line[0]):
                    chain = line[:0:-1] + chain
                else:
                    continue
                pending.pop(i)
                grown = True
                break
        chains.append(chain)
    return chains


def simplify(line: list[Point], tolerance_m: float = 3.0) -> list[Point]:
    """Douglas-Peucker on a local flat projection."""
    if len(line) < 3:
        return list(line)
    lat0 = math.radians(line[0][0])
    kx = EARTH_RADIUS_M * math.pi / 180 * math.cos(lat0)
    ky = EARTH_RADIUS_M * math.pi / 180
    xy = [(p[1] * kx, p[0] * ky) for p in line]

    keep = [False] * len(line)
    keep[0] = keep[-1] = True
    stack = [(0, len(line) - 1)]
    while stack:
        first, last = stack.pop()
        ax, ay = xy[first]
        bx, by = xy[last]
        dx, dy = bx - ax, by - ay
        seg2 = dx * dx + dy * dy
        worst, worst_i = -1.0, -1
        for i in range(first + 1, last):
            px, py = xy[i]
            if seg2 == 0:
                dist = math.hypot(px - ax, py - ay)
            else:
                t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / seg2))
                dist = math.hypot(px - (ax + t * dx), py - (ay + t * dy))
            if dist > worst:
                worst, worst_i = dist, i
        if worst > tolerance_m:
            keep[worst_i] = True
            stack.append((first, worst_i))
            stack.append((worst_i, last))
    return [p for p, k in zip(line, keep) if k]


def encode_polyline(line: list[Point], precision: int = 5) -> str:
    """Google encoded polyline (lat, lon order)."""
    factor = 10**precision
    result = []
    prev_lat = prev_lon = 0
    for lat, lon in line:
        ilat, ilon = round(lat * factor), round(lon * factor)
        for delta in (ilat - prev_lat, ilon - prev_lon):
            value = ~(delta << 1) if delta < 0 else delta << 1
            while value >= 0x20:
                result.append(chr((0x20 | (value & 0x1F)) + 63))
                value >>= 5
            result.append(chr(value + 63))
        prev_lat, prev_lon = ilat, ilon
    return "".join(result)


def decode_polyline(encoded: str, precision: int = 5) -> list[Point]:
    factor = 10**precision
    points, index, lat, lon = [], 0, 0, 0
    while index < len(encoded):
        deltas = []
        for _ in range(2):
            shift = result = 0
            while True:
                b = ord(encoded[index]) - 63
                index += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            deltas.append(~(result >> 1) if result & 1 else result >> 1)
        lat += deltas[0]
        lon += deltas[1]
        points.append((lat / factor, lon / factor))
    return points
