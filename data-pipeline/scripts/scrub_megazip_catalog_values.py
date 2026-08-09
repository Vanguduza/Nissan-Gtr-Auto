"""Scrub megazip strings from hosted catalog via bulk PostgREST filters."""

from __future__ import annotations

import logging
from pathlib import Path
from urllib.parse import urlparse

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials
from supabase import create_client

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logger = logging.getLogger("scrub_megazip")

PAGE = 1000


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
    logger.info("makers OK")

    for table in ("catalog_models", "catalog_variants", "catalog_sections"):
        client.table(table).update({"source_url": None}).ilike("source_url", "%megazip%").execute()
        logger.info("%s source_url nulled", table)

    # Diagrams: rewrite storage_path in pages (PostgREST cannot SQL-replace in bulk)
    n = 0
    while True:
        rows = (
            client.table("catalog_diagrams")
            .select("id,storage_path,source_url,image_url")
            .or_(
                "storage_path.ilike.megazip/%,"
                "source_url.ilike.%megazip%,"
                "image_url.ilike.%megazip%"
            )
            .limit(PAGE)
            .execute()
            .data
            or []
        )
        if not rows:
            break
        for row in rows:
            path = row.get("storage_path") or ""
            if path.lower().startswith("megazip/"):
                path = "epc/" + path[len("megazip/") :]
            payload = {
                "storage_path": path,
                "source_url": None
                if row.get("source_url") and "megazip" in str(row["source_url"]).lower()
                else row.get("source_url"),
                "image_url": None
                if row.get("image_url") and "megazip" in str(row["image_url"]).lower()
                else row.get("image_url"),
            }
            client.table("catalog_diagrams").update(payload).eq("id", row["id"]).execute()
            n += 1
        logger.info("catalog_diagrams %s", n)
    logger.info("catalog_diagrams done %s", n)

    for table in ("catalog_diagram_parts", "part_fitment"):
        n = 0
        while True:
            rows = (
                client.table(table)
                .select("id,diagram_path")
                .ilike("diagram_path", "megazip/%")
                .limit(PAGE)
                .execute()
                .data
                or []
            )
            if not rows:
                break
            for row in rows:
                path = row.get("diagram_path") or ""
                if path.lower().startswith("megazip/"):
                    path = "epc/" + path[len("megazip/") :]
                client.table(table).update({"diagram_path": path}).eq("id", row["id"]).execute()
                n += 1
            logger.info("%s %s", table, n)
        logger.info("%s done %s", table, n)

    logger.info("DONE values. Apply migration 20260809120000 for column renames + RPCs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
