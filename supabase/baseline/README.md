# Reconciled database baseline

`migrations/20261102080000_reconciled_erp_baseline.sql` is the fresh-install schema.
Forward migrations follow it. The 173 former active migration files are preserved
byte-for-byte in `migration-history/legacy/`; the 125 exported hosted files remain
unchanged in `live-history/`. None of those historical folders is an active
Supabase migration directory.

`manifest.json` binds every historical input, the curated reconstruction order,
compatibility corrections, generated SQL and source contract by SHA-256. Run
`python3 scripts/verify-database-baseline.py` from the repository root to verify it.

## What was reconciled

The hosted export is not a standalone installation: its commerce migrations rely
on legacy customer, POS, finance, HR and inventory prerequisites. Conversely, the
former active chain lacked the hosted reserve-first POS, split-payment, ECR,
governance and payroll reconstruction. Applying both folders blindly duplicates
objects and replaces later behavior with earlier definitions.

The isolated reconstruction used 114 legacy prerequisites, 117 hosted overlays,
two POS discovery predecessors and 38 October forward migrations. Duplicate
foundations and superseded legacy counterparts were explicitly excluded. Two
compatibility corrections were required during reconstruction: local fixture
inserts could not use the retired Storage `(bucket_id,name)` conflict arbiter,
and a hosted invoice read-model replacement needed the old OUT-row signature
dropped first. The resulting schema was dumped in pre-data/reference/post-data
order, including application policies on native Storage and the Auth user hook.
Platform-owner default ACLs remain with Supabase rather than being replayed by
an application migration. Before source replay, native per-role table, sequence
and function defaults are cleared, matching the committed simulation bootstrap;
normal PUBLIC function defaults and each source GRANT/REVOKE remain authoritative.
The baseline repeats that reset before creating any application objects, so a
native platform's permissive direct defaults cannot add unintended capabilities.

A clean application-schema reset and baseline load on native PostgreSQL 17.6,
Auth 2.197.0 and Storage 1.79.36 reproduced all 210 tables and all 769
public/private function definitions from that reconstruction. A second roundtrip
explicitly restored native permissive defaults before applying the baseline and
proved the same effective function/table grants, RLS policies and view options
(5,136 relation capability assertions). PostgreSQL's equivalent array-cast
deparser normalization for `v_master_stock` is recorded in the manifest. This proves the
retained source contract, not identity with an uninspected current production
database. The local vfs Docker environment used a flattened filesystem copy of
the same pinned Postgres image; no replacement Auth/Storage stubs were used.

The baseline contains declarative configuration and published catalogue control
metadata. It contains no Auth users/passwords, Vault/provider credentials, sales,
customer operational records, local demo stock/catalogue or employee schedules.
`seed.sql` remains local development/testing only.

## Fresh local installation and CI

Use the normal Supabase local initialization and reset. The native Auth/Storage
schemas must exist before the application migration is applied. PostgreSQL 17 is
required by `config.toml`.

After reset and the local seed:

```sh
python3 scripts/verify-database-baseline.py
python3 scripts/verify-database-baseline.py --container supabase_db_bicyjghgdnzlnjqxzoud --smoke
```

The runtime verifier accepts a local Docker container only. It does not discover
credentials, connect to a hosted database, reset a database or repair migration
history. `--all-smokes` additionally includes historical/simulation acceptance
that needs its separate simulation fixture setup.

## Existing hosted database adoption

Do not execute the fresh-install SQL on a populated installation. It explicitly
refuses an existing `profiles` or `pos_carts` relation. Do not mark the baseline
applied merely to silence Supabase CLI history errors.

For the intended hosted project, first retain a restorable database backup and a
copy of its migration ledger. Obtain a fresh, read-only schema/ACL inventory and
compare its tables, columns, signatures, RLS, role grants, Auth hook, Storage
policies, extension prerequisites and maintenance jobs with the source contract.
Resolve each difference using reviewed, data-preserving forward SQL and test that
upgrade on a restored staging snapshot. Preserve the original ledger as evidence.
Only after that comparison and staging acceptance may the release operator use
the supported migration-repair workflow to adopt the baseline marker and align
the archived historical versions, then apply the reviewed forward migrations.
This repository change does not perform that production operation or attest its
outcome. Never reset production, replay both historical folders, delete real
business records or copy local seed credentials during adoption.
