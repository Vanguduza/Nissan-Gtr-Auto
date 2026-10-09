# Point every diagram's diagram_parts route at its own harvest shard (parts + callout boxes).
import json, os, urllib.request, time
from concurrent.futures import ThreadPoolExecutor
T = os.environ["SBP_TOKEN"]; R = "bicyjghgdnzlnjqxzoud"; REL = "6402bcb6-744a-43c1-8bef-5d6a13a68a3a"
def sql(q):
    for a in range(6):
        try:
            req = urllib.request.Request(f"https://api.supabase.com/v1/projects/{R}/database/query", data=json.dumps({"query": q}).encode(),
                                         headers={"Authorization": f"Bearer {T}", "Content-Type": "application/json"}, method="POST")
            return json.loads(urllib.request.urlopen(req, timeout=300).read() or b"[]")
        except Exception as e:
            err = f"{e} {getattr(e, 'read', lambda: b'')()[:300]}"; time.sleep(3 * (a + 1))
    raise RuntimeError(err)
lit = lambda v: "'" + str(v).replace("'", "''") + "'"
idx = json.load(open("dparts_index.json"))
uploaded = set(open("dparts_uploaded.log").read().split())
items = [(dg, v) for dg, v in idx.items() if v["key"] in uploaded]
meta = json.dumps({"version": "harvest-2026-09", "complete": True, "callout_boxes": True})
B = 2000
def batch(i):
    vals = ",".join(f"({lit(dg)},{lit(v['key'])},{v['rows']},{v['bytes']},{lit(v['sha256'])})" for dg, v in items[i:i + B])
    sql(f"""insert into public.catalog_r2_serving_objects (release_id, maker_slug, object_kind, scope_key, object_key, sha256, row_count, bytes, content_encoding, metadata)
            select '{REL}', 'nissan', 'diagram_parts', x.dg, x.k, x.h, x.r, x.b, 'gzip', {lit(meta)}::jsonb from (values {vals}) as x(dg, k, r, b, h)
            on conflict (release_id, object_kind, scope_key) do update set object_key = excluded.object_key, sha256 = excluded.sha256,
              row_count = excluded.row_count, bytes = excluded.bytes, content_encoding = excluded.content_encoding, metadata = excluded.metadata""")
    return i
with ThreadPoolExecutor(4) as ex: list(ex.map(batch, range(0, len(items), B)))
print("diagrams repointed", len(items), "of", len(idx))
print(sql("select count(*) filter (where metadata->>'callout_boxes' = 'true') with_boxes, count(*) all_diagram_parts from public.catalog_r2_serving_objects where object_kind = 'diagram_parts'"))
