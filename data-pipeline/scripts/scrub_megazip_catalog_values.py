"""Concurrent PostgREST scrub of megazip paths/URLs from hosted catalog."""

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
CONCURRENCY = 40


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
    table: str,
    row_id: str,
    payload: dict,
    key: str,
) -> None:
    async with sem:
        url = f"{base}/rest/v1/{table}?id=eq.{row_id}"
        for attempt in range(5):
            resp = await client.patch(url, json=payload, headers=_headers(key))
            if resp.status_code < 300:
                return
            if resp.status_code in (429, 500, 502, 503, 504):
                await asyncio.sleep(0.5 * (attempt + 1))
                continue
            resp.raise_for_status()


async def _fetch_page(
    client: httpx.AsyncClient,
    base: str,
    table: str,
    select: str,
    filt: str,
    key: str,
) -> list[dict]:
    url = f"{base}/rest/v1/{table}?select={select}&{filt}&limit={PAGE}"
    headers = {
        **_headers(key),
        "Prefer": "count=exact",
    }
    resp = await client.get(url, headers=headers)
    resp.raise_for_status()
    return resp.json()


async def scrub_table_paths(
    client: httpx.AsyncClient,
    base: str,
    key: str,
    table: str,
    path_col: str,
) -> int:
    sem = asyncio.Semaphore(CONCURRENCY)
    total = 0
    while True:
        rows = await _fetch_page(
            client,
            base,
            table,
            f"id,{path_col}",
            f"{path_col}=ilike.megazip/*",
            key,
        )
        if not rows:
            break
        tasks = []
        for row in rows:
            new_path = _rewrite(row.get(path_col))
            if new_path == row.get(path_col):
                continue
            tasks.append(
                _patch(client, sem, base, table, row["id"], {path_col: new_path}, key)
            )
        if tasks:
            await asyncio.gather(*tasks)
        total += len(rows)
        logger.info("%s rewritten batch total=%s", table, total)
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
        rows = await _fetch_page(
            client,
            base,
            "catalog_diagrams",
            "id,storage_path,source_url,image_url",
            filt,
            key,
        )
        if not rows:
            break
        tasks = []
        for row in rows:
            payload = {
                "storage_path": _rewrite(row.get("storage_path")),
                "source_url": None
                if row.get("source_url") and "megazip" in str(row["source_url"]).lower()
                else row.get("source_url"),
                "image_url": None
                if row.get("image_url") and "megazip" in str(row["image_url"]).lower()
                else row.get("image_url"),
            }
            tasks.append(_patch(client, sem, base, "catalog_diagrams", row["id"], payload, key))
        await asyncio.gather(*tasks)
        total += len(rows)
        logger.info("catalog_diagrams total=%s", total)
    return total


async def bulk_null_urls(client: httpx.AsyncClient, base: str, key: str) -> None:
    """Single-shot updates where PostgREST can filter without per-row rewrite."""
    headers = _headers(key)
    for table in ("catalog_models", "catalog_variants", "catalog_sections"):
        url = f"{base}/rest/v1/{table}?source_url=ilike.*megazip*"
        resp = await client.patch(url, json={"source_url": None}, headers=headers)
        # timeout possible on huge tables — fall back to paged
        if resp.status_code >= 400:
            logger.warning("%s bulk null failed (%s); paging", table, resp.status_code)
            while True:
                rows = await _fetch_page(
                    client, base, table, "id,source_url", "source_url=ilike.*megazip*", key
                )
                if not rows:
                    break
                sem = asyncio.Semaphore(CONCURRENCY)
                await asyncio.gather(
                    *[
                        _patch(client, sem, base, table, r["id"], {"source_url": None}, key)
                        for r in rows
                    ]
                )
                logger.info("%s nulled +%s", table, len(rows))
        else:
            logger.info("%s source_url bulk-nulled status=%s", table, resp.status_code)

    url = f"{base}/rest/v1/catalog_makers?source=ilike.*megazip*"
    resp = await client.patch(url, json={"source": "epc"}, headers=headers)
    logger.info("makers status=%s", resp.status_code)


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
        await scrub_diagrams(client, base, key)
        await scrub_table_paths(client, base, key, "catalog_diagram_parts", "diagram_path")
        await scrub_table_paths(client, base, key, "part_fitment", "diagram_path")
    logger.info("DONE value/path scrub")
    return 0


def main() -> int:
    # Avoid Windows Proactor issues with large gather
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    return asyncio.run(main_async())


if __name__ == "__main__":
    raise SystemExit(main())
