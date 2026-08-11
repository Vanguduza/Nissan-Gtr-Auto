"""Thin CLI: Megazip multivehicle catalog (all makers).

Primary entrypoint::

  python -m data_pipeline.megazip_catalog_orchestrator --help

This wrapper is identical. See the multimaker runbook:

  docs/guides/megazip-multivehicle-catalog.md

Nissan lessons (engines, vendor scrub, workers, re-parse) apply to Toyota/Honda/…
"""

from data_pipeline.megazip_catalog_orchestrator import main

if __name__ == "__main__":
    raise SystemExit(main())
