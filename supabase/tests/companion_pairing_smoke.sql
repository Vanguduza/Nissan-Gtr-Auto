-- Companion pairing as the till and phone make it: create code, claim with the same staff account, phone QR scan lands in the till cart.
-- Run: psql -v ON_ERROR_STOP=1 -f supabase/tests/companion_pairing_smoke.sql (after db reset + seed).
DO $$
DECLARE
  v_rep uuid := gen_random_uuid(); v_main uuid; v_uom uuid; v_list uuid; v_item uuid; v_cart uuid; r record; v_sid uuid; v_n int; v_batch text;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';
  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id) VALUES ('COMPANION-SMOKE-01', 'Companion smoke pad', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description RETURNING id INTO v_item;
  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge) VALUES (v_list, v_item, 30, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE SET unit_price = 30;
  PERFORM public.post_stock_receipt(v_main, 'Companion smoke', jsonb_build_array(jsonb_build_object(
    'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 5, 'unit_cost', 10, 'currency', 'USD', 'valuation_method', 'FIFO')));
  INSERT INTO auth.users (id, email) VALUES (v_rep, 'rep@smoke.test');
  INSERT INTO public.profiles (id) VALUES (v_rep) ON CONFLICT (id) DO NOTHING;
  INSERT INTO public.staff_roles (user_id, role) VALUES (v_rep, 'sales');
  PERFORM set_config('request.jwt.claim.sub', v_rep::text, true);
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
  SET LOCAL ROLE authenticated;
  v_cart := public.create_pos_cart(p_warehouse_id => v_main, p_currency => 'USD', p_fulfillment_mode => 'immediate');
  SELECT * INTO r FROM public.create_pos_scan_session(p_cart_id => v_cart);
  v_sid := public.claim_pos_scan_session(p_pairing_code => r.pairing_code);
  IF (SELECT cart_id FROM public.pos_scan_sessions WHERE id = v_sid) <> v_cart THEN RAISE EXCEPTION 'session cart mismatch'; END IF;
  PERFORM public.add_cart_line_from_qr(p_cart_id => v_cart, p_qr_payload => 'gtr://part/COMPANION-SMOKE-01?batch=B1&valuation=FIFO', p_qty => 1);
  SELECT count(*) INTO v_n FROM public.pos_cart_lines WHERE cart_id = v_cart;
  RAISE NOTICE 'phone scan added % line(s) to the till cart; session %', v_n, (SELECT status FROM public.pos_scan_sessions WHERE id = v_sid);
  IF v_n <> 1 THEN RAISE EXCEPTION 'scan did not land'; END IF;
END $$;
