-- Phase 10 logistics smoke (postgres).
-- Dispatch pick → partial DN → stock/COGS; over-pick denied; cancel reverses;
-- immediate DN blocked; GPS ingest + delivery_* events.

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
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_customer UUID := 'b0000000-0000-4000-8000-000000000099';
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_list UUID;
  v_cart UUID;
  v_inv UUID;
  v_inv_line UUID;
  v_pick UUID;
  v_pick_line UUID;
  v_dn UUID;
  v_dn2 UUID;
  v_job UUID;
  v_loc UUID;
  v_stock_before NUMERIC;
  v_stock_after NUMERIC;
  v_stock_restored NUMERIC;
  v_fulfilled NUMERIC;
  v_cogs_je UUID;
  v_rev_je UUID;
  v_evt INT;
  v_bad BOOLEAN;
BEGIN
  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';

  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/EA missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P10-LOG-001', 'Phase10 logistics smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P10-LOG-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 40, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 40, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'P10 seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 50,
        'unit_cost', 12,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  SELECT quantity INTO v_stock_before
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;

  -- -----------------------------------------------------------------------
  -- 1) Dispatch checkout: no stock issue; qty_fulfilled=0
  -- -----------------------------------------------------------------------
  v_cart := public.create_pos_cart(v_main, NULL, 'USD', 'dispatch');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 10);
  v_inv := public.checkout_pos_cart(v_cart);

  IF NOT EXISTS (
    SELECT 1 FROM public.sales_invoices
    WHERE id = v_inv AND status = 'posted' AND fulfillment_mode = 'dispatch'
  ) THEN
    RAISE EXCEPTION 'smoke fail: dispatch invoice not posted';
  END IF;

  SELECT quantity INTO v_stock_after
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;
  IF v_stock_after IS DISTINCT FROM v_stock_before THEN
    RAISE EXCEPTION 'smoke fail: dispatch checkout must not issue stock (before=% after=%)',
      v_stock_before, v_stock_after;
  END IF;

  SELECT id, qty_fulfilled INTO v_inv_line, v_fulfilled
  FROM public.sales_invoice_lines
  WHERE invoice_id = v_inv AND NOT is_core_charge
  LIMIT 1;
  IF v_fulfilled <> 0 THEN
    RAISE EXCEPTION 'smoke fail: dispatch qty_fulfilled should be 0, got %', v_fulfilled;
  END IF;

  -- -----------------------------------------------------------------------
  -- 2) Partial pick → DN → stock down + qty_fulfilled + COGS
  -- -----------------------------------------------------------------------
  v_pick := public.create_pick_list(
    v_inv,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 4)
    )
  );
  SELECT id INTO v_pick_line FROM public.pick_list_lines WHERE pick_list_id = v_pick LIMIT 1;

  PERFORM public.confirm_pick_lines(
    v_pick,
    jsonb_build_array(
      jsonb_build_object('pick_list_line_id', v_pick_line, 'qty_picked', 4)
    )
  );

  -- Over-pick denied
  v_bad := false;
  BEGIN
    PERFORM public.confirm_pick_lines(
      v_pick,
      jsonb_build_array(
        jsonb_build_object('pick_list_line_id', v_pick_line, 'qty_picked', 99)
      )
    );
  EXCEPTION
    WHEN OTHERS THEN
      v_bad := true;
      IF SQLERRM NOT LIKE '%only draft%' AND SQLERRM NOT LIKE '%over-pick%' THEN
        -- already done — create second pick that overshoots
        NULL;
      END IF;
  END;

  -- Fresh pick attempting over-open
  BEGIN
    PERFORM public.create_pick_list(
      v_inv,
      jsonb_build_array(
        jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 99)
      )
    );
    RAISE EXCEPTION 'smoke fail: over-pick create should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%open qty%' AND SQLERRM NOT LIKE '%cannot pick%' THEN
        RAISE;
      END IF;
  END;

  v_dn := public.create_delivery_note(
    v_inv,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 4)
    ),
    v_pick
  );
  PERFORM public.submit_delivery_note(v_dn);

  SELECT quantity INTO v_stock_after
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;
  IF v_stock_after <> v_stock_before - 4 THEN
    RAISE EXCEPTION 'smoke fail: stock after DN expected % got %',
      v_stock_before - 4, v_stock_after;
  END IF;

  SELECT qty_fulfilled INTO v_fulfilled
  FROM public.sales_invoice_lines WHERE id = v_inv_line;
  IF v_fulfilled <> 4 THEN
    RAISE EXCEPTION 'smoke fail: qty_fulfilled expected 4 got %', v_fulfilled;
  END IF;

  SELECT cogs_journal_entry_id INTO v_cogs_je
  FROM public.delivery_notes WHERE id = v_dn;
  IF v_cogs_je IS NULL THEN
    RAISE EXCEPTION 'smoke fail: COGS journal missing on submitted DN';
  END IF;

  -- Second DN exceeding remaining open (6 left) with 7 → deny
  BEGIN
    v_dn2 := public.create_delivery_note(
      v_inv,
      jsonb_build_array(
        jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 7)
      )
    );
    RAISE EXCEPTION 'smoke fail: over-ship DN create should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%open qty%' AND SQLERRM NOT LIKE '%exceeds%' THEN
        RAISE;
      END IF;
  END;

  -- -----------------------------------------------------------------------
  -- 3) Cancel DN → stock restored + reverse JE
  -- -----------------------------------------------------------------------
  PERFORM public.cancel_delivery_note(v_dn);

  SELECT quantity INTO v_stock_restored
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;
  IF v_stock_restored <> v_stock_before THEN
    RAISE EXCEPTION 'smoke fail: cancel should restore stock (expected % got %)',
      v_stock_before, v_stock_restored;
  END IF;

  SELECT qty_fulfilled INTO v_fulfilled
  FROM public.sales_invoice_lines WHERE id = v_inv_line;
  IF v_fulfilled <> 0 THEN
    RAISE EXCEPTION 'smoke fail: cancel should zero qty_fulfilled, got %', v_fulfilled;
  END IF;

  SELECT reverse_journal_entry_id INTO v_rev_je
  FROM public.delivery_notes WHERE id = v_dn AND status = 'cancelled';
  IF v_rev_je IS NULL THEN
    RAISE EXCEPTION 'smoke fail: reverse JE missing after cancel';
  END IF;

  -- Re-submit path for job/events: new DN for 3
  v_dn := public.create_delivery_note(
    v_inv,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 3)
    )
  );
  PERFORM public.submit_delivery_note(v_dn);

  -- -----------------------------------------------------------------------
  -- 4) Immediate checkout still issues once; DN blocked
  -- -----------------------------------------------------------------------
  SELECT quantity INTO v_stock_before
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;

  v_cart := public.create_pos_cart(v_main, NULL, 'USD', 'immediate');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 2);
  v_inv := public.checkout_pos_cart(v_cart);

  SELECT quantity INTO v_stock_after
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;
  IF v_stock_after <> v_stock_before - 2 THEN
    RAISE EXCEPTION 'smoke fail: immediate checkout should issue 2 (before=% after=%)',
      v_stock_before, v_stock_after;
  END IF;

  SELECT id INTO v_inv_line
  FROM public.sales_invoice_lines
  WHERE invoice_id = v_inv AND NOT is_core_charge
  LIMIT 1;

  BEGIN
    PERFORM public.create_delivery_note(
      v_inv,
      jsonb_build_array(
        jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 1)
      )
    );
    RAISE EXCEPTION 'smoke fail: DN on immediate invoice should be blocked';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%immediate%' AND SQLERRM NOT LIKE '%double-issue%' THEN
        RAISE;
      END IF;
  END;

  -- -----------------------------------------------------------------------
  -- 5) Delivery job + GPS ingest + events; customer SELECT denied
  -- -----------------------------------------------------------------------
  v_job := public.create_delivery_job(v_dn, v_admin, now() + interval '2 hours', 'smoke job');
  PERFORM public.update_delivery_job_status(v_job, 'dispatched');
  v_loc := public.ingest_delivery_location(v_job, -17.8252, 31.0335, now(), 12.5);

  IF v_loc IS NULL THEN
    RAISE EXCEPTION 'smoke fail: location ingest returned null';
  END IF;

  -- Rate limit
  BEGIN
    PERFORM public.ingest_delivery_location(v_job, -17.8253, 31.0336, now(), 10);
    RAISE EXCEPTION 'smoke fail: rate limit should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%rate limit%' THEN
        RAISE;
      END IF;
  END;

  -- POD required to complete (dedicated delivery app P0)
  PERFORM public.submit_delivery_pod(
    v_job,
    'pod/photos/phase10-smoke.jpg',
    'pod/signatures/phase10-smoke.png',
    'phase10 complete'
  );

  SELECT count(*)::int INTO v_evt
  FROM public.domain_events
  WHERE event_code IN ('delivery_dispatched', 'delivery_completed')
    AND payload ->> 'delivery_job_id' = v_job::text;
  IF v_evt < 2 THEN
    RAISE EXCEPTION 'smoke fail: expected delivery_dispatched+completed events, got %', v_evt;
  END IF;

  -- Non-dispatcher (customer) SELECT denied under RLS
  IF EXISTS (SELECT 1 FROM auth.users WHERE id = v_customer) THEN
    PERFORM public._test_set_auth_uid(v_customer);
    SET LOCAL ROLE authenticated;
    IF EXISTS (SELECT 1 FROM public.delivery_locations WHERE id = v_loc) THEN
      RAISE EXCEPTION 'smoke fail: customer must not see delivery_locations';
    END IF;
    RESET ROLE;
    PERFORM public._test_set_auth_uid(v_admin);
  END IF;

  RAISE NOTICE 'phase10_logistics_smoke: PASS inv_dispatch_dn=% job=% loc=%', v_dn, v_job, v_loc;
END;
$$;
