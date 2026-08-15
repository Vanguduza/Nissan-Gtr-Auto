#!/usr/bin/env python3
import re
from pathlib import Path

html = Path(r"C:\Nissan GTR auto\apps\catalog-apk\scripts\_japancats_sample.html").read_text(encoding="utf-8")
submit_re = re.compile(
    r"""javascript:submit\(\s*'hF'\s*,\s*'([^']+)'\s*,\s*'([^']+)'""",
    re.I,
)
print("hF matches", len(submit_re.findall(html)))
print(submit_re.findall(html)[:3])
# any submit
all_sub = re.findall(r"javascript:submit\(([^)]{0,200})\)", html)
print("any submit", len(all_sub))
print(all_sub[:5])
# bracket codes
print("bracket", len(re.findall(r"\[([A-Z0-9]{2,12})\]", html)))
