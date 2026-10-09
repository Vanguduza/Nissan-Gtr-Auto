# Remote Supabase (hosted)

**Active URL:** `https://bicyjghgdnzlnjqxzoud.supabase.co`  
**Active ref:** `bicyjghgdnzlnjqxzoud`  
**Retired project ref:** `gylrgwqyuiwkyykardwc` — keep intact until replacement Auth, R2 serving, app cutover, and E2E gates are green.  
**Historical decision:** `docs/decisions/2026-07-23-remote-supabase-project.md`

The replacement project is the control/commerce plane. Heavy EPC catalog payloads live in Cloudflare R2 and are reached through `catalog-live-r2`; do not recreate the retired full-Postgres catalog-v2 layer.

## 1. Add API keys (Dashboard)

Supabase Dashboard → **Project Settings → API** for project `bicyjghgdnzlnjqxzoud`:

| Env var | Which value |
|---------|-------------|
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` / `SUPABASE_ANON_KEY` | publishable / anon |
| `SUPABASE_SERVICE_KEY` | `service_role` (server only) |

**Database** (optional direct SQL / some CLI flows):

| Env var | Which value |
|---------|-------------|
| `DATABASE_URL` | `postgresql://postgres:YOUR_PASSWORD@db.bicyjghgdnzlnjqxzoud.supabase.co:5432/postgres` |

Password: Dashboard → **Project Settings → Database** → Database password (or reset). Put the full URL only in **`.env.local`** / secret storage — never commit it.

## 2. Link CLI & push migrations

```powershell
cd "C:\Users\Admin\Documents\nissan gtr auto\Nissan-Gtr-Auto"
npx supabase login
npx supabase link --project-ref bicyjghgdnzlnjqxzoud
npx supabase migration list
npx supabase db push
pnpm db:types:linked
```

Do **not** link active development back to `gylrgwqyuiwkyykardwc` except for an explicit read-only recovery/audit operation.

## 3. Catalog runtime split

- Supabase: Auth, staff roles, commerce, lightweight catalog hierarchy, release/control metadata.
- Cloudflare R2: fitment/search shards, section/diagram parts, diagram images, other heavy EPC payloads.
- `catalog-live-r2`: authenticated boundary joining the two planes.
- Staff hierarchy actions: `staff-families`, `staff-variants`, `staff-sections`, `staff-diagrams`.
- Legacy `staff_catalog_v2_*` RPCs and full-Postgres catalog delivery are not part of the replacement architecture.

## 4. Safety

- Clients: publishable/anon key only.
- Migrations/admin: service role on server or CLI login — never in apps.
- Never commit Cloudflare R2 credentials; store them as Edge Function secrets.
- Never run `supabase db reset` against hosted.
- Do not delete the retired project until Auth identities/roles, R2 manifests, client envs, and end-to-end flows are verified on the replacement project.
