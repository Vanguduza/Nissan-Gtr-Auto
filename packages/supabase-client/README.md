# @gtr/supabase-client

Typed Supabase client + generated `Database` types.

Mobile scaffolds (`apps/ios`, `apps/android-*`) consume these types/helpers — do not redefine row shapes or pricing in apps. See `apps/SHARED_CLIENT.md`.

```bash
supabase gen types typescript --local > packages/supabase-client/src/database.types.ts
```

Never ship the service_role key to clients — anon key only (`SUPABASE_ANON_KEY`).
