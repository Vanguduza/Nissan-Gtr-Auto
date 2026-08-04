from __future__ import annotations

import pytest

from data_pipeline.vin_decode import (
    chassis_from_prefix,
    decode_from_chassis,
    decode_model_year,
    decode_vin_local,
    enrich_hints_with_vin,
    resolve_vin_to_vehicle_hints,
)


def test_decode_model_year_iso() -> None:
    # Position 10 = 'A' → 2010 in current cycle
    assert decode_model_year("JN1TANN40A0000001") == 2010
    assert decode_model_year("JN1TANN4050000001") == 2005


def test_decode_navara_prefix() -> None:
    decoded = decode_vin_local("MNTCCND40A1234567")
    assert decoded.chassis_code == "D40"
    assert decoded.vin_prefix is not None
    assert decoded.production_year == 2010
    assert "Navara" in (decoded.model_variant or "")


def test_chassis_from_fixture_prefix() -> None:
    assert chassis_from_prefix("MNTCCND40") == "D40"


def test_decode_from_chassis_year_range() -> None:
    rows = decode_from_chassis("D40", engine_code="YD25", year_start=2010, year_end=2012)
    years = {r.production_year for r in rows}
    assert years == {2010, 2011, 2012}
    assert all(r.vin_prefix for r in rows)


def test_enrich_hints_links_vin_prefix() -> None:
    hints = enrich_hints_with_vin(
        {"chassis_code": "D40", "engine_code": "YD25", "model_variant": "Navara"}
    )
    assert hints["vin_prefix"] == "MNTCCND40"
    assert hints.get("production_year") is not None


@pytest.mark.parametrize(
    "chassis,expected_prefix_substr",
    [
        ("B13", "JN1EB31S"),
        ("K13", "JN1CANK13"),
        ("S14", "JN1AS4CU"),
        ("Z33", "JN1AZ34D"),
        ("K12", "JN1CANK12"),
    ],
)
def test_new_chassis_decode_non_null_prefix(chassis: str, expected_prefix_substr: str) -> None:
    rows = decode_from_chassis(chassis)
    assert rows
    assert all(r.vin_prefix for r in rows)
    assert rows[0].vin_prefix == expected_prefix_substr
    hints = enrich_hints_with_vin({"chassis_code": chassis})
    assert hints.get("vin_prefix") == expected_prefix_substr


def test_chassis_from_new_market_prefixes() -> None:
    assert chassis_from_prefix("JN1EB31S") == "B13"
    assert chassis_from_prefix("3N1CK3CP") == "K13"
    assert chassis_from_prefix("JN1AS4CU") == "S14"
    assert chassis_from_prefix("JN1AZ34E") == "Z33"


def test_resolve_vin_to_vehicle_hints_z33() -> None:
    hints = resolve_vin_to_vehicle_hints("JN1AZ34E03T000894")
    assert hints.get("chassis_code") == "Z33"
    assert hints.get("vin_prefix")
    assert hints.get("production_year") == 2003
