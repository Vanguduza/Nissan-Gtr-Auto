#!/usr/bin/env python3
"""Create the public test-only encrypted cache fixture with a legacy SQLCipher 4.5.4 CLI."""

import argparse
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--sqlcipher-4-5-4", required=True, type=Path)
args = parser.parse_args()
destination = Path(__file__).resolve().parents[1] / "feature/pos/src/androidTest/assets"
destination.mkdir(parents=True, exist_ok=True)
path = destination / "sqlcipher-4.5.4-cache.db"
if path.exists():
    path.unlink()
# Public, test-only bytes exercising the legacy unsigned-byte -> CharArray -> UTF-8 conversion.
test_key = "".join(chr(value) for value in bytes.fromhex("4180ffc3a9"))
sql = f"""PRAGMA key='{test_key}';
PRAGMA cipher_version;
PRAGMA user_version=7;
CREATE TABLE catalog_items(stock_item_id TEXT PRIMARY KEY,description TEXT NOT NULL);
INSERT INTO catalog_items VALUES('part-before-upgrade','Retained fixture part');
CREATE TABLE pending_sales(local_id TEXT PRIMARY KEY,payload_json TEXT NOT NULL);
INSERT INTO pending_sales VALUES('sale-before-upgrade','{{"cart":"retained","amount_minor":1250}}');
"""
result = subprocess.run([str(args.sqlcipher_4_5_4), str(path)], input=sql, text=True, capture_output=True, check=True)
version = next((line.strip() for line in result.stdout.splitlines() if line.strip().startswith("4.5.4")), None)
if version is None:
    path.unlink(missing_ok=True)
    raise SystemExit("Fixture generator requires the actual legacy SQLCipher 4.5.4 driver.")
try:
    with sqlite3.connect(path) as plain:
        plain.execute("SELECT * FROM pending_sales").fetchall()
except sqlite3.DatabaseError:
    pass
else:
    raise SystemExit("Fixture is unexpectedly readable without the encryption key.")
metadata = {
    "purpose": "Public test-only encrypted cache/outbox fixture; no production records or keys",
    "creator_cipher_version": version,
    "legacy_wrapped_test_bytes_hex": "4180ffc3a9",
    "schema_user_version": 7,
    "file": path.name,
    "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    "generator": "apps/android-management/tools/build_sqlcipher_compatibility_fixture.py",
    "source": "https://github.com/sqlcipher/sqlcipher/releases/tag/v4.5.4",
}
(destination / "sqlcipher-4.5.4-cache.provenance.json").write_text(json.dumps(metadata, indent=2) + "\n")
(destination / ".gitignore").write_text("!sqlcipher-4.5.4-cache.db\n")
print(f"Created encrypted legacy compatibility fixture ({path.stat().st_size} bytes).")
