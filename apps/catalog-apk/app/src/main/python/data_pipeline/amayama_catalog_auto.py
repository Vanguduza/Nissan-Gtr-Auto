"""Nissan OEM catalog scrape — PartSouq primary (FlareSolverr, local IP).

Default: run until the English Nissan queue is drained, then transform.

Run:
  cd data-pipeline
  pip install -e ".[scraping,dev]"
  docker compose -f ../docker-compose.satellites.yml --profile scrape up -d
  python -m data_pipeline.amayama_catalog_auto
  python -m data_pipeline.amayama_catalog_auto --until-complete --local-ip --import-dry-run
  python -m data_pipeline.amayama_catalog_auto --max-pages 50 --local-ip   # single capped pass
"""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import logging
import os
import random
import re
import shutil
import sqlite3
import sys
import time
import urllib.request
from dataclasses import asdict, dataclass, field
from enum import IntEnum
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urljoin, urlparse, unquote
from urllib.robotparser import RobotFileParser

from data_pipeline.import_catalog import import_catalog, import_supabase, load_bundle, load_env_files, resolve_supabase_credentials
from data_pipeline.parse_fast import write_bundle
from data_pipeline.parse_partsouq_html import (
    assembly_hints_from_diagram_title,
    category_hints_from_url,
    is_generic_part_name,
    normalize_epc_category_name,
    subcategory_from_diagram_title,
)
from data_pipeline.validate import validate_bundle

logger = logging.getLogger("data_pipeline.amayama_catalog_auto")
PACKAGE_ROOT = Path(__file__).resolve().parent.parent

# ======================================================================
# 1. SCRAPE ETIQUETTE
# ======================================================================

DEFAULT_USER_AGENT = "GTR-Auto-CatalogBot/1.0 (+https://nissangtrauto.co.zw/bot)"
DEFAULT_SESSION_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
)


@dataclass
class ScrapeConfig:
    base_url: str = "https://partsouq.com"
    start_url: str = "https://partsouq.com/en/catalog/genuine/locate?c=Nissan"
    user_agent: str = DEFAULT_USER_AGENT
    session_user_agent: str = DEFAULT_SESSION_UA
    rate_limit_seconds: float = 1.5
    jitter_seconds: float = 0.75
    jitter_ratio: float = 0.35
    max_retries: int = 5
    backoff_base_seconds: float = 2.0
    backoff_max_seconds: float = 60.0
    max_concurrent_workers: int = 1
    flaresolverr_max_concurrent: int = 1
    flaresolverr_session_name: str = "gtr-catalog"
    flaresolverr_persist_session: bool = True
    flaresolverr_keepalive_seconds: float = 240.0
    proxy_list: list[str] = field(default_factory=list)
    proxies_file: str = "config/proxies.json"
    session_dir: str = "out/browser_session"
    flaresolverr_url: str = "http://127.0.0.1:8191/v1"
    flaresolverr_timeout_ms: int = 60000
    use_flaresolverr: bool = True
    allowed_path_substring: str = "/en/catalog/"
    allowed_locale: str = "en"
    allowed_brand: str = "nissan"
    diagram_storage_prefix: str = "partsouq/nissan"
    supabase_diagrams_bucket: str = "catalog-diagrams"
    max_attempts: int = 5
    checkpoint_every: int = 10
    hierarchy_aware: bool = True
    nhtsa_enrich: bool = False
    run_until_complete: bool = True
    completion_max_rounds: int = 5
    # Compat: used when queue_mode is empty / unknown.
    queue_deep_first: bool = True
    # "deep_first" | "bfs" | "hybrid" — hybrid prefers /vehicle (L2) then deep-first.
    queue_mode: str = ""
    # Multi-make chassis→VIN maps (default config/chassis_catalogs.json).
    chassis_catalogs_path: str = "config/chassis_catalogs.json"

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> ScrapeConfig:
        known = {f.name for f in cls.__dataclass_fields__.values()}  # type: ignore[attr-defined]
        return cls(**{k: v for k, v in data.items() if k in known})

    @classmethod
    def load(cls, path: Path | None = None) -> ScrapeConfig:
        if path is None:
            path = Path(__file__).resolve().parent.parent / "config" / "scrape.json"
        if not path.exists():
            return cls()
        with path.open(encoding="utf-8") as fh:
            return cls.from_dict(json.load(fh))

    def resolved_queue_mode(self) -> str:
        """Return canonical queue claim mode: deep_first | bfs | hybrid."""
        mode = (self.queue_mode or "").strip().lower()
        if mode in {"deep_first", "bfs", "hybrid"}:
            return mode
        return "deep_first" if self.queue_deep_first else "bfs"


# ======================================================================
# PROXY + BROWSER SESSION (residential + manual CF pass)
# ======================================================================


@dataclass(frozen=True)
class ProxyEndpoint:
    raw: str
    server: str
    username: str | None = None
    password: str | None = None

    def as_playwright(self) -> dict[str, str]:
        out = {"server": self.server}
        if self.username:
            out["username"] = self.username
        if self.password:
            out["password"] = self.password
        return out

    def fingerprint(self) -> str:
        material = f"{self.server}|{self.username or ''}"
        return hashlib.sha256(material.encode("utf-8")).hexdigest()[:16]

    def redacted(self) -> str:
        if self.username:
            return f"{self.server} (user={self.username[:2]}…)"
        return self.server


def parse_proxy(raw: str) -> ProxyEndpoint:
    """Parse residential proxy URLs / host:port:user:pass into Patchright dict."""
    text = raw.strip()
    if not text or text.startswith("_"):
        raise ValueError("empty proxy")

    # host:port:user:pass
    if "://" not in text and text.count(":") >= 3:
        host, port, user, password = text.split(":", 3)
        return ProxyEndpoint(
            raw=text,
            server=f"http://{host}:{port}",
            username=user,
            password=password,
        )

    if "://" not in text:
        text = f"http://{text}"

    parsed = urlparse(text)
    if not parsed.hostname or not parsed.port:
        raise ValueError(f"Invalid proxy (need host+port): {raw}")
    server = f"{parsed.scheme}://{parsed.hostname}:{parsed.port}"
    user = unquote(parsed.username) if parsed.username else None
    password = unquote(parsed.password) if parsed.password else None
    return ProxyEndpoint(raw=raw, server=server, username=user, password=password)


def load_proxy_strings(
    *,
    config: ScrapeConfig,
    package_root: Path,
    explicit: list[str] | None = None,
) -> list[str]:
    """Merge PROXY_LIST env, --proxy args, proxies.json, and scrape.json proxy_list."""
    found: list[str] = []
    if explicit:
        found.extend(explicit)

    env_proxies = [
        p.strip() for p in os.environ.get("PROXY_LIST", "").split(",") if p.strip()
    ]
    found.extend(env_proxies)

    proxies_path = Path(config.proxies_file)
    if not proxies_path.is_absolute():
        proxies_path = package_root / proxies_path
    if proxies_path.exists():
        with proxies_path.open(encoding="utf-8") as fh:
            payload = json.load(fh)
        if isinstance(payload, list):
            found.extend(str(x) for x in payload if str(x).strip())
        elif isinstance(payload, dict):
            found.extend(str(x) for x in payload.get("proxies", []) if str(x).strip())

    found.extend(config.proxy_list)

    # Drop placeholders / empties / comments
    cleaned: list[str] = []
    seen: set[str] = set()
    for item in found:
        s = item.strip()
        if not s or s.startswith("_") or "example-provider" in s or s == "USER:PASS@":
            continue
        if "USER:PASS@" in s:
            continue
        if s not in seen:
            seen.add(s)
            cleaned.append(s)
    return cleaned


def resolve_proxies(raw_list: list[str]) -> list[ProxyEndpoint]:
    out: list[ProxyEndpoint] = []
    for raw in raw_list:
        try:
            out.append(parse_proxy(raw))
        except ValueError as exc:
            logger.warning("Skipping bad proxy %r: %s", raw, exc)
    return out


def session_paths(session_dir: Path) -> dict[str, Path]:
    return {
        "root": session_dir,
        "profile": session_dir / "profile",
        "storage_state": session_dir / "storage_state.json",
        "meta": session_dir / "active_proxy.json",
        "flaresolverr_session": session_dir / "flaresolverr_session.json",
    }


def clean_browser_session(session_dir: Path) -> None:
    """Wipe Patchright profile + CF cookies so a new proxy starts clean."""
    if session_dir.exists():
        shutil.rmtree(session_dir, ignore_errors=True)
        logger.info("Cleaned browser session directory: %s", session_dir)
    session_dir.mkdir(parents=True, exist_ok=True)
    (session_dir / "profile").mkdir(parents=True, exist_ok=True)


def read_session_meta(session_dir: Path) -> dict[str, Any]:
    meta_path = session_paths(session_dir)["meta"]
    if not meta_path.exists():
        return {}
    try:
        return json.loads(meta_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}


def write_session_meta(session_dir: Path, meta: dict[str, Any]) -> None:
    paths = session_paths(session_dir)
    paths["root"].mkdir(parents=True, exist_ok=True)
    paths["meta"].write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")


def ensure_session_for_proxy(
    session_dir: Path,
    proxy: ProxyEndpoint | None,
    *,
    force_clean: bool = False,
    flaresolverr_url: str | None = None,
) -> None:
    """Clean session when proxy changes or --clean-session is set."""
    paths = session_paths(session_dir)
    fp = proxy.fingerprint() if proxy else "direct"
    meta = read_session_meta(session_dir)
    changed = meta.get("proxy_fingerprint") != fp
    if force_clean or changed or not paths["profile"].exists():
        if changed and meta:
            logger.info(
                "Proxy changed (%s → %s); wiping browser session",
                meta.get("proxy_fingerprint"),
                fp,
            )
        # Drop persisted FlareSolverr session so a fresh cookie jar is created
        persisted = paths.get("flaresolverr_session")
        if persisted and persisted.exists() and flaresolverr_url:
            try:
                data = json.loads(persisted.read_text(encoding="utf-8"))
                sid = data.get("session")
                if sid:
                    asyncio.run(flaresolverr_session_destroy(flaresolverr_url, str(sid)))
            except Exception:  # noqa: BLE001
                pass
        clean_browser_session(session_dir)
        write_session_meta(
            session_dir,
            {
                "proxy_fingerprint": fp,
                "proxy_redacted": proxy.redacted() if proxy else "direct",
                "cf_passed": False,
            },
        )


async def probe_proxy(proxy: ProxyEndpoint, *, timeout: float = 20.0) -> bool:
    """Quick residential egress check via https://api.ipify.org."""
    try:
        import httpx
    except ImportError:
        logger.warning("httpx not installed — skipping proxy probe")
        return True

    proxy_url = proxy.raw
    if proxy.username and "://" in proxy.server:
        scheme = urlparse(proxy.server).scheme
        hostport = proxy.server.split("://", 1)[1]
        proxy_url = f"{scheme}://{proxy.username}:{proxy.password}@{hostport}"

    try:
        async with httpx.AsyncClient(proxy=proxy_url, timeout=timeout, follow_redirects=True) as client:
            response = await client.get("https://api.ipify.org?format=json")
            response.raise_for_status()
            ip = response.json().get("ip")
            logger.info("Proxy OK %s → egress IP %s", proxy.redacted(), ip)
            return True
    except Exception as exc:  # noqa: BLE001
        logger.error("Proxy FAILED %s: %s", proxy.redacted(), exc)
        return False


async def select_working_proxy(
    proxies: list[ProxyEndpoint],
    *,
    index: int | None = None,
    probe: bool = True,
) -> ProxyEndpoint | None:
    if not proxies:
        return None
    if index is not None:
        if index < 0 or index >= len(proxies):
            raise IndexError(f"proxy index {index} out of range 0..{len(proxies)-1}")
        chosen = proxies[index]
        if probe and not await probe_proxy(chosen):
            raise RuntimeError(f"Selected proxy is not working: {chosen.redacted()}")
        return chosen

    if not probe:
        return proxies[0]

    for proxy in proxies:
        if await probe_proxy(proxy):
            return proxy
    raise RuntimeError(
        "No working residential proxy found. Update config/proxies.json or PROXY_LIST."
    )


async def wait_for_cf_clear(page, *, timeout_ms: int = 300_000) -> None:
    """Wait until Cloudflare interstitial is gone (manual or auto)."""
    await page.wait_for_function(
        """() => {
            const t = (document.title || '').toLowerCase();
            if (t.includes('just a moment') || t.includes('attention required')) return false;
            if (document.querySelector('#challenge-stage, #cf-challenge-running')) return false;
            return true;
        }""",
        timeout=timeout_ms,
    )


async def manual_cf_pass(
    *,
    config: ScrapeConfig,
    session_dir: Path,
    proxy: ProxyEndpoint | None,
    start_url: str | None = None,
    timeout_ms: int = 300_000,
) -> Path:
    """Headed Patchright session: user solves CF, cookies saved for crawl reuse."""
    try:
        from patchright.async_api import async_playwright
    except ImportError as exc:
        raise RuntimeError(
            "patchright required. pip install -e '.[scraping]' && patchright install chromium"
        ) from exc

    paths = session_paths(session_dir)
    paths["profile"].mkdir(parents=True, exist_ok=True)
    target = start_url or config.start_url
    proxy_kw = proxy.as_playwright() if proxy else None

    logger.info(
        "Manual CF pass starting (proxy=%s). Solve the challenge in the browser window.",
        proxy.redacted() if proxy else "direct",
    )
    print(
        "\n=== MANUAL CLOUDFLARE PASS ===\n"
        f"Proxy : {proxy.redacted() if proxy else 'direct (no proxy)'}\n"
        f"URL   : {target}\n"
        "1) Complete the Cloudflare check in the opened browser.\n"
        "2) Wait until the real Amayama catalog page loads.\n"
        "3) Return here — the script auto-detects clearance "
        f"(timeout {timeout_ms // 1000}s).\n"
    )

    async with async_playwright() as p:
        context = await p.chromium.launch_persistent_context(
            user_data_dir=str(paths["profile"]),
            headless=False,
            proxy=proxy_kw,
            viewport={"width": 1365, "height": 900},
            user_agent=config.session_user_agent,
            locale="en-US",
            args=["--disable-blink-features=AutomationControlled"],
        )
        page = context.pages[0] if context.pages else await context.new_page()
        await page.goto(target, wait_until="domcontentloaded", timeout=60000)
        try:
            await wait_for_cf_clear(page, timeout_ms=timeout_ms)
        except Exception as exc:  # noqa: BLE001
            await context.close()
            raise RuntimeError(
                "Cloudflare was not cleared in time. Re-run --cf-pass and complete the check."
            ) from exc

        # Confirm we are not still on an interstitial
        title = await page.title()
        if "just a moment" in title.lower():
            await context.close()
            raise RuntimeError("CF challenge still present after wait")

        await context.storage_state(path=str(paths["storage_state"]))
        meta = read_session_meta(session_dir)
        meta.update(
            {
                "cf_passed": True,
                "cf_passed_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "start_url": target,
                "session_user_agent": config.session_user_agent,
                "proxy_fingerprint": proxy.fingerprint() if proxy else "direct",
                "proxy_redacted": proxy.redacted() if proxy else "direct",
            }
        )
        write_session_meta(session_dir, meta)
        await context.close()

    logger.info("CF pass saved → %s", paths["storage_state"])
    print(f"\nCF cookies saved to {paths['storage_state']}\n")
    return paths["storage_state"]


# ======================================================================
# FLARESOLVERR (preferred Cloudflare solve)
# ======================================================================


def flaresolverr_proxy_payload(proxy: ProxyEndpoint | None) -> dict[str, str] | None:
    if not proxy:
        return None
    if proxy.username and proxy.password:
        scheme = urlparse(proxy.server).scheme or "http"
        hostport = proxy.server.split("://", 1)[-1]
        return {"url": f"{scheme}://{proxy.username}:{proxy.password}@{hostport}"}
    return {"url": proxy.server}


