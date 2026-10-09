# Per-diagram part shards with callout boxes, from the harvest. Box = hotspot source rect / true image size.
import sqlite3, json, hashlib, gzip, os, re, collections
c = sqlite3.connect("file:harvest.sqlite?mode=ro&immutable=1", uri=True)
VER = "harvest-2026-09"; OUT = "out_dparts"
img = {sha: (w, h) for sha, w, h in c.execute("select sha256, width, height from images")}
node_img = {nk: (sha or "").lower() for nk, sha in c.execute("select node_key, image_sha256 from nodes")}
TITLE = re.compile(r"^(.*?)\s+for\s+\d{4}")
norm = lambda s: re.sub(r"[^A-Z0-9]", "", (s or "").upper())
index = {}; stats = collections.Counter()
for nk, payload in c.execute("select node_key, payload_json from parsed_nodes"):
    p = json.loads(payload); dg = "DG-" + hashlib.sha256(nk.encode()).hexdigest()[:28]
    wh = img.get(node_img.get(nk)) or (p.get("image_width"), p.get("image_height"))
    W, H = (wh[0] or 0), (wh[1] or 0)
    hs = {h["itemslist_id"]: h for h in p.get("hotspots") or []}
    # Some diagrams' hotspot coordinates are on a canvas 1.5x the served image; detect by overflow.
    if hs and W and H:
        mx = max(h["source_x"] + h["source_width"] for h in hs.values()); my = max(h["source_y"] + h["source_height"] for h in hs.values())
        if mx > W * 1.02 or my > H * 1.02: W, H = W * 1.5, H * 1.5; stats["scaled_1_5"] += 1
    extra = {r.get("itemslist_id"): r for r in p.get("parts") or []}
    title = p.get("title") or ""; m = TITLE.match(title); short = (m.group(1) if m else title).strip()
    rows = []
    for r in p.get("parts_table") or p.get("parts") or []:
        oem = (r.get("oem_part_number") or "").strip()
        if not oem: continue
        e = extra.get(r.get("itemslist_id"), {}); h = hs.get(r.get("itemslist_id"))
        row = {"normalized_oem_number": norm(oem), "display_oem_number": oem, "name": r.get("description") or oem,
               "description": r.get("description"), "pnc_code": e.get("pnc_code") or r.get("pnc_code"),
               "callout_ref": r.get("callout_ref"), "quantity": r.get("quantity"), "diagram_id": dg,
               "section_slug": p.get("section_slug"), "category_name": short, "subcategory_name": short,
               "engine_code": e.get("engine_code") or None}
        if h and W and H and h.get("source_width"):
            x, y, w, hh = h["source_x"] / W, h["source_y"] / H, h["source_width"] / W, h["source_height"] / H
            if 0 <= x <= 1 and 0 <= y <= 1:
                row.update(bbox_x=round(x, 5), bbox_y=round(y, 5), bbox_width=round(min(w, 1 - x), 5), bbox_height=round(min(hh, 1 - y), 5)); stats["box"] += 1
            else: stats["box_out"] += 1
        else: stats["no_box"] += 1
        rows.append(row)
    if not rows: stats["empty"] += 1; continue
    h32 = hashlib.sha256(dg.encode()).hexdigest()[:32]
    key = f"serving/nissan/{VER}/diagram-parts/{h32[:2]}/{h32}.ndjson.gz"
    path = os.path.join(OUT, key); os.makedirs(os.path.dirname(path), exist_ok=True)
    body = gzip.compress(("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n").encode(), mtime=0)
    open(path, "wb").write(body)
    index[dg] = {"key": key, "rows": len(rows), "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest()}
    stats["diagrams"] += 1
json.dump(index, open("dparts_index.json", "w"))
print(dict(stats), "total MB", round(sum(v["bytes"] for v in index.values()) / 1e6, 1))
