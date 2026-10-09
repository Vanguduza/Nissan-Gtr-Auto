-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905143606 auth_challenge_cancel_v1).
-- Source of record for what production ran; see supabase/live-history/README.md.

create or replace function public.cancel_auth_challenge(
  p_kind text,
  p_challenge_id uuid
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_kind text := lower(trim(coalesce(p_kind,'')));
  v_count integer;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'service_role required';
  end if;
  if p_challenge_id is null then return false; end if;
  if v_kind='auth_otp' then
    update public.auth_otp_challenges
      set expires_at=least(expires_at,clock_timestamp())
      where id=p_challenge_id and consumed_at is null;
  elsif v_kind='password_reset' then
    update public.password_reset_challenges
      set expires_at=least(expires_at,clock_timestamp())
      where id=p_challenge_id and consumed_at is null;
  else
    raise exception 'invalid challenge kind';
  end if;
  get diagnostics v_count = row_count;
  return v_count > 0;
end;
$$;
revoke all on function public.cancel_auth_challenge(text,uuid) from public,anon,authenticated;
grant execute on function public.cancel_auth_challenge(text,uuid) to service_role;