def flaresolverr_cookies_to_storage_state(
    cookies: list[dict[str, Any]],
    *,
    user_agent: str,
) -> dict[str, Any]:
    """Convert FlareSolverr cookie list → Playwright storage_state."""
    converted: list[dict[str, Any]] = []
    for raw in cookies:
        domain = raw.get("domain") or ".partsouq.com"
        expires = raw.get("expiry") or raw.get("expires") or -1
        try:
            expires_f = float(expires)
        except (TypeError, ValueError):
            expires_f = -1
        converted.append(
            {
                "name": raw["name"],
                "value": raw["value"],
                "domain": domain,
                "path": raw.get("path") or "/",
                "expires": expires_f,
                "httpOnly": bool(raw.get("httpOnly", False)),
                "secure": bool(raw.get("secure", True)),
                "sameSite": "Lax",
            }
        )
    return {"cookies": converted, "origins": [], "userAgent": user_agent}


async def flaresolverr_request(
    url: str,
    *,
    api_url: str,
    timeout_ms: int = 60000,
    proxy: ProxyEndpoint | None = None,
    session: str | None = None,
    method: str = "GET",
    post_data: str | None = None,
) -> dict[str, Any]:
    """Call FlareSolverr request.get/post and return the solution object."""
    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError("httpx required for FlareSolverr (pip install -e '.[scraping]')") from exc

    cmd = "request.post" if method.upper() == "POST" else "request.get"
    payload: dict[str, Any] = {
        "cmd": cmd,
        "url": url,
        "maxTimeout": timeout_ms,
    }
    if session:
        payload["session"] = session
    if method.upper() == "POST":
        payload["postData"] = post_data or ""
    # Proxy on request only when not using a sticky session (session carries its own proxy)
    if not session:
        proxy_payload = flaresolverr_proxy_payload(proxy)
        if proxy_payload:
            payload["proxy"] = proxy_payload

    async with httpx.AsyncClient(timeout=(timeout_ms / 1000) + 30) as client:
        response = await client.post(api_url, json=payload)
        response.raise_for_status()
        data = response.json()

    if data.get("status") != "ok":
        raise RuntimeError(f"FlareSolverr error: {data.get('message') or data}")
    solution = data.get("solution")
    if not isinstance(solution, dict):
        raise RuntimeError(f"FlareSolverr missing solution: {data}")
    return solution


async def flaresolverr_sessions_list(api_url: str) -> list[str]:
    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError("httpx required") from exc
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(api_url, json={"cmd": "sessions.list"})
        response.raise_for_status()
        data = response.json()
    if data.get("status") != "ok":
        raise RuntimeError(f"FlareSolverr sessions.list failed: {data}")
    sessions = data.get("sessions") or []
    return [str(s) for s in sessions]


async def flaresolverr_session_create(
    api_url: str,
    *,
    session_name: str | None = None,
    proxy: ProxyEndpoint | None = None,
) -> str:
    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError("httpx required") from exc
    payload: dict[str, Any] = {"cmd": "sessions.create"}
    if session_name:
        payload["session"] = session_name
    proxy_payload = flaresolverr_proxy_payload(proxy)
    if proxy_payload:
        payload["proxy"] = proxy_payload
    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(api_url, json=payload)
        response.raise_for_status()
        data = response.json()
    if data.get("status") != "ok":
        # Named session may already exist — treat as success when name provided
        message = str(data.get("message") or data).lower()
        if session_name and "already exists" in message:
            return session_name
        raise RuntimeError(f"FlareSolverr sessions.create failed: {data}")
    session = data.get("session") or session_name
    if not session:
        raise RuntimeError(f"FlareSolverr sessions.create missing session id: {data}")
    return str(session)


async def flaresolverr_session_destroy(api_url: str, session: str) -> None:
    try:
        import httpx
    except ImportError:
        return
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            await client.post(api_url, json={"cmd": "sessions.destroy", "session": session})
    except Exception:  # noqa: BLE001
        pass


class ConcurrencyGate:
    """Hard cap on in-flight async work (FlareSolverr / browser fetches)."""

    def __init__(self, limit: int) -> None:
        self.limit = max(1, int(limit))
        self._sem = asyncio.Semaphore(self.limit)

    async def __aenter__(self) -> ConcurrencyGate:
        await self._sem.acquire()
        return self

    async def __aexit__(self, *args: object) -> None:
        self._sem.release()


class FlareSolverrSession:
    """Persistent FlareSolverr browser session — keeps CF cookies alive across requests.

    Uses sessions.create / sessions.list / sessions.destroy. Optionally reuses a named
    session across process restarts when persist_session is enabled.
    """

    def __init__(
        self,
        *,
        api_url: str,
        session_name: str = "gtr-catalog",
        proxy: ProxyEndpoint | None = None,
        persist_path: Path | None = None,
        persist_session: bool = True,
        max_concurrent: int = 1,
        timeout_ms: int = 60000,
        keepalive_seconds: float = 240.0,
        keepalive_url: str | None = None,
    ) -> None:
        self.api_url = api_url
        self.session_name = session_name
        self.proxy = proxy
        self.persist_path = persist_path
        self.persist_session = persist_session
        self.timeout_ms = timeout_ms
        self.keepalive_seconds = max(0.0, keepalive_seconds)
        self.keepalive_url = keepalive_url
        self.gate = ConcurrencyGate(max_concurrent)
        self.session_id: str | None = None
        self._last_used = 0.0
        self._lock = asyncio.Lock()
        self._cf_streak = 0

    def _read_persisted(self) -> str | None:
        if not self.persist_path or not self.persist_path.exists():
            return None
        try:
            data = json.loads(self.persist_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return None
        sid = data.get("session")
        return str(sid) if sid else None

    def _write_persisted(self, session_id: str) -> None:
        if not self.persist_path:
            return
        self.persist_path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "session": session_id,
            "session_name": self.session_name,
            "proxy": self.proxy.redacted() if self.proxy else None,
            "updated_at": time.time(),
        }
        self.persist_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    def _clear_persisted(self) -> None:
        if self.persist_path and self.persist_path.exists():
            try:
                self.persist_path.unlink()
            except OSError:
                pass

    async def ensure(self) -> str:
        async with self._lock:
            if self.session_id:
                return self.session_id

            existing = self._read_persisted() if self.persist_session else None
            live: list[str] = []
            try:
                live = await flaresolverr_sessions_list(self.api_url)
            except Exception as exc:  # noqa: BLE001
                logger.warning("sessions.list failed: %s", exc)

            candidates = []
            if existing:
                candidates.append(existing)
            if self.session_name not in candidates:
                candidates.append(self.session_name)

            for cand in candidates:
                if cand in live:
                    self.session_id = cand
                    self._last_used = time.monotonic()
                    logger.info("Reusing FlareSolverr session %s (cookies preserved)", cand)
                    self._write_persisted(cand)
                    return cand

            created = await flaresolverr_session_create(
                self.api_url,
                session_name=self.session_name,
                proxy=self.proxy,
            )
            self.session_id = created
            self._last_used = time.monotonic()
            self._write_persisted(created)
            logger.info(
                "Created FlareSolverr session %s (proxy=%s)",
                created,
                self.proxy.redacted() if self.proxy else "local-ip",
            )
            return created

    async def request(
        self,
        url: str,
        *,
        method: str = "GET",
        post_data: str | None = None,
    ) -> dict[str, Any]:
        await self.maybe_keepalive()
        async with self.gate:
            session_id = await self.ensure()
            try:
                solution = await flaresolverr_request(
                    url,
                    api_url=self.api_url,
                    timeout_ms=self.timeout_ms,
                    session=session_id,
                    method=method,
                    post_data=post_data,
                )
            except Exception as exc:  # noqa: BLE001
                msg = str(exc).lower()
                if "session" in msg or "not found" in msg:
                    logger.warning("FlareSolverr session lost (%s) — recreating", exc)
                    async with self._lock:
                        self.session_id = None
                    session_id = await self.ensure()
                    solution = await flaresolverr_request(
                        url,
                        api_url=self.api_url,
                        timeout_ms=self.timeout_ms,
                        session=session_id,
                        method=method,
                        post_data=post_data,
                    )
                else:
                    raise
            self._last_used = time.monotonic()
            return solution

    async def note_success(self) -> None:
        self._cf_streak = 0

    async def note_cf_block(self, *, recycle_after: int = 3) -> None:
        """Destroy/recreate session after consecutive Cloudflare challenge pages."""
        self._cf_streak += 1
        if self._cf_streak < max(1, recycle_after):
            return
        logger.warning(
            "Recycling FlareSolverr session after %s consecutive CF blocks",
            self._cf_streak,
        )
        await self.close(destroy=True)
        self._cf_streak = 0

    async def maybe_keepalive(self) -> None:
        """Touch the session if idle too long so CF cookies stay warm."""
        if self.keepalive_seconds <= 0 or not self.keepalive_url:
            return
        if not self.session_id:
            return
        idle = time.monotonic() - self._last_used if self._last_used else self.keepalive_seconds + 1
        if idle < self.keepalive_seconds:
            return
        logger.info("FlareSolverr keepalive after %.0fs idle → %s", idle, self.keepalive_url)
        async with self.gate:
            session_id = await self.ensure()
            try:
                await flaresolverr_request(
                    self.keepalive_url,
                    api_url=self.api_url,
                    timeout_ms=self.timeout_ms,
                    session=session_id,
                )
                self._last_used = time.monotonic()
            except Exception as exc:  # noqa: BLE001
                logger.warning("Keepalive failed: %s — will recreate on next request", exc)
                async with self._lock:
                    self.session_id = None

    async def close(self, *, destroy: bool | None = None) -> None:
        should_destroy = (not self.persist_session) if destroy is None else destroy
        if should_destroy and self.session_id:
            await flaresolverr_session_destroy(self.api_url, self.session_id)
            self._clear_persisted()
            logger.info("Destroyed FlareSolverr session %s", self.session_id)
        self.session_id = None

    async def __aenter__(self) -> FlareSolverrSession:
        await self.ensure()
        return self

    async def __aexit__(self, *args: object) -> None:
        await self.close()


async def flaresolverr_health(api_url: str) -> bool:
    try:
        import httpx
    except ImportError:
        return False
    # API is /v1 — health is typically on /
    base = api_url.rstrip("/")
    if base.endswith("/v1"):
        base = base[: -len("/v1")]
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{base}/health")
            return response.status_code == 200
    except Exception:  # noqa: BLE001
        return False


async def flaresolverr_bootstrap_session(
    *,
    config: ScrapeConfig,
    session_dir: Path,
    proxy: ProxyEndpoint | None = None,
    start_url: str | None = None,
) -> Path:
    """Solve CF via FlareSolverr and persist cookies/UA into the browser session."""
    target = start_url or config.start_url
    api = config.flaresolverr_url
    if not await flaresolverr_health(api):
        raise RuntimeError(
            f"FlareSolverr not reachable at {api}. Start it with:\n"
            "  docker compose -f docker-compose.satellites.yml --profile scrape up -d"
        )

    logger.info("FlareSolverr solving CF for %s (proxy=%s)", target, proxy.redacted() if proxy else "direct")
    solution = await flaresolverr_request(
        target,
        api_url=api,
        timeout_ms=config.flaresolverr_timeout_ms,
        proxy=proxy,
    )
    user_agent = solution.get("userAgent") or config.session_user_agent
    cookies = solution.get("cookies") or []
    state = flaresolverr_cookies_to_storage_state(cookies, user_agent=user_agent)

    paths = session_paths(session_dir)
    paths["root"].mkdir(parents=True, exist_ok=True)
    paths["profile"].mkdir(parents=True, exist_ok=True)
    paths["storage_state"].write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")

    # Also dump HTML snapshot for debugging
    html = solution.get("response") or ""
    if html:
        (paths["root"] / "flaresolverr_last.html").write_text(html, encoding="utf-8")

    write_session_meta(
        session_dir,
        {
            "cf_passed": True,
            "cf_method": "flaresolverr",
            "cf_passed_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "start_url": target,
            "session_user_agent": user_agent,
            "proxy_fingerprint": proxy.fingerprint() if proxy else "direct",
            "proxy_redacted": proxy.redacted() if proxy else "direct",
            "flaresolverr_url": api,
            "cookie_count": len(cookies),
        },
    )
    logger.info(
        "FlareSolverr OK — %s cookies saved to %s",
        len(cookies),
        paths["storage_state"],
    )
    return paths["storage_state"]


async def apply_storage_state_cookies(context, storage_state_path: Path) -> str | None:
    """Load cookies from storage_state into an open browser context. Returns UA if present."""
    if not storage_state_path.exists():
        return None
    state = json.loads(storage_state_path.read_text(encoding="utf-8"))
    cookies = state.get("cookies") or []
    if cookies:
        # Patchright/Playwright require url or domain+path
        normalized = []
        for cookie in cookies:
            item = dict(cookie)
            if "sameSite" in item and item["sameSite"] not in ("Strict", "Lax", "None"):
                item["sameSite"] = "Lax"
            normalized.append(item)
        await context.add_cookies(normalized)
        logger.info("Injected %s FlareSolverr/CF cookies into browser context", len(normalized))
    return state.get("userAgent")


# ======================================================================
# BOT / CF PAGE DETECTION (no third-party captcha APIs)
# ======================================================================


def is_recaptcha_challenge(html: str) -> bool:
    """True only for a blocking bot-captcha interstitial, not pages that embed widgets."""
    low = (html or "").lower()
    # Amayama-style hard gate
    if "/bot/captcha/verify" in low or "recaptcha_bot" in low:
        return True
    if "complete the captcha" in low and "g-recaptcha" in low:
        return True
    # Generic interstitial copy without real catalog navigation
    blocking_phrases = (
        "please verify you are a human",
        "verify you are human",
        "access denied",
        "are you a robot",
    )
    if any(p in low for p in blocking_phrases) and "g-recaptcha" in low:
        return True
    return False



def is_cloudflare_challenge(html: str) -> bool:
    low = (html or "").lower()
    return (
        "just a moment" in low
        or "cf-browser-verification" in low
        or "challenge-platform" in low
        or ("cloudflare" in low and "attention required" in low)
    )


class RateLimiter:
    """Enforce a minimum interval between requests, plus randomised jitter."""

    def __init__(
        self,
        min_interval_seconds: float,
        *,
        jitter_seconds: float = 0.0,
        jitter_ratio: float = 0.0,
    ) -> None:
        self.min_interval = max(0.0, min_interval_seconds)
        self.jitter_seconds = max(0.0, jitter_seconds)
        self.jitter_ratio = max(0.0, jitter_ratio)
        self._lock = asyncio.Lock()
        self._last_request_at = 0.0

    def next_delay(self) -> float:
        """Base interval + uniform absolute jitter + proportional jitter."""
        absolute = random.uniform(0.0, self.jitter_seconds) if self.jitter_seconds else 0.0
        proportional = (
            self.min_interval * random.uniform(0.0, self.jitter_ratio)
            if self.jitter_ratio
            else 0.0
        )
        return self.min_interval + absolute + proportional

    async def wait(self) -> None:
        async with self._lock:
            target = self.next_delay()
            now = time.monotonic()
            delay = target - (now - self._last_request_at)
            if delay > 0:
                await asyncio.sleep(delay)
            self._last_request_at = time.monotonic()


class RobotsGate:
    """Cache robots.txt decisions per host."""

    def __init__(self, user_agent: str) -> None:
        self.user_agent = user_agent
        self._parsers: dict[str, RobotFileParser] = {}

    def _parser_for(self, url: str) -> RobotFileParser:
        parsed = urlparse(url)
        origin = f"{parsed.scheme}://{parsed.netloc}"
        if origin not in self._parsers:
            robots_url = f"{origin}/robots.txt"
            rp = RobotFileParser()
            rp.set_url(robots_url)
            try:
                # RobotFileParser.read() uses urllib with no UA; Amayama returns
                # HTTP 403 for that, which the stdlib treats as "disallow all".
                import urllib.request

                req = urllib.request.Request(
                    robots_url,
                    headers={"User-Agent": self.user_agent},
                )
                with urllib.request.urlopen(req, timeout=20) as resp:
                    body = resp.read().decode("utf-8", errors="replace")
                rp.parse(body.splitlines())
            except Exception as exc:  # noqa: BLE001 — fail open only for fetch errors
                logger.warning(
                    "Could not read robots.txt at %s: %s (allowing by default)",
                    robots_url,
                    exc,
                )
            self._parsers[origin] = rp
        return self._parsers[origin]

    def allowed(self, url: str) -> bool:
        try:
            return bool(self._parser_for(url).can_fetch(self.user_agent, url))
        except Exception as exc:  # noqa: BLE001
            logger.warning("robots.txt check failed for %s: %s", url, exc)
            return False


