import re
from pathlib import Path

import httpx

URL = (
    "https://www.catcar.info/nissan/?l="
    "cmVnaW9uPT1qcHx8c3Q9PTcwfHxzdHM9PXsiMTAiOiJcdTA0MjBcdTA0NGJcdTA0M2RcdTA0M2VcdTA0M2EiLCIyMCI6Ilx1MDQyZlx1MDQxZlx1MDQxZVx1MDQxZFx1MDQxOFx1MDQyZiIsIjUwIjoiTklTU0FOIEdULVIgKFIzNSkiLCI2MCI6Ilx1MDQxNFx1MDQxMlx1MDQxOFx1MDQxM1x1MDQxMFx1MDQyMlx1MDQxNVx1MDQxYlx1MDQyYywgXHUwNDIyXHUwNDFlXHUwNDFmXHUwNDFiXHUwNDE4XHUwNDEyXHUwNDFkXHUwNDEwXHUwNDJmIFx1MDQyMVx1MDQxOFx1MDQyMVx1MDQyMlx1MDQxNVx1MDQxY1x1MDQxMCIsIjcwIjoiMTAxQSAwMDEifXx8bW9kX2lkPT0yMTV8fHN0YXJ0PT0yMDA3LTExLTAxfHxlbmQ9PXx8c2E9PVp8fHNlY19pZD09MTAxfHxzdWZmPT1BfHxwYWdlPT0wMDF8fHN0YXJ0Z3JwPT0yMDA3LTExLTAxfHxlbmRncnA9PTIwMTAtMTEtMDE%3D"
)
OUT = Path(__file__).resolve().parents[1] / "data" / "catcar_test_crawl" / "diagram_101A_001.html"

r = httpx.get(URL, headers={"User-Agent": "probe"}, timeout=30)
html = r.text
OUT.write_text(html, encoding="utf-8")
areas = re.findall(r"<area[^>]+>", html, re.I)
maps = re.findall(r"<map[^>]+>", html, re.I)
imgs = re.findall(r"https?://ci\.catcar\.info[^\"'\s>]+", html)
parts = re.findall(r"\b\d{5}-[A-Z0-9]{2,6}[A-Z0-9-]*\b", html)
refs = re.findall(r'class="[^"]*ref[^"]*"[^>]*>([^<]+)<', html, re.I)

report = OUT.with_suffix(".report.txt")
report.write_text(
    "\n".join(
        [
            f"status={r.status_code} bytes={len(r.content)}",
            f"areas={len(areas)} maps={len(maps)}",
            f"ci_imgs={imgs[:5]}",
            f"parts={parts[:15]}",
            f"refs={refs[:10]}",
            f"area_sample={areas[0][:400] if areas else 'none'}",
        ]
    ),
    encoding="utf-8",
)
print(report.read_text(encoding="utf-8"))
