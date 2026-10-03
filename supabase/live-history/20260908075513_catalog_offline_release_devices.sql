-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908075513 catalog_offline_release_devices).
-- Source of record for what production ran; see supabase/live-history/README.md.

create table if not exists public.catalog_offline_device_grants (
  id uuid primary key default gen_random_uuid(),
  release_id uuid not null references public.catalog_offline_releases(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  device_key_sha256 text not null check (device_key_sha256 ~ '^[0-9a-f]{64}$'),
  app_flavor text not null check (app_flavor in ('phone','tablet')),
  first_granted_at timestamptz not null default now(),
  last_granted_at timestamptz not null default now(),
  revoked_at timestamptz,
  unique(release_id,user_id,device_key_sha256,app_flavor)
);
alter table public.catalog_offline_device_grants enable row level security;
revoke all on table public.catalog_offline_device_grants from public, anon, authenticated;
grant select,insert,update,delete on table public.catalog_offline_device_grants to service_role;
comment on table public.catalog_offline_device_grants is 'Audit-only grants for device-bound offline EPC catalogue key delivery.';
