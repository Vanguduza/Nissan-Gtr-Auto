-- Smoke coverage for locked operator POS read models.
DO $$
DECLARE
  v_count integer;
BEGIN
  PERFORM set_config('request.jwt.claim.role', 'service_role', true);

  IF to_regprocedure('public.list_pos_popular_spares(integer,integer)') IS NULL THEN
    RAISE EXCEPTION 'list_pos_popular_spares missing';
  END IF;
  IF to_regprocedure('public.list_pos_recent_invoices(text,integer)') IS NULL THEN
    RAISE EXCEPTION 'list_pos_recent_invoices missing';
  END IF;

  SELECT count(*) INTO v_count
  FROM public.list_pos_popular_spares(90, 8);
  IF v_count > 8 THEN
    RAISE EXCEPTION 'popular spares limit not enforced: %', v_count;
  END IF;

  SELECT count(*) INTO v_count
  FROM public.list_pos_recent_invoices(NULL, 20);
  IF v_count > 20 THEN
    RAISE EXCEPTION 'recent invoice limit not enforced: %', v_count;
  END IF;
END;
$$;
