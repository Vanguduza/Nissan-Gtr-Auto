-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908075555 catalog_offline_release_revoke_guard).
-- Source of record for what production ran; see supabase/live-history/README.md.

create or replace function public.catalog_offline_device_is_revoked(p_release_id uuid,p_user_id uuid,p_device_key_sha256 text,p_app_flavor text)
returns boolean language sql security definer set search_path=public stable as $$
  select coalesce((select revoked_at is not null from public.catalog_offline_device_grants where release_id=p_release_id and user_id=p_user_id and device_key_sha256=p_device_key_sha256 and app_flavor=p_app_flavor),false)
$$;
revoke all on function public.catalog_offline_device_is_revoked(uuid,uuid,text,text) from public,anon,authenticated;
grant execute on function public.catalog_offline_device_is_revoked(uuid,uuid,text,text) to service_role;
