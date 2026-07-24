-- Phase 13 payments / ContiPay / receipts / SMS / forecast smoke
-- Run via: docker exec -i supabase_db_… psql -U postgres < this file
-- Exclusions: no ZIMRA / FDMS / fiscal / payroll tax strings in receipt path.

DO $$
DECLARE
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_list UUID;
  v_cust UUID;
  v_cart UUID;
  v_inv UUID;
  v_pe UUID;
  v_pe2 UUID;
  v_paid NUMERIC;
  v_open NUMERIC;
  v_je UUID;
  v_dr NUMERIC;
  v_cr NUMERIC;
  v_intent UUID;
  v_pe_cp UUID;
  v_sc_bal NUMERIC;
  v_ledger UUID;
  v_art UUID;
  v_sms_body TEXT;
  v_sent INT;
  v_again INT;
  v_sug UUID;
  v_mr UUID;
  v_mgr UUID;
  v_outbox_before INT;
  v_outbox_after INT;
  v_ev UUID;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, reorder_point, reorder_qty)
  VALUES ('P13-SMOKE-001', 'Phase13 smoke part', v_uom, 100, 25)
  ON CONFLICT (oem_part_number) DO UPDATE
  SET description = EXCLUDED.description,
      reorder_point = 100,
      reorder_qty = 25,
      base_uom_id = COALESCE(public.stock_items.base_uom_id, EXCLUDED.base_uom_id)
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P13-SMOKE-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 40, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 40, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'P13 seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 50,
        'unit_cost', 10,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  INSERT INTO public.customers (display_name, phone_e164, email, whatsapp_e164, currency, sms_receipts, email_receipts, whatsapp_receipts)
  VALUES ('P13 Smoke Customer', '+263771300001', 'p13@example.com', '+263771300001', 'USD', true, true, true)
  RETURNING id INTO v_cust;

  -- Manager prefs for payment SMS (opt-in). Use a synthetic profile if needed.
  SELECT id INTO v_mgr FROM public.profiles LIMIT 1;
  IF v_mgr IS NOT NULL THEN
    INSERT INTO public.manager_sms_preferences (user_id, event_code, phone_e164, enabled)
    VALUES
      (v_mgr, 'payment_received', '+263771399999', true),
      (v_mgr, 'payment_partial', '+263771399999', true),
      (v_mgr, 'refund_issued', '+263771399999', true)
    ON CONFLICT (user_id, event_code) DO UPDATE
    SET enabled = true, phone_e164 = EXCLUDED.phone_e164;
  END IF;

  v_cart := public.create_pos_cart(v_main, v_cust, 'USD');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 2); -- 80 total
  v_inv := public.checkout_pos_cart(v_cart);

  IF NOT EXISTS (
    SELECT 1 FROM public.sales_invoices
    WHERE id = v_inv AND status = 'posted' AND total = 80 AND amount_paid = 0
  ) THEN
    RAISE EXCEPTION 'smoke fail: invoice not posted with open AR';
  END IF;

  -- 1) Partial cash payment
  v_pe := public.create_payment_entry(v_cust, 'cash', 30, 'USD', 1, 'partial');
  PERFORM public.allocate_payment(
    v_pe,
    jsonb_build_array(jsonb_build_object('sales_invoice_id', v_inv, 'amount', 30))
  );
  PERFORM public.post_payment_entry(v_pe);

  SELECT amount_paid INTO v_paid FROM public.sales_invoices WHERE id = v_inv;
  IF v_paid <> 30 THEN
    RAISE EXCEPTION 'smoke fail: amount_paid expected 30 got %', v_paid;
  END IF;

  SELECT journal_entry_id INTO v_je FROM public.payment_entries WHERE id = v_pe;
  SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
  INTO v_dr, v_cr
  FROM public.journal_entry_lines WHERE journal_entry_id = v_je;
  IF v_dr <> v_cr OR v_dr <> 30 THEN
    RAISE EXCEPTION 'smoke fail: payment JE unbalanced dr=% cr=%', v_dr, v_cr;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.domain_events
    WHERE event_code = 'payment_partial' AND dedupe_key = 'payment:payment_partial:' || v_pe::text
  ) THEN
    RAISE EXCEPTION 'smoke fail: payment_partial domain event missing';
  END IF;

  -- Over-allocate denied
  v_pe2 := public.create_payment_entry(v_cust, 'cash', 100, 'USD', 1, 'too much');
  BEGIN
    PERFORM public.allocate_payment(
      v_pe2,
      jsonb_build_array(jsonb_build_object('sales_invoice_id', v_inv, 'amount', 100))
    );
    RAISE EXCEPTION 'smoke fail: over-allocate allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
      IF SQLERRM NOT ILIKE '%over-allocate%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected over-allocate error: %', SQLERRM;
      END IF;
  END;

  -- Clear remainder
  PERFORM public.allocate_payment(
    v_pe2,
    jsonb_build_array(jsonb_build_object('sales_invoice_id', v_inv, 'amount', 50))
  );
  -- Shrink payment amount via cancel draft + new entry (amount fixed at create)
  PERFORM public.cancel_payment_entry(v_pe2);
  v_pe2 := public.create_payment_entry(v_cust, 'cash', 50, 'USD', 1, 'clear');
  PERFORM public.allocate_payment(
    v_pe2,
    jsonb_build_array(jsonb_build_object('sales_invoice_id', v_inv, 'amount', 50))
  );
  PERFORM public.post_payment_entry(v_pe2);

  SELECT amount_paid, total - amount_paid INTO v_paid, v_open
  FROM public.sales_invoices WHERE id = v_inv;
  IF v_paid <> 80 OR v_open <> 0 THEN
    RAISE EXCEPTION 'smoke fail: invoice not cleared paid=% open=%', v_paid, v_open;
  END IF;

  -- 2) ContiPay intent → settle (idempotent duplicate webhook)
  v_cart := public.create_pos_cart(v_main, v_cust, 'USD');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 1); -- 40
  v_inv := public.checkout_pos_cart(v_cart);

  v_intent := public.create_contipay_intent(
    'P13-CP-' || v_inv::text,
    'ecocash',
    40,
    'USD',
    1,
    v_cust,
    'ZIG',
    1200,
    30
  );

  v_pe_cp := public.mark_contipay_settled(
    'P13-CP-' || v_inv::text,
    'hash-p13-' || v_inv::text,
    'prov-1',
    jsonb_build_array(jsonb_build_object('sales_invoice_id', v_inv, 'amount', 40)),
    'ZIG',
    1200,
    30,
    true,
    NULL
  );

  IF v_pe_cp IS NULL THEN
    RAISE EXCEPTION 'smoke fail: ContiPay settle returned null';
  END IF;

  -- Duplicate webhook must not double-post
  PERFORM public.mark_contipay_settled(
    'P13-CP-' || v_inv::text,
    'hash-p13-' || v_inv::text,
    'prov-1',
    jsonb_build_array(jsonb_build_object('sales_invoice_id', v_inv, 'amount', 40)),
    'ZIG', 1200, 30, true, NULL
  );

  SELECT amount_paid INTO v_paid FROM public.sales_invoices WHERE id = v_inv;
  IF v_paid <> 40 THEN
    RAISE EXCEPTION 'smoke fail: ContiPay double settle paid=%', v_paid;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.domain_events WHERE event_code = 'payment_received'
      AND dedupe_key LIKE 'payment:payment_received:%'
  ) THEN
    RAISE EXCEPTION 'smoke fail: payment_received event missing';
  END IF;

  IF v_mgr IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM public.sms_outbox
    WHERE event_code IN ('payment_received', 'payment_partial')
      AND recipient_user_id = v_mgr
  ) THEN
    RAISE EXCEPTION 'smoke fail: manager sms_outbox row missing for opted-in prefs';
  END IF;

  -- 3) Store credit issue + redeem
  v_ledger := public.issue_store_credit(v_cust, 15, 'USD', 1, 'P13 refund', '1100');
  SELECT balance INTO v_sc_bal FROM public.store_credit_accounts WHERE customer_id = v_cust;
  IF v_sc_bal <> 15 THEN
    RAISE EXCEPTION 'smoke fail: store credit balance expected 15 got %', v_sc_bal;
  END IF;

  v_cart := public.create_pos_cart(v_main, v_cust, 'USD');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 1);
  v_inv := public.checkout_pos_cart(v_cart);

  PERFORM public.redeem_store_credit(v_cust, 15, 'USD', 1, v_inv, 'redeem smoke');
  SELECT balance INTO v_sc_bal FROM public.store_credit_accounts WHERE customer_id = v_cust;
  IF v_sc_bal <> 0 THEN
    RAISE EXCEPTION 'smoke fail: store credit not zero after redeem (% )', v_sc_bal;
  END IF;

  BEGIN
    PERFORM public.redeem_store_credit(v_cust, 1, 'USD', 1, v_inv, 'overdraw');
    RAISE EXCEPTION 'smoke fail: store credit overdraw allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'smoke fail:%' THEN RAISE; END IF;
  END;

  -- 4) Receipt PDF + channel send (tax-agnostic URL)
  v_art := public.mark_receipt_pdf_ready(
    v_inv,
    'customer-receipts/' || v_inv::text || '.pdf',
    'p13tok' || replace(v_inv::text, '-', '')
  );

  SELECT summary_body INTO v_sms_body
  FROM public.customer_receipt_outbox
  WHERE document_id = v_inv AND channel = 'sms';

  IF v_sms_body IS NULL OR v_sms_body NOT ILIKE '%nissangtrauto.co.zw%' THEN
    RAISE EXCEPTION 'smoke fail: SMS body missing company domain link';
  END IF;
  IF v_sms_body ~* '(zimra|fdms|fiscal)' THEN
    RAISE EXCEPTION 'smoke fail: fiscal marker in SMS body';
  END IF;

  SELECT count(*)::int INTO v_outbox_before
  FROM public.customer_receipt_outbox
  WHERE document_id = v_inv AND status = 'pending';

  v_sent := public.process_receipt_outbox_batch(20, true);
  IF v_sent < 1 THEN
    RAISE EXCEPTION 'smoke fail: receipt batch sent 0 (pending was %)', v_outbox_before;
  END IF;

  SELECT count(*)::int INTO v_outbox_after
  FROM public.customer_receipt_outbox
  WHERE document_id = v_inv AND status = 'sent';
  IF v_outbox_after < 1 THEN
    RAISE EXCEPTION 'smoke fail: no sent receipt channels';
  END IF;

  v_again := public.process_receipt_outbox_batch(20, true);
  IF v_again <> 0 THEN
    RAISE EXCEPTION 'smoke fail: re-drain duplicated successful receipt channels (% )', v_again;
  END IF;

  -- 5) Manager SMS stub drain
  v_sent := public.drain_sms_outbox_batch(50, true);

  -- Prefs-off manager: ensure disabled event does not enqueue new row for payment_failed without prefer
  -- (catalog emit with no prefs → no outbox). Emit payment_failed with unique key.
  SELECT count(*)::int INTO v_outbox_before FROM public.sms_outbox WHERE event_code = 'payment_failed';
  v_ev := public.emit_domain_event(
    'payment_failed',
    'p13:fail:nopref:' || gen_random_uuid()::text,
    '{}'::jsonb,
    NULL,
    'GTR Auto: payment failed smoke'
  );
  SELECT count(*)::int INTO v_outbox_after FROM public.sms_outbox WHERE event_code = 'payment_failed';
  IF v_outbox_after <> v_outbox_before THEN
    -- Only fail if someone unexpectedly opted into payment_failed
    IF EXISTS (
      SELECT 1 FROM public.manager_sms_preferences
      WHERE event_code = 'payment_failed' AND enabled
    ) THEN
      NULL; -- ok
    ELSE
      RAISE EXCEPTION 'smoke fail: payment_failed enqueued without prefs';
    END IF;
  END IF;

  -- 6) Forecast → draft MR (no PO)
  -- Force low stock relative to reorder_point
  UPDATE public.stock_levels
  SET quantity = 5
  WHERE stock_item_id = v_item AND warehouse_id = v_main;

  PERFORM public.generate_forecast_suggestions(v_main, 30, 25);

  SELECT id INTO v_sug
  FROM public.forecast_suggestions
  WHERE stock_item_id = v_item AND warehouse_id = v_main AND status = 'open';

  IF v_sug IS NULL THEN
    RAISE EXCEPTION 'smoke fail: forecast suggestion missing';
  END IF;

  v_mr := public.create_mr_from_forecast(ARRAY[v_sug], CURRENT_DATE + 7, 'P13 forecast MR');
  IF NOT EXISTS (
    SELECT 1 FROM public.material_requests WHERE id = v_mr AND status = 'draft'
  ) THEN
    RAISE EXCEPTION 'smoke fail: MR not draft';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.purchase_orders WHERE material_request_id = v_mr
  ) THEN
    RAISE EXCEPTION 'smoke fail: auto PO created from forecast';
  END IF;

  -- Exclusion grep-ish: no fiscal columns on artifacts
  IF EXISTS (
    SELECT 1 FROM public.receipt_pdf_artifacts WHERE has_fiscal_payload = true
  ) THEN
    RAISE EXCEPTION 'smoke fail: fiscal payload flag set';
  END IF;

  RAISE NOTICE 'phase13_payments_receipts_smoke: PASS pe=% contipay=% sc=% art=% mr=% sms_drained=%',
    v_pe, v_pe_cp, v_ledger, v_art, v_mr, v_sent;
END;
$$;
