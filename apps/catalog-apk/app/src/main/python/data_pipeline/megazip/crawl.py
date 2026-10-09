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
    acquire_model_leases,
    enqueue_url,
    heartbeat_leases,
    init_db,
    leased_pending_count,
    list_active_leases,
    load_all_parsed,
    mark_url,
    claim_next_url,
    pending_count,
    reclaim_stale_processing,
    release_leases,
    reset_url_pending,
    save_cache,
    upsert_parsed,
    queue_stats,
)
from data_pipeline.parse_partsouq_html import normalize_chassis_code

logger = logging.getLogger(__name__)

USER_AGENT = "GTR-Auto-CatalogBot/1.0 (+https://nissangtrauto.co.zw/bot; megazip-epc)"


async def fetch_html(
    client,
    url: str,
    *,
    flaresolverr_url: str | None = None,
    force_flaresolverr: bool = False,
) -> tuple[int, str]:
    from data_pipeline.flaresolverr_transport import (
        flaresolverr_fetch_html,
        looks_like_cloudflare,
    )

    if force_flaresolverr and flaresolverr_url:
        return await flaresolverr_fetch_html(url, api_url=flaresolverr_url)

    resp = await client.get(url, headers={"User-Agent": USER_AGENT, "Accept-Language": "en"})
    status, html = resp.status_code, resp.text
    headers = {k: v for k, v in resp.headers.items()}
    if flaresolverr_url and looks_like_cloudflare(status, html, headers):
        logger.info("Cloudflare detected for %s — routing via FlareSolverr", url)
        return await flaresolverr_fetch_html(url, api_url=flaresolverr_url)
    return status, html


def _pause_requested(pause_flag: Path | None) -> bool:
    return pause_flag is not None and pause_flag.is_file()


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



def should_retain_html(page_type: str, payload: dict[str, Any]) -> bool:
    """Auto-keep HTML when parse looks incomplete (no manual toggle)."""
    if page_type == "diagram":
        parts = payload.get("parts") or []
        table = payload.get("parts_table") or []
        kind = str(payload.get("diagram_kind") or "ambiguous")
        title = (payload.get("title") or "").strip()
        if not title:
            return True
        if not parts and not table:
            return True
        if kind == "exploded_diagram" and (
            not payload.get("image_width") or not payload.get("image_height")
        ):
            return True
        if kind == "ambiguous" and not table:
            return True
        oem_ok = sum(1 for p in parts if p.get("oem_part_number"))
        if kind == "exploded_diagram" and oem_ok < 3:
            return True
        return False
    if page_type == "variant_list":
        variants = payload.get("variants") or []
        if not variants:
            return True
        # Keep if any variant is missing chassis (engine may be absent on Megazip).
        if any(not (v.get("chassis_code") or "").strip() for v in variants):
            return True
        return False
    if page_type in ("section_list", "maker_hub", "model_catalog", "model_hub"):
        key = {
            "section_list": "sections",
            "maker_hub": "models",
            "model_catalog": "models",
            "model_hub": "models",
        }[page_type]
        items = payload.get(key) or payload.get("variants") or []
        return len(items) == 0
    return False


def _engine_for_variant(
    db_path: Path,
    *,
    maker_slug: str,
    model_slug: str,
    variant_slug: str,
) -> str:
    """Best-effort engine_code from a prior variant_list parse for this variant."""
    if not variant_slug:
        return ""
    for row in load_all_parsed(db_path, maker_slug=maker_slug):
        if row.get("page_type") != "variant_list":
            continue
        payload = row.get("payload") or {}
        if model_slug and (payload.get("model_slug") or "") not in ("", model_slug):
            continue
        for v in payload.get("variants") or []:
            if (v.get("slug") or "") != variant_slug:
                continue
            eng = (v.get("engine_code") or "").strip()
            if eng:
                return eng
    return ""


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


