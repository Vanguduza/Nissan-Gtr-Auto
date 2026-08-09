"""Fast PostgREST scrub for small megazip value leaks (makers/models/variants/URLs).

Large path rewrites (diagrams / fitment / sections) require applying
``supabase/migrations/20260809120000_scrub_megazip_from_catalog.sql`` in the
Supabase SQL editor (or ``supabase db push`` with DATABASE_URL).
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
    for table in ("catalog_models", "catalog_variants", "catalog_sections"):
        client.table(table).update({"source_url": None}).ilike("source_url", "%megazip%").execute()
        logger.info("nulled %s.source_url", table)

    # Null diagram vendor URLs (paths need SQL REPLACE)
    client.table("catalog_diagrams").update({"source_url": None}).ilike(
        "source_url", "%megazip%"
    ).execute()
    client.table("catalog_diagrams").update({"image_url": None}).ilike(
        "image_url", "%megazip%"
    ).execute()
    logger.info("nulled catalog_diagrams vendor URLs")
    logger.info(
        "Apply SQL migration 20260809120000_scrub_megazip_from_catalog.sql "
        "for storage_path/diagram_path rewrite + megazip_* column renames."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
