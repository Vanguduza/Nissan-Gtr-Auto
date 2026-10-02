# Upload local files to R2 (SigV4 PUT). Usage: r2put.py <local_root> <index.json> <done_log>
import os, sys, hmac, hashlib, datetime, urllib.parse, urllib.request, json, time
from concurrent.futures import ThreadPoolExecutor
import r2lib
root, index_path, done_path = sys.argv[1:4]
idx = json.load(open(index_path)); keys = sorted({v["key"] for v in idx.values()})
done = set(open(done_path).read().split()) if os.path.exists(done_path) else set()
todo = [k for k in keys if k not in done]
def put(key):
    body = open(os.path.join(root, key), "rb").read(); ph = hashlib.sha256(body).hexdigest()
    path = "/" + r2lib.bucket + "/" + urllib.parse.quote(key)
    for attempt in range(6):
        try:
            t = datetime.datetime.utcnow(); amz = t.strftime("%Y%m%dT%H%M%SZ"); d = t.strftime("%Y%m%d")
            hdrs = {"content-encoding": "gzip", "content-type": "application/x-ndjson", "host": r2lib.host, "x-amz-content-sha256": ph, "x-amz-date": amz}
            signed = ";".join(sorted(hdrs)); canon_h = "".join(f"{k}:{hdrs[k]}\n" for k in sorted(hdrs))
            canon = f"PUT\n{path}\n\n{canon_h}\n{signed}\n{ph}"; scope = f"{d}/auto/s3/aws4_request"
            sts = f"AWS4-HMAC-SHA256\n{amz}\n{scope}\n{hashlib.sha256(canon.encode()).hexdigest()}"
            k = r2lib._s(r2lib._s(r2lib._s(r2lib._s(("AWS4" + r2lib.sec).encode(), d), "auto"), "s3"), "aws4_request")
            sig = hmac.new(k, sts.encode(), hashlib.sha256).hexdigest()
            h = {kk: vv for kk, vv in hdrs.items() if kk != "host"}; h["Authorization"] = f"AWS4-HMAC-SHA256 Credential={r2lib.key}/{scope}, SignedHeaders={signed}, Signature={sig}"
            urllib.request.urlopen(urllib.request.Request(f"https://{r2lib.host}{path}", data=body, headers=h, method="PUT"), timeout=60).read()
            return key
        except Exception as e:
            err = e; time.sleep(1 + attempt * 2)
    raise RuntimeError(f"{key}: {err}")
n = 0; t0 = time.time()
with open(done_path, "a") as log, ThreadPoolExecutor(int(os.environ.get("PUT_THREADS", "48"))) as ex:
    for key in ex.map(put, todo):
        log.write(key + "\n"); n += 1
        if n % 20000 == 0: print(n, len(todo), int(time.time() - t0), "s", flush=True)
print("UPLOADED", n, "of", len(todo), "total keys", len(keys))
