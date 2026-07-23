# Remote Supabase (hosted)

**URL:** `https://gylrgwqyuiwkyykardwc.supabase.co`  
**Ref:** `gylrgwqyuiwkyykardwc`  
**Decision:** `docs/decisions/2026-07-23-remote-supabase-project.md`

## 1. Add API keys (Dashboard)

Supabase Dashboard → **Project Settings → API**:

| Env var | Which value |
|---------|-------------|
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` / `SUPABASE_ANON_KEY` | `anon` / publishable |
| `SUPABASE_SERVICE_KEY` | `service_role` (server only) |

**Database** (optional direct SQL / some CLI flows):

| Env var | Which value |
|---------|-------------|
| `DATABASE_URL` | `postgresql://postgres:YOUR_PASSWORD@db.gylrgwqyuiwkyykardwc.supabase.co:5432/postgres` |

Password: Dashboard → **Project Settings → Database** → Database password (or reset). Put the full URL only in **`.env.local`** — never commit or paste the password in chat.

## 2. Link CLI & push migrations

```powershell
cd "C:\Users\j\Desktop\nissan gtr"
npx supabase login
npx supabase link --project-ref gylrgwqyuiwkyykardwc
npx supabase db push
# Prefer workspace scripts after link:
pnpm db:types:linked
# equivalent:
# npx supabase gen types typescript --linked > packages/supabase-client/src/database.types.ts
```

**Phase 2 auth:** migration `20260723200000_auth_profiles_roles.sql` (signup → profiles, `assign_staff_role` / `revoke_staff_role`). Local staff users live only in `supabase/seed.sql` (not applied by `db push`).
## 3. Safety

- Clients: **anon** key only (`packages/supabase-client` browser helper).
- Migrations / admin: service key on server or CLI login — not in apps.
- Prod guard hook blocks careless `db push --linked` patterns in agent shells; run push yourself in a normal terminal when ready.
