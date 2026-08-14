"""Tests for Megazip HTML parser and hierarchy transform."""

from __future__ import annotations

import struct
from pathlib import Path

from data_pipeline.megazip.config import (
    load_megazip_chassis_map,
    load_merged_priority_model_seeds,
    load_priority_chassis_codes,
    load_priority_model_seeds,
    megazip_chassis_available,
    megazip_model_seeds_for_chassis,
)
from data_pipeline.megazip.enrich_pcdb import enrich_pcdb
from data_pipeline.megazip.parse_html import (
    _diagram_image_dimensions,
    classify_diagram_kind,
    normalize_bbox,
    parse_diagram_page,
    parse_maker_hub,
    parse_section_list,
    parse_variant_list,
)
from data_pipeline.megazip.quality import (
    assert_publishable,
    bundle_quality_report,
    variant_quality_breakdown,
)

FIX = Path(__file__).resolve().parent / "fixtures" / "megazip"


def _png_bytes(width: int, height: int) -> bytes:
    return b"\x89PNG\r\n\x1a\n" + b"\x00" * 8 + struct.pack(">II", width, height)


def test_priority_model_seeds_chassis_specific() -> None:
    priority_file = Path(__file__).resolve().parents[1] / "config" / "priority_chassis.json"
    all_codes = load_priority_chassis_codes(priority_file)
    assert "T32" in all_codes
    assert "R35" not in all_codes
    t32_seeds = load_priority_model_seeds(priority_file, maker_slug="nissan", chassis_code="T32")
    assert len(t32_seeds) == 1
    assert "x-trail-2064" in t32_seeds[0]
    assert "gt-r" not in t32_seeds[0].lower()
    r35_seeds = load_priority_model_seeds(priority_file, maker_slug="nissan", chassis_code="R35")
    assert r35_seeds == ()


def test_parse_maker_hub_models_sorted() -> None:
    html = (FIX / "maker_hub_toyota.html").read_text(encoding="utf-8")
    page = parse_maker_hub(html, "https://www.megazip.net/parts/toyota", "toyota")
    models = page.payload["models"]
    assert len(models) >= 2
    assert models[0]["sort_key"] <= models[1]["sort_key"]


def test_parse_maker_hub_silcard_nissan() -> None:
    html = (FIX / "maker_hub_nissan_silcard.html").read_text(encoding="utf-8")
    page = parse_maker_hub(html, "https://www.megazip.net/parts/nissan", "nissan")
    slugs = {m["slug"] for m in page.payload["models"]}
    assert "x-trail-2064" in slugs
    assert "navara-2055" in slugs


def test_parse_variant_list_chassis() -> None:
    html = (FIX / "variant_list.html").read_text(encoding="utf-8")
    url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/toyota/camry-vista-aurion-42430/acv40-55581"
    page = parse_variant_list(html, url, "toyota", "camry-vista-aurion-42430")
    variants = page.payload["variants"]
    assert len(variants) == 1
    assert variants[0]["chassis_code"] == "ACV40"
    assert variants[0]["engine_code"] == "2AZ-FE"
    assert variants[0]["year_label"] == "2007 - 2011"


def test_parse_section_list() -> None:
    html = (FIX / "section_list.html").read_text(encoding="utf-8")
    url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/toyota/camry-vista-aurion-42430/acv40-55581/acv40l-jeankr-928407"
    page = parse_section_list(html, url, "toyota", "camry-vista-aurion-42430", "acv40l-jeankr-928407")
    sections = page.payload["sections"]
    assert len(sections) == 1
    assert sections[0]["name"] == "Standard Tool"


def test_parse_diagram_hotspots_and_oem() -> None:
    html = (FIX / "diagram.html").read_text(encoding="utf-8")
    url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/toyota/camry-vista-aurion-42430/acv40-55581/acv40l-jeankr-928407/standard-tool-17957949"
    page = parse_diagram_page(
        html,
        url,
        "toyota",
        "camry-vista-aurion-42430",
        "acv40l-jeankr-928407",
        "standard-tool-17957949",
        default_chassis="ACV40",
    )
    parts = page.payload["parts"]
    assert len(parts) == 2
    assert parts[0]["oem_part_number"] == "09113-08061"
    assert parts[0]["bbox_x"] is not None
    assert page.payload["diagram_kind"] in ("exploded_diagram", "ambiguous", "parts_list_raster")
    assert len(page.payload["parts_table"]) == 2


