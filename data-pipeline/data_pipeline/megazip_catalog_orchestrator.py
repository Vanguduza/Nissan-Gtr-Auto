"""Sequential Megazip multi-maker catalog pipeline.

Runs one maker at a time in ``config/megazip_makers.json`` order (homepage
popularity: Toyota → Lexus → Honda → …). Each maker completes crawl → parse →
transform → (optional import) before the next starts.
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from data_pipeline.megazip.crawl import run_crawl
from data_pipeline.megazip.makers import (
    load_makers_config,
    load_merged_priority_model_seeds,
    load_priority_chassis,
    maker_out_dir,
    resolve_makers,
)
from data_pipeline.megazip.parse_html import run_parse
from data_pipeline.megazip.quality import write_quality_report
from data_pipeline.megazip.transform import run_transform

logger = logging.getLogger(__name__)

PHASES = ("crawl", "parse", "transform", "import")


def _utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def run_maker_pipeline(
    maker: str,
    *,
    config: dict[str, Any],
    out_root: Path,
    phases: tuple[str, ...],
    max_pages: int | None,
    priority_chassis: frozenset[str] | None,
    priority_model_seeds: tuple[str, ...] = (),
    skip_crawl: bool,
    refresh_diagram_dims: bool,
    pcdb_file: Path | None,
    live_import: bool,
    complete_only: bool,
    strict_gate: bool,
    ensure_stock: bool,
    prune_stale: str | None,
) -> dict[str, Any]:
    """Run selected phases for one maker. Returns a summary dict."""
    maker_dir = maker_out_dir(out_root, maker)
    maker_dir.mkdir(parents=True, exist_ok=True)
    summary: dict[str, Any] = {"maker": maker, "out_dir": str(maker_dir), "phases": {}}

    if "crawl" in phases and not skip_crawl:
        crawl_stats = run_crawl(
            maker,
            config=config,
            out_dir=maker_dir,
            max_pages=max_pages,
            priority_chassis=priority_chassis,
            priority_model_seeds=priority_model_seeds or None,
        )
        summary["phases"]["crawl"] = crawl_stats

    if "parse" in phases:
        parse_stats = run_parse(maker_dir)
        summary["phases"]["parse"] = parse_stats

    if "transform" in phases:
        xform = run_transform(
            maker_dir,
            maker=maker,
            refresh_diagram_dims=refresh_diagram_dims,
            pcdb_file=pcdb_file,
        )
        summary["phases"]["transform"] = {
            "bundle": str(xform["bundle_path"]),
            "quality_report": str(xform["quality_report_path"]),
            "counts": xform.get("counts"),
            "quality": xform.get("quality"),
        }
        gate = xform.get("quality") or {}
        if strict_gate and not gate.get("ok", True):
            raise RuntimeError(f"quality gate failed for {maker}: {gate.get('errors')}")

    if "import" in phases:
        if not live_import:
            summary["phases"]["import"] = {"skipped": True, "reason": "pass --live-import"}
        else:
            from data_pipeline.import_hierarchy_catalog import run_import as run_hierarchy_import

            bundle = maker_dir / "megazip_hierarchy_bundle.json"
            if not bundle.is_file():
                raise FileNotFoundError(f"missing bundle for import: {bundle}")
            qr_path = maker_dir / "quality_report.json"
            if qr_path.is_file():
                write_quality_report(json.loads(qr_path.read_text(encoding="utf-8")), qr_path)
            if complete_only:
                qr = json.loads(qr_path.read_text(encoding="utf-8")) if qr_path.is_file() else {}
                if not qr.get("ok", False):
                    raise RuntimeError(f"--complete-only: quality not ok for {maker}")
            imp = run_hierarchy_import(
                bundle,
                dry_run=False,
                ensure_stock=ensure_stock,
                prune_stale=prune_stale,
            )
            summary["phases"]["import"] = imp

    (maker_dir / "pipeline_summary.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return summary


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description=(
            "Sequential Megazip multi-maker catalog: one maker fully through "
            "selected phases, then the next (order from megazip_makers.json)."
        )
    )
    p.add_argument(
        "--makers",
        default="all",
        help="Comma list or 'all' (config order). Default: all.",
    )
    p.add_argument(
        "--config",
        type=Path,
        default=None,
        help="Path to megazip_makers.json (default: data-pipeline/config/…)",
    )
    p.add_argument(
        "--out-root",
        type=Path,
        default=Path("out/megazip"),
        help="Root output directory (per-maker subdirs created under this)",
    )
    p.add_argument(
        "--phases",
        default="crawl,parse,transform",
        help="Comma list from: crawl,parse,transform,import",
    )
    p.add_argument("--max-pages", type=int, default=None, help="Crawl page budget (debug)")
    p.add_argument(
        "--priority-chassis",
        default="",
        help="Optional chassis codes to seed first within a maker (comma-separated)",
    )
    p.add_argument(
        "--priority-chassis-file",
        type=Path,
        default=None,
        help="Optional JSON list of chassis codes (merged with --priority-chassis)",
    )
    p.add_argument(
        "--chassis-map-file",
        type=Path,
        default=None,
        help="Optional chassis→model seed map (default: config/megazip_nissan_chassis_map.json)",
    )
    p.add_argument(
        "--all-models",
        action="store_true",
        help="Ignore priority chassis filters; crawl every model for selected makers",
    )
    p.add_argument(
        "--single-chassis",
        default="",
        metavar="CODE",
        help="Limit crawl to one chassis (seeds its model only)",
    )
    p.add_argument("--skip-crawl", action="store_true", help="Skip crawl even if in --phases")
    p.add_argument("--refresh-diagram-dims", action="store_true")
    p.add_argument("--pcdb-file", type=Path, default=None)
    p.add_argument(
        "--live-import",
        action="store_true",
        help="Actually run Supabase import when 'import' is in --phases",
    )
    p.add_argument(
        "--complete-only",
        action="store_true",
        help="Refuse import unless quality_report.ok is true",
    )
    p.add_argument(
        "--strict-gate",
        action="store_true",
        help="Abort maker (and stop sequence) if quality gate fails after transform",
    )
    p.add_argument("--ensure-stock", action="store_true")
    p.add_argument(
        "--prune-stale",
        choices=("off", "report", "delete"),
        default="",
        help="Pass-through to hierarchy import prune mode",
    )
    p.add_argument("-v", "--verbose", action="store_true")
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )

    config = load_makers_config(args.config)
    makers = resolve_makers(args.makers, config)
    if not makers:
        logger.error("No makers resolved from --makers=%r", args.makers)
        return 2

    phases = tuple(p.strip() for p in args.phases.split(",") if p.strip())
    for ph in phases:
        if ph not in PHASES:
            logger.error("Unknown phase %r (allowed: %s)", ph, ",".join(PHASES))
            return 2

    file_codes = load_priority_chassis(args.priority_chassis_file)
    cli_codes = {c.strip().upper() for c in args.priority_chassis.split(",") if c.strip()}
    all_priority = frozenset(file_codes | cli_codes)

    single = (args.single_chassis or "").strip().upper()
    if single:
        all_priority = frozenset({single})
        args.all_models = False

    priority: frozenset[str] | None
    if args.all_models:
        priority = None
    elif all_priority:
        priority = all_priority
    else:
        priority = None

    skip_maker_crawl = False
    if single:
        from data_pipeline.megazip.makers import load_chassis_map, megazip_chassis_available

        cmap = load_chassis_map(args.chassis_map_file)
        if not megazip_chassis_available(single, cmap):
            logger.warning(
                "Chassis %s is not available as a Megazip seed (see chassis map); "
                "skipping crawl for makers that rely on that seed",
                single,
            )
            skip_maker_crawl = True

    manifest: dict[str, Any] = {
        "started_at": _utc_now(),
        "makers": makers,
        "phases": list(phases),
        "results": [],
    }
    args.out_root.mkdir(parents=True, exist_ok=True)

    for maker in makers:
        if skip_maker_crawl and maker.lower() == "nissan" and args.single_chassis:
            logger.info("=== Megazip pipeline: %s (skipped — chassis not on Megazip) ===", maker)
            manifest["results"].append(
                {"maker": maker, "skipped": "chassis_not_on_megazip"}
            )
            continue

        maker_priority = priority if maker.lower() == "nissan" and priority else None
        maker_seeds: tuple[str, ...] = ()
        if maker_priority:
            maker_seeds = load_merged_priority_model_seeds(
                args.priority_chassis_file,
                args.chassis_map_file,
                maker_slug="nissan",
                priority_codes=maker_priority,
            )

        logger.info("=== Megazip pipeline: %s (sequential) ===", maker)
        try:
            res = run_maker_pipeline(
                maker,
                config=config,
                out_root=args.out_root,
                phases=phases,
                max_pages=args.max_pages,
                priority_chassis=maker_priority,
                priority_model_seeds=maker_seeds,
                skip_crawl=args.skip_crawl,
                refresh_diagram_dims=args.refresh_diagram_dims,
                pcdb_file=args.pcdb_file,
                live_import=args.live_import,
                complete_only=args.complete_only,
                strict_gate=args.strict_gate,
                ensure_stock=args.ensure_stock,
                prune_stale=args.prune_stale if args.prune_stale else None,
            )
            manifest["results"].append(res)
        except Exception as exc:  # noqa: BLE001
            logger.error("[%s] pipeline failed: %s", maker, exc)
            manifest["results"].append({"maker": maker, "error": str(exc)})
            if args.strict_gate:
                manifest["finished_at"] = _utc_now()
                (args.out_root / "orchestrator_manifest.json").write_text(
                    json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
                    encoding="utf-8",
                )
                return 1

    manifest["finished_at"] = _utc_now()
    man_path = args.out_root / "orchestrator_manifest.json"
    man_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    logger.info("Wrote orchestrator manifest %s", man_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
