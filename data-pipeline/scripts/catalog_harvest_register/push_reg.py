# Register the harvest catalogue on the hosted project (Management API SQL). Steps: objects | vehicles | cleanup
import json, os, urllib.request, time, sys
from concurrent.futures import ThreadPoolExecutor
T = os.environ["SBP_TOKEN"]; R = "bicyjghgdnzlnjqxzoud"; REL = "6402bcb6-744a-43c1-8bef-5d6a13a68a3a"


def sql(q):
    err = None
    for a in range(6):
        try:
            req = urllib.request.Request(
                f"https://api.supabase.com/v1/projects/{R}/database/query",
                data=json.dumps({"query": q}).encode(),
                headers={"Authorization": f"Bearer {T}", "Content-Type": "application/json"},
                method="POST",
            )
            return json.loads(urllib.request.urlopen(req, timeout=300).read() or b"[]")
        except Exception as e:
            err = f"{e} {getattr(e, 'read', lambda: b'')()[:300]}"
            time.sleep(3 * (a + 1))
    raise RuntimeError(err)


lit = lambda v: "NULL" if v is None else ("'" + str(v).replace("'", "''") + "'")
step = sys.argv[1]

if step == "objects":
    rows = json.load(open("registration.json"))["objects"]; B = 2000

    def batch(i):
        vals = ",".join(
            f"('{REL}','nissan',{lit(k)},{lit(s)},{lit(o)},{lit(h)},0,0,{'NULL' if k == 'diagram_image' else lit('gzip')},{lit(m)}::jsonb)"
            for k, s, o, h, m in rows[i:i + B]
        )
        sql("insert into public.catalog_r2_serving_objects (release_id,maker_slug,object_kind,scope_key,object_key,sha256,row_count,bytes,content_encoding,metadata) values "
            + vals + " on conflict (release_id,object_kind,scope_key) do nothing")
        return i

    done = 0
    with ThreadPoolExecutor(4) as ex:
        for _ in ex.map(batch, range(0, len(rows), B)):
            done += 1
            if done % 50 == 0:
                print("object batches", done, flush=True)
    print("objects done", len(rows))

elif step == "vehicles":
    rows = json.load(open("registration.json"))["vehicles"]; B = 2000
    for i in range(0, len(rows), B):
        vals = ",".join(
            f"({lit(vm)},'nissan',{lit(m)},{lit(ch)},{lit(e)},{lit(ys)},{lit(ye)},{lit(rg)},'harvest-2026-09')"
            for vm, m, ch, e, ys, ye, rg in rows[i:i + B]
        )
        sql("insert into public.catalog_r2_vehicle_master (r2_scope_key,maker_slug,model,chassis_code,engine_code,year_start,year_end,sales_region,source_release_version) values "
            + vals + " on conflict (r2_scope_key) do nothing")
    print("vehicles done", len(rows))

elif step == "cleanup":
    # The 16 sample vehicles of the partial v2 release; the harvest registers the same vehicles.
    print(sql("delete from public.catalog_r2_vehicle_master where source_release_version = 'v2-storage-2026-09' returning r2_scope_key"))

print(sql("select (select count(*) from public.catalog_r2_vehicle_master) vehicles, "
          "(select json_object_agg(object_kind, n) from (select object_kind, count(*) n from public.catalog_r2_serving_objects group by 1) x) objects"))