def load_visited_deep_chassis(db_path: Path) -> frozenset[str]:
    """Chassis codes that already have VISITED section_list or diagram pages."""
    conn = sqlite3.connect(db_path, timeout=60.0)
    try:
        rows = conn.execute(
            """
            SELECT DISTINCT chassis_code FROM queue
            WHERE status = 'VISITED'
              AND page_type IN ('section_list', 'diagram')
              AND chassis_code IS NOT NULL
              AND trim(chassis_code) != ''
            """
        ).fetchall()
    finally:
        conn.close()
    out: set[str] = set()
    for (raw,) in rows:
        code = str(raw or "").strip()
        if not code:
            continue
        norm = normalize_chassis_code(code) or code.upper()
        out.add(norm)
        out.add(code.upper())
    return frozenset(out)


def prepare_remaining_crawl(
    paths: MakerPaths,
    *,
    priority_chassis: frozenset[str],
) -> dict[str, int]:
    """Re-enqueue model/variant branches skipped during priority-only crawl.

    ``priority_chassis`` is treated as already-covered: those variants are not
    re-queued. Pass the empty set only when you intentionally want every
    chassis re-queued; prefer ``load_visited_deep_chassis`` for resume.
    """
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


def _hub_model_count(db_path: Path, hub_url: str) -> int:
    """Number of models the maker hub parsed to (0 signals a broken fan-out)."""
    conn = sqlite3.connect(db_path, timeout=60.0)
    try:
        row = conn.execute(
            "SELECT payload_json FROM parsed_pages WHERE url = ?", (hub_url,)
        ).fetchone()
    finally:
        conn.close()
    if not row:
        return 0
    try:
        return len((json.loads(row[0]) or {}).get("models") or [])
    except (json.JSONDecodeError, TypeError):
        return 0


def _parsed_payload_usable(payload_json: str | None) -> bool:
    """True when ``parsed_pages`` already holds a usable payload for transform."""
    if not payload_json or not str(payload_json).strip():
        return False
    try:
        payload = json.loads(payload_json)
    except (json.JSONDecodeError, TypeError):
        return False
    return isinstance(payload, dict)


def self_heal_queue(paths: MakerPaths, hub_url: str) -> dict[str, int]:
    """Recover a stale crawl queue before a fresh pass (applies to every maker).

    Two failure modes are made self-correcting so coverage can never
    permanently freeze to seed models:

    1. **Lost cache** — any ``VISITED``/``ERROR`` page whose cached HTML file no
       longer exists on disk is re-queued to ``PENDING``, **unless**
       ``parsed_pages`` already has a usable payload. Parsed rows are enough for
       transform/import, so completed models can drop HTML cache safely.
    2. **Unproductive hub** — a maker hub that was visited but parsed to zero
       models is re-queued. A 0-model hub makes model fan-out impossible, so the
       maker would yield only its priority seeds (the X-Trail-only symptom).

    Only ``VISITED``/``ERROR`` rows are touched, never ``PENDING``/``PROCESSING``,
    so a live worker is never disturbed.
    """
    conn = sqlite3.connect(paths.state_db, timeout=60.0)
    missing_cache = 0
    missing_cache_kept_parsed = 0
    hub_reset = 0
    try:
        rows = conn.execute(
            "SELECT url FROM queue WHERE status IN ('VISITED', 'ERROR')"
        ).fetchall()
        for (url,) in rows:
            cache_file = paths.cache_dir / f"{cache_key(url)}.html"
            if cache_file.is_file():
                continue
            parsed_row = conn.execute(
                "SELECT payload_json FROM parsed_pages WHERE url = ?",
                (url,),
            ).fetchone()
            if parsed_row and _parsed_payload_usable(parsed_row[0]):
                missing_cache_kept_parsed += 1
                continue
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

    if missing_cache or hub_reset or missing_cache_kept_parsed:
        logger.info(
            "[%s] self-heal: re-queued %s page(s) with missing cache; "
            "kept_parsed=%s; hub_reset=%s",
            paths.maker,
            missing_cache,
            missing_cache_kept_parsed,
            hub_reset,
        )
    return {
        "missing_cache_requeued": missing_cache,
        "missing_cache_kept_parsed": missing_cache_kept_parsed,
        "hub_reset": hub_reset,
    }


