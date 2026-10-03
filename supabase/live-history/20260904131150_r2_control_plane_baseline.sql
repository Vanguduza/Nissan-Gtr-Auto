-- Exported from the hosted project's supabase_migrations.schema_migrations (20260904131150 r2_control_plane_baseline).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- R2-first catalog control-plane baseline for clean Supabase rebuilds.
-- Heavy EPC rows and binary diagrams belong in Cloudflare R2, never this database.

create table if not exists public.catalog_releases (
  id uuid primary key default gen_random_uuid(),
  maker_slug text not null,
  version text not null,
  bucket_name text not null,
  bundle_object_key text null,
  bundle_size_bytes bigint not null default 0,
  bundle_sha256 text null,
  diagram_count integer not null default 0,
  is_current boolean not null default false,
  published_at timestamptz null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (maker_slug, version)
);

create unique index if not exists catalog_releases_one_current_per_maker
  on public.catalog_releases (maker_slug)
  where is_current;

alter table public.catalog_releases enable row level security;
revoke all on public.catalog_releases from anon, authenticated;
grant select, insert, update, delete on public.catalog_releases to service_role;

update public.catalog_releases
set is_current = false, updated_at = now()
where maker_slug = 'nissan' and version <> 'v2-storage-2026-09' and is_current;

insert into public.catalog_releases (
  maker_slug, version, bucket_name, bundle_object_key,
  bundle_size_bytes, bundle_sha256, diagram_count, is_current, published_at
) values (
  'nissan','v2-storage-2026-09','nissangtrauto',
  'bundles/nissan/catalog_nissan_v2_storage.sqlite.enc',6329485031,
  'd9f762e0aa9fa2860186a8709636d8ae6228ebb5dcadc8be4c74b0a329335665',
  119852,true,'2026-09-01T23:21:13.274662+00:00'::timestamptz
)
on conflict (maker_slug, version) do update set
  bucket_name=excluded.bucket_name,
  bundle_object_key=excluded.bundle_object_key,
  bundle_size_bytes=excluded.bundle_size_bytes,
  bundle_sha256=excluded.bundle_sha256,
  diagram_count=excluded.diagram_count,
  is_current=true,
  published_at=excluded.published_at,
  updated_at=now();
