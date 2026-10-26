import pathlib
import sys

import pytest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

from geo_utils import (  # noqa: E402
    chain_lines,
    decode_polyline,
    encode_polyline,
    haversine_m,
    length_m,
    normalize,
    parse_pr_code,
    simplify,
)


def test_normalize_matches_app_rules():
    assert normalize("  Caldeirão   Verde! ") == "caldeirao verde"
    assert normalize("Baía d'Abra") == "baia d abra"
    assert normalize("PR 6.1") == "pr 6.1"


@pytest.mark.parametrize(
    "text, porto_santo, expected",
    [
        ("PR14", False, "PR 14"),
        ("PR 6.1", False, "PR 6.1"),
        ("PR1.2", False, "PR 1.2"),
        ("PR14 - Levada dos Cedros", False, "PR 14"),
        ("PS PR1", False, "PS PR 1"),
        ("PR1", True, "PS PR 1"),
        ("pr 13.1", False, "PR 13.1"),
        ("Levada do Rei", False, None),
        (None, False, None),
        ("GR1", False, None),
    ],
)
def test_parse_pr_code(text, porto_santo, expected):
    assert parse_pr_code(text, porto_santo) == expected


def test_haversine_reference_value():
    assert haversine_m((32.70, -17.00), (32.70, -16.98)) == pytest.approx(1871.437, abs=0.01)


def test_chain_lines_joins_reversed_and_unordered_pieces():
    a = [(0.0, 0.0), (0.0, 0.001)]
    b = [(0.0, 0.002), (0.0, 0.001)]  # reversed
    c = [(0.0, 0.003), (0.0, 0.002)]  # reversed, comes before b in walking order
    far = [(1.0, 1.0), (1.0, 1.001)]
    chains = chain_lines([a, c, far, b])
    assert len(chains) == 2
    longest = max(chains, key=len)
    assert longest == [(0.0, 0.0), (0.0, 0.001), (0.0, 0.002), (0.0, 0.003)]


def test_simplify_drops_points_on_a_straight_line_but_keeps_corners():
    straight = [(32.7, -17.0 + i * 0.0001) for i in range(11)]
    assert simplify(straight, 1.0) == [straight[0], straight[-1]]
    corner = [(32.7, -17.0), (32.7, -16.99), (32.71, -16.99)]
    assert simplify(corner, 1.0) == corner


def test_polyline_round_trip_and_known_value():
    line = [(38.5, -120.2), (40.7, -120.95), (43.252, -126.453)]
    encoded = encode_polyline(line)
    assert encoded == "_p~iF~ps|U_ulLnnqC_mqNvxq`@"  # Google's documented example
    assert decode_polyline(encoded) == line


def test_length_m_sums_legs():
    line = [(32.70, -17.00), (32.70, -16.99), (32.70, -16.98)]
    assert length_m(line) == pytest.approx(1871.437, abs=0.01)
