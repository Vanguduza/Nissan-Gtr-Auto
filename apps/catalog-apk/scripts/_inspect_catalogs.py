import json
from pathlib import Path

p = Path(r"C:\Nissan GTR auto\apps\catalog-apk\app\src\main\assets\site_catalogs")
for f in sorted(p.glob("*.json")):
    d = json.loads(f.read_text(encoding="utf-8"))
    makers = d.get("makers") or []
    print(f"\n== {f.name} makers={len(makers)}")
    for m in makers:
        n = len(m.get("models") or [])
        ch = sum(len(x.get("chassis") or []) for x in (m.get("models") or []))
        print(f"  {m.get('name')}: models={n} chassis={ch}")
        if m.get("slug") == "nissan" and n:
            print("   models:", [x["display_name"] for x in m["models"][:10]])
            print("   chassis0:", (m["models"][0].get("chassis") or [])[:2])
