#!/usr/bin/env python3
"""Offline checks for the canonical 94 vehicle-artwork WebP set."""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from upload_vehicle_artwork import (
    ART_DIR,
    EXPECTED_COUNT,
    FILENAME_RE,
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

    def test_filename_regex_rejects_traversal(self) -> None:
        self.assertTrue(FILENAME_RE.match("nissan_navara_d23.webp"))
        self.assertFalse(FILENAME_RE.match("../nissan_navara_d23.webp"))
        self.assertFalse(FILENAME_RE.match("nested/nissan_navara_d23.webp"))
        self.assertFalse(FILENAME_RE.match("nissan_navara_d23.png"))

    def test_resolver_does_not_point_at_qashqai(self) -> None:
        names = resolver_filenames()
        self.assertTrue(names)
        self.assertFalse(any("qashqai" in name for name in names))

    def test_fitment_mapping_json_matches_webp_set(self) -> None:
        mapping_path = REPO / "supabase" / "FITMENT_ART_MAPPING.json"
        self.assertTrue(mapping_path.is_file(), "pack mapping JSON must live in supabase/")
        mapping = json.loads(mapping_path.read_text(encoding="utf-8"))
        names = {
            Path(str(rule.get("asset") or "")).name
            for rule in mapping.get("rules", [])
            if rule.get("asset")
        }
        files = {path.name for path in ART_DIR.glob("*.webp")}
        self.assertEqual(names, files)
        csv_path = REPO / "supabase" / "FITMENT_ART_MAPPING.csv"
        self.assertTrue(csv_path.is_file(), "pack mapping CSV must live in supabase/")

    def test_android_bundles_the_set(self) -> None:
        # The customer app ships the artwork in its assets (works offline, no bucket needed),
        # so every canonical WebP must be bundled byte-for-byte.
        visual = REPO / "apps/android-customer/core/visual/src/main"
        url_kt = visual / "java/co/zw/nissangtr/customer/visual/VehicleArtworkUrl.kt"
        if not url_kt.is_file():
            self.skipTest("Android customer visual module not present")
        self.assertIn("file:///android_asset/vehicles/", url_kt.read_text(encoding="utf-8"))
        bundled = visual / "assets/vehicles"
        for art in ART_DIR.glob("*.webp"):
            copy = bundled / art.name
            self.assertTrue(copy.is_file(), f"{art.name} is not bundled in the Android app")
            self.assertEqual(copy.read_bytes(), art.read_bytes(), f"{art.name} differs from the canonical set")


if __name__ == "__main__":
    unittest.main()
