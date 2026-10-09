-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908075537 catalog_offline_release_grant_upsert).
-- Source of record for what production ran; see supabase/live-history/README.md.

create or replace function public.catalog_offline_record_device_grant(
  p_release_id uuid,
  p_user_id uuid,
  p_device_key_sha256 text,
  p_app_flavor text
) returns void
language plpgsql security definer set search_path=public
as $$
begin
  if p_app_flavor not in ('phone','tablet') then raise exception 'invalid app flavor'; end if;
  insert into public.catalog_offline_device_grants(release_id,user_id,device_key_sha256,app_flavor)
  values(p_release_id,p_user_id,p_device_key_sha256,p_app_flavor)
  on conflict(release_id,user_id,device_key_sha256,app_flavor)
  do update set last_granted_at=now(), revoked_at=null;
end;$$;
revoke all on function public.catalog_offline_record_device_grant(uuid,uuid,text,text) from public,anon,authenticated;
grant execute on function public.catalog_offline_record_device_grant(uuid,uuid,text,text) to service_role;
