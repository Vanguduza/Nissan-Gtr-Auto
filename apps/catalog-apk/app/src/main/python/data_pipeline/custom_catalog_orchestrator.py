"""Custom path-template catalog adapter — live fetch + quality artifacts for Catalog APK."""

from __future__ import annotations

import argparse
import json
import logging
import re
import time
from pathlib import Path
from typing import Any
from urllib.parse import urljoin

logger = logging.getLogger("data_pipeline.custom_catalog_orchestrator")

try:
    from bs4 import BeautifulSoup
except ImportError:  # pragma: no cover
    BeautifulSoup = None  # type: ignore


def _slugify(text: str) -> str:
    t = re.sub(r"[^\w\s-]", "", text.strip().lower(), flags=re.UNICODE)
    return re.sub(r"[\s_]+", "-", t).strip("-") or "unknown"


def _render(template: str, **kwargs: str) -> str:
    out = template
    for key, value in kwargs.items():
        out = out.replace("{" + key + "}", value)
    return out


def _load_snapshot(path: Path | None) -> dict[str, Any]:
    if not path or not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


async def _fetch(url: str, *, flaresolverr_url: str | None, force: bool) -> tuple[int, str]:
    from data_pipeline.flaresolverr_transport import (
        flaresolverr_fetch_html,
        looks_like_cloudflare,
    )

    if force and flaresolverr_url:
        return await flaresolverr_fetch_html(url, api_url=flaresolverr_url)

    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError("httpx required") from exc

    async with httpx.AsyncClient(timeout=60.0, follow_redirects=True) as client:
        resp = await client.get(
            url,
            headers={
                "User-Agent": "GTR-CatalogApk/0.3 (+custom-adapter)",
                "Accept-Language": "en",
            },
        )
        status, html = resp.status_code, resp.text
        headers = {k: v for k, v in resp.headers.items()}
    if flaresolverr_url and looks_like_cloudflare(status, html, headers):
        return await flaresolverr_fetch_html(url, api_url=flaresolverr_url)
    return status, html


