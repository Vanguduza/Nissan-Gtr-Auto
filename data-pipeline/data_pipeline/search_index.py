"""Fixture search index — mirrors PG FTS search_catalog contract for offline tests."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal

SearchMode = Literal["part", "vin", "model", "pnc"]


@dataclass
class CatalogIndex:
    vehicle_master: list[dict[str, Any]]
    pnc_categories: list[dict[str, Any]]
    part_fitment: list[dict[str, Any]]
    oe_cross_refs: list[dict[str, Any]]

    @classmethod
    def from_bundle(
        cls,
        bundle: dict[str, list[dict[str, Any]]],
        oe_cross_refs: list[dict[str, Any]] | None = None,
    ) -> CatalogIndex:
        return cls(
            vehicle_master=bundle.get("vehicle_master", []),
            pnc_categories=bundle.get("pnc_categories", []),
            part_fitment=bundle.get("part_fitment", []),
            oe_cross_refs=oe_cross_refs or [],
        )

    def search(self, mode: SearchMode, query: str) -> list[dict[str, Any]]:
        q = query.strip()
        if not q:
            return []

        if mode == "part":
            return self._search_part(q)
        if mode == "vin":
            return self._search_vin(q)
        if mode == "model":
            return self._search_model(q)
        if mode == "pnc":
            return self._search_pnc(q)
        raise ValueError(f"Unknown mode: {mode}")

    def _enrich_fitment(self, row: dict[str, Any]) -> dict[str, Any]:
        pnc = next(
            (p for p in self.pnc_categories if p["pnc_code"] == row.get("pnc_code")),
            None,
        )
        return {
            "type": "part",
            "oem_part_number": row["oem_part_number"],
            "pnc_code": row.get("pnc_code"),
            "chassis_code": row.get("chassis_code"),
            "engine_code": row.get("engine_code"),
            "superseded_by": row.get("superseded_by"),
            "category_name": pnc["category_name"] if pnc else None,
            "subcategory_name": (pnc or {}).get("subcategory_name"),
            "diagram_path": row.get("diagram_path"),
        }

    def _search_part(self, query: str) -> list[dict[str, Any]]:
        q_upper = query.upper()
        hits: dict[str, dict[str, Any]] = {}

        for row in self.part_fitment:
            oem = row["oem_part_number"].upper()
            if q_upper in oem or oem == q_upper:
                hits[oem] = self._enrich_fitment(row)

        for row in self.part_fitment:
            superseded = (row.get("superseded_by") or "").upper()
            if superseded and (q_upper in superseded or superseded == q_upper):
                hits[row["oem_part_number"].upper()] = self._enrich_fitment(row)

        for xref in self.oe_cross_refs:
            oe = xref["oe_number"].upper()
            if q_upper in oe or oe == q_upper:
                oem = xref["oem_part_number"].upper()
                fitment = next(
                    (f for f in self.part_fitment if f["oem_part_number"].upper() == oem),
                    None,
                )
                if fitment:
                    hit = self._enrich_fitment(fitment)
                    hit["matched_oe_number"] = xref["oe_number"]
                    hit["matched_brand"] = xref.get("brand")
                    hits[oem] = hit

        return list(hits.values())

    def _search_vin(self, query: str) -> list[dict[str, Any]]:
        prefix = query[:11].upper()
        vehicles = [
            v
            for v in self.vehicle_master
            if (v.get("vin_prefix") or "").upper().startswith(prefix)
            or prefix.startswith((v.get("vin_prefix") or "").upper())
        ]
        results: list[dict[str, Any]] = []
        for vehicle in vehicles:
            chassis = vehicle.get("chassis_code")
            engine = vehicle.get("engine_code")
            fitments = [
                self._enrich_fitment(f)
                for f in self.part_fitment
                if f.get("chassis_code") == chassis
                and (not engine or f.get("engine_code") == engine)
            ]
            results.append(
                {
                    "type": "vehicle",
                    "vin_prefix": vehicle.get("vin_prefix"),
                    "model_variant": vehicle["model_variant"],
                    "chassis_code": chassis,
                    "engine_code": engine,
                    "production_year": vehicle.get("production_year"),
                    "fitments": fitments,
                }
            )
        return results

    def _search_model(self, query: str) -> list[dict[str, Any]]:
        q_lower = query.lower()
        results: list[dict[str, Any]] = []
        for vehicle in self.vehicle_master:
            haystack = " ".join(
                str(vehicle.get(k, "") or "")
                for k in ("model_variant", "chassis_code", "engine_code")
            ).lower()
            if q_lower in haystack:
                results.append(
                    {
                        "type": "vehicle",
                        "vin_prefix": vehicle.get("vin_prefix"),
                        "model_variant": vehicle["model_variant"],
                        "chassis_code": vehicle.get("chassis_code"),
                        "engine_code": vehicle.get("engine_code"),
                        "production_year": vehicle.get("production_year"),
                    }
                )
        return results

    def _search_pnc(self, query: str) -> list[dict[str, Any]]:
        q_lower = query.lower()
        results: list[dict[str, Any]] = []
        for pnc in self.pnc_categories:
            haystack = " ".join(
                [
                    pnc["pnc_code"],
                    pnc["category_name"],
                    pnc.get("subcategory_name") or "",
                ]
            ).lower()
            if q_lower in haystack or query == pnc["pnc_code"]:
                fitments = [
                    self._enrich_fitment(f)
                    for f in self.part_fitment
                    if f.get("pnc_code") == pnc["pnc_code"]
                ]
                results.append(
                    {
                        "type": "pnc",
                        "pnc_code": pnc["pnc_code"],
                        "category_name": pnc["category_name"],
                        "subcategory_name": pnc.get("subcategory_name"),
                        "fitments": fitments,
                    }
                )
        return results
