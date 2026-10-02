import sqlite3, json, re, collections
c = sqlite3.connect("file:harvest.sqlite?mode=ro&immutable=1", uri=True)
TITLE = re.compile(r"for\s+(\d{4})\s*-\s*(\d{4})\s+Nissan\s+(.+?)\s+([A-Z0-9][A-Z0-9-]{0,15})\s*\|\s*([^,|]+?)\s+sales region", re.I)
ENG = re.compile(r"engine|cylinder|piston|camshaft|crankshaft|manifold|oil-pump|timing|turbo|fuel-injection|water-pump|valve")
node = {}
for nk, payload in c.execute("select node_key, payload_json from parsed_nodes"):
    p = json.loads(payload); m = TITLE.search(p.get("title") or "")
    node[nk] = ((p.get("engine_code") or "").strip().upper() or None, (int(m[1]), int(m[2]), m[3].strip(), m[4].strip().upper(), m[5].strip()) if m else None)
own = json.load(open("variant_identity.json"))
fm = json.load(open("fitment_map.json"))
wanted = {(v["variant"][0], v["variant"][1]) for v in fm.values() if v["variant"]}
eng = collections.defaultdict(collections.Counter); lab = collections.defaultdict(collections.Counter)
for nk, fam, var, sec in c.execute("select node_key, family_slug, variant_slug, section_slug from placements where maker_slug='nissan'"):
    if (fam, var) not in wanted or nk not in node: continue
    e, l = node[nk]
    if e and ENG.search(sec): eng[(fam, var)][e] += 1
    if l: lab[(fam, var)][l] += 1
res = {}; src = collections.Counter()
for fv in wanted:
    k = f"{fv[0]}|{fv[1]}"; o = own.get(k)
    engine = o["engine"] if o and o["engine"] else None; how = "own" if engine else None
    if not engine and eng[fv]:
        top, n = eng[fv].most_common(1)[0]
        if n / sum(eng[fv].values()) >= 0.8: engine, how = top, "shared-engine-sections"
    label = (o["label"] if o and o["label"] else None)
    if not label and lab[fv]:
        top, n = lab[fv].most_common(1)[0]
        if n / sum(lab[fv].values()) >= 0.6: label = top
    src[how or "unresolved"] += 1
    res[k] = {"engine": engine, "engine_source": how, "label": label, "engine_votes": dict(eng[fv].most_common(4))}
json.dump(res, open("vehicle_identity.json", "w"))
print("vehicles", len(res), dict(src), "with label", sum(1 for r in res.values() if r["label"]))
print("models", len({r["label"][2] for r in res.values() if r["label"]}))
un = [k for k, r in res.items() if not r["engine"]]
print("unresolved by family", collections.Counter(k.split("|")[0] for k in un).most_common(12))
