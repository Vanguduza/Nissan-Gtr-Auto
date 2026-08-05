"""Unit tests for Phase C forecast scaffold (no heavy deps required)."""

from data_pipeline.forecast_statsforecast import (
    build_stub_reason,
    enrich_reason_for_oem,
    try_statsforecast_available,
)


def test_build_stub_reason_shape():
    reason = build_stub_reason("15208-65F0C", horizon_days=14)
    assert reason["phase_c"] is True
    assert reason["model"]["source"] == "stub"
    assert reason["model"]["oem_part_number"] == "15208-65F0C"
    assert reason["model"]["horizon_days"] == 14
    assert reason["model"]["point_forecast"] is None


def test_enrich_merges_existing():
    out = enrich_reason_for_oem("OEM-1", existing={"tier": "A"})
    assert out["tier"] == "A"
    assert out["phase_c"] is True


def test_statsforecast_probe_is_bool():
    assert isinstance(try_statsforecast_available(), bool)
