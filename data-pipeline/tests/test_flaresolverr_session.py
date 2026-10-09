from __future__ import annotations

import asyncio
from pathlib import Path

from data_pipeline.amayama_catalog_auto import (
    ConcurrencyGate,
    FlareSolverrSession,
    RateLimiter,
    flaresolverr_session_create,
    flaresolverr_sessions_list,
)


def test_rate_limiter_includes_jitter() -> None:
    limiter = RateLimiter(1.0, jitter_seconds=0.5, jitter_ratio=0.2)
    samples = [limiter.next_delay() for _ in range(40)]
    assert min(samples) >= 1.0
    assert max(samples) <= 1.0 + 0.5 + 0.2 + 1e-9
    assert max(samples) > min(samples)  # randomised


def test_concurrency_gate_caps_inflight() -> None:
    gate = ConcurrencyGate(2)
    inflight = 0
    peak = 0
    lock = asyncio.Lock()

    async def job() -> None:
        nonlocal inflight, peak
        async with gate:
            async with lock:
                inflight += 1
                peak = max(peak, inflight)
            await asyncio.sleep(0.05)
            async with lock:
                inflight -= 1

    async def run() -> None:
        await asyncio.gather(*[job() for _ in range(6)])

    asyncio.run(run())
    assert peak <= 2


def test_flaresolverr_session_reuses_named_session(tmp_path: Path, monkeypatch) -> None:
    import httpx

    calls: list[dict] = []

    class FakeResp:
        def __init__(self, payload: dict) -> None:
            self._payload = payload

        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict:
            return self._payload

    class FakeClient:
        def __init__(self, *args, **kwargs) -> None:
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        async def post(self, url, json):
            calls.append(json)
            cmd = json.get("cmd")
            if cmd == "sessions.list":
                return FakeResp({"status": "ok", "sessions": ["gtr-catalog"]})
            if cmd == "sessions.create":
                return FakeResp({"status": "ok", "session": json.get("session") or "new"})
            if cmd == "request.get":
                assert json.get("session") == "gtr-catalog"
                return FakeResp(
                    {
                        "status": "ok",
                        "solution": {"response": "<html>ok</html>", "cookies": [], "userAgent": "UA"},
                    }
                )
            if cmd == "sessions.destroy":
                return FakeResp({"status": "ok"})
            raise AssertionError(cmd)

    monkeypatch.setattr(httpx, "AsyncClient", FakeClient)
    persist = tmp_path / "flaresolverr_session.json"
    persist.write_text('{"session": "gtr-catalog"}\n', encoding="utf-8")

    async def _run() -> str:
        fs = FlareSolverrSession(
            api_url="http://127.0.0.1:8191/v1",
            session_name="gtr-catalog",
            persist_path=persist,
            persist_session=True,
            max_concurrent=1,
            keepalive_seconds=0,
        )
        async with fs:
            sol = await fs.request("https://partsouq.com/en/catalog/genuine/locate?c=Nissan")
            assert "ok" in sol["response"]
            return fs.session_id or ""

    sid = asyncio.run(_run())
    assert sid == "gtr-catalog"
    assert any(c.get("cmd") == "sessions.list" for c in calls)
    assert not any(c.get("cmd") == "sessions.create" for c in calls)
    assert any(c.get("cmd") == "request.get" for c in calls)
    # persist_session=True → no destroy on exit
    assert not any(c.get("cmd") == "sessions.destroy" for c in calls)


def test_flaresolverr_session_create_accepts_name(monkeypatch) -> None:
    import httpx

    class FakeResp:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict:
            return {"status": "ok", "session": "named-1"}

    class FakeClient:
        def __init__(self, *args, **kwargs) -> None:
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        async def post(self, url, json):
            assert json["cmd"] == "sessions.create"
            assert json["session"] == "named-1"
            return FakeResp()

    monkeypatch.setattr(httpx, "AsyncClient", FakeClient)
    sid = asyncio.run(
        flaresolverr_session_create("http://127.0.0.1:8191/v1", session_name="named-1")
    )
    assert sid == "named-1"


def test_flaresolverr_sessions_list(monkeypatch) -> None:
    import httpx

    class FakeResp:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict:
            return {"status": "ok", "sessions": ["a", "b"]}

    class FakeClient:
        def __init__(self, *args, **kwargs) -> None:
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        async def post(self, url, json):
            assert json["cmd"] == "sessions.list"
            return FakeResp()

    monkeypatch.setattr(httpx, "AsyncClient", FakeClient)
    assert asyncio.run(flaresolverr_sessions_list("http://127.0.0.1:8191/v1")) == ["a", "b"]