def backoff_delay(attempt: int, *, base: float = 2.0, maximum: float = 60.0) -> float:
    """Exponential backoff with jitter. attempt is 0-based."""
    raw = min(maximum, base * (2**attempt))
    return raw + random.uniform(0.0, 0.5)


async def retry_async(
    operation,
    *,
    max_retries: int = 5,
    base: float = 2.0,
    maximum: float = 60.0,
    label: str = "operation",
):
    """Run an async callable with exponential backoff."""
    last_exc: Exception | None = None
    for attempt in range(max_retries):
        try:
            return await operation()
        except Exception as exc:  # noqa: BLE001
            last_exc = exc
            if attempt == max_retries - 1:
                break
            delay = backoff_delay(attempt, base=base, maximum=maximum)
            logger.warning(
                "%s failed (attempt %s/%s): %s — waiting %.1fs",
                label,
                attempt + 1,
                max_retries,
                exc,
                delay,
            )
            await asyncio.sleep(delay)
    assert last_exc is not None
    raise last_exc


class ResponseCache:
    """Filesystem cache keyed by URL to avoid re-scraping unchanged pages."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.root.mkdir(parents=True, exist_ok=True)

    def _path_for(self, url: str, suffix: str) -> Path:
        import hashlib

        digest = hashlib.sha256(url.encode("utf-8")).hexdigest()
        return self.root / f"{digest}{suffix}"

    def get_text(self, url: str) -> str | None:
        path = self._path_for(url, ".html")
        if path.exists():
            return path.read_text(encoding="utf-8")
        return None

    def put_text(self, url: str, body: str) -> Path:
        path = self._path_for(url, ".html")
        path.write_text(body, encoding="utf-8")
        return path

    def get_json(self, url: str) -> Any | None:
        import json

        path = self._path_for(url, ".json")
        if path.exists():
            with path.open(encoding="utf-8") as fh:
                return json.load(fh)
        return None

    def put_json(self, url: str, payload: Any) -> Path:
        import json

        path = self._path_for(url, ".json")
        with path.open("w", encoding="utf-8") as fh:
            json.dump(payload, fh)
        return path

# ======================================================================
# 2. HIERARCHY
# ======================================================================

PATH_RE = re.compile(
    r"/catalogs/nissan(?:/(?P<rest>.*))?/?$",
    re.IGNORECASE,
)
PARTSOUQ_PATH_RE = re.compile(
    r"/en/catalog/genuine(?:/(?P<page>[^/]+))?/?$",
    re.IGNORECASE,
)

ENGINE_RE = re.compile(r"^[A-Z]{1,3}\d{2}[A-Z]{0,4}$", re.IGNORECASE)
CHASSIS_RE = re.compile(r"^[A-Z]\d{2}[A-Z]?$", re.IGNORECASE)
YEAR_RANGE_RE = re.compile(
    r"(?P<y1>19\d{2}|20\d{2})\s*[\-–—/]\s*(?P<y2>19\d{2}|20\d{2}|present|now|current)",
    re.IGNORECASE,
)
YEAR_SINGLE_RE = re.compile(r"\b(19\d{2}|20\d{2})\b")


class HierarchyLevel(IntEnum):
    ROOT = 0
    MODEL = 1
    CHASSIS = 2
    ENGINE_OR_PERIOD = 3
    ASSEMBLY = 4
    DIAGRAM = 5


@dataclass(frozen=True)
class HierarchyNode:
    url: str
    level: HierarchyLevel
    model_slug: str | None = None
    chassis_code: str | None = None
    engine_code: str | None = None
    assembly_slug: str | None = None
    year_start: int | None = None
    year_end: int | None = None

    def vehicle_hints(self) -> dict[str, Any]:
        hints: dict[str, Any] = {}
        if self.model_slug:
            hints["model_variant"] = self.model_slug.replace("-", " ").replace("_", " ").title()
        if self.chassis_code:
            hints["chassis_code"] = self.chassis_code.upper()
        if self.engine_code:
            hints["engine_code"] = self.engine_code.upper()
        if self.year_start is not None:
            hints["year_start"] = self.year_start
        if self.year_end is not None:
            hints["year_end"] = self.year_end
        return hints


def parse_year_range(text: str) -> tuple[int | None, int | None]:
    match = YEAR_RANGE_RE.search(text or "")
    if match:
        y1 = int(match.group("y1"))
        y2_raw = match.group("y2").lower()
        y2 = 2026 if y2_raw in {"present", "now", "current"} else int(y2_raw)
        if y1 <= y2:
            return y1, y2
    years = [int(y) for y in YEAR_SINGLE_RE.findall(text or "")]
    if len(years) >= 2:
        return min(years), max(years)
    if len(years) == 1:
        return years[0], years[0]
    return None, None


def classify_url(url: str, *, page_text: str = "") -> HierarchyNode:
    parsed = urlparse(url)
    path = parsed.path.rstrip("/")
    query = (parsed.query or "").lower()
    host = (parsed.netloc or "").lower()

    # --- PartSouq (primary source) ---
    if "partsouq.com" in host or PARTSOUQ_PATH_RE.search(path):
        page_match = PARTSOUQ_PATH_RE.search(path)
        page = ((page_match.group("page") if page_match else "") or "").lower()
        model_slug = None
        if "nissan" in query or "nissan" in (page_text or "").lower() or "c=nissan" in query:
            model_slug = "nissan"
        y1, y2 = parse_year_range(page_text)

        if not page or page in {"locate", "genuine"}:
            return HierarchyNode(
                url=url, level=HierarchyLevel.ROOT, model_slug=model_slug, year_start=y1, year_end=y2
            )
        if page in {"filter", "model", "models"}:
            return HierarchyNode(
                url=url, level=HierarchyLevel.MODEL, model_slug=model_slug or "nissan",
                year_start=y1, year_end=y2,
            )
        if page in {"vehicle", "modification", "modifications"}:
            return HierarchyNode(
                url=url, level=HierarchyLevel.CHASSIS, model_slug=model_slug or "nissan",
                year_start=y1, year_end=y2,
            )
        if page == "groups":
            return HierarchyNode(
                url=url, level=HierarchyLevel.ASSEMBLY, model_slug=model_slug or "nissan",
                year_start=y1, year_end=y2,
            )
        if page in {"unit", "result", "parts"}:
            return HierarchyNode(
                url=url, level=HierarchyLevel.DIAGRAM, model_slug=model_slug or "nissan",
                year_start=y1, year_end=y2,
            )
        return HierarchyNode(
            url=url, level=HierarchyLevel.MODEL, model_slug=model_slug or "nissan",
            year_start=y1, year_end=y2,
        )

    # --- Amayama (legacy path classification for existing tests) ---
    match = PATH_RE.search(path)
    if not match:
        return HierarchyNode(url=url, level=HierarchyLevel.ROOT)

    rest = (match.group("rest") or "").strip("/")
    segments = [s for s in rest.split("/") if s]
    if not segments:
        return HierarchyNode(url=url, level=HierarchyLevel.ROOT)

    model = segments[0]
    chassis = None
    engine = None
    assembly = None
    level = HierarchyLevel.MODEL

    if len(segments) >= 2:
        chassis = segments[1].upper()
        level = HierarchyLevel.CHASSIS
    if len(segments) >= 3:
        third = segments[2]
        if ENGINE_RE.fullmatch(third):
            engine = third.upper()
            level = HierarchyLevel.ENGINE_OR_PERIOD
        else:
            # generation / period slug
            level = HierarchyLevel.ENGINE_OR_PERIOD
    if len(segments) >= 4:
        assembly = segments[3]
        level = HierarchyLevel.ASSEMBLY
    if len(segments) >= 5:
        level = HierarchyLevel.DIAGRAM

    y1, y2 = parse_year_range(page_text)
    if y1 is None and chassis:
        # year sometimes appears in path segment
        for seg in segments:
            a, b = parse_year_range(seg)
            if a is not None:
                y1, y2 = a, b
                break

    return HierarchyNode(
        url=url,
        level=level,
        model_slug=model,
        chassis_code=chassis,
        engine_code=engine,
        assembly_slug=assembly,
        year_start=y1,
        year_end=y2,
    )


def should_enqueue_child(parent: HierarchyNode, child_url: str) -> bool:
    """Prefer deeper-or-equal hierarchy under the same model lineage (no lateral jumps)."""
    child = classify_url(child_url)
    # PartSouq: opaque query tokens — scope is enforced by is_in_scope_url separately.
    if "partsouq.com" in urlparse(child_url).netloc.lower() or "partsouq.com" in urlparse(parent.url).netloc.lower():
        path = urlparse(child_url).path.lower()
        return path.startswith("/en/catalog/")
    if child.level == HierarchyLevel.ROOT and parent.level > HierarchyLevel.ROOT:
        return False
    if parent.model_slug and child.model_slug and parent.model_slug != child.model_slug:
        # Allow sibling models only from ROOT
        return parent.level == HierarchyLevel.ROOT
    if parent.chassis_code and child.chassis_code and parent.chassis_code != child.chassis_code:
        return parent.level <= HierarchyLevel.MODEL
    # Accept same level (pagination) or deeper
    return child.level >= parent.level or child.level == parent.level


def extract_page_vehicle_meta(html_or_text: str) -> dict[str, Any]:
    """Pull year range / engine tokens from visible page text."""
    y1, y2 = parse_year_range(html_or_text)
    engines = sorted(
        {
            m.group(0).upper()
            for m in re.finditer(r"\b([A-Z]{2,3}\d{2}[A-Z]{0,3})\b", html_or_text.upper())
            if ENGINE_RE.fullmatch(m.group(0))
        }
    )
    out: dict[str, Any] = {}
    if y1 is not None:
        out["year_start"] = y1
        out["year_end"] = y2
    if engines:
        out["engines_seen"] = engines[:8]
        out["engine_code"] = engines[0]
    return out

# ======================================================================
# 3. VIN DECODE
# ======================================================================

_YEAR_CODES: dict[str, int] = {
    "A": 1980,
    "B": 1981,
    "C": 1982,
    "D": 1983,
    "E": 1984,
    "F": 1985,
    "G": 1986,
    "H": 1987,
    "J": 1988,
    "K": 1989,
    "L": 1990,
    "M": 1991,
    "N": 1992,
    "P": 1993,
    "R": 1994,
    "S": 1995,
    "T": 1996,
    "V": 1997,
    "W": 1998,
    "X": 1999,
    "Y": 2000,
    "1": 2001,
    "2": 2002,
    "3": 2003,
    "4": 2004,
    "5": 2005,
    "6": 2006,
    "7": 2007,
    "8": 2008,
    "9": 2009,
}

# 30-year cycle repeats (A=2010, …). Prefer the cycle that lands in [1980, 2039].
_YEAR_CYCLE = 30

VIN_RE = re.compile(r"^[A-HJ-NPR-Z0-9]{11,17}$", re.IGNORECASE)

# Multi-make chassis→vin_prefix registry (config/chassis_catalogs.json).
# CHASSIS_CATALOG is the *active brand* curated map (compat for tests/imports).
from data_pipeline.chassis_catalog_registry import (  # noqa: E402
    active_chassis_catalog,
    chassis_from_prefix as registry_chassis_from_prefix,
    display_name as brand_display_name,
    load_catalogs as load_chassis_catalogs,
    lookup_chassis as registry_lookup_chassis,
    resolve_chassis_vin,
    set_active_brand,
)

load_chassis_catalogs()
CHASSIS_CATALOG: dict[str, dict[str, Any]] = active_chassis_catalog()


@dataclass(frozen=True)
class VinDecode:
    vin: str | None
    vin_prefix: str | None
    production_year: int | None
    chassis_code: str | None
    engine_code: str | None
    model_variant: str | None
    source: str  # local | nhtsa | hybrid | unknown
    confidence: float

    def as_hints(self) -> dict[str, Any]:
        return {
            k: v
            for k, v in {
                "vin_prefix": self.vin_prefix,
                "production_year": self.production_year,
                "chassis_code": self.chassis_code,
                "engine_code": self.engine_code,
                "model_variant": self.model_variant,
            }.items()
            if v is not None
        }

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def normalize_vin(raw: str | None) -> str | None:
    if not raw:
        return None
    cleaned = re.sub(r"[^A-Za-z0-9]", "", raw).upper()
    cleaned = cleaned.replace("I", "1").replace("O", "0").replace("Q", "0")
    if len(cleaned) < 8:
        return None
    return cleaned


def decode_model_year(vin: str, *, reference_year: int = 2026) -> int | None:
    """ISO 3779 year from position 10 (1-based). Handles 30-year cycle."""
    if len(vin) < 10:
        return None
    code = vin[9]
    base = _YEAR_CODES.get(code)
    if base is None:
        return None
    candidates = [base, base + _YEAR_CYCLE]
    if code.isalpha():
        candidates.append(base + 2 * _YEAR_CYCLE)
    # Prefer the newest year that is not far in the future
    valid = [y for y in candidates if 1980 <= y <= reference_year + 1]
    if not valid:
        return None
    return max(valid)


def vin_prefix_of(vin: str, length: int = 9) -> str:
    return vin[: min(length, len(vin))]


def refresh_chassis_catalog_view() -> None:
    """Refresh module-level CHASSIS_CATALOG after set_active_brand()."""
    global CHASSIS_CATALOG
    CHASSIS_CATALOG = active_chassis_catalog()


def chassis_from_prefix(prefix: str) -> str | None:
    return registry_chassis_from_prefix(prefix)


def lookup_chassis(chassis_code: str) -> dict[str, Any] | None:
    return registry_lookup_chassis(chassis_code)


def decode_vin_local(vin_or_prefix: str, *, engine_hint: str | None = None) -> VinDecode:
    vin = normalize_vin(vin_or_prefix)
    if not vin:
        return VinDecode(None, None, None, None, None, None, "unknown", 0.0)

    year = decode_model_year(vin) if len(vin) >= 10 else None
    prefix = vin_prefix_of(vin, min(9, len(vin)))
    chassis = chassis_from_prefix(vin)
    meta = lookup_chassis(chassis) if chassis else None
    engine = engine_hint
    if engine is None and meta:
        engines = meta.get("engines") or []
        engine = engines[0] if len(engines) == 1 else None
    model = meta["model_variant"] if meta else None
    if meta and chassis and not any(prefix.startswith(p) for p in meta["vin_prefixes"]):
        # Prefer catalog's primary prefix for garage search consistency
        prefix = meta["vin_prefixes"][0]

    confidence = 0.35
    if year:
        confidence += 0.25
    if chassis:
        confidence += 0.3
    if model:
        confidence += 0.1

    return VinDecode(
        vin=vin if len(vin) >= 11 else None,
        vin_prefix=prefix,
        production_year=year,
        chassis_code=chassis,
        engine_code=engine,
        model_variant=model,
        source="local",
        confidence=min(confidence, 1.0),
    )


def decode_from_chassis(
    chassis_code: str,
    *,
    engine_code: str | None = None,
    production_year: int | None = None,
    model_variant: str | None = None,
    year_start: int | None = None,
    year_end: int | None = None,
    example_vid: str | None = None,
    example_url: str | None = None,
    discover_unmapped: bool = True,
    brand: str | None = None,
) -> list[VinDecode]:
    """Expand chassis (+ optional year range) into vehicle decode rows for vehicle_master.

    Curated brand chassis maps supply vin_prefix when present. Otherwise the brand's
    ``epc_stub`` synthesizes ``{wmi}{chassis}`` as a garage search key. Only when
    enrichment_source is ``none`` is vin_prefix left empty and discovery recorded.
    """
    if brand:
        set_active_brand(brand)
        refresh_chassis_catalog_view()

    resolved = resolve_chassis_vin(
        chassis_code,
        brand=brand,
        model_variant=model_variant,
    )
    prefix = resolved.get("vin_prefix")
    source = resolved.get("enrichment_source") or "none"
    model = resolved.get("model_variant") or (
        f"{brand_display_name(brand)} {chassis_code.upper()}" if chassis_code else None
    )

    if source == "none" and discover_unmapped and chassis_code:
        from data_pipeline.chassis_discovery import note_unmapped_chassis

        note_unmapped_chassis(
            chassis_code,
            example_vid=example_vid,
            example_url=example_url,
            model_variant=model_variant,
        )
    elif source == "epc_stub" and discover_unmapped and chassis_code:
        # Still surface for curation — stub is searchable but not market-verified.
        from data_pipeline.chassis_discovery import note_unmapped_chassis

        note_unmapped_chassis(
            chassis_code,
            example_vid=example_vid,
            example_url=example_url,
            model_variant=model_variant or model,
        )

    engines = [engine_code] if engine_code else (resolved.get("engines") or [None])
    if not engines:
        engines = [None]

    years: list[int | None]
    if production_year is not None:
        years = [production_year]
    elif year_start is not None and year_end is not None and year_start <= year_end:
        span = min(year_end, year_start + 40)
        years = list(range(year_start, span + 1))
    elif resolved.get("year_range"):
        lo, hi = resolved["year_range"]
        years = [(int(lo) + int(hi)) // 2]
    else:
        years = [None]

    confidence = 0.7 if source == "curated" and prefix else (0.55 if prefix else 0.5)
    rows: list[VinDecode] = []
    for year in years:
        for eng in engines if engine_code else [engine_code]:
            rows.append(
                VinDecode(
                    vin=None,
                    vin_prefix=prefix,
                    production_year=year,
                    chassis_code=chassis_code.upper(),
                    engine_code=eng,
                    model_variant=model,
                    source="local" if source == "curated" else ("epc_stub" if prefix else "unknown"),
                    confidence=confidence,
                )
            )
            if not engine_code:
                break  # one engine slot when unknown
    return rows


def enrich_hints_with_vin(
    hints: dict[str, Any],
    *,
    vin: str | None = None,
) -> dict[str, Any]:
    """Merge VIN decode into scrape/transform vehicle hints (VIN wins on conflicts)."""
    merged = dict(hints)
    # Discovery context only — strip before returning so vehicle payloads stay clean
    example_url = merged.pop("source_url", None) or merged.pop("url", None)
    example_vid = merged.get("vid")

    if vin:
        decoded = decode_vin_local(vin, engine_hint=hints.get("engine_code"))
        for key, value in decoded.as_hints().items():
            if value is not None:
                merged[key] = value
        return merged

    chassis = merged.get("chassis_code")
    if chassis:
        rows = decode_from_chassis(
            chassis,
            engine_code=merged.get("engine_code"),
            production_year=merged.get("production_year"),
            model_variant=merged.get("model_variant"),
            year_start=merged.get("year_start"),
            year_end=merged.get("year_end"),
            example_vid=str(example_vid) if example_vid else None,
            example_url=str(example_url) if example_url else None,
        )
        if rows:
            primary = rows[0]
            for key, value in primary.as_hints().items():
                merged.setdefault(key, value)
            if len(rows) > 1:
                merged["_year_expand"] = [
                    r.production_year for r in rows if r.production_year is not None
                ]
                merged["_vin_prefix"] = primary.vin_prefix
    return merged


async def enrich_nhtsa(vin: str) -> dict[str, Any]:
    """Optional NHTSA vPIC enrichment (network). Returns sparse make/model/year dict."""
    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError("httpx required for NHTSA enrichment") from exc

    cleaned = normalize_vin(vin)
    if not cleaned or len(cleaned) < 17:
        return {}

    url = (
        "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/"
        f"{cleaned}?format=json"
    )
    async with httpx.AsyncClient(timeout=20.0) as client:
        response = await client.get(url)
        response.raise_for_status()
        data = response.json()
    results = data.get("Results") or []
    if not results:
        return {}
    row = results[0]
    out: dict[str, Any] = {"source": "nhtsa"}
    make = (row.get("Make") or "").strip()
    model = (row.get("Model") or "").strip()
    year = row.get("ModelYear")
    if make and model:
        out["model_variant"] = f"{make} {model}".strip()
    if year and str(year).isdigit():
        out["production_year"] = int(year)
    return out


def decode_vin(
    vin_or_prefix: str,
    *,
    engine_hint: str | None = None,
    nhtsa: dict[str, Any] | None = None,
) -> VinDecode:
    """Local decode, optionally merged with a pre-fetched NHTSA payload."""
    local = decode_vin_local(vin_or_prefix, engine_hint=engine_hint)
    if not nhtsa:
        return local
    return VinDecode(
        vin=local.vin,
        vin_prefix=local.vin_prefix,
        production_year=local.production_year or nhtsa.get("production_year"),
        chassis_code=local.chassis_code,
        engine_code=local.engine_code,
        model_variant=local.model_variant or nhtsa.get("model_variant"),
        source="hybrid" if local.chassis_code else "nhtsa",
        confidence=min(1.0, local.confidence + 0.15),
    )

# ======================================================================
# 4. TRANSFORM → SCHEMA BUNDLE
# ======================================================================

OEM_RE = re.compile(r"^[0-9A-Z]{5}-[0-9A-Z]{5}$")
# Classic Nissan FAST PNCs are 5 digits; PartSouq code-on-image may be
# alphanumeric (e.g. C8320, 16132PA). Keep within DB VARCHAR(8).
PNC_RE = re.compile(r"^[0-9A-Z]{2,8}$")
PNC_DIGITS_RE = re.compile(r"^[0-9]{5}$")
_PNC_LABELED_DIGITS_RE = re.compile(r"^[A-Z]{1,3}([0-9]{5})$")


def _omit_none(row: dict[str, Any]) -> dict[str, Any]:
    return {k: v for k, v in row.items() if v is not None}


def normalize_oem(raw: str | None) -> str | None:
    """Normalize OEM to Nissan `XXXXX-XXXXX` form, or None if unusable."""
    if not raw:
        return None
    cleaned = re.sub(r"[^0-9A-Za-z]", "", str(raw)).upper()
    if len(cleaned) == 10:
        candidate = f"{cleaned[:5]}-{cleaned[5:]}"
    else:
        candidate = str(raw).strip().upper()
    if OEM_RE.match(candidate):
        return candidate
    return None


def normalize_pnc(raw: Any, *, oem: str | None = None) -> str | None:
    """Normalize a PNC / PartSouq code-on-image to a schema-valid code.

    Preserves classic 5-digit Nissan PNCs (including ``PNC-15208`` labels) and
    PartSouq alphanumeric codes such as ``C8320`` / ``16132PA``.
    """
    if raw is not None:
        alnum = re.sub(r"[^0-9A-Za-z]", "", str(raw)).upper()
        if alnum.isdigit() and len(alnum) >= 5:
            return alnum[:5]
        labeled = _PNC_LABELED_DIGITS_RE.fullmatch(alnum)
        if labeled:
            return labeled.group(1)
        if PNC_RE.match(alnum) and re.search(r"[A-Z]", alnum):
            return alnum
        if PNC_DIGITS_RE.match(alnum):
            return alnum
    if oem and OEM_RE.match(oem):
        prefix = oem.split("-", 1)[0]
        if PNC_RE.match(prefix):
            return prefix
    return None


def _as_float(value: Any) -> float | None:
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def extract_bbox(item: dict[str, Any]) -> dict[str, float] | None:
    """Extract normalized 0–1 bbox from common hotspot shapes."""
    if all(k in item for k in ("bbox_x", "bbox_y", "bbox_width", "bbox_height")):
        x = _as_float(item["bbox_x"])
        y = _as_float(item["bbox_y"])
        w = _as_float(item["bbox_width"])
        h = _as_float(item["bbox_height"])
        if None not in (x, y, w, h):
            return {"bbox_x": x, "bbox_y": y, "bbox_width": w, "bbox_height": h}

    # left/top/right/bottom or x1/y1/x2/y2
    left = _as_float(item.get("left", item.get("x1", item.get("x"))))
    top = _as_float(item.get("top", item.get("y1", item.get("y", item.get("pos_y")))))
    right = _as_float(item.get("right", item.get("x2")))
    bottom = _as_float(item.get("bottom", item.get("y2")))
    width = _as_float(item.get("width", item.get("w")))
    height = _as_float(item.get("height", item.get("h")))
    pos_x = _as_float(item.get("pos_x"))

    if left is None and pos_x is not None:
        left = pos_x
    if top is None:
        top = _as_float(item.get("pos_y"))

    if left is not None and top is not None and right is not None and bottom is not None:
        width = right - left
        height = bottom - top
    if left is None or top is None or width is None or height is None:
        return None

    # Heuristic: values > 1 are pixel or percent coords — normalize if image size known
    img_w = _as_float(item.get("image_width", item.get("img_width", item.get("canvas_width"))))
    img_h = _as_float(item.get("image_height", item.get("img_height", item.get("canvas_height"))))

    coords = [left, top, width, height]
    if any(c > 1.0 for c in coords):
        if img_w and img_h and img_w > 0 and img_h > 0:
            left, width = left / img_w, width / img_w
            top, height = top / img_h, height / img_h
        elif all(c <= 100.0 for c in coords):
            # percent
            left, top, width, height = left / 100.0, top / 100.0, width / 100.0, height / 100.0
        else:
            # Unknown absolute coords — keep relative if we can clamp later; skip if huge
            return None

    return {
        "bbox_x": left,
        "bbox_y": top,
        "bbox_width": width,
        "bbox_height": height,
    }


def vehicle_context_from_url(url: str) -> dict[str, Any]:
    """Best-effort model/chassis extraction from Amayama catalog paths."""

    return classify_url(url).vehicle_hints()


def _parts_list(payload: dict[str, Any]) -> list[dict[str, Any]]:
    for key in ("parts", "hotspots", "items", "data"):
        value = payload.get(key)
        if isinstance(value, list):
            return [v for v in value if isinstance(v, dict)]
        if isinstance(value, dict) and isinstance(value.get("parts"), list):
            return [v for v in value["parts"] if isinstance(v, dict)]
    return []


def _image_url(payload: dict[str, Any]) -> str | None:
    for key in ("image_url", "image", "diagram_url", "img", "src"):
        value = payload.get(key)
        if isinstance(value, str) and value.startswith("http"):
            return value
        if isinstance(value, dict):
            nested = value.get("url") or value.get("src")
            if isinstance(nested, str) and nested.startswith("http"):
                return nested
    return None


def _content_type_from_url(url: str) -> str:
    lower = url.lower()
    if lower.endswith(".gif"):
        return "image/gif"
    if lower.endswith((".jpg", ".jpeg")):
        return "image/jpeg"
    if lower.endswith(".webp"):
        return "image/webp"
    if lower.endswith(".svg"):
        return "image/svg+xml"
    return "image/png"


def _content_type_for_diagram(*, url: str = "", storage_path: str = "", declared: str = "") -> str:
    """Resolve MIME type for PartSouq GIF/PNG diagram uploads."""
    if declared and declared != "image/png":
        return declared
    for hint in (storage_path, url):
        lower = hint.lower()
        if lower.endswith(".gif"):
            return "image/gif"
        if lower.endswith((".jpg", ".jpeg")):
            return "image/jpeg"
        if lower.endswith(".webp"):
            return "image/webp"
    return _content_type_from_url(url)


def _storage_path(prefix: str, image_url: str, chassis: str | None) -> str:
    filename = image_url.rstrip("/").split("/")[-1].split("?")[0] or "diagram.png"
    chassis_seg = (chassis or "unknown").lower()
    return f"{prefix.rstrip('/')}/{chassis_seg}/{filename}"


def is_catalog_payload(data: Any) -> bool:
    if not isinstance(data, dict):
        return False
    if _parts_list(data):
        return True
    blob = str(data).lower()
    return "pnc" in blob and any(k in blob for k in ("part", "hotspot", "bbox", "coord", "pos_x"))


def _pnc_category_rank(name: str | None) -> int:
    """Higher = better EPC group label for ``pnc_categories.category_name``."""
    if not name or not str(name).strip():
        return 0
    cleaned = str(name).strip()
    lower = cleaned.lower()
    if lower in {"uncategorized", "unknown", "misc", "other"}:
        return 0
    if is_generic_part_name(cleaned):
        return 1
    return 2


def _resolve_pnc_category(
    *,
    payload: dict[str, Any],
    source_url: str,
    item: dict[str, Any],
) -> tuple[str, str | None]:
    """Resolve EPC assembly group + unit name; never use fastener labels as category."""
    url_hints = category_hints_from_url(source_url)
    diagram_hints = assembly_hints_from_diagram_title(str(payload.get("diagram_title") or ""))
    payload_cat = (
        payload.get("category_name")
        or url_hints.get("category_name")
        or diagram_hints.get("category_name")
    )
    payload_sub = (
        payload.get("subcategory_name")
        or url_hints.get("subcategory_name")
        or diagram_hints.get("subcategory_name")
    )
    if not payload_sub:
        unit = diagram_hints.get("category_name")
        if unit and unit != payload_cat:
            payload_sub = unit
    item_cat = item.get("category_name") or item.get("category") or item.get("group_name")
    if item_cat and not is_generic_part_name(str(item_cat)):
        payload_cat = payload_cat or item_cat
    item_sub = item.get("subcategory_name") or item.get("subcategory")
    if item_sub and not is_generic_part_name(str(item_sub)):
        payload_sub = payload_sub or item_sub

    category = str(payload_cat).strip() if payload_cat else "Uncategorized"
    category = normalize_epc_category_name(category) or "Uncategorized"
    subcategory = str(payload_sub).strip() if payload_sub else None
    subcategory = normalize_epc_category_name(subcategory)
    return category[:200] or "Uncategorized", subcategory


def transform_payload(
    payload: dict[str, Any],
    *,
    source_url: str = "",
    diagram_storage_prefix: str = "partsouq/nissan",
    vehicle_hints: dict[str, Any] | None = None,
) -> dict[str, list[dict[str, Any]]]:
    """Map one Amayama JSON payload into (possibly partial) catalog tables."""

    vehicle_obj = payload.get("vehicle") if isinstance(payload.get("vehicle"), dict) else {}
    raw_hints = {
        **(vehicle_context_from_url(source_url) if source_url else {}),
        **(vehicle_obj or {}),
        **(vehicle_hints or {}),
    }
    vin_from_payload: str | None = None
    if isinstance(vehicle_obj, dict) and isinstance(vehicle_obj.get("vin"), str):
        vin_from_payload = vehicle_obj["vin"]
    for key in ("vin", "VIN", "vin_number"):
        value = payload.get(key)
        if isinstance(value, str) and value.strip():
            vin_from_payload = value
            break
    hints = enrich_hints_with_vin(raw_hints, vin=vin_from_payload)

    chassis = hints.get("chassis_code")
    # PartSouq pages often lack chassis in the URL; fall back so diagrams/fitments emit.
    if not chassis and hints.get("model_variant"):
        chassis = str(hints["model_variant"]).strip()
    engine = hints.get("engine_code") or (vehicle_obj or {}).get("engine_code")
    model_variant = hints.get("model_variant") or "Nissan"

    vehicles: list[dict[str, Any]] = []
    if chassis:
        year_expand = hints.get("_year_expand")
        if isinstance(year_expand, list) and year_expand:
            for year in year_expand:
                vehicles.append(
                    _omit_none(
                        {
                            "vin_prefix": hints.get("vin_prefix") or hints.get("_vin_prefix"),
                            "chassis_code": chassis,
                            "engine_code": engine,
                            "production_year": year,
                            "model_variant": model_variant,
                        }
                    )
                )
        else:
            decoded_rows = decode_from_chassis(
                chassis,
                engine_code=engine,
                production_year=hints.get("production_year"),
                model_variant=model_variant,
                year_start=hints.get("year_start"),
                year_end=hints.get("year_end"),
            )
            for row in decoded_rows:
                vehicles.append(_omit_none(row.as_hints() | {"model_variant": model_variant}))

    pnc_map: dict[str, dict[str, Any]] = {}
    fitments: list[dict[str, Any]] = []
    diagrams: list[dict[str, Any]] = []
    oem_display_names: dict[str, str] = {}

    image_url = _image_url(payload)
    diagram_path = (
        _storage_path(diagram_storage_prefix, image_url, chassis) if image_url else None
    )

    parts = _parts_list(payload)
    # Propagate image dimensions onto parts for bbox normalization
    img_meta = {
        "image_width": payload.get("image_width") or payload.get("width"),
        "image_height": payload.get("image_height") or payload.get("height"),
    }

    for item in parts:
        oem = normalize_oem(
            item.get("oem_part_number")
            or item.get("oem_number")
            or item.get("part_number")
            or item.get("number")
            or item.get("oem")
        )
        if not oem:
            continue
        pnc = normalize_pnc(
            item.get("pnc") or item.get("pnc_code") or item.get("code"),
            oem=oem,
        )
        if not pnc:
            continue

        part_name = (
            item.get("description")
            or item.get("name")
            or item.get("part_name")
            or ""
        )
        part_name = str(part_name).strip()
        if part_name:
            oem_display_names[oem] = part_name[:200]

        category, subcategory = _resolve_pnc_category(
            payload=payload, source_url=source_url, item=item
        )
        if pnc not in pnc_map:
            pnc_map[pnc] = _omit_none(
                {
                    "pnc_code": pnc,
                    "category_name": category,
                    "subcategory_name": subcategory,
                }
            )
        else:
            existing = pnc_map[pnc]
            if _pnc_category_rank(category) > _pnc_category_rank(
                existing.get("category_name")
            ):
                existing["category_name"] = category
            if subcategory and not existing.get("subcategory_name"):
                existing["subcategory_name"] = subcategory

        merged = {**img_meta, **item}
        bbox = extract_bbox(merged) or {}
        fitments.append(
            _omit_none(
                {
                    "oem_part_number": oem,
                    "pnc_code": pnc,
                    "chassis_code": chassis,
                    "engine_code": engine,
                    "superseded_by": normalize_oem(item.get("superseded_by")),
                    "diagram_path": diagram_path,
                    **bbox,
                }
            )
        )

    if diagram_path and chassis and pnc_map:
        first_pnc = next(iter(pnc_map))
        diagrams.append(
            _omit_none(
                {
                    "storage_path": diagram_path,
                    "pnc_code": first_pnc,
                    "chassis_code": chassis,
                    "engine_code": engine,
                    "content_type": _content_type_from_url(image_url or ""),
                    "source_url": image_url or source_url or None,
                    "provenance": "scraped-reference",
                }
            )
        )

    return {
        "vehicle_master": vehicles,
        "pnc_categories": list(pnc_map.values()),
        "part_fitment": fitments,
        "diagram_assets": diagrams,
        "_oem_display_names": oem_display_names,
    }


def merge_bundles(bundles: list[dict[str, list[dict[str, Any]]]]) -> dict[str, list[dict[str, Any]]]:
    """Deduplicate table rows by natural keys."""
    vehicles: dict[tuple, dict[str, Any]] = {}
    pncs: dict[str, dict[str, Any]] = {}
    fitments: dict[tuple, dict[str, Any]] = {}
    diagrams: dict[str, dict[str, Any]] = {}
    oem_display_names: dict[str, str] = {}

    for bundle in bundles:
        for oem, name in (bundle.get("_oem_display_names") or {}).items():
            if oem and name:
                oem_display_names[str(oem)] = str(name)
        for row in bundle.get("vehicle_master", []):
            key = (
                row.get("vin_prefix"),
                row.get("chassis_code"),
                row.get("engine_code"),
                row.get("production_year"),
                row.get("model_variant"),
            )
            vehicles[key] = row
        for row in bundle.get("pnc_categories", []):
            code = row["pnc_code"]
            prev = pncs.get(code)
            if prev is None or _pnc_category_rank(row.get("category_name")) > _pnc_category_rank(
                prev.get("category_name")
            ):
                pncs[code] = row
            elif prev is not None and not prev.get("subcategory_name") and row.get(
                "subcategory_name"
            ):
                prev["subcategory_name"] = row["subcategory_name"]
        for row in bundle.get("part_fitment", []):
            key = (
                row.get("oem_part_number"),
                row.get("chassis_code"),
                row.get("engine_code"),
                row.get("pnc_code"),
            )
            fitments[key] = row
        for row in bundle.get("diagram_assets", []):
            diagrams[row["storage_path"]] = row

    merged: dict[str, Any] = {
        "vehicle_master": list(vehicles.values()),
        "pnc_categories": list(pncs.values()),
        "part_fitment": list(fitments.values()),
        "diagram_assets": list(diagrams.values()),
    }
    if oem_display_names:
        merged["_oem_display_names"] = oem_display_names
    return merged


def transform_raw_records(
    records: list[tuple[str, dict[str, Any]]]
    | list[tuple[str, dict[str, Any], dict[str, Any] | None]],
    *,
    diagram_storage_prefix: str = "partsouq/nissan",
    validate: bool = True,
) -> dict[str, list[dict[str, Any]]]:
    """Transform scraped records into one validated catalog bundle.

    Each record is ``(source_url, payload)`` or ``(source_url, payload, vehicle_context)``.
    """
    partials: list[dict[str, list[dict[str, Any]]]] = []
    for record in records:
        source_url = record[0]
        payload = record[1]
        context = record[2] if len(record) >= 3 else None  # type: ignore[misc]
        if not is_catalog_payload(payload):
            continue
        hints = context if isinstance(context, dict) else None
        partials.append(
            transform_payload(
                payload,
                source_url=source_url,
                diagram_storage_prefix=diagram_storage_prefix,
                vehicle_hints=hints,
            )
        )
    bundle = merge_bundles(partials)
    if validate:
        validate_bundle(bundle)
    return bundle


__all__ = [
    "extract_bbox",
    "is_catalog_payload",
    "merge_bundles",
    "normalize_oem",
    "normalize_pnc",
    "transform_payload",
    "transform_raw_records",
    "vehicle_context_from_url",
    "write_bundle",
]

# ======================================================================
# 5. CRAWLER + CHECKPOINT + IMPORT CLI (Patchright)
# ======================================================================

def init_db(db_path: Path) -> None:
    db_path = Path(db_path)
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists() and db_path.stat().st_size == 0:
        try:
            db_path.unlink()
        except OSError:
            pass
        for suffix in ("-wal", "-shm", "-journal"):
            side = Path(str(db_path) + suffix)
            if side.exists():
                try:
                    side.unlink()
                except OSError:
                    pass
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS queue (
                url TEXT PRIMARY KEY,
                status TEXT NOT NULL DEFAULT 'PENDING',
                attempts INTEGER NOT NULL DEFAULT 0,
                hierarchy_level INTEGER NOT NULL DEFAULT 0,
                vehicle_context TEXT,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS scraped_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_url TEXT NOT NULL,
                payload TEXT NOT NULL,
                vehicle_context TEXT,
                captured_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """
        )
        # Migrate older DBs created before attempts/context columns
        cols = {row[1] for row in conn.execute("PRAGMA table_info(queue)").fetchall()}
        if "attempts" not in cols:
            conn.execute("ALTER TABLE queue ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0")
        if "hierarchy_level" not in cols:
            conn.execute(
                "ALTER TABLE queue ADD COLUMN hierarchy_level INTEGER NOT NULL DEFAULT 0"
            )
        if "vehicle_context" not in cols:
            conn.execute("ALTER TABLE queue ADD COLUMN vehicle_context TEXT")
        scraped_cols = {
            row[1] for row in conn.execute("PRAGMA table_info(scraped_data)").fetchall()
        }
        if "vehicle_context" not in scraped_cols:
            conn.execute("ALTER TABLE scraped_data ADD COLUMN vehicle_context TEXT")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_queue_status ON queue(status)")
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_queue_level ON queue(hierarchy_level, status)"
        )
        conn.commit()
    finally:
        conn.close()


