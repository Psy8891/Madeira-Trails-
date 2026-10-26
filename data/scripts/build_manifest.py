"""Writes manifest.json for the offline map package (spec: *Package manifest*).

    python build_manifest.py --version 2026.10.27 --base-url https://github.com/OWNER/REPO/releases/download/maps-2026.10.27 \
        --out manifest.json base=madeira-base.pmtiles contours=madeira-contours.pmtiles dem=madeira-dem.pmtiles
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

SCHEMA = 1
ROLES = ("base", "contours", "dem")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def build(version: str, base_url: str, files: dict[str, Path], min_app: int = 1) -> dict:
    if not re.fullmatch(r"\d{4}\.\d{2}\.\d{2}", version):
        raise ValueError("version must be YYYY.MM.DD so it sorts")
    if not base_url.startswith("https://"):
        raise ValueError("base URL must be https")
    missing = [r for r in ROLES if r not in files]
    if missing:
        raise ValueError(f"missing roles: {missing}")
    return {
        "schema": SCHEMA,
        "packages": [{
            "id": "madeira",
            "name": "Madeira and Porto Santo",
            "version": version,
            "minApp": min_app,
            "files": [
                {
                    "role": role,
                    "url": f"{base_url.rstrip('/')}/{files[role].name}",
                    "bytes": files[role].stat().st_size,
                    "sha256": sha256(files[role]),
                }
                for role in ROLES
            ],
        }],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--version", required=True)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("files", nargs="+", help="role=path")
    args = parser.parse_args()
    files = {}
    for item in args.files:
        role, _, path = item.partition("=")
        files[role] = Path(path)
    manifest = build(args.version, args.base_url, files)
    args.out.write_text(json.dumps(manifest, indent=2) + "\n")
    for f in manifest["packages"][0]["files"]:
        print(f"{f['role']}: {f['bytes'] / 1e6:.1f} MB {f['sha256'][:12]}")


if __name__ == "__main__":
    main()
