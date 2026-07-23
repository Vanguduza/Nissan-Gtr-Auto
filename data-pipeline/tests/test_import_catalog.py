from __future__ import annotations

import json
from pathlib import Path

from data_pipeline.import_catalog import import_catalog, load_bundle

FIXTURE_DIR = Path(__file__).resolve().parent.parent / "fixtures" / "navara_d40_yd25"


def _oe_cross_refs() -> list[dict]:
    return json.loads((FIXTURE_DIR / "oe_cross_refs.json").read_text(encoding="utf-8"))


def test_import_twice_is_idempotent() -> None:
    bundle = load_bundle(FIXTURE_DIR)
    oe = _oe_cross_refs()

    first = import_catalog(bundle, oe_cross_refs=oe)
    counts_first = first.store.row_counts()

    second = import_catalog(bundle, store=first.store, oe_cross_refs=oe)
    counts_second = second.store.row_counts()

    assert counts_first == counts_second
    assert all(stat.inserted > 0 for stat in first.stats.values())
    assert all(stat.inserted == 0 for stat in second.stats.values())
    assert all(stat.unchanged > 0 for stat in second.stats.values())


def test_import_cli_dry_run(capsys) -> None:
    from data_pipeline.import_catalog import main

    assert main([str(FIXTURE_DIR)]) == 0
    assert "Dry-run import OK" in capsys.readouterr().out
