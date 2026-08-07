"""Parse PartSouq HTML cache into catalog data without touching the live crawl.

Reads visited URLs + cached HTML only. Writes parsed parts into ``scraped_data``
and tracks progress in a separate SQLite file so the scraper queue is undisturbed.

Also builds a ``vid → chassis → vin_prefix`` map from ``/vehicle`` pages and
backfills already-parsed parts rows when identity is learned.

Usage (from data-pipeline/)::

  # one pass over what is already cached
  python -m data_pipeline.cache_parse_worker --once

  # keep watching for new cache files while scrape runs
  python -m data_pipeline.cache_parse_worker --watch --poll-seconds 10 --batch-size 75 --write-bundle-every 17
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import sqlite3
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from data_pipeline.amayama_catalog_auto import (
    ResponseCache,
    decode_from_chassis,
    enrich_hints_with_vin,
    extract_page_vehicle_meta,
    init_db,
    load_scraped_records,
    merge_bundles,
    store_payload,
    transform_raw_records,
    write_bundle,
    _omit_none,
)
from data_pipeline.chassis_discovery import ensure_schema as ensure_unmapped_schema
from data_pipeline.chassis_discovery import set_discovery_db
from data_pipeline.parse_partsouq_html import (
    is_partsouq_parts_html,
    is_partsouq_vehicle_html,
    is_partsouq_vehicle_url,
    parse_partsouq_parts_html,
    parse_partsouq_vehicle_html,
    vid_from_url,
)

logger = logging.getLogger(__name__)

DEFAULT_STATE_DB = Path("crawler_state.db")
DEFAULT_CACHE_DIR = Path("out/partsouq_cache")
DEFAULT_PARSE_DB = Path("out/cache_parse_state.db")
DEFAULT_OUT_DIR = Path("out/partsouq_bundle")

# Identity fields stamped onto payload vehicle + vehicle_context
_IDENTITY_HINT_KEYS = (
    "chassis_code",
    "model_variant",
    "engine_code",
    "production_year",
    "year_start",
    "year_end",
    "grade",
    "market",
    "vin_prefix",
    "vid",
)


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _digest(url: str) -> str:
    return hashlib.sha256(url.encode("utf-8")).hexdigest()


def init_parse_db(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path, timeout=60.0)
    try:
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS parse_queue (
                url TEXT PRIMARY KEY,
                digest TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                diagrams INTEGER NOT NULL DEFAULT 0,
                parts INTEGER NOT NULL DEFAULT 0,
                error TEXT,
                enqueued_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_parse_queue_status ON parse_queue(status)"
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS vehicle_identity (
                vid TEXT PRIMARY KEY,
                chassis_code TEXT,
                chassis_raw TEXT,
                model_variant TEXT,
                grade TEXT,
                market TEXT,
                production_year INTEGER,
                year_start INTEGER,
                year_end INTEGER,
                engine_code TEXT,
                vin_prefix TEXT,
                source_url TEXT,
                payload_json TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_vehicle_identity_chassis "
            "ON vehicle_identity(chassis_code)"
        )
        conn.commit()
    finally:
        conn.close()
    # Unmapped chassis discovery lives in the same parse DB during watch
    ensure_unmapped_schema(path)


def list_visited_urls(crawl_db: Path) -> list[str]:
    """Read-only view of URLs the scraper has finished."""
    conn = sqlite3.connect(f"file:{crawl_db.resolve()}?mode=ro", uri=True, timeout=60.0)
    try:
        rows = conn.execute(
            "SELECT url FROM queue WHERE status = 'VISITED' ORDER BY url"
        ).fetchall()
        return [str(r[0]) for r in rows]
    finally:
        conn.close()


def identity_hints(row: dict[str, Any]) -> dict[str, Any]:
    """Hints suitable for enrich_hints_with_vin / vehicle_context."""
    hints = {k: row[k] for k in _IDENTITY_HINT_KEYS if row.get(k) is not None}
    # Pass source_url for unmapped-chassis discovery examples; enrich strips it.
    if row.get("source_url"):
        hints["source_url"] = row["source_url"]
    return enrich_hints_with_vin(hints)


def upsert_vehicle_identity(parse_db: Path, identity: dict[str, Any]) -> dict[str, Any]:
    """Persist vid identity and return enriched hints (incl. vin_prefix)."""
    vid = str(identity.get("vid") or "").strip()
    if not vid:
        raise ValueError("vehicle identity requires vid")

    hints = identity_hints(identity)
    now = _utc_now()
    stored = {
        **identity,
        "vid": vid,
        "vin_prefix": hints.get("vin_prefix") or identity.get("vin_prefix"),
        "chassis_code": hints.get("chassis_code") or identity.get("chassis_code"),
        "model_variant": hints.get("model_variant") or identity.get("model_variant"),
        "engine_code": hints.get("engine_code") or identity.get("engine_code"),
        "production_year": hints.get("production_year") or identity.get("production_year"),
        "year_start": hints.get("year_start") or identity.get("year_start"),
        "year_end": hints.get("year_end") or identity.get("year_end"),
    }
    conn = sqlite3.connect(parse_db, timeout=60.0)
    try:
        conn.execute(
            """
            INSERT INTO vehicle_identity (
                vid, chassis_code, chassis_raw, model_variant, grade, market,
                production_year, year_start, year_end, engine_code, vin_prefix,
                source_url, payload_json, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(vid) DO UPDATE SET
                chassis_code = excluded.chassis_code,
                chassis_raw = excluded.chassis_raw,
                model_variant = excluded.model_variant,
                grade = excluded.grade,
                market = excluded.market,
                production_year = excluded.production_year,
                year_start = excluded.year_start,
                year_end = excluded.year_end,
                engine_code = excluded.engine_code,
                vin_prefix = excluded.vin_prefix,
                source_url = excluded.source_url,
                payload_json = excluded.payload_json,
                updated_at = excluded.updated_at
            """,
            (
                vid,
                stored.get("chassis_code"),
                stored.get("chassis_raw"),
                stored.get("model_variant"),
                stored.get("grade"),
                stored.get("market"),
                stored.get("production_year"),
                stored.get("year_start"),
                stored.get("year_end"),
                stored.get("engine_code"),
                stored.get("vin_prefix"),
                stored.get("source_url"),
                json.dumps(stored, ensure_ascii=False),
                now,
            ),
        )
        conn.commit()
    finally:
        conn.close()
    return identity_hints(stored)


def get_vehicle_identity(parse_db: Path, vid: str) -> dict[str, Any] | None:
    if not vid:
        return None
    conn = sqlite3.connect(parse_db, timeout=60.0)
    try:
        row = conn.execute(
            "SELECT payload_json FROM vehicle_identity WHERE vid = ?", (str(vid),)
        ).fetchone()
    finally:
        conn.close()
    if not row or not row[0]:
        return None
    try:
        data = json.loads(row[0])
    except json.JSONDecodeError:
        return None
    return data if isinstance(data, dict) else None


def list_vehicle_identities(parse_db: Path) -> list[dict[str, Any]]:
    conn = sqlite3.connect(parse_db, timeout=60.0)
    try:
        rows = conn.execute("SELECT payload_json FROM vehicle_identity").fetchall()
    finally:
        conn.close()
    out: list[dict[str, Any]] = []
    for (raw,) in rows:
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            continue
        if isinstance(data, dict) and data.get("vid"):
            out.append(data)
    return out


def has_vehicle_identity(parse_db: Path, vid: str) -> bool:
    if not vid:
        return False
    conn = sqlite3.connect(parse_db, timeout=60.0)
    try:
        row = conn.execute(
            "SELECT 1 FROM vehicle_identity WHERE vid = ? AND chassis_code IS NOT NULL",
            (str(vid),),
        ).fetchone()
        return row is not None
    finally:
        conn.close()


def _merge_identity_into(target: dict[str, Any], hints: dict[str, Any]) -> dict[str, Any]:
    merged = dict(target)
    noise_engines = {"FS12PX", "MT10", "X25"}
    for key in _IDENTITY_HINT_KEYS:
        value = hints.get(key)
        if value is None:
            continue
        if key in {"chassis_code", "vin_prefix", "grade", "market", "vid"}:
            merged[key] = value
            continue
        if key == "engine_code":
            existing = str(merged.get("engine_code") or "")
            if not existing or existing in noise_engines or existing.endswith("PX"):
                merged[key] = value
            continue
        if not merged.get(key):
            merged[key] = value
    return enrich_hints_with_vin(merged)


def apply_identity_to_payloads(
    payloads: list[dict[str, Any]],
    hints: dict[str, Any],
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for payload in payloads:
        vehicle = payload.get("vehicle") if isinstance(payload.get("vehicle"), dict) else {}
        stamped = dict(payload)
        stamped["vehicle"] = _merge_identity_into(vehicle, hints)
        out.append(stamped)
    return out


def _row_vid(source_url: str, payload: dict[str, Any], context: dict[str, Any] | None) -> str | None:
    for blob in (context, payload.get("vehicle") if isinstance(payload.get("vehicle"), dict) else None):
        if isinstance(blob, dict) and blob.get("vid"):
            return str(blob["vid"])
    return vid_from_url(source_url)


def _context_needs_enrichment(context: dict[str, Any] | None, hints: dict[str, Any]) -> bool:
    if not hints.get("chassis_code"):
        return False
    if not context:
        return True
    if not context.get("chassis_code"):
        return True
    if hints.get("vin_prefix") and not context.get("vin_prefix"):
        return True
    if hints.get("chassis_code") and context.get("chassis_code") != hints.get("chassis_code"):
        # Parts page used model name as chassis — replace with real code
        if str(context.get("chassis_code") or "").upper() != str(hints["chassis_code"]).upper():
            return True
    return False


def backfill_scraped_for_vid(
    crawl_db: Path,
    parse_db: Path,
    vid: str,
    hints: dict[str, Any] | None = None,
) -> int:
    """Re-stamp existing scraped_data rows for a vid. Does not touch crawl queue."""
    if not vid:
        return 0
    resolved = hints
    if not resolved or not resolved.get("chassis_code"):
        identity = get_vehicle_identity(parse_db, vid)
        if not identity:
            return 0
        resolved = identity_hints(identity)
    if not resolved.get("chassis_code"):
        return 0

    init_db(crawl_db)
    conn = sqlite3.connect(crawl_db, timeout=60.0)
    updated = 0
    vid_s = str(vid)
    try:
        # Narrow candidates first — full-table JSON parse is too slow under live scrape.
        rows = conn.execute(
            """
            SELECT id, source_url, payload, vehicle_context FROM scraped_data
            WHERE source_url LIKE ?
               OR IFNULL(vehicle_context, '') LIKE ?
               OR payload LIKE ?
            """,
            (f"%vid={vid_s}%", f'%"{vid_s}"%', f'%"{vid_s}"%'),
        ).fetchall()
        for row_id, source_url, raw_payload, raw_ctx in rows:
            try:
                payload = json.loads(raw_payload)
            except json.JSONDecodeError:
                continue
            if not isinstance(payload, dict):
                continue
            context = None
            if raw_ctx:
                try:
                    loaded = json.loads(raw_ctx)
                    if isinstance(loaded, dict):
                        context = loaded
                except json.JSONDecodeError:
                    context = None
            row_vid = _row_vid(str(source_url), payload, context)
            if row_vid != vid_s:
                continue
            vehicle = payload.get("vehicle") if isinstance(payload.get("vehicle"), dict) else None
            if not _context_needs_enrichment(context, resolved) and not _context_needs_enrichment(
                vehicle, resolved
            ):
                continue
            payload = dict(payload)
            payload["vehicle"] = _merge_identity_into(vehicle or {}, resolved)
            new_ctx = _merge_identity_into(context or {}, resolved)
            conn.execute(
                "UPDATE scraped_data SET payload = ?, vehicle_context = ? WHERE id = ?",
                (json.dumps(payload), json.dumps(new_ctx), row_id),
            )
            updated += 1
        if updated:
            conn.commit()
    finally:
        conn.close()
    return updated


def reenrich_vehicle_identities(parse_db: Path) -> dict[str, int]:
    """Re-upsert identities so chassis→vin_prefix map changes land in vehicle_identity."""
    updated = 0
    with_prefix = 0
    for identity in list_vehicle_identities(parse_db):
        vid = str(identity.get("vid") or "")
        if not vid or not identity.get("chassis_code"):
            continue
        hints = upsert_vehicle_identity(parse_db, identity)
        updated += 1
        if hints.get("vin_prefix"):
            with_prefix += 1
    return {"updated": updated, "with_vin_prefix": with_prefix}


def backfill_all_identities(crawl_db: Path, parse_db: Path) -> dict[str, int]:
    """Apply every known vehicle_identity to matching scraped_data rows."""
    total_rows = 0
    vids = 0
    for identity in list_vehicle_identities(parse_db):
        vid = str(identity.get("vid") or "")
        if not vid or not identity.get("chassis_code"):
            continue
        n = backfill_scraped_for_vid(crawl_db, parse_db, vid, identity_hints(identity))
        if n:
            vids += 1
            total_rows += n
    return {"vids": vids, "rows": total_rows}


def _scraped_incomplete_for_url(crawl_db: Path, url: str, hints: dict[str, Any]) -> bool:
    """True when scraped rows for url lack chassis/vin_prefix vs known identity."""
    conn = sqlite3.connect(f"file:{crawl_db.resolve()}?mode=ro", uri=True, timeout=60.0)
    try:
        rows = conn.execute(
            "SELECT payload, vehicle_context FROM scraped_data WHERE source_url = ?",
            (url,),
        ).fetchall()
    finally:
        conn.close()
    if not rows:
        return False
    for raw_payload, raw_ctx in rows:
        context = None
        if raw_ctx:
            try:
                loaded = json.loads(raw_ctx)
                if isinstance(loaded, dict):
                    context = loaded
            except json.JSONDecodeError:
                context = None
        try:
            payload = json.loads(raw_payload)
        except json.JSONDecodeError:
            continue
        vehicle = payload.get("vehicle") if isinstance(payload, dict) else None
        if _context_needs_enrichment(context, hints) or _context_needs_enrichment(
            vehicle if isinstance(vehicle, dict) else None, hints
        ):
            return True
    return False


def enqueue_new_from_cache(
    *,
    crawl_db: Path,
    cache_dir: Path,
    parse_db: Path,
) -> int:
    """Add visited URLs that have HTML on disk and are new/changed to the parse queue.

    Also re-queues:
    - vehicle pages that never produced a vehicle_identity row
    - DONE parts pages whose scraped rows lack chassis/vin_prefix while identity exists
    """
    cache = ResponseCache(cache_dir)
    visited = list_visited_urls(crawl_db)
    added = 0
    conn = sqlite3.connect(parse_db, timeout=60.0)
    try:
        for url in visited:
            if "partsouq.com" not in urlparse(url).netloc.lower():
                continue
            path = cache_dir / f"{_digest(url)}.html"
            if not path.exists():
                if cache.get_text(url) is None:
                    continue
            digest = _digest(url)
            row = conn.execute(
                "SELECT digest, status FROM parse_queue WHERE url = ?", (url,)
            ).fetchone()
            now = _utc_now()
            is_vehicle = is_partsouq_vehicle_url(url)
            vid = vid_from_url(url)

            requeue = False
            reason = ""
            if row is None:
                conn.execute(
                    """
                    INSERT INTO parse_queue
                        (url, digest, status, enqueued_at, updated_at)
                    VALUES (?, ?, 'PENDING', ?, ?)
                    """,
                    (url, digest, now, now),
                )
                added += 1
                continue

            old_digest, status = str(row[0]), str(row[1])
            if old_digest != digest or status == "ERROR":
                requeue = True
                reason = "digest/error"
            elif (
                is_vehicle
                and status in {"DONE", "EMPTY"}
                and vid
                and not has_vehicle_identity(parse_db, vid)
            ):
                requeue = True
                reason = "vehicle missing identity"
            # Parts enrichment is handled by backfill_all_identities in run_pass —
            # do not touch crawl_db per URL here (locks against the live scrape).

            if requeue:
                conn.execute(
                    """
                    UPDATE parse_queue
                    SET digest = ?, status = 'PENDING', error = NULL, updated_at = ?
                    WHERE url = ?
                    """,
                    (digest, now, url),
                )
                added += 1
                logger.debug("Re-queued %s (%s)", url[:80], reason)
        conn.commit()
    finally:
        conn.close()
    return added


def _claim_batch(parse_db: Path, limit: int) -> list[tuple[str, str]]:
    conn = sqlite3.connect(parse_db, timeout=60.0)
    try:
        rows = conn.execute(
            """
            SELECT url, digest FROM parse_queue
            WHERE status = 'PENDING'
            ORDER BY enqueued_at
            LIMIT ?
            """,
            (limit,),
        ).fetchall()
        now = _utc_now()
        out: list[tuple[str, str]] = []
        for url, digest in rows:
            conn.execute(
                """
                UPDATE parse_queue
                SET status = 'PROCESSING', updated_at = ?
                WHERE url = ? AND status = 'PENDING'
                """,
                (now, url),
            )
            if conn.total_changes:
                out.append((str(url), str(digest)))
        conn.commit()
        return out
    finally:
        conn.close()


def _mark_done(
    parse_db: Path,
    url: str,
    *,
    status: str,
    diagrams: int = 0,
    parts: int = 0,
    error: str | None = None,
) -> None:
    conn = sqlite3.connect(parse_db, timeout=60.0)
    try:
        conn.execute(
            """
            UPDATE parse_queue
            SET status = ?, diagrams = ?, parts = ?, error = ?, updated_at = ?
            WHERE url = ?
            """,
            (status, diagrams, parts, error, _utc_now(), url),
        )
        conn.commit()
    finally:
        conn.close()


def _replace_scraped_payloads(
    crawl_db: Path,
    source_url: str,
    payloads: list[dict[str, Any]],
    vehicle_context: dict[str, Any] | None,
) -> None:
    """Swap scraped_data rows for this URL. Does not touch the crawl queue table."""
    init_db(crawl_db)
    conn = sqlite3.connect(crawl_db, timeout=60.0)
    try:
        conn.execute("DELETE FROM scraped_data WHERE source_url = ?", (source_url,))
        conn.commit()
    finally:
        conn.close()
    for payload in payloads:
        store_payload(crawl_db, source_url, payload, vehicle_context)


def parse_one(
    *,
    url: str,
    cache_dir: Path,
    crawl_db: Path,
    parse_db: Path,
    base_url: str,
) -> tuple[str, int, int]:
    """Parse one cached URL.

    Returns ``(kind, diagrams, parts)`` where kind is ``parts``, ``identity``, or ``empty``.
    """
    cache = ResponseCache(cache_dir)
    html = cache.get_text(url)
    if not html:
        raise FileNotFoundError(f"No cached HTML for {url}")

    # Vehicle identity pages (build vid → chassis map)
    if is_partsouq_vehicle_url(url) or is_partsouq_vehicle_html(html):
        if is_partsouq_vehicle_html(html):
            identity = parse_partsouq_vehicle_html(html, source_url=url)
            if identity and identity.get("vid"):
                hints = upsert_vehicle_identity(parse_db, identity)
                backfilled = backfill_scraped_for_vid(
                    crawl_db, parse_db, str(identity["vid"]), hints
                )
                logger.info(
                    "Identity vid=%s chassis=%s vin_prefix=%s backfilled=%s",
                    identity.get("vid"),
                    hints.get("chassis_code"),
                    hints.get("vin_prefix"),
                    backfilled,
                )
                return "identity", 0, 0
        return "empty", 0, 0

    if not is_partsouq_parts_html(html):
        return "empty", 0, 0

    payloads = parse_partsouq_parts_html(html, source_url=url, base_url=base_url)
    if not payloads:
        return "empty", 0, 0

    page_meta = extract_page_vehicle_meta(html)
    context = {**page_meta, **(payloads[0].get("vehicle") or {})}
    vid = str(context.get("vid") or vid_from_url(url) or "")
    if vid:
        identity = get_vehicle_identity(parse_db, vid)
        if identity and identity.get("chassis_code"):
            hints = identity_hints(identity)
            payloads = apply_identity_to_payloads(payloads, hints)
            context = _merge_identity_into(context, hints)

    context = enrich_hints_with_vin(context)
    _replace_scraped_payloads(crawl_db, url, payloads, context)
    parts = sum(len(p.get("parts") or []) for p in payloads)
    return "parts", len(payloads), parts


def vehicle_master_from_identities(identities: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Build vehicle_master rows from /vehicle identity map (no parts required)."""
    rows: list[dict[str, Any]] = []
    for identity in identities:
        chassis = identity.get("chassis_code")
        if not chassis:
            continue
        hints = identity_hints(identity)
        model_variant = (
            hints.get("model_variant") or identity.get("model_variant") or "Unknown"
        )
        year_expand = hints.get("_year_expand")
        if isinstance(year_expand, list) and year_expand:
            for year in year_expand:
                rows.append(
                    _omit_none(
                        {
                            "vin_prefix": hints.get("vin_prefix") or hints.get("_vin_prefix"),
                            "chassis_code": str(chassis),
                            "engine_code": hints.get("engine_code"),
                            "production_year": year,
                            "model_variant": model_variant,
                        }
                    )
                )
            continue
        for decoded in decode_from_chassis(
            str(chassis),
            engine_code=hints.get("engine_code"),
            production_year=hints.get("production_year"),
            model_variant=model_variant,
            year_start=hints.get("year_start"),
            year_end=hints.get("year_end"),
            example_vid=str(identity.get("vid") or "") or None,
            example_url=str(identity.get("source_url") or "") or None,
            discover_unmapped=False,
        ):
            rows.append(_omit_none(decoded.as_hints() | {"model_variant": model_variant}))
    return rows


def bundle_quality_counts(bundle: dict[str, Any]) -> dict[str, int]:
    """Catalog enrichment metrics for ops checks (categories, part display names)."""
    pncs = bundle.get("pnc_categories") or []
    uncat = sum(
        1
        for p in pncs
        if str(p.get("category_name") or "").lower() == "uncategorized"
    )
    oem_names = bundle.get("_oem_display_names") or {}
    return {
        "uncategorized_pncs": uncat,
        "oem_display_names": len(oem_names) if isinstance(oem_names, dict) else 0,
    }


def refresh_bundle(*, crawl_db: Path, parse_db: Path, out_dir: Path) -> dict[str, int]:
    records = load_scraped_records(crawl_db)
    parts_bundle = transform_raw_records(records, validate=False)
    identities = list_vehicle_identities(parse_db)
    identity_bundle = {
        "vehicle_master": vehicle_master_from_identities(identities),
        "pnc_categories": [],
        "part_fitment": [],
        "diagram_assets": [],
    }
    # Parts-derived rows win on duplicate natural keys (richer engine/year from hotspots).
    bundle = merge_bundles([identity_bundle, parts_bundle])
    write_bundle(bundle, out_dir)
    quality = bundle_quality_counts(bundle)
    counts = {
        "vehicles": len(bundle.get("vehicle_master") or []),
        "vehicles_from_identity": len(identity_bundle["vehicle_master"]),
        "vehicles_from_parts": len(parts_bundle.get("vehicle_master") or []),
        "fitments": len(bundle.get("part_fitment") or []),
        "diagrams": len(bundle.get("diagram_assets") or []),
        "pncs": len(bundle.get("pnc_categories") or []),
        "records": len(records),
        "identities": len(identities),
        **quality,
    }
    meta_path = out_dir / "parse_bundle_meta.json"
    meta_path.write_text(json.dumps(counts, indent=2) + "\n", encoding="utf-8")
    if quality["uncategorized_pncs"]:
        logger.warning(
            "Bundle has %s uncategorized PNCs — check diagram_title / cname enrichment",
            quality["uncategorized_pncs"],
        )
    return counts


def queue_stats(parse_db: Path) -> dict[str, int]:
    conn = sqlite3.connect(parse_db, timeout=60.0)
    try:
        rows = conn.execute(
            "SELECT status, COUNT(*) FROM parse_queue GROUP BY status"
        ).fetchall()
        return {str(status): int(count) for status, count in rows}
    finally:
        conn.close()


def run_pass(
    *,
    crawl_db: Path,
    cache_dir: Path,
    parse_db: Path,
    out_dir: Path,
    base_url: str,
    batch_size: int,
    write_bundle_every: int,
    limit: int = 0,
    catch_up: bool = True,
) -> dict[str, Any]:
    set_discovery_db(parse_db)
    init_parse_db(parse_db)
    enqueued = enqueue_new_from_cache(
        crawl_db=crawl_db, cache_dir=cache_dir, parse_db=parse_db
    )

    backfill_stats: dict[str, int] = {"vids": 0, "rows": 0}
    if catch_up:
        backfill_stats = backfill_all_identities(crawl_db, parse_db)
        if backfill_stats["rows"]:
            logger.info("Catch-up backfill %s", backfill_stats)

    processed = 0
    diagrams = 0
    parts = 0
    identities = 0
    errors = 0
    empties = 0
    since_bundle = 0
    identity_updated = False

    while True:
        if limit > 0 and processed >= limit:
            break
        take = batch_size if limit <= 0 else min(batch_size, limit - processed)
        batch = _claim_batch(parse_db, take)
        if not batch:
            break
        for url, _digest in batch:
            try:
                kind, d_count, p_count = parse_one(
                    url=url,
                    cache_dir=cache_dir,
                    crawl_db=crawl_db,
                    parse_db=parse_db,
                    base_url=base_url,
                )
                if kind == "empty":
                    _mark_done(parse_db, url, status="EMPTY")
                    empties += 1
                elif kind == "identity":
                    _mark_done(parse_db, url, status="DONE", diagrams=0, parts=0)
                    identities += 1
                    identity_updated = True
                else:
                    _mark_done(
                        parse_db,
                        url,
                        status="DONE",
                        diagrams=d_count,
                        parts=p_count,
                    )
                    diagrams += d_count
                    parts += p_count
                processed += 1
                since_bundle += 1
            except Exception as exc:  # noqa: BLE001
                logger.exception("Parse failed for %s", url)
                _mark_done(parse_db, url, status="ERROR", error=str(exc)[:500])
                errors += 1
                processed += 1

            if write_bundle_every > 0 and since_bundle >= write_bundle_every:
                counts = refresh_bundle(
                    crawl_db=crawl_db, parse_db=parse_db, out_dir=out_dir
                )
                logger.info("Bundle refresh %s", counts)
                since_bundle = 0

            if limit > 0 and processed >= limit:
                break

    # Same-pass catch-up: identity learned mid-pass must stamp existing parts rows
    if identity_updated:
        extra = backfill_all_identities(crawl_db, parse_db)
        if extra.get("rows"):
            logger.info("Post-identity backfill %s", extra)
            backfill_stats = {
                "vids": backfill_stats.get("vids", 0) + extra.get("vids", 0),
                "rows": backfill_stats.get("rows", 0) + extra.get("rows", 0),
            }

    bundle_counts: dict[str, int] = {}
    if processed or backfill_stats.get("rows") or identity_updated:
        bundle_counts = refresh_bundle(
            crawl_db=crawl_db, parse_db=parse_db, out_dir=out_dir
        )

    identity_count = 0
    conn = sqlite3.connect(parse_db, timeout=60.0)
    try:
        row = conn.execute("SELECT COUNT(*) FROM vehicle_identity").fetchone()
        identity_count = int(row[0]) if row else 0
    finally:
        conn.close()

    return {
        "enqueued": enqueued,
        "processed": processed,
        "diagrams": diagrams,
        "parts": parts,
        "identities": identities,
        "identity_rows": identity_count,
        "backfill": backfill_stats,
        "empty": empties,
        "errors": errors,
        "queue": queue_stats(parse_db),
        "bundle": bundle_counts,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Parse PartSouq HTML cache into catalog hotspots (safe alongside crawl)"
    )
    parser.add_argument("--state-db", type=Path, default=DEFAULT_STATE_DB)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR)
    parser.add_argument("--parse-db", type=Path, default=DEFAULT_PARSE_DB)
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR)
    parser.add_argument("--base-url", default="https://partsouq.com")
    parser.add_argument("--batch-size", type=int, default=75)
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Max pages to parse this pass (0=all pending)",
    )
    parser.add_argument(
        "--write-bundle-every",
        type=int,
        default=17,
        help="Rewrite catalog JSON every N parsed pages (0=only at end of pass)",
    )
    parser.add_argument("--once", action="store_true", help="Single pass then exit")
    parser.add_argument("--watch", action="store_true", help="Loop and pick up new cache files")
    parser.add_argument("--poll-seconds", type=float, default=10.0)
    parser.add_argument(
        "--no-catch-up",
        action="store_true",
        help="Skip start-of-pass backfill of scraped_data from vehicle_identity",
    )
    parser.add_argument(
        "--brand",
        default="",
        help="Maker slug for VIN/chassis enrichment (default: nissan or scrape.json allowed_brand)",
    )
    parser.add_argument(
        "--scrape-config",
        type=Path,
        default=None,
        help="Optional scrape.json to read allowed_brand + chassis_catalogs_path",
    )
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)

    if not args.once and not args.watch:
        args.once = True

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    from data_pipeline.amayama_catalog_auto import (
        CHASSIS_CATALOG,
        refresh_chassis_catalog_view,
    )
    from data_pipeline.chassis_catalog_registry import (
        load_catalogs,
        set_active_brand,
    )

    brand = (args.brand or "").strip()
    cat_path = None
    if args.scrape_config and args.scrape_config.exists():
        import json as _json

        scrape = _json.loads(args.scrape_config.read_text(encoding="utf-8"))
        brand = brand or str(scrape.get("allowed_brand") or "")
        raw_cat = scrape.get("chassis_catalogs_path")
        if raw_cat:
            cat_path = Path(raw_cat)
            if not cat_path.is_absolute():
                cat_path = Path.cwd() / cat_path
    load_catalogs(path=cat_path, force=True)
    set_active_brand(brand or "nissan")
    refresh_chassis_catalog_view()
    logging.getLogger(__name__).info(
        "Parse VIN enrichment brand=%s curated_chassis=%s",
        brand or "nissan",
        len(CHASSIS_CATALOG),
    )

    if not args.state_db.exists():
        logger.error("Crawl DB not found: %s", args.state_db)
        return 1
    if not args.cache_dir.exists():
        logger.error("Cache dir not found: %s", args.cache_dir)
        return 1

    # Durable catch-up on watcher start (and every poll via run_pass)
    logger.info("Starting cache parse worker (catch_up=%s)", not args.no_catch_up)

    while True:
        summary = run_pass(
            crawl_db=args.state_db,
            cache_dir=args.cache_dir,
            parse_db=args.parse_db,
            out_dir=args.out_dir,
            base_url=args.base_url,
            batch_size=args.batch_size,
            write_bundle_every=args.write_bundle_every,
            limit=args.limit,
            catch_up=not args.no_catch_up,
        )
        logger.info("Parse pass: %s", summary)
        if args.once:
            break
        time.sleep(max(1.0, args.poll_seconds))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
