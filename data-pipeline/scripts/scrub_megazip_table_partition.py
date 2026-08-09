"""Scrub megazip→epc diagram_path on one table, optional UUID id range partition."""

from __future__ import annotations

import argparse
import asyncio
import logging
import os
from pathlib import Path

import httpx

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logger = logging.getLogger("scrub_part")

PAGE = 1000
PATCH_CHUNK = 150
CONCURRENCY = 32


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
    async with sem:
        url = f"{base}/rest/v1/{table}?id=in.({','.join(ids)})"
        for attempt in range(10):
            try:
                resp = await client.patch(url, json={col: new_value}, headers=_headers(key))
            except (httpx.RemoteProtocolError, httpx.ReadError, httpx.ConnectError, httpx.WriteError):
                await asyncio.sleep(0.4 * (attempt + 1))
                continue
            if resp.status_code < 300:
                return
            if resp.status_code in (429, 500, 502, 503, 504) or "timeout" in (resp.text or "").lower():
                await asyncio.sleep(0.4 * (attempt + 1))
                continue
            raise RuntimeError(f"{resp.status_code}: {resp.text[:200]}")


async def rewrite(
    client: httpx.AsyncClient,
    base: str,
    key: str,
    table: str,
    col: str,
    id_gte: str | None,
    id_lt: str | None,
    label: str,
) -> int:
    sem = asyncio.Semaphore(CONCURRENCY)
    total = 0
    while True:
        q = (
            f"{base}/rest/v1/{table}?select=id,{col}"
            f"&{col}=like.megazip/*"
            f"&order=id&limit={PAGE}"
        )
        if id_gte:
            q += f"&id=gte.{id_gte}"
        if id_lt:
            q += f"&id=lt.{id_lt}"
        for attempt in range(8):
            try:
                resp = await client.get(q, headers=_headers(key))
                break
            except (httpx.RemoteProtocolError, httpx.ReadError, httpx.ConnectError):
                await asyncio.sleep(0.4 * (attempt + 1))
        else:
            raise RuntimeError("page fetch failed")
        resp.raise_for_status()
        rows = resp.json()
        if not rows:
            break
        groups: dict[str, list[str]] = {}
        for row in rows:
            old = row.get(col) or ""
            new = _to_epc(old)
            if new != old and row.get("id"):
                groups.setdefault(new, []).append(str(row["id"]))
        tasks = []
        for new_val, ids in groups.items():
            for i in range(0, len(ids), PATCH_CHUNK):
                tasks.append(
                    asyncio.create_task(
                        _patch_ids(
                            client, sem, base, key, table, col, ids[i : i + PATCH_CHUNK], new_val
                        )
                    )
                )
        if tasks:
            await asyncio.gather(*tasks)
        total += len(rows)
        logger.info("%s rewritten≈%s page=%s", label, total, len(rows))
    logger.info("%s DONE approx=%s", label, total)
    return total


async def main_async(args: argparse.Namespace) -> int:
    repo = Path(__file__).resolve().parents[2]
    pipe = Path(__file__).resolve().parents[1]
    load_env_files(pipe / ".env", repo / ".env", override=True)
    url, key = resolve_supabase_credentials()
    if not url or not key:
        return 2
    base = url.rstrip("/")
    timeout = httpx.Timeout(180.0, connect=30.0)
    limits = httpx.Limits(max_connections=CONCURRENCY + 8, max_keepalive_connections=CONCURRENCY)
    async with httpx.AsyncClient(timeout=timeout, limits=limits, http2=False) as client:
        await rewrite(
            client,
            base,
            key,
            args.table,
            args.column,
            args.id_gte,
            args.id_lt,
            args.label or args.table,
        )
    return 0


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--table", default="catalog_diagram_parts")
    p.add_argument("--column", default="diagram_path")
    p.add_argument("--id-gte")
    p.add_argument("--id-lt")
    p.add_argument("--label")
    args = p.parse_args()
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    return asyncio.run(main_async(args))


if __name__ == "__main__":
    raise SystemExit(main())
