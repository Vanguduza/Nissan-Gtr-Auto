import os, hmac, hashlib, datetime, urllib.parse, urllib.request, re, gzip, json
from concurrent.futures import ThreadPoolExecutor
acc, key, sec, bucket = os.environ["R2_ACC"], os.environ["R2_KEY"], os.environ["R2_SEC"], "nissangtrauto"
host = f"{acc}.r2.cloudflarestorage.com"
def _s(k, m): return hmac.new(k, m.encode(), hashlib.sha256).digest()
def req(path, query={}):
    for attempt in range(5):
        try:
            t = datetime.datetime.utcnow(); amz = t.strftime("%Y%m%dT%H%M%SZ"); d = t.strftime("%Y%m%d")
            qs = "&".join(f"{urllib.parse.quote(k, safe='')}={urllib.parse.quote(v, safe='')}" for k, v in sorted(query.items()))
            ph = hashlib.sha256(b"").hexdigest()
            canon = f"GET\n{path}\n{qs}\nhost:{host}\nx-amz-content-sha256:{ph}\nx-amz-date:{amz}\n\nhost;x-amz-content-sha256;x-amz-date\n{ph}"
            scope = f"{d}/auto/s3/aws4_request"
            sts = f"AWS4-HMAC-SHA256\n{amz}\n{scope}\n{hashlib.sha256(canon.encode()).hexdigest()}"
            k = _s(_s(_s(_s(("AWS4" + sec).encode(), d), "auto"), "s3"), "aws4_request")
            sig = hmac.new(k, sts.encode(), hashlib.sha256).hexdigest()
            r = urllib.request.Request(f"https://{host}{path}" + (f"?{qs}" if qs else ""), headers={"x-amz-date": amz, "x-amz-content-sha256": ph, "Authorization": f"AWS4-HMAC-SHA256 Credential={key}/{scope}, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature={sig}"})
            return urllib.request.urlopen(r, timeout=120).read()
        except Exception as e:
            if attempt == 4: raise
def list_all(prefix):
    out, tok = [], None
    while True:
        q = {"list-type": "2", "prefix": prefix, "max-keys": "1000"}
        if tok: q["continuation-token"] = tok
        x = req("/" + bucket, q).decode()
        for blk in re.findall(r"<Contents>(.*?)</Contents>", x, re.S):
            out.append((re.search(r"<Key>(.*?)</Key>", blk).group(1), int(re.search(r"<Size>(\d+)</Size>", blk).group(1))))
        m = re.search(r"<NextContinuationToken>(.*?)</NextContinuationToken>", x)
        if not m: return out
        tok = m.group(1)
def list_sharded(prefix):
    with ThreadPoolExecutor(32) as ex:
        return [o for part in ex.map(lambda i: list_all(f"{prefix}{i:02x}/"), range(256)) for o in part]
def get(key_):
    b = req("/" + bucket + "/" + urllib.parse.quote(key_))
    return gzip.decompress(b) if b[:2] == b"\x1f\x8b" else b
