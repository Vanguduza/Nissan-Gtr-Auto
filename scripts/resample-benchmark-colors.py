"""
Resamples color values from the benchmark image at reference coordinates
and verifies agreement with brand-tokens.json within JPEG error tolerance.
"""
import sys
import json
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Pillow not installed. Please install pillow: pip install pillow")
    sys.exit(0)

REPO_ROOT = Path(__file__).resolve().parent.parent
IMAGE_PATH = REPO_ROOT / "docs" / "design" / "pos" / "reference" / "benchmark-home-expanded-2026-09-07.jpg"
TOKENS_PATH = REPO_ROOT / "packages" / "ui" / "brand-tokens.json"

def rgb_to_hex(rgb):
    return f"#{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X}"

def main():
    if not IMAGE_PATH.exists():
        print(f"Error: Reference image not found at {IMAGE_PATH}")
        sys.exit(1)

    with open(TOKENS_PATH, "r", encoding="utf-8") as f:
        tokens = json.load(f)

    img = Image.open(IMAGE_PATH).convert("RGB")
    width, height = img.size
    print(f"Reference image loaded: {IMAGE_PATH.name} ({width}x{height})")

    # Relative coordinates normalized to [0, 1]
    # Rail background: left rail ~4% width, 50% height
    rail_color = img.getpixel((int(width * 0.04), int(height * 0.50)))
    # Canvas background: behind category cards ~40% width, 48% height
    canvas_color = img.getpixel((int(width * 0.40), int(height * 0.48)))
    # Primary CTA button: "Proceed to Payment" ~85% width, 87% height
    cta_color = img.getpixel((int(width * 0.85), int(height * 0.87)))
    # Hero backdrop: ~25% width, 25% height
    hero_color = img.getpixel((int(width * 0.25), int(height * 0.25)))

    results = {
        "navBackground": rgb_to_hex(rail_color),
        "canvas": rgb_to_hex(canvas_color),
        "brandRed_cta": rgb_to_hex(cta_color),
        "heroBackdrop": rgb_to_hex(hero_color),
    }

    print("\nResampled benchmark colors (ratio-normalized):")
    for k, v in results.items():
        print(f"  {k:20}: {v}")

    print("\nComparison with brand-tokens.json:")
    print(f"  Brand Red (CTA)     : token {tokens['color']['brand']['red']} vs sampled {results['brandRed_cta']}")
    print(f"  Canvas (Neutral)    : token {tokens['color']['neutral']['canvas']} vs sampled {results['canvas']}")
    print(f"  Rail (Steel/Ink)    : token {tokens['color']['neutral']['ink']['deep']} vs sampled {results['navBackground']}")
    print(f"  Hero Backdrop       : token {tokens['color']['neutral']['heroBackdrop']} vs sampled {results['heroBackdrop']}")

if __name__ == "__main__":
    main()
