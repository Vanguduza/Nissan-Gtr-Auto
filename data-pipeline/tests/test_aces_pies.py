"""Tests for thin ACES/PIES enrichment adapters."""

from __future__ import annotations

import json
from pathlib import Path

from data_pipeline.aces_pies.aces import (
    ACES_APPLY_STUB_REASON,
    parse_aces_xml,
    stub_apply_aces_apps,
)
from data_pipeline.aces_pies.enrich import (
    apply_pies_to_bundle,
    enrich_bundle_pcdb_then_pies,
    merge_pies_into_epc_mapping,
    stock_rows_from_pies,
)
from data_pipeline.aces_pies.pies import parse_pies_xml
from data_pipeline.aces_pies_import import main as aces_pies_main

FIXTURES = Path(__file__).resolve().parent.parent / "fixtures" / "aces_pies"
SAMPLE_PIES = FIXTURES / "sample_pies.xml"
SAMPLE_ACES = FIXTURES / "sample_aces.xml"


def test_parse_pies_sample() -> None:
    items = parse_pies_xml(SAMPLE_PIES)
    assert len(items) == 2
    by_oem = {i.part_number: i for i in items}
    pad = by_oem["41060EB70A"]
    assert pad.part_terminology_id == 1684
    assert pad.brand_aaia_id == "GTST"
    assert "Disc Brake" in pad.primary_description
    assert pad.attributes.get("110") == "1.2"
    assert pad.attributes.get("HAZMAT") == "N"


def test_parse_aces_sample() -> None:
    apps = parse_aces_xml(SAMPLE_ACES)
    assert len(apps) == 2
    assert apps[0].part_number == "41060EB70A"
    assert apps[0].base_vehicle_id == 90001
    assert apps[0].part_terminology_id == 1684
    stub = stub_apply_aces_apps(apps)
    assert stub["status"] == "stubbed"
    assert stub["apps_applied"] == 0
    assert ACES_APPLY_STUB_REASON in stub["reason"]


def test_apply_pies_sets_pcdb_and_display_names() -> None:
    items = parse_pies_xml(SAMPLE_PIES)
    bundle = {
        "pnc_categories": [
            {"pnc_code": "44000", "category_name": "BRAKE"},
            {"pnc_code": "10000", "category_name": "ENGINE ASSEMBLY"},
        ],
        "part_fitment": [
            {
                "oem_part_number": "41060EB70A",
                "pnc_code": "44000",
                "chassis_code": "D40",
                "engine_code": "YD25",
            },
            {
                "oem_part_number": "10101EB30A",
                "pnc_code": "10000",
                "chassis_code": "D40",
                "engine_code": "YD25",
            },
        ],
    }
    result = apply_pies_to_bundle(bundle, items)
    assert result.pcdb_mapped == 2
    assert bundle["pnc_categories"][0]["pcdb_part_type_id"] == 1684
    assert bundle["pnc_categories"][1]["pcdb_part_type_id"] == 12000
    assert "41060EB70A" in bundle["_oem_display_names"]
    assert result.attrs_retained >= 1
    rows = stock_rows_from_pies(items)
    assert any(r["oem_part_number"] == "41060EB70A" and "Brake" in r["description"] for r in rows)


def test_enrich_preserves_existing_pcdb_id() -> None:
    items = parse_pies_xml(SAMPLE_PIES)
    bundle = {
        "pnc_categories": [
            {"pnc_code": "44000", "category_name": "BRAKE", "pcdb_part_type_id": 99999},
        ],
        "part_fitment": [
            {"oem_part_number": "41060EB70A", "pnc_code": "44000", "chassis_code": "D40"},
        ],
    }
    result = apply_pies_to_bundle(bundle, items)
    assert result.pcdb_mapped == 0
    assert bundle["pnc_categories"][0]["pcdb_part_type_id"] == 99999


def test_curated_pcdb_then_pies() -> None:
    """Curated epc_to_pcdb wins on known names; PIES fills unmapped PNCs via OEM join."""
    items = parse_pies_xml(SAMPLE_PIES)
    bundle = {
        "pnc_categories": [
            {"pnc_code": "44000", "category_name": "BRAKE"},  # curated → 13000
            {"pnc_code": "10000", "category_name": "ENGINE ASSEMBLY"},  # curated → 12000
            {"pnc_code": "55000", "category_name": "UNMAPPED GROUP"},  # PIES OEM only
        ],
        "part_fitment": [
            {"oem_part_number": "41060EB70A", "pnc_code": "55000", "chassis_code": "D40"},
        ],
    }
    result = enrich_bundle_pcdb_then_pies(bundle, items)
    by_pnc = {r["pnc_code"]: r for r in bundle["pnc_categories"]}
    assert by_pnc["10000"]["pcdb_part_type_id"] == 12000
    assert by_pnc["44000"]["pcdb_part_type_id"] == 13000
    assert by_pnc["55000"]["pcdb_part_type_id"] == 1684
    assert result.pcdb_mapped == 1


def test_merge_pies_into_mapping(tmp_path: Path) -> None:
    items = parse_pies_xml(SAMPLE_PIES)
    out = tmp_path / "epc_to_pcdb.json"
    doc, added = merge_pies_into_epc_mapping(items, mapping_path=None)
    assert added >= 1
    out.write_text(json.dumps(doc), encoding="utf-8")
    doc2, added2 = merge_pies_into_epc_mapping(items, mapping_path=out)
    assert added2 == 0


def test_cli_dry_run(tmp_path: Path) -> None:
    out = tmp_path / "enrich"
    rc = aces_pies_main(
        [
            "--pies",
            str(SAMPLE_PIES),
            "--aces",
            str(SAMPLE_ACES),
            "--out",
            str(out),
        ]
    )
    assert rc == 0
    assert (out / "pies_items.json").is_file()
    assert (out / "aces_apps.json").is_file()
    report = json.loads((out / "enrichment_report.json").read_text(encoding="utf-8"))
    assert report["aces"]["status"] == "stubbed"
    assert report["pies_items"] == 2


def test_cli_with_bundle(tmp_path: Path) -> None:
    bundle_dir = tmp_path / "bundle"
    bundle_dir.mkdir()
    (bundle_dir / "pnc_categories.json").write_text(
        json.dumps([{"pnc_code": "44000", "category_name": "UNMAPPED GROUP"}]),
        encoding="utf-8",
    )
    (bundle_dir / "part_fitment.json").write_text(
        json.dumps(
            [
                {
                    "oem_part_number": "41060EB70A",
                    "pnc_code": "44000",
                    "chassis_code": "D40",
                    "engine_code": "YD25",
                }
            ]
        ),
        encoding="utf-8",
    )
    (bundle_dir / "vehicle_master.json").write_text("[]", encoding="utf-8")
    out = tmp_path / "out"
    rc = aces_pies_main(
        ["--pies", str(SAMPLE_PIES), "--bundle", str(bundle_dir), "--out", str(out)]
    )
    assert rc == 0
    pncs = json.loads((out / "pnc_categories_enriched.json").read_text(encoding="utf-8"))
    assert pncs[0]["pcdb_part_type_id"] == 1684


def test_pies_namespace_optional() -> None:
    xml = b"""<?xml version="1.0"?><PIES><Items>
      <Item><PartNumber>X1</PartNumber><PartTerminologyID>42</PartTerminologyID>
      <Descriptions><Description DescriptionCode="DES">Widget</Description></Descriptions>
      </Item></Items></PIES>"""
    items = parse_pies_xml(xml)
    assert len(items) == 1
    assert items[0].part_number == "X1"
    assert items[0].part_terminology_id == 42