def enqueue_url(
    db_path: Path,
    url: str,
    *,
    hierarchy_level: int = 0,
    vehicle_context: dict[str, Any] | None = None,
) -> bool:
    url = fully_unescape(url)
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        try:
            conn.execute(
                """
                INSERT INTO queue (url, hierarchy_level, vehicle_context)
                VALUES (?, ?, ?)
                """,
                (
                    url,
                    hierarchy_level,
                    json.dumps(vehicle_context) if vehicle_context else None,
                ),
            )
            conn.commit()
            return True
        except sqlite3.IntegrityError:
            return False
    finally:
        conn.close()


def _pending_queue_order_sql(mode: str) -> tuple[str, tuple[Any, ...]]:
    """Return (ORDER BY clause suffix, extra params) for PENDING candidate scan."""
    chassis_level = int(HierarchyLevel.CHASSIS)
    if mode == "hybrid":
        return (
            """
            ORDER BY
              CASE
                WHEN hierarchy_level = ? OR url LIKE '%/vehicle%' THEN 0
                ELSE 1
              END ASC,
              hierarchy_level DESC,
              attempts ASC,
              rowid ASC
            """,
            (chassis_level,),
        )
    order = "DESC" if mode in {"deep_first", "hybrid"} else "ASC"
    return (f"ORDER BY hierarchy_level {order}, attempts ASC, rowid ASC", ())


