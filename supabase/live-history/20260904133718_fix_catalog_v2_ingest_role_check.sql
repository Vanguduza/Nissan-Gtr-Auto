-- Exported from the hosted project's supabase_migrations.schema_migrations (20260904133718 fix_catalog_v2_ingest_role_check).
-- Source of record for what production ran; see supabase/live-history/README.md.

create or replace function public.catalog_v2_ingest_r2_serving_objects_batch(p_rows jsonb)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text := coalesce(auth.jwt() ->> 'role', current_setting('request.jwt.claim.role', true), '');
  v_count bigint;
begin
  if v_role is distinct from 'service_role' then raise exception 'service_role required'; end if;
  if jsonb_typeof(p_rows) <> 'array' then raise exception 'p_rows must be a JSON array'; end if;
  insert into public.catalog_r2_serving_objects (release_id,maker_slug,object_kind,scope_key,object_key,sha256,row_count,bytes,content_type,content_encoding,metadata,updated_at)
  select (r->>'release_id')::uuid,lower(coalesce(nullif(trim(r->>'maker_slug'),''),'nissan')),trim(r->>'object_kind'),trim(r->>'scope_key'),trim(r->>'object_key'),nullif(lower(trim(r->>'sha256')),''),coalesce((r->>'row_count')::integer,0),coalesce((r->>'bytes')::bigint,0),coalesce(nullif(trim(r->>'content_type'),''),'application/json'),nullif(trim(r->>'content_encoding'),''),coalesce(r->'metadata','{}'::jsonb),now()
  from jsonb_array_elements(p_rows) as x(r)
  where nullif(trim(r->>'release_id'),'') is not null and nullif(trim(r->>'object_kind'),'') is not null and nullif(trim(r->>'scope_key'),'') is not null and nullif(trim(r->>'object_key'),'') is not null
  on conflict (release_id,object_kind,scope_key) do update set maker_slug=excluded.maker_slug,object_key=excluded.object_key,sha256=excluded.sha256,row_count=excluded.row_count,bytes=excluded.bytes,content_type=excluded.content_type,content_encoding=excluded.content_encoding,metadata=excluded.metadata,updated_at=now();
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;
revoke all on function public.catalog_v2_ingest_r2_serving_objects_batch(jsonb) from public, anon, authenticated;
grant execute on function public.catalog_v2_ingest_r2_serving_objects_batch(jsonb) to service_role;
