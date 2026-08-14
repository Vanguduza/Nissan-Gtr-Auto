-- Smoke: get_delivery_job_settlement authz + minor derivation.
-- Run: docker exec -i <db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 < this file
-- Requires seed logistics + a delivery job with assignee (phase10 / dedicated delivery seeds).

BEGIN;

DO $$
DECLARE
  v_job_id UUID;
  v_assignee UUID;
  v_total NUMERIC;
  v_paid NUMERIC;
  v_due NUMERIC;
  v_due_minor BIGINT;
  v_count INT;
BEGIN
  SELECT dj.id, dj.assignee_user_id, si.total, si.amount_paid
  INTO v_job_id, v_assignee, v_total, v_paid
  FROM public.delivery_jobs dj
  JOIN public.delivery_notes dn ON dn.id = dj.delivery_note_id
  JOIN public.sales_invoices si ON si.id = dn.sales_invoice_id
  WHERE dj.assignee_user_id IS NOT NULL
  LIMIT 1;

  IF v_job_id IS NULL THEN
    RAISE NOTICE 'SKIP get_delivery_job_settlement_smoke: no assigned delivery job in seed';
    RETURN;
  END IF;

  -- As assignee
  PERFORM set_config('request.jwt.claim.sub', v_assignee::text, true);
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

  SELECT COUNT(*) INTO v_count
  FROM public.get_delivery_job_settlement(v_job_id);
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'assignee should see settlement row';
  END IF;

  SELECT amount_due, amount_due_minor
  INTO v_due, v_due_minor
  FROM public.get_delivery_job_settlement(v_job_id);

  IF v_due IS DISTINCT FROM GREATEST(COALESCE(v_total, 0) - COALESCE(v_paid, 0), 0) THEN
    RAISE EXCEPTION 'amount_due mismatch';
  END IF;
  IF v_due_minor IS DISTINCT FROM public._major_to_minor(v_due) THEN
    RAISE EXCEPTION 'amount_due_minor mismatch';
  END IF;

  -- Non-assignee random uid denied
  PERFORM set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000009999', true);
  BEGIN
    PERFORM * FROM public.get_delivery_job_settlement(v_job_id);
    RAISE EXCEPTION 'non-assignee must be denied';
  EXCEPTION
    WHEN others THEN
      IF SQLERRM NOT LIKE '%not authorized%' THEN
        RAISE;
      END IF;
  END;

  RAISE NOTICE 'PASS get_delivery_job_settlement_smoke';
END $$;

ROLLBACK;
