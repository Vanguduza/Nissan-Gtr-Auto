CREATE EXTENSION IF NOT EXISTS pg_cron WITH SCHEMA pg_catalog;

CREATE OR REPLACE FUNCTION private.expire_commerce_checkouts_internal()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  r RECORD;
  v_count INTEGER := 0;
BEGIN
  FOR r IN
    SELECT id
    FROM public.commerce_orders
    WHERE state IN ('awaiting_payment','payment_processing','payment_failed')
      AND reservation_expires_at IS NOT NULL
      AND reservation_expires_at <= now()
      AND settled_payment_entry_id IS NULL
    FOR UPDATE SKIP LOCKED
  LOOP
    PERFORM private.release_commerce_order(r.id, 'payment_expired', 'reservation TTL expired');
    v_count := v_count + 1;
  END LOOP;
  RETURN v_count;
END;
$$;

REVOKE ALL ON FUNCTION private.expire_commerce_checkouts_internal() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.expire_commerce_checkouts_internal() TO service_role;

CREATE OR REPLACE FUNCTION public.expire_commerce_checkouts()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'service role required';
  END IF;
  RETURN private.expire_commerce_checkouts_internal();
END;
$$;

REVOKE ALL ON FUNCTION public.expire_commerce_checkouts() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.expire_commerce_checkouts() TO service_role;

DO $$
DECLARE
  v_jobid bigint;
BEGIN
  SELECT jobid INTO v_jobid FROM cron.job WHERE jobname = 'commerce-expire-checkouts-v1';
  IF v_jobid IS NOT NULL THEN
    PERFORM cron.unschedule(v_jobid);
  END IF;
  PERFORM cron.schedule(
    'commerce-expire-checkouts-v1',
    '*/2 * * * *',
    'SELECT private.expire_commerce_checkouts_internal();'
  );
END;
$$;
