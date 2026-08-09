"""Scrub megazip strings from hosted catalog via PostgREST (no DDL).

Column renames (megazip_* → external_*) require applying
supabase/migrations/20260809120000_scrub_megazip_from_catalog.sql via
`supabase db push` / SQL editor. This script updates row values only.
"""

from __future__ import annotations

import logging
from pathlib import Path
from urllib.parse import urlparse

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials
from supabase import create_client

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logger = logging.getLogger("scrub_megazip")

PAGE = 500


def _rewrite_path(path: str | None) -> str | None:
    if not path:
        return path
    if path.lower().startswith("megazip/"):
        return "epc/" + path[len("megazip/") :]
    return path


def _null_if_vendor(url: str | None) -> str | None:
    if url and "megazip" in url.lower():
        return None
    return url


def main() -> int:
    repo = Path(__file__).resolve().parents[2]
    pipe = Path(__file__).resolve().parents[1]
    load_env_files(pipe / ".env", repo / ".env", override=True)
    url, key = resolve_supabase_credentials()
    host = urlparse(url or "").hostname or ""
    logger.info("host=%s", host)
    if not url or not key or host in {"127.0.0.1", "localhost"}:
        logger.error("hosted credentials required")
        return 2
    client = create_client(url, key)

    client.table("catalog_makers").update({"source": "epc"}).ilike("source", "%megazip%").execute()
    logger.info("makers source scrubbed")

    for table, col in (
        ("catalog_models", "source_url"),
        ("catalog_variants", "source_url"),
        ("catalog_sections", "source_url"),
    ):
        # Null vendor URLs in pages
        offset = 0
        updated = 0
        while True:
            rows = (
                client.table(table)
                .select(f"id,{col}")
                .ilike(col, "%megazip%")
                .range(offset, offset + PAGE - 1)
                .execute()
                .data
                or []
            )
            if not rows:
                break
            for row in rows:
                client.table(table).update({col: None}).eq("id", row["id"]).execute()
                updated += 1
            if len(rows) < PAGE:
                break
            # after updates, keep offset 0 (rows disappear from filter)
        logger.info("%s.%s nulled=%s", table, col, updated)

    # diagrams: paths + urls
    diag_updated = 0
    while True:
        rows = (
            client.table("catalog_diagrams")
            .select("id,storage_path,source_url,image_url")
            .or_(
                "storage_path.ilike.%megazip%,source_url.ilike.%megazip%,image_url.ilike.%megazip%"
            )
            .limit(PAGE)
            .execute()
            .data
            or []
        )
        if not rows:
            break
        for row in rows:
            payload = {
                "storage_path": _rewrite_path(row.get("storage_path")),
                "source_url": _null_if_vendor(row.get("source_url")),
                "image_url": _null_if_vendor(row.get("image_url")),
            }
            client.table("catalog_diagrams").update(payload).eq("id", row["id"]).execute()
            diag_updated += 1
        logger.info("catalog_diagrams progress %s", diag_updated)
    logger.info("catalog_diagrams done %s", diag_updated)

    for table, col in (
        ("catalog_diagram_parts", "diagram_path"),
        ("part_fitment", "diagram_path"),
    ):
        n = 0
        while True:
            rows = (
                client.table(table)
                .select(f"id,{col}")
                .ilike(col, "megazip/%")
                .limit(PAGE)
                .execute()
                .data
                or []
            )
            if not rows:
                break
            for row in rows:
                new_path = _rewrite_path(row.get(col))
                client.table(table).update({col: new_path}).eq("id", row["id"]).execute()
                n += 1
            if n % 5000 == 0:
                logger.info("%s progress %s", table, n)
        logger.info("%s done %s", table, n)

    logger.info("DONE value scrub (apply SQL migration for column renames)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
