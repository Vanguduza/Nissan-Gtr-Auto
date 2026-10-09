# Point each look-alike group's kept vehicle at its merged shard and drop the duplicate entries.
import json, os, urllib.request, time
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
g = json.load(open("groups_index.json")); items = list(g.items()); B = 400
for i in range(0, len(items), B):
    chunk = items[i:i + B]
    vals = ",".join(f"({lit(k)},{lit(v['key'])},{lit(json.dumps({'version': 'harvest-2026-09', 'complete': True, 'merged_builds': len(v['members'])}))}::jsonb,{v['rows']},{v['bytes']},{lit(v['sha256'])})" for k, v in chunk)
    sql(f"""update public.catalog_r2_serving_objects o set object_key = x.k, metadata = x.m, row_count = x.r, bytes = x.b, sha256 = x.h
            from (values {vals}) as x(vm, k, m, r, b, h)
            where o.release_id = '{REL}' and o.object_kind in ('vehicle_search', 'vehicle_fitment') and o.scope_key = x.vm""")
drop = [m for k, v in items for m in v["members"] if m != k]
for i in range(0, len(drop), 2000):
    sql("delete from public.catalog_r2_vehicle_master where r2_scope_key in (" + ",".join(lit(m) for m in drop[i:i + 2000]) + ")")
print(sql("select count(*) vehicles, (select count(*) from public.catalog_r2_serving_objects where metadata->>'merged_builds' is not null and object_kind = 'vehicle_search') merged_routes from public.catalog_r2_vehicle_master"))
