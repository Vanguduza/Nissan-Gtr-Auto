#!/usr/bin/env python3
import json
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "site_catalogs"
for f in sorted(p.glob("*.json")):
    d = json.loads(f.read_text(encoding="utf-8"))
    assert d.get("id") and d.get("makers")
    for m in d["makers"]:
        assert m.get("name") and m.get("slug") and m.get("models") is not None
        assert m["models"], f"{f.name} maker {m.get('name')} has no models"
        for mo in m["models"]:
            assert mo.get("display_name") and mo.get("slug")
            assert isinstance(mo.get("chassis"), list) and mo["chassis"]
            for c in mo["chassis"]:
                assert c.get("code"), f"{f.name} empty chassis code"
    n_models = sum(len(m["models"]) for m in d["makers"])
    n_ch = sum(len(x["chassis"]) for m in d["makers"] for x in m["models"])
    print(f"OK {f.name}: makers={len(d['makers'])} models={n_models} chassis={n_ch}")
