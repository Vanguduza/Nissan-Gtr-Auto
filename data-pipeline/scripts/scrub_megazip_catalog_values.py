"""Scrub megazip paths from hosted catalog via batched id PATCHes.

Path-equality PATCHes on large tables time out (one path can touch 100k+ rows).
Paging by id and rewriting in small batches stays under statement timeout.
"""

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
PATCH_CHUNK = 80
CONCURRENCY = 40


def _headers(key: str, *, prefer: str = "return=minimal") -> dict[str, str]:
    return {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Prefer": prefer,
    }


def _to_epc(path: str) -> str:
    if path.lower().startswith("megazip/"):
        return "epc/" + path[len("megazip/") :]
    return path


async def _patch_ids(
    client: httpx.AsyncClient,
    sem: asyncio.Semaphore,
    base: str,
    key: str,
    table: str,
    col: str,
    ids: list[str],
    new_value: str,
) -> None:
    if not ids:
        return
    async with sem:
        # PostgREST in.() list
        id_list = ",".join(ids)
        url = f"{base}/rest/v1/{table}?id=in.({id_list})"
        payload = {col: new_value}
        for attempt in range(8):
            resp = await client.patch(url, json=payload, headers=_headers(key))
            if resp.status_code < 300:
                return
            if resp.status_code in (429, 500, 502, 503, 504) or "timeout" in (resp.text or "").lower():
                await asyncio.sleep(0.4 * (attempt + 1))
                continue
            raise RuntimeError(f"{table} patch {resp.status_code}: {resp.text[:240]}")


async def rewrite_table(
    client: httpx.AsyncClient, base: str, key: str, table: str, col: str
) -> int:
    """Page megazip rows and rewrite diagram_path / storage_path to epc/."""
    sem = asyncio.Semaphore(CONCURRENCY)
    total = 0
    # Prefer keyset on id to avoid offset drift while updating
    after: str | None = None
    while True:
        q = (
            f"{base}/rest/v1/{table}?select=id,{col}"
            f"&{col}=ilike.{quote('megazip/%', safe='')}"
            f"&order=id&limit={PAGE}"
        )
        if after:
            q += f"&id=gt.{after}"
        resp = await client.get(q, headers=_headers(key))
        if resp.status_code >= 300:
            # Fallback: plain filter if encode quirks
            q2 = (
                f"{base}/rest/v1/{table}?select=id,{col}"
                f"&{col}=like.megazip/*"
                f"&order=id&limit={PAGE}"
            )
            if after:
                q2 += f"&id=gt.{after}"
            resp = await client.get(q2, headers=_headers(key))
        resp.raise_for_status()
        rows = resp.json()
        if not by_path := rows:
            break

        # Group ids by target path
        groups: dict[str, list[str]] = {}
        for row in rows:
            rid = row.get("id")
            old = row.get(col) or ""
            if not rid or not old:
                continue
            new = _to_epc(old)
            if new == old:
                continue
            groups.setdefault(new, []).append(str(rid))

        tasks: list[asyncio.Task] = []
        for new_val, ids in groups.items():
            for i in range(0, len(ids), PATCH_CHUNK):
                chunk = ids[i : i + PATCH_CHUNK]
                tasks.append(
                    asyncio.create_task(
                        _patch_ids(client, sem, base, key, table, col, chunk, new_val)
                    )
                )
        if tasks:
            await asyncio.gather(*tasks)

        total += len(rows)
        after = str(rows[-1]["id"])
        logger.info("%s rewritten rows≈%s (page=%s after=%s…)", table, total, len(rows), after[:8])
        if len(rows) < PAGE:
            break

    logger.info("%s done approx_rows=%s", table, total)
    return total


async def scrub_urls_and_makers(client: httpx.AsyncClient, base: str, key: str) -> None:
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
    # diagrams storage_path (should already be epc/)
    await rewrite_table(client, base, key, "catalog_diagrams", "storage_path")
    logger.info("urls/makers/diagrams scrubbed")


async def sample_remaining(
    client: httpx.AsyncClient, base: str, key: str, table: str, col: str
) -> int:
    resp = await client.get(
        f"{base}/rest/v1/{table}?select=id&{col}=ilike.*megazip*&limit=1",
        headers={**_headers(key), "Prefer": "count=exact"},
    )
    if resp.status_code >= 300:
        # presence check only
        resp2 = await client.get(
            f"{base}/rest/v1/{table}?select=id,{col}&{col}=like.megazip/*&limit=5",
            headers=_headers(key),
        )
        resp2.raise_for_status()
        n = len(resp2.json())
        logger.info("%s.%s leftover_sample=%s", table, col, n)
        return n
    # Content-Range: 0-0/123
    cr = resp.headers.get("content-range") or ""
    total = -1
    if "/" in cr:
        try:
            total = int(cr.split("/")[-1])
        except ValueError:
            total = -1
    logger.info("%s.%s leftover_count≈%s", table, col, total)
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
    timeout = httpx.Timeout(180.0, connect=30.0)
    limits = httpx.Limits(max_connections=CONCURRENCY + 10, max_keepalive_connections=CONCURRENCY)
    async with httpx.AsyncClient(timeout=timeout, limits=limits, http2=True) as client:
        await scrub_urls_and_makers(client, base, key)
        # Smaller table first
        await rewrite_table(client, base, key, "part_fitment", "diagram_path")
        await rewrite_table(client, base, key, "catalog_diagram_parts", "diagram_path")
        for table, col in (
            ("part_fitment", "diagram_path"),
            ("catalog_diagram_parts", "diagram_path"),
            ("catalog_diagrams", "storage_path"),
            ("catalog_makers", "source"),
        ):
            try:
                await sample_remaining(client, base, key, table, col)
            except Exception as exc:  # noqa: BLE001
                logger.warning("sample %s.%s failed: %s", table, col, exc)
    logger.info("DONE")
    return 0


def main() -> int:
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    return asyncio.run(main_async())


if __name__ == "__main__":
    raise SystemExit(main())
