"""Priority chassis filter for PartSouq catalog crawl queue claiming.

When ``--priority-chassis-file`` (or orchestrator equivalent) is set, ``claim_next_url``
only selects PENDING rows that belong to the priority chassis set until those vehicles
are comprehensively crawled. Non-priority URLs remain PENDING in ``crawler_state.db`` —
restart without the flag to resume the full crawl.

Config: ``data-pipeline/config/priority_chassis.json``
"""

from __future__ import annotations

import json
import logging
import re
import sqlite3
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from data_pipeline.parse_partsouq_html import normalize_chassis_code, vid_from_url

logger = logging.getLogger(__name__)

PACKAGE_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PRIORITY_FILE = PACKAGE_ROOT / "config" / "priority_chassis.json"

# Hierarchy levels at or below MODEL — needed to reach /vehicle discovery pages.
_BOOTSTRAP_MAX_LEVEL = 1

_VEHICLE_PATH_MARKERS = ("/vehicle", "/modification", "/modifications")


@dataclass(frozen=True)
class PriorityChassisEntry:
    code: str
    canonical: str
    aliases: tuple[str, ...] = ()
    model_variant: str | None = None
    curated: bool = False
    notes: str | None = None

    def all_tokens(self) -> frozenset[str]:
        tokens = {self.code.upper(), self.canonical.upper()}
        tokens.update(a.upper() for a in self.aliases if a)
        return frozenset(tokens)


@dataclass
class PriorityChassisFilter:
    """Runtime filter passed to ``claim_next_url``."""

    entries: dict[str, PriorityChassisEntry] = field(default_factory=dict)
    tokens: frozenset[str] = frozenset()
    vid_chassis: dict[str, str] = field(default_factory=dict)
    strict: bool = True

    @classmethod
    def from_codes(
        cls,
        codes: list[str],
        *,
        vid_chassis: dict[str, str] | None = None,
        strict: bool = True,
    ) -> PriorityChassisFilter:
        entries: dict[str, PriorityChassisEntry] = {}
        all_tokens: set[str] = set()
        for raw in codes:
            code = raw.strip().upper()
            if not code:
                continue
            canonical = normalize_chassis_code(code) or code
            entry = PriorityChassisEntry(code=code, canonical=canonical, aliases=(code,))
            entries[canonical] = entry
            all_tokens.update(entry.all_tokens())
        return cls(entries=entries, tokens=frozenset(all_tokens), vid_chassis=vid_chassis or {}, strict=strict)

    def chassis_in_priority(self, chassis: str | None) -> bool | None:
        """Return True/False if known, None if chassis absent/unknown."""
        if not chassis or not str(chassis).strip():
            return None
        norm = normalize_chassis_code(str(chassis)) or str(chassis).upper()
        raw = str(chassis).upper()
        if norm in self.tokens or raw in self.tokens:
            return True
        for entry in self.entries.values():
            if norm == entry.canonical or raw in entry.all_tokens():
                return True
        return False

    def _chassis_from_vid(self, vid: str | None) -> str | None:
        if not vid:
            return None
        mapped = self.vid_chassis.get(str(vid))
        return mapped.upper() if mapped else None

    def _url_contains_priority_token(self, url: str) -> bool:
        upper = (url or "").upper()
        for token in self.tokens:
            if len(token) >= 3 and token in upper:
                return True
        return False

    def is_bootstrap_url(self, url: str, hierarchy_level: int) -> bool:
        """Locate/filter/model pages required before chassis is known."""
        if hierarchy_level <= _BOOTSTRAP_MAX_LEVEL:
            return True
        path = urlparse(url).path.lower()
        return any(marker in path for marker in _VEHICLE_PATH_MARKERS)

    def is_eligible(
        self,
        url: str,
        vehicle_context: dict[str, Any] | None,
        *,
        hierarchy_level: int = 99,
    ) -> bool:
        ctx = vehicle_context or {}
        chassis = ctx.get("chassis_code")
        known = self.chassis_in_priority(str(chassis) if chassis else None)

        if known is True:
            return True
        if known is False:
            return False

        vid = ctx.get("vid") or vid_from_url(url)
        vid_chassis = self._chassis_from_vid(str(vid) if vid else None)
        if vid_chassis:
            vid_known = self.chassis_in_priority(vid_chassis)
            if vid_known is True:
                return True
            if vid_known is False:
                return False

        if self.is_bootstrap_url(url, hierarchy_level):
            return True

        if self._url_contains_priority_token(url):
            return True

        return not self.strict

    @property
    def codes(self) -> list[str]:
        return sorted({e.canonical for e in self.entries.values()})


