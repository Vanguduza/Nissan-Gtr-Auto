#!/usr/bin/env python3
import sqlite3
import shutil
from pathlib import Path

src = Path(r"C:\Users\j\AppData\Local\Temp\catalog_apk.db")
# also copy wal/shm if present from adb pull
con = sqlite3.connect(f"file:{src}?mode=ro", uri=True)
print("tables:", [r[0] for r in con.execute("select name from sqlite_master where type='table'")])
cols = [r[1] for r in con.execute("pragma table_info(jobs)")]
print("cols", cols)
for row in con.execute(
    "select id, status, desiredState, errorMessage, chassisCodesCsv, updatedAt from jobs order by updatedAt desc limit 30"
):
    print(row)
