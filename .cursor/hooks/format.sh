#!/usr/bin/env bash
# Auto-format edited files based on extension.
# Called by Cursor afterFileEdit hook.

set -euo pipefail

FILE="${1:-}"
if [ -z "$FILE" ] || [ ! -f "$FILE" ]; then
  exit 0
fi

case "$FILE" in
  *.ts|*.tsx|*.js|*.jsx)
    if command -v prettier &>/dev/null; then
      prettier --write "$FILE" 2>/dev/null || true
    fi
    ;;
  *.py)
    if command -v ruff &>/dev/null; then
      ruff format "$FILE" 2>/dev/null || true
    fi
    ;;
  *.kt)
    if [ -f "apps/android-customer/gradlew" ]; then
      # ktlint formatting handled by Gradle task when available
      true
    fi
    ;;
  *.swift)
    if command -v swiftformat &>/dev/null; then
      swiftformat "$FILE" 2>/dev/null || true
    fi
    ;;
  *.sql)
    if command -v sqlfluff &>/dev/null; then
      sqlfluff fix "$FILE" 2>/dev/null || true
    fi
    ;;
esac

exit 0
