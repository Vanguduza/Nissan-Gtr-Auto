-- Phase 4b stock reconciliation smoke (postgres).
-- Requires MAIN warehouse, EA uom, CoA 1300/5100, seed users from seed.sql.

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
END;
$$;

DO $$
DECLARE
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_recon UUID;
  v_qty NUMERIC;
  v_status public.stock_entry_status;
  v_journal UUID;
  v_rev UUID;
  v_wh UUID := 'a0000000-0000-4000-8000-000000000003';
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_debit NUMERIC;
  v_credit NUMERIC;
  v_threshold JSONB;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';

  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/EA missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, requires_serial)
  VALUES ('RECON-SMOKE-001', 'Recon smoke part', v_uom, false)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;

  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'RECON-SMOKE-001';
  END IF;

  PERFORM public.post_stock_receipt(
    v_main,
    'Recon smoke receipt',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 20,
        'unit_cost', 10,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  -- Write-down: count 15 vs system 20 (variance $50, below default $500 threshold)
  PERFORM public._test_set_auth_uid(v_wh);
  v_recon := public.create_stock_reconciliation_draft(
    v_main, 'partial', ARRAY[v_item], 'Smoke write-down', 'USD', 1
  );

  PERFORM public.upsert_stock_reconciliation_lines(
    v_recon,
    jsonb_build_array(jsonb_build_object('stock_item_id', v_item, 'counted_qty', 15))
  );

  PERFORM public.submit_stock_reconciliation(v_recon);

  SELECT status, journal_entry_id INTO v_status, v_journal
  FROM public.stock_reconciliations WHERE id = v_recon;

  IF v_status <> 'posted' THEN
    RAISE EXCEPTION 'smoke fail: write-down not auto-posted (status=%)', v_status;
  END IF;

  SELECT quantity INTO v_qty
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;

  IF v_qty IS DISTINCT FROM 15 THEN
    RAISE EXCEPTION 'smoke fail: on-hand not set to counted qty (%)', v_qty;
  END IF;

  SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
  INTO v_debit, v_credit
  FROM public.journal_entry_lines
  WHERE journal_entry_id = v_journal;

  IF abs(v_debit - v_credit) > 0.01 OR v_debit = 0 THEN
    RAISE EXCEPTION 'smoke fail: write-down journal unbalanced (% / %)', v_debit, v_credit;
  END IF;

  -- Cancel reverses stock + journal
  PERFORM public.cancel_stock_reconciliation(v_recon, 'Smoke cancel');

  SELECT status, reversal_journal_entry_id INTO v_status, v_rev
  FROM public.stock_reconciliations WHERE id = v_recon;

  IF v_status <> 'cancelled' OR v_rev IS NULL THEN
    RAISE EXCEPTION 'smoke fail: cancel did not reverse journal';
  END IF;

  SELECT quantity INTO v_qty
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;

  IF v_qty IS DISTINCT FROM 20 THEN
    RAISE EXCEPTION 'smoke fail: cancel did not restore system qty (%)', v_qty;
  END IF;

  -- Write-up: count 22 vs system 20
  v_recon := public.create_stock_reconciliation_draft(
    v_main, 'partial', ARRAY[v_item], 'Smoke write-up', 'USD', 1
  );
  PERFORM public.upsert_stock_reconciliation_lines(
    v_recon,
    jsonb_build_array(jsonb_build_object('stock_item_id', v_item, 'counted_qty', 22))
  );
  PERFORM public.submit_stock_reconciliation(v_recon);

  SELECT quantity INTO v_qty
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;

  IF v_qty IS DISTINCT FROM 22 THEN
    RAISE EXCEPTION 'smoke fail: write-up on-hand wrong (%)', v_qty;
  END IF;

  -- Direct mutation on posted header/lines must be denied (RLS + triggers)
  BEGIN
    UPDATE public.stock_reconciliations
    SET status = 'draft', first_approver_id = v_admin
    WHERE id = v_recon;
    RAISE EXCEPTION 'smoke fail: direct UPDATE on posted reconciliation should be denied';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%immutable%'
        AND SQLERRM NOT LIKE '%reconciliation RPCs%'
        AND SQLERRM NOT LIKE '%create_stock_reconciliation_draft%'
      THEN
        RAISE;
      END IF;
  END;

  BEGIN
    UPDATE public.stock_reconciliation_lines
    SET counted_qty = 0
    WHERE stock_reconciliation_id = v_recon;
    RAISE EXCEPTION 'smoke fail: direct UPDATE on posted reconciliation lines should be denied';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%immutable%'
        AND SQLERRM NOT LIKE '%reconciliation RPCs%'
        AND SQLERRM NOT LIKE '%parent must be draft%'
      THEN
        RAISE;
      END IF;
  END;

  -- Dual-auth: force threshold to 0, submit large variance, approve as distinct user
  SELECT value_json INTO v_threshold
  FROM public.app_settings
  WHERE key = 'inventory.reconciliation_variance_dual_auth_threshold';

  UPDATE public.app_settings
  SET value_json = '{"USD": 0}'::jsonb
  WHERE key = 'inventory.reconciliation_variance_dual_auth_threshold';

  v_recon := public.create_stock_reconciliation_draft(
    v_main, 'partial', ARRAY[v_item], 'Smoke dual-auth', 'USD', 1
  );
  PERFORM public.upsert_stock_reconciliation_lines(
    v_recon,
    jsonb_build_array(jsonb_build_object('stock_item_id', v_item, 'counted_qty', 10))
  );

  PERFORM public._test_set_auth_uid(v_wh);
  PERFORM public.submit_stock_reconciliation(v_recon);

  SELECT status INTO v_status FROM public.stock_reconciliations WHERE id = v_recon;
  IF v_status <> 'pending_approval' THEN
    RAISE EXCEPTION 'smoke fail: large variance did not require dual-auth (status=%)', v_status;
  END IF;

  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM public.approve_stock_reconciliation(v_recon);

  SELECT status INTO v_status FROM public.stock_reconciliations WHERE id = v_recon;
  IF v_status <> 'posted' THEN
    RAISE EXCEPTION 'smoke fail: approve did not post (status=%)', v_status;
  END IF;

  UPDATE public.app_settings
  SET value_json = v_threshold
  WHERE key = 'inventory.reconciliation_variance_dual_auth_threshold';

  RAISE NOTICE 'phase4b_reconciliation_smoke: PASS';
END;
$$;

DROP FUNCTION IF EXISTS public._test_set_auth_uid(UUID);
