from __future__ import annotations

from data_pipeline.amayama_catalog_auto import ScrapeConfig, claim_next_url, queue_status_counts
from data_pipeline.scrape_amayama import enqueue_url, init_db


def test_claim_deep_first(tmp_path) -> None:
    db = tmp_path / "q.db"
    init_db(db)
    enqueue_url(db, "https://partsouq.com/en/catalog/genuine/locate?c=Nissan", hierarchy_level=0)
    enqueue_url(
        db,
        "https://partsouq.com/en/catalog/genuine/unit?c=Nissan&uid=1",
        hierarchy_level=5,
    )
    url, _ = claim_next_url(db, deep_first=True) or (None, None)
    assert url and "unit" in url


def test_claim_hybrid_prefers_vehicle_over_parts(tmp_path) -> None:
    db = tmp_path / "q.db"
    init_db(db)
    enqueue_url(
        db,
        "https://partsouq.com/en/catalog/genuine/unit?c=Nissan&uid=99",
        hierarchy_level=5,
    )
    enqueue_url(
        db,
        "https://partsouq.com/en/catalog/genuine/vehicle?c=Nissan&vid=42",
        hierarchy_level=2,
    )
    url, _ = claim_next_url(db, mode="hybrid") or (None, None)
    assert url and "/vehicle" in url


def test_claim_hybrid_falls_back_to_deep_first(tmp_path) -> None:
    db = tmp_path / "q.db"
    init_db(db)
    enqueue_url(db, "https://partsouq.com/en/catalog/genuine/locate?c=Nissan", hierarchy_level=0)
    enqueue_url(
        db,
        "https://partsouq.com/en/catalog/genuine/unit?c=Nissan&uid=7",
        hierarchy_level=5,
    )
    url, _ = claim_next_url(db, mode="hybrid") or (None, None)
    assert url and "unit" in url


def test_config_until_complete_defaults() -> None:
    cfg = ScrapeConfig.load()
    assert cfg.run_until_complete is True
    assert cfg.completion_max_rounds >= 1
    assert cfg.queue_deep_first is True
    assert cfg.resolved_queue_mode() == "hybrid"


def test_queue_status_counts(tmp_path) -> None:
    db = tmp_path / "q.db"
    init_db(db)
    enqueue_url(db, "https://partsouq.com/en/catalog/genuine/locate?c=Nissan", hierarchy_level=0)
    assert queue_status_counts(db).get("PENDING") == 1
