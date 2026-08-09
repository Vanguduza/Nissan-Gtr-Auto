"""Megazip EPC catalog — configuration and path layout."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PACKAGE_ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_MAKERS_FILE = PACKAGE_ROOT / "config" / "megazip_makers.json"
DEFAULT_PRIORITY_FILE = PACKAGE_ROOT / "config" / "priority_chassis.json"
DEFAULT_CHASSIS_MAP_FILE = PACKAGE_ROOT / "config" / "megazip_chassis_map.json"
DEFAULT_PCDB_FILE = PACKAGE_ROOT / "config" / "epc_to_pcdb.json"
DEFAULT_OUT_ROOT = Path("out/megazip")


def maker_slug(name: str) -> str:
    text = name.strip().lower()
    text = re.sub(r"[^\w\s-]", "", text, flags=re.UNICODE)
    text = re.sub(r"[\s_]+", "-", text).strip("-")
    return text or "unknown"


@dataclass(frozen=True)
class MegazipConfig:
    base_url: str
    parts_hub_path: str
    catalog_path_prefix: str
    rate_limit_seconds: float
    max_concurrent_workers: int
    diagram_storage_prefix_template: str
    makers: tuple[str, ...]
    maker_slugs: dict[str, str]

    @classmethod
    def load(cls, path: Path | None = None) -> MegazipConfig:
        p = path or DEFAULT_MAKERS_FILE
        data = json.loads(p.read_text(encoding="utf-8"))
        makers = tuple(str(m) for m in data.get("makers") or [])
        slugs = {str(k): str(v) for k, v in (data.get("maker_slugs") or {}).items()}
        for m in makers:
            slugs.setdefault(m, maker_slug(m))
        return cls(
            base_url=str(data.get("base_url") or "https://www.megazip.net").rstrip("/"),
            parts_hub_path=str(data.get("parts_hub_path") or "/parts"),
            catalog_path_prefix=str(data.get("catalog_path_prefix") or "/zapchasti-dlya-avtomobilej"),
            rate_limit_seconds=float(data.get("rate_limit_seconds") or 0.35),
            max_concurrent_workers=int(data.get("max_concurrent_workers") or 4),
            diagram_storage_prefix_template=str(
                data.get("diagram_storage_prefix_template") or "epc/{maker_slug}"
            ),
            makers=makers,
            maker_slugs=slugs,
        )

    def hub_url(self, maker: str) -> str:
        slug = self.maker_slugs.get(maker, maker_slug(maker))
        return f"{self.base_url}{self.parts_hub_path}/{slug}"

    def storage_prefix(self, maker: str) -> str:
        slug = self.maker_slugs.get(maker, maker_slug(maker))
        return self.diagram_storage_prefix_template.format(maker_slug=slug)


@dataclass(frozen=True)
class MakerPaths:
    maker: str
    slug: str
    root: Path
    state_db: Path
    cache_dir: Path
    bundle_dir: Path
    diagrams_dir: Path
    meta_json: Path

    @property
    def diagram_storage_prefix(self) -> str:
        return f"epc/{self.slug}"


def build_maker_paths(maker: str, out_root: Path, config: MegazipConfig | None = None) -> MakerPaths:
    cfg = config or MegazipConfig.load()
    slug = cfg.maker_slugs.get(maker, maker_slug(maker))
    root = out_root / slug
    return MakerPaths(
        maker=maker.strip(),
        slug=slug,
        root=root,
        state_db=root / "megazip_state.db",
        cache_dir=root / "cache",
        bundle_dir=root / "bundle",
        diagrams_dir=root / "diagrams",
        meta_json=root / "meta.json",
    )


def load_priority_chassis_codes(path: Path | None = None) -> frozenset[str]:
    p = path or DEFAULT_PRIORITY_FILE
    if not p.is_file():
        return frozenset()
    data = json.loads(p.read_text(encoding="utf-8"))
    codes: set[str] = set()
    for raw in data.get("chassis_codes") or []:
        codes.add(str(raw).upper())
    for code, entry in (data.get("chassis") or {}).items():
        codes.add(str(code).upper())
        if isinstance(entry, dict):
            for alias in entry.get("aliases") or []:
                codes.add(str(alias).upper())
    return frozenset(codes)


def load_priority_model_seeds(
    path: Path | None = None,
    maker_slug: str = "",
    *,
    chassis_code: str | None = None,
    priority_codes: frozenset[str] | None = None,
) -> tuple[str, ...]:
    """Direct model catalog URLs to seed when --priority-chassis or --single-chassis is active."""
    p = path or DEFAULT_PRIORITY_FILE
    if not p.is_file():
        return ()
    data = json.loads(p.read_text(encoding="utf-8"))
    seeds = data.get("model_seed_urls") or {}
    if isinstance(seeds, list):
        return tuple(str(u) for u in seeds)
    if not maker_slug or not isinstance(seeds, dict):
        return ()
    raw = seeds.get(maker_slug) or seeds.get(maker_slug.lower()) or []
    if isinstance(raw, list):
        return tuple(str(u) for u in raw)
    if not isinstance(raw, dict):
        return ()
    if chassis_code:
        keyed = raw.get(chassis_code.upper()) or raw.get(chassis_code.lower()) or []
        return tuple(str(u) for u in keyed)
    allowed = priority_codes or load_priority_chassis_codes(p)
    out: list[str] = []
    for code, urls in raw.items():
        if code.startswith("_"):
            continue
        if allowed and str(code).upper() not in allowed:
            continue
        if isinstance(urls, list):
            out.extend(str(u) for u in urls)
    return tuple(out)


def load_megazip_chassis_map(path: Path | None = None) -> dict[str, dict[str, Any]]:
    """Per-chassis Megazip availability, proxy, and optional model_seed URL."""
    p = path or DEFAULT_CHASSIS_MAP_FILE
    if not p.is_file():
        return {}
    data = json.loads(p.read_text(encoding="utf-8"))
    raw = data.get("chassis") or {}
    out: dict[str, dict[str, Any]] = {}
    for code, entry in raw.items():
        if isinstance(entry, dict):
            out[str(code).upper()] = dict(entry)
    return out


def megazip_chassis_entry(
    chassis_code: str,
    chassis_map: dict[str, dict[str, Any]] | None = None,
) -> dict[str, Any]:
    m = chassis_map if chassis_map is not None else load_megazip_chassis_map()
    return m.get(chassis_code.upper(), {})


def megazip_chassis_available(chassis_code: str, chassis_map: dict[str, dict[str, Any]] | None = None) -> bool:
    """True when chassis is crawlable on Megazip (default True if unlisted)."""
    entry = megazip_chassis_entry(chassis_code, chassis_map)
    if not entry:
        return True
    return entry.get("megazip_available", True) is not False


def load_merged_priority_model_seeds(
    priority_file: Path | None = None,
    chassis_map_file: Path | None = None,
    maker_slug: str = "nissan",
    *,
    priority_codes: frozenset[str] | None = None,
) -> tuple[str, ...]:
    """Merge model seeds from priority_chassis.json and megazip_chassis_map."""
    seeds = list(
        load_priority_model_seeds(
            priority_file,
            maker_slug,
            priority_codes=priority_codes,
        )
    )
    chassis_map = load_megazip_chassis_map(chassis_map_file)
    allowed = priority_codes or load_priority_chassis_codes(priority_file)
    for code in sorted(allowed):
        if not megazip_chassis_available(code, chassis_map):
            continue
        seed = megazip_chassis_entry(code, chassis_map).get("model_seed")
        if seed:
            seeds.append(str(seed))
    seen: set[str] = set()
    out: list[str] = []
    for url in seeds:
        if url not in seen:
            seen.add(url)
            out.append(url)
    return tuple(out)


def megazip_model_seeds_for_chassis(
    chassis_code: str,
    *,
    chassis_map: dict[str, dict[str, Any]] | None = None,
    priority_file: Path | None = None,
    maker_slug: str = "nissan",
) -> tuple[str, ...]:
    """Merge model seeds from megazip_chassis_map and priority_chassis.json."""
    seeds: list[str] = []
    entry = megazip_chassis_entry(chassis_code, chassis_map)
    seed = entry.get("model_seed")
    if seed:
        seeds.append(str(seed))
    seeds.extend(load_priority_model_seeds(priority_file, maker_slug, chassis_code=chassis_code))
    seen: set[str] = set()
    out: list[str] = []
    for u in seeds:
        if u not in seen:
            seen.add(u)
            out.append(u)
    return tuple(out)