def claim_next_url(
    db_path: Path,
    *,
    deep_first: bool = False,
    mode: str | None = None,
    priority: Any | None = None,
) -> tuple[str, dict[str, Any]] | None:
    """Atomically claim one PENDING URL.

    Modes:
      - bfs: lowest hierarchy_level first
      - deep_first: highest hierarchy_level first (parts/units)
      - hybrid: prefer vehicle/L2 (CHASSIS) pages while any remain, else deep-first

    When *priority* (:class:`~data_pipeline.priority_chassis.PriorityChassisFilter`)
    is set, only bootstrap navigation, ``/vehicle`` discovery, and URLs whose
    ``vehicle_context`` / ``vid`` map to a priority chassis are claimed. Other
    PENDING rows stay queued for a later full crawl.

    ``deep_first=True`` is kept for callers/tests; ``mode`` overrides when set.
    Follow-up (not here): prefer one URL per distinct ``vid`` when claiming vehicles
    (ssd-variant dedup against VISITED / identity).
    """
    from data_pipeline.priority_chassis import PriorityChassisFilter, _parse_vehicle_context

    resolved = (mode or "").strip().lower()
    if resolved not in {"deep_first", "bfs", "hybrid"}:
        resolved = "deep_first" if deep_first else "bfs"

    order_sql, order_params = _pending_queue_order_sql(resolved)
    batch_size = 500
    max_scan = 50_000 if priority is not None else batch_size
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        conn.isolation_level = None
        conn.execute("BEGIN IMMEDIATE")
        pf: PriorityChassisFilter | None = priority if isinstance(priority, PriorityChassisFilter) else None
        row = None
        offset = 0
        while offset < max_scan:
            rows = conn.execute(
                f"""
                SELECT url, vehicle_context, attempts, hierarchy_level
                FROM queue
                WHERE status = 'PENDING'
                {order_sql}
                LIMIT ? OFFSET ?
                """,
                (*order_params, batch_size, offset),
            ).fetchall()
            if not rows:
                break
            for candidate in rows:
                url, ctx_raw, _attempts, level = candidate
                if pf is not None:
                    ctx = _parse_vehicle_context(ctx_raw)
                    if not pf.is_eligible(url, ctx, hierarchy_level=int(level)):
                        continue
                row = candidate
                break
            if row is not None:
                break
            offset += batch_size
        if not row:
            conn.execute("COMMIT")
            return None
        url, ctx_raw, _attempts, _level = row
        cur = conn.execute(
            """
            UPDATE queue
            SET status = 'PROCESSING',
                attempts = attempts + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE url = ? AND status = 'PENDING'
            """,
            (url,),
        )
        if cur.rowcount != 1:
            conn.execute("COMMIT")
            return None
        conn.execute("COMMIT")
        context: dict[str, Any] = {}
        if ctx_raw:
            try:
                loaded = json.loads(ctx_raw)
                if isinstance(loaded, dict):
                    context = loaded
            except json.JSONDecodeError:
                pass
        return url, context
    except Exception:
        conn.execute("ROLLBACK")
        raise
    finally:
        conn.close()


def queue_status_counts(db_path: Path) -> dict[str, int]:
    init_db(db_path)
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        rows = conn.execute(
            "SELECT status, COUNT(*) FROM queue GROUP BY status"
        ).fetchall()
        return {str(status): int(n) for status, n in rows}
    finally:
        conn.close()


def set_url_status(db_path: Path, url: str, status: str) -> None:
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        conn.execute(
            """
            UPDATE queue
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE url = ?
            """,
            (status, url),
        )
        conn.commit()
    finally:
        conn.close()


def mark_visit_result(
    db_path: Path,
    url: str,
    *,
    ok: bool,
    max_attempts: int,
) -> str:
    """Mark VISITED, re-queue as PENDING, or FAILED after max attempts."""
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        row = conn.execute(
            "SELECT attempts FROM queue WHERE url = ?", (url,)
        ).fetchone()
        attempts = int(row[0]) if row else max_attempts
        if ok:
            status = "VISITED"
        elif attempts < max_attempts:
            status = "PENDING"
            logger.warning(
                "Re-queueing %s after failure (attempt %s/%s)",
                url,
                attempts,
                max_attempts,
            )
        else:
            status = "FAILED"
            logger.error("Permanently failed %s after %s attempts", url, attempts)
        conn.execute(
            """
            UPDATE queue
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE url = ?
            """,
            (status, url),
        )
        conn.commit()
        return status
    finally:
        conn.close()


def requeue_failed(db_path: Path) -> int:
    """Reset FAILED / stuck / CF-blocked URLs back to PENDING for another pass."""
    reclaim_stale_processing(db_path)
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        cur = conn.execute(
            """
            UPDATE queue
            SET status = 'PENDING', updated_at = CURRENT_TIMESTAMP
            WHERE status IN ('FAILED', 'PROCESSING', 'BLOCKED_CF')
            """
        )
        conn.commit()
        return int(cur.rowcount)
    finally:
        conn.close()


def reclaim_stale_processing(db_path: Path, *, stale_seconds: float = 900.0) -> int:
    """Re-queue PROCESSING rows left by a crashed worker (older than ``stale_seconds``)."""
    if stale_seconds <= 0:
        return 0
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        cur = conn.execute(
            """
            UPDATE queue
            SET status = 'PENDING', updated_at = CURRENT_TIMESTAMP
            WHERE status = 'PROCESSING'
              AND updated_at < datetime('now', ?)
            """,
            (f"-{int(stale_seconds)} seconds",),
        )
        conn.commit()
        n = int(cur.rowcount)
        if n:
            logger.info("Reclaimed %s stale PROCESSING URLs (>%ss)", n, int(stale_seconds))
        return n
    finally:
        conn.close()


def store_payload(
    db_path: Path,
    source_url: str,
    payload: dict[str, Any],
    vehicle_context: dict[str, Any] | None = None,
) -> None:
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        conn.execute(
            """
            INSERT INTO scraped_data (source_url, payload, vehicle_context)
            VALUES (?, ?, ?)
            """,
            (
                source_url,
                json.dumps(payload),
                json.dumps(vehicle_context) if vehicle_context else None,
            ),
        )
        conn.commit()
    finally:
        conn.close()