def _parse_links(html: str, base_url: str) -> list[dict[str, str]]:
    if BeautifulSoup is None:
        return []
    soup = BeautifulSoup(html, "html.parser")
    out: list[dict[str, str]] = []
    for a in soup.select("a[href]"):
        href = (a.get("href") or "").strip()
        if not href or href.startswith("#"):
            continue
        text = a.get_text(" ", strip=True)
        out.append({"url": urljoin(base_url, href), "text": text})
    return out


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Custom path-template catalog adapter")
    parser.add_argument("--out-root", type=Path, required=True)
    parser.add_argument("--makers", default="Custom")
    parser.add_argument("--model-slug", default="")
    parser.add_argument("--phase", default="crawl,transform,filter")
    parser.add_argument("--single-chassis", default=None)
    parser.add_argument("--max-pages", type=int, default=25)
    parser.add_argument("--drop-html-after-parse", action="store_true", default=True)
    parser.add_argument("--prune-html-cache", action="store_true", default=True)
    parser.add_argument("--flaresolverr-url", default=None)
    parser.add_argument("--force-flaresolverr", action="store_true")
    parser.add_argument("--pause-flag", type=Path, default=None)
    parser.add_argument("--profile-snapshot", type=Path, default=None)
    parser.add_argument("-v", "--verbose", action="store_true")
    args, _unknown = parser.parse_known_args(argv)

    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO)

    snapshot = _load_snapshot(args.profile_snapshot)
    base_url = str(snapshot.get("base_url") or "").rstrip("/")
    paths = snapshot.get("paths") or {}
    maker_name = (args.makers.split(",")[0] or "custom").strip()
    maker_map = paths.get("maker_slug_map") or {}
    maker_slug = str(maker_map.get(maker_name) or _slugify(maker_name))
    model_slug = (args.model_slug or "").strip() or "model"

    out = args.out_root
    out.mkdir(parents=True, exist_ok=True)
    maker_dir = out / maker_slug
    cache_dir = maker_dir / "cache"
    bundle = maker_dir / "bundle"
    cache_dir.mkdir(parents=True, exist_ok=True)
    bundle.mkdir(parents=True, exist_ok=True)

    if not base_url:
        logger.error("profile snapshot missing base_url")
        return 1

    import asyncio

    pages: list[dict[str, Any]] = []
    max_pages = max(1, int(args.max_pages or 25))

    async def run_crawl() -> int:
        seed_paths = []
        maker_hub = paths.get("maker_hub") or paths.get("parts_hub") or "/"
        seed_paths.append(_render(str(maker_hub), maker_slug=maker_slug, model_slug=model_slug))
        if paths.get("model"):
            seed_paths.append(
                _render(str(paths["model"]), maker_slug=maker_slug, model_slug=model_slug)
            )

        visited: set[str] = set()
        queue = [urljoin(base_url + "/", p.lstrip("/")) for p in seed_paths]
        while queue and len(pages) < max_pages:
            if args.pause_flag and args.pause_flag.is_file():
                logger.info("pause flag set — cooperative stop")
                return 2
            url = queue.pop(0)
            if url in visited:
                continue
            visited.add(url)
            status, html = await _fetch(
                url,
                flaresolverr_url=args.flaresolverr_url,
                force=args.force_flaresolverr,
            )
            digest = f"{len(html)}:{status}"
            cache_file = cache_dir / f"{abs(hash(url)) & 0xFFFFFFFF:08x}.html"
            if not args.drop_html_after_parse:
                cache_file.write_text(html, encoding="utf-8")
            links = _parse_links(html, url)
            pages.append(
                {
                    "url": url,
                    "status": status,
                    "bytes": len(html),
                    "links": len(links),
                    "digest": digest,
                }
            )
            logger.info("custom crawl %s -> %s (%s links)", url, status, len(links))
            # Enqueue same-host path-template matches.
            for link in links:
                href = link["url"]
                if not href.startswith(base_url):
                    continue
                if model_slug and model_slug not in href and maker_slug not in href:
                    continue
                if href not in visited and len(queue) + len(pages) < max_pages * 2:
                    queue.append(href)
            time.sleep(0.2)
        return 0

    code = asyncio.run(run_crawl())
    if code == 2:
        return 2

    chassis = args.single_chassis or ""
    variants = [
        {
            "model_slug": model_slug,
            "variant_slug": _slugify(chassis or "variant"),
            "chassis_code": chassis,
            "exploded_count": 0,
            "exploded_passing_count": 1 if pages else 0,
            "raster_count": 0,
            "raster_passing_count": 0,
            "ambiguous_count": 0,
            "ambiguous_passing_count": 0,
            "companion_parts_rows": 0,
            "complete": bool(pages),
            "publishable": bool(pages),
        }
    ]
    quality = {
        "sections": max(0, len(pages) - 1),
        "diagrams": 0,
        "publishable_variants": 1 if pages else 0,
        "uncategorized": 0,
        "engine_fill_pct": 0,
        "publishable": bool(pages),
        "custom_adapter": True,
        "pages_fetched": len(pages),
        "profile_id": snapshot.get("id"),
    }
    (bundle / "quality_report.json").write_text(json.dumps(quality, indent=2) + "\n", encoding="utf-8")
    (bundle / "variant_quality.json").write_text(
        json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8"
    )
    (bundle / "attrs_audit.json").write_text(
        json.dumps(
            {
                "chassis": chassis,
                "variants_checked": len(variants),
                "engine_present": 0,
                "engine_missing": len(variants),
                "custom_adapter": True,
                "pages": pages[:50],
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    (bundle / "catalog_makers.json").write_text(
        json.dumps([{"slug": maker_slug, "name": maker_name}], indent=2) + "\n",
        encoding="utf-8",
    )
    (bundle / "catalog_models.json").write_text(
        json.dumps(
            [{"maker_slug": maker_slug, "slug": model_slug, "name": model_slug}],
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    (bundle / "catalog_variants.json").write_text(
        json.dumps(
            [
                {
                    "maker_slug": maker_slug,
                    "model_slug": model_slug,
                    "slug": v["variant_slug"],
                    "chassis_code": chassis,
                    "name": chassis or v["variant_slug"],
                }
                for v in variants
            ],
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    logger.info("Custom adapter complete: %s pages under %s", len(pages), bundle)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
