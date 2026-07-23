-- Phase 5 sales smoke (postgres). Needs MAIN warehouse, EA uom, price list item.

DO $$
DECLARE
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_list UUID;
  v_cart UUID;
  v_inv UUID;
  v_outbox INT;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P5-SMOKE-001', 'Phase5 smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P5-SMOKE-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 50, 10)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 50, core_charge = 10;

  -- Seed stock via receipt
  PERFORM public.post_stock_receipt(
    v_main,
    'P5 seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 20,
        'unit_cost', 20,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  v_cart := public.create_pos_cart(v_main, NULL, 'USD');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 2);

  IF NOT EXISTS (
    SELECT 1 FROM public.pos_cart_lines
    WHERE cart_id = v_cart AND is_core_charge = true AND unit_price = 10
  ) THEN
    RAISE EXCEPTION 'smoke fail: core charge line missing';
  END IF;

  v_inv := public.checkout_pos_cart(v_cart);

  IF NOT EXISTS (
    SELECT 1 FROM public.sales_invoices WHERE id = v_inv AND status = 'posted'
  ) THEN
    RAISE EXCEPTION 'smoke fail: invoice not posted';
  END IF;

  -- Walk-in may have no outbox contacts
  SELECT count(*)::int INTO v_outbox
  FROM public.customer_receipt_outbox WHERE document_id = v_inv;

  RAISE NOTICE 'phase5_sales_smoke: PASS invoice=% outbox_rows=%', v_inv, v_outbox;
END;
$$;
