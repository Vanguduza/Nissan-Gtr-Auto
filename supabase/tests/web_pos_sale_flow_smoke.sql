-- Web / tablet POS sale flow as a signed-in sales cashier, using the same named-argument calls the
-- web gateway (apps/web/lib/pos/supabase-gateway.ts) and tablet RPC client make:
-- open cart -> add line -> set customer vehicle -> park / resume -> split-tender checkout.
-- Asserts: invoice posted, tenders settle the balance, stock issued, every journal entry balances.
DO $$
DECLARE
  v_cashier uuid := gen_random_uuid();
  v_main uuid;
  v_uom uuid;
  v_list uuid;
  v_item uuid;
  v_cart uuid;
  v_inv uuid;
  v_total numeric;
  v_qty_before numeric;
  v_qty_after numeric;
  v_unbalanced int;
  v_err text;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';
  IF v_main IS NULL OR v_uom IS NULL OR v_list IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN warehouse, EA uom and RETAIL price list required';
  END IF;

  -- Setup (as the migration owner): a priced, stocked part.
  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('WEBPOS-SMOKE-01', 'Web POS smoke brake pad', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 48.25, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE SET unit_price = 48.25, core_charge = 0;
  PERFORM public.post_stock_receipt(v_main, 'Web POS smoke seed', jsonb_build_array(jsonb_build_object(
    'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 10, 'unit_cost', 20,
    'currency', 'USD', 'valuation_method', 'FIFO')));
  SELECT COALESCE(sum(quantity), 0) INTO v_qty_before
  FROM public.stock_levels WHERE stock_item_id = v_item AND warehouse_id = v_main;

  INSERT INTO auth.users (id, email) VALUES (v_cashier, 'webpos-cashier@smoke.test');
  INSERT INTO public.profiles (id) VALUES (v_cashier) ON CONFLICT (id) DO NOTHING;
  INSERT INTO public.staff_roles (user_id, role) VALUES (v_cashier, 'sales');

  -- Act as the cashier from here on.
  PERFORM set_config('request.jwt.claim.sub', v_cashier::text, true);
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

  v_cart := public.create_pos_cart(
    p_warehouse_id => v_main, p_currency => 'USD', p_fulfillment_mode => 'immediate');
  PERFORM public.add_cart_line(p_cart_id => v_cart, p_stock_item_id => v_item, p_uom_id => v_uom, p_qty => 3);
  PERFORM public.set_pos_cart_vehicle(
    p_cart_id => v_cart, p_model_slug => 'navara', p_model_name => 'Navara',
    p_generation => 'D40', p_chassis_code => 'D40', p_engine_code => 'YD25');

  PERFORM public.park_pos_cart(p_cart_id => v_cart);
  PERFORM public.resume_pos_cart(p_cart_id => v_cart);

  SELECT sum(line_total) INTO v_total FROM public.pos_cart_lines WHERE cart_id = v_cart;
  IF v_total <> 144.75 THEN
    RAISE EXCEPTION 'smoke fail: cart total expected 144.75, got %', v_total;
  END IF;

  -- Tenders must equal the balance exactly; change due is UI-only.
  BEGIN
    PERFORM public.checkout_pos_cart_with_tenders(
      p_cart_id => v_cart,
      p_tenders => jsonb_build_array(jsonb_build_object('tender', 'cash', 'amount', 200)));
    RAISE EXCEPTION 'smoke fail: over-tender was accepted';
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS v_err = MESSAGE_TEXT;
    IF v_err LIKE 'smoke fail%' THEN RAISE; END IF;
  END;

  v_inv := public.checkout_pos_cart_with_tenders(
    p_cart_id => v_cart,
    p_tenders => jsonb_build_array(
      jsonb_build_object('tender', 'cash', 'amount', 100),
      jsonb_build_object('tender', 'ecocash', 'amount', 44.75)),
    p_receipt_email => NULL, p_receipt_whatsapp_e164 => NULL, p_receipt_phone_e164 => NULL);

  -- The internal-posting flag must not outlive the call, and the ledger stays closed to direct calls.
  IF public._pos_posting_active() THEN
    RAISE EXCEPTION 'smoke fail: app.pos_posting still set after checkout';
  END IF;
  BEGIN
    PERFORM public.post_journal_entry(current_date, 'cashier direct post', 'USD', 1,
      '[{"account_code":"1000","debit":1,"credit":0},{"account_code":"4000","debit":0,"credit":1}]'::jsonb);
    RAISE EXCEPTION 'smoke fail: sales cashier posted a journal directly';
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS v_err = MESSAGE_TEXT;
    IF v_err LIKE 'smoke fail%' THEN RAISE; END IF;
    IF v_err NOT LIKE 'finance or admin role required%' THEN
      RAISE EXCEPTION 'smoke fail: direct journal post refused for the wrong reason: %', v_err;
    END IF;
  END;

  PERFORM set_config('request.jwt.claim.sub', '', true);
  PERFORM set_config('request.jwt.claim.role', '', true);

  IF NOT EXISTS (SELECT 1 FROM public.sales_invoices WHERE id = v_inv AND status = 'posted') THEN
    RAISE EXCEPTION 'smoke fail: invoice % not posted', v_inv;
  END IF;

  SELECT COALESCE(sum(quantity), 0) INTO v_qty_after
  FROM public.stock_levels WHERE stock_item_id = v_item AND warehouse_id = v_main;
  IF v_qty_before - v_qty_after <> 3 THEN
    RAISE EXCEPTION 'smoke fail: expected 3 issued, on hand % -> %', v_qty_before, v_qty_after;
  END IF;

  SELECT count(*) INTO v_unbalanced FROM (
    SELECT journal_entry_id FROM public.journal_entry_lines
    GROUP BY journal_entry_id HAVING sum(debit) <> sum(credit)
  ) u;
  IF v_unbalanced > 0 THEN
    RAISE EXCEPTION 'smoke fail: % unbalanced journal entries', v_unbalanced;
  END IF;

  RAISE NOTICE 'web_pos_sale_flow_smoke: PASS invoice=%', v_inv;
END $$;
