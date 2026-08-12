-- H8: fund-release insert-once smoke (postgres).
-- Prove create→submit→approve creates one release; conflict / re-approve path
-- does not mutate amount_minor. Requires seed staff + MAIN/EA + 20260812080000+.

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', p_uid::text, 'role', 'authenticated')::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
END;
$$;

DO $$
DECLARE
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_fin UUID := 'a0000000-0000-4000-8000-000000000002';
  v_supplier UUID;
  v_po UUID;
  v_po_line UUID;
  v_release UUID;
  v_amount NUMERIC;
  v_amount_minor BIGINT;
  v_amount_minor_after BIGINT;
  v_release_count INT;
  v_evt_po INT;
  v_evt_funds INT;
  v_evt_amount NUMERIC;
  v_evt_amount_minor BIGINT;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';

  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'H8 smoke fail: MAIN/EA missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, requires_serial)
  VALUES ('H8-FUND-ONCE', 'H8 fund-release insert-once part', v_uom, false)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'H8-FUND-ONCE';
  END IF;

  PERFORM public._test_set_auth_uid(v_admin);
  SELECT id INTO v_supplier FROM public.suppliers WHERE code = 'H8-SUP' LIMIT 1;
  IF v_supplier IS NULL THEN
    v_supplier := public.create_supplier('H8-SUP', 'H8 Fund Release Supplier');
  END IF;

  -- create → submit → approve (quoted total = 2 × 10.00 = 20.00 → minor 2000)
  v_po := public.create_purchase_order(
    v_supplier,
    v_main,
    'USD',
    1,
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 2,
        'unit_price', 10.00,
        'currency', 'USD'
      )
    ),
    'H8 insert-once smoke'
  );
  PERFORM public.submit_purchase_order(v_po);

  SELECT id INTO v_po_line
  FROM public.purchase_order_lines
  WHERE purchase_order_id = v_po
  LIMIT 1;

  PERFORM public._test_set_auth_uid(v_fin);
  PERFORM public.approve_purchase_order(v_po);

  SELECT id, amount, amount_minor
  INTO v_release, v_amount, v_amount_minor
  FROM public.procurement_fund_releases
  WHERE purchase_order_id = v_po;

  IF v_release IS NULL THEN
    RAISE EXCEPTION 'H8 smoke fail: fund release missing after approve';
  END IF;
  IF v_amount IS DISTINCT FROM 20.00 THEN
    RAISE EXCEPTION 'H8 smoke fail: amount expected 20 got %', v_amount;
  END IF;
  IF v_amount_minor IS DISTINCT FROM 2000 THEN
    RAISE EXCEPTION 'H8 smoke fail: amount_minor expected 2000 got %', v_amount_minor;
  END IF;

  SELECT COUNT(*)::int INTO v_release_count
  FROM public.procurement_fund_releases
  WHERE purchase_order_id = v_po;
  IF v_release_count <> 1 THEN
    RAISE EXCEPTION 'H8 smoke fail: expected 1 fund release got %', v_release_count;
  END IF;

  -- Second approve while approved must refuse (status gate) without money mutation
  BEGIN
    PERFORM public.approve_purchase_order(v_po);
    RAISE EXCEPTION 'H8 smoke fail: second approve should raise when status=approved';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%only submitted%' THEN
        RAISE;
      END IF;
  END;

  SELECT amount_minor INTO v_amount_minor_after
  FROM public.procurement_fund_releases
  WHERE purchase_order_id = v_po;
  IF v_amount_minor_after IS DISTINCT FROM 2000 THEN
    RAISE EXCEPTION 'H8 smoke fail: amount_minor mutated after failed re-approve (% )', v_amount_minor_after;
  END IF;

  -- Conflict path: bump quoted total, force status back to submitted, re-approve.
  -- Insert-once must keep original amount_minor (not rewrite to new total).
  -- Clear prior domain events so re-approve emit can be asserted (dedupe would no-op).
  -- Direct UPDATEs require procurement RPC flag (mutation guards).
  PERFORM public._procurement_begin_rpc();
  UPDATE public.purchase_order_lines
  SET unit_price = 99.00
  WHERE id = v_po_line;

  UPDATE public.purchase_orders
  SET
    status = 'submitted',
    approved_by = NULL,
    approved_at = NULL,
    funds_released_at = NULL,
    progress_step = 'submitted',
    updated_at = now()
  WHERE id = v_po;

  DELETE FROM public.domain_events
  WHERE dedupe_key IN (
    'purchase_order_approved:' || v_po::text,
    'procurement_fund_release:' || v_release::text
  );
  PERFORM public._procurement_end_rpc();

  PERFORM public._test_set_auth_uid(v_fin);
  PERFORM public.approve_purchase_order(v_po);

  SELECT COUNT(*)::int, MAX(amount_minor)
  INTO v_release_count, v_amount_minor_after
  FROM public.procurement_fund_releases
  WHERE purchase_order_id = v_po;

  IF v_release_count <> 1 THEN
    RAISE EXCEPTION 'H8 smoke fail: conflict re-approve created extra release (count=%)', v_release_count;
  END IF;
  IF v_amount_minor_after IS DISTINCT FROM 2000 THEN
    RAISE EXCEPTION
      'H8 smoke fail: ON CONFLICT must not rewrite amount_minor (got % want 2000)',
      v_amount_minor_after;
  END IF;

  -- Conflict-path events must carry stored release money, not recalculated quoted total.
  SELECT COUNT(*)::int,
         MAX((payload->>'amount')::numeric),
         MAX((payload->>'amount_minor')::bigint)
  INTO v_evt_po, v_evt_amount, v_evt_amount_minor
  FROM public.domain_events
  WHERE event_code = 'po_approved'
    AND dedupe_key = 'purchase_order_approved:' || v_po::text;
  IF v_evt_po <> 1 THEN
    RAISE EXCEPTION 'H8 smoke fail: po_approved event count=% (want 1)', v_evt_po;
  END IF;
  IF v_evt_amount IS DISTINCT FROM 20.00 OR v_evt_amount_minor IS DISTINCT FROM 2000 THEN
    RAISE EXCEPTION
      'H8 smoke fail: po_approved payload must use stored release (got amount=% minor=%)',
      v_evt_amount, v_evt_amount_minor;
  END IF;

  SELECT COUNT(*)::int,
         MAX((payload->>'amount')::numeric),
         MAX((payload->>'amount_minor')::bigint)
  INTO v_evt_funds, v_evt_amount, v_evt_amount_minor
  FROM public.domain_events
  WHERE event_code = 'procurement_funds_released'
    AND dedupe_key = 'procurement_fund_release:' || v_release::text;
  IF v_evt_funds <> 1 THEN
    RAISE EXCEPTION 'H8 smoke fail: procurement_funds_released event count=% (want 1)', v_evt_funds;
  END IF;
  IF v_evt_amount IS DISTINCT FROM 20.00 OR v_evt_amount_minor IS DISTINCT FROM 2000 THEN
    RAISE EXCEPTION
      'H8 smoke fail: funds_released payload must use stored release (got amount=% minor=%)',
      v_evt_amount, v_evt_amount_minor;
  END IF;

  -- Direct conflict INSERT must also leave money untouched
  INSERT INTO public.procurement_fund_releases (
    purchase_order_id,
    requesting_official_id,
    approved_by,
    amount,
    amount_minor,
    currency,
    status,
    notes
  )
  VALUES (
    v_po,
    v_admin,
    v_fin,
    999.00,
    99900,
    'USD',
    'released',
    'H8 conflict probe — must DO NOTHING'
  )
  ON CONFLICT (purchase_order_id) DO NOTHING;

  SELECT amount_minor INTO v_amount_minor_after
  FROM public.procurement_fund_releases
  WHERE purchase_order_id = v_po;
  IF v_amount_minor_after IS DISTINCT FROM 2000 THEN
    RAISE EXCEPTION 'H8 smoke fail: direct conflict INSERT mutated amount_minor';
  END IF;

  RAISE NOTICE 'H8 fund-release insert-once smoke OK';
END;
$$;
