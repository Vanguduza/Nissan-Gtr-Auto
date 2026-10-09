"""Brand-scoped chassis → VIN-prefix catalogs for multi-make PartSouq enrichment.

Lookup is ``(brand, chassis)``. Curated ``vin_prefixes`` win when present.
Otherwise, if the brand enables ``epc_stub``, synthesize a garage search key
``{primary_wmi}{chassis}`` (same pattern as Nissan EPC stubs like JN1CANK13) —
not an ISO VIN, never a fabricated full VIN.

Config: ``data-pipeline/config/chassis_catalogs.json`` (override via
``chassis_catalogs_path`` / env). Active brand is set from ``allowed_brand``.
"""

from __future__ import annotations

import json
import logging
import re
from contextvars import ContextVar
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

PACKAGE_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CATALOGS_PATH = PACKAGE_ROOT / "config" / "chassis_catalogs.json"

_active_brand: ContextVar[str] = ContextVar("chassis_active_brand", default="nissan")
_catalogs: dict[str, dict[str, Any]] | None = None
_prefix_index: list[tuple[str, str, str]] = []  # (prefix, chassis, brand)


def normalize_brand(brand: str | None) -> str:
    text = (brand or "nissan").strip().lower()
    text = re.sub(r"[^\w\s-]", "", text, flags=re.UNICODE)
    text = re.sub(r"[\s_]+", "-", text).strip("-")
    return text or "nissan"


def set_active_brand(brand: str | None) -> str:
    """Set brand for subsequent chassis lookups (crawl/parse process)."""
    slug = normalize_brand(brand)
    _active_brand.set(slug)
    return slug


def get_active_brand() -> str:
    return _active_brand.get()


def catalogs_path(override: Path | str | None = None) -> Path:
    if override:
        return Path(override)
    return DEFAULT_CATALOGS_PATH


def _builtin_nissan_chassis() -> dict[str, dict[str, Any]]:
    """Fallback if JSON missing — mirrors historical CHASSIS_CATALOG."""
    return {
        "D40": {
            "model_variant": "Nissan Navara D40",
            "vin_prefixes": ["MNTCCND40", "VSKCVND40", "JN1TANN40"],
            "engines": ["YD25", "YN25", "VQ40DE", "QR25DE"],
            "year_range": [2005, 2015],
        },
        "D22": {
            "model_variant": "Nissan Navara / Hardbody D22",
            "vin_prefixes": ["JN1TANN22", "ADNTANN22"],
            "engines": ["YD25", "KA24DE", "YD25DDTi"],
            "year_range": [1997, 2005],
        },
        "D23": {
            "model_variant": "Nissan Navara NP300 D23",
            "vin_prefixes": ["VSKCVAD23", "MNTCBAD23"],
            "engines": ["YS23DDT", "QR25DE"],
            "year_range": [2015, 2024],
        },
        "T31": {
            "model_variant": "Nissan X-Trail T31",
            "vin_prefixes": ["JN1TANT31", "JN1TBNT31"],
            "engines": ["MR20DE", "QR25DE", "M9R"],
            "year_range": [2007, 2014],
        },
        "T32": {
            "model_variant": "Nissan X-Trail T32",
            "vin_prefixes": ["JN1TANT32"],
            "engines": ["MR20DD", "QR25DE", "R9M"],
            "year_range": [2014, 2021],
        },
        "R35": {
            "model_variant": "Nissan GT-R R35",
            "vin_prefixes": ["JN1AR5EF"],
            "engines": ["VR38DETT"],
            "year_range": [2007, 2024],
        },
        "J10": {
            "model_variant": "Nissan Qashqai J10",
            "vin_prefixes": ["SJNFBAJ10", "MDHFBAJ10"],
            "engines": ["MR20DE", "HR16DE", "K9K"],
            "year_range": [2007, 2013],
        },
        "JJ10": {
            "model_variant": "Nissan Qashqai+2 JJ10",
            "vin_prefixes": ["SJNFBAJ10", "MDHFBAJ10"],
            "engines": ["MR20DE", "HR16DE", "K9K"],
            "year_range": [2008, 2013],
        },
        "J11": {
            "model_variant": "Nissan Qashqai J11",
            "vin_prefixes": ["SJNFBAJ11"],
            "engines": ["MR20DD", "HR16DE", "K9K"],
            "year_range": [2014, 2021],
        },
        "K12": {
            "model_variant": "Nissan Micra K12",
            "vin_prefixes": ["JN1CANK12"],
            "engines": ["CR14DE", "CG12DE"],
            "year_range": [2002, 2010],
        },
        "K13": {
            "model_variant": "Nissan Micra K13",
            "vin_prefixes": ["JN1CANK13", "3N1CK3CP", "VSKKBAK13"],
            "engines": ["HR12DE", "HR12DDR", "K9K", "HRA0"],
            "year_range": [2010, 2017],
        },
        "B13": {
            "model_variant": "Nissan Sunny / Sentra / NX B13",
            "vin_prefixes": ["JN1EB31S", "JN1HB31P", "3N1CB31S"],
            "engines": ["GA16DE", "SR20DE", "GA13DE", "CD17"],
            "year_range": [1990, 1994],
        },
        "S14": {
            "model_variant": "Nissan 200SX / Silvia S14",
            "vin_prefixes": ["JN1AS4CU", "JN1PS4EU"],
            "engines": ["SR20DET", "KA24DE", "SR20DE"],
            "year_range": [1993, 2000],
        },
        "Z33": {
            "model_variant": "Nissan 350Z / Fairlady Z Z33",
            "vin_prefixes": ["JN1AZ34D", "JN1AZ34E", "JN1BZ34D"],
            "engines": ["VQ35DE", "VQ35HR"],
            "year_range": [2002, 2009],
        },
        "Y61": {
            "model_variant": "Nissan Patrol Y61",
            "vin_prefixes": ["JN1TANY61", "JN1TEBY61"],
            "engines": ["ZD30DDTi", "TB48DE", "RD28ETi"],
            "year_range": [1997, 2016],
        },
        "Y62": {
            "model_variant": "Nissan Patrol Y62",
            "vin_prefixes": ["JN1TANY62"],
            "engines": ["VK56VD"],
            "year_range": [2010, 2024],
        },
    }


