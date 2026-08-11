"""Post-import verification for Megazip catalog (any maker).

Checks the Nissan-pipeline failure modes before declaring a maker import done:

* no ``megazip`` leakage in source/paths
* ``catalog_makers.source == epc``
* engine coverage on published variant chassis
* hierarchy + browse RPC sanity

Usage (from data-pipeline/)::

  python scripts/megazip_post_import_verify.py
  python scripts/megazip_post_import_verify.py --maker-slug toyota
  python scripts/megazip_post_import_verify.py --fail-on-engine-gaps
"""

from __future__ import annotations

import argparse
import logging
import sys
from collections import defaultdict
from pathlib import Path

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
logger = logging.getLogger("megazip_verify")


def _paginate(client, table: str, select: str, *, eq: dict[str, str] | None = None):
    rows: list[dict] = []
    start = 0
    while True:
        q = client.table(table).select(select)
        if eq:
            for k, v in eq.items():
                q = q.eq(k, v)
        chunk = q.range(start, start + 999).execute().data or []
        if not chunk:
            break
        rows.extend(chunk)
        start += 1000
        if len(chunk) < 1000:
            break
    return rows


def verify(*, maker_slug: str | None, fail_on_engine_gaps: bool) -> int:
    try:
        from supabase import create_client
    except ImportError as exc:
        raise RuntimeError("pip install -e '.[supabase]'") from exc

    repo = Path(__file__).resolve().parents[2]
    pipe = Path(__file__).resolve().parents[1]
    load_env_files(pipe / ".env", repo / ".env", override=True)
    url, key = resolve_supabase_credentials()
    if not url or not key:
        logger.error("missing Supabase credentials")
        return 2
    client = create_client(url, key)
    failures: list[str] = []
    warnings: list[str] = []

    makers = _paginate(client, "catalog_makers", "slug,name,source")
    if maker_slug:
        makers = [m for m in makers if m.get("slug") == maker_slug]
    if not makers:
        failures.append(f"no catalog_makers for slug={maker_slug or '*'}")
    for m in makers:
        src = (m.get("source") or "").lower()
        if src != "epc":
            failures.append(f"catalog_makers.{m.get('slug')} source={m.get('source')!r} (want epc)")

    # Vendor string leakage samples
    leak_checks = [
        ("catalog_models", "source_url"),
        ("catalog_variants", "source_url"),
        ("catalog_sections", "source_url"),
        ("catalog_sections", "thumbnail_url"),
        ("catalog_diagrams", "source_url"),
        ("catalog_diagrams", "image_url"),
        ("catalog_diagrams", "storage_path"),
        ("part_fitment", "diagram_path"),
    ]
    for table, col in leak_checks:
        try:
            q = client.table(table).select("id", count="exact").ilike(col, "%megazip%").limit(1)
            if maker_slug and table.startswith("catalog_") and table != "catalog_makers":
                # variants/models/sections/diagrams have maker_slug
                if table != "part_fitment":
                    q = client.table(table).select("id", count="exact").eq(
                        "maker_slug", maker_slug
                    ).ilike(col, "%megazip%").limit(1)
            r = q.execute()
            n = r.count if r.count is not None else len(r.data or [])
            if n:
                failures.append(f"{table}.{col} still contains megazip ({n}+ rows)")
            else:
                logger.info("OK %s.%s no megazip", table, col)
        except Exception as exc:  # noqa: BLE001
            warnings.append(f"leak check {table}.{col}: {exc}")

    # Engine coverage for published variants
    eq = {"maker_slug": maker_slug} if maker_slug else None
    models = _paginate(client, "catalog_models", "slug,display_name", eq=eq)
    variants = _paginate(client, "catalog_variants", "model_slug,chassis_code", eq=eq)
    vm = _paginate(client, "vehicle_master", "chassis_code,engine_code,model_variant")

    engines_by_label_chassis: dict[tuple[str, str], set[str]] = defaultdict(set)
    for row in vm:
        eng = row.get("engine_code")
        ch = row.get("chassis_code") or ""
        label = row.get("model_variant") or ""
        if eng and ch and label:
            engines_by_label_chassis[(label, ch)].add(eng)

    display = {m["slug"]: m["display_name"] for m in models}
    maker_name = (makers[0].get("name") if makers else "Nissan") or "Nissan"
    missing: list[str] = []
    covered = 0
    for v in variants:
        ch = (v.get("chassis_code") or "").strip()
        if not ch:
            continue
        ms = v.get("model_slug") or ""
        label = f"{maker_name} {display.get(ms, ms)}"
        if engines_by_label_chassis.get((label, ch)):
            covered += 1
        else:
            missing.append(f"{ms}/{ch}")

    logger.info(
        "engine coverage: %s/%s published chassis-with-engine (unique variant rows counted)",
        covered,
        covered + len(missing),
    )
    if missing:
        sample = ", ".join(missing[:12])
        msg = f"chassis missing engines ({len(missing)}): {sample}"
        if fail_on_engine_gaps:
            failures.append(msg)
        else:
            warnings.append(msg)

    try:
        rpc = client.rpc("list_catalog_makers").execute().data
        logger.info("list_catalog_makers OK (%s makers)", len(rpc or []))
    except Exception as exc:  # noqa: BLE001
        failures.append(f"list_catalog_makers RPC failed: {exc}")

    for w in warnings:
        logger.warning("%s", w)
    for f in failures:
        logger.error("%s", f)

    if failures:
        logger.error("VERIFY FAILED (%s errors, %s warnings)", len(failures), len(warnings))
        return 1
    logger.info("VERIFY OK (%s warnings)", len(warnings))
    return 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--maker-slug", default=None, help="Limit checks to one maker_slug")
    p.add_argument(
        "--fail-on-engine-gaps",
        action="store_true",
        help="Treat incomplete engine coverage as failure (default: warn)",
    )
    args = p.parse_args(argv)
    return verify(maker_slug=args.maker_slug, fail_on_engine_gaps=args.fail_on_engine_gaps)


if __name__ == "__main__":
    raise SystemExit(main())
