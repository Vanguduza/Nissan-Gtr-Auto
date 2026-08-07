"""Build Meilisearch catalog documents from Supabase catalog rows."""

from __future__ import annotations

from typing import Any


def _slug_id(prefix: str, *parts: Any) -> str:
    """Meili primaryKey: alphanumeric, hyphen, underscore only (no ':')."""

    def _seg(p: Any) -> str:
        s = str(p or "").strip()
        return "".join(c if c.isalnum() or c in "-_" else "_" for c in s)

    raw = "_".join(s for s in (_seg(p) for p in parts) if s)
    return f"{prefix}_{raw}" if raw else prefix


def build_part_documents(
    part_fitment: list[dict[str, Any]],
    pnc_by_code: dict[str, dict[str, Any]],
    stock_by_oem: dict[str, dict[str, Any]],
    oe_by_oem: dict[str, list[str]],
) -> list[dict[str, Any]]:
    """One Meili document per distinct OEM part number."""
    by_oem: dict[str, dict[str, Any]] = {}

    for row in part_fitment:
        oem = (row.get("oem_part_number") or "").strip()
        if not oem:
            continue
        pnc = pnc_by_code.get(row.get("pnc_code") or "", {})
        stock = stock_by_oem.get(oem.upper(), {})
        description = (stock.get("description") or "").strip()
        category = pnc.get("category_name")
        subcategory = pnc.get("subcategory_name")
        chassis = row.get("chassis_code")
        engine = row.get("engine_code")

        existing = by_oem.get(oem)
        if existing is None:
            by_oem[oem] = {
                "id": _slug_id("part", oem),
                "doc_kind": "part",
                "type": "part",
                "oem_part_number": oem,
                "description": description or None,
                "pnc_code": row.get("pnc_code"),
                "category_name": category,
                "subcategory_name": subcategory,
                "chassis_code": chassis,
                "engine_code": engine,
                "superseded_by": row.get("superseded_by"),
                "diagram_path": row.get("diagram_path"),
                "oe_numbers": list(oe_by_oem.get(oem.upper(), [])),
                "chassis_codes": [c for c in [chassis] if c],
                "engine_codes": [e for e in [engine] if e],
            }
        else:
            if chassis and chassis not in existing["chassis_codes"]:
                existing["chassis_codes"].append(chassis)
            if engine and engine not in existing["engine_codes"]:
                existing["engine_codes"].append(engine)
            if not existing.get("diagram_path") and row.get("diagram_path"):
                existing["diagram_path"] = row.get("diagram_path")
            if not existing.get("superseded_by") and row.get("superseded_by"):
                existing["superseded_by"] = row.get("superseded_by")

    docs: list[dict[str, Any]] = []
    for doc in by_oem.values():
        doc["search_blob"] = " ".join(
            filter(
                None,
                [
                    doc.get("oem_part_number"),
                    doc.get("description"),
                    doc.get("pnc_code"),
                    doc.get("category_name"),
                    doc.get("subcategory_name"),
                    doc.get("superseded_by"),
                    " ".join(doc.get("oe_numbers") or []),
                    " ".join(doc.get("chassis_codes") or []),
                    " ".join(doc.get("engine_codes") or []),
                ],
            )
        )
        docs.append(doc)
    return docs


def build_vehicle_documents(vehicle_master: list[dict[str, Any]]) -> list[dict[str, Any]]:
    docs: list[dict[str, Any]] = []
    for row in vehicle_master:
        vin = row.get("vin_prefix")
        chassis = row.get("chassis_code")
        engine = row.get("engine_code")
        model = row.get("model_variant")
        year = row.get("production_year")
        doc_id = _slug_id("vehicle", vin, chassis, engine, year, model)
        docs.append(
            {
                "id": doc_id,
                "doc_kind": "vehicle",
                "type": "vehicle",
                "vin_prefix": vin,
                "model_variant": model,
                "chassis_code": chassis,
                "engine_code": engine,
                "production_year": year,
                "search_blob": " ".join(
                    filter(None, [vin, model, chassis, engine, str(year) if year else None])
                ),
            }
        )
    return docs


def build_pnc_documents(
    pnc_categories: list[dict[str, Any]],
    fitment_count_by_pnc: dict[str, int] | None = None,
) -> list[dict[str, Any]]:
    counts = fitment_count_by_pnc or {}
    docs: list[dict[str, Any]] = []
    for row in pnc_categories:
        code = row.get("pnc_code")
        if not code:
            continue
        category = row.get("category_name")
        sub = row.get("subcategory_name")
        docs.append(
            {
                "id": _slug_id("pnc", code),
                "doc_kind": "pnc",
                "type": "pnc",
                "pnc_code": code,
                "category_name": category,
                "subcategory_name": sub,
                "fitment_count": counts.get(code, 0),
                "search_blob": " ".join(filter(None, [code, category, sub])),
            }
        )
    return docs


def build_catalog_documents(
    *,
    vehicle_master: list[dict[str, Any]],
    pnc_categories: list[dict[str, Any]],
    part_fitment: list[dict[str, Any]],
    stock_items: list[dict[str, Any]],
    oe_cross_refs: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    pnc_by_code = {p["pnc_code"]: p for p in pnc_categories if p.get("pnc_code")}
    stock_by_oem = {
        (s.get("oem_part_number") or "").upper(): s
        for s in stock_items
        if s.get("oem_part_number")
    }
    oe_by_oem: dict[str, list[str]] = {}
    for xref in oe_cross_refs:
        oem = (xref.get("oem_part_number") or "").upper()
        oe = xref.get("oe_number")
        if oem and oe:
            oe_by_oem.setdefault(oem, []).append(oe)

    fitment_count_by_pnc: dict[str, int] = {}
    for row in part_fitment:
        code = row.get("pnc_code")
        if code:
            fitment_count_by_pnc[code] = fitment_count_by_pnc.get(code, 0) + 1

    return (
        build_part_documents(part_fitment, pnc_by_code, stock_by_oem, oe_by_oem)
        + build_vehicle_documents(vehicle_master)
        + build_pnc_documents(pnc_categories, fitment_count_by_pnc)
    )
