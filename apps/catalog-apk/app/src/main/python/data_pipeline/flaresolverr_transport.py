"""Minimal FlareSolverr HTTP transport shared by Megazip / PartSouq / Catalog APK."""

from __future__ import annotations

import logging
from typing import Any

logger = logging.getLogger(__name__)

_CF_MARKERS = (
    "just a moment",
    "cf-browser-verification",
    "cdn-cgi/challenge",
    "attention required",
    "cloudflare",
    "managed challenge",
)


def normalize_flaresolverr_api(url: str) -> str:
    base = (url or "").strip().rstrip("/")
    if not base:
        return "http://127.0.0.1:8191/v1"
    if base.endswith("/v1"):
        return base
    return f"{base}/v1"


def normalize_flaresolverr_health(url: str) -> str:
    base = (url or "").strip().rstrip("/")
    if base.endswith("/v1"):
        base = base[: -len("/v1")]
    return base or "http://127.0.0.1:8191"


def looks_like_cloudflare(status: int, html: str, headers: dict[str, str] | None = None) -> bool:
    if status in (403, 503, 429):
        return True
    hdrs = {k.lower(): v.lower() for k, v in (headers or {}).items()}
    if any(k.startswith("cf-") for k in hdrs) or "cloudflare" in hdrs.get("server", ""):
        return True
    lower = (html or "")[:8000].lower()
    return any(m in lower for m in _CF_MARKERS)


async def flaresolverr_health(api_or_base: str) -> bool:
    try:
        import httpx
    except ImportError:
        return False
    base = normalize_flaresolverr_health(api_or_base)
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            for path in ("/health", "/"):
                try:
                    response = await client.get(f"{base}{path}")
                    if response.status_code == 200:
                        return True
                except Exception:  # noqa: BLE001
                    continue
    except Exception:  # noqa: BLE001
        return False
    return False


async def flaresolverr_fetch_html(
    url: str,
    *,
    api_url: str,
    timeout_ms: int = 60_000,
    session: str | None = None,
) -> tuple[int, str]:
    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError("httpx required for FlareSolverr") from exc

    api = normalize_flaresolverr_api(api_url)
    payload: dict[str, Any] = {
        "cmd": "request.get",
        "url": url,
        "maxTimeout": timeout_ms,
    }
    if session:
        payload["session"] = session

    async with httpx.AsyncClient(timeout=(timeout_ms / 1000) + 30) as client:
        response = await client.post(api, json=payload)
        response.raise_for_status()
        data = response.json()

    if data.get("status") != "ok":
        raise RuntimeError(f"FlareSolverr error: {data.get('message') or data}")
    solution = data.get("solution")
    if not isinstance(solution, dict):
        raise RuntimeError(f"FlareSolverr missing solution: {data}")
    html = str(solution.get("response") or "")
    status = int(solution.get("status") or 0)
    return status, html
