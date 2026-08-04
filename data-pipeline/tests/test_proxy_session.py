from __future__ import annotations

from pathlib import Path

from data_pipeline.amayama_catalog_auto import (
    clean_browser_session,
    ensure_session_for_proxy,
    parse_proxy,
    read_session_meta,
    session_paths,
)


def test_parse_proxy_url_with_auth() -> None:
    ep = parse_proxy("http://alice:s3cret@proxy.example:8000")
    assert ep.server == "http://proxy.example:8000"
    assert ep.username == "alice"
    assert ep.password == "s3cret"
    assert "alice" not in ep.redacted() or "al" in ep.redacted()
    pw = ep.as_playwright()
    assert pw["server"] == "http://proxy.example:8000"
    assert pw["username"] == "alice"


def test_parse_proxy_host_port_user_pass() -> None:
    ep = parse_proxy("1.2.3.4:10000:user1:pass1")
    assert ep.server == "http://1.2.3.4:10000"
    assert ep.username == "user1"
    assert ep.password == "pass1"


def test_clean_session_on_proxy_change(tmp_path: Path) -> None:
    session = tmp_path / "browser_session"
    a = parse_proxy("http://u:p@host:1000")
    b = parse_proxy("http://u:p@host:2000")

    ensure_session_for_proxy(session, a, force_clean=True)
    marker = session_paths(session)["profile"] / "marker.txt"
    marker.write_text("keep", encoding="utf-8")
    assert marker.exists()

    ensure_session_for_proxy(session, b, force_clean=False)
    assert not marker.exists()
    meta = read_session_meta(session)
    assert meta["proxy_fingerprint"] == b.fingerprint()


def test_clean_browser_session_wipes(tmp_path: Path) -> None:
    session = tmp_path / "sess"
    (session / "profile").mkdir(parents=True)
    (session / "profile" / "x").write_text("1", encoding="utf-8")
    clean_browser_session(session)
    assert session.exists()
    assert (session / "profile").exists()
    assert not (session / "profile" / "x").exists()
