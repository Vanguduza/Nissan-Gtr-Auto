#!/usr/bin/env python3
"""Repository truth guard for Nissan-Gtr-Auto.

Commands:
  record                         Capture/stage a ledger entry for the staged change.
  verify-head                    Verify HEAD has a matching automatic ledger entry.
  release-check --app APP        Refuse builds from stale/unresolved app lineages.
  status                         Print current repo/app truth status.

The guard intentionally treats Git history + PROJECT_CANONICAL_STATE.json as authority.
Chat/agent memory is never accepted as proof of current lineage.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from typing import Iterable

REPO_ROOT = Path(__file__).resolve().parents[1]
STATE_FILE = REPO_ROOT / "PROJECT_CANONICAL_STATE.json"
LEDGER_FILE = REPO_ROOT / "docs" / "project-state" / "CHANGE_LEDGER.jsonl"
LAST_CHANGE_FILE = REPO_ROOT / "docs" / "project-state" / "LAST_CHANGE.json"
MANAGED_PATHS = {
    "docs/project-state/CHANGE_LEDGER.jsonl",
    "docs/project-state/LAST_CHANGE.json",
}


def git(*args: str, check: bool = True, text: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        check=check,
        text=text,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def repo_rel(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def current_branch() -> str:
    return git("rev-parse", "--abbrev-ref", "HEAD").stdout.strip()


def head_sha() -> str:
    return git("rev-parse", "HEAD").stdout.strip()


def first_parent(ref: str = "HEAD") -> str | None:
    cp = git("rev-parse", f"{ref}^1", check=False)
    return cp.stdout.strip() if cp.returncode == 0 else None


def excluded_pathspecs() -> list[str]:
    return [f":(exclude){path}" for path in sorted(MANAGED_PATHS)]


def staged_changed_files() -> list[str]:
    cp = git(
        "diff",
        "--cached",
        "--name-only",
        "--no-renames",
        "--",
        ".",
        *excluded_pathspecs(),
    )
    return [line.strip() for line in cp.stdout.splitlines() if line.strip()]


def changed_files_between(base: str, head: str) -> list[str]:
    cp = git(
        "diff",
        "--name-only",
        "--no-renames",
        base,
        head,
        "--",
        ".",
        *excluded_pathspecs(),
    )
    return [line.strip() for line in cp.stdout.splitlines() if line.strip()]


def diff_digest_staged() -> str:
    cp = git(
        "diff",
        "--cached",
        "--binary",
        "--no-renames",
        "--",
        ".",
        *excluded_pathspecs(),
        text=False,
    )
    return hashlib.sha256(cp.stdout).hexdigest()


def diff_digest_between(base: str, head: str) -> str:
    cp = git(
        "diff",
        "--binary",
        "--no-renames",
        base,
        head,
        "--",
        ".",
        *excluded_pathspecs(),
        text=False,
    )
    return hashlib.sha256(cp.stdout).hexdigest()


def classify_domains(files: Iterable[str]) -> list[str]:
    domains: set[str] = set()
    for path in files:
        if path.startswith("apps/android-customer/"):
            domains.add("customer-android")
        elif path.startswith("apps/android-management/feature/pos/"):
            domains.add("pos-android")
            domains.add("management-android")
        elif path.startswith("apps/android-management/"):
            domains.add("management-android")
        elif path.startswith("apps/android-delivery/"):
            domains.add("delivery-android")
        elif path.startswith("apps/web/"):
            domains.add("web")
        elif path.startswith("supabase/"):
            domains.add("backend")
        elif path.startswith("data-pipeline/"):
            domains.add("catalog-data")
        elif path.startswith("packages/android-ui/"):
            domains.update({"customer-android", "pos-android", "management-android", "delivery-android"})
        elif path.startswith(".github/workflows/"):
            domains.add("ci")
        elif path.startswith("docs/"):
            domains.add("documentation")
        else:
            domains.add("repository")
    return sorted(domains)


def load_state() -> dict:
    if not STATE_FILE.exists():
        raise SystemExit("BLOCKED: PROJECT_CANONICAL_STATE.json is missing")
    with STATE_FILE.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def append_ledger(entry: dict) -> None:
    LEDGER_FILE.parent.mkdir(parents=True, exist_ok=True)
    existing = LEDGER_FILE.read_text(encoding="utf-8") if LEDGER_FILE.exists() else ""
    with LEDGER_FILE.open("w", encoding="utf-8", newline="\n") as fh:
        fh.write(existing)
        if existing and not existing.endswith("\n"):
            fh.write("\n")
        fh.write(json.dumps(entry, sort_keys=True, separators=(",", ":")))
        fh.write("\n")
    LAST_CHANGE_FILE.write_text(json.dumps(entry, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    git("add", repo_rel(LEDGER_FILE), repo_rel(LAST_CHANGE_FILE))


def read_last_ledger_entry(ref: str = "HEAD") -> dict | None:
    cp = git("show", f"{ref}:docs/project-state/CHANGE_LEDGER.jsonl", check=False)
    if cp.returncode != 0:
        return None
    lines = [line for line in cp.stdout.splitlines() if line.strip()]
    if not lines:
        return None
    return json.loads(lines[-1])


def record() -> int:
    files = staged_changed_files()
    if not files:
        print("project-truth: no non-ledger staged changes")
        return 0
    digest = diff_digest_staged()
    parent = head_sha()
    entry = {
        "schema_version": 1,
        "change_id": digest[:20],
        "recorded_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "branch_at_record": current_branch(),
        "source_parent": parent,
        "diff_sha256": digest,
        "domains": classify_domains(files),
        "changed_files": files,
        "actor": os.getenv("GTR_CHANGE_ACTOR") or os.getenv("GITHUB_ACTOR") or os.getenv("USER") or "unknown",
        "intent": os.getenv("GTR_CHANGE_INTENT", "automatic-file-level-record"),
    }
    append_ledger(entry)
    print(f"project-truth: staged ledger entry {entry['change_id']} for {len(files)} file(s)")
    return 0


def verify_head() -> int:
    parent = first_parent("HEAD")
    if parent is None:
        print("project-truth: root commit; verification skipped")
        return 0

    files = changed_files_between(parent, "HEAD")
    if not files:
        print("project-truth: HEAD changes only guard metadata; OK")
        return 0

    entry = read_last_ledger_entry("HEAD")
    if not entry:
        print("BLOCKED: HEAD changes repository content but contains no automatic change-ledger entry", file=sys.stderr)
        return 20

    expected_digest = diff_digest_between(parent, "HEAD")
    failures: list[str] = []
    if entry.get("source_parent") != parent:
        failures.append(f"ledger source_parent={entry.get('source_parent')} but HEAD^1={parent}")
    if entry.get("diff_sha256") != expected_digest:
        failures.append("ledger diff digest does not match the committed change")
    if sorted(entry.get("changed_files", [])) != sorted(files):
        failures.append("ledger changed_files does not match the committed change")

    if failures:
        print("BLOCKED: repository truth ledger mismatch:", file=sys.stderr)
        for failure in failures:
            print(f" - {failure}", file=sys.stderr)
        return 21

    print(f"project-truth: HEAD verified by change {entry.get('change_id')}")
    return 0


def release_check(app: str) -> int:
    state = load_state()
    apps = state.get("applications", {})
    if app not in apps:
        print(f"BLOCKED: app '{app}' is not registered in PROJECT_CANONICAL_STATE.json", file=sys.stderr)
        return 30
    app_state = apps[app]
    if app_state.get("release_blocked_until_reconciled"):
        print(
            f"BLOCKED: {app} is marked release_blocked_until_reconciled. "
            "Resolve/reconcile its canonical lineage and update PROJECT_CANONICAL_STATE.json before building.",
            file=sys.stderr,
        )
        return 31
    required = app_state.get("required_ancestor")
    if not required:
        print(f"BLOCKED: {app} has no required_ancestor recorded", file=sys.stderr)
        return 32
    cp = git("merge-base", "--is-ancestor", required, "HEAD", check=False)
    if cp.returncode != 0:
        print(
            f"BLOCKED: HEAD {head_sha()} does not contain required canonical ancestor {required} for {app}",
            file=sys.stderr,
        )
        return 33
    print(f"project-truth: {app} lineage verified at HEAD {head_sha()}")
    return 0


def status() -> int:
    state = load_state()
    print(f"repository: {state.get('repository')}")
    print(f"branch:     {current_branch()}")
    print(f"HEAD:       {head_sha()}")
    print("applications:")
    for app, app_state in state.get("applications", {}).items():
        blocked = app_state.get("release_blocked_until_reconciled", True)
        required = app_state.get("required_ancestor", "UNRESOLVED")
        print(f"  - {app}: status={app_state.get('status')} blocked={blocked} required={required}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("record")
    sub.add_parser("verify-head")
    rel = sub.add_parser("release-check")
    rel.add_argument("--app", required=True)
    sub.add_parser("status")
    args = parser.parse_args()

    if args.command == "record":
        return record()
    if args.command == "verify-head":
        return verify_head()
    if args.command == "release-check":
        return release_check(args.app)
    if args.command == "status":
        return status()
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
