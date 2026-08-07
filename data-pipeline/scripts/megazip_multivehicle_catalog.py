#!/usr/bin/env python3
"""Thin CLI wrapper for Megazip multivehicle catalog development.

See: python -m data_pipeline.megazip_catalog_orchestrator --help
Guide: docs/guides/megazip-multivehicle-catalog.md
"""

from data_pipeline.megazip_catalog_orchestrator import main

if __name__ == "__main__":
    raise SystemExit(main())
