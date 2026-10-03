-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905143531 password_reset_account_resolution_v1).
-- Source of record for what production ran; see supabase/live-history/README.md.

create or replace function public.resolve_password_reset_user(
  p_email text default null,
  p_phone_e164 text default null
) returns uuid
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_email text := lower(trim(coalesce(p_email,'')));
  v_phone text := trim(coalesce(p_phone_e164,''));
  v_email_uid uuid;
  v_phone_uid uuid;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'service_role required';
  end if;

  if v_email <> '' then
    select u.id into v_email_uid
    from auth.users u
    where lower(u.email)=v_email
    order by u.created_at asc
    limit 1;
  end if;

  if v_phone <> '' then
    select p.id into v_phone_uid
    from public.profiles p
    where p.phone_e164=v_phone
    order by p.created_at asc
    limit 1;
  end if;

  if v_email <> '' and v_phone <> '' then
    if v_email_uid is not null and v_phone_uid is not null and v_email_uid=v_phone_uid then
      return v_email_uid;
    end if;
    return null;
  end if;

  return coalesce(v_email_uid,v_phone_uid);
end;
$$;

revoke all on function public.resolve_password_reset_user(text,text) from public, anon, authenticated;
grant execute on function public.resolve_password_reset_user(text,text) to service_role;
