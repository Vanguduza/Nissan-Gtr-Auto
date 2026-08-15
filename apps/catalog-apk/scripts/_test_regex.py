#!/usr/bin/env python3
import re
from pathlib import Path

src = Path(__file__).with_name("harvest_site_catalogs.py").read_text(encoding="utf-8")
# extract first finditer pattern around parts/
for m in re.finditer(r"re\.finditer\(\s*(r[^,\n]+)", src):
    lit = m.group(1)
    if "parts" in lit and "a-z0-9" in lit:
        print("LIT:", lit)
        try:
            # eval the raw string literal carefully
            pat = eval(lit)  # noqa: S307
            print("REPR:", repr(pat))
            print("MATCH nissan?", bool(re.search(pat, 'href="/parts/nissan"', re.I)))
            print("MATCH zap?", bool(re.search(
                pat.replace("parts", "zapchasti-dlya-avtomobilej") if "zapchasti" not in pat else pat,
                'href="/zapchasti-dlya-avtomobilej/nissan/skyline-2086"',
                re.I,
            )))
        except Exception as exc:  # noqa: BLE001
            print("eval fail", exc)
        print("---")