def _parse_vehicle_context(raw: str | None) -> dict[str, Any]:
    if not raw:
        return {}
    try:
        loaded = json.loads(raw)
        return loaded if isinstance(loaded, dict) else {}
    except json.JSONDecodeError:
        return {}


def load_priority_chassis_file(path: Path | str) -> dict[str, Any]:
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f"Priority chassis file not found: {p}")
    data = json.loads(p.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"Priority chassis file must be a JSON object: {p}")
    return data


def build_priority_filter(
    path: Path | str | None = None,
    *,
    extra_codes: list[str] | None = None,
    vid_chassis: dict[str, str] | None = None,
    strict: bool = True,
    catalogs_path: Path | str | None = None,
) -> PriorityChassisFilter | None:
    """Load priority chassis JSON and merge optional CLI overrides."""
    if path is None and not extra_codes:
        return None

    entries: dict[str, PriorityChassisEntry] = {}
    all_tokens: set[str] = set()

    if path is not None:
        data = load_priority_chassis_file(path)
        chassis_block = data.get("chassis") or {}
        if isinstance(chassis_block, dict):
            for raw_code, meta in chassis_block.items():
                if not isinstance(meta, dict):
                    meta = {}
                code = str(raw_code).strip().upper()
                canonical = normalize_chassis_code(
                    str(meta.get("canonical") or code)
                ) or code
                aliases_raw = meta.get("aliases") or [code]
                aliases = tuple(str(a).strip().upper() for a in aliases_raw if str(a).strip())
                entry = PriorityChassisEntry(
                    code=code,
                    canonical=canonical,
                    aliases=aliases,
                    model_variant=meta.get("model_variant"),
                    curated=bool(meta.get("curated")),
                    notes=meta.get("notes"),
                )
                entries[canonical] = entry
                all_tokens.update(entry.all_tokens())
        for code in data.get("chassis_codes") or []:
            c = str(code).strip().upper()
            if not c:
                continue
            canonical = normalize_chassis_code(c) or c
            if canonical not in entries:
                entries[canonical] = PriorityChassisEntry(code=c, canonical=canonical, aliases=(c,))
            all_tokens.update(entries[canonical].all_tokens())

    for raw in extra_codes or []:
        c = str(raw).strip().upper()
        if not c:
            continue
        canonical = normalize_chassis_code(c) or c
        if canonical not in entries:
            entries[canonical] = PriorityChassisEntry(code=c, canonical=canonical, aliases=(c,))
        all_tokens.update(entries[canonical].all_tokens())

    if not entries:
        return None

    merged_vid = dict(vid_chassis or {})
    return PriorityChassisFilter(
        entries=entries,
        tokens=frozenset(all_tokens),
        vid_chassis=merged_vid,
        strict=strict,
    )


def resolve_parse_db(*, state_db: Path, explicit: Path | None = None) -> Path | None:
    """Locate parse DB for vid→chassis priority matching (common layout fallbacks)."""
    if explicit is not None:
        p = explicit
        return p if p.exists() else None
    root = state_db.parent
    for candidate in (
        root / "cache_parse_state.db",
        root / "out" / "cache_parse_state.db",
    ):
        if candidate.exists():
            return candidate
    return None


def load_vid_chassis_map(parse_db: Path | str | None) -> dict[str, str]:
    """vid → normalized chassis_code from ``vehicle_identity``."""
    if not parse_db:
        return {}
    db = Path(parse_db)
    if not db.exists():
        return {}
    conn = sqlite3.connect(db, timeout=30.0)
    try:
        rows = conn.execute(
            "SELECT vid, chassis_code FROM vehicle_identity WHERE chassis_code IS NOT NULL"
        ).fetchall()
        out: dict[str, str] = {}
        for vid, chassis in rows:
            if vid and chassis:
                norm = normalize_chassis_code(str(chassis)) or str(chassis).upper()
                out[str(vid)] = norm
        return out
    except sqlite3.Error as exc:
        logger.warning("Could not load vid→chassis map from %s: %s", db, exc)
        return {}
    finally:
        conn.close()


