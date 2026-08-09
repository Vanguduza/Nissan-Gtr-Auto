"""Scrub megazip→epc on catalog_diagram_parts via id keyset (no LIKE filter).

Hosted PostgREST times out on diagram_path=like.megazip/* scans.
Walk by primary key, rewrite rows whose path still starts with megazip/.
"""

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
logger = logging.getLogger("scrub_keyset")

PAGE = 1000


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


async def main_async(args: argparse.Namespace) -> int:
    repo = Path(__file__).resolve().parents[2]
    pipe = Path(__file__).resolve().parents[1]
    load_env_files(pipe / ".env", repo / ".env", override=True)
    url, key = resolve_supabase_credentials()
    if not url or not key:
        return 2
    base = url.rstrip("/")
    table = "catalog_diagram_parts"
    timeout = httpx.Timeout(180.0, connect=30.0)
    label = args.label or "all"
    scanned = 0
    rewritten = 0
    after = args.id_gte  # exclusive lower bound via id=gt
    stop_lt = args.id_lt

    async with httpx.AsyncClient(timeout=timeout, http2=False) as client:
        while True:
            q = f"{base}/rest/v1/{table}?select=*&order=id&limit={PAGE}"
            if after:
                q += f"&id=gt.{after}"
            elif args.id_gte_inclusive:
                q += f"&id=gte.{args.id_gte_inclusive}"
            if stop_lt:
                q += f"&id=lt.{stop_lt}"

            resp = None
            for attempt in range(12):
                try:
                    resp = await client.get(q, headers=_headers(key, "return=representation"))
                    if resp.status_code >= 500:
                        await asyncio.sleep(1.2 * (attempt + 1))
                        continue
                    break
                except Exception:
                    await asyncio.sleep(0.5 * (attempt + 1))
            if resp is None:
                raise RuntimeError("fetch failed")
            resp.raise_for_status()
            rows = resp.json()
            if not rows:
                break

            payload = []
            for row in rows:
                old = row.get("diagram_path") or ""
                new = _to_epc(old)
                if new != old:
                    row = dict(row)
                    row["diagram_path"] = new
                    payload.append(row)

            scanned += len(rows)
            after = str(rows[-1]["id"])

            if payload:
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
                    if up.status_code in (429, 500, 502, 503, 504) or "timeout" in (
                        up.text or ""
                    ).lower():
                        await asyncio.sleep(0.6 * (attempt + 1))
                        continue
                    raise RuntimeError(f"upsert {up.status_code}: {up.text[:300]}")
                else:
                    raise RuntimeError("upsert failed")
                rewritten += len(payload)

            if scanned % 5000 < PAGE or payload:
                logger.info(
                    "%s scanned≈%s rewritten≈%s after=%s…",
                    label,
                    scanned,
                    rewritten,
                    after[:8],
                )

            if len(rows) < PAGE:
                break

    logger.info("%s DONE scanned=%s rewritten=%s", label, scanned, rewritten)
    return 0


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--id-gte", help="Resume after this id (exclusive, id=gt)")
    p.add_argument("--id-gte-inclusive", help="Start at this id inclusive (first page only)")
    p.add_argument("--id-lt")
    p.add_argument("--label", default="all")
    args = p.parse_args()
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    return asyncio.run(main_async(args))


if __name__ == "__main__":
    raise SystemExit(main())
