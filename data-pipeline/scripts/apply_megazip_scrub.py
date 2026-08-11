"""Apply catalog megazip_* → external_* rename (same path as live import)."""

from __future__ import annotations

import json
import sys

from data_pipeline.megazip.schema_ensure import ensure_external_catalog_columns


def main() -> int:
    status = ensure_external_catalog_columns()
    print(json.dumps(status, indent=2))
    if not status.get("ok", False):
        return 1
    if status.get("reason") == "no_database_url":
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
