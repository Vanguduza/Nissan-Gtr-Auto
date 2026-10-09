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
    assert page.payload["engine_code"] == "2AZFE"
    assert parts[0]["engine_code"] == "2AZFE"
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


def test_pcdb_enrich_stems_megazip_for_suffix() -> None:
    from data_pipeline.megazip.enrich_pcdb import epc_category_stem

    title = "WIRING FOR 2004 - 2011 NISSAN ALTIMA L31 | U.S.A. SALES REGION"
    assert epc_category_stem(title) == "WIRING"
    bundle = {
        "pnc_categories": [{"pnc_code": "MZ2", "category_name": title}],
        "part_fitment": [],
    }
    stats = enrich_pcdb(bundle)
    assert stats["pcdb_mapped"] == 1
    assert bundle["pnc_categories"][0]["pcdb_part_type_id"] is not None


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

    bundle = {
        "catalog_variants": [
            {"slug": "v1", "model_slug": "m1", "chassis_code": "R35"},
            {"slug": "v2", "model_slug": "m1", "chassis_code": "R34"},
        ],
        "catalog_diagrams": [
            {
                "storage_path": "epc/nissan/a.png",
                "diagram_kind": "exploded_diagram",
                "hotspot_count": 8,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
            {
                "storage_path": "epc/nissan/b.png",
                "diagram_kind": "parts_list_raster",
                "hotspot_count": 3,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
        ],
        "catalog_diagram_parts": [
            {"diagram_path": "epc/nissan/b.png", "oem_part_number": "12345-ABCDE"},
        ],
        "part_fitment": [
            {
                "oem_part_number": "09113-08061",
                "chassis_code": "R35",
                "bbox_x": 0.1,
                "diagram_path": "epc/nissan/a.png",
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
                "storage_path": "epc/nissan/raster.png",
                "diagram_kind": "parts_list_raster",
                "model_slug": "m1",
                "variant_slug": "v1",
            },
        ],
        "catalog_diagram_parts": [
            {"diagram_path": "epc/nissan/raster.png", "oem_part_number": "12345-ABCDE"},
        ],
        "part_fitment": [
            {
                "oem_part_number": "12345-ABCDE",
                "chassis_code": "R35",
                "diagram_path": "epc/nissan/raster.png",
            },
        ],
        "vehicle_master": [{"chassis_code": "R35", "model_variant": "GT-R R35"}],
        "pnc_categories": [{"pnc_code": "MZ1", "category_name": "Gasket"}],
        "diagram_assets": [{"storage_path": "epc/nissan/raster.png", "source_url": "http://x"}],
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
                "storage_path": "epc/toyota/x.png",
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
                "diagram_path": "epc/toyota/x.png",
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
                "storage_path": "epc/nissan/good.png",
                "diagram_kind": "exploded_diagram",
                "hotspot_count": 8,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
            {
                "storage_path": "epc/nissan/bad.png",
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
                "diagram_path": "epc/nissan/good.png",
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
                "storage_path": "epc/nissan/amb.png",
                "diagram_kind": "ambiguous",
                "hotspot_count": 1,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
            {
                "storage_path": "epc/nissan/exp.png",
                "diagram_kind": "exploded_diagram",
                "hotspot_count": 8,
                "model_slug": "m1",
                "variant_slug": "v1",
            },
        ],
        "catalog_diagram_parts": [
            {"diagram_path": "epc/nissan/amb.png", "oem_part_number": "12345-ABCDE"},
        ],
        "part_fitment": [
            {
                "oem_part_number": "09113-08061",
                "chassis_code": "T31",
                "bbox_x": 0.1,
                "diagram_path": "epc/nissan/exp.png",
            }
            for _ in range(3)
        ],
        "vehicle_master": [{"chassis_code": "T31"}],
        "pnc_categories": [{"pnc_code": "MZ1", "category_name": "Gasket"}],
        "diagram_assets": [],
    }
    passing = _passing_diagram_paths(bundle)
    assert "epc/nissan/amb.png" in passing
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


def test_self_heal_requeues_missing_cache_and_empty_hub(tmp_path) -> None:
    """Permanent guard: a lost cache file or a 0-model hub must self-recover.

    Regression for the X-Trail-only freeze: a stale ``VISITED`` hub that parsed
    to zero models (and whose cache file went missing) was never re-fetched, so
    model fan-out stayed empty for the whole maker.
    """
    import sqlite3

    from data_pipeline.megazip import state
    from data_pipeline.megazip.config import MegazipConfig, build_maker_paths
    from data_pipeline.megazip.crawl import self_heal_queue
    from data_pipeline.megazip.parse_html import cache_key

    config = MegazipConfig.load()
    paths = build_maker_paths("Nissan", tmp_path, config)
    state.init_db(paths.state_db)
    paths.cache_dir.mkdir(parents=True, exist_ok=True)
    hub_url = config.hub_url("Nissan")

    # Hub: cache present but parsed to zero models -> must be re-queued.
    state.enqueue_url(paths.state_db, hub_url, page_type="maker_hub", maker_slug=paths.slug)
    state.mark_url(paths.state_db, hub_url, ok=True)
    hub_cache = paths.cache_dir / f"{cache_key(hub_url)}.html"
    hub_cache.write_text("<html></html>", encoding="utf-8")
    state.save_cache(paths.state_db, hub_url, str(hub_cache), "hash")
    state.upsert_parsed(paths.state_db, hub_url, "maker_hub", paths.slug, {"models": []})

    # Good page: cache present -> must stay VISITED.
    good_url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/x-trail-2064/t30/s/p-1"
    state.enqueue_url(paths.state_db, good_url, page_type="diagram", maker_slug=paths.slug)
    state.mark_url(paths.state_db, good_url, ok=True)
    good_cache = paths.cache_dir / f"{cache_key(good_url)}.html"
    good_cache.write_text("<html></html>", encoding="utf-8")
    state.save_cache(paths.state_db, good_url, str(good_cache), "hash")

    # Lost page: visited but cache file missing, no parse -> must be re-queued.
    lost_url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/x-trail-2064/t30/s/p-2"
    state.enqueue_url(paths.state_db, lost_url, page_type="diagram", maker_slug=paths.slug)
    state.mark_url(paths.state_db, lost_url, ok=True)

    # Parsed-only page: cache deleted after successful parse -> must stay VISITED.
    parsed_url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/x-trail-2064/t30/s/p-3"
    state.enqueue_url(paths.state_db, parsed_url, page_type="diagram", maker_slug=paths.slug)
    state.mark_url(paths.state_db, parsed_url, ok=True)
    state.upsert_parsed(
        paths.state_db,
        parsed_url,
        "diagram",
        paths.slug,
        {"image_url": "https://cdn.example/p3.png", "hotspots": []},
    )

    stats = self_heal_queue(paths, hub_url)
    assert stats["hub_reset"] == 1
    assert stats["missing_cache_requeued"] == 1
    assert stats["missing_cache_kept_parsed"] == 1

    conn = sqlite3.connect(paths.state_db)
    try:
        statuses = dict(conn.execute("SELECT url, status FROM queue").fetchall())
    finally:
        conn.close()
    assert statuses[hub_url] == "PENDING"
    assert statuses[lost_url] == "PENDING"
    assert statuses[good_url] == "VISITED"
    assert statuses[parsed_url] == "VISITED"


def test_prune_model_html_cache_requires_parsed(tmp_path) -> None:
    from data_pipeline.megazip import state
    from data_pipeline.megazip.config import MegazipConfig, build_maker_paths
    from data_pipeline.megazip.crawl import prune_model_html_cache
    from data_pipeline.megazip.parse_html import cache_key

    config = MegazipConfig.load()
    paths = build_maker_paths("Nissan", tmp_path, config)
    state.init_db(paths.state_db)
    paths.cache_dir.mkdir(parents=True, exist_ok=True)

    keep_url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/serena-2090/c25/s/p-1"
    drop_url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/serena-2090/c25/s/p-2"
    other_url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/frontier-2140/d40/s/p-1"

    for url, model, payload in (
        (keep_url, "serena-2090", None),
        (drop_url, "serena-2090", {"image_url": "https://cdn.example/p2.png"}),
        (other_url, "frontier-2140", {"image_url": "https://cdn.example/p3.png"}),
    ):
        state.enqueue_url(
            paths.state_db,
            url,
            page_type="diagram",
            maker_slug=paths.slug,
            model_slug=model,
        )
        state.mark_url(paths.state_db, url, ok=True)
        cache = paths.cache_dir / f"{cache_key(url)}.html"
        cache.write_text("<html></html>", encoding="utf-8")
        if payload is not None:
            state.upsert_parsed(paths.state_db, url, "diagram", paths.slug, payload)

    stats = prune_model_html_cache(paths, ["serena-2090"])
    assert stats["deleted"] == 1
    assert stats["skipped_no_parsed"] == 1
    assert (paths.cache_dir / f"{cache_key(drop_url)}.html").is_file() is False
    assert (paths.cache_dir / f"{cache_key(keep_url)}.html").is_file()
    assert (paths.cache_dir / f"{cache_key(other_url)}.html").is_file()


def test_claim_next_url_model_slug_filter(tmp_path) -> None:
    """Parallel workers must only claim URLs for their assigned models."""
    from data_pipeline.megazip import state

    db = tmp_path / "q.db"
    state.init_db(db)
    state.enqueue_url(
        db,
        "https://example/a",
        page_type="diagram",
        maker_slug="nissan",
        model_slug="pathfinder-2142",
    )
    state.enqueue_url(
        db,
        "https://example/b",
        page_type="diagram",
        maker_slug="nissan",
        model_slug="frontier-2140",
    )
    claimed = state.claim_next_url(
        db,
        maker_slug="nissan",
        model_slugs=frozenset({"pathfinder-2142"}),
    )
    assert claimed is not None
    assert claimed["model_slug"] == "pathfinder-2142"
    assert claimed["url"] == "https://example/a"
    assert state.pending_count(db, model_slugs=frozenset({"pathfinder-2142"})) == 0
    assert state.pending_count(db, model_slugs=frozenset({"frontier-2140"})) == 1


def test_worker_leases_exclude_from_main_claim(tmp_path) -> None:
    """Main crawler must skip models leased by workers (no double-work)."""
    from data_pipeline.megazip import state

    db = tmp_path / "leases.db"
    state.init_db(db)
    state.enqueue_url(
        db,
        "https://example/frontier",
        page_type="diagram",
        maker_slug="nissan",
        model_slug="frontier-2140",
    )
    state.enqueue_url(
        db,
        "https://example/pathfinder",
        page_type="diagram",
        maker_slug="nissan",
        model_slug="pathfinder-2142",
    )
    acquired, blocked = state.acquire_model_leases(
        db, "worker-pathfinder", frozenset({"pathfinder-2142"})
    )
    assert acquired == frozenset({"pathfinder-2142"})
    assert blocked == {}

    # Second worker cannot steal the lease.
    acquired2, blocked2 = state.acquire_model_leases(
        db, "worker-other", frozenset({"pathfinder-2142"})
    )
    assert acquired2 == frozenset()
    assert blocked2["pathfinder-2142"] == "worker-pathfinder"

    # Main excludes leased pathfinder → gets frontier.
    claimed = state.claim_next_url(db, maker_slug="nissan", exclude_leased=True)
    assert claimed is not None
    assert claimed["model_slug"] == "frontier-2140"
    assert state.pending_count(db, exclude_leased=True) == 0
    assert state.leased_pending_count(db) == 1


def test_prepare_remaining_requeues_non_priority_variants(tmp_path) -> None:
    from data_pipeline.megazip import state
    from data_pipeline.megazip.config import MegazipConfig, build_maker_paths
    from data_pipeline.megazip.crawl import prepare_remaining_crawl

    paths = build_maker_paths("Nissan", tmp_path, MegazipConfig.load())
    state.init_db(paths.state_db)
    paths.root.mkdir(parents=True, exist_ok=True)
    state.upsert_parsed(
        paths.state_db,
        "https://example/hub",
        "maker_hub",
        "nissan",
        {
            "models": [
                {
                    "slug": "cube-2138",
                    "source_url": "https://example/nissan/cube-2138",
                }
            ]
        },
    )
    state.upsert_parsed(
        paths.state_db,
        "https://example/cube/variants",
        "variant_list",
        "nissan",
        {
            "model_slug": "cube-2138",
            "variants": [
                {
                    "slug": "z12-1",
                    "chassis_code": "Z12",
                    "source_url": "https://example/cube/z12",
                },
                {
                    "slug": "t31-1",
                    "chassis_code": "T31",
                    "source_url": "https://example/cube/t31",
                },
            ],
        },
    )
    stats = prepare_remaining_crawl(paths, priority_chassis=frozenset({"T31"}))
    assert stats["variants"] == 1
    assert state.pending_count(paths.state_db) >= 1
    claimed = state.claim_next_url(paths.state_db, maker_slug="nissan")
    assert claimed is not None
    assert "z12" in claimed["url"]


def test_load_visited_deep_chassis(tmp_path) -> None:
    from data_pipeline.megazip import state
    from data_pipeline.megazip.crawl import load_visited_deep_chassis

    db = tmp_path / "q.db"
    state.init_db(db)
    state.enqueue_url(
        db,
        "https://example/d22",
        page_type="diagram",
        maker_slug="nissan",
        model_slug="frontier-2140",
        chassis_code="D22",
    )
    state.mark_url(db, "https://example/d22", ok=True)
    codes = load_visited_deep_chassis(db)
    assert "D22" in codes


def test_transform_attaches_diagram_engine_even_if_diagram_parsed_first(tmp_path) -> None:
    """SQLite insert order must not drop engines (diagram before variant_list)."""
    from data_pipeline.megazip import state
    from data_pipeline.megazip.config import MegazipConfig, build_maker_paths
    from data_pipeline.megazip.transform import build_hierarchy_bundle

    paths = build_maker_paths("Toyota", tmp_path, MegazipConfig.load())
    state.init_db(paths.state_db)
    paths.root.mkdir(parents=True, exist_ok=True)

    # Intentionally upsert diagram BEFORE variant_list (bad physical order).
    state.upsert_parsed(
        paths.state_db,
        "https://example/toyota/camry/acv40/diagram",
        "diagram",
        "toyota",
        {
            "model_slug": "camry-vista-aurion-42430",
            "variant_slug": "acv40-55581",
            "section_slug": "standard-tool",
            "engine_code": "2AZFE",
            "title": "Standard Tool",
            "parts": [
                {
                    "oem_part_number": "09113-08061",
                    "pnc_code": "MZ1",
                    "chassis_code": "ACV40",
                    "engine_code": "2AZFE",
                }
            ],
        },
    )
    state.upsert_parsed(
        paths.state_db,
        "https://example/toyota/camry/variants",
        "variant_list",
        "toyota",
        {
            "model_slug": "camry-vista-aurion-42430",
            "variants": [
                {
                    "slug": "acv40-55581",
                    "chassis_code": "ACV40",
                    "frame": "ACV40",
                    "megazip_data_id": "1",
                    "engine_code": "",
                }
            ],
        },
    )

    bundle = build_hierarchy_bundle(paths, storage_prefix="epc/toyota")
    engines = {
        (r.get("chassis_code"), r.get("engine_code"), r.get("model_variant"))
        for r in bundle["vehicle_master"]
        if r.get("engine_code")
    }
    assert ("ACV40", "2AZFE", "Toyota CAMRY VISTA AURION 42430") in engines
    fit_engines = {f.get("engine_code") for f in bundle["part_fitment"]}
    assert "2AZFE" in fit_engines


def test_quality_report_includes_engine_coverage() -> None:
    from data_pipeline.megazip.quality import bundle_quality_report

    report = bundle_quality_report(
        {
            "catalog_variants": [
                {"model_slug": "m", "slug": "v1", "chassis_code": "T30"},
                {"model_slug": "m", "slug": "v2", "chassis_code": "T31"},
            ],
            "catalog_diagrams": [],
            "catalog_diagram_parts": [],
            "part_fitment": [],
            "pnc_categories": [],
            "vehicle_master": [
                {
                    "chassis_code": "T30",
                    "engine_code": "QR20DE",
                    "model_variant": "Nissan X TRAIL 2064",
                },
                {"chassis_code": "T31", "model_variant": "Nissan X TRAIL 2064"},
            ],
        }
    )
    assert report["vehicle_master_with_engine"] == 1
    assert report["variant_chassis_missing_engine_count"] == 1
    assert report["variant_chassis_missing_engine"] == ["T31"]


