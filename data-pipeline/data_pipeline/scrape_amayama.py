"""Backward-compatible entrypoint — prefer amayama_catalog_auto (Patchright).

    python -m data_pipeline.amayama_catalog_auto
    python -m data_pipeline.scrape_amayama  # same pipeline
"""

from __future__ import annotations

from data_pipeline.amayama_catalog_auto import (  # noqa: F401
    claim_next_url,
    enqueue_url,
    init_db,
    main,
    mark_visit_result,
    pending_count,
    requeue_failed,
    run_crawl,
    run_transform,
    set_url_status,
    store_payload,
    write_checkpoint,
)

if __name__ == "__main__":
    raise SystemExit(main())
