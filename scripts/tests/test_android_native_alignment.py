import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED, ZIP_STORED

SPEC = importlib.util.spec_from_file_location("android_native_alignment", Path(__file__).parents[1] / "android_native_alignment.py")
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class NativeAlignmentTest(unittest.TestCase):
    def elf(self, alignment):
        data = bytearray(120)
        data[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 1)
        struct.pack_into("<I", data, 64, 1)
        struct.pack_into("<Q", data, 112, alignment)
        return bytes(data)

    def test_exposes_4kb_legacy_load_alignment(self):
        self.assertEqual([4096], CHECK.load_alignments(self.elf(4096)))

    def test_reads_16kb_native_load_alignment(self):
        self.assertEqual([16384], CHECK.load_alignments(self.elf(16384)))

    def test_truncated_or_non_elf_input_fails_closed(self):
        with self.assertRaises(ValueError):
            CHECK.load_alignments(self.elf(16384)[:70])
        with self.assertRaises(ValueError):
            CHECK.load_alignments(b"not-an-elf")

    def inspect_apk(self, alignment, stored=False, padded=False):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            entry = ZipInfo("lib/arm64-v8a/libfixture.so")
            entry.compress_type = ZIP_STORED if stored else ZIP_DEFLATED
            if padded:
                padding = 16384 - 30 - len(entry.filename.encode())
                entry.extra = struct.pack("<HH", 0xcafe, padding - 4) + bytes(padding - 4)
            with ZipFile(path, "w") as archive:
                archive.writestr(entry, self.elf(alignment))
            return CHECK.inspect(path)

    def test_legacy_4kb_native_library_blocks_artifact(self):
        result = self.inspect_apk(4096)
        self.assertTrue(any("PT_LOAD" in error for error in result["errors"]))

    def test_uncompressed_unaligned_native_entry_blocks_artifact(self):
        result = self.inspect_apk(16384, stored=True)
        self.assertTrue(any("APK entry" in error for error in result["errors"]))

    def test_aligned_native_artifact_passes(self):
        self.assertEqual([], self.inspect_apk(16384, stored=True, padded=True)["errors"])
        self.assertEqual([], self.inspect_apk(16384)["errors"])


if __name__ == "__main__":
    unittest.main()
