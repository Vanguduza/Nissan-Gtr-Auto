# Canonical Staff Authority

The replacement Supabase project `bicyjghgdnzlnjqxzoud` is authoritative for hosted Nissan GTR Auto staff authentication.

## Locked canonical identities

| UUID | Email | Role |
|---|---|---|
| `a0000000-0000-4000-8000-000000000001` | `admin@gtr.local` | `admin` |
| `a0000000-0000-4000-8000-000000000002` | `finance@gtr.local` | `finance` |
| `a0000000-0000-4000-8000-000000000003` | `warehouse@gtr.local` | `warehouse` |

These UUID/email/role mappings are production identity facts. Passwords are not source-of-truth data and must never be committed.

## Provisioning gate

Use a server-only Supabase Secret key or legacy service-role key. Never use a publishable/anon key for provisioning.

1. Copy `config/canonical-staff.env.example` to the repository root as `.canonical-staff.env` on an authorized server. This filename is gitignored.
2. Populate the server credential and three unique bootstrap passwords (20+ characters).
3. Run `corepack pnpm staff:provision`; the provisioner loads `.canonical-staff.env` automatically.
4. Run `corepack pnpm staff:verify` and require `Canonical staff authority gate: PASS`.

Newly created accounts and explicit password rotations set `profiles.must_change_password=true`. The staff gate blocks all other staff surfaces until the user changes that password.

## Rotation

Set new values in the private environment and run `corepack pnpm staff:rotate-passwords`, then `corepack pnpm staff:verify`. Rotated users are forced through `/staff/change-password` before accessing staff features.

## Database authority

`public.canonical_staff_accounts` is migration-owned. Runtime clients cannot mutate it. `public.staff_roles` has a trigger that prevents these fixed identities from receiving a conflicting role or losing their authoritative role through normal runtime paths.

`public.canonical_staff_drift()` is service-role-only and verifies Auth presence, exact email, confirmed email, trusted provisioning metadata, profile existence, `is_staff`, exact role, and absence of extra roles.

## Hosted Auth configuration

Supabase hosted Auth provider/site/password settings live outside PostgreSQL. Do not infer them from `supabase/config.toml`, and never push the local `site_url=http://127.0.0.1:3000` to production unchanged.

Before production sign-off, verify the hosted site URL/redirect allow-list, email-confirmation behavior, public-signup/hook behavior, password policy, and leaked-password protection through authenticated Supabase project configuration tooling. Google and Apple remain disabled until real provider credentials are intentionally configured.

## Local fixtures

`supabase/seed.sql` and any `local-dev-*` password are local-development fixtures only. They are not valid hosted credentials and must never be promoted to production.
