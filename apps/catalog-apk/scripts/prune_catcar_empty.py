#!/usr/bin/env python3
"""Remove makers with zero models from catcar preset."""
from __future__ import annotations

import json
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "site_catalogs" / "catcar.json"
data = json.loads(path.read_text(encoding="utf-8"))
before = len(data["makers"])
data["makers"] = [m for m in data["makers"] if m.get("models")]
path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"catcar makers {before} -> {len(data['makers'])}")
