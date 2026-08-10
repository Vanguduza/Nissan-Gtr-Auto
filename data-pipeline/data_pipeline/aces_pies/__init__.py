"""Thin Auto Care ACES / PIES adapters — enrichment only (GTR SoR stays Supabase/EPC).

Does not vendor SandPIM PHP. Real VCdb/PCdb reference data requires an Auto Care
Association subscription; fixtures use synthetic PartTerminologyIDs for tests.
"""

from data_pipeline.aces_pies.aces import AcesApp, parse_aces_xml
from data_pipeline.aces_pies.enrich import (
    EnrichmentResult,
    apply_pies_to_bundle,
    merge_pies_into_epc_mapping,
    stock_rows_from_pies,
)
from data_pipeline.aces_pies.pies import PiesItem, parse_pies_xml

__all__ = [
    "AcesApp",
    "EnrichmentResult",
    "PiesItem",
    "apply_pies_to_bundle",
    "merge_pies_into_epc_mapping",
    "parse_aces_xml",
    "parse_pies_xml",
    "stock_rows_from_pies",
]
