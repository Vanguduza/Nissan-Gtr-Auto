"""Scrub megazip paths by unique path value (one PATCH updates many rows)."""

from __future__ import annotations

import asyncio
import logging
import os
from pathlib import Path
from urllib.parse import quote

import httpx

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
logger = logging.getLogger("scrub")

PAGE = 1000
CONCURRENCY = 60


def _headers(key: str) -> dict[str, str]:
    return {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }


async def _patch_path(
    client: httpx.AsyncClient,
    sem: asyncio.Semaphore,
    base: str,
    key: str,
    table: str,
    col: str,
    old: str,
    new: str,
) -> None:
    async with sem:
        # eq filter must be URL-encoded
        filt = quote(old, safe="")
        url = f"{base}/rest/v1/{table}?{col}=eq.{filt}"
        for attempt in range(6):
            resp = await client.patch(url, json={col: new}, headers=_headers(key))
            if resp.status_code < 300:
                return
            if resp.status_code in (429, 500, 502, 503, 504):
                await asyncio.sleep(0.3 * (attempt + 1))
                continue
            raise RuntimeError(f"{table} {resp.status_code}: {resp.text[:220]}")


async def load_epc_paths(client: httpx.AsyncClient, base: str, key: str) -> list[str]:
    paths: list[str] = []
    offset = 0
    while True:
        url = (
            f"{base}/rest/v1/catalog_diagrams?select=storage_path"
            f"&storage_path=ilike.epc/*&order=storage_path"
            f"&offset={offset}&limit={PAGE}"
        )
        resp = await client.get(url, headers=_headers(key))
        resp.raise_for_status()
        rows = resp.json()
        if not rows:
            break
        for row in rows:
            p = row.get("storage_path")
            if p:
                paths.append(p)
        if len(rows) < PAGE:
            break
        offset += PAGE
    # unique preserve order
    seen: set[str] = set()
    out: list[str] = []
    for p in paths:
        if p not in seen:
            seen.add(p)
            out.append(p)
    return out


async def rewrite_table_by_path(
    client: httpx.AsyncClient, base: str, key: str, table: str, col: str, epc_paths: list[str]
) -> int:
    sem = asyncio.Semaphore(CONCURRENCY)
    total = 0
    batch: list[asyncio.Task] = []
    for epc in epc_paths:
        old = "megazip/" + epc[len("epc/") :] if epc.startswith("epc/") else None
        if not old:
            continue
        batch.append(
            asyncio.create_task(_patch_path(client, sem, base, key, table, col, old, epc))
        )
        if len(batch) >= CONCURRENCY * 2:
            await asyncio.gather(*batch)
            total += len(batch)
            batch.clear()
            logger.info("%s path-groups done %s/%s", table, total, len(epc_paths))
    if batch:
        await asyncio.gather(*batch)
        total += len(batch)
    logger.info("%s done path-groups=%s", table, total)
    return total


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
        headers = _headers(key)
        await client.patch(
            f"{base}/rest/v1/catalog_makers?source=ilike.*megazip*",
            json={"source": "epc"},
            headers=headers,
        )
        for table in ("catalog_models", "catalog_variants", "catalog_sections"):
            await client.patch(
                f"{base}/rest/v1/{table}?source_url=ilike.*megazip*",
                json={"source_url": None},
                headers=headers,
            )
        await client.patch(
            f"{base}/rest/v1/catalog_diagrams?source_url=ilike.*megazip*",
            json={"source_url": None},
            headers=headers,
        )
        await client.patch(
            f"{base}/rest/v1/catalog_diagrams?image_url=ilike.*megazip*",
            json={"image_url": None},
            headers=headers,
        )
        logger.info("urls/makers scrubbed")

        epc_paths = await load_epc_paths(client, base, key)
        logger.info("unique epc diagram paths=%s", len(epc_paths))
        await rewrite_table_by_path(
            client, base, key, "catalog_diagram_parts", "diagram_path", epc_paths
        )
        await rewrite_table_by_path(client, base, key, "part_fitment", "diagram_path", epc_paths)

        # leftover megazip paths not in diagram list
        for table, col in (
            ("catalog_diagram_parts", "diagram_path"),
            ("part_fitment", "diagram_path"),
            ("catalog_diagrams", "storage_path"),
        ):
            while True:
                resp = await client.get(
                    f"{base}/rest/v1/{table}?select=id,{col}&{col}=ilike.megazip/*&limit=500",
                    headers=headers,
                )
                resp.raise_for_status()
                rows = resp.json()
                if not rows:
                    break
                sem = asyncio.Semaphore(CONCURRENCY)

                async def one(row: dict) -> None:
                    old = row.get(col) or ""
                    new = "epc/" + old[len("megazip/") :] if old.lower().startswith("megazip/") else old
                    async with sem:
                        await client.patch(
                            f"{base}/rest/v1/{table}?id=eq.{row['id']}",
                            json={col: new},
                            headers=headers,
                        )

                await asyncio.gather(*[one(r) for r in rows])
                logger.info("%s leftover patched %s", table, len(rows))

    logger.info("DONE")
    return 0


def main() -> int:
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    return asyncio.run(main_async())


if __name__ == "__main__":
    raise SystemExit(main())
