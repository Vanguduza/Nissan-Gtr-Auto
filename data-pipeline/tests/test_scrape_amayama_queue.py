from __future__ import annotations

from pathlib import Path

from data_pipeline.scrape_amayama import (
    claim_next_url,
    enqueue_url,
    init_db,
    mark_visit_result,
    pending_count,
    requeue_failed,
    set_url_status,
)


def test_atomic_claim_is_exclusive(tmp_path: Path) -> None:
    db = tmp_path / "state.db"
    init_db(db)
    enqueue_url(db, "https://www.amayama.com/en/catalogs/nissan/a", hierarchy_level=1)
    enqueue_url(db, "https://www.amayama.com/en/catalogs/nissan/b", hierarchy_level=2)
    assert pending_count(db) == 2

    first = claim_next_url(db)
    second = claim_next_url(db)
    third = claim_next_url(db)

    assert first is not None
    assert second is not None
    assert first[0] != second[0]
    # BFS: lower hierarchy_level first
    assert first[0].endswith("/a")
    assert third is None
    assert pending_count(db) == 0

    set_url_status(db, first[0], "VISITED")
    set_url_status(db, second[0], "FAILED")


def test_failed_auto_retry_then_permanent(tmp_path: Path) -> None:
    db = tmp_path / "state.db"
    init_db(db)
    url = "https://www.amayama.com/en/catalogs/nissan/navara"
    enqueue_url(db, url, hierarchy_level=1)

    claimed = claim_next_url(db)
    assert claimed is not None
    status = mark_visit_result(db, url, ok=False, max_attempts=2)
    assert status == "PENDING"

    claimed2 = claim_next_url(db)
    assert claimed2 is not None
    status2 = mark_visit_result(db, url, ok=False, max_attempts=2)
    assert status2 == "FAILED"

    assert requeue_failed(db) == 1
    assert pending_count(db) == 1
