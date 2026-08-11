"""Fully strip Nissan two-phase from megazip_catalog_orchestrator.py."""
from __future__ import annotations

import re
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "data_pipeline" / "megazip_catalog_orchestrator.py"
text = p.read_text(encoding="utf-8")

new_doc = '''Megazip multivehicle EPC catalog orchestrator.

EPC-first, PCdb additive, re-runnable without re-crawl.

Phases (``--phase``):
  crawl     — fetch HTML to cache (skip with ``--skip-crawl``)
  parse     — re-parse cached HTML into SQLite (no network)
  transform — build hierarchy JSON bundle (re-runnable)
  pcdb      — additive PartTerminologyID mapping (re-runnable)
  filter    — complete-only filter + quality report
  upload    — download diagram PNGs + Storage upsert (long Cache-Control)
  import    — Supabase hierarchy + fitment + stock_items
  all       — default pipeline for each maker

Maker order (``config/megazip_makers.json``): Toyota → Lexus → Honda → … (homepage popularity).
One maker at a time through selected phases, then the next.

Usage (from ``data-pipeline/``)::

  python -m data_pipeline.megazip_catalog_orchestrator --makers Toyota --max-pages 50
  python -m data_pipeline.megazip_catalog_orchestrator --makers all --phase crawl
  python -m data_pipeline.megazip_catalog_orchestrator --phase transform,pcdb,import --skip-crawl
  python -m data_pipeline.megazip_catalog_orchestrator --makers all --live-import --complete-only
'''
parts = text.split('"""', 2)
text = '"""' + new_doc + '"""' + parts[2]

text = re.sub(
    r"\n    parser\.add_argument\(\n        \"--nissan-two-phase\".*?help=\"Disable automatic Nissan two-phase crawl\",\n    \)\n",
    "\n",
    text,
    flags=re.S,
)

text = text.replace(
    "Restrict crawl to priority_chassis.json codes (Nissan phase A)",
    "Restrict crawl to priority_chassis.json codes (optional filter)",
)
text = text.replace(
    "Crawl all maker models without priority filter (Nissan phase B)",
    "Crawl all maker models without priority filter",
)
text = text.replace(
    'default="Nissan",\n        help="Comma-separated makers or \'all\' (default order from megazip_makers.json)",',
    'default="all",\n        help="Comma-separated makers or \'all\' (default: all, order from megazip_makers.json)",',
)

# Pipeline signature + crawl call kwargs
text = text.replace(
    "    prepare_remaining_before: frozenset[str] | None = None,\n"
    "    auto_start_remaining: bool = False,\n",
    "",
)
text = text.replace(
    "                prepare_remaining_before=prepare_remaining_before,\n"
    "                auto_start_remaining=auto_start_remaining,\n",
    "",
)
text = text.replace(
    "        prepare_remaining_before: frozenset[str] | None = None,\n"
    "        auto_start_remaining: bool = False,\n",
    "",
)

# Replace is_nissan_two block through end of for-loop body
start = text.find("        is_nissan_two = (")
end = text.find("    manifest_path = args.out_root")
if start < 0 or end < 0:
    raise SystemExit(f"loop markers not found start={start} end={end}")

text = (
    text[:start]
    + '''        maker_priority = priority if maker.lower() == "nissan" and priority else None
        maker_seeds = model_seeds if maker.lower() == "nissan" and model_seeds else ()
        if not _run_maker(
            maker,
            phases_run=phases,
            pass_label="sequential",
            maker_priority=maker_priority,
            maker_seeds=maker_seeds,
            skip_crawl=args.skip_crawl,
        ):
            return 1

'''
    + text[end:]
)

# Drop leftover auto_remaining comments if any
text = re.sub(
    r"\n        # --all-models: when the current drain finishes.*?\n",
    "\n",
    text,
)

for needle in (
    "nissan_two_phase",
    "auto_start_remaining",
    "prepare_remaining_before",
    "is_nissan_two",
    "nissan-two-phase",
    "auto_remaining",
):
    if needle in text:
        raise SystemExit(f"still contains {needle}")

p.write_text(text, encoding="utf-8")
print("wrote", p, "lines", text.count("\n") + 1)
