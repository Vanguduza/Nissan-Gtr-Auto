"""Megazip httpx crawler — cache HTML for re-runnable parse/transform."""

from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import random
import sqlite3
from pathlib import Path
from typing import Any

from data_pipeline.megazip.config import MegazipConfig, MakerPaths, load_priority_model_seeds
from data_pipeline.megazip.parse_html import (
    _DIAGRAM_IMG,
    _section_rank,
    cache_key,
    classify_megazip_url,
    parse_html_page,
)
from data_pipeline.megazip.state import (
    enqueue_url,
    init_db,
    load_all_parsed,
    mark_url,
    claim_next_url,
    pending_count,
    reset_url_pending,
    save_cache,
    upsert_parsed,
    queue_stats,
)
from data_pipeline.parse_partsouq_html import normalize_chassis_code

logger = logging.getLogger(__name__)

USER_AGENT = "GTR-Auto-CatalogBot/1.0 (+https://nissangtrauto.co.zw/bot; megazip-epc)"


async def fetch_html(client, url: str) -> tuple[int, str]:
    resp = await client.get(url, headers={"User-Agent": USER_AGENT, "Accept-Language": "en"})
    return resp.status_code, resp.text


def _discover_from_parsed(parsed: Any, *, maker_slug: str) -> list[dict[str, str]]:
    discovered: list[dict[str, str]] = []
    ptype = parsed.page_type
    payload = parsed.payload

    if ptype == "maker_hub":
        for m in payload.get("models") or []:
            discovered.append(
                {
                    "url": m["source_url"],
                    "page_type": "model_catalog",
                    "maker_slug": maker_slug,
                    "model_slug": m["slug"],
                }
            )
    elif ptype == "variant_list":
        model_slug = payload.get("model_slug") or ""
        for v in payload.get("variants") or []:
            discovered.append(
                {
                    "url": v["source_url"],
                    "page_type": "section_list",
                    "maker_slug": maker_slug,
                    "model_slug": model_slug,
                    "variant_slug": v["slug"],
                    "chassis_code": v.get("chassis_code") or "",
                }
            )
    elif ptype == "section_list":
        model_slug = payload.get("model_slug") or ""
        variant_slug = payload.get("variant_slug") or ""
        sections = sorted(
            payload.get("sections") or [],
            key=lambda s: _section_rank(s.get("name") or ""),
        )
        for s in sections:
            discovered.append(
                {
                    "url": s["source_url"],
                    "page_type": "diagram",
                    "maker_slug": maker_slug,
                    "model_slug": model_slug,
                    "variant_slug": variant_slug,
                    "section_slug": s["slug"],
                    "chassis_code": "",
                }
            )
    return discovered


def _priority_allows(chassis: str, priority: frozenset[str] | None) -> bool:
    if not priority:
        return True
    if not chassis:
        return True
    norm = normalize_chassis_code(chassis) or chassis.upper()
    return norm in priority or chassis.upper() in priority


def _chassis_in_priority(chassis: str, priority: frozenset[str]) -> bool:
    if not chassis:
        return False
    norm = normalize_chassis_code(chassis) or chassis.upper()
    return norm in priority or chassis.upper() in priority


