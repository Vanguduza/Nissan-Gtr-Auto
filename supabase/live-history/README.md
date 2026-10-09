# Hosted project migration history (source of record)

These files are the SQL the hosted Supabase project (`bicyjghgdnzlnjqxzoud`) actually ran, exported
2026-10-03 from `supabase_migrations.schema_migrations` (one file per version, statements verbatim).

## Why this folder exists

The hosted project was rebuilt on 2026-09-04 → 2026-09-08 from a migration history that is not in
`supabase/migrations/`: 115 of its 125 versions have no file there, and no branch or commit in this
repository contains them. Among them is the POS backend the tablet and web POS run on
(`pos_operations_p0_p1`, reserve-first and split payments, card-terminal ECR, governance reasons,
account credit, payment resolution letters, COD card settlement, split recovery queue), the
procurement / petty-cash / kits / shop-merch restores of `main`'s August backend, payroll worker
reconstruction and the R2 catalogue control plane.

Until this export the only copy of that source was the production database itself.

## How to use it

- Read-only reference: do **not** add these files to `supabase/migrations/` as they are. The two
  histories overlap object-for-object with different version numbers, so a fresh `supabase db reset`
  would apply both and fail.
- New backend work should be written against the hosted schema (this history), not against older
  repo migrations that production never ran.
- Unifying `supabase/migrations/` with this history (baseline squash of the hosted schema, then
  forward migrations only) is a separate, reviewed change.

No secrets: credentials reach the database only as RPC parameters into Vault
(`catalog_r2_vault_and_vehicle_routing`); hashes in the files are SHA-256 object digests.
