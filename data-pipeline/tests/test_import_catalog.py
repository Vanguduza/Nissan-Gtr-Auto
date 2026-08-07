from __future__ import annotations

import json
from pathlib import Path

from data_pipeline.import_catalog import (
    build_stock_items_from_fitment,
    import_catalog,
    load_bundle,
    resolve_supabase_credentials,
)

FIXTURE_DIR = Path(__file__).resolve().parent.parent / "fixtures" / "navara_d40_yd25"
ERP_BUNDLE = Path(__file__).resolve().parent.parent / "out" / "erp_catalog_v1"


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

    assert main([str(FIXTURE_DIR), "--no-ensure-stock-items"]) == 0
    assert "Dry-run import OK" in capsys.readouterr().out


def test_ensure_stock_items_from_fitment() -> None:
    bundle = load_bundle(FIXTURE_DIR)
    result = import_catalog(bundle, ensure_stock_items=True)
    assert "stock_items" in result.stats
    assert result.stats["stock_items"].inserted > 0
    stock = build_stock_items_from_fitment(
        bundle["part_fitment"], bundle.get("pnc_categories")
    )
    assert len(stock) == result.store.row_counts()["stock_items"]


def test_stock_items_use_part_display_names() -> None:
    rows = build_stock_items_from_fitment(
        [{"oem_part_number": "01121-04411", "pnc_code": "01121A"}],
        [{"pnc_code": "01121A", "category_name": "POWER TRAIN", "subcategory_name": None}],
        oem_display_names={"01121-04411": "BOLT"},
    )
    assert rows[0]["description"] == "BOLT"


def test_resolve_supabase_credentials_service_key_alias(monkeypatch) -> None:
    monkeypatch.delenv("SUPABASE_SERVICE_ROLE_KEY", raising=False)
    monkeypatch.setenv("SUPABASE_URL", "http://127.0.0.1:54321")
    monkeypatch.setenv("SUPABASE_SERVICE_KEY", "test-key")
    url, key = resolve_supabase_credentials()
    assert url == "http://127.0.0.1:54321"
    assert key == "test-key"


def test_erp_catalog_v1_dry_run_counts() -> None:
    if not ERP_BUNDLE.is_dir():
        return
    from data_pipeline.import_catalog import main

    assert main([str(ERP_BUNDLE), "--no-ensure-stock-items"]) == 0