def prepare_remaining_crawl(
    paths: MakerPaths,
    *,
    priority_chassis: frozenset[str],
) -> dict[str, int]:
    """Re-enqueue model/variant branches skipped during priority-only crawl."""
    init_db(paths.state_db)
    parsed = load_all_parsed(paths.state_db, maker_slug=paths.slug)
    parsed_variant_models = {
        row["payload"].get("model_slug") or ""
        for row in parsed
        if row["page_type"] == "variant_list"
    }
    stats = {"models_reset": 0, "variants": 0, "sections": 0}

    for row in parsed:
        ptype = row["page_type"]
        payload = row["payload"]

        if ptype == "maker_hub":
            for model in payload.get("models") or []:
                model_slug = model.get("slug") or ""
                model_url = model.get("source_url") or ""
                if not model_url:
                    continue
                if model_slug not in parsed_variant_models:
                    enqueue_url(
                        paths.state_db,
                        model_url,
                        page_type="model_catalog",
                        maker_slug=paths.slug,
                        model_slug=model_slug,
                    )
                    reset_url_pending(paths.state_db, model_url)
                    stats["models_reset"] += 1
            continue

        if ptype == "variant_list":
            model_slug = payload.get("model_slug") or ""
            for variant in payload.get("variants") or []:
                chassis = variant.get("chassis_code") or ""
                if _chassis_in_priority(chassis, priority_chassis):
                    continue
                variant_url = variant.get("source_url") or ""
                if not variant_url:
                    continue
                norm = normalize_chassis_code(chassis) or chassis.upper()
                enqueue_url(
                    paths.state_db,
                    variant_url,
                    page_type="section_list",
                    maker_slug=paths.slug,
                    model_slug=model_slug,
                    variant_slug=variant.get("slug") or "",
                    chassis_code=norm,
                )
                reset_url_pending(paths.state_db, variant_url)
                stats["variants"] += 1
            continue

        if ptype == "section_list":
            chassis = payload.get("chassis_code") or ""
            if _chassis_in_priority(chassis, priority_chassis):
                continue
            model_slug = payload.get("model_slug") or ""
            variant_slug = payload.get("variant_slug") or ""
            norm = normalize_chassis_code(chassis) or chassis.upper()
            for section in payload.get("sections") or []:
                section_url = section.get("source_url") or ""
                if not section_url:
                    continue
                enqueue_url(
                    paths.state_db,
                    section_url,
                    page_type="diagram",
                    maker_slug=paths.slug,
                    model_slug=model_slug,
                    variant_slug=variant_slug,
                    section_slug=section.get("slug") or "",
                    chassis_code=norm,
                )
                reset_url_pending(paths.state_db, section_url)
                stats["sections"] += 1

    logger.info(
        "[%s] remaining pass queued: %s models reset, %s variants, %s sections",
        paths.maker,
        stats["models_reset"],
        stats["variants"],
        stats["sections"],
    )
    return stats


def self_heal_queue(paths: MakerPaths, hub_url: str) -> dict[str, int]:
    """Recover a stale crawl queue before a fresh pass (applies to every maker).

    Two failure modes are made self-correcting so coverage can never
    permanently freeze to seed models:

    1. **Lost cache** — any ``VISITED``/``ERROR`` page whose cached HTML file no
       longer exists on disk is re-queued to ``PENDING``. A missing cache file
       otherwise strands the page (``parse`` skips it) with no way to re-fetch.
    2. **Unproductive hub** — a maker hub that was visited but parsed to zero
       models is re-queued. A 0-model hub makes model fan-out impossible, so the
       maker would yield only its priority seeds (the X-Trail-only symptom).

    Only ``VISITED``/``ERROR`` rows are touched, never ``PENDING``/``PROCESSING``,
    so a live worker is never disturbed.
    """
    conn = sqlite3.connect(paths.state_db, timeout=60.0)
    missing_cache = 0
    hub_reset = 0
    try:
        rows = conn.execute(
            "SELECT url FROM queue WHERE status IN ('VISITED', 'ERROR')"
        ).fetchall()
        for (url,) in rows:
            cache_file = paths.cache_dir / f"{cache_key(url)}.html"
            if not cache_file.is_file():
                conn.execute(
                    "UPDATE queue SET status = 'PENDING', last_error = NULL, "
                    "updated_at = datetime('now') WHERE url = ? "
                    "AND status IN ('VISITED', 'ERROR')",
                    (url,),
                )
                missing_cache += 1

        hub_status_row = conn.execute(
            "SELECT status FROM queue WHERE url = ?", (hub_url,)
        ).fetchone()
        hub_payload_row = conn.execute(
            "SELECT payload_json FROM parsed_pages WHERE url = ?", (hub_url,)
        ).fetchone()
        hub_models: list[Any] = []
        if hub_payload_row:
            try:
                hub_models = (json.loads(hub_payload_row[0]) or {}).get("models") or []
            except (json.JSONDecodeError, TypeError):
                hub_models = []
        if (
            hub_status_row
            and hub_status_row[0] in ("VISITED", "ERROR")
            and not hub_models
        ):
            conn.execute(
                "UPDATE queue SET status = 'PENDING', last_error = NULL, "
                "updated_at = datetime('now') WHERE url = ? "
                "AND status IN ('VISITED', 'ERROR')",
                (hub_url,),
            )
            hub_reset = 1
        conn.commit()
    finally:
        conn.close()

    if missing_cache or hub_reset:
        logger.info(
            "[%s] self-heal: re-queued %s page(s) with missing cache; hub_reset=%s",
            paths.maker,
            missing_cache,
            hub_reset,
        )
    return {"missing_cache_requeued": missing_cache, "hub_reset": hub_reset}


