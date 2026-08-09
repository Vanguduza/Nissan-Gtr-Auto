"""Megazip multivehicle EPC catalog orchestrator.

EPC-first, PCdb additive, re-runnable without re-crawl.

Phases (``--phase``):
  crawl     — fetch HTML to cache (skip with ``--skip-crawl``)
  parse     — re-parse cached HTML into SQLite (no network)
  transform — build hierarchy JSON bundle (re-runnable)
  pcdb      — additive PartTerminologyID mapping (re-runnable)
  filter    — complete-only filter + quality report
  upload    — download diagram PNGs (optional Storage upload)
  import    — Supabase hierarchy + fitment + stock_items
  all       — default pipeline for each maker

Maker order (``config/megazip_makers.json``): Nissan → Toyota → Honda → Mazda → …
Nissan default: two-phase crawl (priority chassis, then all remaining models).

Usage (from ``data-pipeline/``)::

  python -m data_pipeline.megazip_catalog_orchestrator --makers Nissan --max-pages 50
  python -m data_pipeline.megazip_catalog_orchestrator --makers Nissan --priority-chassis --no-nissan-two-phase
  python -m data_pipeline.megazip_catalog_orchestrator --phase transform,pcdb,import --skip-crawl
  python -m data_pipeline.megazip_catalog_orchestrator --makers all --live-import --complete-only
"""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from data_pipeline.bundle_filter import filter_complete_bundle
from data_pipeline.import_hierarchy_catalog import import_hierarchy_bundle_dir
from data_pipeline.megazip.config import (
    DEFAULT_CHASSIS_MAP_FILE,
    DEFAULT_MAKERS_FILE,
    DEFAULT_OUT_ROOT,
    DEFAULT_PRIORITY_FILE,
    MegazipConfig,
    build_maker_paths,
    load_megazip_chassis_map,
    load_priority_chassis_codes,
    load_merged_priority_model_seeds,
    load_priority_model_seeds,
    megazip_chassis_available,
    megazip_chassis_entry,
    megazip_model_seeds_for_chassis,
)
from data_pipeline.megazip.crawl import crawl_maker, parse_cached_pages
from data_pipeline.megazip.enrich_pcdb import enrich_pcdb
from data_pipeline.megazip.quality import assert_publishable, bundle_quality_report, variant_quality_breakdown
from data_pipeline.megazip.transform import transform_maker, write_bundle

logger = logging.getLogger("data_pipeline.megazip_catalog_orchestrator")

PACKAGE_ROOT = Path(__file__).resolve().parent.parent
ALL_PHASES = ("crawl", "parse", "transform", "pcdb", "filter", "upload", "import")


def _setup_logging(verbose: bool) -> None:
    logging.basicConfig(
        level=logging.DEBUG if verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )


def _resolve_makers(raw: str, config: MegazipConfig) -> list[str]:
    if raw.strip().lower() == "all":
        return list(config.makers)
    return [m.strip() for m in raw.split(",") if m.strip()]


def _parse_phases(raw: str | None) -> tuple[str, ...]:
    if not raw or raw.lower() == "all":
        return ALL_PHASES
    return tuple(p.strip().lower() for p in raw.split(",") if p.strip())


