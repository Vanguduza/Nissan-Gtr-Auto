#!/usr/bin/env python3
"""Verify 64-bit native ELF loads and uncompressed APK entries support 16KB pages."""

import argparse
import json
from pathlib import Path
import struct
from zipfile import ZIP_STORED, ZipFile


def load_alignments(data: bytes) -> list[int]:
    if len(data) < 64 or data[:5] != b"\x7fELF\x02" or data[5] not in {1, 2}:
        raise ValueError("invalid 64-bit ELF header")
    endian = "<" if data[5] == 1 else ">"
    table = struct.unpack_from(endian + "Q", data, 32)[0]
    stride, count = struct.unpack_from(endian + "HH", data, 54)
    if stride < 56 or count == 0 or table + stride * count > len(data):
        raise ValueError("invalid ELF program-header table")
    return [
        struct.unpack_from(endian + "Q", data, table + stride * index + 48)[0]
        for index in range(count)
        if struct.unpack_from(endian + "I", data, table + stride * index)[0] == 1
    ]


def inspect(path: Path) -> dict:
    findings = []
    errors = []
    with ZipFile(path) as archive:
        for entry in archive.infolist():
            name = entry.filename
            if not name.endswith(".so") or not any(f"/{abi}/" in name for abi in ("arm64-v8a", "x86_64")):
                continue
            try:
                alignments = load_alignments(archive.read(entry))
            except (ValueError, struct.error) as error:
                errors.append(f"{name}: {error}")
                continue
            if not alignments or min(alignments) < 16384:
                errors.append(f"{name}: PT_LOAD alignment is smaller than 16KB")
            zip_aligned = None
            if path.suffix == ".apk" and entry.compress_type == ZIP_STORED:
                with path.open("rb") as source:
                    source.seek(entry.header_offset + 26)
                    name_size, extra_size = struct.unpack("<HH", source.read(4))
                offset = entry.header_offset + 30 + name_size + extra_size
                zip_aligned = offset % 16384 == 0
                if not zip_aligned:
                    errors.append(f"{name}: uncompressed APK entry is not 16KB-aligned")
            findings.append({"library": name, "elf_load_alignment": alignments, "apk_zip_16kb_aligned": zip_aligned})
    return {"artifact": str(path), "libraries": findings, "errors": errors}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    artifacts = sorted([*args.directory.rglob("*.apk"), *args.directory.rglob("*.aab")])
    if not artifacts:
        raise SystemExit("No APK/AAB artifacts found for native alignment verification.")
    report = [inspect(path) for path in artifacts]
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n")
    errors = [message for item in report for message in item["errors"]]
    for message in errors:
        print("BLOCKED:", message)
    if not errors:
        print(f"16KB ELF/APK alignment verified for {len(artifacts)} artifact(s).")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