async def crawl_maker(
    paths: MakerPaths,
    config: MegazipConfig,
    *,
    max_pages: int | None = None,
    priority_chassis: frozenset[str] | None = None,
    skip_crawl: bool = False,
    priority_model_seeds: tuple[str, ...] = (),
    prepare_remaining_before: frozenset[str] | None = None,
) -> dict[str, Any]:
    init_db(paths.state_db)
    paths.cache_dir.mkdir(parents=True, exist_ok=True)

    # Self-heal a stale/partial queue so a lost cache file or an unproductive
    # hub can never permanently freeze coverage (all makers). Skipped when not
    # crawling, since healing re-queues pages that only a network pass can fetch.
    heal_stats: dict[str, int] = {}
    if not skip_crawl:
        heal_stats = self_heal_queue(paths, config.hub_url(paths.maker))

    if prepare_remaining_before is not None:
        requeue_stats = prepare_remaining_crawl(
            paths,
            priority_chassis=prepare_remaining_before,
        )
    else:
        requeue_stats = {}

    start_url = config.hub_url(paths.maker)
    # Priority phase must discover all models from hub; seeds alone only cover listed URLs.
    if priority_chassis or not priority_model_seeds:
        enqueue_url(
            paths.state_db,
            start_url,
            page_type="maker_hub",
            maker_slug=paths.slug,
        )

    for seed_url in priority_model_seeds:
        parts = seed_url.rstrip("/").split("/")
        model_slug = parts[-1] if len(parts) >= 4 else ""
        enqueue_url(
            paths.state_db,
            seed_url,
            page_type="model_catalog",
            maker_slug=paths.slug,
            model_slug=model_slug,
        )
        logger.info("[%s] priority model seed: %s", paths.maker, seed_url)

    if skip_crawl:
        logger.info("[%s] skip-crawl — using cached HTML only", paths.maker)
        return {
            "pages_fetched": 0,
            "queue": queue_stats(paths.state_db),
            "self_heal": heal_stats,
        }

    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError("httpx required: pip install -e '.[scraping]'") from exc

    pages_done = 0
    rate = config.rate_limit_seconds

    async with httpx.AsyncClient(
        timeout=60.0,
        follow_redirects=True,
        limits=httpx.Limits(max_connections=config.max_concurrent_workers),
    ) as client:
        while True:
            if max_pages is not None and pages_done >= max_pages:
                break
            if pending_count(paths.state_db) == 0:
                break
            row = claim_next_url(paths.state_db, maker_slug=paths.slug)
            if not row:
                await asyncio.sleep(0.2)
                continue

            url = row["url"]
            chassis = row.get("chassis_code") or ""
            if priority_chassis and row.get("page_type") in ("section_list", "diagram"):
                if chassis and not _priority_allows(chassis, priority_chassis):
                    mark_url(paths.state_db, url, ok=True)
                    continue

            await asyncio.sleep(rate + random.uniform(0, rate * 0.3))
            try:
                status, html = await fetch_html(client, url)
                if status >= 400:
                    mark_url(paths.state_db, url, ok=False, error=f"HTTP {status}")
                    continue
                digest = hashlib.sha256(html.encode("utf-8")).hexdigest()
                cache_file = paths.cache_dir / f"{cache_key(url)}.html"
                cache_file.write_text(html, encoding="utf-8")
                save_cache(paths.state_db, url, str(cache_file), digest)

                parsed = parse_html_page(
                    html,
                    url,
                    maker_slug=paths.slug,
                    model_slug=row.get("model_slug") or "",
                    variant_slug=row.get("variant_slug") or "",
                    section_slug=row.get("section_slug") or "",
                    default_chassis=chassis,
                )
                if parsed.page_type == "diagram":
                    img_url = parsed.payload.get("image_url") or ""
                    if img_url:
                        try:
                            ir = await client.get(img_url)
                            if ir.status_code == 200 and ir.content:
                                parsed = parse_html_page(
                                    html,
                                    url,
                                    maker_slug=paths.slug,
                                    model_slug=row.get("model_slug") or "",
                                    variant_slug=row.get("variant_slug") or "",
                                    section_slug=row.get("section_slug") or "",
                                    default_chassis=chassis,
                                    image_bytes=ir.content,
                                )
                        except Exception as img_exc:  # noqa: BLE001
                            logger.warning("[%s] diagram image fetch %s: %s", paths.maker, img_url, img_exc)

                upsert_parsed(paths.state_db, url, parsed.page_type, paths.slug, parsed.payload)

                for item in _discover_from_parsed(parsed, maker_slug=paths.slug):
                    ch = item.get("chassis_code") or chassis
                    if priority_chassis and item.get("page_type") in (
                        "variant_list",
                        "section_list",
                        "diagram",
                    ):
                        if ch and not _priority_allows(ch, priority_chassis):
                            continue
                    enqueue_url(
                        paths.state_db,
                        item["url"],
                        page_type=item.get("page_type") or "",
                        maker_slug=item.get("maker_slug") or paths.slug,
                        model_slug=item.get("model_slug") or "",
                        variant_slug=item.get("variant_slug") or "",
                        section_slug=item.get("section_slug") or "",
                        chassis_code=ch,
                    )

                mark_url(paths.state_db, url, ok=True)
                pages_done += 1
                if pages_done % 10 == 0:
                    logger.info("[%s] crawl progress: %s pages", paths.maker, pages_done)
            except Exception as exc:  # noqa: BLE001
                mark_url(paths.state_db, url, ok=False, error=str(exc))
                logger.warning("[%s] fetch failed %s: %s", paths.maker, url, exc)

    return {
        "pages_fetched": pages_done,
        "queue": queue_stats(paths.state_db),
        "requeue": requeue_stats,
        "self_heal": heal_stats,
    }