def load_scraped_records(
    db_path: Path,
) -> list[tuple[str, dict[str, Any], dict[str, Any] | None]]:
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        rows = conn.execute(
            "SELECT source_url, payload, vehicle_context FROM scraped_data ORDER BY id"
        ).fetchall()
    finally:
        conn.close()
    out: list[tuple[str, dict[str, Any], dict[str, Any] | None]] = []
    for source_url, raw, ctx_raw in rows:
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            continue
        if not isinstance(payload, dict):
            continue
        context = None
        if ctx_raw:
            try:
                loaded = json.loads(ctx_raw)
                if isinstance(loaded, dict):
                    context = loaded
            except json.JSONDecodeError:
                context = None
        out.append((source_url, payload, context))
    return out


def pending_count(db_path: Path) -> int:
    init_db(db_path)
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        row = conn.execute(
            "SELECT COUNT(*) FROM queue WHERE status = 'PENDING'"
        ).fetchone()
        return int(row[0]) if row else 0
    finally:
        conn.close()


def write_checkpoint(
    *,
    db_path: Path,
    out_dir: Path,
    config: ScrapeConfig,
    pages_done: int,
) -> Path:
    """Persist a schema-valid mid-crawl bundle under out_dir/checkpoints/."""
    records = load_scraped_records(db_path)
    bundle = transform_raw_records(
        records,
        diagram_storage_prefix=config.diagram_storage_prefix,
        validate=True,
    )
    checkpoint_dir = out_dir / "checkpoints" / f"page-{pages_done:05d}"
    write_bundle(bundle, checkpoint_dir)
    # Also refresh the live bundle at out_dir root for resume consumers
    write_bundle(bundle, out_dir)
    meta = {
        "pages_done": pages_done,
        "pending": pending_count(db_path),
        "vehicles": len(bundle["vehicle_master"]),
        "fitments": len(bundle["part_fitment"]),
        "diagrams": len(bundle["diagram_assets"]),
    }
    (checkpoint_dir / "checkpoint_meta.json").write_text(
        json.dumps(meta, indent=2) + "\n", encoding="utf-8"
    )
    logger.info("Checkpoint written to %s (%s)", checkpoint_dir, meta)
    return checkpoint_dir


# ---------------------------------------------------------------------------
# Asset download
# ---------------------------------------------------------------------------


async def download_diagram(
    image_url: str,
    dest: Path,
    *,
    config: ScrapeConfig,
    rate_limiter: RateLimiter,
) -> Path | None:
    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError(
            "httpx required for diagram download. "
            "pip install -e '.[scraping]'"
        ) from exc

    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        return dest

    async def _get():
        await rate_limiter.wait()
        async with httpx.AsyncClient(
            headers={"User-Agent": config.user_agent},
            timeout=30.0,
            follow_redirects=True,
        ) as client:
            response = await client.get(image_url)
            response.raise_for_status()
            dest.write_bytes(response.content)
            return dest

    try:
        return await retry_async(
            _get,
            max_retries=config.max_retries,
            base=config.backoff_base_seconds,
            maximum=config.backoff_max_seconds,
            label=f"download {image_url}",
        )
    except Exception as exc:  # noqa: BLE001
        logger.error("Diagram download failed for %s: %s", image_url, exc)
        return None


def upload_diagram_supabase(
    local_path: Path,
    storage_path: str,
    *,
    bucket: str,
    content_type: str,
) -> None:
    url, key = resolve_supabase_credentials()
    if not url or not key:
        logger.warning("Skipping Supabase upload — credentials not set")
        return
    try:
        from supabase import ClientOptions, create_client
    except ImportError as exc:
        raise RuntimeError("pip install -e '.[supabase]'") from exc

    client = create_client(
        url,
        key,
        options=ClientOptions(storage_client_timeout=120),
    )
    try:
        client.storage.from_(bucket).upload(
            path=storage_path,
            file=local_path.read_bytes(),
            file_options={"content-type": content_type, "upsert": "true"},
        )
    except Exception as exc:  # noqa: BLE001
        logger.error("Supabase upload failed for %s: %s", storage_path, exc)
        raise


def fully_unescape(text: str) -> str:
    """Decode nested HTML entities (&amp;amp; → &)."""
    import html as html_lib

    prev = None
    cur = text
    while prev != cur:
        prev = cur
        cur = html_lib.unescape(cur)
    return cur


def is_in_scope_url(url: str, config: ScrapeConfig) -> bool:
    """English + brand scope gate (PartSouq: /en/catalog/… with c=Nissan*)."""
    url = fully_unescape(url)
    parsed = urlparse(url)
    base = urlparse(config.base_url)
    host = (parsed.netloc or base.netloc or "").lower()
    base_host = (base.netloc or "").lower()
    if parsed.netloc and host != base_host:
        return False

    path = parsed.path or ""
    needle = config.allowed_path_substring or "/en/catalog/"
    if needle not in path:
        return False

    locale = (config.allowed_locale or "en").strip().lower() or "en"
    brand = (config.allowed_brand or "nissan").strip().lower() or "nissan"

    if "partsouq.com" in host or "partsouq.com" in base_host:
        if not path.lower().startswith(f"/{locale}/"):
            return False
        # Reject other language mirrors even if substring somehow matched
        if re.match(r"^/(es|fr|de|ar|ru|ja|zh|pt|it)/", path, flags=re.I):
            return False
        c_vals = [v.strip().lower() for v in parse_qs(parsed.query).get("c", []) if v]
        if c_vals:
            return any(v == brand or v.startswith(brand) for v in c_vals)
        # No brand query — only allow if brand is explicit in the path
        return brand in path.lower()

    # Amayama / other: path substring is enough
    return True


def prune_out_of_scope_queue(db_path: Path, config: ScrapeConfig) -> int:
    """Delete queued/visited URLs outside English+brand scope."""
    init_db(db_path)
    conn = sqlite3.connect(db_path, timeout=30.0)
    try:
        rows = conn.execute("SELECT url FROM queue").fetchall()
        drop = [url for (url,) in rows if not is_in_scope_url(url, config)]
        for url in drop:
            conn.execute("DELETE FROM queue WHERE url = ?", (url,))
        conn.commit()
        return len(drop)
    finally:
        conn.close()


def extract_hrefs_from_html(html: str, base_url: str) -> list[str]:
    """Pull absolute http(s) links from HTML without BeautifulSoup."""
    found: list[str] = []
    for match in re.finditer(r'''href\s*=\s*["']([^"']+)["']''', html, flags=re.I):
        href = fully_unescape(match.group(1).strip())
        if not href or href.startswith(("#", "javascript:", "mailto:")):
            continue
        found.append(urljoin(base_url, href))
    return found


def extract_embedded_json_payloads(html: str) -> list[dict[str, Any]]:
    """Best-effort extraction of catalog-like JSON blobs from script tags / literals."""
    payloads: list[dict[str, Any]] = []
    # application/json script tags
    for match in re.finditer(
        r'<script[^>]*type=["\']application/json["\'][^>]*>(.*?)</script>',
        html,
        flags=re.I | re.S,
    ):
        raw = match.group(1).strip()
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            continue
        if isinstance(data, dict) and is_catalog_payload(data):
            payloads.append(data)
        elif isinstance(data, list):
            for item in data:
                if isinstance(item, dict) and is_catalog_payload(item):
                    payloads.append(item)
    # Common window.__NUXT__ / __NEXT_DATA__ style
    for match in re.finditer(
        r'<script[^>]*id=["\']__NEXT_DATA__["\'][^>]*>(.*?)</script>',
        html,
        flags=re.I | re.S,
    ):
        try:
            data = json.loads(match.group(1).strip())
        except json.JSONDecodeError:
            continue
        if isinstance(data, dict):
            # walk shallow for catalog-ish nodes
            stack = [data]
            while stack:
                node = stack.pop()
                if isinstance(node, dict):
                    if is_catalog_payload(node):
                        payloads.append(node)
                    stack.extend(node.values())
                elif isinstance(node, list):
                    stack.extend(node)
    return payloads


async def flaresolverr_crawl_worker(
    *,
    worker_id: int = 1,
    db_path: Path,
    out_dir: Path,
    config: ScrapeConfig,
    rate_limiter: RateLimiter,
    robots: RobotsGate,
    cache: ResponseCache,
    session_dir: Path,
    fs_session: FlareSolverrSession,
    max_pages: int | None,
    pages_done: dict[str, int],
    pages_lock: asyncio.Lock,
    priority: Any | None = None,
) -> None:
    """Crawl via a shared persistent FlareSolverr session (cookies stay warm)."""
    paths = session_paths(session_dir)

    while True:
        async with pages_lock:
            if max_pages is not None and pages_done["n"] >= max_pages:
                break

        if not await flaresolverr_health(config.flaresolverr_url):
            logger.warning(
                "[FS worker %s] FlareSolverr unreachable at %s — backing off 30s",
                worker_id,
                config.flaresolverr_url,
            )
            await asyncio.sleep(30.0)
            continue

        claimed = claim_next_url(
            db_path, mode=config.resolved_queue_mode(), priority=priority
        )
        if not claimed:
            await asyncio.sleep(0.3 + random.uniform(0.0, 0.2))
            if pending_count(db_path) == 0:
                break
            if priority is not None:
                from data_pipeline.priority_chassis import count_eligible_pending

                eligible, total = count_eligible_pending(db_path, priority)
                if total > 0 and eligible == 0:
                    logger.info(
                        "[FS worker %s] Priority chassis queue drained (%s non-priority PENDING deferred)",
                        worker_id,
                        total,
                    )
                    break
            continue

        url, queued_context = claimed
        if not robots.allowed(url):
            logger.warning("Blocked by robots.txt: %s", url)
            set_url_status(db_path, url, "BLOCKED")
            continue

        node = classify_url(url)
        page_context = enrich_hints_with_vin({**node.vehicle_hints(), **queued_context})

        try:
            await rate_limiter.wait()
            solution = await fs_session.request(url)
            html = solution.get("response") or ""

            if is_recaptcha_challenge(html):
                paths["root"].mkdir(parents=True, exist_ok=True)
                (paths["root"] / "flaresolverr_last.html").write_text(
                    html, encoding="utf-8"
                )
                set_url_status(db_path, url, "FAILED")
                logger.error(
                    "Site reCAPTCHA on %s — captcha APIs disabled; skip/fail this URL",
                    url,
                )
                mark_visit_result(db_path, url, ok=False, max_attempts=config.max_attempts)
                continue

            if is_cloudflare_challenge(html):
                set_url_status(db_path, url, "BLOCKED_CF")
                await fs_session.note_cf_block()
                logger.error("Cloudflare challenge still present: %s", url)
                continue

            cache.put_text(url, html)

            ua = solution.get("userAgent") or config.session_user_agent
            cookies = solution.get("cookies") or []
            state = flaresolverr_cookies_to_storage_state(cookies, user_agent=ua)
            paths["storage_state"].write_text(
                json.dumps(state, indent=2) + "\n", encoding="utf-8"
            )

            page_meta = extract_page_vehicle_meta(html)
            node = classify_url(url, page_text=html)
            page_context = enrich_hints_with_vin(
                {**queued_context, **node.vehicle_hints(), **page_meta}
            )

            for payload in extract_embedded_json_payloads(html):
                store_payload(db_path, url, payload, page_context)
                cache.put_json(url + "#embedded", payload)

            for full in extract_hrefs_from_html(html, config.base_url):
                clean = urlparse(full)._replace(fragment="").geturl()
                if not is_in_scope_url(clean, config):
                    continue
                child = classify_url(clean)
                if config.hierarchy_aware and not should_enqueue_child(node, clean):
                    continue
                child_hints = enrich_hints_with_vin(
                    {**page_context, **child.vehicle_hints()}
                )
                enqueue_url(
                    db_path,
                    clean,
                    hierarchy_level=int(child.level),
                    vehicle_context=child_hints,
                )

            mark_visit_result(db_path, url, ok=True, max_attempts=config.max_attempts)
            await fs_session.note_success()
            async with pages_lock:
                pages_done["n"] += 1
                done = pages_done["n"]
            logger.info(
                "[FS worker %s] Visited L%s (%s): %s",
                worker_id,
                int(node.level),
                done,
                url,
            )

            if config.checkpoint_every > 0 and done % config.checkpoint_every == 0:
                async with pages_lock:
                    try:
                        write_checkpoint(
                            db_path=db_path,
                            out_dir=out_dir,
                            config=config,
                            pages_done=done,
                        )
                    except Exception as exc:  # noqa: BLE001
                        logger.error("Checkpoint failed at page %s: %s", done, exc)

        except Exception as exc:  # noqa: BLE001
            logger.error("[FS worker %s] Failed %s: %s", worker_id, url, exc)
            mark_visit_result(db_path, url, ok=False, max_attempts=config.max_attempts)

    logger.info("[FS worker %s] Shutdown", worker_id)


async def run_flaresolverr_crawl(
    *,
    db_path: Path,
    out_dir: Path,
    config: ScrapeConfig,
    rate_limiter: RateLimiter,
    robots: RobotsGate,
    cache: ResponseCache,
    session_dir: Path,
    proxy: ProxyEndpoint | None,
    max_pages: int | None,
    priority: Any | None = None,
) -> int:
    """Run 1..N FlareSolverr workers against one persistent named session."""
    workers = max(1, min(config.max_concurrent_workers, config.flaresolverr_max_concurrent))
    paths = session_paths(session_dir)
    fs = FlareSolverrSession(
        api_url=config.flaresolverr_url,
        session_name=config.flaresolverr_session_name,
        proxy=proxy,
        persist_path=paths["flaresolverr_session"],
        persist_session=config.flaresolverr_persist_session,
        max_concurrent=config.flaresolverr_max_concurrent,
        timeout_ms=config.flaresolverr_timeout_ms,
        keepalive_seconds=config.flaresolverr_keepalive_seconds,
        keepalive_url=config.start_url,
    )
    pages_done = {"n": 0}
    pages_lock = asyncio.Lock()
    async with fs:
        logger.info(
            "FlareSolverr crawl: session=%s workers=%s concurrent=%s jitter=%.2fs+%.0f%%",
            fs.session_id,
            workers,
            config.flaresolverr_max_concurrent,
            config.jitter_seconds,
            config.jitter_ratio * 100,
        )
        tasks = [
            asyncio.create_task(
                flaresolverr_crawl_worker(
                    worker_id=i + 1,
                    db_path=db_path,
                    out_dir=out_dir,
                    config=config,
                    rate_limiter=rate_limiter,
                    robots=robots,
                    cache=cache,
                    session_dir=session_dir,
                    fs_session=fs,
                    max_pages=max_pages,
                    pages_done=pages_done,
                    pages_lock=pages_lock,
                    priority=priority,
                )
            )
            for i in range(workers)
        ]
        await asyncio.gather(*tasks)
    return pages_done["n"]


