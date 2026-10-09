#!/usr/bin/env python3
"""Harvest maker → model → chassis trees for Catalog APK presets.

Writes apps/catalog-apk/app/src/main/assets/site_catalogs/<id>.json
Uses direct HTTP first; FlareSolverr at 127.0.0.1:8191 when challenged.
"""

from __future__ import annotations

import html as htmlmod
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "site_catalogs"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
FLARE = "http://127.0.0.1:8191/v1"
DELAY = 0.25


def _clean(s: str) -> str:
    s = htmlmod.unescape(s or "")
    return re.sub(r"\s+", " ", s).strip()


def _join(base: str, path: str) -> str:
    if path.startswith("http"):
        return path
    return urllib.parse.urljoin(base.rstrip("/") + "/", path.lstrip("/"))


def looks_cf(status: int, body: str) -> bool:
    low = body.lower()
    return status in (403, 503, 429) or "just a moment" in low or "cf-browser-verification" in low


def flare_get(url: str) -> tuple[int, str]:
    payload = json.dumps({"cmd": "request.get", "url": url, "maxTimeout": 60000}).encode()
    req = urllib.request.Request(
        FLARE,
        data=payload,
        headers={"Content-Type": "application/json", "User-Agent": UA},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        data = json.loads(resp.read().decode("utf-8", "replace"))
    sol = data.get("solution") or {}
    return int(sol.get("status") or 0), str(sol.get("response") or "")


def fetch(url: str, *, force_flare: bool = False) -> tuple[int, str]:
    if force_flare:
        try:
            return flare_get(url)
        except Exception as exc:  # noqa: BLE001
            return 0, str(exc)
    req = urllib.request.Request(
        url,
        headers={"User-Agent": UA, "Accept": "text/html", "Accept-Language": "en-US,en;q=0.9"},
    )
    try:
        with urllib.request.urlopen(req, timeout=40) as resp:
            body = resp.read().decode("utf-8", "replace")
            status = int(resp.status)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace") if exc.fp else ""
        status = int(exc.code)
    except Exception as exc:  # noqa: BLE001
        return 0, str(exc)
    if looks_cf(status, body):
        try:
            return flare_get(url)
        except Exception:
            return status, body
    return status, body


def chassis_token(text: str) -> str:
    t = _clean(text).upper()
    m = re.search(r"\b([A-Z]{1,3}\d{1,3}[A-Z]?)\b", t)
    return m.group(1) if m else t[:16]


def dump(site_id: str, makers: list[dict]) -> Path:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{site_id}.json"
    payload = {
        "id": site_id,
        "harvested_at": time.strftime("%Y-%m-%d"),
        "makers": makers,
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    n_models = sum(len(m.get("models") or []) for m in makers)
    n_ch = sum(len(mo.get("chassis") or []) for m in makers for mo in (m.get("models") or []))
    print(f"  wrote {path.name}: makers={len(makers)} models={n_models} chassis={n_ch}", flush=True)
    return path


# --- Megazip -----------------------------------------------------------------


def harvest_megazip() -> None:
    """Car catalogs live under /zapchasti-dlya-avtomobilej/{maker}/{model}/{chassis}."""
    print("=== megazip ===", flush=True)
    base = "https://www.megazip.net"
    status, html = fetch(base + "/")
    print(f"  home {status} bytes={len(html)}", flush=True)
    makers: dict[str, dict] = {}
    for m in re.finditer(r'href="(/parts/([a-z0-9-]+))/?"', html, re.I):
        slug = m.group(2).lower()
        if slug in {"parts", "search", "snowmobiles", "watercraft", "generators", "atv"}:
            continue
        makers[slug] = {
            "name": slug.replace("-", " ").title(),
            "slug": slug,
            "source_url": base + f"/parts/{slug}",
            "models": [],
        }
    # Prefer known car OEMs first so incremental dumps are useful early
    preferred = [
        "nissan", "toyota", "lexus", "honda", "mazda", "mitsubishi", "subaru", "suzuki",
        "infiniti", "daihatsu", "isuzu",
    ]
    order = [s for s in preferred if s in makers] + sorted(s for s in makers if s not in preferred)
    out_makers: list[dict] = []
    for slug in order:
        maker = makers[slug]
        time.sleep(DELAY)
        st, page = fetch(maker["source_url"])
        print(f"  maker {slug} {st} bytes={len(page)}", flush=True)
        models: dict[str, dict] = {}
        # Model hubs: /zapchasti-dlya-avtomobilej/{maker}/{model-id}
        for m in re.finditer(
            rf'href="(/zapchasti-dlya-avtomobilej/{re.escape(slug)}/([a-z0-9-]+))/?"',
            page,
            re.I,
        ):
            path, mslug = m.group(1), m.group(2).lower()
            if "/" in mslug or mslug == slug:
                continue
            # strip trailing numeric site id for display: skyline-2086 -> Skyline
            label = re.sub(r"-\d+$", "", mslug).replace("-", " ").title()
            models.setdefault(
                mslug,
                {
                    "display_name": label,
                    "slug": mslug,
                    "source_url": _join(base, path),
                    "chassis": [],
                },
            )
        for m in re.finditer(
            rf'<a[^>]+href="(/zapchasti-dlya-avtomobilej/{re.escape(slug)}/([a-z0-9-]+))/?"[^>]*>([^<]{{1,80}})</a>',
            page,
            re.I,
        ):
            mslug = m.group(2).lower()
            label = _clean(m.group(3))
            if mslug in models and label and len(label) > 1 and not label.lower().startswith("http"):
                models[mslug]["display_name"] = label
        if not models:
            print(f"  skip {slug}: no car model tree", flush=True)
            continue
        for mslug, model in list(models.items()):
            time.sleep(DELAY)
            st2, mhtml = fetch(model["source_url"])
            if st2 != 200:
                print(f"    model {mslug} fail {st2}", flush=True)
                continue
            found: dict[str, dict] = {}
            # Chassis: /zapchasti-dlya-avtomobilej/{maker}/{model}/{chassis-id}
            for vm in re.finditer(
                rf'href="(/zapchasti-dlya-avtomobilej/{re.escape(slug)}/{re.escape(mslug)}/([a-z0-9-]+))/?"',
                mhtml,
                re.I,
            ):
                href, vslug = vm.group(1), vm.group(2).lower()
                code = re.sub(r"-\d+$", "", vslug).upper()
                found[vslug] = {
                    "code": code,
                    "variant_slug": vslug,
                    "frame": code,
                    "year_label": "",
                    "engine_code": "",
                    "source_url": _join(base, href),
                }
            for vm in re.finditer(
                rf'<a[^>]+href="(/zapchasti-dlya-avtomobilej/{re.escape(slug)}/{re.escape(mslug)}/([a-z0-9-]+))/?"[^>]*>([^<]{{1,80}})</a>',
                mhtml,
                re.I,
            ):
                vslug, label = vm.group(2).lower(), _clean(vm.group(3))
                if vslug in found and label:
                    found[vslug]["frame"] = label
                    found[vslug]["code"] = chassis_token(label) or found[vslug]["code"]
            model["chassis"] = list(found.values()) or [
                {
                    "code": chassis_token(mslug),
                    "variant_slug": mslug,
                    "frame": model["display_name"],
                    "year_label": "",
                    "engine_code": "",
                    "source_url": model["source_url"],
                }
            ]
        maker["models"] = sorted(models.values(), key=lambda x: x["display_name"].lower())
        out_makers.append(maker)
        dump("megazip", out_makers)
    dump("megazip", out_makers)


# --- 7zap --------------------------------------------------------------------


def harvest_7zap() -> None:
    print("=== 7zap ===")
    base = "https://7zap.com"
    st, html = fetch(base + "/en/catalog/cars/")
    print(f"  cars hub {st} bytes={len(html)}")
    brands = {}
    for m in re.finditer(
        r'"url"\s*:\s*"(https://([a-z0-9-]+)\.7zap\.com/en/[^"]+)"\s*,\s*"name"\s*:\s*"([^"]+)"',
        html,
        re.I,
    ):
        slug = m.group(2).lower()
        name = _clean(m.group(3)).replace(" OEM Parts Catalog", "")
        brands[slug] = {"name": name, "slug": slug, "hubs": set()}
    for m in re.finditer(r'href="((?:https://7zap\.com)?/en/catalog/cars/([a-z0-9-]+)(?:/([a-z0-9-]+))?/?)"', html, re.I):
        slug = m.group(2).lower()
        region = (m.group(3) or "europe").lower()
        brands.setdefault(slug, {"name": slug.replace("-", " ").title(), "slug": slug, "hubs": set()})
        brands[slug]["hubs"].add(f"/en/catalog/cars/{slug}/{region}/")
    # also collect region links from a first brand page later
    out_makers = []
    for slug, brand in sorted(brands.items(), key=lambda kv: kv[1]["name"].lower()):
        hubs = brand["hubs"] or {f"/en/catalog/cars/{slug}/europe/"}
        models: dict[str, dict] = {}
        regions_seen = set()
        for hub in list(hubs):
            time.sleep(DELAY)
            url = _join(base, hub)
            st, page = fetch(url)
            print(f"  {slug} {hub} {st} bytes={len(page)}")
            for rm in re.finditer(
                rf'href="((?:https://7zap\.com)?/en/catalog/cars/{re.escape(slug)}/([a-z0-9-]+)/?)"',
                page,
                re.I,
            ):
                region = rm.group(2).lower()
                if region.endswith("-parts-catalog"):
                    continue
                path = f"/en/catalog/cars/{slug}/{region}/"
                if path not in hubs and region not in regions_seen:
                    regions_seen.add(region)
                    hubs.add(path)
            for mm in re.finditer(
                rf'href="((?:https://7zap\.com)?/en/catalog/cars/{re.escape(slug)}/([a-z0-9-]+)/([a-z0-9%-]+)-parts-catalog/?)"',
                page,
                re.I,
            ):
                mslug = mm.group(3).lower()
                href = mm.group(1)
                absu = href if href.startswith("http") else _join(base, href)
                label = mslug.replace("-", " ")
                code_m = re.search(r"([a-z]{1,4}\d{1,3}[a-z]?)(?:-facelift)?$", mslug, re.I)
                code = (code_m.group(1) if code_m else mslug[:12]).upper()
                rec = models.setdefault(
                    mslug,
                    {
                        "display_name": label,
                        "slug": mslug,
                        "source_url": absu,
                        "chassis": [],
                    },
                )
                rec["chassis"].append(
                    {
                        "code": code,
                        "variant_slug": mslug,
                        "frame": label,
                        "year_label": mm.group(2),
                        "engine_code": "",
                        "source_url": absu,
                    }
                )
        # extra region hubs discovered
        for hub in list(hubs):
            if hub in brand["hubs"]:
                continue
            time.sleep(DELAY)
            url = _join(base, hub)
            st, page = fetch(url)
            print(f"  {slug} extra {hub} {st}")
            for mm in re.finditer(
                rf'href="((?:https://7zap\.com)?/en/catalog/cars/{re.escape(slug)}/([a-z0-9-]+)/([a-z0-9%-]+)-parts-catalog/?)"',
                page,
                re.I,
            ):
                mslug = mm.group(3).lower()
                href = mm.group(1)
                absu = href if href.startswith("http") else _join(base, href)
                label = mslug.replace("-", " ")
                code_m = re.search(r"([a-z]{1,4}\d{1,3}[a-z]?)(?:-facelift)?$", mslug, re.I)
                code = (code_m.group(1) if code_m else mslug[:12]).upper()
                rec = models.setdefault(
                    mslug,
                    {"display_name": label, "slug": mslug, "source_url": absu, "chassis": []},
                )
                rec["chassis"].append(
                    {
                        "code": code,
                        "variant_slug": mslug,
                        "frame": label,
                        "year_label": mm.group(2),
                        "engine_code": "",
                        "source_url": absu,
                    }
                )
        # dedupe chassis per model
        for rec in models.values():
            by = {}
            for row in rec["chassis"]:
                by[row["code"] + "|" + row.get("year_label", "")] = row
            rec["chassis"] = list(by.values()) or [
                {
                    "code": chassis_token(rec["slug"]),
                    "variant_slug": rec["slug"],
                    "frame": rec["display_name"],
                    "year_label": "",
                    "engine_code": "",
                    "source_url": rec["source_url"],
                }
            ]
        out_makers.append(
            {
                "name": brand["name"],
                "slug": slug,
                "source_url": _join(base, f"/en/catalog/cars/{slug}/europe/"),
                "models": sorted(models.values(), key=lambda x: x["display_name"].lower()),
            }
        )
        dump("7zap", out_makers)
    dump("7zap", out_makers)


# --- CatCar ------------------------------------------------------------------

CATCAR_MAKERS = [
    "nissan", "toyota", "honda", "mazda", "subaru", "mitsubishi", "suzuki",
    "mercedes", "bmw", "renault", "opel", "ford", "volvo", "peugeot", "citroen",
    "jaguar", "audi", "ssangyong",
]


def harvest_catcar() -> None:
    print("=== catcar ===")
    base = "https://www.catcar.info"
    st, html = fetch(base + "/")
    print(f"  home {st} bytes={len(html)}")
    slugs = set(CATCAR_MAKERS)
    for m in re.finditer(r'href="/(nissan|toyota|honda|mazda|subaru|mitsubishi|suzuki|mercedes|bmw|renault|opel|ford|volvo|peugeot|citroen|jaguar|audi|ssangyong)/', html, re.I):
        slugs.add(m.group(1).lower())
    out_makers = []
    for slug in sorted(slugs):
        hub = f"{base}/{slug}/?lang=en"
        time.sleep(DELAY)
        st, page = fetch(hub)
        print(f"  {slug} hub {st} bytes={len(page)}")
        models: dict[str, dict] = {}
        for m in re.finditer(
            rf'href="((?:https?://(?:www\.)?catcar\.info)?/{re.escape(slug)}/\?[^"]*?l=([^"\'&]+))"',
            page,
            re.I,
        ):
            href, token = m.group(1), m.group(2)
            absu = href if href.startswith("http") else _join(base, href)
            label = _decode_catcar_label(token) or f"market-{token[:8]}"
            mslug = re.sub(r"[^a-z0-9]+", "-", label.lower()).strip("-")
            models.setdefault(
                mslug,
                {"display_name": label, "slug": mslug, "source_url": absu, "chassis": []},
            )
        for mslug, model in list(models.items()):
            time.sleep(DELAY)
            st2, mhtml = fetch(model["source_url"])
            chassis = {}
            for cm in re.finditer(
                r'<a[^>]+href="([^"]*[?&]l=([^"\'&]+)[^"]*)"[^>]*>([^<]{2,80})</a>',
                mhtml,
                re.I,
            ):
                href, token, label = cm.group(1), cm.group(2), _clean(cm.group(3))
                code_m = re.search(r"\(([A-Z0-9]{2,12})\)", label) or re.search(
                    r"\b([A-Z]{1,3}\d{1,3}[A-Z]?)\b", label.upper()
                )
                if not code_m:
                    continue
                code = code_m.group(1).upper()
                absu = href if href.startswith("http") else _join(base, href)
                chassis[code] = {
                    "code": code,
                    "variant_slug": token,
                    "frame": label,
                    "year_label": "",
                    "engine_code": "",
                    "source_url": absu,
                }
            model["chassis"] = list(chassis.values())
            if not model["chassis"]:
                model["chassis"] = [
                    {
                        "code": chassis_token(model["display_name"]),
                        "variant_slug": model["slug"],
                        "frame": model["display_name"],
                        "year_label": "",
                        "engine_code": "",
                        "source_url": model["source_url"],
                    }
                ]
        out_makers.append(
            {
                "name": slug.replace("_", " ").title(),
                "slug": slug,
                "source_url": hub,
                "models": sorted(models.values(), key=lambda x: x["display_name"].lower()),
            }
        )
        dump("catcar", out_makers)
    dump("catcar", out_makers)


def _decode_catcar_label(token: str) -> str | None:
    try:
        raw = urllib.parse.unquote(token)
        pad = "=" * ((4 - len(raw) % 4) % 4)
        import base64

        decoded = base64.b64decode(raw + pad).decode("utf-8", "replace")
        m = re.search(r"['\"]20['\"]\s*:\s*['\"]([^'\"]+)['\"]", decoded)
        return m.group(1) if m else None
    except Exception:
        return None


# --- JapanCats ---------------------------------------------------------------


def harvest_japancats() -> None:
    """Regions are models; chassis parsed from javascript:submit(...) modification links."""
    print("=== japancats ===", flush=True)
    base = "https://www.japancats.ru"
    st, html = fetch("https://japancats.ru/")
    print(f"  home {st} bytes={len(html)}", flush=True)
    makers = {}
    for m in re.finditer(r'href="(/([A-Za-z][A-Za-z0-9-]*)/)"', html):
        slug = m.group(2)
        if slug.lower() in {"css", "moto", "js", "images", "scripts"}:
            continue
        makers[slug.lower()] = {
            "name": slug[:1].upper() + slug[1:],
            "slug": slug,
            "source_url": f"{base}/{slug}/",
        }
    # Disk flavors: hF (Nissan), mF (Mazda GUID), lF (Toyota table / Mitsubishi list)
    submit_re = re.compile(
        r"""javascript:submit\(\s*'([a-z]F)'\s*,\s*'([^']+)'\s*,\s*'([^']+)'""",
        re.I,
    )
    lf_row_re = re.compile(
        r"""submit\('lF','([^']+)','([0-9A-Fa-f]+);','true'\)\">([^<]*)</a>\s*<td>\s*<a[^>]*>\s*([^<]+)\s*</a>\s*<td>\s*<a[^>]*>\s*([^<]+)\s*</a>""",
        re.I,
    )
    out_makers = []
    for _key, maker in sorted(makers.items(), key=lambda kv: kv[1]["name"].lower()):
        time.sleep(DELAY)
        st, page = fetch(maker["source_url"])
        print(f"  {maker['slug']} {st} bytes={len(page)}", flush=True)
        regions: dict[str, str] = {}
        for m in re.finditer(
            r'<input[^>]+name=["\']r["\'][^>]+value=["\']([A-Z]{1,4})["\'][^>]*>\s*<label[^>]*>([^<]+)</label>',
            page,
            re.I,
        ):
            regions[m.group(1).upper()] = _clean(m.group(2)) or m.group(1)
        if not regions:
            regions = {
                "EL": "Europe",
                "US": "USA",
                "CA": "Canada",
                "GL": "Asia",
                "JP": "Japan",
            }
        models: dict[str, dict] = {}
        for code, label in regions.items():
            models[code] = {
                "display_name": label,
                "slug": code,
                "source_url": f"{maker['source_url']}?Region={code}",
                "chassis": [],
            }
        for code, model in list(models.items()):
            time.sleep(DELAY)
            st2, mhtml = fetch(model["source_url"])
            print(f"    region {code} {st2} bytes={len(mhtml)}", flush=True)
            chassis: dict[str, dict] = {}
            # Toyota/Lexus style grid rows: name | chassis | years
            for cm in lf_row_re.finditer(mhtml):
                disk_id, cat_id, name, ch_code, years = (
                    cm.group(1),
                    cm.group(2),
                    _clean(cm.group(3)),
                    _clean(cm.group(4)).upper(),
                    _clean(cm.group(5)),
                )
                if not ch_code:
                    continue
                chassis[f"{ch_code}|{cat_id}"] = {
                    "code": re.split(r"[,#]", ch_code)[0].strip()[:12],
                    "variant_slug": cat_id,
                    "frame": f"{name} ({ch_code})".strip() if name else ch_code,
                    "year_label": years,
                    "engine_code": "",
                    "source_url": model["source_url"],
                }
            for cm in submit_re.finditer(mhtml):
                kind, disk_id, payload = cm.group(1), cm.group(2), cm.group(3)
                parts = [p.strip() for p in payload.split(";") if p.strip()]
                years = ""
                # Skip pure numeric catalog ids already handled by lf_row_re
                if kind.lower() == "lf" and parts and re.fullmatch(r"[0-9A-Fa-f]+", parts[0]):
                    continue
                if kind.lower() == "mf" and parts and re.match(r"^[0-9a-f-]{8,}$", parts[0], re.I):
                    rest = parts[1] if len(parts) > 1 else ""
                    ym = re.search(r"\(([^)]*\d{4}[^)]*)\)", rest)
                    if ym:
                        years = ym.group(1)
                    name = re.sub(r"\([^)]*\d{4}[^)]*\)", "", rest).strip(" -;")
                    code_token = re.sub(r"[^A-Z0-9]", "", name.upper())[:12] or disk_id[:12]
                    frame = rest or name or code_token
                else:
                    # hF / Mitsubishi lF: CODE;disk;[CODE] NAME or CODE;disk;NAME (CODE)
                    code_token = (parts[0] if parts else "").upper()
                    title = ""
                    for part in parts:
                        if part.startswith("[") and "]" in part:
                            title = part
                            break
                    if not title and len(parts) >= 3:
                        title = parts[-1]
                    ym = re.search(r"\(([^)]*\d{4}[^)]*)\)", title or payload)
                    if ym:
                        years = ym.group(1)
                    frame = title.strip("[] ") if title else code_token
                if not code_token or len(code_token) < 2:
                    continue
                chassis.setdefault(
                    f"{code_token}|{disk_id}|{payload[:24]}",
                    {
                        "code": code_token[:16],
                        "variant_slug": disk_id,
                        "frame": frame or code_token,
                        "year_label": years,
                        "engine_code": "",
                        "source_url": model["source_url"],
                    },
                )
            if not chassis:
                for cm in re.finditer(r"\[([A-Z0-9]{2,12})\]\s*([^<\n]{2,80})", mhtml):
                    ccode, rest = cm.group(1).upper(), _clean(cm.group(2))
                    chassis[ccode] = {
                        "code": ccode,
                        "variant_slug": ccode.lower(),
                        "frame": f"[{ccode}] {rest}",
                        "year_label": "",
                        "engine_code": "",
                        "source_url": model["source_url"],
                    }
            model["chassis"] = list(chassis.values())
            if not model["chassis"]:
                model["chassis"] = [
                    {
                        "code": code,
                        "variant_slug": code,
                        "frame": model["display_name"],
                        "year_label": "",
                        "engine_code": "",
                        "source_url": model["source_url"],
                    }
                ]
            print(f"    region {code} chassis={len(model['chassis'])}", flush=True)
        out_makers.append(
            {
                **{k: maker[k] for k in ("name", "slug", "source_url")},
                "models": sorted(models.values(), key=lambda x: x["display_name"].lower()),
            }
        )
        dump("japancats", out_makers)
    dump("japancats", out_makers)


# --- Japan Parts EU ----------------------------------------------------------


def harvest_japan_parts() -> None:
    print("=== japan_parts ===")
    base = "https://japan-parts.eu"
    st, html = fetch(base + "/")
    print(f"  home {st} bytes={len(html)}")
    out_makers = []
    for slug, name in [("toyota", "Toyota"), ("lexus", "Lexus")]:
        time.sleep(DELAY)
        st, page = fetch(f"{base}/{slug}")
        models: dict[str, dict] = {}
        for m in re.finditer(rf'href="(/{slug}/([a-z]{{2}})/(\d{{4}})/?)"', page, re.I):
            href, region, year = m.group(1), m.group(2), m.group(3)
            mslug = year if region == "eu" else f"{region}-{year}"
            models.setdefault(
                mslug,
                {
                    "display_name": f"{region.upper()} {year}",
                    "slug": mslug,
                    "source_url": _join(base, href),
                    "chassis": [],
                },
            )
        for mslug, model in list(models.items()):
            time.sleep(DELAY)
            st2, mhtml = fetch(model["source_url"])
            chassis = {}
            for cm in re.finditer(
                rf'href="(/{slug}/([a-z]{{2}})/(\d{{4}})/([a-z0-9-]+)/?)"',
                mhtml,
                re.I,
            ):
                href, region, year, name_slug = cm.groups()
                tokens = list(re.finditer(r"([a-z]{1,3}\d{1,3}[a-z0-9]*)", name_slug, re.I))
                code = tokens[-1].group(1).upper() if tokens else name_slug[:12].upper()
                chassis[name_slug] = {
                    "code": code,
                    "variant_slug": name_slug,
                    "frame": name_slug.replace("-", " "),
                    "year_label": year,
                    "engine_code": "",
                    "source_url": _join(base, href),
                }
            model["chassis"] = list(chassis.values())
            if not model["chassis"]:
                model["chassis"] = [
                    {
                        "code": mslug,
                        "variant_slug": mslug,
                        "frame": model["display_name"],
                        "year_label": mslug,
                        "engine_code": "",
                        "source_url": model["source_url"],
                    }
                ]
        out_makers.append(
            {
                "name": name,
                "slug": slug,
                "source_url": f"{base}/{slug}",
                "models": sorted(models.values(), key=lambda x: x["display_name"], reverse=True),
            }
        )
        dump("japan_parts", out_makers)
    dump("japan_parts", out_makers)


# --- PartSouq ----------------------------------------------------------------


def harvest_partsouq() -> None:
    print("=== partsouq ===")
    base = "https://partsouq.com"
    # curated makers from pipeline config, plus live locate page
    makers_file = ROOT / "app" / "src" / "main" / "python" / "config" / "partsouq_makers.json"
    curated = json.loads(makers_file.read_text(encoding="utf-8")).get("makers") or []
    st, html = fetch(base + "/en/catalog/genuine/locate", force_flare=True)
    print(f"  locate {st} bytes={len(html)}")
    live: dict[str, dict] = {}
    for m in re.finditer(
        r'href="([^"]*(?:/maker/|/parts/|/catalog/)([A-Za-z0-9%+\-]+)[^"]*)"[^>]*>([^<]{2,48})<',
        html,
        re.I,
    ):
        slug = urllib.parse.unquote(m.group(2)).lower().replace(" ", "-")
        live[slug] = {
            "name": _clean(m.group(3)) or slug,
            "slug": slug,
            "source_url": m.group(1) if m.group(1).startswith("http") else _join(base, m.group(1)),
        }
    # also c= query brands
    for m in re.finditer(r"[?&]c=([A-Za-z0-9+\-]+)", html):
        slug = urllib.parse.unquote(m.group(1)).lower()
        live.setdefault(
            slug,
            {
                "name": slug.replace("-", " ").title(),
                "slug": slug,
                "source_url": f"{base}/en/catalog/genuine/locate?c={m.group(1)}",
            },
        )
    for name in curated:
        slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
        live.setdefault(
            slug,
            {
                "name": name,
                "slug": slug,
                "source_url": f"{base}/en/catalog/genuine/locate?c={urllib.parse.quote(name)}",
            },
        )
    out_makers = []
    for slug, maker in sorted(live.items(), key=lambda kv: kv[1]["name"].lower()):
        time.sleep(DELAY)
        st, page = fetch(maker["source_url"], force_flare=True)
        print(f"  {maker['name']} {st} bytes={len(page)}")
        models: dict[str, dict] = {}
        for m in re.finditer(
            r'href="([^"]+/vehicle/[^"]+)"[^>]*>([^<]{2,80})<',
            page,
            re.I,
        ):
            href, label = m.group(1), _clean(m.group(2))
            mslug = href.rstrip("/").split("/")[-1]
            models.setdefault(
                mslug,
                {
                    "display_name": label or mslug,
                    "slug": mslug,
                    "source_url": href if href.startswith("http") else _join(base, href),
                    "chassis": [],
                },
            )
        for m in re.finditer(r'href="([^"]+)"[^>]*>([^<]{2,80})</a>', page, re.I):
            href, label = m.group(1), _clean(m.group(2))
            if "catalog" not in href.lower() and "vehicle" not in href.lower():
                continue
            if any(x in href.lower() for x in ("locate", "login", "cart")):
                continue
            mslug = href.rstrip("/").split("/")[-1].split("?")[0]
            if len(mslug) < 2:
                continue
            models.setdefault(
                mslug,
                {
                    "display_name": label or mslug,
                    "slug": mslug,
                    "source_url": href if href.startswith("http") else _join(base, href),
                    "chassis": [],
                },
            )
        # chassis from same page (PartSouq often lists generations on maker locate)
        for rec in models.values():
            code = chassis_token(rec["display_name"])
            rec["chassis"] = [
                {
                    "code": code,
                    "variant_slug": rec["slug"],
                    "frame": rec["display_name"],
                    "year_label": "",
                    "engine_code": "",
                    "source_url": rec["source_url"],
                }
            ]
        if not models:
            # at least one synthetic model so the session UI can proceed
            models["catalog"] = {
                "display_name": "Genuine catalog",
                "slug": "catalog",
                "source_url": maker["source_url"],
                "chassis": [
                    {
                        "code": maker["slug"][:12].upper(),
                        "variant_slug": maker["slug"],
                        "frame": maker["name"],
                        "year_label": "",
                        "engine_code": "",
                        "source_url": maker["source_url"],
                    }
                ],
            }
        out_makers.append(
            {
                "name": maker["name"],
                "slug": maker["slug"],
                "source_url": maker["source_url"],
                "models": sorted(models.values(), key=lambda x: x["display_name"].lower()),
            }
        )
        dump("partsouq", out_makers)
    dump("partsouq", out_makers)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    harvest_japan_parts()
    harvest_japancats()
    harvest_catcar()
    harvest_7zap()
    harvest_megazip()
    harvest_partsouq()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
