"""Fast megazip path scrub via PostgREST bulk upserts (1000 rows/request)."""

from __future__ import annotations

import asyncio
import logging
import os
from pathlib import Path

import httpx

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
logger = logging.getLogger("scrub")

PAGE = 1000
CONCURRENCY = 12


def _headers(key: str, *, prefer: str) -> dict[str, str]:
    return {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Prefer": prefer,
    }


def _rewrite(path: str | None) -> str | None:
    if not path:
        return path
    if path.lower().startswith("megazip/"):
        return "epc/" + path[len("megazip/") :]
    return path


async def _fetch(
    client: httpx.AsyncClient, base: str, key: str, table: str, select: str, filt: str
) -> list[dict]:
    url = f"{base}/rest/v1/{table}?select={select}&{filt}&limit={PAGE}"
    resp = await client.get(url, headers=_headers(key, prefer="count=exact"))
    resp.raise_for_status()
    return resp.json()


async def _upsert_chunk(
    client: httpx.AsyncClient,
    sem: asyncio.Semaphore,
    base: str,
    key: str,
    table: str,
    rows: list[dict],
) -> None:
    if not rows:
        return
    async with sem:
        url = f"{base}/rest/v1/{table}?on_conflict=id"
        headers = _headers(key, prefer="resolution=merge-duplicates,return=minimal")
        for attempt in range(6):
            resp = await client.post(url, json=rows, headers=headers)
            if resp.status_code < 300:
                return
            if resp.status_code in (429, 500, 502, 503, 504):
                await asyncio.sleep(0.4 * (attempt + 1))
                continue
            raise RuntimeError(f"{table} upsert {resp.status_code}: {resp.text[:300]}")


async def scrub_path_table(
    client: httpx.AsyncClient, base: str, key: str, table: str, path_col: str
) -> int:
    sem = asyncio.Semaphore(CONCURRENCY)
    total = 0
    while True:
        rows = await _fetch(
            client, base, key, table, f"id,{path_col}", f"{path_col}=ilike.megazip/*"
        )
        if not rows:
            break
        payload = []
        for row in rows:
            new_path = _rewrite(row.get(path_col))
            if new_path and new_path != row.get(path_col):
                payload.append({"id": row["id"], path_col: new_path})
        # Upsert current page immediately (filter shrinks as we rewrite)
        await _upsert_chunk(client, sem, base, key, table, payload)
        total += len(payload)
        logger.info("%s upserted %s", table, total)
    return total


async def scrub_diagrams(client: httpx.AsyncClient, base: str, key: str) -> int:
    sem = asyncio.Semaphore(CONCURRENCY)
    total = 0
    filt = (
        "or=(storage_path.ilike.megazip/*,"
        "source_url.ilike.*megazip*,"
        "image_url.ilike.*megazip*)"
    )
    while True:
        rows = await _fetch(
            client,
            base,
            key,
            "catalog_diagrams",
            "id,storage_path,source_url,image_url",
            filt,
        )
        if not rows:
            break
        payload = []
        for row in rows:
            payload.append(
                {
                    "id": row["id"],
                    "storage_path": _rewrite(row.get("storage_path")),
                    "source_url": None
                    if row.get("source_url") and "megazip" in str(row["source_url"]).lower()
                    else row.get("source_url"),
                    "image_url": None
                    if row.get("image_url") and "megazip" in str(row["image_url"]).lower()
                    else row.get("image_url"),
                }
            )
        await _upsert_chunk(client, sem, base, key, "catalog_diagrams", payload)
        total += len(payload)
        logger.info("catalog_diagrams upserted %s", total)
    return total


async def bulk_null_urls(client: httpx.AsyncClient, base: str, key: str) -> None:
    headers = _headers(key, prefer="return=minimal")
    await client.patch(
        f"{base}/rest/v1/catalog_makers?source=ilike.*megazip*",
        json={"source": "epc"},
        headers=headers,
    )
    for table in ("catalog_models", "catalog_variants", "catalog_sections"):
        resp = await client.patch(
            f"{base}/rest/v1/{table}?source_url=ilike.*megazip*",
            json={"source_url": None},
            headers=headers,
        )
        logger.info("%s null urls status=%s", table, resp.status_code)


async def main_async() -> int:
    repo = Path(__file__).resolve().parents[2]
    pipe = Path(__file__).resolve().parents[1]
    load_env_files(pipe / ".env", repo / ".env", override=True)
    url, key = resolve_supabase_credentials()
    if not url or not key:
        logger.error("missing credentials")
        return 2
    base = url.rstrip("/")
    timeout = httpx.Timeout(180.0, connect=30.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        await bulk_null_urls(client, base, key)
        n = await scrub_diagrams(client, base, key)
        logger.info("diagrams done %s", n)
        n = await scrub_path_table(client, base, key, "catalog_diagram_parts", "diagram_path")
        logger.info("diagram_parts done %s", n)
        n = await scrub_path_table(client, base, key, "part_fitment", "diagram_path")
        logger.info("part_fitment done %s", n)
    logger.info("DONE")
    return 0


def main() -> int:
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    return asyncio.run(main_async())


if __name__ == "__main__":
    raise SystemExit(main())