async def scraper_worker(
    worker_id: int,
    context,
    *,
    db_path: Path,
    out_dir: Path,
    config: ScrapeConfig,
    rate_limiter: RateLimiter,
    robots: RobotsGate,
    cache: ResponseCache,
    max_pages: int | None,
    pages_done: dict[str, int],
    pages_lock: asyncio.Lock,
    proxy_label: str,
    concurrency_gate: ConcurrencyGate | None = None,
    priority: Any | None = None,
) -> None:
    if worker_id == 1 and context.pages:
        page = context.pages[0]
    else:
        page = await context.new_page()

    gate = concurrency_gate or ConcurrencyGate(1)

    await page.route(
        "**/*",
        lambda route: route.abort()
        if route.request.resource_type in ("font", "media")
        else route.continue_(),
    )

    active_context: dict[str, Any] = {"value": {}}

    async def on_response(response) -> None:
        ctype = response.headers.get("content-type", "")
        if "application/json" not in ctype or response.status != 200:
            return
        try:
            payload = await response.json()
        except Exception:  # noqa: BLE001
            return
        if not is_catalog_payload(payload):
            return
        store_payload(db_path, response.url, payload, active_context["value"] or None)
        cache.put_json(response.url, payload)
        logger.info("[Worker %s] Captured catalog JSON: %s", worker_id, response.url)

    page.on("response", on_response)
    logger.info("[Worker %s] Ready (proxy=%s)", worker_id, proxy_label)

    while True:
        async with pages_lock:
            if max_pages is not None and pages_done["n"] >= max_pages:
                break

        claimed = claim_next_url(
            db_path, mode=config.resolved_queue_mode(), priority=priority
        )
        if not claimed:
            await asyncio.sleep(0.3 + random.uniform(0.0, 0.4))
            if pending_count(db_path) == 0:
                break
            if priority is not None:
                from data_pipeline.priority_chassis import count_eligible_pending

                eligible, total = count_eligible_pending(db_path, priority)
                if total > 0 and eligible == 0:
                    logger.info(
                        "[Worker %s] Priority chassis queue drained (%s non-priority PENDING deferred)",
                        worker_id,
                        total,
                    )
                    break
            continue

        url, queued_context = claimed
        if not robots.allowed(url):
            logger.warning("[Worker %s] Blocked by robots.txt: %s", worker_id, url)
            set_url_status(db_path, url, "BLOCKED")
            continue

        node = classify_url(url)
        page_context = enrich_hints_with_vin({**node.vehicle_hints(), **queued_context})
        active_context["value"] = page_context

        try:
            await rate_limiter.wait()
            target_url = url

            async def _goto(goto_url: str = target_url):
                async with gate:
                    await page.goto(goto_url, wait_until="domcontentloaded", timeout=45000)
                    await page.wait_for_timeout(int(500 + random.uniform(0, 400)))

            await retry_async(
                _goto,
                max_retries=config.max_retries,
                base=config.backoff_base_seconds,
                maximum=config.backoff_max_seconds,
                label=f"goto {url}",
            )

            # Prefer FlareSolverr/session cookies; short wait then fail clearly
            try:
                await wait_for_cf_clear(page, timeout_ms=15_000)
            except Exception:  # noqa: BLE001
                title = await page.title()
                if "just a moment" in (title or "").lower():
                    set_url_status(db_path, url, "BLOCKED_CF")
                    raise RuntimeError(
                        f"Cloudflare challenge not cleared for {url}. "
                        "Run with --flaresolverr (docker profile scrape) or --cf-pass."
                    ) from None

            html = await page.content()
            cache.put_text(url, html)

            text = await page.inner_text("body")
            page_meta = extract_page_vehicle_meta(text)
            node = classify_url(url, page_text=text)
            page_context = enrich_hints_with_vin(
                {**queued_context, **node.vehicle_hints(), **page_meta}
            )
            active_context["value"] = page_context

            anchors = await page.locator("a[href]").all()
            for anchor in anchors:
                href = await anchor.get_attribute("href")
                if not href:
                    continue
                full = urljoin(config.base_url, href)
                clean = urlparse(full)._replace(fragment="").geturl()
                if not is_in_scope_url(clean, config):
                    continue
                child = classify_url(clean)
                if config.hierarchy_aware and not should_enqueue_child(node, clean):
                    continue
                child_hints = enrich_hints_with_vin(
                    {**page_context, **child.vehicle_hints()}
                )
                enqueue_url(
                    db_path,
                    clean,
                    hierarchy_level=int(child.level),
                    vehicle_context=child_hints,
                )

            mark_visit_result(db_path, url, ok=True, max_attempts=config.max_attempts)
            async with pages_lock:
                pages_done["n"] += 1
                done = pages_done["n"]
            logger.info(
                "[Worker %s] Visited L%s (%s): %s",
                worker_id,
                int(node.level),
                done,
                url,
            )

            if config.checkpoint_every > 0 and done % config.checkpoint_every == 0:
                async with pages_lock:
                    try:
                        write_checkpoint(
                            db_path=db_path,
                            out_dir=out_dir,
                            config=config,
                            pages_done=done,
                        )
                    except Exception as exc:  # noqa: BLE001
                        logger.error("Checkpoint failed at page %s: %s", done, exc)

        except Exception as exc:  # noqa: BLE001
            logger.error("[Worker %s] Failed %s: %s", worker_id, url, exc)
            mark_visit_result(db_path, url, ok=False, max_attempts=config.max_attempts)

    logger.info("[Worker %s] Shutdown", worker_id)


async def wait_for_flaresolverr(
    api_url: str,
    *,
    interval_seconds: float = 30.0,
    max_wait_seconds: float = 3600.0,
) -> None:
    """Block until FlareSolverr responds (or ``max_wait_seconds`` elapses)."""
    deadline = time.monotonic() + max(0.0, max_wait_seconds)
    attempt = 0
    while True:
        if await flaresolverr_health(api_url):
            if attempt:
                logger.info("FlareSolverr reachable at %s after %s wait(s)", api_url, attempt)
            return
        attempt += 1
        if time.monotonic() >= deadline:
            raise RuntimeError(
                f"FlareSolverr not reachable at {api_url} after {max_wait_seconds:.0f}s. "
                "docker compose -f docker-compose.satellites.yml --profile scrape up -d"
            )
        logger.warning(
            "FlareSolverr not reachable at %s — retry in %.0fs (attempt %s)",
            api_url,
            interval_seconds,
            attempt,
        )
        await asyncio.sleep(interval_seconds)


async def run_crawl(
    *,
    config: ScrapeConfig,
    db_path: Path,
    out_dir: Path,
    cache_dir: Path,
    session_dir: Path,
    proxy: ProxyEndpoint | None,
    max_pages: int | None,
    headless: bool = True,
    retry_failed: bool = False,
    use_flaresolverr_fetch: bool = True,
    priority: Any | None = None,
) -> None:
    init_db(db_path)
    pruned = prune_out_of_scope_queue(db_path, config)
    if pruned:
        logger.info("Pruned %s out-of-scope queue URLs (English %s / brand %s only)", pruned, config.allowed_locale, config.allowed_brand)

    reclaim_stale_processing(db_path)

    if retry_failed:
        n = requeue_failed(db_path)
        logger.info("Re-queued %s failed/stuck URLs", n)

    if pending_count(db_path) == 0:
        conn = sqlite3.connect(db_path)
        total = conn.execute("SELECT COUNT(*) FROM queue").fetchone()[0]
        # Re-seed / re-open start URL so scoped link discovery can run again
        row = conn.execute(
            "SELECT status FROM queue WHERE url = ?", (config.start_url,)
        ).fetchone()
        if row is None:
            conn.close()
            start_node = classify_url(config.start_url)
            enqueue_url(
                db_path,
                config.start_url,
                hierarchy_level=int(start_node.level),
                vehicle_context=enrich_hints_with_vin(start_node.vehicle_hints()),
            )
        elif row[0] != "PENDING" and total > 0 and pruned:
            conn.execute(
                "UPDATE queue SET status = 'PENDING', attempts = 0 WHERE url = ?",
                (config.start_url,),
            )
            conn.commit()
            conn.close()
            logger.info("Re-queued start URL for scoped re-crawl: %s", config.start_url)
        else:
            conn.close()
            if total == 0:
                start_node = classify_url(config.start_url)
                enqueue_url(
                    db_path,
                    config.start_url,
                    hierarchy_level=int(start_node.level),
                    vehicle_context=enrich_hints_with_vin(start_node.vehicle_hints()),
                )

    robots = RobotsGate(config.user_agent)
    if not robots.allowed(config.start_url):
        raise RuntimeError(f"robots.txt disallows start URL: {config.start_url}")

    rate_limiter = RateLimiter(
        config.rate_limit_seconds,
        jitter_seconds=config.jitter_seconds,
        jitter_ratio=config.jitter_ratio,
    )
    cache = ResponseCache(cache_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    paths = session_paths(session_dir)
    paths["root"].mkdir(parents=True, exist_ok=True)
    paths["profile"].mkdir(parents=True, exist_ok=True)

    if use_flaresolverr_fetch:
        await wait_for_flaresolverr(config.flaresolverr_url)
        logger.info(
            "Crawl mode: FlareSolverr fetch (proxy=%s, workers=%s, concurrent=%s)",
            proxy.redacted() if proxy else "direct",
            config.max_concurrent_workers,
            config.flaresolverr_max_concurrent,
        )
        pages_done_n = await run_flaresolverr_crawl(
            db_path=db_path,
            out_dir=out_dir,
            config=config,
            rate_limiter=rate_limiter,
            robots=robots,
            cache=cache,
            session_dir=session_dir,
            proxy=proxy,
            max_pages=max_pages,
            priority=priority,
        )
        if pages_done_n > 0:
            try:
                write_checkpoint(
                    db_path=db_path,
                    out_dir=out_dir,
                    config=config,
                    pages_done=pages_done_n,
                )
            except Exception as exc:  # noqa: BLE001
                logger.error("Final checkpoint failed: %s", exc)
        return

    # Fallback: Patchright persistent browser (manual CF cookies)
    try:
        from patchright.async_api import async_playwright
    except ImportError as exc:
        raise RuntimeError(
            "patchright required for browser crawl. pip install -e '.[scraping]' "
            "&& patchright install chromium"
        ) from exc

    meta = read_session_meta(session_dir)
    pages_done = {"n": 0}
    pages_lock = asyncio.Lock()
    proxy_label = proxy.redacted() if proxy else "direct"
    session_ua = meta.get("session_user_agent") or config.session_user_agent
    workers = max(1, config.max_concurrent_workers)
    concurrency_gate = ConcurrencyGate(max(1, config.flaresolverr_max_concurrent))

    async with async_playwright() as p:
        context = await p.chromium.launch_persistent_context(
            user_data_dir=str(paths["profile"]),
            headless=headless,
            proxy=proxy.as_playwright() if proxy else None,
            viewport={"width": 1365, "height": 900},
            user_agent=session_ua,
            locale="en-US",
            args=["--disable-blink-features=AutomationControlled"],
        )
        await apply_storage_state_cookies(context, paths["storage_state"])
        try:
            logger.info(
                "Patchright crawl: workers=%s concurrent=%s jitter=%.2fs",
                workers,
                concurrency_gate.limit,
                config.jitter_seconds,
            )
            tasks = [
                asyncio.create_task(
                    scraper_worker(
                        i + 1,
                        context,
                        db_path=db_path,
                        out_dir=out_dir,
                        config=config,
                        rate_limiter=rate_limiter,
                        robots=robots,
                        cache=cache,
                        max_pages=max_pages,
                        pages_done=pages_done,
                        pages_lock=pages_lock,
                        proxy_label=proxy_label,
                        concurrency_gate=concurrency_gate,
                        priority=priority,
                    )
                )
                for i in range(workers)
            ]
            await asyncio.gather(*tasks)
        finally:
            try:
                await context.storage_state(path=str(paths["storage_state"]))
            except Exception:  # noqa: BLE001
                pass
            await context.close()

    if pages_done["n"] > 0:
        try:
            write_checkpoint(
                db_path=db_path,
                out_dir=out_dir,
                config=config,
                pages_done=pages_done["n"],
            )
        except Exception as exc:  # noqa: BLE001
            logger.error("Final checkpoint failed: %s", exc)


async def run_until_catalogue_complete(
    *,
    config: ScrapeConfig,
    db_path: Path,
    out_dir: Path,
    cache_dir: Path,
    session_dir: Path,
    proxy: ProxyEndpoint | None,
    headless: bool = True,
    use_flaresolverr_fetch: bool = True,
    priority: Any | None = None,
) -> dict[str, int]:
    """Drain the English-Nissan queue: crawl → retry failures → repeat until done."""
    init_db(db_path)
    max_rounds = max(1, int(config.completion_max_rounds))
    final_stats: dict[str, int] = {}

    for round_n in range(1, max_rounds + 1):
        stats_before = queue_status_counts(db_path)
        logger.info(
            "Catalogue round %s/%s — queue=%s",
            round_n,
            max_rounds,
            stats_before,
        )
        await run_crawl(
            config=config,
            db_path=db_path,
            out_dir=out_dir,
            cache_dir=cache_dir,
            session_dir=session_dir,
            proxy=proxy,
            max_pages=None,
            headless=headless,
            retry_failed=True,
            use_flaresolverr_fetch=use_flaresolverr_fetch,
            priority=priority,
        )
        final_stats = queue_status_counts(db_path)
        pending = final_stats.get("PENDING", 0) + final_stats.get("PROCESSING", 0)
        retriable = final_stats.get("FAILED", 0) + final_stats.get("BLOCKED_CF", 0)
        visited = final_stats.get("VISITED", 0)
        if priority is not None:
            from data_pipeline.priority_chassis import count_eligible_pending

            eligible, total_pending = count_eligible_pending(db_path, priority)
            logger.info(
                "Round %s done — visited=%s eligible_pending=%s/%s deferred=%s retriable=%s",
                round_n,
                visited,
                eligible,
                total_pending,
                max(0, total_pending - eligible),
                retriable,
            )
            if total_pending > 0 and eligible == 0:
                logger.info(
                    "Priority chassis crawl complete — %s non-priority URLs remain PENDING "
                    "(restart without --priority-chassis-file to resume full crawl)",
                    total_pending,
                )
                break
        else:
            logger.info(
                "Round %s done — visited=%s pending=%s retriable=%s full=%s",
                round_n,
                visited,
                pending,
                retriable,
                final_stats,
            )
        if pending > 0:
            # Crawl returned early with work left — continue
            continue
        if retriable == 0:
            logger.info("Catalogue queue drained (no pending / FAILED / BLOCKED_CF)")
            break
        if round_n >= max_rounds:
            logger.warning(
                "Stopping after %s rounds with residual failures: %s",
                max_rounds,
                final_stats,
            )
            break
        # Next loop requeues via retry_failed=True
    return final_stats


# ---------------------------------------------------------------------------
# Transform + import pipeline
# ---------------------------------------------------------------------------


def run_transform(
    *,
    db_path: Path,
    out_dir: Path,
    config: ScrapeConfig,
    validate: bool = True,
) -> dict[str, list[dict[str, Any]]]:
    records = load_scraped_records(db_path)
    if not records:
        logger.warning("No scraped JSON payloads in %s", db_path)
        bundle: dict[str, list[dict[str, Any]]] = {
            "vehicle_master": [],
            "pnc_categories": [],
            "part_fitment": [],
            "diagram_assets": [],
        }
        write_bundle(bundle, out_dir)
        return bundle

    bundle = transform_raw_records(
        records,
        diagram_storage_prefix=config.diagram_storage_prefix,
        validate=validate,
    )
    write_bundle(bundle, out_dir)
    logger.info(
        "Wrote bundle to %s — vehicles=%s pncs=%s fitments=%s diagrams=%s",
        out_dir,
        len(bundle["vehicle_master"]),
        len(bundle["pnc_categories"]),
        len(bundle["part_fitment"]),
        len(bundle["diagram_assets"]),
    )
    return bundle


async def download_bundle_diagrams(
    bundle: dict[str, list[dict[str, Any]]],
    *,
    diagrams_dir: Path,
    config: ScrapeConfig,
    upload: bool = False,
) -> None:
    rate_limiter = RateLimiter(config.rate_limit_seconds)
    for asset in bundle.get("diagram_assets", []):
        source = asset.get("source_url")
        storage_path = asset.get("storage_path")
        if not source or not storage_path:
            continue
        dest = diagrams_dir / storage_path
        path = await download_diagram(
            source, dest, config=config, rate_limiter=rate_limiter
        )
        if path and upload:
            try:
                upload_diagram_supabase(
                    path,
                    storage_path,
                    bucket=config.supabase_diagrams_bucket,
                    content_type=_content_type_for_diagram(
                        url=source,
                        storage_path=storage_path,
                        declared=str(asset.get("content_type") or ""),
                    ),
                )
            except Exception as exc:  # noqa: BLE001
                logger.warning("Skipping upload for %s after error: %s", storage_path, exc)


def configure_logging(verbose: bool) -> None:
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Scrape PartSouq Nissan catalog → validated GTR catalog bundle"
    )
    p.add_argument(
        "--config",
        type=Path,
        default=PACKAGE_ROOT / "config" / "scrape.json",
        help="Scrape config JSON",
    )
    p.add_argument(
        "--state-db",
        type=Path,
        default=Path("crawler_state.db"),
        help="SQLite crawl state + captured JSON",
    )
    p.add_argument(
        "--parse-db",
        type=Path,
        default=None,
        help=(
            "Parse worker DB with vehicle_identity (transform-only). "
            "When set, merges identity + parts via refresh_bundle instead of parts-only transform."
        ),
    )
    p.add_argument(
        "--out-dir",
        type=Path,
        default=Path("out/partsouq_bundle"),
        help="Directory for vehicle_master.json / part_fitment.json / …",
    )
    p.add_argument(
        "--cache-dir",
        type=Path,
        default=Path("out/partsouq_cache"),
        help="HTTP/HTML response cache",
    )
    p.add_argument(
        "--diagrams-dir",
        type=Path,
        default=Path("out/partsouq_diagrams"),
        help="Local diagram image root",
    )
    p.add_argument("--start-url", type=str, default=None, help="Override start URL")
    p.add_argument(
        "--max-pages",
        type=int,
        default=None,
        help="Cap pages this run (omit / use --until-complete for full catalogue)",
    )
    p.add_argument(
        "--until-complete",
        action="store_true",
        default=None,
        help="Crawl until queue drained (retry FAILED/BLOCKED_CF rounds), then transform",
    )
    p.add_argument(
        "--no-until-complete",
        action="store_true",
        help="Single crawl pass only (do not loop to completion)",
    )
    p.add_argument(
        "--completion-rounds",
        type=int,
        default=None,
        help="Max FAILED/BLOCKED_CF retry rounds when --until-complete (default 5)",
    )
    p.add_argument(
        "--workers",
        type=int,
        default=None,
        help="Crawl workers (capped by --max-concurrent / flaresolverr_max_concurrent)",
    )
    p.add_argument(
        "--max-concurrent",
        type=int,
        default=None,
        help="Hard cap on in-flight FlareSolverr/browser fetches",
    )
    p.add_argument(
        "--jitter-seconds",
        type=float,
        default=None,
        help="Absolute random jitter added to rate limit (seconds)",
    )
    p.add_argument(
        "--jitter-ratio",
        type=float,
        default=None,
        help="Proportional jitter as fraction of rate_limit_seconds (e.g. 0.35)",
    )
    p.add_argument(
        "--session-name",
        type=str,
        default=None,
        help="FlareSolverr persistent session name (default gtr-catalog)",
    )
    p.add_argument(
        "--no-persist-session",
        action="store_true",
        help="Destroy FlareSolverr session on exit (default: keep cookies warm)",
    )
    p.add_argument(
        "--checkpoint-every",
        type=int,
        default=None,
        help="Write mid-crawl bundle every N pages (default from config)",
    )
    p.add_argument(
        "--retry-failed",
        action="store_true",
        help="Re-queue FAILED/PROCESSING URLs before crawling",
    )
    p.add_argument(
        "--transform-only",
        action="store_true",
        help="Skip crawl; transform scraped_data in --state-db only",
    )
    p.add_argument(
        "--crawl-only",
        action="store_true",
        help="Skip final transform/import after crawl (checkpoints still written)",
    )
    p.add_argument(
        "--download-diagrams",
        action="store_true",
        help="Download diagram images referenced by the bundle",
    )
    p.add_argument(
        "--upload-diagrams",
        action="store_true",
        help="Upload diagrams to Supabase Storage (implies --download-diagrams)",
    )
    p.add_argument(
        "--import-dry-run",
        action="store_true",
        help="Validate + in-memory import after transform",
    )
    p.add_argument(
        "--live-import",
        action="store_true",
        help="Import bundle to Supabase (service role)",
    )
    p.add_argument("--headed", action="store_true", help="Run browser headed")
    p.add_argument(
        "--clean-session",
        action="store_true",
        help="Wipe browser session/profile before run (required when switching proxy)",
    )
    p.add_argument(
        "--flaresolverr",
        action="store_true",
        default=None,
        help="Bootstrap CF cookies via FlareSolverr before crawl (default from config)",
    )
    p.add_argument(
        "--no-flaresolverr",
        action="store_true",
        help="Skip FlareSolverr bootstrap",
    )
    p.add_argument(
        "--flaresolverr-url",
        type=str,
        default=None,
        help="FlareSolverr API URL (default http://127.0.0.1:8191/v1)",
    )
    p.add_argument(
        "--cf-pass",
        action="store_true",
        help="Fallback: headed manual Cloudflare pass (prefer --flaresolverr)",
    )
    p.add_argument(
        "--proxy-file",
        type=Path,
        default=None,
        help="Residential proxy list JSON (default config/proxies.json)",
    )
    p.add_argument(
        "--proxy",
        action="append",
        default=[],
        help="Proxy URL (repeatable). Formats: http://user:pass@host:port or host:port:user:pass",
    )
    p.add_argument(
        "--proxy-index",
        type=int,
        default=None,
        help="Use this index from the loaded proxy list (0-based)",
    )
    p.add_argument(
        "--no-proxy-probe",
        action="store_true",
        help="Skip ipify health check when selecting a residential proxy",
    )
    p.add_argument(
        "--session-dir",
        type=Path,
        default=None,
        help="Browser session directory (profile + CF cookies)",
    )
    p.add_argument(
        "--cf-timeout",
        type=int,
        default=300,
        help="Seconds to wait for manual CF clearance (default 300)",
    )
    p.add_argument(
        "--local-ip",
        action="store_true",
        help="Force crawl via host local IP (ignore proxy list / --proxy)",
    )
    p.add_argument(
        "--priority-chassis",
        action="store_true",
        help="Enable priority chassis filter using config/priority_chassis.json",
    )
    p.add_argument(
        "--priority-chassis-file",
        type=Path,
        default=None,
        help=(
            "JSON file listing priority chassis codes (default: config/priority_chassis.json). "
            "Only matching PENDING URLs are claimed; others stay queued for a later full crawl."
        ),
    )
    p.add_argument(
        "--extra-priority-chassis",
        type=str,
        default=None,
        help="Comma-separated chassis codes merged with --priority-chassis-file when both set",
    )
    p.add_argument(
        "--no-priority-strict",
        action="store_true",
        help="With priority filter: also claim unknown non-bootstrap URLs (not recommended)",
    )
    p.add_argument(
        "--chassis-coverage",
        action="store_true",
        help="Print identity vs fitment coverage for priority chassis and exit (no crawl)",
    )
    p.add_argument("-v", "--verbose", action="store_true")
    return p


