# Collapse look-alike vehicles (same model, chassis, engine, years, region) into one entry whose
# shard is the de-duplicated union of its builds. Section ids are re-keyed to the kept build.
import json, hashlib, gzip, os, collections, sys
from concurrent.futures import ThreadPoolExecutor
import r2lib
VER = "harvest-2026-09"; OUT = "out_vehicles"
reg = json.load(open("registration.json")); fm = json.load(open("fitment_map.json"))
sha28 = lambda s: hashlib.sha256(s.encode()).hexdigest()[:28]
vm_info = {}
for f in fm.values():
    if f["variant"]:
        vm = "VM-" + sha28(f"nissan|{f['variant'][0]}|{f['variant'][1]}"); vm_info[vm] = (f["variant"][0], f["variant"][1], f["pages"])
groups = collections.defaultdict(list)
for v in reg["vehicles"]: groups[tuple(v[1:])].append(v[0])
plan = []
for key, vms in groups.items():
    if len(vms) < 2: continue
    vms = sorted(vms); plan.append((vms[0], vms))
def fetch(vm):
    return vm, [json.loads(l) for p in vm_info[vm][2] for l in r2lib.get(p).decode().splitlines() if l.strip()]
def build(item):
    keep, vms = item
    fam, kvar, _ = vm_info[keep]
    with ThreadPoolExecutor(8) as ex: shards = dict(ex.map(fetch, vms))
    seen = set(); rows = []
    for vm in vms:
        for r in shards[vm]:
            slug = r.get("section_slug") or ""
            if slug: r["section_id"] = "SEC-" + sha28(f"nissan|{fam}|{kvar}|{slug}")
            k = (r.get("diagram_id"), r.get("normalized_oem_number") or r.get("display_oem_number"), r.get("pnc_code"))
            if k in seen: continue
            seen.add(k); rows.append(r)
    h32 = hashlib.sha256(keep.encode()).hexdigest()[:32]
    key = f"serving/nissan/{VER}/vehicle/{h32[:2]}/{h32}.ndjson.gz"
    body = gzip.compress(("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n").encode(), mtime=0)
    path = os.path.join(OUT, key); os.makedirs(os.path.dirname(path), exist_ok=True); open(path, "wb").write(body)
    return keep, {"members": vms, "key": key, "rows": len(rows), "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest(),
                  "member_rows": sum(len(s) for s in shards.values())}
out = {}
with ThreadPoolExecutor(6) as ex:
    for i, (keep, info) in enumerate(ex.map(build, plan)):
        out[keep] = info
        if i % 100 == 0: print(i, len(plan), info["rows"], info["bytes"], flush=True)
json.dump(out, open("groups_index.json", "w"))
print("groups", len(out), "max MB", round(max(v["bytes"] for v in out.values()) / 1e6, 2), "total MB", round(sum(v["bytes"] for v in out.values()) / 1e6, 1),
      "removed vehicles", sum(len(v["members"]) - 1 for v in out.values()))
