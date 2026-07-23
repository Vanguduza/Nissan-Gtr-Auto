from __future__ import annotations

import json
from pathlib import Path

from data_pipeline.import_catalog import import_catalog, load_bundle
from data_pipeline.search_index import CatalogIndex

FIXTURE_DIR = Path(__file__).resolve().parent.parent / "fixtures" / "navara_d40_yd25"


def _index() -> CatalogIndex:
    bundle = load_bundle(FIXTURE_DIR)
    oe = json.loads((FIXTURE_DIR / "oe_cross_refs.json").read_text(encoding="utf-8"))
    import_catalog(bundle, oe_cross_refs=oe)
    return CatalogIndex.from_bundle(bundle, oe_cross_refs=oe)


def test_search_part_oem() -> None:
    hits = _index().search("part", "15208-65F0C")
    assert any(h["oem_part_number"] == "15208-65F0C" for h in hits)


def test_search_part_oe_cross_ref() -> None:
    hits = _index().search("part", "AY100-NS004")
    assert any(h["oem_part_number"] == "15208-65F0C" for h in hits)


def test_search_part_supersession() -> None:
    hits = _index().search("part", "21010-JF00A")
    assert any(h["oem_part_number"] == "21010-JF00A" for h in hits)


def test_search_vin_prefix() -> None:
    hits = _index().search("vin", "MNTCCND40U123456")
    assert len(hits) == 1
    assert hits[0]["chassis_code"] == "D40"
    assert len(hits[0]["fitments"]) >= 4


def test_search_model() -> None:
    hits = _index().search("model", "Navara D40")
    assert any("Navara" in h["model_variant"] for h in hits)


def test_search_pnc() -> None:
    hits = _index().search("pnc", "15208")
    assert any(h["pnc_code"] == "15208" for h in hits)
    oil = next(h for h in hits if h["pnc_code"] == "15208")
    assert any(f["oem_part_number"] == "15208-65F0C" for f in oil["fitments"])
