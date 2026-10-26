"""Reads the official trail status from Visit Madeira and writes status.json for the app.

Source: the hiking page lists every classified trail with Open / Restricted / Closed.
Safety: if the page can't be read or parsed, the previous status is kept with "stale": true and its
old check time, so the app falls back to "unknown" instead of guessing.

Usage: python data/scripts/build_status.py --out status.json [--previous old.json] [--html page.html]
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

LISTING_URL = "https://www.visitmadeira.com/en/what-to-do/nature-seekers/activities/hiking/"
USER_AGENT = "VeredaTrailStatus/1.0 (+https://github.com/Psy8891/Madeira-Trails-)"
MIN_TRAILS = 30  # fewer than this means the page layout changed: treat as a failure

CODE = re.compile(r"\b(PS\s*PR|PR)\s*(\d+(?:\.\d+)?)\b", re.IGNORECASE)
STATUS = [
    (re.compile(r"\bpartially\s+open\b|\bparcialmente\s+aberto\b", re.I), "partial"),
    (re.compile(r"\bpartially\s+closed\b|\bparcialmente\s+(encerrado|fechado)\b", re.I), "partial"),
    (re.compile(r"\brestricted\b|\bcondicionado\b|\brestrito\b", re.I), "partial"),
    (re.compile(r"\bclosed\b|\bencerrado\b|\bfechado\b", re.I), "closed"),
    (re.compile(r"\bopen\b|\baberto\b", re.I), "open"),
]


def now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def trail_id(code: str) -> str:
    return code.lower().replace(" ", "-")


def find_code(text: str) -> str | None:
    match = CODE.search(text)
    if not match:
        return None
    prefix = "PS PR" if match.group(1).upper().startswith("PS") else "PR"
    return f"{prefix} {match.group(2)}"


def find_status(text: str) -> tuple[str, str] | None:
    """Returns (state, label as written) for the earliest status word in the text."""
    best = None
    for pattern, state in STATUS:
        match = pattern.search(text)
        if match and (best is None or match.start() < best[2]):
            best = (state, match.group(0), match.start())
    return (best[0], best[1]) if best else None


def parse_listing(html: str, base_url: str = LISTING_URL) -> dict[str, dict]:
    """Maps trail id -> {code, state, label, sourceUrl} from the hiking listing page."""
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()
    found: dict[str, dict] = {}
    for anchor in soup.find_all("a", href=True):
        href = urljoin(base_url, anchor["href"])
        slug = href.rstrip("/").rsplit("/", 1)[-1]
        if "/hiking/" not in href or not re.match(r"^(ps-)?pr-\d", slug):
            continue
        node = anchor
        for _ in range(6):
            text = " ".join(node.get_text(" ").split())
            codes = {find_code(m.group(0)) for m in CODE.finditer(text)}
            if len(codes) > 1:
                node = None  # reached a container holding several trails: give up on this link
                break
            status = find_status(text)
            code = find_code(text)
            if code and status:
                break
            node = node.parent
            if node is None:
                break
        else:
            node = None
        if node is None or not code or not status:
            continue
        tid = trail_id(code)
        if tid not in found:
            found[tid] = {"code": code, "state": status[0], "label": status[1], "sourceUrl": href}
    return found


def build(previous: dict | None, html: str | None, error: str | None = None) -> tuple[dict, bool]:
    """Returns (status document, ok)."""
    checked = now_iso()
    if html is not None and error is None:
        try:
            parsed = parse_listing(html)
            if len(parsed) >= MIN_TRAILS:
                trails = {
                    tid: {**info, "checkedAt": checked, "stale": False} for tid, info in sorted(parsed.items())
                }
                return {"schema": 1, "generatedAt": checked, "ok": True, "source": LISTING_URL, "trails": trails}, True
            error = f"Only {len(parsed)} trails parsed (expected at least {MIN_TRAILS}); page layout may have changed"
        except Exception as exc:  # noqa: BLE001
            error = f"Parse error: {exc}"

    old_trails = (previous or {}).get("trails", {})
    trails = {tid: {**info, "stale": True} for tid, info in old_trails.items()}
    return {
        "schema": 1,
        "generatedAt": checked,
        "ok": False,
        "error": error,
        "source": LISTING_URL,
        "trails": trails,
    }, False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--previous")
    parser.add_argument("--html", help="Parse a saved page instead of downloading")
    parser.add_argument("--debug-out", help="Write the parsed rows here for inspection")
    args = parser.parse_args()

    previous = None
    if args.previous and pathlib.Path(args.previous).exists():
        try:
            previous = json.loads(pathlib.Path(args.previous).read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            previous = None

    html, error = None, None
    if args.html:
        html = pathlib.Path(args.html).read_text(encoding="utf-8")
    else:
        try:
            response = requests.get(LISTING_URL, headers={"User-Agent": USER_AGENT}, timeout=60)
            response.raise_for_status()
            html = response.text
        except Exception as exc:  # noqa: BLE001
            error = f"Download failed: {exc}"

    document, ok = build(previous, html, error)
    pathlib.Path(args.out).write_text(json.dumps(document, ensure_ascii=False, indent=1), encoding="utf-8")
    if args.debug_out:
        rows = [f"{t['code']:<8} {t['state']:<8} {t['label']:<12} {t.get('sourceUrl', '')}" for t in document["trails"].values()]
        pathlib.Path(args.debug_out).write_text("\n".join(rows) + "\n", encoding="utf-8")

    counts: dict[str, int] = {}
    for t in document["trails"].values():
        counts[t["state"]] = counts.get(t["state"], 0) + 1
    print(f"ok={ok} trails={len(document['trails'])} {counts} error={document.get('error')}")
    if not ok:
        print(f"::warning title=Trail status::{document.get('error')}")


if __name__ == "__main__":
    main()
