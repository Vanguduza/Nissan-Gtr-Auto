from __future__ import annotations

import asyncio

from data_pipeline.amayama_catalog_auto import (
    flaresolverr_cookies_to_storage_state,
    flaresolverr_proxy_payload,
    flaresolverr_request,
    parse_proxy,
)


def test_flaresolverr_proxy_payload_with_auth() -> None:
    ep = parse_proxy("http://u:p@host:9000")
    assert flaresolverr_proxy_payload(ep) == {"url": "http://u:p@host:9000"}


def test_flaresolverr_cookies_to_storage_state() -> None:
    state = flaresolverr_cookies_to_storage_state(
        [
            {
                "name": "cf_clearance",
                "value": "abc",
                "domain": ".amayama.com",
                "path": "/",
                "expiry": 2000000000,
                "httpOnly": True,
                "secure": True,
            }
        ],
        user_agent="UA-Test",
    )
    assert state["userAgent"] == "UA-Test"
    assert state["cookies"][0]["name"] == "cf_clearance"
    assert state["cookies"][0]["sameSite"] == "Lax"


def test_flaresolverr_request_ok(monkeypatch) -> None:
    import httpx

    class FakeResp:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict:
            return {
                "status": "ok",
                "solution": {
                    "userAgent": "UA",
                    "cookies": [{"name": "a", "value": "b", "domain": ".x.com"}],
                    "response": "<html></html>",
                },
            }

    class FakeClient:
        def __init__(self, *args, **kwargs) -> None:
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        async def post(self, url, json):  # noqa: A002
            assert "8191" in url
            assert json["cmd"] == "request.get"
            return FakeResp()

    monkeypatch.setattr(httpx, "AsyncClient", FakeClient)
    solution = asyncio.run(
        flaresolverr_request("https://example.com", api_url="http://127.0.0.1:8191/v1")
    )
    assert solution["userAgent"] == "UA"
