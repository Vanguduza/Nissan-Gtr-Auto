#!/usr/bin/env python3
"""Decode base64 ADB dumps of catalog_apk.db (+ wal) and print recent jobs."""
from __future__ import annotations

import base64
import re
import sqlite3
import sys
from pathlib import Path

tmp = Path(sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\j\AppData\Local\Temp\catalog_apk_db")


def dec(name: str, out: str) -> None:
    raw = tmp.joinpath(name).read_text(encoding="utf-8", errors="ignore")
    raw = re.sub(r"[^A-Za-z0-9+/=]", "", "".join(raw.split()))
    tmp.joinpath(out).write_bytes(base64.b64decode(raw))
    print(out, tmp.joinpath(out).stat().st_size)


dec("db.b64", "clean.db")
wal = tmp / "wal.b64"
if wal.exists() and wal.stat().st_size > 8:
    dec("wal.b64", "clean.db-wal")

con = sqlite3.connect(str(tmp / "clean.db"))
try:
    con.execute("PRAGMA wal_checkpoint(FULL)")
except Exception as e:  # noqa: BLE001
    print("checkpoint", e)
con.close()

con = sqlite3.connect(f"file:{tmp / 'clean.db'}?mode=ro", uri=True)
print("count", con.execute("select count(*) from jobs").fetchone())
q = (
    "select id, profileId, status, desiredState, chassisCodesCsv, modelSlug, maker, "
    "substr(coalesce(errorMessage,''),1,80), updatedAt "
    "from jobs order by updatedAt desc limit 40"
)
for row in con.execute(q):
    print(row)
print("counts", list(con.execute("select status, desiredState, count(*) from jobs group by 1,2")))
