#!/usr/bin/env python3
"""
POS Design Lint — Enforces Blueprint §11.4 rules across POS codebase.
Exit 0: All checks pass.
Exit 1: Design lint violations detected.
"""

import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

SCAN_DIRS = [
    REPO_ROOT / "packages" / "pos-design" / "src",
    REPO_ROOT / "apps" / "android-management" / "feature" / "pos-domain" / "src",
    REPO_ROOT / "apps" / "android-management" / "feature" / "pos-data" / "src",
    REPO_ROOT / "apps" / "android-management" / "feature" / "pos-ui" / "src",
]

# Excluded generated files or token definition files
ALLOWED_RAW_COLOR_FILES = {
    "PosTokens.kt",
    "brand-tokens.json",
    "tokens.ts",
}

violations = []

def add_violation(rule_id, file_path, line_num, message):
    rel_path = file_path.relative_to(REPO_ROOT)
    violations.append(f"[{rule_id}] {rel_path}:{line_num}: {message}")

# Rule regexes
RE_RAW_COLOR_LITERAL = re.compile(r'Color\(0x[0-9a-fA-F]+\)')
RE_ROUNDED_CORNER = re.compile(r'RoundedCornerShape\(\s*\d+(\.\d+)?\.dp\s*\)')
RE_M3_COLOR = re.compile(r'MaterialTheme\.colorScheme')
RE_M3_TYPE = re.compile(r'MaterialTheme\.typography')
RE_ICONS_EXTENDED = re.compile(r'androidx\.compose\.material\.icons')
RE_REPORTS_NAV = re.compile(r'["\']Reports["\']|NavDestination\.Reports')
RE_LAZY_ROW_LITERAL_COUNT = re.compile(r'items\(\s*\d+\s*\)')
RE_UNGUARDED_BLUR = re.compile(r'Modifier\.blur\(')
RE_DOMAIN_ANDROID = re.compile(r'import\s+(android\.|androidx\.)')

def scan_file(file_path: Path):
    is_test_file = "test" in file_path.parts
    is_domain_file = "pos-domain" in file_path.parts
    file_name = file_path.name

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
    except Exception as e:
        return

    for idx, line in enumerate(lines, start=1):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*"):
            continue

        # Rule 1: Raw color literals outside token definitions
        if file_name not in ALLOWED_RAW_COLOR_FILES and not is_test_file:
            if RE_RAW_COLOR_LITERAL.search(line):
                add_violation("DL-01", file_path, idx, "Raw Color literal outside token definition.")

        # Rule 2: Ad hoc RoundedCornerShape
        if file_name not in ALLOWED_RAW_COLOR_FILES and not is_test_file:
            if RE_ROUNDED_CORNER.search(line):
                add_violation("DL-02", file_path, idx, "Ad hoc RoundedCornerShape outside design token system.")

        # Rule 3: MaterialTheme colorScheme / typography in POS components
        if RE_M3_COLOR.search(line):
            add_violation("DL-03", file_path, idx, "Forbidden reference to MaterialTheme.colorScheme in POS component.")
        if RE_M3_TYPE.search(line):
            add_violation("DL-03", file_path, idx, "Forbidden reference to MaterialTheme.typography in POS component.")

        # Rule 6: material-icons-extended
        if RE_ICONS_EXTENDED.search(line):
            add_violation("DL-06", file_path, idx, "Forbidden material-icons dependency; use Lucide tokens.")

        # Rule 7: Reports navigation destination
        if RE_REPORTS_NAV.search(line):
            add_violation("DL-07", file_path, idx, "Forbidden 'Reports' navigation destination in POS.")

        # Rule 8: Literal count in LazyRow items()
        if RE_LAZY_ROW_LITERAL_COUNT.search(line):
            add_violation("DL-08", file_path, idx, "Forbidden integer literal item count in lazy layout; counts must be derived.")

        # Rule: Domain purity (ARCH-03)
        if is_domain_file and not is_test_file:
            if RE_DOMAIN_ANDROID.search(line):
                add_violation("ARCH-03", file_path, idx, "Forbidden Android/AndroidX import in pos-domain module.")

def main():
    print("Running POS Design Lint across clean modules...")
    for target_dir in SCAN_DIRS:
        if not target_dir.exists():
            continue
        for root, _, files in os.walk(target_dir):
            for file in files:
                if file.endswith((".kt", ".java")):
                    scan_file(Path(root) / file)

    if violations:
        print(f"\nFAILED: {len(violations)} design lint violation(s) detected:")
        for v in violations:
            print(f"  {v}")
        sys.exit(1)
    else:
        print("PASSED: 0 design lint violations detected across POS modules.")
        sys.exit(0)

if __name__ == "__main__":
    main()