def _prune_html_rows(
    paths: MakerPaths,
    rows: list[tuple[Any, ...]],
    *,
    require_parsed: bool,
    dry_run: bool,
) -> dict[str, int]:
    deleted = 0
    skipped_no_parsed = 0
    skipped_missing = 0
    for url, payload_json in rows:
        cache_file = paths.cache_dir / f"{cache_key(str(url))}.html"
        if require_parsed and not _parsed_payload_usable(payload_json):
            skipped_no_parsed += 1
            continue
        if not cache_file.is_file():
            skipped_missing += 1
            continue
        if not dry_run:
            cache_file.unlink(missing_ok=True)
        deleted += 1
    return {
        "urls": len(rows),
        "deleted": deleted,
        "skipped_no_parsed": skipped_no_parsed,
        "skipped_missing": skipped_missing,
    }


def prune_model_html_cache(
    paths: MakerPaths,
    model_slugs: frozenset[str] | set[str] | list[str],
    *,
    require_parsed: bool = True,
    dry_run: bool = False,
) -> dict[str, int]:
    """Delete cached HTML for crawl-complete models that already have parsed payloads.

    Safe only after self-heal skips requeue when ``parsed_pages`` exists.
    Never deletes files for URLs still ``PENDING``/``PROCESSING``, or (when
    ``require_parsed``) without a usable parse.
    """
    wanted = frozenset(s for s in model_slugs if s)
    if not wanted:
        return {"models": 0, "urls": 0, "deleted": 0, "skipped_no_parsed": 0, "skipped_missing": 0}

    conn = sqlite3.connect(paths.state_db, timeout=60.0)
    try:
        placeholders = ",".join("?" for _ in wanted)
        rows = conn.execute(
            f"""
            SELECT q.url, p.payload_json
            FROM queue q
            LEFT JOIN parsed_pages p ON p.url = q.url
            WHERE q.model_slug IN ({placeholders})
              AND q.status IN ('VISITED', 'ERROR')
            """,
            sorted(wanted),
        ).fetchall()
    finally:
        conn.close()

    stats = _prune_html_rows(paths, rows, require_parsed=require_parsed, dry_run=dry_run)
    logger.info(
        "[%s] prune HTML cache models=%s urls=%s deleted=%s skipped_no_parsed=%s "
        "skipped_missing=%s dry_run=%s",
        paths.maker,
        len(wanted),
        stats["urls"],
        stats["deleted"],
        stats["skipped_no_parsed"],
        stats["skipped_missing"],
        dry_run,
    )
    return {"models": len(wanted), **stats}


def prune_chassis_html_cache(
    paths: MakerPaths,
    chassis_codes: frozenset[str] | set[str] | list[str],
    *,
    require_parsed: bool = True,
    dry_run: bool = False,
) -> dict[str, int]:
    """Delete cached HTML for finished chassis deep pages that are already parsed."""
    wanted = frozenset(
        (normalize_chassis_code(c) or str(c).upper()) for c in chassis_codes if c
    )
    if not wanted:
        return {"chassis": 0, "urls": 0, "deleted": 0, "skipped_no_parsed": 0, "skipped_missing": 0}

    conn = sqlite3.connect(paths.state_db, timeout=60.0)
    try:
        placeholders = ",".join("?" for _ in wanted)
        rows = conn.execute(
            f"""
            SELECT q.url, p.payload_json
            FROM queue q
            LEFT JOIN parsed_pages p ON p.url = q.url
            WHERE upper(trim(COALESCE(q.chassis_code, ''))) IN ({placeholders})
              AND q.status IN ('VISITED', 'ERROR')
              AND q.page_type IN ('section_list', 'diagram')
            """,
            sorted(wanted),
        ).fetchall()
    finally:
        conn.close()

    stats = _prune_html_rows(paths, rows, require_parsed=require_parsed, dry_run=dry_run)
    logger.info(
        "[%s] prune HTML cache chassis=%s urls=%s deleted=%s skipped_no_parsed=%s "
        "skipped_missing=%s dry_run=%s",
        paths.maker,
        ",".join(sorted(wanted)),
        stats["urls"],
        stats["deleted"],
        stats["skipped_no_parsed"],
        stats["skipped_missing"],
        dry_run,
    )
    return {"chassis": len(wanted), **stats}


