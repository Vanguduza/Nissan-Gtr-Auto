"""Hard publish-quality gate for catalog bundles (APK + laptop SoT).

A job / test **PASS**es only when the bundle meets §2c / Megazip publish rules:
complete fitments (chassis + bbox + diagram_path), non-empty diagrams, and
engine-specific publishable flags. Hierarchy-only ``catalog_models.json`` with
``diagrams == 0`` is an explicit **FAIL**.

Custom / 7zap / catcar path-template adapters are **not** diagram SoR — they
always fail this gate (do not pretend publishable=true from HTML page counts).
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

logger = logging.getLogger("data_pipeline.bundle_quality_gate")

# Engines that produce storefront diagram + hotspot bundles.
DIAGRAM_SOR_ENGINES = frozenset({"megazip", "partsouq"})


def _load_json(path: Path) -> Any | None:
    try:
        if not path.is_file():
            return None
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def find_bundle_dirs(out_root: Path) -> list[Path]:
    """Locate maker ``bundle/`` directories under an orchestrator out-root."""
    root = Path(out_root)
    if not root.is_dir():
        return []
    found: list[Path] = []
    direct = root / "bundle"
    if direct.is_dir():
        found.append(direct)
    for child in sorted(root.iterdir()):
        if child.is_dir():
            b = child / "bundle"
            if b.is_dir():
                found.append(b)
    # de-dupe
    return list(dict.fromkeys(found))


def load_bundle_from_dir(bundle_dir: Path) -> dict[str, Any]:
    """Load hierarchy / PartSouq JSON shards into one dict."""
    keys = (
        "vehicle_master",
        "pnc_categories",
        "part_fitment",
        "diagram_assets",
        "catalog_makers",
        "catalog_models",
        "catalog_variants",
        "catalog_sections",
        "catalog_diagrams",
        "catalog_diagram_parts",
    )
    bundle: dict[str, Any] = {}
    for key in keys:
        data = _load_json(bundle_dir / f"{key}.json")
        if data is None:
            bundle[key] = []
        elif isinstance(data, list):
            bundle[key] = data
        elif isinstance(data, dict) and key in data:
            bundle[key] = data[key]
        else:
            bundle[key] = data if isinstance(data, list) else []
    return bundle


def detect_engine(out_root: Path, *, argv_hint: str | None = None) -> str:
    snap = _load_json(Path(out_root) / "profile_snapshot.json")
    if isinstance(snap, dict):
        eng = str(snap.get("engine") or "").strip().lower()
        if eng:
            return eng
    if argv_hint:
        low = argv_hint.lower()
        if "megazip" in low:
            return "megazip"
        if "partsouq" in low:
            return "partsouq"
        if "custom_catalog" in low:
            return "custom"
    # Infer from artifacts
    for b in find_bundle_dirs(Path(out_root)):
        q = _load_json(b / "quality_report.json")
        if isinstance(q, dict) and q.get("custom_adapter"):
            return "custom"
        if (b / "catalog_diagrams.json").is_file() and (b.parent / "megazip_state.db").is_file():
            return "megazip"
        if (b.parent / "crawler_state.db").is_file():
            return "partsouq"
    return "unknown"


def _count_pngs(out_root: Path) -> int:
    root = Path(out_root)
    try:
        return len({p.resolve() for p in root.rglob("*.png") if p.is_file()})
    except OSError:
        return 0


def evaluate_bundle(
    bundle: dict[str, Any],
    *,
    engine: str,
    quality_report: dict[str, Any] | None = None,
    png_count: int = 0,
) -> dict[str, Any]:
    """Return a gate verdict dict (``ok`` bool + reasons)."""
    engine = (engine or "unknown").lower()
    models = bundle.get("catalog_models") or []
    diagrams = bundle.get("catalog_diagrams") or []
    fitments = bundle.get("part_fitment") or []
    reasons: list[str] = []

    if engine not in DIAGRAM_SOR_ENGINES:
        reasons.append(
            f"engine={engine} is not diagram SoR (megazip/partsouq only); "
            "hierarchy/HTML page counts must not be treated as publishable"
        )
        return {
            "ok": False,
            "engine": engine,
            "publishable": False,
            "diagrams": len(diagrams),
            "fitments_total": len(fitments),
            "fitments_complete": 0,
            "fitments_out": 0,
            "models": len(models),
            "png_count": png_count,
            "reasons": reasons,
        }

    from data_pipeline.bundle_filter import filter_complete_bundle

    _, filter_meta = filter_complete_bundle(bundle, completed_only=True)
    fitments_out = int(filter_meta.get("fitments_out") or 0)
    diagrams_out = int(filter_meta.get("diagrams_out") or 0)

    publishable = False
    if engine == "megazip":
        from data_pipeline.megazip.quality import assert_publishable, bundle_quality_report

        meta = quality_report or bundle_quality_report(bundle)
        publishable = bool(meta.get("publishable"))
        if not publishable:
            reasons.append(
                "megazip publishable=false "
                f"(variants_publishable={meta.get('variants_publishable')}, "
                f"diagrams={meta.get('diagrams')}, "
                f"fitments_complete={meta.get('fitments_complete')})"
            )
        try:
            assert_publishable(meta, strict=True)
        except RuntimeError as exc:
            if str(exc) not in reasons:
                reasons.append(str(exc))
            publishable = False
        diagram_count = int(meta.get("diagrams") or len(diagrams))
        fitments_complete = int(meta.get("fitments_complete") or 0)
    else:
        # PartSouq §2c: complete fitments > 0; identity-only hierarchy is not enough
        diagram_count = len(diagrams) if diagrams else diagrams_out
        fitments_complete = fitments_out
        if fitments_out <= 0:
            reasons.append(
                "partsouq filter_complete_bundle fitments_out=0 "
                "(need chassis + bbox + diagram_path)"
            )
        if diagram_count <= 0 and png_count <= 0:
            reasons.append("partsouq has no diagram artifacts (catalog_diagrams empty, png_count=0)")
        uncat = 0
        for p in bundle.get("pnc_categories") or []:
            if str(p.get("category_name") or "").lower() == "uncategorized":
                uncat += 1
        if uncat:
            reasons.append(f"uncategorized_pncs={uncat}")
        publishable = fitments_out > 0 and (diagram_count > 0 or png_count > 0) and uncat == 0
        if quality_report and quality_report.get("publishable") is False:
            publishable = False
            reasons.append("quality_report.publishable=false")

    # Universal: hierarchy-only stubs fail
    if len(models) > 0 and diagram_count == 0 and fitments_complete == 0 and png_count == 0:
        msg = (
            f"hierarchy-only stub: models={len(models)} diagrams=0 "
            "fitments_complete=0 png_count=0"
        )
        if msg not in reasons:
            reasons.append(msg)
        publishable = False

    if not reasons and not publishable:
        reasons.append("publishable=false")

    return {
        "ok": bool(publishable) and not reasons,
        "engine": engine,
        "publishable": bool(publishable),
        "diagrams": diagram_count,
        "fitments_total": len(fitments),
        "fitments_complete": fitments_complete,
        "fitments_out": fitments_out,
        "models": len(models),
        "png_count": png_count,
        "reasons": reasons if not publishable else [],
        "filter_meta": filter_meta,
    }


def evaluate_out_root(
    out_root: Path,
    *,
    engine: str | None = None,
    argv_hint: str | None = None,
) -> dict[str, Any]:
    root = Path(out_root)
    eng = (engine or detect_engine(root, argv_hint=argv_hint)).lower()
    png_count = _count_pngs(root)
    bundles = find_bundle_dirs(root)
    if not bundles:
        return {
            "ok": False,
            "engine": eng,
            "publishable": False,
            "diagrams": 0,
            "fitments_total": 0,
            "fitments_complete": 0,
            "fitments_out": 0,
            "models": 0,
            "png_count": png_count,
            "reasons": [f"no bundle/ under {root}"],
            "bundle_dirs": [],
        }

    # Aggregate: PASS if any maker bundle passes (multi-maker rare on APK)
    best: dict[str, Any] | None = None
    per: list[dict[str, Any]] = []
    for bdir in bundles:
        bundle = load_bundle_from_dir(bdir)
        q = _load_json(bdir / "quality_report.json")
        qdict = q if isinstance(q, dict) else None
        verdict = evaluate_bundle(
            bundle,
            engine=eng,
            quality_report=qdict,
            png_count=png_count,
        )
        verdict["bundle_dir"] = str(bdir)
        per.append(verdict)
        if best is None or (verdict.get("ok") and not best.get("ok")):
            best = verdict
        elif best is not None and verdict.get("fitments_out", 0) > best.get("fitments_out", 0):
            best = verdict

    assert best is not None
    best = dict(best)
    best["bundle_dirs"] = [str(b) for b in bundles]
    best["per_bundle"] = per
    # ok if any bundle ok
    if any(p.get("ok") for p in per):
        best["ok"] = True
        best["publishable"] = True
        best["reasons"] = []
    return best


def write_gate_artifacts(out_root: Path, verdict: dict[str, Any]) -> Path:
    """Write ``bundle_quality.txt`` + refresh ``quality_report.json`` gate fields."""
    root = Path(out_root)
    root.mkdir(parents=True, exist_ok=True)
    lines = [
        f"ok={bool(verdict.get('ok'))}",
        f"publishable={bool(verdict.get('publishable'))}",
        f"engine={verdict.get('engine')}",
        f"diagrams={verdict.get('diagrams')}",
        f"fitments_out={verdict.get('fitments_out')}",
        f"fitments_complete={verdict.get('fitments_complete')}",
        f"models={verdict.get('models')}",
        f"png_count={verdict.get('png_count')}",
    ]
    for reason in verdict.get("reasons") or []:
        lines.append(f"FAIL: {reason}")
    if verdict.get("ok"):
        lines.append("PASS: bundle meets diagram/fitment publish gate")
    text = "\n".join(lines) + "\n"
    out = root / "bundle_quality.txt"
    out.write_text(text, encoding="utf-8")
    # Mirror into each bundle quality_report when present
    for bdir_s in verdict.get("bundle_dirs") or []:
        bdir = Path(bdir_s)
        qpath = bdir / "quality_report.json"
        existing = _load_json(qpath)
        meta = existing if isinstance(existing, dict) else {}
        meta["publishable"] = bool(verdict.get("publishable")) and bool(verdict.get("ok"))
        meta["gate_ok"] = bool(verdict.get("ok"))
        meta["gate_reasons"] = list(verdict.get("reasons") or [])
        meta["gate_engine"] = verdict.get("engine")
        qpath.write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    return out


def assert_out_root_publishable(
    out_root: Path,
    *,
    engine: str | None = None,
    argv_hint: str | None = None,
) -> dict[str, Any]:
    """Evaluate + write artifacts; raise RuntimeError on FAIL."""
    verdict = evaluate_out_root(out_root, engine=engine, argv_hint=argv_hint)
    write_gate_artifacts(out_root, verdict)
    if not verdict.get("ok"):
        reasons = "; ".join(verdict.get("reasons") or ["publish gate failed"])
        raise RuntimeError(reasons)
    return verdict
