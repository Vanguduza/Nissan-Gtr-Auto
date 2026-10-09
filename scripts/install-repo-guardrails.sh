#!/usr/bin/env sh
set -eu
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit .githooks/pre-push scripts/project_truth_guard.py || true
echo "Nissan GTR repository guardrails enabled via core.hooksPath=.githooks"
