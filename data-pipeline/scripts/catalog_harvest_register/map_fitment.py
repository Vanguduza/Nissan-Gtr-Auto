import sqlite3, hashlib, json, zlib, collections, urllib.parse, urllib.request, datetime, hmac, time
from concurrent.futures import ThreadPoolExecutor
import r2lib
c = sqlite3.connect("file:harvest.sqlite?mode=ro&immutable=1", uri=True)
sid2var = {}
for fam, var, sec in c.execute("select distinct family_slug, variant_slug, section_slug from placements where maker_slug='nissan'"):
    sid2var["SEC-" + hashlib.sha256(f"nissan|{fam}|{var}|{sec}".encode()).hexdigest()[:28]] = (fam, var)
print("section ids", len(sid2var), flush=True)
folders = collections.defaultdict(list)
for k, _ in json.load(open("v3_fitment.json")): folders[k.split("/")[6]].append(k)
def head(key):
    path = "/" + r2lib.bucket + "/" + urllib.parse.quote(key)
    for attempt in range(6):
        try:
            t = datetime.datetime.utcnow(); amz = t.strftime("%Y%m%dT%H%M%SZ"); d = t.strftime("%Y%m%d"); ph = hashlib.sha256(b"").hexdigest()
            canon = f"GET\n{path}\n\nhost:{r2lib.host}\nx-amz-content-sha256:{ph}\nx-amz-date:{amz}\n\nhost;x-amz-content-sha256;x-amz-date\n{ph}"
            scope = f"{d}/auto/s3/aws4_request"; sts = f"AWS4-HMAC-SHA256\n{amz}\n{scope}\n{hashlib.sha256(canon.encode()).hexdigest()}"
            k = r2lib._s(r2lib._s(r2lib._s(r2lib._s(("AWS4" + r2lib.sec).encode(), d), "auto"), "s3"), "aws4_request")
            sig = hmac.new(k, sts.encode(), hashlib.sha256).hexdigest()
            r = urllib.request.Request(f"https://{r2lib.host}{path}", headers={"Range": "bytes=0-65535", "x-amz-date": amz, "x-amz-content-sha256": ph, "Authorization": f"AWS4-HMAC-SHA256 Credential={r2lib.key}/{scope}, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature={sig}"})
            raw = urllib.request.urlopen(r, timeout=60).read()
            txt = zlib.decompressobj(16 + zlib.MAX_WBITS).decompress(raw).decode("utf-8", "ignore")
            return [json.loads(l) for l in txt.splitlines()[:-1] if l.strip()]
        except Exception as e:
            err = e; time.sleep(1 + attempt)
    return err
def work(item):
    folder, keys = item
    rows = head(sorted(keys)[0])
    if isinstance(rows, Exception): return folder, None, f"err {rows}"
    votes = collections.Counter(sid2var.get(r.get("section_id")) for r in rows if r.get("section_id") in sid2var)
    return folder, (votes.most_common(1)[0][0] if votes else None), f"{sum(votes.values())}/{len(rows)} {len(votes)}"
out = {}; amb = 0
with ThreadPoolExecutor(32) as ex:
    for i, (folder, var, note) in enumerate(ex.map(work, folders.items())):
        out[folder] = {"variant": var, "note": note, "pages": sorted(folders[folder])}
        if var and not note.endswith(" 1"): amb += 1
        if i % 2000 == 0: print(i, folder, var, note, flush=True)
json.dump(out, open("fitment_map.json", "w"))
vs = [v["variant"] for v in out.values() if v["variant"]]
print("folders", len(out), "mapped", len(vs), "distinct variants", len(set(map(tuple, vs))), "multi-vote", amb)
