"""Deprecated path — use the combined Patchright pipeline.

    cd data-pipeline
    pip install -e ".[scraping,dev]"
    patchright install chromium
    python -m data_pipeline.amayama_catalog_auto
"""

from __future__ import annotations

import runpy
import sys
from pathlib import Path


def main() -> int:
    pipeline_root = Path(__file__).resolve().parents[1] / "data-pipeline"
    sys.path.insert(0, str(pipeline_root))
    print(
        "Forwarding to data_pipeline.amayama_catalog_auto (Patchright)",
        file=sys.stderr,
    )
    sys.argv[0] = "data_pipeline.amayama_catalog_auto"
    runpy.run_module("data_pipeline.amayama_catalog_auto", run_name="__main__")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
