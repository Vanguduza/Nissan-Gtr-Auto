"""Generate minimal placeholder PNGs for Navara diagram fixtures (no secrets)."""
from __future__ import annotations

import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "diagrams" / "navara-d40"

NAMES = [
    "15208-oil-filter.png",
    "40206-brake-disc.png",
    "21410-water-pump.png",
    "16546-air-filter.png",
]
COLORS = [
    (0xC8, 0x10, 0x2E),
    (0x12, 0x15, 0x1C),
    (0xC0, 0xC5, 0xCE),
    (0x2A, 0x5A, 0x8C),
]


def _chunk(tag: bytes, data: bytes) -> bytes:
    return (
        struct.pack(">I", len(data))
        + tag
        + data
        + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    )


def png(w: int, h: int, rgb: tuple[int, int, int]) -> bytes:
    raw = b"".join(b"\x00" + bytes(rgb) * w for _ in range(h))
    return (
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
        + _chunk(b"IDAT", zlib.compress(raw, 9))
        + _chunk(b"IEND", b"")
    )


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    for name, color in zip(NAMES, COLORS, strict=True):
        path = ROOT / name
        path.write_bytes(png(64, 64, color))
        print(f"{path.name} {path.stat().st_size}")


if __name__ == "__main__":
    main()