def main(argv: list[str] | None = None) -> int:
    if argv is None and len(sys.argv) == 1:
        argv = ["--until-complete", "--local-ip", "--import-dry-run", "--retry-failed"]
    args = build_parser().parse_args(argv)
    configure_logging(args.verbose)

    config = ScrapeConfig.load(args.config)
    if args.start_url:
        config.start_url = args.start_url
    if args.workers is not None:
        config.max_concurrent_workers = max(1, args.workers)
    if args.max_concurrent is not None:
        config.flaresolverr_max_concurrent = max(1, args.max_concurrent)
    if args.jitter_seconds is not None:
        config.jitter_seconds = max(0.0, args.jitter_seconds)
    if args.jitter_ratio is not None:
        config.jitter_ratio = max(0.0, args.jitter_ratio)
    if args.session_name:
        config.flaresolverr_session_name = args.session_name
    if args.no_persist_session:
        config.flaresolverr_persist_session = False
    if args.checkpoint_every is not None:
        config.checkpoint_every = max(0, args.checkpoint_every)
    if args.completion_rounds is not None:
        config.completion_max_rounds = max(1, args.completion_rounds)
    if args.proxy_file is not None:
        config.proxies_file = str(args.proxy_file)

    # Brand-scoped VIN enrichment (multi-make chassis catalogs)
    cat_path = Path(config.chassis_catalogs_path)
    if not cat_path.is_absolute():
        cat_path = Path.cwd() / cat_path
    load_chassis_catalogs(path=cat_path, force=True)
    set_active_brand(config.allowed_brand)
    refresh_chassis_catalog_view()
    logger.info(
        "Chassis VIN catalog brand=%s (%s curated platforms)",
        config.allowed_brand,
        len(CHASSIS_CATALOG),
    )

    from data_pipeline.priority_chassis import (
        DEFAULT_PRIORITY_FILE,
        build_priority_filter,
        chassis_coverage_report,
        load_vid_chassis_map,
        resolve_parse_db,
    )

    priority_path = args.priority_chassis_file
    if priority_path is None and args.priority_chassis:
        priority_path = DEFAULT_PRIORITY_FILE
    if priority_path is not None and not priority_path.is_absolute():
        priority_path = Path.cwd() / priority_path
    extra_codes = [
        c.strip() for c in (args.extra_priority_chassis or "").split(",") if c.strip()
    ]
    parse_db_for_vid = resolve_parse_db(state_db=args.state_db, explicit=args.parse_db)
    priority_filter = build_priority_filter(
        priority_path if (priority_path or extra_codes) else None,
        extra_codes=extra_codes or None,
        vid_chassis=load_vid_chassis_map(parse_db_for_vid),
        strict=not args.no_priority_strict,
    )
    if priority_filter:
        logger.info(
            "Priority chassis mode ON — %s platforms (%s vid map entries)",
            len(priority_filter.codes),
            len(priority_filter.vid_chassis),
        )
    if args.chassis_coverage:
        if not priority_filter:
            priority_filter = build_priority_filter(DEFAULT_PRIORITY_FILE)
        if not priority_filter:
            print("ERROR: --chassis-coverage requires --priority-chassis-file or default config")
            return 2
        report = chassis_coverage_report(
            parse_db=parse_db_for_vid,
            bundle_dir=args.out_dir,
            priority=priority_filter,
        )
        print(json.dumps(report, indent=2))
        return 0

    until_complete = config.run_until_complete
    if args.until_complete:
        until_complete = True
    if args.no_until_complete or args.max_pages is not None:
        until_complete = False
    if until_complete:
        args.max_pages = None
        args.retry_failed = True
        if not args.crawl_only and not args.live_import:
            args.import_dry_run = True
        logger.info(
            "Until-complete mode ON (queue_mode=%s, max_rounds=%s, no page cap)",
            config.resolved_queue_mode(),
            config.completion_max_rounds,
        )

    session_dir = args.session_dir or Path(config.session_dir)
    if not session_dir.is_absolute():
        session_dir = Path.cwd() / session_dir

    if args.local_ip or not (
        list(args.proxy or []) or os.environ.get("PROXY_LIST") or config.proxy_list
    ):
        # Default: host egress IP (no residential proxy) unless proxies are explicitly set
        if args.local_ip:
            raw_proxies: list[str] = []
            logger.info("Local-IP mode: proxies disabled; FlareSolverr uses host egress IP")
        else:
            raw_proxies = load_proxy_strings(
                config=config,
                package_root=PACKAGE_ROOT,
                explicit=list(args.proxy or []),
            )
            if not raw_proxies:
                logger.info("No proxies configured — FlareSolverr uses host local IP")
    else:
        raw_proxies = load_proxy_strings(
            config=config,
            package_root=PACKAGE_ROOT,
            explicit=list(args.proxy or []),
        )
    proxies = resolve_proxies(raw_proxies)

    async def _pick_proxy() -> ProxyEndpoint | None:
        if not proxies:
            return None
        return await select_working_proxy(
            proxies,
            index=args.proxy_index,
            probe=not args.no_proxy_probe,
        )

    # Always resolve proxy before session wipe so fingerprint matches
    try:
        active_proxy = asyncio.run(_pick_proxy()) if proxies else None
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: proxy selection failed: {exc}")
        return 1

    if (args.proxy or os.environ.get("PROXY_LIST") or args.proxy_index is not None) and not proxies:
        print(
            "ERROR: proxy flags/env set but no valid residential endpoints parsed. "
            "Use http://user:pass@host:port or host:port:user:pass "
            "(see config/proxies.example.json)."
        )
        return 1

    if args.flaresolverr_url:
        config.flaresolverr_url = args.flaresolverr_url
    use_flare = config.use_flaresolverr
    if args.no_flaresolverr:
        use_flare = False
    elif args.flaresolverr:
        use_flare = True
    # Env override
    env_flare = os.environ.get("FLARESOLVERR_URL")
    if env_flare:
        config.flaresolverr_url = env_flare
        use_flare = True

    ensure_session_for_proxy(
        session_dir,
        active_proxy,
        force_clean=args.clean_session,
        flaresolverr_url=config.flaresolverr_url,
    )

    # FlareSolverr fetch mode solves CF per page — no separate bootstrap needed.
    # Manual --cf-pass still available for Patchright fallback sessions.
    if args.cf_pass:
        try:
            asyncio.run(
                manual_cf_pass(
                    config=config,
                    session_dir=session_dir,
                    proxy=active_proxy,
                    start_url=config.start_url,
                    timeout_ms=max(30, args.cf_timeout) * 1000,
                )
            )
        except Exception as exc:  # noqa: BLE001
            print(f"ERROR: CF pass failed: {exc}")
            return 1
        only_cf = not any(
            [
                args.max_pages is not None,
                args.import_dry_run,
                args.live_import,
                args.download_diagrams,
                args.upload_diagrams,
                args.retry_failed,
            ]
        )
        if only_cf and "--cf-pass" in (argv or sys.argv):
            return 0

    if not args.transform_only:
        if until_complete:
            final_stats = asyncio.run(
                run_until_catalogue_complete(
                    config=config,
                    db_path=args.state_db,
                    out_dir=args.out_dir,
                    cache_dir=args.cache_dir,
                    session_dir=session_dir,
                    proxy=active_proxy,
                    headless=not args.headed and not args.cf_pass,
                    use_flaresolverr_fetch=use_flare and not args.cf_pass,
                    priority=priority_filter,
                )
            )
            print(f"Catalogue complete — queue: {final_stats}")
        else:
            asyncio.run(
                run_crawl(
                    config=config,
                    db_path=args.state_db,
                    out_dir=args.out_dir,
                    cache_dir=args.cache_dir,
                    session_dir=session_dir,
                    proxy=active_proxy,
                    max_pages=args.max_pages,
                    headless=not args.headed and not args.cf_pass,
                    retry_failed=args.retry_failed,
                    use_flaresolverr_fetch=use_flare and not args.cf_pass,
                    priority=priority_filter,
                )
            )

    if args.crawl_only:
        return 0

    load_env_files(PACKAGE_ROOT.parent / ".env", PACKAGE_ROOT / ".env")

    if args.parse_db is not None:
        from data_pipeline.cache_parse_worker import refresh_bundle

        counts = refresh_bundle(
            crawl_db=args.state_db,
            parse_db=args.parse_db,
            out_dir=args.out_dir,
        )
        logger.info(
            "Merged bundle (identity + parts) — vehicles=%s pncs=%s fitments=%s "
            "uncategorized_pncs=%s oem_display_names=%s",
            counts.get("vehicles"),
            counts.get("pncs"),
            counts.get("fitments"),
            counts.get("uncategorized_pncs"),
            counts.get("oem_display_names"),
        )
        bundle = load_bundle(args.out_dir)
    else:
        bundle = run_transform(
            db_path=args.state_db,
            out_dir=args.out_dir,
            config=config,
            validate=True,
        )

    if args.download_diagrams or args.upload_diagrams:
        asyncio.run(
            download_bundle_diagrams(
                bundle,
                diagrams_dir=args.diagrams_dir,
                config=config,
                upload=args.upload_diagrams,
            )
        )

    if args.import_dry_run:
        result = import_catalog(load_bundle(args.out_dir))
        counts = result.store.row_counts() if result.store else {}
        print(f"Dry-run import OK: {counts}")
        for table, stat in result.stats.items():
            print(f"  {table}: +{stat.inserted} ~{stat.updated} ={stat.unchanged}")

    if args.live_import:
        url, key = resolve_supabase_credentials()
        if not url or not key:
            print(
                "ERROR: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY "
                "(or SUPABASE_SERVICE_KEY) required for --live-import"
            )
            return 1
        result = import_supabase(
            load_bundle(args.out_dir), url=url, key=key, ensure_stock_items=True
        )
        for table, stat in result.stats.items():
            print(f"  {table}: +{stat.inserted} ~{stat.updated} ={stat.unchanged}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
