-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905143457 auth_challenge_rate_limit_and_atomicity_v1).
-- Source of record for what production ran; see supabase/live-history/README.md.

create table if not exists public.auth_request_rate_limits (
  scope text not null,
  key_hash text not null,
  window_started_at timestamptz not null default now(),
  request_count integer not null default 0 check (request_count >= 0),
  updated_at timestamptz not null default now(),
  primary key (scope, key_hash)
);

create index if not exists auth_request_rate_limits_updated_idx
  on public.auth_request_rate_limits(updated_at);

alter table public.auth_request_rate_limits enable row level security;
revoke all on public.auth_request_rate_limits from public, anon, authenticated;
grant all on public.auth_request_rate_limits to service_role;

create or replace function public._consume_auth_rate_limit(
  p_scope text,
  p_key_hash text,
  p_max_requests integer,
  p_window_seconds integer
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_scope text := lower(trim(coalesce(p_scope,'')));
  v_key text := trim(coalesce(p_key_hash,''));
  v_max integer := greatest(1, least(coalesce(p_max_requests,1),100000));
  v_window integer := greatest(1, least(coalesce(p_window_seconds,60),86400));
  v_now timestamptz := clock_timestamp();
  v_row public.auth_request_rate_limits%rowtype;
  v_retry integer := 0;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'service_role required';
  end if;
  if v_scope = '' or v_key = '' then
    raise exception 'rate-limit scope/key required';
  end if;

  insert into public.auth_request_rate_limits(scope,key_hash,window_started_at,request_count,updated_at)
  values(v_scope,v_key,v_now,0,v_now)
  on conflict(scope,key_hash) do nothing;

  select * into strict v_row
  from public.auth_request_rate_limits
  where scope=v_scope and key_hash=v_key
  for update;

  if v_row.window_started_at + make_interval(secs => v_window) <= v_now then
    update public.auth_request_rate_limits
    set window_started_at=v_now, request_count=1, updated_at=v_now
    where scope=v_scope and key_hash=v_key;
    return jsonb_build_object('allowed',true,'remaining',v_max-1,'retry_after_seconds',0);
  end if;

  if v_row.request_count >= v_max then
    v_retry := greatest(1,ceil(extract(epoch from (v_row.window_started_at + make_interval(secs => v_window) - v_now)))::integer);
    raise exception 'AUTH_RATE_LIMITED scope=% retry_after_seconds=%', v_scope, v_retry;
  end if;

  update public.auth_request_rate_limits
  set request_count=request_count+1, updated_at=v_now
  where scope=v_scope and key_hash=v_key;

  return jsonb_build_object('allowed',true,'remaining',v_max-(v_row.request_count+1),'retry_after_seconds',0);
end;
$$;

revoke all on function public._consume_auth_rate_limit(text,text,integer,integer) from public, anon, authenticated;
grant execute on function public._consume_auth_rate_limit(text,text,integer,integer) to service_role;

create or replace function public.issue_auth_challenge(
  p_kind text,
  p_channel text,
  p_identifier text,
  p_code_hash text,
  p_expires_at timestamptz,
  p_ip_hash text default null,
  p_device_hash text default null
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_kind text := lower(trim(coalesce(p_kind,'')));
  v_channel text := lower(trim(coalesce(p_channel,'')));
  v_identifier text := lower(trim(coalesce(p_identifier,'')));
  v_now timestamptz := clock_timestamp();
  v_last timestamptz;
  v_id uuid;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'service_role required';
  end if;
  if v_kind not in ('auth_otp','password_reset') then raise exception 'invalid challenge kind'; end if;
  if v_channel not in ('email','phone') then raise exception 'invalid challenge channel'; end if;
  if v_identifier='' or coalesce(trim(p_code_hash),'')='' then raise exception 'identifier/code hash required'; end if;
  if p_expires_at is null or p_expires_at <= v_now or p_expires_at > v_now + interval '30 minutes' then
    raise exception 'invalid challenge expiry';
  end if;

  perform public._consume_auth_rate_limit(v_kind||':request:identifier',v_channel||':'||v_identifier,5,900);
  if coalesce(trim(p_ip_hash),'')<>'' then
    perform public._consume_auth_rate_limit(v_kind||':request:ip',trim(p_ip_hash),30,900);
  end if;
  if coalesce(trim(p_device_hash),'')<>'' then
    perform public._consume_auth_rate_limit(v_kind||':request:device',trim(p_device_hash),20,900);
  end if;
  perform public._consume_auth_rate_limit(v_kind||':request:global','global',300,60);

  perform pg_advisory_xact_lock(hashtextextended(v_kind||':'||v_channel||':'||v_identifier,0));

  if v_kind='auth_otp' then
    select max(created_at) into v_last from public.auth_otp_challenges
      where channel=v_channel and identifier=v_identifier;
  else
    select max(created_at) into v_last from public.password_reset_challenges
      where channel=v_channel and identifier=v_identifier;
  end if;
  if v_last is not null and v_last > v_now - interval '60 seconds' then
    raise exception 'AUTH_RESEND_COOLDOWN retry_after_seconds=%', greatest(1,ceil(extract(epoch from (v_last + interval '60 seconds' - v_now)))::integer);
  end if;

  if v_kind='auth_otp' then
    update public.auth_otp_challenges
      set expires_at=least(expires_at,v_now)
      where channel=v_channel and identifier=v_identifier and consumed_at is null and expires_at>v_now;
    insert into public.auth_otp_challenges(channel,identifier,code_hash,expires_at,attempt_count)
      values(v_channel,v_identifier,p_code_hash,p_expires_at,0) returning id into v_id;
  else
    update public.password_reset_challenges
      set expires_at=least(expires_at,v_now)
      where channel=v_channel and identifier=v_identifier and consumed_at is null and expires_at>v_now;
    insert into public.password_reset_challenges(channel,identifier,code_hash,expires_at,attempt_count)
      values(v_channel,v_identifier,p_code_hash,p_expires_at,0) returning id into v_id;
  end if;
  return v_id;
end;
$$;

revoke all on function public.issue_auth_challenge(text,text,text,text,timestamptz,text,text) from public, anon, authenticated;
grant execute on function public.issue_auth_challenge(text,text,text,text,timestamptz,text,text) to service_role;

create or replace function public.verify_auth_challenge(
  p_kind text,
  p_channel text,
  p_identifier text,
  p_code_hash text,
  p_ip_hash text default null,
  p_device_hash text default null
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_kind text := lower(trim(coalesce(p_kind,'')));
  v_channel text := lower(trim(coalesce(p_channel,'')));
  v_identifier text := lower(trim(coalesce(p_identifier,'')));
  v_now timestamptz := clock_timestamp();
  v_id uuid;
  v_hash text;
  v_attempts integer;
begin
  if auth.role() is distinct from 'service_role' then raise exception 'service_role required'; end if;
  if v_kind not in ('auth_otp','password_reset') then raise exception 'invalid challenge kind'; end if;
  if v_channel not in ('email','phone') then raise exception 'invalid challenge channel'; end if;
  if v_identifier='' or coalesce(trim(p_code_hash),'')='' then raise exception 'identifier/code hash required'; end if;

  perform public._consume_auth_rate_limit(v_kind||':verify:identifier',v_channel||':'||v_identifier,20,900);
  if coalesce(trim(p_ip_hash),'')<>'' then perform public._consume_auth_rate_limit(v_kind||':verify:ip',trim(p_ip_hash),60,900); end if;
  if coalesce(trim(p_device_hash),'')<>'' then perform public._consume_auth_rate_limit(v_kind||':verify:device',trim(p_device_hash),40,900); end if;
  perform public._consume_auth_rate_limit(v_kind||':verify:global','global',1200,60);

  perform pg_advisory_xact_lock(hashtextextended(v_kind||':'||v_channel||':'||v_identifier,0));

  if v_kind='auth_otp' then
    select id,code_hash,attempt_count into v_id,v_hash,v_attempts
      from public.auth_otp_challenges
      where channel=v_channel and identifier=v_identifier and consumed_at is null and expires_at>v_now
      order by created_at desc limit 1 for update;
  else
    select id,code_hash,attempt_count into v_id,v_hash,v_attempts
      from public.password_reset_challenges
      where channel=v_channel and identifier=v_identifier and consumed_at is null and expires_at>v_now
      order by created_at desc limit 1 for update;
  end if;

  if v_id is null then raise exception 'AUTH_CHALLENGE_INVALID_OR_EXPIRED'; end if;
  if coalesce(v_attempts,0)>=5 then raise exception 'AUTH_CHALLENGE_ATTEMPTS_EXCEEDED'; end if;

  if v_hash is distinct from p_code_hash then
    if v_kind='auth_otp' then
      update public.auth_otp_challenges set attempt_count=attempt_count+1 where id=v_id;
    else
      update public.password_reset_challenges set attempt_count=attempt_count+1 where id=v_id;
    end if;
    raise exception 'AUTH_CHALLENGE_INVALID_OR_EXPIRED';
  end if;

  if v_kind='auth_otp' then
    update public.auth_otp_challenges set consumed_at=v_now where id=v_id and consumed_at is null;
    update public.auth_otp_challenges set expires_at=least(expires_at,v_now)
      where channel=v_channel and identifier=v_identifier and id<>v_id and consumed_at is null and expires_at>v_now;
  else
    update public.password_reset_challenges set consumed_at=v_now where id=v_id and consumed_at is null;
    update public.password_reset_challenges set expires_at=least(expires_at,v_now)
      where channel=v_channel and identifier=v_identifier and id<>v_id and consumed_at is null and expires_at>v_now;
  end if;

  return v_id;
end;
$$;

revoke all on function public.verify_auth_challenge(text,text,text,text,text,text) from public, anon, authenticated;
grant execute on function public.verify_auth_challenge(text,text,text,text,text,text) to service_role;

create index if not exists auth_otp_challenges_identifier_created_idx
  on public.auth_otp_challenges(channel,identifier,created_at desc);
create index if not exists password_reset_challenges_identifier_created_idx
  on public.password_reset_challenges(channel,identifier,created_at desc);
