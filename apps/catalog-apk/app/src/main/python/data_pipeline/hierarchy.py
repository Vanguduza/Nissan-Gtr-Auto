"""Backward-compatible re-exports — implementation lives in amayama_catalog_auto. """

from data_pipeline.amayama_catalog_auto import (  # noqa: F401
    ENGINE_RE,
    HierarchyLevel,
    HierarchyNode,
    classify_url,
    extract_page_vehicle_meta,
    parse_year_range,
    should_enqueue_child,
)