def _fetch_png_header_bytes(client, url: str) -> bytes | None:
    """Fetch enough bytes to read PNG IHDR dimensions (first 24 bytes)."""
    try:
        resp = client.get(
            url,
            headers={"User-Agent": USER_AGENT, "Range": "bytes=0-23"},
        )
        if resp.status_code in (200, 206) and len(resp.content) >= 24:
            return resp.content[:24]
    except Exception as exc:  # noqa: BLE001
        logger.warning("PNG header fetch failed %s: %s", url, exc)
    return None


def parse_cached_pages(
    paths: MakerPaths,
    *,
    refresh_diagram_dims: bool = False,
) -> int:
    """Re-parse cached HTML; optionally refresh diagram PNG dimensions from CDN."""
    init_db(paths.state_db)
    conn = sqlite3.connect(paths.state_db, timeout=60.0)
    try:
        rows = conn.execute(
            """
            SELECT c.url, c.cache_path, q.model_slug, q.variant_slug, q.section_slug, q.chassis_code
            FROM page_cache c
            LEFT JOIN queue q ON q.url = c.url
            """
        ).fetchall()
        existing_rows = conn.execute(
            "SELECT url, payload_json FROM parsed_pages"
        ).fetchall()
    finally:
        conn.close()

    existing_payloads: dict[str, dict[str, Any]] = {}
    for url, payload_json in existing_rows:
        try:
            existing_payloads[url] = json.loads(payload_json)
        except json.JSONDecodeError:
            continue

    needs_network = refresh_diagram_dims
    if not needs_network:
        for url, cache_path, *_rest in rows:
            prior = existing_payloads.get(url) or {}
            if (
                (classify_megazip_url(url) == "diagram" or prior.get("image_url"))
                and (not prior.get("image_width") or not prior.get("image_height"))
            ):
                needs_network = True
                break

    client = None
    if needs_network:
        try:
            import httpx

            client = httpx.Client(timeout=30.0, follow_redirects=True)
        except ImportError:
            logger.warning("httpx unavailable — skipping diagram dimension refresh fetch")

    count = 0
    try:
        for url, cache_path, model_slug, variant_slug, section_slug, chassis in rows:
            p = Path(cache_path)
            if not p.is_file():
                continue
            html = p.read_text(encoding="utf-8", errors="ignore")
            prior = existing_payloads.get(url) or {}
            stored_w = prior.get("image_width")
            stored_h = prior.get("image_height")
            image_bytes: bytes | None = None

            page_type_hint = classify_megazip_url(url)
            if page_type_hint == "diagram" or prior.get("image_url"):
                need_dims = refresh_diagram_dims or not stored_w or not stored_h
                if need_dims and client is not None:
                    img_url = prior.get("image_url") or ""
                    if not img_url:
                        img_m = _DIAGRAM_IMG.search(html)
                        img_url = img_m.group(1) if img_m else ""
                    if img_url:
                        image_bytes = _fetch_png_header_bytes(client, img_url)

            parsed = parse_html_page(
                html,
                url,
                maker_slug=paths.slug,
                model_slug=model_slug or "",
                variant_slug=variant_slug or "",
                section_slug=section_slug or "",
                default_chassis=chassis or "",
                image_bytes=image_bytes,
                stored_width=int(stored_w) if stored_w else None,
                stored_height=int(stored_h) if stored_h else None,
            )
            upsert_parsed(paths.state_db, url, parsed.page_type, paths.slug, parsed.payload)
            count += 1
    finally:
        if client is not None:
            client.close()
    return count