def _default_maker_shell(slug: str, display: str, wmi: str, extra_wmis: list[str] | None = None) -> dict[str, Any]:
    wmis = [wmi] + [w for w in (extra_wmis or []) if w and w != wmi]
    return {
        "display_name": display,
        "wmi_prefixes": wmis,
        "epc_stub": {"enabled": True, "primary_wmi": wmi, "pattern": "{wmi}{chassis}"},
        "chassis": {},
    }


def default_catalogs_document() -> dict[str, Any]:
    """Full multi-make document used when JSON is absent or to seed a new file."""
    makers: dict[str, dict[str, Any]] = {
        "nissan": {
            "display_name": "Nissan",
            "wmi_prefixes": ["JN1", "SJN", "MDH", "VSK", "MNT", "3N1", "ADN"],
            "epc_stub": {"enabled": True, "primary_wmi": "JN1", "pattern": "{wmi}{chassis}"},
            "chassis": _builtin_nissan_chassis(),
        },
        "infiniti": _default_maker_shell("infiniti", "Infiniti", "JN1", ["5N3"]),
        "toyota": _default_maker_shell("toyota", "Toyota", "JTD", ["JT2", "JTE", "JTM", "4T1", "5TD", "2T1", "MR0"]),
        "lexus": _default_maker_shell("lexus", "Lexus", "JTJ", ["JTH", "2T2", "58A"]),
        "honda": _default_maker_shell("honda", "Honda", "JHM", ["JH4", "1HG", "2HG", "3CZ", "SHH"]),
        "acura": _default_maker_shell("acura", "Acura", "JH4", ["19U", "2HN"]),
        "mazda": _default_maker_shell("mazda", "Mazda", "JM1", ["JM3", "1YV", "3MZ"]),
        "mitsubishi": _default_maker_shell("mitsubishi", "Mitsubishi", "JA3", ["JA4", "4A3", "6MM"]),
        "subaru": _default_maker_shell("subaru", "Subaru", "JF1", ["JF2", "4S3", "4S4"]),
        "suzuki": _default_maker_shell("suzuki", "Suzuki", "JS2", ["JS3", "JSA", "TSM"]),
        "daihatsu": _default_maker_shell("daihatsu", "Daihatsu", "JDA", ["JD1"]),
        "isuzu": _default_maker_shell("isuzu", "Isuzu", "JAL", ["4S2"]),
        "hino": _default_maker_shell("hino", "Hino", "JHD", ["JHB"]),
        "hyundai": _default_maker_shell("hyundai", "Hyundai", "KMH", ["KM8", "5NP", "5NM"]),
        "kia": _default_maker_shell("kia", "Kia", "KNA", ["KND", "5XY", "3KP"]),
        "genesis": _default_maker_shell("genesis", "Genesis", "KMT", ["KMH"]),
        "bmw": _default_maker_shell("bmw", "BMW", "WBA", ["WBS", "WBY", "4US", "5UX"]),
        "mini": _default_maker_shell("mini", "Mini", "WMW", ["WMZ"]),
        "mercedes-benz": _default_maker_shell("mercedes-benz", "Mercedes-Benz", "WDD", ["WDB", "4JG", "WDY"]),
        "smart": _default_maker_shell("smart", "Smart", "WME", ["W1K"]),
        "audi": _default_maker_shell("audi", "Audi", "WAU", ["WA1", "TRU"]),
        "volkswagen": _default_maker_shell("volkswagen", "Volkswagen", "WVW", ["WV1", "WV2", "3VW", "1VW"]),
        "skoda": _default_maker_shell("skoda", "Skoda", "TMB", []),
        "seat": _default_maker_shell("seat", "Seat", "VSS", []),
        "porsche": _default_maker_shell("porsche", "Porsche", "WP0", ["WP1"]),
        "ford": _default_maker_shell("ford", "Ford", "1FA", ["1FT", "1FM", "WF0", "6FP"]),
        "lincoln": _default_maker_shell("lincoln", "Lincoln", "5L1", ["5LM", "1LN"]),
        "chevrolet": _default_maker_shell("chevrolet", "Chevrolet", "1G1", ["1GC", "1GN", "3G1"]),
        "cadillac": _default_maker_shell("cadillac", "Cadillac", "1G6", ["1GY"]),
        "buick": _default_maker_shell("buick", "Buick", "1G4", ["2G4"]),
        "gmc": _default_maker_shell("gmc", "GMC", "1GT", ["1GK"]),
        "jeep": _default_maker_shell("jeep", "Jeep", "1C4", ["1J4"]),
        "dodge": _default_maker_shell("dodge", "Dodge", "1B3", ["1D7", "2B3"]),
        "chrysler": _default_maker_shell("chrysler", "Chrysler", "1C3", ["2C3"]),
        "ram": _default_maker_shell("ram", "Ram", "1C6", ["3C6"]),
        "volvo": _default_maker_shell("volvo", "Volvo", "YV1", ["YV4"]),
        "land-rover": _default_maker_shell("land-rover", "Land Rover", "SAL", []),
        "jaguar": _default_maker_shell("jaguar", "Jaguar", "SAJ", []),
        "peugeot": _default_maker_shell("peugeot", "Peugeot", "VF3", []),
        "citroen": _default_maker_shell("citroen", "Citroen", "VF7", []),
        "renault": _default_maker_shell("renault", "Renault", "VF1", []),
        "opel": _default_maker_shell("opel", "Opel", "W0L", []),
        "fiat": _default_maker_shell("fiat", "Fiat", "ZFA", []),
        "alfa-romeo": _default_maker_shell("alfa-romeo", "Alfa Romeo", "ZAR", []),
    }
    # Seed a few high-volume non-Nissan curated platforms (real market stubs).
    makers["toyota"]["chassis"] = {
        "ZVW30": {
            "model_variant": "Toyota Prius ZVW30",
            "vin_prefixes": ["JTDKN3DU", "JTDKN3DE"],
            "engines": ["2ZR-FXE"],
            "year_range": [2009, 2015],
        },
        "NHW20": {
            "model_variant": "Toyota Prius NHW20",
            "vin_prefixes": ["JTDKB20U", "JTDKB22U"],
            "engines": ["1NZ-FXE"],
            "year_range": [2003, 2009],
        },
        "ACV40": {
            "model_variant": "Toyota Camry ACV40",
            "vin_prefixes": ["4T1BE46K", "JTNBE46K"],
            "engines": ["2AZ-FE", "2GR-FE"],
            "year_range": [2006, 2011],
        },
        "GRJ150": {
            "model_variant": "Toyota Land Cruiser Prado GRJ150",
            "vin_prefixes": ["JTEBU5JR", "JTEBH5JR"],
            "engines": ["1GR-FE"],
            "year_range": [2009, 2023],
        },
    }
    makers["honda"]["chassis"] = {
        "FD1": {
            "model_variant": "Honda Civic FD1",
            "vin_prefixes": ["JHMFD16", "SHHFD16"],
            "engines": ["R18A"],
            "year_range": [2005, 2011],
        },
        "RU1": {
            "model_variant": "Honda CR-V RU1",
            "vin_prefixes": ["JHLRW1", "2HKRM4"],
            "engines": ["R20A"],
            "year_range": [2012, 2016],
        },
    }
    makers["bmw"]["chassis"] = {
        "E90": {
            "model_variant": "BMW 3 Series E90",
            "vin_prefixes": ["WBAVB1", "WBAVA1"],
            "engines": ["N52", "N47"],
            "year_range": [2005, 2013],
        },
        "F30": {
            "model_variant": "BMW 3 Series F30",
            "vin_prefixes": ["WBA3A5", "WBA3B1"],
            "engines": ["N20", "N55", "B48"],
            "year_range": [2012, 2019],
        },
    }
    return {
        "version": 1,
        "notes": (
            "Per-maker chassis→vin_prefix maps for PartSouq enrichment. "
            "Curated chassis.vin_prefixes preferred; else epc_stub synthesizes "
            "{primary_wmi}{chassis} as a garage search key (not a full ISO VIN)."
        ),
        "makers": makers,
    }