async def _upload_diagrams(paths, bundle: dict[str, Any]) -> dict[str, int]:
    try:
        import httpx
    except ImportError as exc:
        raise RuntimeError("httpx required for upload phase") from exc

    paths.diagrams_dir.mkdir(parents=True, exist_ok=True)
    downloaded = 0
    skipped_existing = 0
    seen_names: set[str] = set()

    # Collect URLs from diagram_assets and catalog_diagrams (all filtered bundle PNGs)
    pending: list[tuple[str, str]] = []
    for asset in bundle.get("diagram_assets") or []:
        url = asset.get("source_url") or ""
        name = Path(asset.get("storage_path") or "diagram.png").name
        if url and name:
            pending.append((url, name))
    for diag in bundle.get("catalog_diagrams") or []:
        url = diag.get("image_url") or diag.get("source_url") or ""
        sp = diag.get("storage_path") or ""
        name = Path(sp).name if sp else ""
        if url and name:
            pending.append((url, name))

    async with httpx.AsyncClient(timeout=90.0, follow_redirects=True) as client:
        for url, name in pending:
            if name in seen_names:
                continue
            seen_names.add(name)
            dest = paths.diagrams_dir / name
            if dest.is_file():
                skipped_existing += 1
                continue
            try:
                resp = await client.get(url)
                if resp.status_code == 200 and resp.content:
                    digest = hashlib.sha256(resp.content).hexdigest()[:16]
                    # Dedupe by content hash: skip if same hash already on disk
                    hash_marker = paths.diagrams_dir / f".{digest}.name"
                    if hash_marker.is_file():
                        skipped_existing += 1
                        continue
                    dest.write_bytes(resp.content)
                    hash_marker.write_text(name, encoding="utf-8")
                    downloaded += 1
            except Exception as exc:  # noqa: BLE001
                logger.warning("diagram download failed %s: %s", url, exc)
    return {
        "downloaded": downloaded,
        "skipped_existing": skipped_existing,
        "unique_diagrams": len(seen_names),
    }


def _write_meta(paths, payload: dict[str, Any]) -> None:
    paths.root.mkdir(parents=True, exist_ok=True)
    existing: dict[str, Any] = {}
    if paths.meta_json.is_file():
        existing = json.loads(paths.meta_json.read_text(encoding="utf-8"))
    existing.update(payload)
    existing["updated_at"] = datetime.now(timezone.utc).isoformat()
    paths.meta_json.write_text(json.dumps(existing, indent=2) + "\n", encoding="utf-8")


