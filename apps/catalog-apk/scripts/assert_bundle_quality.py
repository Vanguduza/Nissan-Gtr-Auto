#!/usr/bin/env python3
"""Assert a catalog job out-root meets publish quality (exit 0 = PASS, 1 = FAIL).

SoT:
  - docs/guides/partsouq-multimake-catalog-pipeline.md §2c
  - data_pipeline.megazip.quality.assert_publishable / bundle_quality_report
  - data_pipeline.bundle_quality_gate

Usage:
  python apps/catalog-apk/scripts/assert_bundle_quality.py path/to/job/out
  python apps/catalog-apk/scripts/assert_bundle_quality.py path/to/job/out --engine partsouq

PASS requires non-empty complete fitments + diagrams (megazip/partsouq).
Hierarchy-only stubs and custom/7zap adapters always FAIL.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

# Prefer monorepo data-pipeline, then APK-vendored copy.
_REPO = Path(__file__).resolve().parents[3]
for _cand in (
    _REPO / "data-pipeline",
    _REPO / "apps" / "catalog-apk" / "app" / "src" / "main" / "python",
):
    if _cand.is_dir() and str(_cand) not in sys.path:
        sys.path.insert(0, str(_cand))

from data_pipeline.bundle_quality_gate import (  # noqa: E402
    assert_out_root_publishable,
    evaluate_out_root,
    write_gate_artifacts,
)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("out_root", type=Path, help="Job --out-root (contains maker/bundle/)")
    p.add_argument("--engine", default=None, help="Override engine (megazip|partsouq|custom)")
    p.add_argument(
        "--json",
        action="store_true",
        help="Print full verdict JSON to stdout",
    )
    args = p.parse_args(argv)

    out_root = args.out_root.resolve()
    if not out_root.is_dir():
        print(f"FAIL: out_root not a directory: {out_root}", file=sys.stderr)
        return 1

    try:
        verdict = assert_out_root_publishable(out_root, engine=args.engine)
    except RuntimeError as exc:
        verdict = evaluate_out_root(out_root, engine=args.engine)
        write_gate_artifacts(out_root, verdict)
        print(f"FAIL: {exc}", file=sys.stderr)
        print(f"(wrote {out_root / 'bundle_quality.txt'})", file=sys.stderr)
        if args.json:
            print(json.dumps(verdict, indent=2))
        return 1

    print(
        "PASS: "
        f"engine={verdict.get('engine')} "
        f"diagrams={verdict.get('diagrams')} "
        f"fitments_out={verdict.get('fitments_out')} "
        f"png_count={verdict.get('png_count')}"
    )
    print(f"(wrote {out_root / 'bundle_quality.txt'})")
    if args.json:
        print(json.dumps(verdict, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
