"""Build vehicle_master engine rows from Megazip cache HTML (no SQLite writes).

Safe to run while crawl workers hold megazip_state.db. Scans diagram HTML for
``Engine`` attrs + Frame/chassis, then optionally upserts vehicle_master.

Usage (from data-pipeline/)::

  python scripts/extract_engines_from_cache.py
  python scripts/extract_engines_from_cache.py --live-import
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sqlite3
import time
from pathlib import Path
from urllib.parse import urlparse

from data_pipeline.import_catalog import (
    import_supabase,
    load_env_files,
    resolve_supabase_credentials,
)
from data_pipeline.import_hierarchy_catalog import sanitize_legacy_bundle_for_import
from data_pipeline.megazip.config import DEFAULT_OUT_ROOT, MegazipConfig, build_maker_paths
from data_pipeline.megazip.parse_html import _engine_from_attrs, _parse_attrs
from data_pipeline.parse_partsouq_html import normalize_chassis_code

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("extract_engines")

_FRAME_RE = re.compile(
    r'<dt[^>]*s-catalog__attrs-term[^>]*>\s*Frame\s*</dt>\s*'
    r'<dd[^>]*s-catalog__attrs-data[^>]*>(.*?)</dd>',
    re.I | re.S,
)
_TITLE_CHASSIS_RE = re.compile(
    r"Nissan\s+.+?\s+([A-Z0-9]+)\s*\|",
    re.I,
)


def _clean_html(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", text)).strip()


def _model_display(model_slug: str) -> str:
    base = re.sub(r"-\d+$", "", model_slug or "")
    return base.replace("-", " ").title() or model_slug


def _hierarchy_model_variant(maker_name: str, model_slug: str, display_name: str | None = None) -> str:
    """Match hosted import labels: ``Nissan PATHFINDER 2142`` from slug/display."""
    if display_name:
        return f"{maker_name} {display_name}"
    return f"{maker_name} {(model_slug or '').replace('-', ' ').upper()}"


def extract_from_cache(
    state_db: Path,
    cache_dir: Path,
    *,
    maker_name: str = "Nissan",
    maker_slug: str = "nissan",
    limit: int | None = None,
) -> list[dict]:
    # Read-only: URI mode reduces write-lock contention with workers.
    uri = f"file:{state_db.as_posix()}?mode=ro"
    conn = sqlite3.connect(uri, uri=True, timeout=30.0)
    try:
        rows = conn.execute(
            """
            SELECT q.url, q.model_slug, q.chassis_code, c.cache_path
            FROM page_cache c
            LEFT JOIN queue q ON q.url = c.url
            WHERE c.cache_path IS NOT NULL
            """
        ).fetchall()
    finally:
        conn.close()

    vehicles: dict[tuple[str, str, str], dict] = {}
    scanned = 0
    with_engine = 0
    for url, model_slug, chassis_q, cache_path in rows:
        if limit is not None and scanned >= limit:
            break
        scanned += 1
        path = Path(cache_path)
        if not path.is_file():
            # Relative paths under out/megazip/nissan/cache
            alt = cache_dir / path.name
            path = alt if alt.is_file() else path
        if not path.is_file():
            continue
        try:
            html = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        if "s-catalog__attrs-term" not in html:
            continue
        attrs = _parse_attrs(html)
        engine = _engine_from_attrs(attrs)
        if not engine:
            continue
        with_engine += 1
        frame_m = _FRAME_RE.search(html)
        frame = _clean_html(frame_m.group(1)) if frame_m else ""
        chassis = (
            (normalize_chassis_code(chassis_q) if chassis_q else None)
            or (normalize_chassis_code(frame) if frame else None)
            or (frame.upper() if frame else "")
        )
        if not chassis:
            title_m = re.search(r"<title>([^<]+)</title>", html, re.I)
            if title_m:
                tm = _TITLE_CHASSIS_RE.search(title_m.group(1))
                chassis = (tm.group(1).upper() if tm else "")
        if not chassis or chassis.upper() in {"XXXXXXXX", "UNKNOWN", "N/A", "-"}:
            continue
        if not re.match(r"^[A-Z0-9]{2,12}$", chassis.upper()):
            continue
        ms = model_slug or ""
        if not ms and url:
            parts = urlparse(url).path.strip("/").split("/")
            if len(parts) >= 3:
                ms = parts[2]
        model_variant = _hierarchy_model_variant(maker_name, ms)
        key = (chassis, engine, model_variant)
        vehicles[key] = {
            "chassis_code": chassis,
            "engine_code": engine,
            "model_variant": model_variant,
        }

    logger.info(
        "scanned=%s html_with_engine=%s unique_vehicles=%s",
        scanned,
        with_engine,
        len(vehicles),
    )
    return sorted(
        vehicles.values(),
        key=lambda r: (r["model_variant"], r["chassis_code"], r["engine_code"]),
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maker", default="Nissan")
    parser.add_argument("--out-root", type=Path, default=DEFAULT_OUT_ROOT)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--live-import", action="store_true")
    parser.add_argument(
        "--write-json",
        type=Path,
        default=None,
        help="Optional path to write vehicle_master JSON",
    )
    args = parser.parse_args(argv)

    config = MegazipConfig.load(None)
    paths = build_maker_paths(args.maker, args.out_root, config)
    if not paths.state_db.is_file():
        logger.error("state db missing: %s", paths.state_db)
        return 2

    t0 = time.perf_counter()
    rows = extract_from_cache(
        paths.state_db,
        paths.cache_dir,
        maker_name=paths.maker,
        maker_slug=paths.slug,
        limit=args.limit,
    )
    logger.info("extract done in %.1fs (%s rows)", time.perf_counter() - t0, len(rows))

    out_path = args.write_json or (paths.bundle_dir / "vehicle_master_engines.json")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(rows, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    logger.info("wrote %s", out_path)

    engines_by_chassis: dict[str, set[str]] = {}
    for r in rows:
        engines_by_chassis.setdefault(r["chassis_code"], set()).add(r["engine_code"])
    sample = {k: sorted(v) for k, v in list(engines_by_chassis.items())[:12]}
    logger.info("sample chassis→engines: %s", sample)

    if not args.live_import:
        logger.info("dry run — pass --live-import to upsert vehicle_master")
        return 0

    repo_root = Path(__file__).resolve().parents[2]
    pipeline_root = Path(__file__).resolve().parents[1]
    load_env_files(pipeline_root / ".env", repo_root / ".env", override=True)
    url, key = resolve_supabase_credentials()
    host = urlparse(url or "").hostname or ""
    if not url or not key:
        logger.error("missing Supabase credentials")
        return 2
    if host in {"127.0.0.1", "localhost"}:
        logger.error("refusing import to local %s", host)
        return 3

    sanitized = sanitize_legacy_bundle_for_import(
        {
            "vehicle_master": rows,
            "pnc_categories": [],
            "part_fitment": [],
            "diagram_assets": [],
        }
    )
    result = import_supabase(
        {
            "vehicle_master": sanitized["vehicle_master"],
            "pnc_categories": [],
            "part_fitment": [],
            "diagram_assets": [],
        },
        url=url,
        key=key,
        ensure_stock_items=False,
        prune_stale=False,
    )
    logger.info("vehicle_master import: %s", result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
