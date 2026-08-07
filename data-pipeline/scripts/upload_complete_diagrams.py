"""Upload diagram GIFs for complete-only bundle (no DB refresh)."""
from __future__ import annotations

import asyncio
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from data_pipeline.amayama_catalog_auto import ScrapeConfig, download_bundle_diagrams
from data_pipeline.bundle_filter import filter_complete_bundle
from data_pipeline.import_catalog import load_bundle, load_env_files

load_env_files(ROOT.parent / ".env", ROOT / ".env")
bundle = load_bundle(ROOT / "out" / "erp_catalog_v1")
filtered, meta = filter_complete_bundle(bundle, completed_only=True)
print(f"Complete-only diagrams: {len(filtered.get('diagram_assets') or [])} assets", meta)
config = ScrapeConfig.load(ROOT / "config" / "scrape.json")
asyncio.run(
    download_bundle_diagrams(
        filtered,
        diagrams_dir=ROOT / "out" / "diagram_downloads",
        config=config,
        upload=True,
    )
)
print("Upload pass complete")
