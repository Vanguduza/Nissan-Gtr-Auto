from __future__ import annotations

import json
import sqlite3
from pathlib import Path

from data_pipeline.cache_parse_worker import (
    apply_identity_to_payloads,
    backfill_scraped_for_vid,
    enqueue_new_from_cache,
    init_parse_db,
    identity_hints,
    upsert_vehicle_identity,
)
from data_pipeline.parse_partsouq_html import (
    normalize_chassis_code,
    parse_partsouq_vehicle_html,
    vid_from_url,
)

VEHICLE_HTML = """
<html><body>
<table class="vehicle-tg">
<tr>
  <td data-title="Brand">NISSAN</td>
  <td data-title="Name">QASHQAI+2</td>
  <td data-title="Grade">ST</td>
  <td data-title="Market">Australia (RHD)</td>
  <td data-title="Model">JJ10E</td>
  <td data-title="Modelyearfrom">04.2010</td>
  <td data-title="Options">WHEEL DRIVE:2WD</td>
  <td data-title="Transmission">M-CVT</td>
</tr>
</table>
<a href="/en/catalog/genuine/parts?c=Nissan&amp;vid=190773&amp;gid=1000">parts</a>
<p>Engine MR20DE category listing</p>
</body></html>
"""

PARTS_URL = (
    "https://partsouq.com/en/catalog/genuine/parts?c=Nissan&vid=190773&gid=1000"
)
VEHICLE_URL = (
    "https://partsouq.com/en/catalog/genuine/vehicle?c=Nissan&vid=190773&ssd=abc"
)


def test_normalize_chassis_strips_market_letter() -> None:
    assert normalize_chassis_code("JJ10E") == "JJ10"
    assert normalize_chassis_code("D40") == "D40"


def test_parse_vehicle_html_identity() -> None:
    identity = parse_partsouq_vehicle_html(VEHICLE_HTML, source_url=VEHICLE_URL)
    assert identity is not None
    assert identity["vid"] == "190773"
    assert identity["chassis_code"] == "JJ10"
    assert identity["model_variant"] == "QASHQAI+2"
    assert identity["grade"] == "ST"
    assert identity["market"] == "Australia (RHD)"
    assert identity["production_year"] == 2010
    assert identity["engine_code"] == "MR20DE"


def test_identity_enrichment_adds_vin_prefix() -> None:
    identity = parse_partsouq_vehicle_html(VEHICLE_HTML, source_url=VEHICLE_URL)
    assert identity is not None
    hints = identity_hints(identity)
    assert hints["chassis_code"] == "JJ10"
    assert hints["vin_prefix"] == "SJNFBAJ10"


def test_apply_identity_stamps_parts_payload() -> None:
    payloads = [
        {
            "vehicle": {
                "vid": "190773",
                "model_variant": "QASHQAI+2",
                "production_year": 2010,
            },
            "parts": [{"oem_part_number": "123"}],
        }
    ]
    hints = identity_hints(
        {
            "vid": "190773",
            "chassis_code": "JJ10",
            "model_variant": "QASHQAI+2",
            "engine_code": "MR20DE",
            "production_year": 2010,
        }
    )
    stamped = apply_identity_to_payloads(payloads, hints)
    vehicle = stamped[0]["vehicle"]
    assert vehicle["chassis_code"] == "JJ10"
    assert vehicle["vin_prefix"] == "SJNFBAJ10"
    assert vehicle["engine_code"] == "MR20DE"


def test_upsert_and_backfill(tmp_path: Path) -> None:
    parse_db = tmp_path / "parse.db"
    crawl_db = tmp_path / "crawl.db"
    init_parse_db(parse_db)

    conn = sqlite3.connect(crawl_db)
    conn.execute(
        """
        CREATE TABLE scraped_data (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_url TEXT,
            payload TEXT,
            vehicle_context TEXT
        )
        """
    )
    payload = {
        "vehicle": {"vid": "190773", "model_variant": "QASHQAI+2"},
        "parts": [],
    }
    ctx = {"vid": "190773", "model_variant": "QASHQAI+2", "engine_code": "FS12PX"}
    conn.execute(
        "INSERT INTO scraped_data (source_url, payload, vehicle_context) VALUES (?, ?, ?)",
        (PARTS_URL, json.dumps(payload), json.dumps(ctx)),
    )
    conn.commit()
    conn.close()

    identity = parse_partsouq_vehicle_html(VEHICLE_HTML, source_url=VEHICLE_URL)
    assert identity is not None
    hints = upsert_vehicle_identity(parse_db, identity)
    assert hints["vin_prefix"] == "SJNFBAJ10"

    updated = backfill_scraped_for_vid(crawl_db, parse_db, "190773", hints)
    assert updated == 1

    conn = sqlite3.connect(crawl_db)
    row = conn.execute(
        "SELECT payload, vehicle_context FROM scraped_data"
    ).fetchone()
    conn.close()
    new_payload = json.loads(row[0])
    new_ctx = json.loads(row[1])
    assert new_payload["vehicle"]["chassis_code"] == "JJ10"
    assert new_payload["vehicle"]["vin_prefix"] == "SJNFBAJ10"
    assert new_ctx["chassis_code"] == "JJ10"
    assert new_ctx["vin_prefix"] == "SJNFBAJ10"
    assert new_ctx["engine_code"] == "MR20DE"


def test_enqueue_rescans_missing_and_vehicle(tmp_path: Path) -> None:
    parse_db = tmp_path / "parse.db"
    crawl_db = tmp_path / "crawl.db"
    cache_dir = tmp_path / "cache"
    cache_dir.mkdir()
    init_parse_db(parse_db)

    import hashlib

    conn = sqlite3.connect(crawl_db)
    conn.execute(
        "CREATE TABLE queue (url TEXT PRIMARY KEY, status TEXT)"
    )
    conn.execute(
        "INSERT INTO queue (url, status) VALUES (?, 'VISITED')",
        (VEHICLE_URL,),
    )
    conn.commit()
    conn.close()

    digest = hashlib.sha256(VEHICLE_URL.encode()).hexdigest()
    (cache_dir / f"{digest}.html").write_text(VEHICLE_HTML, encoding="utf-8")

    added = enqueue_new_from_cache(
        crawl_db=crawl_db, cache_dir=cache_dir, parse_db=parse_db
    )
    assert added == 1

    # Mark DONE without identity — should re-queue on next enqueue
    conn = sqlite3.connect(parse_db)
    conn.execute(
        "UPDATE parse_queue SET status = 'DONE', updated_at = 't' WHERE url = ?",
        (VEHICLE_URL,),
    )
    conn.commit()
    conn.close()

    added2 = enqueue_new_from_cache(
        crawl_db=crawl_db, cache_dir=cache_dir, parse_db=parse_db
    )
    assert added2 == 1
    conn = sqlite3.connect(parse_db)
    status = conn.execute(
        "SELECT status FROM parse_queue WHERE url = ?", (VEHICLE_URL,)
    ).fetchone()[0]
    conn.close()
    assert status == "PENDING"


def test_vid_from_url() -> None:
    assert vid_from_url(VEHICLE_URL) == "190773"
    assert vid_from_url("https://partsouq.com/en/catalog/genuine/vehicle") is None