def ensure_catalogs_file(path: Path | None = None) -> Path:
    """Write default multi-make catalogs JSON if missing."""
    dest = catalogs_path(path)
    if dest.exists():
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(default_catalogs_document(), indent=2) + "\n", encoding="utf-8")
    logger.info("Wrote default chassis catalogs → %s", dest)
    return dest


def load_catalogs(*, path: Path | str | None = None, force: bool = False) -> dict[str, dict[str, Any]]:
    global _catalogs
    if _catalogs is not None and not force:
        return _catalogs

    cfg_path = catalogs_path(path)
    if not cfg_path.exists():
        ensure_catalogs_file(cfg_path)

    try:
        raw = json.loads(cfg_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        logger.warning("Chassis catalogs unreadable (%s); using built-in defaults", exc)
        raw = default_catalogs_document()

    makers_raw = raw.get("makers") if isinstance(raw, dict) else None
    if not isinstance(makers_raw, dict) or not makers_raw:
        makers_raw = default_catalogs_document()["makers"]

    normalized: dict[str, dict[str, Any]] = {}
    for key, meta in makers_raw.items():
        slug = normalize_brand(str(key))
        if not isinstance(meta, dict):
            continue
        entry = dict(meta)
        chassis = entry.get("chassis") or {}
        if not isinstance(chassis, dict):
            chassis = {}
        # Normalize chassis keys + year_range tuples
        clean_chassis: dict[str, dict[str, Any]] = {}
        for code, row in chassis.items():
            if not isinstance(row, dict):
                continue
            item = dict(row)
            prefixes = item.get("vin_prefixes") or []
            item["vin_prefixes"] = [str(p).upper() for p in prefixes if str(p).strip()]
            yr = item.get("year_range")
            if isinstance(yr, list) and len(yr) == 2:
                item["year_range"] = (int(yr[0]), int(yr[1]))
            clean_chassis[str(code).upper()] = item
        entry["chassis"] = clean_chassis
        entry.setdefault("display_name", slug.replace("-", " ").title())
        entry.setdefault("wmi_prefixes", [])
        entry.setdefault(
            "epc_stub",
            {
                "enabled": True,
                "primary_wmi": (entry["wmi_prefixes"][0] if entry["wmi_prefixes"] else "XXX"),
                "pattern": "{wmi}{chassis}",
            },
        )
        normalized[slug] = entry

    # Guarantee nissan exists
    if "nissan" not in normalized:
        normalized["nissan"] = default_catalogs_document()["makers"]["nissan"]

    _catalogs = normalized
    _rebuild_prefix_index()
    return _catalogs


def _rebuild_prefix_index() -> None:
    global _prefix_index
    idx: list[tuple[str, str, str]] = []
    for brand, meta in (_catalogs or {}).items():
        for chassis, row in (meta.get("chassis") or {}).items():
            for pfx in row.get("vin_prefixes") or []:
                idx.append((str(pfx).upper(), chassis, brand))
    idx.sort(key=lambda t: len(t[0]), reverse=True)
    _prefix_index = idx


def list_brands() -> list[str]:
    return sorted(load_catalogs().keys())


def brand_meta(brand: str | None = None) -> dict[str, Any]:
    slug = normalize_brand(brand or get_active_brand())
    catalogs = load_catalogs()
    return catalogs.get(slug) or catalogs.get("nissan") or {}


def display_name(brand: str | None = None) -> str:
    meta = brand_meta(brand)
    return str(meta.get("display_name") or normalize_brand(brand or get_active_brand()).title())


def chassis_map(brand: str | None = None) -> dict[str, dict[str, Any]]:
    return dict(brand_meta(brand).get("chassis") or {})


def lookup_chassis(chassis_code: str, brand: str | None = None) -> dict[str, Any] | None:
    code = (chassis_code or "").strip().upper()
    if not code:
        return None
    return chassis_map(brand).get(code)


def synthesize_epc_stub(chassis_code: str, brand: str | None = None) -> str | None:
    """Build ``{primary_wmi}{chassis}`` garage key when epc_stub.enabled for brand."""
    code = (chassis_code or "").strip().upper()
    if not code:
        return None
    meta = brand_meta(brand)
    stub = meta.get("epc_stub") or {}
    if not stub.get("enabled", True):
        return None
    wmi = str(stub.get("primary_wmi") or "").strip().upper()
    if not wmi:
        wmis = meta.get("wmi_prefixes") or []
        wmi = str(wmis[0]).upper() if wmis else ""
    if not wmi:
        return None
    pattern = str(stub.get("pattern") or "{wmi}{chassis}")
    return pattern.format(wmi=wmi, chassis=code).upper()


def resolve_chassis_vin(
    chassis_code: str,
    *,
    brand: str | None = None,
    model_variant: str | None = None,
) -> dict[str, Any]:
    """Return enrichment fields for a chassis under *brand*.

    Keys: vin_prefix, model_variant, engines, year_range, enrichment_source
    (``curated`` | ``epc_stub`` | ``none``).
    """
    code = (chassis_code or "").strip().upper()
    slug = normalize_brand(brand or get_active_brand())
    curated = lookup_chassis(code, slug)
    if curated and curated.get("vin_prefixes"):
        return {
            "vin_prefix": curated["vin_prefixes"][0],
            "vin_prefixes": list(curated["vin_prefixes"]),
            "model_variant": model_variant or curated.get("model_variant"),
            "engines": list(curated.get("engines") or []),
            "year_range": curated.get("year_range"),
            "enrichment_source": "curated",
            "brand": slug,
        }

    stub = synthesize_epc_stub(code, slug) if code else None
    if stub:
        return {
            "vin_prefix": stub,
            "vin_prefixes": [stub],
            "model_variant": model_variant or f"{display_name(slug)} {code}",
            "engines": [],
            "year_range": None,
            "enrichment_source": "epc_stub",
            "brand": slug,
        }

    return {
        "vin_prefix": None,
        "vin_prefixes": [],
        "model_variant": model_variant or (f"{display_name(slug)} {code}" if code else None),
        "engines": [],
        "year_range": None,
        "enrichment_source": "none",
        "brand": slug,
    }


def chassis_from_prefix(prefix: str, brand: str | None = None) -> str | None:
    upper = (prefix or "").upper()
    if not upper:
        return None
    slug = normalize_brand(brand or get_active_brand()) if brand or get_active_brand() else None
    load_catalogs()
    # Prefer active brand matches
    for pfx, chassis, b in _prefix_index:
        if slug and b != slug:
            continue
        if upper.startswith(pfx) or pfx in upper:
            return chassis
    for pfx, chassis, _b in _prefix_index:
        if upper.startswith(pfx) or pfx in upper:
            return chassis
    # Embedded chassis token within active brand map
    for chassis in chassis_map(slug):
        if chassis in upper:
            return chassis
    return None


def active_chassis_catalog() -> dict[str, dict[str, Any]]:
    """Backward-compatible view: active brand's curated chassis map."""
    return chassis_map()
