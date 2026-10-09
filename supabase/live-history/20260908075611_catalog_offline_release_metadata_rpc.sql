-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908075611 catalog_offline_release_metadata_rpc).
-- Source of record for what production ran; see supabase/live-history/README.md.

create or replace function public.catalog_offline_current_release_public()
returns table(release_id uuid,version text,schema_version integer,r2_object_key text,encrypted_size_bytes bigint,encrypted_sha256 text,sqlite_page_size integer,encryption_format text,vehicle_count bigint,section_count bigint,diagram_count bigint,fitment_count bigint,image_count bigint,source_release text,published_at timestamptz)
language sql security definer set search_path=public stable as $$
  select id,version,schema_version,r2_object_key,encrypted_size_bytes,encrypted_sha256,sqlite_page_size,encryption_format,vehicle_count,section_count,diagram_count,fitment_count,image_count,source_release,published_at
  from public.catalog_offline_releases where maker_slug='nissan' and is_current and published_at is not null order by published_at desc limit 1
$$;
revoke all on function public.catalog_offline_current_release_public() from public,anon,authenticated;
grant execute on function public.catalog_offline_current_release_public() to service_role;
