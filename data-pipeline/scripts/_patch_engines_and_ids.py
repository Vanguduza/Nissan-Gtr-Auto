"""Add external_item_id aliases + crawl/parse default_engine from variant."""
from __future__ import annotations

from pathlib import Path

root = Path(__file__).resolve().parents[1]

# --- parse_html: dual external_item_id on part rows ---
parse_path = root / "data_pipeline" / "megazip" / "parse_html.py"
pt = parse_path.read_text(encoding="utf-8")
needle = '"megazip_item_id": meta.get("megazip_item_id") or item_id,'
insert = (
    '"megazip_item_id": meta.get("megazip_item_id") or item_id,\n'
    '                "external_item_id": meta.get("external_item_id") '
    'or meta.get("megazip_item_id") or item_id,'
)
if insert not in pt:
    count = pt.count(needle)
    if count < 1:
        raise SystemExit("megazip_item_id needle missing")
    pt = pt.replace(needle, insert)
    parse_path.write_text(pt, encoding="utf-8")
    print("parse_html external_item_id x", count)
else:
    print("parse_html already dual-mapped")

# --- crawl: lookup engine from parsed variants ---
crawl_path = root / "data_pipeline" / "megazip" / "crawl.py"
ct = crawl_path.read_text(encoding="utf-8")

helper = '''
def _engine_for_variant(
    db_path: Path,
    *,
    maker_slug: str,
    model_slug: str,
    variant_slug: str,
) -> str:
    """Best-effort engine_code from a prior variant_list parse for this variant."""
    if not variant_slug:
        return ""
    for row in load_all_parsed(db_path, maker_slug=maker_slug):
        if row.get("page_type") != "variant_list":
            continue
        payload = row.get("payload") or {}
        if model_slug and (payload.get("model_slug") or "") not in ("", model_slug):
            continue
        for v in payload.get("variants") or []:
            if (v.get("slug") or "") != variant_slug:
                continue
            eng = (v.get("engine_code") or "").strip()
            if eng:
                return eng
    return ""


'''

if "_engine_for_variant" not in ct:
    anchor = "def _priority_allows("
    if anchor not in ct:
        raise SystemExit("anchor missing")
    ct = ct.replace(anchor, helper + anchor, 1)

# Ensure load_all_parsed is imported
if "load_all_parsed" not in ct.split("from data_pipeline.megazip.state import")[1].split("\n)")[0]:
    ct = ct.replace(
        "from data_pipeline.megazip.state import (",
        "from data_pipeline.megazip.state import (\n    load_all_parsed,",
        1,
    )

# Inject default_engine into crawl parse_html_page calls (two places in crawl loop)
old_parse = '''                    parsed = parse_html_page(
                        html,
                        url,
                        maker_slug=paths.slug,
                        model_slug=row.get("model_slug") or "",
                        variant_slug=row.get("variant_slug") or "",
                        section_slug=row.get("section_slug") or "",
                        default_chassis=chassis,
                    )'''
new_parse = '''                    default_engine = ""
                    if (row.get("page_type") or "") == "diagram" or classify_megazip_url(url) == "diagram":
                        default_engine = _engine_for_variant(
                            paths.state_db,
                            maker_slug=paths.slug,
                            model_slug=row.get("model_slug") or "",
                            variant_slug=row.get("variant_slug") or "",
                        )
                    parsed = parse_html_page(
                        html,
                        url,
                        maker_slug=paths.slug,
                        model_slug=row.get("model_slug") or "",
                        variant_slug=row.get("variant_slug") or "",
                        section_slug=row.get("section_slug") or "",
                        default_chassis=chassis,
                        default_engine=default_engine,
                    )'''
if old_parse not in ct:
    raise SystemExit("crawl parse_html_page block not found")
ct = ct.replace(old_parse, new_parse, 1)

# Second parse after image fetch should also pass default_engine
old_reparse = '''                                    parsed = parse_html_page(
                                        html,
                                        url,
                                        maker_slug=paths.slug,
                                        model_slug=row.get("model_slug") or "",
                                        variant_slug=row.get("variant_slug") or "",
                                        section_slug=row.get("section_slug") or "",
                                        default_chassis=chassis,
                                        image_bytes=ir.content,
                                    )'''
new_reparse = '''                                    parsed = parse_html_page(
                                        html,
                                        url,
                                        maker_slug=paths.slug,
                                        model_slug=row.get("model_slug") or "",
                                        variant_slug=row.get("variant_slug") or "",
                                        section_slug=row.get("section_slug") or "",
                                        default_chassis=chassis,
                                        default_engine=default_engine,
                                        image_bytes=ir.content,
                                    )'''
if old_reparse not in ct:
    raise SystemExit("crawl reparse block not found")
ct = ct.replace(old_reparse, new_reparse, 1)

# parse_cached_pages: pass default_engine
old_cached = '''            parsed = parse_html_page(
                html,
                url,
                maker_slug=paths.slug,
                model_slug=model_slug or "",
                variant_slug=variant_slug or "",
                section_slug=section_slug or "",
                default_chassis=chassis or "",
                image_bytes=image_bytes,
                stored_width=int(stored_w) if stored_w else None,
                stored_height=int(stored_h) if stored_h else None,
            )'''
new_cached = '''            default_engine = _engine_for_variant(
                paths.state_db,
                maker_slug=paths.slug,
                model_slug=model_slug or "",
                variant_slug=variant_slug or "",
            )
            parsed = parse_html_page(
                html,
                url,
                maker_slug=paths.slug,
                model_slug=model_slug or "",
                variant_slug=variant_slug or "",
                section_slug=section_slug or "",
                default_chassis=chassis or "",
                default_engine=default_engine,
                image_bytes=image_bytes,
                stored_width=int(stored_w) if stored_w else None,
                stored_height=int(stored_h) if stored_h else None,
            )'''
if old_cached not in ct:
    raise SystemExit("parse_cached_pages block not found")
ct = ct.replace(old_cached, new_cached, 1)

# Need classify_megazip_url import if not present
if "classify_megazip_url" not in ct:
    raise SystemExit("classify_megazip_url should already be imported")

crawl_path.write_text(ct, encoding="utf-8")
print("crawl engine lookup patched")
