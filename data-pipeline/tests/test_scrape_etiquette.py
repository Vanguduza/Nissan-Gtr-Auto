from __future__ import annotations

import asyncio
from pathlib import Path
from urllib.robotparser import RobotFileParser

from data_pipeline.scrape_etiquette import (
    RateLimiter,
    ResponseCache,
    RobotsGate,
    ScrapeConfig,
    backoff_delay,
)


def test_scrape_config_loads_defaults(tmp_path: Path) -> None:
    missing = tmp_path / "nope.json"
    cfg = ScrapeConfig.load(missing)
    assert cfg.rate_limit_seconds == 1.5
    assert cfg.jitter_seconds >= 0.0
    assert cfg.flaresolverr_max_concurrent >= 1
    assert "GTR-Auto-CatalogBot" in cfg.user_agent


def test_scrape_config_from_repo_file() -> None:
    cfg = ScrapeConfig.load()
    assert cfg.base_url.startswith("https://")
    assert "partsouq.com" in cfg.base_url
    assert "nissan" in cfg.start_url.lower()
    assert cfg.use_flaresolverr is True
    assert cfg.flaresolverr_persist_session is True
    assert cfg.rate_limit_seconds >= 1.0


def test_backoff_grows_and_caps() -> None:
    d0 = backoff_delay(0, base=2.0, maximum=60.0)
    d3 = backoff_delay(3, base=2.0, maximum=60.0)
    d10 = backoff_delay(10, base=2.0, maximum=60.0)
    assert 2.0 <= d0 <= 2.5
    assert d3 > d0
    assert d10 <= 60.5


def test_rate_limiter_enforces_interval() -> None:
    limiter = RateLimiter(0.05)

    async def _run() -> float:
        import time

        t0 = time.monotonic()
        await limiter.wait()
        await limiter.wait()
        return time.monotonic() - t0

    elapsed = asyncio.run(_run())
    assert elapsed >= 0.04


def test_response_cache_roundtrip(tmp_path: Path) -> None:
    cache = ResponseCache(tmp_path)
    url = "https://www.amayama.com/en/catalogs/nissan"
    cache.put_text(url, "<html>ok</html>")
    cache.put_json(url, {"parts": []})
    assert cache.get_text(url) == "<html>ok</html>"
    assert cache.get_json(url) == {"parts": []}


def test_robots_gate_honours_parser(monkeypatch) -> None:
    gate = RobotsGate("GTR-Auto-CatalogBot/1.0")

    class FakeParser(RobotFileParser):
        def can_fetch(self, useragent: str, url: str) -> bool:
            return "/forbidden" not in url

    monkeypatch.setattr(gate, "_parser_for", lambda url: FakeParser())
    assert gate.allowed("https://example.com/ok")
    assert not gate.allowed("https://example.com/forbidden/path")
