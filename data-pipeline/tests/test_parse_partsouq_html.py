from __future__ import annotations

from data_pipeline.parse_partsouq_html import parse_partsouq_parts_html
from data_pipeline.transform_amayama import is_catalog_payload, transform_payload
from data_pipeline.validate import validate_bundle

SAMPLE = """
<html><body>
<div id="zoom_container_1" class="unit-image" style="width: 900px; height: 800px;">
  <img alt="NISSAN 200SX 07.1994 THROTTLE CHAMBER" class="drag"
       src="/assets/tesseract/assets/global/NISSAN201809/source/AR/demo.gif">
  <div class="landmarks">
    <div class="item lable lable-single "
         data-title="1613273C10 NUT"
         data-codeonimage="16132PA"
         data-size="75,17"
         data-position="48,226"></div>
    <div class="item lable lable-single "
         data-title="16122V5200 BOLT-CHAMBER"
         data-codeonimage="16292"
         data-size="56,17"
         data-position="71,468"></div>
  </div>
</div>
<table>
  <tr class="part-search-tr">
    <td class="oem"><a href="/en/search/all?q=1613273C10&amp;qty=1">1613273C10</a></td>
    <td>NUT</td>
    <td class="codeonimage">16132PA</td>
    <td class="hidden-xs">01</td>
    <td class="hidden-xs">SR20DET</td>
    <td class="hidden-xs">07.1994 - ...</td>
  </tr>
</table>
</body></html>
"""


def test_parse_hotspots_and_parts() -> None:
    url = (
        "https://partsouq.com/en/catalog/genuine/parts?"
        "c=Nissan&vid=1&gid=2&cid=6&cname=POWER+TRAIN"
    )
    payloads = parse_partsouq_parts_html(SAMPLE, source_url=url)
    assert len(payloads) == 1
    payload = payloads[0]
    assert is_catalog_payload(payload)
    assert payload["category_name"] == "POWER TRAIN"
    assert payload["subcategory_name"] == "THROTTLE CHAMBER"
    assert payload["image_url"].endswith("demo.gif")
    assert payload["vehicle"]["model_variant"] == "200SX"
    assert payload["vehicle"]["engine_code"] == "SR20DET"
    assert len(payload["parts"]) == 2
    first = payload["parts"][0]
    assert first["oem_part_number"] == "1613273C10"
    assert first["left"] == 48
    assert first["top"] == 226
    assert first["engine_code"] == "SR20DET"

    bundle = transform_payload(payload, source_url=payload["source_url"])
    assert len(bundle["part_fitment"]) >= 1
    fit = bundle["part_fitment"][0]
    assert fit["oem_part_number"] == "16132-73C10"
    assert fit["pnc_code"] == "16132PA"
    assert "bbox_x" in fit
    assert 0.0 <= fit["bbox_x"] <= 1.0
    assert len(bundle["diagram_assets"]) == 1
    assert any(p["pnc_code"] == "16132PA" for p in bundle["pnc_categories"])
    assert any(p["pnc_code"] == "16292" for p in bundle["pnc_categories"])
    pnc_row = next(p for p in bundle["pnc_categories"] if p["pnc_code"] == "16132PA")
    assert pnc_row["category_name"] == "POWER TRAIN"
    assert pnc_row["subcategory_name"] == "THROTTLE CHAMBER"
    assert bundle["_oem_display_names"]["16132-73C10"] == "NUT"
    validate_bundle(bundle)


def test_empty_html_returns_no_payloads() -> None:
    assert parse_partsouq_parts_html("<html><body>hello</body></html>") == []
