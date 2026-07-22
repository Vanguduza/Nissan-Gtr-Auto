#!/usr/bin/env bash
# Run targeted tests based on which directories were changed.
# Called by Cursor subagentStop hook.

set -euo pipefail

CHANGED=$(git diff --name-only HEAD 2>/dev/null || echo "")

if [ -z "$CHANGED" ]; then
  exit 0
fi

RAN=false

# Supabase migrations
if echo "$CHANGED" | grep -q "^supabase/"; then
  if command -v supabase &>/dev/null && supabase status &>/dev/null 2>&1; then
    echo "Running Supabase migration validation..."
    supabase db lint 2>/dev/null || true
    RAN=true
  fi
fi

# Data pipeline
if echo "$CHANGED" | grep -q "^data-pipeline/"; then
  if [ -f "data-pipeline/pyproject.toml" ] || [ -f "data-pipeline/requirements.txt" ]; then
    echo "Running data pipeline tests..."
    (cd data-pipeline && python -m pytest -x -q 2>/dev/null) || true
    RAN=true
  fi
fi

# Web app
if echo "$CHANGED" | grep -q "^apps/web/"; then
  if [ -f "apps/web/package.json" ]; then
    echo "Running web tests..."
    (cd apps/web && pnpm test --passWithNoTests 2>/dev/null) || true
    RAN=true
  fi
fi

# Shared packages
if echo "$CHANGED" | grep -q "^packages/shared/"; then
  if [ -f "packages/shared/package.json" ]; then
    echo "Running shared package tests..."
    (cd packages/shared && pnpm test --passWithNoTests 2>/dev/null) || true
    RAN=true
  fi
fi

if [ "$RAN" = false ]; then
  echo "No test runner configured for changed files."
fi

exit 0
