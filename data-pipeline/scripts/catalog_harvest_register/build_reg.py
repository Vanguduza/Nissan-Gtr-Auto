import sqlite3, json, hashlib, re, collections
REL = "6402bcb6-744a-43c1-8bef-5d6a13a68a3a"; VER = "harvest-2026-09"
c = sqlite3.connect("file:harvest.sqlite?mode=ro&immutable=1", uri=True)
fm = json.load(open("fitment_map.json")); ident = json.load(open("vehicle_identity.json"))
scan = json.load(open("v3_scan.json")); existing = {k: set(v) for k, v in json.load(open("existing_scopes.json")).items()}
images = {k.rsplit("/", 1)[1][:-4]: k for k, _ in json.load(open("diagrams.json"))}
sha28 = lambda s: hashlib.sha256(s.encode()).hexdigest()[:28]
def pretty(s):
    s = re.sub(r"-\d+$", "", s)
    def w(t): return t.upper() if re.search(r"\d", t) or len(t) <= 2 else t.capitalize()
    return re.sub(r"[A-Za-z0-9]+", lambda m: w(m.group(0)), s.replace("-", " ") if " " not in s and "/" not in s and s.islower() else s.lower())
vehicles = {}; objs = []; var2vm = {}; vm_pages = {}
for folder, f in fm.items():
    if not f["variant"]: continue
    fam, var = f["variant"]; k = f"{fam}|{var}"
    if k in var2vm: continue
    vm = "VM-" + sha28(f"nissan|{fam}|{var}"); var2vm[k] = vm
    idn = ident.get(k, {}); lab = idn.get("label")
    votes = idn.get("engine_votes") or {}
    if idn.get("engine"): engine = idn["engine"]
    elif votes:
        tot = sum(votes.values()); engine = " / ".join(e for e, n in sorted(votes.items(), key=lambda x: -x[1]) if n / tot >= 0.2)
    else: engine = ""
    model = pretty(lab[2]) if lab else pretty(fam)
    chassis = lab[3] if lab else re.sub(r"-\d+$", "", var).upper()
    vehicles[vm] = (vm, model, chassis, engine, lab[0] if lab else None, lab[1] if lab else None, lab[4] if lab else None)
    pages = f["pages"]; vm_pages[vm] = pages
    meta = {"version": VER, "complete": True, "pages": pages}
    for kind in ("vehicle_search", "vehicle_fitment"):
        objs.append((kind, vm, pages[0], None, json.dumps(meta)))
# sections from the R2 section-parts scan
sec_key = {sid: s["key"] for sid, s in scan["sections"].items()}
for sid, key in sec_key.items():
    if sid not in existing.get("section_parts", ()):
        objs.append(("section_parts", sid, key, None, json.dumps({"version": VER, "complete": True})))
# diagrams: every harvest node placed on a registered vehicle
dg_section = {did: d["section"] for did, d in scan["diagrams"].items()}
node_img = {nk: (sha or "").lower() for nk, sha in c.execute("select node_key, image_sha256 from nodes")}
seen = set(); no_img = 0; via_vehicle = 0
for nk, fam, var in c.execute("select node_key, family_slug, variant_slug from placements where maker_slug='nissan'"):
    vm = var2vm.get(f"{fam}|{var}")
    dg = "DG-" + sha28(nk)
    if not vm or dg in seen: continue
    seen.add(dg)
    if dg not in existing.get("diagram_parts", ()):
        sid = dg_section.get(dg)
        if sid and sid in sec_key:
            objs.append(("diagram_parts", dg, sec_key[sid], None, json.dumps({"version": VER, "complete": True, "filter": "diagram_id"})))
        else:
            via_vehicle += 1
            objs.append(("diagram_parts", dg, vm_pages[vm][0], None, json.dumps({"version": VER, "complete": True, "filter": "diagram_id", "pages": vm_pages[vm]})))
    img = node_img.get(nk)
    if dg not in existing.get("diagram_image", ()):
        if img in images: objs.append(("diagram_image", dg, images[img], img, json.dumps({"version": VER, "complete": True, "recovered_from": "harvest_image_sha256"})))
        else: no_img += 1
json.dump({"vehicles": list(vehicles.values()), "objects": objs}, open("registration.json", "w"))
kinds = collections.Counter(o[0] for o in objs)
eng = collections.Counter("single" if v[3] and "/" not in v[3] else ("multi" if v[3] else "blank") for v in vehicles.values())
print("vehicles", len(vehicles), dict(eng), "models", len({v[1] for v in vehicles.values()}))
print("objects", dict(kinds), "diagram parts via vehicle shard", via_vehicle, "diagrams without image", no_img)
print(sorted({v[1] for v in vehicles.values()})[:95])
