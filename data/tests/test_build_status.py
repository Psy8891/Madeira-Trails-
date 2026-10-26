import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

import build_status  # noqa: E402
from build_status import build, find_code, find_status, parse_listing  # noqa: E402

BASE = "https://www.visitmadeira.com/en/what-to-do/nature-seekers/activities/hiking/"


def card_inside_link(code, name, status, slug):
    return f'<a href="{BASE}{slug}/"><div class="card"><span>{code}</span><h3>{name}</h3><span class="tag">{status}</span></div></a>'


def card_status_outside_link(code, name, status, slug):
    return (
        f'<div class="card"><div class="badge">{status}</div>'
        f'<a href="/en/what-to-do/nature-seekers/activities/hiking/{slug}/"><img alt=""></a>'
        f'<h3><a href="/en/what-to-do/nature-seekers/activities/hiking/{slug}/">{code} {name}</a></h3></div>'
    )


def page(cards):
    return (
        "<html><body><nav><a href='/en/'>Open menu</a></nav>"
        "<script>var status = 'Closed';</script>"
        f"<section class='list'>{''.join(cards)}</section></body></html>"
    )


def test_find_code_normalises_spacing():
    assert find_code("PR1 Vereda do Areeiro") == "PR 1"
    assert find_code("PS PR2 Vereda do Pico do Castelo") == "PS PR 2"
    assert find_code("PR 13.1 Vereda da Palha Carga") == "PR 13.1"
    assert find_code("Levada Nova") is None


def test_find_status_takes_the_first_status_word():
    assert find_status("PR 4 Levada do Barreiro Restricted")[0] == "partial"
    assert find_status("PR 7 Levada do Moinho Closed")[0] == "closed"
    assert find_status("Open PR 6")[0] == "open"
    assert find_status("Opening hours") is None
    assert find_status("Parcialmente aberto")[0] == "partial"


def test_parse_listing_with_status_inside_the_link():
    html = page(
        [
            card_inside_link("PR1", "Vereda do Areeiro", "Open", "pr-1-vereda-do-areeiro"),
            card_inside_link("PR 1.1", "Vereda da Ilha", "Open", "pr-11-vereda-da-ilha"),
            card_inside_link("PR 11", "Vereda dos Balcões", "Restricted", "pr-11-vereda-dos-balcoes"),
            card_inside_link("PS PR 3", "Levada do Pico do Castelo", "Closed", "ps-pr-3-levada-do-pico-do-castelo"),
        ]
    )
    result = parse_listing(html, BASE)
    assert result["pr-1"]["state"] == "open"
    assert result["pr-1.1"]["state"] == "open"
    assert result["pr-11"]["state"] == "partial"
    assert result["pr-11"]["label"] == "Restricted"
    assert result["ps-pr-3"]["state"] == "closed"
    assert result["pr-1"]["sourceUrl"] == BASE + "pr-1-vereda-do-areeiro/"


def test_parse_listing_with_status_next_to_the_link_and_duplicate_links():
    html = page(
        [
            card_status_outside_link("PR 6", "Levada das 25 Fontes", "Open", "pr-6-levada-das-25-fontes"),
            card_status_outside_link("PR 7", "Levada do Moinho", "Closed", "pr-7-levada-do-moinho"),
        ]
    )
    result = parse_listing(html, BASE)
    assert set(result) == {"pr-6", "pr-7"}
    assert result["pr-6"]["state"] == "open"
    assert result["pr-7"]["state"] == "closed"
    assert result["pr-7"]["sourceUrl"].startswith("https://www.visitmadeira.com/")


def real_card(title, status, slug):
    """Markup copied from the Visit Madeira hiking page (checked 26 Oct 2026), text and links only."""
    return (
        '<div class="card" data-layout="background"><div class="context">'
        '<button class="toggle-wishlist" aria-label="Add to Wishlist"></button></div>'
        f'<a class="textual textual-link title" href="/en/what-to-do/nature-seekers/activities/hiking/{slug}/">'
        f'<span class="title state-wrapper"><span class="state-circle"></span>\n   {status}\n  </span>'
        '<div class="textual-link-footer"><div class="text title"><div class="tag"></div>'
        f'<div class="title">{title}</div></div><div class="arrow"></div></div></a>'
        '<div class="overlay overlay-fill"></div></div>'
    )


def test_parse_listing_with_the_real_page_markup():
    cards = [
        real_card("PR1 - Vereda do Areeiro", "Open", "pr-1-vereda-do-areeiro"),
        real_card("PR 1.3 - Vereda da Encumeada", "Restricted", "pr-13-vereda-da-encumeada"),
        real_card("PR 13 - Vereda do Fanal", "Open", "pr-13-vereda-do-fanal"),
        real_card("PR 6.8 - Levada do Paul II - Um caminho para todos", "Open", "pr-68-levada-do-paul-ii-um-caminho-para-todos"),
        real_card("PR 7 - Levada do Moinho", "Closed", "pr-7-levada-do-moinho"),
        real_card("PS PR 3 - Levada do Pico do Castelo", "Closed", "ps-pr-3-levada-do-pico-do-castelo"),
    ]
    html = (
        '<div class="container"><div class="title">Ready for a walk? Explore all the Official Hiking Trails</div>'
        f'<div class="cards" role="group">{"".join(cards)}</div></div>'
    )
    result = parse_listing(html, BASE)
    assert {k: v["state"] for k, v in result.items()} == {
        "pr-1": "open",
        "pr-1.3": "partial",
        "pr-13": "open",
        "pr-6.8": "open",
        "pr-7": "closed",
        "ps-pr-3": "closed",
    }
    assert result["pr-1.3"]["sourceUrl"] == BASE + "pr-13-vereda-da-encumeada/"


def test_parse_listing_ignores_cards_it_cannot_tell_apart():
    # Two trails in one container with no per-trail wrapper: never guess which status belongs to which.
    html = (
        f"<div><a href='{BASE}pr-6-x/'>PR 6</a> <a href='{BASE}pr-7-y/'>PR 7</a> <span>Closed</span></div>"
    )
    assert parse_listing(html, BASE) == {}


def many_cards(n):
    return page([card_inside_link(f"PR {i}", f"Trail {i}", "Open", f"pr-{i}-trail") for i in range(1, n + 1)])


def test_build_success_marks_every_trail_fresh():
    doc, ok = build(previous=None, html=many_cards(35))
    assert ok
    assert doc["ok"] is True
    assert len(doc["trails"]) == 35
    assert all(not t["stale"] and t["checkedAt"] == doc["generatedAt"] for t in doc["trails"].values())


def test_build_keeps_previous_status_as_stale_when_the_page_changes():
    previous = {"trails": {"pr-6": {"code": "PR 6", "state": "open", "checkedAt": "2026-10-25T10:00:00Z", "stale": False}}}
    doc, ok = build(previous=previous, html=many_cards(3))
    assert not ok
    assert doc["trails"]["pr-6"]["stale"] is True
    assert doc["trails"]["pr-6"]["checkedAt"] == "2026-10-25T10:00:00Z"
    assert "page layout" in doc["error"]


def test_build_handles_a_failed_download_with_no_history():
    doc, ok = build(previous=None, html=None, error="Download failed: timeout")
    assert not ok
    assert doc["trails"] == {}
    assert doc["error"] == "Download failed: timeout"


def test_minimum_is_high_enough_to_catch_a_broken_page():
    assert build_status.MIN_TRAILS >= 30
