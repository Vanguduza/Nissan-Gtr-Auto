#!/usr/bin/env python3
"""Verify immutable schema provenance and, optionally, an isolated local Supabase DB.

Static verification is the default. --container additionally checks native schema
capabilities. --smoke runs the critical acceptance SQL against that LOCAL Docker
container; initialize it with the normal Supabase local reset/seed first.
No remote database URL, production reset, credential lookup or migration repair
operation is accepted by this tool.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "supabase/baseline/manifest.json"
CRITICAL_SMOKES = (
    "baseline_contract_smoke.sql",
    "phase2_rls_smoke.sql",
    "phase14_ci_smoke.sql",
    "phase3_finance_smoke.sql",
    "phase4_inventory_smoke.sql",
    "pos_operator_discovery_smoke.sql",
    "pos_vehicle_context_smoke.sql",
    "pos_customer_garage_popular_items_smoke.sql",
    "pos_hidden_bestsellers_smoke.sql",
    "vehicle_artwork_storage_smoke.sql",
    "customer_garage_identity_smoke.sql",
    "payment_settlement_reconciliation_smoke.sql",
    "web_pos_sale_flow_smoke.sql",
    "retained_workflow_posting_smoke.sql",
    "staff_self_service_reconciliation_smoke.sql",
    "staff_payroll_reconciliation_smoke.sql",
    "staff_overview_merchandising_reconciliation_smoke.sql",
)


def checked_file(relative: str, expected: str) -> None:
    path = (ROOT / relative).resolve()
    if not path.is_relative_to(ROOT) or not path.is_file():
        raise ValueError(f"Missing or invalid provenance path: {relative}")
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        raise ValueError(f"Immutable schema provenance mismatch: {relative}")


def static_check() -> dict:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    checked_file(manifest["baseline_file"], manifest["baseline_sha256"])
    checked_file(manifest["source_contract_file"], manifest["source_contract_sha256"])
    for group in ("legacy_history", "hosted_history", "sources"):
        for source in manifest[group]:
            checked_file(source["path"], source["sha256"])
    if len(manifest["legacy_history"]) != 173 or len(manifest["hosted_history"]) != 125:
        raise ValueError("Historical inventory does not match the reviewed baseline")
    active = sorted((ROOT / "supabase/migrations").glob("*.sql"))
    if not active or active[0].relative_to(ROOT).as_posix() != manifest["baseline_file"]:
        raise ValueError("The reconciled baseline must be the first active migration")
    versions = [p.name.split("_", 1)[0] for p in active]
    if len(versions) != len(set(versions)) or any(not re.fullmatch(r"\d{14}", v) for v in versions):
        raise ValueError("Active migrations have an invalid or duplicate version")
    return manifest


def local_psql(container: str, sql: str) -> subprocess.CompletedProcess[str]:
    # Bind explicitly to the task's local daemon; inherited Docker contexts must
    # never redirect this acceptance runner to another host.
    return subprocess.run(
        ["docker", "--host=unix:///var/run/docker.sock", "exec", "-i", container,
         "psql", "-X", "-U", "postgres", "-d", "postgres", "-v", "ON_ERROR_STOP=1"],
        input=sql,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", help="Already initialized local Supabase DB container")
    parser.add_argument("--smoke", action="store_true", help="Run critical SQL acceptance on local fixtures")
    parser.add_argument("--all-smokes", action="store_true", help="Run every SQL smoke; includes separately seeded simulation tests")
    parser.add_argument("--logs-dir", type=Path, help="Retain local SQL results outside the repository")
    args = parser.parse_args()
    try:
        manifest = static_check()
        print(f"Schema provenance PASS: 173 legacy + 125 hosted files retained; {len(manifest['sources'])} curated reconstruction inputs")
        if not args.container:
            if args.smoke or args.all_smokes:
                parser.error("--smoke/--all-smokes require an initialized --container")
            return 0
        if not re.fullmatch(r"(?:supabase_db_|gtr-db-)[A-Za-z0-9_.-]+", args.container):
            parser.error("Only a named local Supabase/reconstruction DB container is supported")
        files = [ROOT / "supabase/tests/baseline_contract_smoke.sql"]
        if args.all_smokes:
            files = sorted((ROOT / "supabase/tests").glob("*.sql"))
        elif args.smoke:
            files = [ROOT / "supabase/tests" / name for name in CRITICAL_SMOKES]
        if args.logs_dir:
            args.logs_dir.mkdir(parents=True, exist_ok=True)
        migration_hashes = {
            p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted((ROOT / "supabase/migrations").glob("*.sql"))
        }
        results = []
        failed = []
        for path in files:
            if not path.is_file():
                raise ValueError(f"Acceptance SQL missing: {path.relative_to(ROOT)}")
            sql_bytes = path.read_bytes()
            executed_source_sha256 = hashlib.sha256(sql_bytes).hexdigest()
            sql = sql_bytes.decode("utf-8")
            # Legacy test identity helpers are fixtures, never application RPCs.
            # Keep them in this connection's temporary schema and grant only the
            # test actors that need to switch identities inside the transaction.
            sql = sql.replace("public._test_", "pg_temp._test_")
            helper_pattern = re.compile(
                r"(CREATE(?: OR REPLACE)? FUNCTION pg_temp\."
                r"(?P<name>_test_set_auth_uid|_test_set_service_role)"
                r"\((?P<args>[^)]*)\)[\s\S]*?^\$\$;)", re.I | re.M
            )
            def grant_fixture(match: re.Match[str]) -> str:
                identity = "uuid" if match.group("args").strip() else ""
                return match.group(0) + (
                    "\nDO $fixture_grant$ BEGIN EXECUTE format("
                    "'GRANT USAGE ON SCHEMA %I TO authenticated,service_role',"
                    "pg_my_temp_schema()::regnamespace::text); END $fixture_grant$;"
                    f"\nGRANT EXECUTE ON FUNCTION pg_temp.{match.group('name')}({identity})"
                    " TO authenticated,service_role;\n"
                )
            sql = helper_pattern.sub(grant_fixture, sql)
            # Historical smoke files predate explicit rollback isolation. A
            # single-transaction psql flag commits successful fixtures; use an
            # explicit rollback instead, including any test-only helper DDL.
            # Existing inner BEGIN/ROLLBACK pairs are safe and still roll back.
            sql = re.sub(r"(?m)^(DROP FUNCTION IF EXISTS pg_temp\._test_)", r"RESET ROLE;\n\1", sql)
            sql = "BEGIN;\n" + sql + "\nRESET ROLE;\nROLLBACK;\n"
            result = local_psql(args.container, sql)
            if args.logs_dir:
                (args.logs_dir / (path.stem + ".log")).write_text(result.stdout, encoding="utf-8")
            tap_failed = re.search(r"(?m)^\s*not ok\b", result.stdout) is not None
            results.append({
                "file": path.relative_to(ROOT).as_posix(),
                "sha256": executed_source_sha256,
                "exit_code": result.returncode,
                "tap_failed": tap_failed,
            })
            print(f"{'PASS' if result.returncode == 0 and not tap_failed else 'FAIL'} {path.name}")
            if result.returncode or tap_failed:
                failed.append(path.name)
                print(result.stdout[-2500:], file=sys.stderr)
        source_changed = any(
            hashlib.sha256((ROOT / path).read_bytes()).hexdigest() != digest
            for path, digest in migration_hashes.items()
        )
        if args.logs_dir:
            receipt = {
                "observed_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
                "local_container": args.container,
                "baseline_sha256": manifest["baseline_sha256"],
                "active_migration_hashes": migration_hashes,
                "source_changed_during_run": source_changed,
                "smokes": results,
                "passed": len(results) - len(failed),
                "failed": failed,
            }
            (args.logs_dir / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        if source_changed:
            print("Active SQL changed during validation; repeat against the final source", file=sys.stderr)
            return 1
        return 1 if failed else 0
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print(f"Database baseline verification FAILED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