def merge_catalog_metadata(
    priority_data: dict[str, Any],
    *,
    maker: str = "nissan",
    catalogs_path: Path | str | None = None,
) -> dict[str, Any]:
    """Enrich priority chassis entries from ``chassis_catalogs.json`` when present."""
    from data_pipeline.chassis_catalog_registry import load_catalogs, normalize_brand

    cat_path = Path(catalogs_path) if catalogs_path else PACKAGE_ROOT / "config" / "chassis_catalogs.json"
    catalogs = load_catalogs(path=cat_path)
    brand = normalize_brand(priority_data.get("maker") or maker)
    curated = (catalogs.get(brand) or {}).get("chassis") or {}

    chassis_block = priority_data.setdefault("chassis", {})
    if not isinstance(chassis_block, dict):
        chassis_block = {}
        priority_data["chassis"] = chassis_block

    for code, entry in list(chassis_block.items()):
        if not isinstance(entry, dict):
            entry = {}
            chassis_block[code] = entry
        canonical = normalize_chassis_code(str(entry.get("canonical") or code)) or str(code).upper()
        row = curated.get(canonical) or curated.get(str(code).upper())
        if row:
            entry.setdefault("model_variant", row.get("model_variant"))
            entry.setdefault("engines", row.get("engines"))
            entry.setdefault("year_range", row.get("year_range"))
            entry.setdefault("vin_prefixes", row.get("vin_prefixes"))
            entry["curated"] = True
    return priority_data


def count_eligible_pending(
    db_path: Path | str,
    priority: PriorityChassisFilter,
    *,
    sample_limit: int = 2000,
) -> tuple[int, int]:
    """Return (eligible_pending, total_pending) for queue status logging."""
    conn = sqlite3.connect(str(db_path), timeout=30.0)
    try:
        rows = conn.execute(
            """
            SELECT url, vehicle_context, hierarchy_level
            FROM queue
            WHERE status = 'PENDING'
            LIMIT ?
            """,
            (sample_limit,),
        ).fetchall()
        total = conn.execute(
            "SELECT COUNT(*) FROM queue WHERE status = 'PENDING'"
        ).fetchone()[0]
        eligible = 0
        for url, ctx_raw, level in rows:
            ctx = _parse_vehicle_context(ctx_raw)
            if priority.is_eligible(url, ctx, hierarchy_level=int(level)):
                eligible += 1
        return eligible, int(total)
    finally:
        conn.close()


def _priority_code_for_identity(
    priority: PriorityChassisFilter,
    vid: str,
    chassis: str | None,
) -> str | None:
    """Map a vehicle_identity row to a canonical priority chassis code."""
    mapped = priority.vid_chassis.get(vid)
    if mapped:
        norm = normalize_chassis_code(mapped) or mapped.upper()
        if norm in priority.codes:
            return norm
    if not chassis:
        return None
    norm = normalize_chassis_code(str(chassis)) or str(chassis).upper()
    if norm in priority.codes:
        return norm
    if priority.chassis_in_priority(str(chassis)):
        for code in priority.codes:
            entry = priority.entries.get(code)
            if entry and (norm == entry.canonical or norm in entry.all_tokens()):
                return code
    return None


def chassis_coverage_report(
    *,
    parse_db: Path | str | None,
    bundle_dir: Path | str | None,
    priority: PriorityChassisFilter,
) -> dict[str, dict[str, Any]]:
    """Summarize identity-only vs fitment coverage per priority chassis."""
    report: dict[str, dict[str, Any]] = {
        code: {"identity_vids": 0, "fitment_rows": 0, "status": "none"}
        for code in priority.codes
    }

    if parse_db and Path(parse_db).exists():
        conn = sqlite3.connect(str(parse_db), timeout=30.0)
        try:
            by_code: dict[str, set[str]] = {code: set() for code in priority.codes}
            rows = conn.execute(
                "SELECT vid, chassis_code FROM vehicle_identity WHERE vid IS NOT NULL"
            ).fetchall()
            for vid, chassis in rows:
                code = _priority_code_for_identity(priority, str(vid), chassis)
                if code:
                    by_code[code].add(str(vid))
            for code in priority.codes:
                report[code]["identity_vids"] = len(by_code[code])
        finally:
            conn.close()

    fitment_path = Path(bundle_dir) / "part_fitment.json" if bundle_dir else None
    if fitment_path and fitment_path.exists():
        try:
            rows = json.loads(fitment_path.read_text(encoding="utf-8"))
            if isinstance(rows, list):
                by_chassis: dict[str, int] = {}
                for row in rows:
                    if not isinstance(row, dict):
                        continue
                    ch = row.get("chassis_code")
                    if not ch:
                        continue
                    norm = normalize_chassis_code(str(ch)) or str(ch).upper()
                    by_chassis[norm] = by_chassis.get(norm, 0) + 1
                for code in report:
                    report[code]["fitment_rows"] = by_chassis.get(code, 0)
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning("Could not read fitments from %s: %s", fitment_path, exc)

    for code, row in report.items():
        if row["fitment_rows"] > 0:
            row["status"] = "fitments"
        elif row["identity_vids"] > 0:
            row["status"] = "identity_only"
        else:
            row["status"] = "none"
    return report
