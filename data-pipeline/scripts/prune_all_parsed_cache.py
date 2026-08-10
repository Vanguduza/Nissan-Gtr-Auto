"""Emergency disk free: delete VISITED HTML cache that already has parsed_pages."""

from __future__ import annotations

import shutil
import sqlite3
from pathlib import Path

from data_pipeline.megazip.config import MegazipConfig, build_maker_paths
from data_pipeline.megazip.crawl import prune_model_html_cache


def main() -> None:
    config = MegazipConfig.load()
    paths = build_maker_paths("Nissan", Path("out/megazip"), config)
    conn = sqlite3.connect(paths.state_db)
    try:
        models = [
            r[0]
            for r in conn.execute(
                "SELECT DISTINCT model_slug FROM queue WHERE model_slug != ''"
            ).fetchall()
        ]
    finally:
        conn.close()
    print(f"models={len(models)}", flush=True)
    stats = prune_model_html_cache(paths, models, dry_run=False)
    print(f"pruned={stats}", flush=True)
    free = shutil.disk_usage("C:/").free / 1e9
    print(f"free_gb={free:.2f}", flush=True)


if __name__ == "__main__":
    main()
