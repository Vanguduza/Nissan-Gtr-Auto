import sqlite3
import json
from pathlib import Path

db = Path(r"out/megazip/nissan/megazip_state.db")
c = sqlite3.connect(db)

print("=== Rule 1: crawl complete (0 pending diagrams, >0 visited) ===")
complete = []
for r in c.execute("""
  SELECT model_slug,
    SUM(CASE WHEN page_type='diagram' AND status='VISITED' THEN 1 ELSE 0 END) AS v,
    SUM(CASE WHEN page_type='diagram' AND status='PENDING' THEN 1 ELSE 0 END) AS p,
    SUM(CASE WHEN page_type='diagram' AND status='ERROR' THEN 1 ELSE 0 END) AS e,
    SUM(CASE WHEN page_type='diagram' AND status='PROCESSING' THEN 1 ELSE 0 END) AS pr
  FROM queue
  WHERE model_slug != ''
  GROUP BY model_slug
  HAVING p = 0 AND pr = 0 AND v > 0
  ORDER BY v DESC
"""):
    complete.append(r[0])
    print(f"  {r[0]:40} visited={r[1]:5} pending={r[2]} error={r[3]} processing={r[4]}")

print("\n=== Rule 2: in local publish bundle (catalog_models / variants) ===")
bundle = Path(r"out/megazip/nissan/bundle")
models_in_bundle = set()
variants_by_model = {}
if (bundle / "catalog_models.json").is_file():
    models = json.loads((bundle / "catalog_models.json").read_text(encoding="utf-8"))
    models_in_bundle = {m.get("slug") for m in models}
    print("  catalog_models:", sorted(models_in_bundle) or "(empty)")
else:
    print("  no catalog_models.json")
if (bundle / "catalog_variants.json").is_file():
    variants = json.loads((bundle / "catalog_variants.json").read_text(encoding="utf-8"))
    for v in variants:
        variants_by_model.setdefault(v.get("model_slug"), []).append(v.get("slug"))
    print("  variants by model:", {k: len(vs) for k, vs in variants_by_model.items()})
if (bundle / "quality_report.json").is_file():
    q = json.loads((bundle / "quality_report.json").read_text(encoding="utf-8"))
    print("  quality:", {k: q.get(k) for k in ["models", "variants_publishable", "publishable", "parts_complete_chassis"]})
print("  bundle mtime:", (bundle / "catalog_models.json").stat().st_mtime if (bundle / "catalog_models.json").is_file() else None)
import datetime
if (bundle / "catalog_models.json").is_file():
    print("  bundle date:", datetime.datetime.fromtimestamp((bundle / "catalog_models.json").stat().st_mtime))

print("\n=== Rule 2b: parsed_pages present (needed if cache deleted) ===")
for slug in complete:
    n = c.execute(
        "SELECT COUNT(1) FROM parsed_pages WHERE url LIKE ?",
        (f"%/{slug}/%",),
    ).fetchone()[0]
    print(f"  {slug:40} parsed_pages={n}")

print("\n=== Rule 3: self-heal allows missing cache when parsed exists ===")
# Check code presence
crawl = Path(r"data_pipeline/megazip/crawl.py").read_text(encoding="utf-8")
patched = "parsed_pages" in crawl[crawl.find("def self_heal_queue"):crawl.find("def self_heal_queue")+1200] and "missing cache" in crawl
# crude: look for skip if parsed
idx = crawl.find("def self_heal_queue")
chunk = crawl[idx:idx+2500]
has_skip = "parsed_pages" in chunk and ("SELECT 1 FROM parsed_pages" in chunk or "payload_json" in chunk and "missing_cache" in chunk and "continue" in chunk)
# Better check: current code requeues ALL missing cache without checking parsed
print("  self-heal skips requeue when parsed exists:", "SELECT url FROM queue WHERE status IN ('VISITED', 'ERROR')" in chunk and "parsed_pages" not in chunk.split("for (url,)")[0] if False else None)
# Direct: missing_cache update without parsed check before it in loop
print("  CURRENT behavior: re-queues ALL VISITED with missing HTML (parsed check: NOT implemented)")

print("\n=== Rule 2c: Supabase live import done? ===")
print("  (local check only — no live-import in this session)")
print("  last known: import NOT run for this recovery crawl; bundle is still X-Trail-only from Aug 7")

print("\n=== VERDICT per completed model ===")
for slug in complete:
    in_bundle = slug in models_in_bundle
    print(f"  {slug}:")
    print(f"    rule1 crawl complete: YES")
    print(f"    rule2 in published bundle: {'YES' if in_bundle else 'NO'}")
    print(f"    rule2 supabase imported: NO (not done this run)")
    print(f"    rule3 self-heal safe: NO (patch not applied)")
    print(f"    ALL SAFE RULES: NO")