def test_classify_diagram_kind() -> None:
    raster_html = """
    <map><area shape="rect" coords="10,40,50,55" data-items-list-id="1"/>
    <area shape="rect" coords="60,42,100,58" data-items-list-id="2"/>
    <area shape="rect" coords="500,45,540,60" data-items-list-id="3"/></map>
    """
    exploded_html = """
    <map><area shape="rect" coords="10,20,50,60" data-items-list-id="1"/>
    <area shape="rect" coords="200,300,240,340" data-items-list-id="2"/>
    <area shape="rect" coords="400,10,440,50" data-items-list-id="3"/></map>
    """
    assert classify_diagram_kind(raster_html) == "parts_list_raster"
    assert classify_diagram_kind(exploded_html) == "exploded_diagram"


def test_parse_parts_table_from_data_item() -> None:
    html = """
    <table class="items-list">
    <tr data-items-list-id="99" data-item="{&quot;itemslist_id&quot;:&quot;99&quot;,&quot;ref&quot;:&quot;11001&quot;,&quot;number&quot;:&quot;11044-00Q0A&quot;,&quot;name&quot;:&quot;BLOCK ASSY&quot;,&quot;quantity&quot;:&quot;1&quot;,&quot;id&quot;:&quot;999&quot;}">
      <td class="items-list__cell_type_ref">11001</td>
      <td class="items-list__cell_type_number"><p class="items-list__number">11044-00Q0A</p></td>
      <td class="items-list__cell_type_quantity">1</td>
    </tr>
    </table>
    """
    page = parse_diagram_page(
        html,
        "https://example/diagram",
        "nissan",
        "nissan-gt-r-2063",
        "r35-6214",
        "cylinder-block-oil-pan-2293503",
        default_chassis="R35",
    )
    table = page.payload["parts_table"]
    assert len(table) == 1
    assert table[0]["oem_part_number"] == "11044-00Q0A"
    assert table[0]["callout_ref"] == "11001"
    assert table[0]["quantity"] == "1"
    assert table[0]["megazip_item_id"] == "999"


def test_dedupe_map_areas() -> None:
    html = """
    <img id="items_list_image" src="https://storage.megazip.net/catalog/M/x.png"/>
    <map>
    <area shape="rect" coords="178,34,222,49" data-items-list-id="415542274"/>
    <area shape="rect" coords="178,34,222,49" data-items-list-id="415542274"/>
    <area shape="rect" coords="335,133,377,147" data-items-list-id="415542275"/>
    </map>
    <tr data-items-list-id="415542274"><td class="items-list__cell_type_number">09113-08061</td></tr>
    <tr data-items-list-id="415542275"><td class="items-list__cell_type_number">09114-08061</td></tr>
    """
    page = parse_diagram_page(
        html,
        "https://example/diagram",
        "toyota",
        "model",
        "variant",
        "section",
    )
    assert page.payload["hotspot_count"] == 2
    assert len(page.payload["parts"]) == 2


def test_png_dimension_override_via_image_bytes() -> None:
    html = """
    <meta name="viewport" content="width=1200"/>
    <img id="items_list_image" src="https://storage.megazip.net/catalog/M/x.png"/>
    """
    w, h = _diagram_image_dimensions(html, image_bytes=_png_bytes(850, 465))
    assert w == 850
    assert h == 465


def test_normalize_bbox() -> None:
    bb = normalize_bbox("178,34,222,49", 560, 819)
    assert bb is not None
    x, y, w, h = bb
    assert 0 <= x <= 1 and 0 <= y <= 1 and w > 0 and h > 0


def test_pcdb_enrich_additive() -> None:
    bundle = {
        "pnc_categories": [{"pnc_code": "MZ1", "category_name": "Engine Assembly"}],
        "part_fitment": [],
    }
    stats = enrich_pcdb(bundle)
    assert stats["pcdb_mapped"] == 1
    assert bundle["pnc_categories"][0]["pcdb_part_type_id"] == 12000


