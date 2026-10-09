import sqlite3, json, re, collections
c = sqlite3.connect("file:harvest.sqlite?mode=ro&immutable=1", uri=True)
TITLE = re.compile(r"for\s+(\d{4})\s*-\s*(\d{4})\s+Nissan\s+(.+?)\s+([A-Z0-9][A-Z0-9-]{0,15})\s*\|\s*([^,|]+?)\s+sales region", re.I)
ident = collections.defaultdict(lambda: {"eng": collections.Counter(), "lab": collections.Counter(), "nodes": 0})
q = c.execute("select n.url, p.payload_json from nodes n join parsed_nodes p using(node_key) where n.maker_slug='nissan'")
for i, (url, payload) in enumerate(q):
    seg = url.rstrip("/").split("/")
    fam, var = seg[-4], seg[-2]
    p = json.loads(payload)
    e = ident[(fam, var)]; e["nodes"] += 1
    if p.get("engine_code"): e["eng"][p["engine_code"].strip().upper()] += 1
    m = TITLE.search(p.get("title") or "")
    if m: e["lab"][(int(m[1]), int(m[2]), m[3].strip(), m[4].strip().upper(), m[5].strip())] += 1
    if i % 50000 == 0: print(i, flush=True)
out = {}
for (fam, var), e in ident.items():
    lab = e["lab"].most_common(1)[0][0] if e["lab"] else None
    out[f"{fam}|{var}"] = {"engine": e["eng"].most_common(1)[0][0] if e["eng"] else None, "engines": dict(e["eng"]), "label": lab, "nodes": e["nodes"]}
json.dump(out, open("variant_identity.json", "w"))
fm = json.load(open("fitment_map.json"))
mapped = {f"{v['variant'][0]}|{v['variant'][1]}" for v in fm.values() if v["variant"]}
have = [out.get(k) for k in mapped]
print("variants with own diagrams", len(out), "mapped vehicles", len(mapped), "identified", sum(1 for h in have if h))
print("with engine", sum(1 for h in have if h and h["engine"]), "with label", sum(1 for h in have if h and h["label"]), "multi-engine", sum(1 for h in have if h and len(h["engines"]) > 1))
print("models", collections.Counter(h["label"][2] for h in have if h and h["label"]).most_common(40))
