-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905145658 supabase_auth_edge_boundary).
-- Source of record for what production ran; see supabase/live-history/README.md.

create or replace function public.hook_before_user_created(event jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_provider text;
  v_via text;
begin
  v_provider := lower(coalesce(event->'user'->'app_metadata'->>'provider', ''));
  v_via := coalesce(event->'user'->'app_metadata'->>'gtr_provisioned_via', '');

  if v_provider in ('google', 'apple') then
    return '{}'::jsonb;
  end if;

  -- Canonical path is supabase_auth_edge. auth_otp remains accepted only for
  -- backward compatibility with accounts provisioned before the cutover.
  if v_via in ('supabase_auth_edge', 'auth_otp', 'hr_onboarding') then
    return '{}'::jsonb;
  end if;

  return jsonb_build_object(
    'error',
    jsonb_build_object(
      'message',
      'Public signup is disabled. Use the Nissan GTR Auto Supabase Auth flow or Google/Apple sign-in.',
      'http_code',
      403
    )
  );
end;
$$;

create or replace function public.resolve_auth_user(
  p_email text default null,
  p_phone_e164 text default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_email text := lower(trim(coalesce(p_email, '')));
  v_phone text := trim(coalesce(p_phone_e164, ''));
  v_email_uid uuid;
  v_phone_uid uuid;
begin
  if coalesce(auth.jwt()->>'role', '') <> 'service_role' then
    raise exception 'service_role required';
  end if;

  if v_email <> '' then
    select u.id into v_email_uid
    from auth.users u
    where lower(u.email) = v_email
    order by u.created_at asc
    limit 1;
  end if;

  if v_phone <> '' then
    select u.id into v_phone_uid
    from auth.users u
    where u.phone = v_phone
    order by u.created_at asc
    limit 1;
  end if;

  if v_email <> '' and v_phone <> ''
     and v_email_uid is not null and v_phone_uid is not null
     and v_email_uid <> v_phone_uid then
    raise exception 'AUTH_IDENTIFIER_CONFLICT';
  end if;

  return coalesce(v_email_uid, v_phone_uid);
end;
$$;

revoke all on function public.resolve_auth_user(text, text) from public, anon, authenticated;
grant execute on function public.resolve_auth_user(text, text) to service_role;

create or replace function public.enforce_auth_edge_rate_limit(
  p_operation text,
  p_identifier_hash text,
  p_ip_hash text default null,
  p_device_hash text default null
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_op text := lower(trim(coalesce(p_operation, '')));
  v_identifier text := trim(coalesce(p_identifier_hash, ''));
  v_ip text := trim(coalesce(p_ip_hash, ''));
  v_device text := trim(coalesce(p_device_hash, ''));
  v_identifier_max integer;
  v_ip_max integer;
  v_device_max integer;
  v_global_max integer;
  v_window integer := 900;
begin
  if v_identifier = '' then
    raise exception 'identifier hash required';
  end if;

  case v_op
    when 'signup_request' then
      v_identifier_max := 5; v_ip_max := 30; v_device_max := 20; v_global_max := 300;
    when 'signup_verify' then
      v_identifier_max := 20; v_ip_max := 60; v_device_max := 40; v_global_max := 1200;
    when 'login' then
      v_identifier_max := 30; v_ip_max := 90; v_device_max := 60; v_global_max := 1800;
    when 'password_reset_request' then
      v_identifier_max := 5; v_ip_max := 30; v_device_max := 20; v_global_max := 300;
    when 'password_reset_verify' then
      v_identifier_max := 20; v_ip_max := 60; v_device_max := 40; v_global_max := 1200;
    else
      raise exception 'invalid auth edge operation';
  end case;

  perform public._consume_auth_rate_limit('auth_edge:' || v_op || ':identifier', v_identifier, v_identifier_max, v_window);
  if v_ip <> '' then
    perform public._consume_auth_rate_limit('auth_edge:' || v_op || ':ip', v_ip, v_ip_max, v_window);
  end if;
  if v_device <> '' then
    perform public._consume_auth_rate_limit('auth_edge:' || v_op || ':device', v_device, v_device_max, v_window);
  end if;
  perform public._consume_auth_rate_limit('auth_edge:' || v_op || ':global', 'global', v_global_max, 60);

  return jsonb_build_object('allowed', true);
end;
$$;

revoke all on function public.enforce_auth_edge_rate_limit(text, text, text, text) from public, anon, authenticated;
grant execute on function public.enforce_auth_edge_rate_limit(text, text, text, text) to service_role;

-- Internal primitives and the retired custom-code challenge API must not be
-- callable by public API roles. The active Edge layer uses Supabase Auth OTPs.
revoke all on function public._consume_auth_rate_limit(text, text, integer, integer) from public, anon, authenticated;
grant execute on function public._consume_auth_rate_limit(text, text, integer, integer) to service_role;

revoke all on function public.issue_auth_challenge(text, text, text, text, timestamptz, text, text) from public, anon, authenticated;
revoke all on function public.verify_auth_challenge(text, text, text, text, text, text) from public, anon, authenticated;
revoke all on function public.cancel_auth_challenge(text, uuid) from public, anon, authenticated;
revoke all on function public.resolve_password_reset_user(text, text) from public, anon, authenticated;
grant execute on function public.issue_auth_challenge(text, text, text, text, timestamptz, text, text) to service_role;
grant execute on function public.verify_auth_challenge(text, text, text, text, text, text) to service_role;
grant execute on function public.cancel_auth_challenge(text, uuid) to service_role;
grant execute on function public.resolve_password_reset_user(text, text) to service_role;