def _chassis_deep_pending(db_path: Path, chassis_code: str) -> int:
    norm = normalize_chassis_code(chassis_code) or chassis_code.upper()
    conn = sqlite3.connect(db_path, timeout=60.0)
    try:
        row = conn.execute(
            """
            SELECT COUNT(*) FROM queue
            WHERE status IN ('PENDING', 'PROCESSING')
              AND page_type IN ('section_list', 'diagram')
              AND upper(trim(COALESCE(chassis_code, ''))) = ?
            """,
            (norm,),
        ).fetchone()
    finally:
        conn.close()
    return int(row[0] or 0) if row else 0


async def crawl_maker(
    paths: MakerPaths,
    config: MegazipConfig,
    *,
    max_pages: int | None = None,
    priority_chassis: frozenset[str] | None = None,
    skip_crawl: bool = False,
    priority_model_seeds: tuple[str, ...] = (),
    model_slugs: frozenset[str] | None = None,
    worker_mode: bool = False,
    worker_id: str | None = None,
    rate_limit_seconds: float | None = None,
    prepare_remaining_before: frozenset[str] | None = None,
    chassis_deep_first: bool = True,
    drop_html_after_parse: bool = True,
    prune_on_chassis_complete: bool = True,
    flaresolverr_url: str | None = None,
    force_flaresolverr: bool = False,
    pause_flag: Path | None = None,
    seed_only: bool = False,
) -> dict[str, Any]:
    """Crawl Megazip pages into cache.

    ``worker_mode`` — drain PENDING pages only for leased ``model_slugs``.
    Skips hub/seed enqueue and self-heal. Acquires exclusive model leases so
    parallel workers and the main crawler never claim the same model.

    ``chassis_deep_first`` — after shallow hub/model/variant discovery, drain
    one chassis's section/diagram pages before the next (streamlined disk use).

    ``drop_html_after_parse`` — delete each page's HTML once ``parsed_pages`` is
    written (transform uses parsed payloads; self-heal will not re-fetch).

    ``prune_on_chassis_complete`` — when a focus chassis deep queue empties,
    prune any leftover HTML for that chassis.

    ``seed_only`` — when True with ``priority_model_seeds``, skip maker-hub
    discovery (APK ``--single-chassis`` with known model seeds). Prevents
    burning pages on 90+ unrelated models before diagram depth.
    """
    init_db(paths.state_db)
    paths.cache_dir.mkdir(parents=True, exist_ok=True)

    heal_stats: dict[str, int] = {}
    requeue_stats: dict[str, int] = {}
    prune_stats: dict[str, Any] = {
        "chassis_prunes": [],
        "html_dropped": 0,
        "html_retained": 0,
    }
    leased_models: frozenset[str] = frozenset()
    wid = worker_id or ("worker-" + "-".join(sorted(model_slugs or []))[:80])
    active_priority = priority_chassis
    focus_chassis: str | None = None

    if worker_mode:
        if not model_slugs:
            raise ValueError("worker_mode requires model_slugs")
        acquired, blocked = acquire_model_leases(paths.state_db, wid, model_slugs)
        if blocked:
            logger.warning(
                "[%s] lease blocked for %s (already owned)",
                paths.maker,
                blocked,
            )
        if not acquired:
            raise RuntimeError(
                f"Could not lease any of {sorted(model_slugs)} — held by {blocked}"
            )
        leased_models = acquired
        model_slugs = acquired
        logger.info(
            "[%s] worker %s leased models=%s",
            paths.maker,
            wid,
            ",".join(sorted(leased_models)),
        )
    else:
        # Self-heal a stale/partial queue so a lost cache file or an unproductive
        # hub can never permanently freeze coverage (all makers). Skipped when not
        # crawling, since healing re-queues pages that only a network pass can fetch.
        if not skip_crawl:
            heal_stats = self_heal_queue(paths, config.hub_url(paths.maker))
            stuck = reclaim_stale_processing(paths.state_db)
            if stuck:
                heal_stats["stale_processing_requeued"] = stuck

        if prepare_remaining_before is not None and not skip_crawl:
            requeue_stats = prepare_remaining_crawl(
                paths, priority_chassis=prepare_remaining_before
            )

        start_url = config.hub_url(paths.maker)
        # Priority phase normally discovers models from hub; seeds alone only cover
        # listed URLs. Single-chassis APK runs pass seed_only=True to skip the hub.
        if seed_only and priority_model_seeds:
            logger.info(
                "[%s] seed_only=True — skipping maker hub; seeds=%s",
                paths.maker,
                len(priority_model_seeds),
            )
        elif priority_chassis or not priority_model_seeds:
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
            "requeue": requeue_stats,
            "prune": prune_stats,
            "chassis_deep_first": chassis_deep_first,
            "drop_html_after_parse": drop_html_after_parse,
        }

    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError("httpx required: pip install -e '.[scraping]'") from exc

    pages_done = 0
    rate = float(rate_limit_seconds if rate_limit_seconds is not None else config.rate_limit_seconds)
    last_heartbeat = 0.0

    def _maybe_prune_completed_focus(previous: str | None) -> None:
        if not prune_on_chassis_complete or not previous:
            return
        if _chassis_deep_pending(paths.state_db, previous) > 0:
            return
        stats = prune_chassis_html_cache(paths, [previous], require_parsed=True)
        prune_stats["chassis_prunes"].append({"chassis": previous, **stats})

    try:
        async with httpx.AsyncClient(
            timeout=60.0,
            follow_redirects=True,
            limits=httpx.Limits(max_connections=config.max_concurrent_workers),
        ) as client:
            while True:
                if _pause_requested(pause_flag):
                    logger.info("[%s] cooperative pause requested", paths.maker)
                    prune_stats["paused"] = True
                    break
                if max_pages is not None and pages_done >= max_pages:
                    break

                # Heartbeat leases so other workers see this owner as alive.
                if worker_mode and leased_models:
                    now = asyncio.get_event_loop().time()
                    if now - last_heartbeat >= 30:
                        heartbeat_leases(paths.state_db, wid)
                        last_heartbeat = now

                if worker_mode:
                    left = pending_count(
                        paths.state_db,
                        maker_slug=paths.slug,
                        model_slugs=model_slugs,
                    )
                    if left == 0:
                        break
                    row = claim_next_url(
                        paths.state_db,
                        maker_slug=paths.slug,
                        model_slugs=model_slugs,
                        chassis_deep_first=chassis_deep_first,
                    )
                else:
                    # Main crawler: never touch models leased by workers. If only
                    # leased work remains, wait for workers instead of ending the
                    # priority pass early (which would trigger remaining handoff).
                    left = pending_count(
                        paths.state_db,
                        maker_slug=paths.slug,
                        exclude_leased=True,
                    )
                    if left == 0:
                        leased_left = leased_pending_count(paths.state_db)
                        if leased_left > 0:
                            leases = list_active_leases(paths.state_db)
                            logger.info(
                                "[%s] waiting on %s leased PENDING pages (%s workers)",
                                paths.maker,
                                leased_left,
                                len(set(leases.values())),
                            )
                            await asyncio.sleep(5.0)
                            continue
                        break
                    row = claim_next_url(
                        paths.state_db,
                        maker_slug=paths.slug,
                        exclude_leased=True,
                        chassis_deep_first=chassis_deep_first,
                    )

                if not row:
                    await asyncio.sleep(0.2)
                    continue

                url = row["url"]
                chassis = row.get("chassis_code") or ""
                page_type = row.get("page_type") or ""
                if (
                    chassis_deep_first
                    and page_type in ("section_list", "diagram")
                    and chassis
                ):
                    norm = normalize_chassis_code(chassis) or chassis.upper()
                    if focus_chassis and focus_chassis != norm:
                        _maybe_prune_completed_focus(focus_chassis)
                    focus_chassis = norm

                if active_priority and page_type in ("section_list", "diagram"):
                    if chassis and not _priority_allows(chassis, active_priority):
                        mark_url(paths.state_db, url, ok=True)
                        continue

                await asyncio.sleep(rate + random.uniform(0, rate * 0.3))
                try:
                    status, html = await fetch_html(
                        client,
                        url,
                        flaresolverr_url=flaresolverr_url,
                        force_flaresolverr=force_flaresolverr,
                    )
                    if status >= 400:
                        mark_url(paths.state_db, url, ok=False, error=f"HTTP {status}")
                        continue
                    digest = hashlib.sha256(html.encode("utf-8")).hexdigest()
                    cache_file = paths.cache_dir / f"{cache_key(url)}.html"
                    cache_file.write_text(html, encoding="utf-8")
                    save_cache(paths.state_db, url, str(cache_file), digest)

                    default_engine = ""
                    if page_type == "diagram" or classify_megazip_url(url) == "diagram":
                        default_engine = _engine_for_variant(
                            paths.state_db,
                            maker_slug=paths.slug,
                            model_slug=row.get("model_slug") or "",
                            variant_slug=row.get("variant_slug") or "",
                        )
                    parsed = parse_html_page(
                        html,
                        url,
                        maker_slug=paths.slug,
                        model_slug=row.get("model_slug") or "",
                        variant_slug=row.get("variant_slug") or "",
                        section_slug=row.get("section_slug") or "",
                        default_chassis=chassis,
                        default_engine=default_engine,
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
                                        default_engine=default_engine,
                                        image_bytes=ir.content,
                                    )
                            except Exception as img_exc:  # noqa: BLE001
                                logger.warning(
                                    "[%s] diagram image fetch %s: %s",
                                    paths.maker,
                                    img_url,
                                    img_exc,
                                )

                    upsert_parsed(paths.state_db, url, parsed.page_type, paths.slug, parsed.payload)

                    for item in _discover_from_parsed(parsed, maker_slug=paths.slug):
                        ch = item.get("chassis_code") or chassis
                        if active_priority and item.get("page_type") in (
                            "variant_list",
                            "section_list",
                            "diagram",
                        ):
                            if ch and not _priority_allows(ch, active_priority):
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
                    retain = should_retain_html(parsed.page_type, parsed.payload or {})
                    if drop_html_after_parse and not retain and cache_file.is_file():
                        cache_file.unlink(missing_ok=True)
                        prune_stats["html_dropped"] = int(prune_stats["html_dropped"]) + 1
                    elif retain:
                        prune_stats["html_retained"] = int(prune_stats.get("html_retained") or 0) + 1
                    pages_done += 1
                    if pages_done % 10 == 0:
                        logger.info("[%s] crawl progress: %s pages", paths.maker, pages_done)
                except Exception as exc:  # noqa: BLE001
                    mark_url(paths.state_db, url, ok=False, error=str(exc))
                    logger.warning("[%s] fetch failed %s: %s", paths.maker, url, exc)
    finally:
        if worker_mode and leased_models:
            release_leases(paths.state_db, wid, leased_models)
            logger.info("[%s] worker %s released leases %s", paths.maker, wid, sorted(leased_models))

    _maybe_prune_completed_focus(focus_chassis)

    hub_models = _hub_model_count(paths.state_db, config.hub_url(paths.maker))
    if hub_models == 0 and not worker_mode:
        logger.error(
            "[%s] maker hub yielded 0 models after crawl — model fan-out will be "
            "empty (coverage limited to priority seeds). Check hub markup/parser.",
            paths.maker,
        )

    return {
        "pages_fetched": pages_done,
        "queue": queue_stats(paths.state_db),
        "requeue": requeue_stats,
        "self_heal": heal_stats,
        "prune": prune_stats,
        "chassis_deep_first": chassis_deep_first,
        "drop_html_after_parse": drop_html_after_parse,
        "focus_chassis_last": focus_chassis,
        "hub_models": hub_models,
        "worker_models": sorted(leased_models) if leased_models else [],
        "worker_id": wid if worker_mode else None,
        "paused": bool(prune_stats.get("paused")),
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

            default_engine = _engine_for_variant(
                paths.state_db,
                maker_slug=paths.slug,
                model_slug=model_slug or "",
                variant_slug=variant_slug or "",
            )
            parsed = parse_html_page(
                html,
                url,
                maker_slug=paths.slug,
                model_slug=model_slug or "",
                variant_slug=variant_slug or "",
                section_slug=section_slug or "",
                default_chassis=chassis or "",
                default_engine=default_engine,
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