def run_maker_pipeline(
    maker: str,
    *,
    config: MegazipConfig,
    out_root: Path,
    phases: tuple[str, ...],
    max_pages: int | None,
    priority_chassis: frozenset[str] | None,
    skip_crawl: bool,
    priority_model_seeds: tuple[str, ...] = (),
    prepare_remaining_before: frozenset[str] | None = None,
    auto_start_remaining: bool = False,
    refresh_diagram_dims: bool = False,
    pcdb_file: Path | None,
    live_import: bool,
    complete_only: bool,
    strict_gate: bool,
    ensure_stock: bool,
    prune_stale: bool | None,
) -> dict[str, Any]:
    paths = build_maker_paths(maker, out_root, config)
    storage_prefix = config.storage_prefix(maker)
    result: dict[str, Any] = {"maker": maker, "slug": paths.slug, "phases": {}}

    if "crawl" in phases:
        crawl_stats = asyncio.run(
            crawl_maker(
                paths,
                config,
                max_pages=max_pages,
                priority_chassis=priority_chassis,
                skip_crawl=skip_crawl,
                priority_model_seeds=priority_model_seeds,
                prepare_remaining_before=prepare_remaining_before,
                auto_start_remaining=auto_start_remaining,
            )
        )
        result["phases"]["crawl"] = crawl_stats

    if "parse" in phases:
        parsed = parse_cached_pages(paths, refresh_diagram_dims=refresh_diagram_dims)
        result["phases"]["parse"] = {"pages_reparsed": parsed, "refresh_diagram_dims": refresh_diagram_dims}

    bundle: dict[str, Any] | None = None
    if "transform" in phases:
        bundle = transform_maker(paths, storage_prefix=storage_prefix)
        result["phases"]["transform"] = {
            "models": len(bundle.get("catalog_models") or []),
            "variants": len(bundle.get("catalog_variants") or []),
            "fitments": len(bundle.get("part_fitment") or []),
        }

    if bundle is None and paths.bundle_dir.is_dir():
        from data_pipeline.import_hierarchy_catalog import load_hierarchy_bundle

        bundle = load_hierarchy_bundle(paths.bundle_dir)

    if bundle and "pcdb" in phases:
        pcdb_stats = enrich_pcdb(bundle, pcdb_file)
        write_bundle(bundle, paths.bundle_dir)
        result["phases"]["pcdb"] = pcdb_stats

    if bundle and "filter" in phases:
        if complete_only:
            bundle, filter_meta = filter_complete_bundle(bundle, completed_only=True)
            write_bundle(bundle, paths.bundle_dir)
        quality = bundle_quality_report(bundle)
        variant_quality = variant_quality_breakdown(bundle)
        (paths.bundle_dir / "quality_report.json").write_text(
            json.dumps(quality, indent=2) + "\n",
            encoding="utf-8",
        )
        (paths.bundle_dir / "variant_quality.json").write_text(
            json.dumps({"variants": variant_quality}, indent=2) + "\n",
            encoding="utf-8",
        )
        result["phases"]["filter"] = {k: v for k, v in quality.items() if k != "variant_quality"}
        if quality.get("maker_publishable") is False and quality.get("publishable"):
            logger.info(
                "[%s] variant-level import ready (%s variants); maker_publishable=false (advisory)",
                maker,
                quality.get("variants_publishable"),
            )
        if strict_gate and live_import:
            assert_publishable(quality, strict=True)

    if bundle and "upload" in phases:
        up = asyncio.run(_upload_diagrams(paths, bundle))
        result["phases"]["upload"] = up

    if bundle and "import" in phases:
        imp = import_hierarchy_bundle_dir(
            paths.bundle_dir,
            live=live_import,
            complete_only=complete_only,
            prune_stale=prune_stale,
            ensure_stock_items=ensure_stock,
        )
        result["phases"]["import"] = {
            "live": live_import,
            "stats": {k: {"inserted": v.inserted, "updated": v.updated} for k, v in imp.stats.items()},
            "notes": imp.notes,
        }

    _write_meta(paths, {"last_run": result})
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Megazip multivehicle EPC catalog orchestrator (hierarchy + search + stock)."
    )
    parser.add_argument(
        "--makers",
        default="Nissan",
        help="Comma-separated makers or 'all' (default order from megazip_makers.json)",
    )
    parser.add_argument("--makers-file", type=Path, default=DEFAULT_MAKERS_FILE)
    parser.add_argument("--out-root", type=Path, default=DEFAULT_OUT_ROOT)
    parser.add_argument(
        "--phase",
        default="all",
        help=f"Phases: {','.join(ALL_PHASES)} or 'all'",
    )
    parser.add_argument("--max-pages", type=int, default=None, help="Cap crawl pages per maker (smoke)")
    parser.add_argument(
        "--priority-chassis",
        action="store_true",
        help="Restrict crawl to priority_chassis.json codes (Nissan phase A)",
    )
    parser.add_argument(
        "--all-models",
        action="store_true",
        help="Crawl all maker models without priority filter (Nissan phase B)",
    )
    parser.add_argument(
        "--nissan-two-phase",
        action="store_true",
        default=True,
        help="Nissan: priority chassis first, then all remaining models (default: on)",
    )
    parser.add_argument(
        "--no-nissan-two-phase",
        action="store_false",
        dest="nissan_two_phase",
        help="Disable automatic Nissan two-phase crawl",
    )
    parser.add_argument("--priority-chassis-file", type=Path, default=DEFAULT_PRIORITY_FILE)
    parser.add_argument("--chassis-map-file", type=Path, default=DEFAULT_CHASSIS_MAP_FILE)
    parser.add_argument(
        "--skip-crawl",
        action="store_true",
        help="Skip network crawl; re-run parse/transform/import from cache",
    )
    parser.add_argument(
        "--refresh-diagram-dims",
        action="store_true",
        help="Re-fetch PNG header bytes during parse to refresh image_width/image_height",
    )
    parser.add_argument(
        "--single-chassis",
        "--chassis",
        dest="single_chassis",
        default=None,
        help="Crawl only variants matching this chassis code (must be in priority_chassis.json, e.g. T32)",
    )
    parser.add_argument("--pcdb-file", type=Path, default=None, help="epc_to_pcdb.json mapping")
    parser.add_argument("--live-import", action="store_true", help="Import to Supabase")
    parser.add_argument(
        "--complete-only",
        action="store_true",
        default=True,
        help="Publish gate: complete fitments only (default: on)",
    )
    parser.add_argument(
        "--no-complete-only",
        action="store_false",
        dest="complete_only",
        help="Disable complete-only filter",
    )
    parser.add_argument(
        "--strict-gate",
        action="store_true",
        default=True,
        help="Fail live import when quality report fails (default: on)",
    )
    parser.add_argument("--no-strict-gate", action="store_false", dest="strict_gate")
    parser.add_argument(
        "--no-ensure-stock-items",
        action="store_false",
        dest="ensure_stock",
        help="Do not upsert stock_items on import",
    )
    parser.add_argument("--ensure-stock-items", action="store_true", default=True)
    parser.add_argument("--prune-stale", action="store_true", help="Prune live rows not in bundle")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)

    _setup_logging(args.verbose)
    config = MegazipConfig.load(args.makers_file)
    makers = _resolve_makers(args.makers, config)
    phases = _parse_phases(args.phase)
    chassis_map = load_megazip_chassis_map(args.chassis_map_file)

    priority: frozenset[str] | None = None
    model_seeds: tuple[str, ...] = ()
    all_priority = load_priority_chassis_codes(args.priority_chassis_file)
    skip_maker_crawl = False

    if args.single_chassis:
        code = args.single_chassis.strip().upper()
        if code not in all_priority:
            logger.error(
                "Chassis %s is not in %s (R35/GT-R is excluded — use a priority code like T32, D23, Y61)",
                code,
                args.priority_chassis_file,
            )
            return 1
        if not megazip_chassis_available(code, chassis_map):
            entry = megazip_chassis_entry(code, chassis_map)
            proxy = entry.get("megazip_proxy") or "none"
            primary = entry.get("primary_source") or "partsouq"
            logger.warning(
                "Chassis %s is not available on Megazip (proxy=%s, primary_source=%s) — skipping crawl",
                code,
                proxy,
                primary,
            )
            skip_maker_crawl = True
        else:
            priority = frozenset({code})
            model_seeds = megazip_model_seeds_for_chassis(
                code,
                chassis_map=chassis_map,
                priority_file=args.priority_chassis_file,
                maker_slug="nissan",
            )
            logger.info("Single-chassis mode: %s (model seeds: %s)", code, len(model_seeds))
    elif args.all_models:
        priority = None
        model_seeds = ()
        logger.info("All-models mode: no priority chassis filter")
    elif args.priority_chassis:
        priority = all_priority
        model_seeds = load_merged_priority_model_seeds(
            args.priority_chassis_file,
            args.chassis_map_file,
            maker_slug="nissan",
            priority_codes=priority,
        )
        for code in sorted(priority):
            if not megazip_chassis_available(code, chassis_map):
                entry = megazip_chassis_entry(code, chassis_map)
                logger.warning(
                    "Priority chassis %s not on Megazip (proxy=%s) — crawl may yield no diagrams",
                    code,
                    entry.get("megazip_proxy"),
                )
        logger.info("Priority chassis mode: %s codes, %s model seeds", len(priority), len(model_seeds))

    manifest: dict[str, Any] = {
        "source": "epc",
        "makers": makers,
        "phases": phases,
        "results": [],
    }
    args.out_root.mkdir(parents=True, exist_ok=True)

    def _run_maker(
        maker: str,
        *,
        phases_run: tuple[str, ...],
        pass_label: str,
        maker_priority: frozenset[str] | None,
        maker_seeds: tuple[str, ...],
        skip_crawl: bool,
        prepare_remaining_before: frozenset[str] | None = None,
        auto_start_remaining: bool = False,
    ) -> bool:
        logger.info("=== Megazip pipeline: %s [%s] ===", maker, pass_label)
        try:
            res = run_maker_pipeline(
                maker,
                config=config,
                out_root=args.out_root,
                phases=phases_run,
                max_pages=args.max_pages,
                priority_chassis=maker_priority,
                priority_model_seeds=maker_seeds,
                skip_crawl=skip_crawl,
                prepare_remaining_before=prepare_remaining_before,
                auto_start_remaining=auto_start_remaining,
                refresh_diagram_dims=args.refresh_diagram_dims,
                pcdb_file=args.pcdb_file,
                live_import=args.live_import,
                complete_only=args.complete_only,
                strict_gate=args.strict_gate,
                ensure_stock=args.ensure_stock,
                prune_stale=args.prune_stale if args.prune_stale else None,
            )
            res["pass"] = pass_label
            manifest["results"].append(res)
            return True
        except Exception as exc:  # noqa: BLE001
            logger.error("[%s/%s] pipeline failed: %s", maker, pass_label, exc)
            manifest["results"].append({"maker": maker, "pass": pass_label, "error": str(exc)})
            return not args.strict_gate

    for maker in makers:
        if skip_maker_crawl and maker.lower() == "nissan" and args.single_chassis:
            logger.info("=== Megazip pipeline: %s (skipped — chassis not on Megazip) ===", maker)
            manifest["results"].append(
                {"maker": maker, "pass": "single", "skipped": "chassis_not_on_megazip"}
            )
            continue

        is_nissan_two = (
            maker.lower() == "nissan"
            and args.nissan_two_phase
            and not args.single_chassis
            and not args.all_models
            and not args.priority_chassis
            and not args.skip_crawl
            and "crawl" in phases
        )

        if is_nissan_two:
            seeds_a = load_merged_priority_model_seeds(
                args.priority_chassis_file,
                args.chassis_map_file,
                maker_slug="nissan",
                priority_codes=all_priority,
            )
            for code in sorted(all_priority):
                if not megazip_chassis_available(code, chassis_map):
                    entry = megazip_chassis_entry(code, chassis_map)
                    logger.warning(
                        "Priority chassis %s not on Megazip (proxy=%s)",
                        code,
                        entry.get("megazip_proxy"),
                    )
            # Single crawl: priority filter first, then auto-handoff to remaining models.
            if not _run_maker(
                maker,
                phases_run=("crawl",),
                pass_label="nissan-two-phase",
                maker_priority=all_priority,
                maker_seeds=seeds_a,
                skip_crawl=False,
                auto_start_remaining=True,
            ):
                return 1
            tail = tuple(p for p in phases if p != "crawl")
            if tail and not _run_maker(
                maker,
                phases_run=tail,
                pass_label="nissan-post-crawl",
                maker_priority=None,
                maker_seeds=(),
                skip_crawl=True,
            ):
                return 1
            continue

        maker_priority = priority if maker.lower() == "nissan" and priority else None
        maker_seeds = model_seeds if maker.lower() == "nissan" and model_seeds else ()
        # --all-models: when the current drain finishes, queue underexplored hub models.
        auto_remaining = bool(args.all_models and maker.lower() == "nissan")
        if not _run_maker(
            maker,
            phases_run=phases,
            pass_label="default",
            maker_priority=maker_priority,
            maker_seeds=maker_seeds,
            skip_crawl=args.skip_crawl,
            auto_start_remaining=auto_remaining,
        ):
            return 1

    manifest_path = args.out_root / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    logger.info("Manifest written: %s", manifest_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
