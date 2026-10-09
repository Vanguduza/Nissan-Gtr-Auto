#!/usr/bin/env python3
"""Drop megazip makers that have zero models (ATV/marine hubs without car trees)."""
from __future__ import annotations

import json
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "site_catalogs" / "megazip.json"
data = json.loads(path.read_text(encoding="utf-8"))
before = len(data["makers"])
data["makers"] = [m for m in data["makers"] if m.get("models")]
path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"megazip makers {before} -> {len(data['makers'])}")
for m in data["makers"]:
    n_ch = sum(len(x.get("chassis") or []) for x in m["models"])
    print(f"  {m['name']}: models={len(m['models'])} chassis={n_ch}")
