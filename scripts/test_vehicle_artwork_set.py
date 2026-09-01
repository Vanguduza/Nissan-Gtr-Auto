#!/usr/bin/env python3
"""Offline checks for the canonical 94 vehicle-artwork WebP set."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from upload_vehicle_artwork import (
    ART_DIR,
    EXPECTED_COUNT,
    public_object_url,
    resolver_filenames,
    verify_local_set,
)

REPO = Path(__file__).resolve().parents[1]


class VehicleArtworkSetTest(unittest.TestCase):
    def test_local_set_covers_resolver(self) -> None:
        errors = verify_local_set()
        self.assertEqual(errors, [], msg="\n".join(errors))

    def test_count(self) -> None:
        files = list(ART_DIR.glob("*.webp"))
        self.assertEqual(len(files), EXPECTED_COUNT)

    def test_public_url_uses_filename_only(self) -> None:
        url = public_object_url(
            "https://gylrgwqyuiwkyykardwc.supabase.co",
            "nissan_navara_d23.webp",
        )
        self.assertEqual(
            url,
            "https://gylrgwqyuiwkyykardwc.supabase.co"
            "/storage/v1/object/public/vehicle-artwork/nissan_navara_d23.webp",
        )

    def test_resolver_does_not_point_at_qashqai(self) -> None:
        names = resolver_filenames()
        self.assertTrue(names)
        self.assertFalse(any("qashqai" in name for name in names))

    def test_kotlin_public_base_matches_bucket(self) -> None:
        url_kt = (
            REPO
            / "apps/android-customer/core/visual/src/main/java"
            / "co/zw/nissangtr/customer/visual/VehicleArtworkUrl.kt"
        )
        if not url_kt.is_file():
            self.skipTest("Android customer visual module not present")
        text = url_kt.read_text(encoding="utf-8")
        self.assertIn("/storage/v1/object/public/vehicle-artwork/", text)


if __name__ == "__main__":
    unittest.main()
