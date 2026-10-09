-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908075439 catalog_offline_release_registry).
-- Source of record for what production ran; see supabase/live-history/README.md.

create table if not exists public.catalog_offline_releases (
  id uuid primary key default gen_random_uuid(),
  maker_slug text not null check (maker_slug = 'nissan'),
  version text not null,
  schema_version integer not null check (schema_version > 0),
  r2_object_key text not null,
  encrypted_size_bytes bigint not null check (encrypted_size_bytes > 0),
  encrypted_sha256 text not null check (encrypted_sha256 ~ '^[0-9a-f]{64}$'),
  sqlite_page_size integer not null default 4096 check (sqlite_page_size >= 1024),
  encryption_format text not null default 'sqlcipher4' check (encryption_format = 'sqlcipher4'),
  key_secret_name text not null,
  vehicle_count bigint not null default 0,
  section_count bigint not null default 0,
  diagram_count bigint not null default 0,
  fitment_count bigint not null default 0,
  image_count bigint not null default 0,
  source_release text,
  generated_at timestamptz not null default now(),
  published_at timestamptz,
  is_current boolean not null default false,
  created_at timestamptz not null default now(),
  unique (maker_slug, version)
);
create unique index if not exists catalog_offline_releases_one_current_nissan
  on public.catalog_offline_releases (maker_slug)
  where is_current and published_at is not null;
alter table public.catalog_offline_releases enable row level security;
revoke all on table public.catalog_offline_releases from public, anon, authenticated;
grant select, insert, update, delete on table public.catalog_offline_releases to service_role;
create or replace function public.catalog_offline_current_release_secret()
returns table(
  release_id uuid,
  version text,
  schema_version integer,
  r2_object_key text,
  encrypted_size_bytes bigint,
  encrypted_sha256 text,
  sqlite_page_size integer,
  encryption_format text,
  vehicle_count bigint,
  section_count bigint,
  diagram_count bigint,
  fitment_count bigint,
  image_count bigint,
  source_release text,
  content_key_b64 text
)
language plpgsql
security definer
set search_path = public, vault
as $$
declare v public.catalog_offline_releases%rowtype;
begin
  select * into v
  from public.catalog_offline_releases
  where maker_slug='nissan' and is_current and published_at is not null
  order by published_at desc
  limit 1;
  if not found then
    raise exception 'offline Nissan catalog release unavailable';
  end if;
  return query
  select v.id, v.version, v.schema_version, v.r2_object_key,
         v.encrypted_size_bytes, v.encrypted_sha256, v.sqlite_page_size,
         v.encryption_format, v.vehicle_count, v.section_count, v.diagram_count,
         v.fitment_count, v.image_count, v.source_release,
         ds.decrypted_secret
  from vault.decrypted_secrets ds
  where ds.name = v.key_secret_name
  limit 1;
  if not found then
    raise exception 'offline Nissan catalog key unavailable';
  end if;
end;
$$;
revoke all on function public.catalog_offline_current_release_secret() from public, anon, authenticated;
grant execute on function public.catalog_offline_current_release_secret() to service_role;
comment on table public.catalog_offline_releases is
  'Published single-file SQLCipher Nissan EPC releases for authorized Nissan GTR Android applications.';
