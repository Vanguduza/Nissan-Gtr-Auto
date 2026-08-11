"""Seed replacement queue from dead/unfinished models (no worker restarts)."""

from __future__ import annotations

import subprocess
from pathlib import Path

from data_pipeline.megazip.config import MegazipConfig, build_maker_paths
from data_pipeline.megazip.replacement_queue import (
    load_queue,
    pending_by_model,
    seed_from_dead_models,
)


def main() -> int:
    ps = r"""
Get-CimInstance Win32_Process -Filter "name='python.exe'" |
  Where-Object { $_.CommandLine -match 'megazip_crawl_worker' } |
  ForEach-Object {
    if ($_.CommandLine -match '--models\s+(\S+)') { $Matches[1].Split(',')[0] }
  }
"""
    raw = subprocess.check_output(
        ["powershell", "-NoProfile", "-Command", ps],
        text=True,
        stderr=subprocess.DEVNULL,
    )
    live = {m.strip() for m in raw.splitlines() if m.strip()}
    print("live", sorted(live))

    paths = build_maker_paths("Nissan", Path("out/megazip"), MegazipConfig.load())
    pending = pending_by_model(paths.state_db)
    prefer = [
        m
        for m in (
            "caravan-homy-2073",
            "auster-stanza-2071",
            "200sx-2128",
            "altima-2135",
        )
        if m in pending
    ]
    prefer += [m for m in pending if "bluebird" in m and m not in prefer]
    seed = seed_from_dead_models(
        Path("out/megazip"),
        paths.state_db,
        live_models=live,
        prefer=prefer,
    )
    print("prefer_front", seed["prefer_front"][:12])
    print("queue_len", len(seed["queued"]))
    print("first12", seed["queued"][:12])
    print("file", load_queue(Path("out/megazip"))[:12])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
