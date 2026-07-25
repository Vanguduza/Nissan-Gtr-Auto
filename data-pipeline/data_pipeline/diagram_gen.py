"""Render original placeholder section diagrams (SVG) from fixture packs.

Reads each pack's ``fast_source.json``, groups parts by shared
``diagram_path``, and renders one exploded-style SVG per section with
numbered callouts positioned exactly on the part's fractional bbox.
The SVG art is original line art (rights-cleared) — never scraped from
FAST/Amayama/7zap. Bboxes in ``part_fitment`` are the single source of
truth; callouts are drawn from the same values, so hotspots always match.

Run: ``python -m data_pipeline.diagram_gen`` (regenerates all packs).
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

PACKAGE_ROOT = Path(__file__).resolve().parent.parent
FIXTURES_DIR = PACKAGE_ROOT / "fixtures"

CANVAS_W = 800
CANVAS_H = 600

_INK = "#2a2f38"
_FAINT = "#9aa3b2"
_ACCENT = "#c8102e"


def _glyph(shape_index: int, x: float, y: float, w: float, h: float) -> str:
    """Simple original line-art glyph inside the bbox — varies by index."""
    cx, cy = x + w / 2, y + h / 2
    r = min(w, h) / 2 - 4
    style = f'fill="none" stroke="{_INK}" stroke-width="2.5"'
    kind = shape_index % 5
    if kind == 0:  # disc with bolt holes
        holes = "".join(
            f'<circle cx="{cx + r * 0.55 * dx:.1f}" cy="{cy + r * 0.55 * dy:.1f}" '
            f'r="{r * 0.12:.1f}" {style}/>'
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
        )
        return (
            f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r:.1f}" {style}/>'
            f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r * 0.35:.1f}" {style}/>'
            f"{holes}"
        )
    if kind == 1:  # finned box (radiator / filter housing)
        fins = "".join(
            f'<line x1="{x + w * f:.1f}" y1="{y + 6:.1f}" '
            f'x2="{x + w * f:.1f}" y2="{y + h - 6:.1f}" '
            f'stroke="{_INK}" stroke-width="1.5"/>'
            for f in (0.25, 0.4, 0.55, 0.7)
        )
        return (
            f'<rect x="{x + 2:.1f}" y="{y + 2:.1f}" width="{w - 4:.1f}" '
            f'height="{h - 4:.1f}" rx="6" {style}/>{fins}'
        )
    if kind == 2:  # gear
        teeth = "".join(
            f'<line x1="{cx:.1f}" y1="{cy:.1f}" '
            f'x2="{cx + (r + 3) * _COS[i]:.1f}" y2="{cy + (r + 3) * _SIN[i]:.1f}" '
            f'stroke="{_INK}" stroke-width="2"/>'
            for i in range(8)
        )
        return (
            f"{teeth}"
            f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r * 0.75:.1f}" '
            f'fill="#fff" stroke="{_INK}" stroke-width="2.5"/>'
            f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r * 0.25:.1f}" {style}/>'
        )
    if kind == 3:  # coil spring
        turns = 4
        step = (h - 12) / turns
        path = f"M {x + w * 0.25:.1f} {y + 6:.1f} "
        for i in range(turns):
            path += (
                f"C {x + w * 0.95:.1f} {y + 6 + i * step:.1f} "
                f"{x + w * 0.05:.1f} {y + 6 + (i + 0.5) * step:.1f} "
                f"{x + w * 0.75:.1f} {y + 6 + (i + 1) * step:.1f} "
            )
        return f'<path d="{path}" {style}/>'
    # kind == 4: cylinder (pump / motor)
    return (
        f'<ellipse cx="{cx:.1f}" cy="{y + h * 0.18:.1f}" rx="{w * 0.4:.1f}" '
        f'ry="{h * 0.14:.1f}" {style}/>'
        f'<line x1="{cx - w * 0.4:.1f}" y1="{y + h * 0.18:.1f}" '
        f'x2="{cx - w * 0.4:.1f}" y2="{y + h * 0.82:.1f}" '
        f'stroke="{_INK}" stroke-width="2.5"/>'
        f'<line x1="{cx + w * 0.4:.1f}" y1="{y + h * 0.18:.1f}" '
        f'x2="{cx + w * 0.4:.1f}" y2="{y + h * 0.82:.1f}" '
        f'stroke="{_INK}" stroke-width="2.5"/>'
        f'<ellipse cx="{cx:.1f}" cy="{y + h * 0.82:.1f}" rx="{w * 0.4:.1f}" '
        f'ry="{h * 0.14:.1f}" {style}/>'
    )


# cos/sin for 8 gear teeth (avoid math import churn in f-strings)
_COS = [1.0, 0.707, 0.0, -0.707, -1.0, -0.707, 0.0, 0.707]
_SIN = [0.0, 0.707, 1.0, 0.707, 0.0, -0.707, -1.0, -0.707]


def _esc(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def render_section_svg(
    title: str,
    vehicle: str,
    parts: list[dict[str, Any]],
) -> str:
    """Render one section diagram. ``parts`` need bbox fractions + labels."""
    body: list[str] = []
    for idx, part in enumerate(parts):
        x = float(part["bbox_x"]) * CANVAS_W
        y = float(part["bbox_y"]) * CANVAS_H
        w = float(part["bbox_width"]) * CANVAS_W
        h = float(part["bbox_height"]) * CANVAS_H
        num = f"{idx + 1:02d}"
        callout_x = x + w - 4
        callout_y = y + 4
        body.append(f"<g>{_glyph(idx, x, y, w, h)}")
        # leader line from glyph to callout badge
        body.append(
            f'<line x1="{x + w / 2:.1f}" y1="{y + h / 2:.1f}" '
            f'x2="{callout_x:.1f}" y2="{callout_y:.1f}" '
            f'stroke="{_FAINT}" stroke-width="1" stroke-dasharray="4 3"/>'
        )
        body.append(
            f'<circle cx="{callout_x:.1f}" cy="{callout_y:.1f}" r="13" '
            f'fill="#fff" stroke="{_ACCENT}" stroke-width="2"/>'
            f'<text x="{callout_x:.1f}" y="{callout_y + 4:.1f}" '
            f'text-anchor="middle" font-family="monospace" font-size="12" '
            f'fill="{_INK}">{num}</text>'
        )
        label = _esc(str(part.get("label", "")))
        if label:
            body.append(
                f'<text x="{x:.1f}" y="{y + h + 16:.1f}" '
                f'font-family="sans-serif" font-size="11" '
                f'fill="{_FAINT}">{num} · {label}</text>'
            )
        body.append("</g>")

    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'viewBox="0 0 {CANVAS_W} {CANVAS_H}" role="img" '
        f'aria-label="{_esc(title)} exploded diagram">'
        f'<rect width="{CANVAS_W}" height="{CANVAS_H}" fill="#ffffff"/>'
        f'<rect x="6" y="6" width="{CANVAS_W - 12}" height="{CANVAS_H - 12}" '
        f'fill="none" stroke="{_FAINT}" stroke-width="1"/>'
        f'<text x="24" y="34" font-family="sans-serif" font-size="20" '
        f'font-weight="bold" fill="{_INK}">{_esc(title)}</text>'
        f'<text x="24" y="54" font-family="sans-serif" font-size="13" '
        f'fill="{_FAINT}">{_esc(vehicle)}</text>'
        f'{"".join(body)}'
        f'<text x="24" y="{CANVAS_H - 18}" font-family="sans-serif" '
        f'font-size="10" fill="{_FAINT}">Original placeholder art — '
        f"Nissan GTR Auto ERP fixture pack (not derived from FAST/Amayama/7zap)"
        f"</text></svg>"
    )


def generate_pack(pack_dir: Path) -> list[Path]:
    """Generate all section SVGs for one fixture pack. Returns written paths."""
    source = pack_dir / "fast_source.json"
    if not source.exists():
        return []
    doc = json.loads(source.read_text(encoding="utf-8"))
    vehicle = str(doc.get("model_variant", pack_dir.name))

    sections: dict[str, dict[str, Any]] = {}
    for assembly in doc.get("assemblies", []):
        path = assembly.get("diagram_path")
        if not path or not str(path).endswith(".svg"):
            continue
        section = sections.setdefault(
            path, {"category": assembly.get("category_name", "Section"), "parts": []}
        )
        for part in assembly.get("parts", []):
            if part.get("bbox_x") is None:
                continue
            section["parts"].append(
                {
                    **part,
                    "label": f"{assembly.get('subcategory_name') or ''} "
                    f"({part['oem_part_number']})".strip(),
                }
            )

    written: list[Path] = []
    for storage_path, section in sections.items():
        out_path = pack_dir / "diagrams" / Path(storage_path)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        svg = render_section_svg(
            f"{section['category']} — exploded view", vehicle, section["parts"]
        )
        out_path.write_text(svg, encoding="utf-8", newline="\n")
        written.append(out_path)
    return written


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description="Generate fixture section SVGs.")
    parser.add_argument(
        "packs",
        nargs="*",
        type=Path,
        help="Fixture pack dirs (default: all under fixtures/)",
    )
    args = parser.parse_args(argv)

    packs = args.packs or sorted(
        p for p in FIXTURES_DIR.iterdir() if (p / "fast_source.json").exists()
    )
    total = 0
    for pack in packs:
        written = generate_pack(pack)
        total += len(written)
        for path in written:
            print(f"wrote {path.relative_to(PACKAGE_ROOT)}")
    print(f"OK: {total} diagram(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
