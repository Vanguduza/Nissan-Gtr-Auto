# Nissan GTR Project Truth Guardrails

This directory is generated/verified project memory for the repository. It exists to prevent stale branches, superseded app implementations, or conversational memory from becoming release authority.

## Authority order

1. Git history and repository contents.
2. `PROJECT_CANONICAL_STATE.json` for declared application lineage and release status.
3. `CHANGE_LEDGER.jsonl` / `LAST_CHANGE.json` for automatic per-change evidence.
4. Locked design/decision documents referenced by the canonical state.
5. Chat/agent memory only as a pointer to the above; memory never proves current branch state.

## Automatic change capture

After cloning, run one of:

```sh
./scripts/install-repo-guardrails.sh
```

```powershell
./scripts/install-repo-guardrails.ps1
```

This configures `core.hooksPath=.githooks`.

The pre-commit hook automatically records the staged diff digest, branch, parent commit, affected domains and changed files in `CHANGE_LEDGER.jsonl`, stages that evidence, and writes `LAST_CHANGE.json`. The pre-push hook independently verifies that HEAD matches the ledger entry.

CI repeats the verification. Therefore bypassing a local hook does not make an unrecorded change acceptable.

## Release lineage gate

Every build/release workflow must call:

```sh
python3 scripts/project_truth_guard.py release-check --app <app-id>
```

A release is refused when:

- the app is not registered;
- canonical lineage is unresolved;
- `release_blocked_until_reconciled` is true; or
- HEAD does not contain the recorded `required_ancestor`.

For distributable artifacts, also pass `--require-clean`. This checks the recorded
HEAD and refuses staged, unstaged or untracked source changes. Debug verification
can run during development while still checking the required lineage.

The approved Illustrated/POS/Express customer implementation and benchmark POS
remain required. The current application states and ancestors are recorded in
`PROJECT_CANONICAL_STATE.json`.

## Deployment reconciliation

`BRANCH_RECONCILIATION.json` accounts for every remote branch observed in the
November 2 audit, including selected ports and intentionally superseded work.
`DEPLOYMENT_READINESS.json` separates source fixes and local verification from
the hosted, provider, signing and device evidence required for release. Neither
a passing build nor a lineage check establishes production acceptance.

`CURRENT_STATE.json` is a dated observation of a named commit. Consult Git and
the change ledger for later changes. CI uploads exact-commit observations as
artifacts instead of making competing automated source commits.

## Rule for agents

No agent may decide that `main`, the GitHub default branch, the newest timestamp, or the branch it happens to be on is canonical. It must read `PROJECT_CANONICAL_STATE.json`, inspect relevant divergent branches, and pass the release gate.

Every approved change to canonical lineage must update `PROJECT_CANONICAL_STATE.json` in the same reviewed change that performs the reconciliation. Silent feature thinning is forbidden.
