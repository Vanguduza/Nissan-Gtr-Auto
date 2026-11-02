#!/usr/bin/env python3
"""Bind Android artifacts to a clean source revision and canonical-state digest."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True).strip()


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", required=True, choices=["customer-android", "management-android", "delivery-android"])
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--build-type", choices=["debug", "release"], required=True)
    args = parser.parse_args()
    if git("status", "--porcelain", "--untracked-files=normal"):
        raise SystemExit("Refusing artifact publication from an uncommitted source state.")
    state_path = Path("PROJECT_CANONICAL_STATE.json")
    state = json.loads(state_path.read_text())
    files = sorted([*args.directory.rglob("*.apk"), *args.directory.rglob("*.aab")])
    files = [path for path in files if any(part.lower().endswith(args.build_type) for part in path.parts)]
    if not files:
        raise SystemExit("No APK/AAB files were built; no artifact manifest created.")
    payload = {
        "repository": state["repository"],
        "branch": os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME") or git("branch", "--show-current"),
        "commit_sha": git("rev-parse", "HEAD"),
        "target_app": args.app,
        "build_type": args.build_type,
        "canonical_state_revision": hashlib.sha256(state_path.read_bytes()).hexdigest(),
        "artifacts": [
            {"path": str(path), "bytes": path.stat().st_size, "sha256": digest(path)}
            for path in files
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n")
    print(f"Android artifact provenance recorded for {len(files)} {args.build_type} artifact(s).")


if __name__ == "__main__":
    main()
