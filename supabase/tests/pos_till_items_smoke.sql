-- Smoke: list_pos_till_items + TillItem shape on pull_pos_offline_snapshot (P0b).
-- Existence + signature + grant + no Meili qty in function body.
-- Live data asserts (document for manual/staff session):
--   * shop_stock returns priced non-quarantine WH rows; saleable_qty from stock_levels
--   * oems hydrates known OEMs in request order; missing OEMs omitted
--   * snapshot items include chassis_codes / engine_codes arrays (possibly empty)
-- No ZIMRA. Does not post sales.

DO $$
DECLARE
  v_has_fn BOOLEAN;
  v_args TEXT;
  v_def TEXT;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'list_pos_till_items'
  ) INTO v_has_fn;
  IF NOT v_has_fn THEN
    RAISE EXCEPTION 'smoke fail: list_pos_till_items missing';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'pull_pos_offline_snapshot'
  ) INTO v_has_fn;
  IF NOT v_has_fn THEN
    RAISE EXCEPTION 'smoke fail: pull_pos_offline_snapshot missing';
  END IF;

  SELECT pg_get_function_arguments(p.oid)
  INTO v_args
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.proname = 'list_pos_till_items'
  LIMIT 1;

  IF v_args IS NULL
     OR v_args NOT ILIKE '%p_warehouse_id%'
     OR v_args NOT ILIKE '%p_source%'
     OR v_args NOT ILIKE '%p_oems%'
     OR v_args NOT ILIKE '%p_section_key%'
  THEN
    RAISE EXCEPTION 'smoke fail: list_pos_till_items signature unexpected: %', v_args;
  END IF;

  -- EXECUTE granted to authenticated (staff RPC pattern)
  IF NOT EXISTS (
    SELECT 1
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    JOIN pg_roles r ON r.rolname = 'authenticated'
    WHERE n.nspname = 'public'
      AND p.proname = 'list_pos_till_items'
      AND has_function_privilege(r.oid, p.oid, 'EXECUTE')
  ) THEN
    RAISE EXCEPTION 'smoke fail: authenticated lacks EXECUTE on list_pos_till_items';
  END IF;

  SELECT pg_get_functiondef(p.oid)
  INTO v_def
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.proname = 'list_pos_till_items'
  LIMIT 1;

  IF v_def ILIKE '%search_catalog%'
     OR v_def ILIKE '%meili%'
     OR v_def ILIKE '%meilisearch%'
  THEN
    RAISE EXCEPTION 'smoke fail: list_pos_till_items must not source qty from Meili/search_catalog';
  END IF;

  IF v_def NOT ILIKE '%stock_levels%' THEN
    RAISE EXCEPTION 'smoke fail: list_pos_till_items must read saleable_qty from stock_levels';
  END IF;

  IF v_def NOT ILIKE '%chassis_codes%' OR v_def NOT ILIKE '%engine_codes%' THEN
    RAISE EXCEPTION 'smoke fail: TillItem chassis_codes/engine_codes missing from list_pos_till_items';
  END IF;

  SELECT pg_get_functiondef(p.oid)
  INTO v_def
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.proname = 'pull_pos_offline_snapshot'
  LIMIT 1;

  IF v_def NOT ILIKE '%chassis_codes%' OR v_def NOT ILIKE '%engine_codes%' THEN
    RAISE EXCEPTION 'smoke fail: snapshot TillItem missing chassis_codes/engine_codes';
  END IF;

  IF v_def NOT ILIKE '%pnc_code%'
     OR v_def NOT ILIKE '%category_name%'
     OR v_def NOT ILIKE '%superseded_by%'
     OR v_def NOT ILIKE '%bin_code%'
  THEN
    RAISE EXCEPTION 'smoke fail: snapshot TillItem missing additive fields';
  END IF;

  RAISE NOTICE 'pos_till_items_smoke ok — shop_stock=priced WH rows; oems hydrates; qty=stock_levels; snapshot has chassis/engine arrays';
END;
$$;
