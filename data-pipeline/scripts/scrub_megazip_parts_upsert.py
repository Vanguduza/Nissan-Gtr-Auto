"""Scrub megazip→epc on catalog_diagram_parts via full-row bulk upsert."""

from __future__ import annotations

import asyncio
import logging
import os
from pathlib import Path

import httpx

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logger = logging.getLogger("scrub_upsert")

PAGE = 500


def _headers(key: str, prefer: str) -> dict[str, str]:
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


async def main_async() -> int:
    repo = Path(__file__).resolve().parents[2]
    pipe = Path(__file__).resolve().parents[1]
    load_env_files(pipe / ".env", repo / ".env", override=True)
    url, key = resolve_supabase_credentials()
    if not url or not key:
        return 2
    base = url.rstrip("/")
    table = "catalog_diagram_parts"
    timeout = httpx.Timeout(180.0, connect=30.0)
    total = 0
    async with httpx.AsyncClient(timeout=timeout, http2=False) as client:
        while True:
            q = (
                f"{base}/rest/v1/{table}?select=*"
                f"&diagram_path=like.megazip/*"
                f"&order=id&limit={PAGE}"
            )
            for attempt in range(8):
                try:
                    resp = await client.get(q, headers=_headers(key, "return=representation"))
                    break
                except Exception:
                    await asyncio.sleep(0.4 * (attempt + 1))
            else:
                raise RuntimeError("fetch failed")
            resp.raise_for_status()
            rows = resp.json()
            if not rows:
                break
            payload = []
            for row in rows:
                old = row.get("diagram_path") or ""
                new = _to_epc(old)
                if new == old:
                    continue
                row = dict(row)
                row["diagram_path"] = new
                payload.append(row)
            if not payload:
                raise RuntimeError("no rewrites in page")
            for attempt in range(8):
                try:
                    up = await client.post(
                        f"{base}/rest/v1/{table}?on_conflict=id",
                        json=payload,
                        headers=_headers(key, "resolution=merge-duplicates,return=minimal"),
                    )
                except Exception:
                    await asyncio.sleep(0.5 * (attempt + 1))
                    continue
                if up.status_code < 300:
                    break
                if up.status_code in (429, 500, 502, 503, 504) or "timeout" in (up.text or "").lower():
                    await asyncio.sleep(0.6 * (attempt + 1))
                    continue
                raise RuntimeError(f"upsert {up.status_code}: {up.text[:300]}")
            else:
                raise RuntimeError("upsert failed")
            total += len(payload)
            logger.info("upserted≈%s (page=%s)", total, len(payload))
    logger.info("DONE approx=%s", total)
    return 0


def main() -> int:
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    return asyncio.run(main_async())


if __name__ == "__main__":
    raise SystemExit(main())
