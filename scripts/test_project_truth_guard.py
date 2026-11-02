"""Exercise ledger integrity against real, isolated Git histories."""
from __future__ import annotations

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("truth_guard", Path(__file__).with_name("project_truth_guard.py"))
assert SPEC and SPEC.loader
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class LedgerIntegrityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        guard.REPO_ROOT = self.root
        guard.LEDGER_FILE = self.root / "docs/project-state/CHANGE_LEDGER.jsonl"
        guard.LAST_CHANGE_FILE = self.root / "docs/project-state/LAST_CHANGE.json"
        guard.STATE_FILE = self.root / "PROJECT_CANONICAL_STATE.json"
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Ledger test")
        self.git("config", "user.email", "ledger-test@example.invalid")
        (self.root / "app.txt").write_text("initial\n")
        self.git("add", "app.txt")
        self.git("commit", "-m", "root")

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def git(self, *args: str, stdin: str | None = None) -> str:
        return subprocess.check_output(["git", *args], cwd=self.root, text=True, input=stdin, stderr=subprocess.STDOUT).strip()

    def record(self, text: str) -> str:
        (self.root / "app.txt").write_text(text + "\n")
        self.git("add", "app.txt")
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(guard.record(), 0)
        self.git("commit", "-m", text)
        return self.git("rev-parse", "HEAD")

    def verify(self) -> int:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return guard.verify_head()

    def test_recorded_source_passes(self) -> None:
        self.record("recorded")
        self.assertEqual(self.verify(), 0)

    def test_unrecorded_source_is_rejected(self) -> None:
        self.record("recorded")
        (self.root / "app.txt").write_text("unrecorded\n")
        self.git("add", "app.txt")
        self.git("commit", "-m", "missing record")
        self.assertEqual(self.verify(), 21)

    def test_exact_pr_tree_merge_verifies_reviewed_head(self) -> None:
        self.git("switch", "-c", "feature")
        self.record("first change")
        reviewed = self.record("second change")
        self.git("switch", "main")
        self.git("merge", "--no-ff", "feature", "-m", "reviewed merge")
        self.assertEqual(self.git("rev-parse", "HEAD^{tree}"), self.git("rev-parse", f"{reviewed}^{{tree}}"))
        self.assertEqual(self.verify(), 0)

    def test_merge_with_unrecorded_extra_file_is_rejected(self) -> None:
        self.git("switch", "-c", "feature")
        self.record("first change")
        self.record("second change")
        self.git("switch", "main")
        self.git("merge", "--no-ff", "--no-commit", "feature")
        (self.root / "unreviewed.txt").write_text("merge-time change\n")
        self.git("add", "unreviewed.txt")
        self.git("commit", "-m", "changed merge tree")
        self.assertEqual(self.verify(), 21)

    def test_ledger_rewrite_is_rejected_even_with_fresh_record(self) -> None:
        self.record("first change")
        guard.LEDGER_FILE.write_text(guard.LEDGER_FILE.read_text().replace('"actor":', '"rewritten_actor":'))
        self.git("add", "docs/project-state/CHANGE_LEDGER.jsonl")
        self.record("second change")
        self.assertEqual(self.verify(), 21)

    def test_metadata_only_forgery_is_rejected(self) -> None:
        self.record("recorded")
        guard.LAST_CHANGE_FILE.write_text("{}\n")
        self.git("add", "docs/project-state/LAST_CHANGE.json")
        self.git("commit", "-m", "metadata forgery")
        self.assertEqual(self.verify(), 21)

    def test_exact_tree_cannot_discard_target_history(self) -> None:
        self.git("switch", "-c", "feature")
        self.record("first feature change")
        reviewed = self.record("second feature change")
        self.git("switch", "main")
        target = self.record("target-only change")
        tree = self.git("rev-parse", f"{reviewed}^{{tree}}")
        forged = self.git("commit-tree", tree, "-p", target, "-p", reviewed, stdin="discard target\n")
        self.git("update-ref", "HEAD", forged)
        self.assertEqual(self.verify(), 21)

    def test_deliverable_gate_rejects_dirty_and_unrecorded_source(self) -> None:
        ancestor = self.git("rev-parse", "HEAD")
        guard.STATE_FILE.write_text(json.dumps({"applications": {"test": {"required_ancestor": ancestor, "release_blocked_until_reconciled": False}}}))
        self.git("add", "PROJECT_CANONICAL_STATE.json")
        self.record("registered")
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(guard.release_check("test", require_clean=True), 0)
            (self.root / "untracked.txt").write_text("unrecorded build input\n")
            self.assertEqual(guard.release_check("test", require_clean=True), 34)
            (self.root / "untracked.txt").unlink()
            (self.root / "app.txt").write_text("missing ledger\n")
            self.git("add", "app.txt")
            self.git("commit", "-m", "unrecorded")
            self.assertEqual(guard.release_check("test", require_clean=True), 35)


if __name__ == "__main__":
    unittest.main()
