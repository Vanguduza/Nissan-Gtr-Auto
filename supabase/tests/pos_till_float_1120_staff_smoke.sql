-- Smoke: sales/warehouse may open/close 1120; 1110 stays finance-only.
-- Run after migration 20260815153000_pos_till_float_1120_staff.sql

DO $$
DECLARE
  v_fn TEXT;
BEGIN
  SELECT pg_get_functiondef(p.oid) INTO v_fn
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.proname = 'open_account_period'
  LIMIT 1;

  IF v_fn IS NULL OR position('1120' IN v_fn) = 0 THEN
    RAISE EXCEPTION 'smoke fail: open_account_period missing 1120 sales/warehouse gate';
  END IF;

  IF position('1110' IN v_fn) = 0 THEN
    RAISE EXCEPTION 'smoke fail: open_account_period missing 1110 finance-only guard';
  END IF;

  RAISE NOTICE 'smoke ok: open_account_period 1120 staff / 1110 finance-only';
END $$;
