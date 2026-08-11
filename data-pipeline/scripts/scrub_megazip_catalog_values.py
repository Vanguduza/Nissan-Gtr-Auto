"""Scrub megazip paths from hosted catalog via batched id PATCHes.

Paginates remaining megazip/* rows and rewrites to epc/ in small id chunks.
Uses HTTP/1.1 (HTTP/2 hits stream-id limits under high concurrency).
"""

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
PATCH_CHUNK = 100
CONCURRENCY = 24


def _headers(key: str) -> dict[str, str]:
    return {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
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
        id_list = ",".join(ids)
        url = f"{base}/rest/v1/{table}?id=in.({id_list})"
        payload = {col: new_value}
        for attempt in range(10):
            try:
                resp = await client.patch(url, json=payload, headers=_headers(key))
            except (httpx.RemoteProtocolError, httpx.ReadError, httpx.WriteError, httpx.ConnectError) as exc:
                await asyncio.sleep(0.5 * (attempt + 1))
                if attempt == 9:
                    raise RuntimeError(f"{table} transport failed: {exc}") from exc
                continue
            if resp.status_code < 300:
                return
            body = (resp.text or "").lower()
            if resp.status_code in (429, 500, 502, 503, 504) or "timeout" in body:
                await asyncio.sleep(0.5 * (attempt + 1))
                continue
            raise RuntimeError(f"{table} patch {resp.status_code}: {resp.text[:240]}")


async def rewrite_table(
    client: httpx.AsyncClient, base: str, key: str, table: str, col: str
) -> int:
    sem = asyncio.Semaphore(CONCURRENCY)
    total = 0
    while True:
        q = (
            f"{base}/rest/v1/{table}?select=id,{col}"
            f"&{col}=like.megazip/*"
            f"&order=id&limit={PAGE}"
        )
        for attempt in range(8):
            try:
                resp = await client.get(q, headers=_headers(key))
                break
            except (httpx.RemoteProtocolError, httpx.ReadError, httpx.ConnectError):
                await asyncio.sleep(0.5 * (attempt + 1))
        else:
            raise RuntimeError(f"{table}: failed to page remaining megazip rows")
        resp.raise_for_status()
        rows = resp.json()
        if not rows:
            break

        groups: dict[str, list[str]] = {}
        unchanged = 0
        for row in rows:
            rid = row.get("id")
            old = row.get(col) or ""
            if not rid or not old:
                continue
            new = _to_epc(old)
            if new == old:
                unchanged += 1
                continue
            groups.setdefault(new, []).append(str(rid))

        if unchanged and not groups:
            raise RuntimeError(
                f"{table}: {unchanged} rows match megazip filter but need no rewrite — abort"
            )

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
        logger.info("%s rewritten rows≈%s (page=%s groups=%s)", table, total, len(rows), len(groups))

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
    # Large tables: id-keyset nulling (single filter PATCH can leave leftovers).
    await _null_ilike_column(client, base, key, "catalog_sections", "thumbnail_url")
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
    await rewrite_table(client, base, key, "catalog_diagrams", "storage_path")
    logger.info("urls/makers/diagrams scrubbed")


async def _null_ilike_column(
    client: httpx.AsyncClient,
    base: str,
    key: str,
    table: str,
    col: str,
    *,
    page_size: int = 1000,
) -> int:
    """Null ``col`` where it ILIKE megazip, paging by id until clear."""
    headers = _headers(key)
    total = 0
    for _ in range(500):
        resp = await client.get(
            f"{base}/rest/v1/{table}?select=id&{col}=ilike.*megazip*&limit={page_size}",
            headers=headers,
        )
        if resp.status_code >= 300:
            raise RuntimeError(f"{table}.{col} list {resp.status_code}: {resp.text[:200]}")
        rows = resp.json() or []
        if not rows:
            break
        ids = [r["id"] for r in rows if r.get("id")]
        for i in range(0, len(ids), PATCH_CHUNK):
            chunk = ids[i : i + PATCH_CHUNK]
            id_list = ",".join(chunk)
            url = f"{base}/rest/v1/{table}?id=in.({id_list})"
            for attempt in range(10):
                pr = await client.patch(url, json={col: None}, headers=headers)
                if pr.status_code < 300:
                    break
                if pr.status_code in (429, 500, 502, 503, 504):
                    await asyncio.sleep(0.5 * (attempt + 1))
                    continue
                raise RuntimeError(f"{table}.{col} null patch {pr.status_code}: {pr.text[:200]}")
            else:
                raise RuntimeError(f"{table}.{col} null patch exhausted retries")
        total += len(ids)
        if total % 5000 < page_size:
            logger.info("%s.%s nulled≈%s", table, col, total)
    logger.info("%s.%s done nulled≈%s", table, col, total)
    return total


async def sample_remaining(
    client: httpx.AsyncClient, base: str, key: str, table: str, col: str
) -> int:
    resp = await client.get(
        f"{base}/rest/v1/{table}?select=id&{col}=like.megazip/*&limit=1",
        headers={**_headers(key), "Prefer": "count=exact,return=minimal"},
    )
    if resp.status_code >= 300:
        resp2 = await client.get(
            f"{base}/rest/v1/{table}?select=id,{col}&{col}=like.megazip/*&limit=5",
            headers=_headers(key),
        )
        resp2.raise_for_status()
        n = len(resp2.json())
        logger.info("%s.%s leftover_sample=%s", table, col, n)
        return n
    cr = resp.headers.get("content-range") or ""
    total = -1
    if "/" in cr:
        try:
            total = int(cr.rsplit("/", 1)[-1])
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
    limits = httpx.Limits(max_connections=CONCURRENCY + 8, max_keepalive_connections=CONCURRENCY)
    # HTTP/1.1 — HTTP/2 stream ids exhaust under long PATCH storms
    async with httpx.AsyncClient(timeout=timeout, limits=limits, http2=False) as client:
        only = (os.environ.get("SCRUB_ONLY") or "").strip().lower()
        if only not in ("parts", "diagram_parts", "catalog_diagram_parts"):
            await scrub_urls_and_makers(client, base, key)
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
