import hashlib
import pathlib
import sys

import pytest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

from build_manifest import build  # noqa: E402


def files(tmp_path):
    out = {}
    for role in ("base", "contours", "dem"):
        p = tmp_path / f"madeira-{role}.pmtiles"
        p.write_bytes(role.encode() * 10)
        out[role] = p
    return out


def test_manifest_lists_sizes_hashes_and_urls(tmp_path):
    m = build("2026.10.27", "https://example.org/releases/download/maps-2026.10.27/", files(tmp_path))
    assert m["schema"] == 1
    p = m["packages"][0]
    assert p["id"] == "madeira" and p["version"] == "2026.10.27"
    base = p["files"][0]
    assert base["role"] == "base"
    assert base["url"] == "https://example.org/releases/download/maps-2026.10.27/madeira-base.pmtiles"
    assert base["bytes"] == 40
    assert base["sha256"] == hashlib.sha256(b"base" * 10).hexdigest()


def test_bad_versions_urls_and_missing_files_are_refused(tmp_path):
    f = files(tmp_path)
    with pytest.raises(ValueError):
        build("2026.10", "https://x", f)
    with pytest.raises(ValueError):
        build("2026.10.27", "http://x", f)
    with pytest.raises(ValueError):
        build("2026.10.27", "https://x", {"base": f["base"]})
