"""Fast megazip path scrub via concurrent PostgREST PATCH (path column only)."""

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

PAGE = 2000
CONCURRENCY = 80


def _headers(key: str) -> dict[str, str]:
    return {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }


def _rewrite(path: str | None) -> str | None:
    if not path:
        return path
    if path.lower().startswith("megazip/"):
        return "epc/" + path[len("megazip/") :]
    return path


async def _patch(
    client: httpx.AsyncClient,
    sem: asyncio.Semaphore,
    base: str,
    key: str,
    table: str,
    row_id: str,
    payload: dict,
) -> None:
    async with sem:
        url = f"{base}/rest/v1/{table}?id=eq.{row_id}"
        for attempt in range(6):
            resp = await client.patch(url, json=payload, headers=_headers(key))
            if resp.status_code < 300:
                return
            if resp.status_code in (429, 500, 502, 503, 504):
                await asyncio.sleep(0.25 * (attempt + 1))
                continue
            raise RuntimeError(f"{table} {row_id} {resp.status_code}: {resp.text[:200]}")


async def scrub_path_table(
    client: httpx.AsyncClient, base: str, key: str, table: str, path_col: str
) -> int:
    sem = asyncio.Semaphore(CONCURRENCY)
    total = 0
    while True:
        url = (
            f"{base}/rest/v1/{table}?select=id,{path_col}"
            f"&{path_col}=ilike.megazip/*&limit={PAGE}"
        )
        resp = await client.get(url, headers=_headers(key))
        resp.raise_for_status()
        rows = resp.json()
        if not rows:
            break
        tasks = []
        for row in rows:
            new_path = _rewrite(row.get(path_col))
            if not new_path or new_path == row.get(path_col):
                continue
            tasks.append(
                _patch(client, sem, base, key, table, row["id"], {path_col: new_path})
            )
        if tasks:
            await asyncio.gather(*tasks)
        total += len(tasks)
        logger.info("%s patched %s", table, total)
    return total


async def bulk_null_urls(client: httpx.AsyncClient, base: str, key: str) -> None:
    headers = _headers(key)
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
    timeout = httpx.Timeout(120.0, connect=30.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        await bulk_null_urls(client, base, key)
        # diagrams already rewritten earlier; cheap no-op if clean
        n = await scrub_path_table(client, base, key, "catalog_diagrams", "storage_path")
        logger.info("diagrams storage_path done %s", n)
        # null remaining diagram vendor urls
        await client.patch(
            f"{base}/rest/v1/catalog_diagrams?source_url=ilike.*megazip*",
            json={"source_url": None},
            headers=_headers(key),
        )
        await client.patch(
            f"{base}/rest/v1/catalog_diagrams?image_url=ilike.*megazip*",
            json={"image_url": None},
            headers=_headers(key),
        )
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