def test_parse_model_catalog_variant_link() -> None:
    html = """
    <ul class="s-catalog__columns-list">
      <li class="filtred_item f_id_6214" data-id="6214">
        <a class="s-catalog__model-link search_value"
           href="/zapchasti-dlya-avtomobilej/nissan/nissan-gt-r-2063/r35-6214">R35</a>
      </li>
    </ul>
    """
    url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/nissan-gt-r-2063"
    page = parse_variant_list(html, url, "nissan", "nissan-gt-r-2063")
    variants = page.payload["variants"]
    assert len(variants) == 1
    assert variants[0]["chassis_code"] == "R35"
    assert variants[0]["slug"] == "r35-6214"


def test_stored_dimensions_skip_fallback() -> None:
    html = """
    <img id="items_list_image" src="https://storage.megazip.net/catalog/M/x.png"/>
    """
    w, h = _diagram_image_dimensions(html, stored_width=850, stored_height=465)
    assert w == 850
    assert h == 465


def test_variant_quality_breakdown() -> None:
    from data_pipeline.megazip.quality import variant_quality_breakdown

    bundle = {
        "catalog_variants": [
            {"slug": "v1", "model_slug": "m1", "chassis_code": "R35"},
            {"slug": "v2", "model_slug": "m1", "chassis_code": "R34"},
        ],
        "catalog_diagrams": [
            {
                "storage_path": "megazip/nissan/a.png",
                "diagram_kind": "exploded_diagram",
                "hotspot_count": 8,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
            {
                "storage_path": "megazip/nissan/b.png",
                "diagram_kind": "parts_list_raster",
                "hotspot_count": 3,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
        ],
        "catalog_diagram_parts": [
            {"diagram_path": "megazip/nissan/b.png", "oem_part_number": "12345-ABCDE"},
        ],
        "part_fitment": [
            {
                "oem_part_number": "09113-08061",
                "chassis_code": "R35",
                "bbox_x": 0.1,
                "diagram_path": "megazip/nissan/a.png",
            }
            for _ in range(3)
        ],
        "pnc_categories": [{"pnc_code": "MZ1", "category_name": "Engine"}],
    }
    rows = variant_quality_breakdown(bundle)
    r35 = next(r for r in rows if r["chassis_code"] == "R35")
    assert r35["exploded_count"] == 1
    assert r35["raster_count"] == 1
    assert r35["companion_parts_rows"] == 1
    assert r35["complete"] is True
    r34 = next(r for r in rows if r["chassis_code"] == "R34")
    assert r34["complete"] is False


def test_complete_only_requires_exploded_per_variant() -> None:
    from data_pipeline.bundle_filter import filter_complete_bundle

    bundle = {
        "catalog_variants": [
            {"slug": "v1", "model_slug": "m1", "chassis_code": "R35"},
        ],
        "catalog_diagrams": [
            {
                "storage_path": "megazip/nissan/raster.png",
                "diagram_kind": "parts_list_raster",
                "model_slug": "m1",
                "variant_slug": "v1",
            },
        ],
        "catalog_diagram_parts": [
            {"diagram_path": "megazip/nissan/raster.png", "oem_part_number": "12345-ABCDE"},
        ],
        "part_fitment": [
            {
                "oem_part_number": "12345-ABCDE",
                "chassis_code": "R35",
                "diagram_path": "megazip/nissan/raster.png",
            },
        ],
        "vehicle_master": [{"chassis_code": "R35", "model_variant": "GT-R R35"}],
        "pnc_categories": [{"pnc_code": "MZ1", "category_name": "Gasket"}],
        "diagram_assets": [{"storage_path": "megazip/nissan/raster.png", "source_url": "http://x"}],
    }
    filtered, meta = filter_complete_bundle(bundle, completed_only=True)
    assert filtered["part_fitment"] == []
    assert meta["variants_with_passing_exploded"] == 0


def test_quality_report_publishable() -> None:
    bundle = {
        "catalog_models": [{}],
        "catalog_variants": [{"slug": "v1", "model_slug": "m1", "chassis_code": "ACV40"}],
        "catalog_sections": [{}],
        "catalog_diagrams": [
            {
                "storage_path": "megazip/toyota/x.png",
                "diagram_kind": "exploded_diagram",
                "hotspot_count": 8,
                "model_slug": "m1",
                "variant_slug": "v1",
            }
        ],
        "catalog_diagram_parts": [],
        "pnc_categories": [{"pnc_code": "MZ1", "category_name": "Standard Tool"}],
        "part_fitment": [
            {
                "oem_part_number": "09113-08061",
                "pnc_code": "MZ1",
                "chassis_code": "ACV40",
                "bbox_x": 0.1,
                "bbox_y": 0.2,
                "bbox_width": 0.05,
                "bbox_height": 0.02,
                "diagram_path": "megazip/toyota/x.png",
            }
            for _ in range(3)
        ],
    }
    meta = bundle_quality_report(bundle)
    assert meta["publishable"] is True
    assert meta["maker_publishable"] is True
    assert meta["fitments_complete"] == 3
    assert meta["diagram_kind_counts"]["exploded_diagram"] == 1


def test_two_tier_publish_variant_ready_maker_advisory() -> None:
    """Variant publishable even when some diagrams fail maker-level strict gate."""
    bundle = {
        "catalog_variants": [
            {"slug": "v1", "model_slug": "m1", "chassis_code": "T31"},
        ],
        "catalog_diagrams": [
            {
                "storage_path": "megazip/nissan/good.png",
                "diagram_kind": "exploded_diagram",
                "hotspot_count": 8,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
            {
                "storage_path": "megazip/nissan/bad.png",
                "diagram_kind": "exploded_diagram",
                "hotspot_count": 1,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
        ],
        "catalog_diagram_parts": [],
        "pnc_categories": [{"pnc_code": "MZ1", "category_name": "Engine"}],
        "part_fitment": [
            {
                "oem_part_number": "09113-08061",
                "pnc_code": "MZ1",
                "chassis_code": "T31",
                "bbox_x": 0.1,
                "diagram_path": "megazip/nissan/good.png",
            }
            for _ in range(3)
        ],
    }
    meta = bundle_quality_report(bundle)
    assert meta["variants_publishable"] == 1
    assert meta["publishable"] is True
    assert meta["maker_publishable"] is False
    assert meta["diagrams_failing_gate_count"] == 1
    assert_publishable(meta, strict=True)


def test_ambiguous_diagram_passes_with_table_only() -> None:
    from data_pipeline.bundle_filter import filter_complete_bundle
    from data_pipeline.megazip.quality import _passing_diagram_paths

    bundle = {
        "catalog_variants": [{"slug": "v1", "model_slug": "m1", "chassis_code": "T31"}],
        "catalog_diagrams": [
            {
                "storage_path": "megazip/nissan/amb.png",
                "diagram_kind": "ambiguous",
                "hotspot_count": 1,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
            {
                "storage_path": "megazip/nissan/exp.png",
                "diagram_kind": "exploded_diagram",
                "hotspot_count": 8,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
        ],
        "catalog_diagram_parts": [
            {"diagram_path": "megazip/nissan/amb.png", "oem_part_number": "12345-ABCDE"},
        ],
        "part_fitment": [
            {
                "oem_part_number": "09113-08061",
                "chassis_code": "T31",
                "bbox_x": 0.1,
                "diagram_path": "megazip/nissan/exp.png",
            }
            for _ in range(3)
        ],
        "vehicle_master": [{"chassis_code": "T31"}],
        "pnc_categories": [{"pnc_code": "MZ1", "category_name": "Gasket"}],
        "diagram_assets": [],
    }
    passing = _passing_diagram_paths(bundle)
    assert "megazip/nissan/amb.png" in passing
    filtered, _meta = filter_complete_bundle(bundle, completed_only=True)
    amb_diag = next(d for d in filtered["catalog_diagrams"] if d["storage_path"].endswith("amb.png"))
    assert amb_diag["publish_diagram"] is False
    assert amb_diag.get("needs_review") is True
    meta = bundle_quality_report(bundle)
    assert meta["ambiguous_diagrams_passing"] == 1


def test_megazip_chassis_map_t32_unavailable() -> None:
    chassis_map = load_megazip_chassis_map(
        Path(__file__).resolve().parents[1] / "config" / "megazip_chassis_map.json"
    )
    assert megazip_chassis_available("T31", chassis_map) is True
    assert megazip_chassis_available("T32", chassis_map) is False
    entry = chassis_map["T32"]
    assert entry.get("megazip_proxy") == "T31"


def test_megazip_model_seeds_merge_chassis_map() -> None:
    priority_file = Path(__file__).resolve().parents[1] / "config" / "priority_chassis.json"
    chassis_map_file = Path(__file__).resolve().parents[1] / "config" / "megazip_chassis_map.json"
    chassis_map = load_megazip_chassis_map(chassis_map_file)
    seeds = megazip_model_seeds_for_chassis(
        "T31",
        chassis_map=chassis_map,
        priority_file=priority_file,
        maker_slug="nissan",
    )
    assert len(seeds) >= 1
    assert "x-trail-2064" in seeds[0]


def test_merged_priority_model_seeds_dedupe_chassis_map() -> None:
    priority_file = Path(__file__).resolve().parents[1] / "config" / "priority_chassis.json"
    chassis_map_file = Path(__file__).resolve().parents[1] / "config" / "megazip_chassis_map.json"
    codes = load_priority_chassis_codes(priority_file)
    merged = load_merged_priority_model_seeds(
        priority_file,
        chassis_map_file,
        maker_slug="nissan",
        priority_codes=codes,
    )
    assert len(merged) == 1
    assert "x-trail-2064" in merged[0]


def test_claim_next_url_chassis_deep_first(tmp_path: Path) -> None:
    from data_pipeline.megazip.state import claim_next_url, enqueue_url, init_db, mark_url

    db = tmp_path / "state.db"
    init_db(db)
    enqueue_url(db, "https://ex/hub", page_type="maker_hub", maker_slug="nissan")
    enqueue_url(
        db,
        "https://ex/t31/sec",
        page_type="section_list",
        maker_slug="nissan",
        model_slug="x-trail",
        chassis_code="T31",
    )
    enqueue_url(
        db,
        "https://ex/d40/sec",
        page_type="section_list",
        maker_slug="nissan",
        model_slug="navara",
        chassis_code="D40",
    )
    enqueue_url(
        db,
        "https://ex/t31/diag",
        page_type="diagram",
        maker_slug="nissan",
        model_slug="x-trail",
        chassis_code="T31",
    )

    first = claim_next_url(db, maker_slug="nissan", chassis_deep_first=True)
    assert first is not None
    assert first["page_type"] == "maker_hub"
    mark_url(db, first["url"], ok=True)

    second = claim_next_url(db, maker_slug="nissan", chassis_deep_first=True)
    assert second is not None
    assert second["chassis_code"] == "D40"  # lexicographic first among deep-only
    mark_url(db, second["url"], ok=True)

    # After D40 has VISITED deep progress and still has no pending, T31 drains fully.
    third = claim_next_url(db, maker_slug="nissan", chassis_deep_first=True)
    assert third is not None
    assert third["chassis_code"] == "T31"
    mark_url(db, third["url"], ok=True)

    fourth = claim_next_url(db, maker_slug="nissan", chassis_deep_first=True)
    assert fourth is not None
    assert fourth["url"].endswith("/t31/diag")
    assert fourth["chassis_code"] == "T31"


def test_claim_prefers_in_progress_chassis(tmp_path: Path) -> None:
    from data_pipeline.megazip.state import claim_next_url, enqueue_url, init_db, mark_url

    db = tmp_path / "state.db"
    init_db(db)
    enqueue_url(
        db,
        "https://ex/t31/sec1",
        page_type="section_list",
        maker_slug="nissan",
        chassis_code="T31",
    )
    enqueue_url(
        db,
        "https://ex/d40/sec1",
        page_type="section_list",
        maker_slug="nissan",
        chassis_code="D40",
    )
    enqueue_url(
        db,
        "https://ex/t31/diag1",
        page_type="diagram",
        maker_slug="nissan",
        chassis_code="T31",
    )

    # Seed VISITED deep progress on T31 so focus prefers T31 over D40.
    mark_url(db, "https://ex/t31/sec1", ok=True)
    # Re-enqueue as visited already — claim should pick T31 diagram before D40 section.
    claimed = claim_next_url(db, maker_slug="nissan", chassis_deep_first=True)
    assert claimed is not None
    assert claimed["chassis_code"] == "T31"
    assert claimed["page_type"] == "diagram"


def test_prune_chassis_html_requires_parsed(tmp_path: Path) -> None:
    from data_pipeline.megazip.config import MakerPaths
    from data_pipeline.megazip.crawl import prune_chassis_html_cache
    from data_pipeline.megazip.parse_html import cache_key
    from data_pipeline.megazip.state import enqueue_url, init_db, mark_url, upsert_parsed

    root = tmp_path / "nissan"
    cache = root / "cache"
    cache.mkdir(parents=True)
    db = root / "megazip_state.db"
    init_db(db)
    url = "https://ex/t31/diag"
    enqueue_url(db, url, page_type="diagram", maker_slug="nissan", chassis_code="T31")
    mark_url(db, url, ok=True)
    html_path = cache / f"{cache_key(url)}.html"
    html_path.write_text("<html/>", encoding="utf-8")
    paths = MakerPaths(
        maker="Nissan",
        slug="nissan",
        root=root,
        state_db=db,
        cache_dir=cache,
        bundle_dir=root / "bundle",
        diagrams_dir=root / "diagrams",
        meta_json=root / "meta.json",
    )

    skipped = prune_chassis_html_cache(paths, ["T31"], require_parsed=True)
    assert skipped["deleted"] == 0
    assert html_path.is_file()

    upsert_parsed(db, url, "diagram", "nissan", {"parts": []})
    deleted = prune_chassis_html_cache(paths, ["T31"], require_parsed=True)
    assert deleted["deleted"] == 1
    assert not html_path.is_file()


def test_should_retain_html_weak_diagram() -> None:
    from data_pipeline.megazip.crawl import should_retain_html

    assert should_retain_html("diagram", {"title": "", "parts": [], "parts_table": []}) is True
    assert (
        should_retain_html(
            "diagram",
            {
                "title": "Engine",
                "diagram_kind": "exploded_diagram",
                "parts": [{"oem_part_number": "1"} for _ in range(5)],
                "parts_table": [{}],
                "image_width": 800,
                "image_height": 600,
            },
        )
        is False
    )
    assert should_retain_html("variant_list", {"variants": [{"chassis_code": "T31"}]}) is False
    assert should_retain_html("variant_list", {"variants": [{"chassis_code": ""}]}) is True


def test_attrs_audit_auto_in_quality_report() -> None:
    bundle = {
        "catalog_variants": [
            {
                "slug": "v1",
                "model_slug": "m1",
                "chassis_code": "T31",
                "engine_code": "MR20DE",
                "year_label": "2007",
            },
            {
                "slug": "v2",
                "model_slug": "m1",
                "chassis_code": "T31",
                "engine_code": "",
                "year_label": "",
            },
        ],
        "catalog_diagrams": [
            {
                "storage_path": "megazip/nissan/x.png",
                "diagram_kind": "exploded_diagram",
                "hotspot_count": 8,
                "model_slug": "m1",
                "variant_slug": "v1",
            }
        ],
        "catalog_diagram_parts": [],
        "pnc_categories": [{"pnc_code": "MZ1", "category_name": "Engine"}],
        "part_fitment": [
            {
                "oem_part_number": "09113-08061",
                "pnc_code": "MZ1",
                "chassis_code": "T31",
                "engine_code": "MR20DE",
                "bbox_x": 0.1,
                "diagram_path": "megazip/nissan/x.png",
            }
            for _ in range(3)
        ],
    }
    meta = bundle_quality_report(bundle)
    assert meta["attrs_audit"]["variants_missing_engine"] == 1
    assert meta["attrs_audit"]["attrs_ok"] is True
    assert meta["attrs_ok"] is True
    assert_publishable(meta, strict=True)
