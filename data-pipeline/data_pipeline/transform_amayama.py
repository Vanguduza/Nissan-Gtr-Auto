""" Backward-compatible re-exports — implementation lives in amayama_catalog_auto. """

from data_pipeline.amayama_catalog_auto import (  # noqa: F401
    extract_bbox,
    is_catalog_payload,
    merge_bundles,
    normalize_oem,
    normalize_pnc,
    transform_payload,
    transform_raw_records,
    vehicle_context_from_url,
    write_bundle,
)
