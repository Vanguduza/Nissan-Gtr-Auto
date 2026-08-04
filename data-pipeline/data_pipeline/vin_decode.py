""" Backward-compatible re-exports — implementation lives in amayama_catalog_auto. """

from __future__ import annotations

from typing import Any

from data_pipeline.amayama_catalog_auto import (  # noqa: F401
    CHASSIS_CATALOG,
    VinDecode,
    chassis_from_prefix,
    decode_from_chassis,
    decode_model_year,
    decode_vin,
    decode_vin_local,
    enrich_hints_with_vin,
    enrich_nhtsa,
    lookup_chassis,
    normalize_vin,
    vin_prefix_of,
)


def resolve_vin_to_vehicle_hints(vin_or_prefix: str) -> dict[str, Any]:
    """Thin VIN→identity hints for import/search when stored vin_prefix rows are sparse.

    Prefers local chassis catalog decode (no network). Use this when a user VIN
    query needs chassis/model/year before matching vehicle_master / vehicle_identity.
    """
    return decode_vin_local(vin_or_prefix).as_hints()
