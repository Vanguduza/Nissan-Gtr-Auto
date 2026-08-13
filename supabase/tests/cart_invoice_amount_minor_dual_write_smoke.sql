-- H4 slice 3: cart/invoice *_minor dual-write smoke (postgres).
-- Prove add_cart_line + checkout fill unit_price_minor / line_total_minor;
-- discount update re-syncs minors. Requires MAIN/EA/RETAIL + seed stock path.

DO $$
DECLARE
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_list UUID;
  v_cart UUID;
  v_inv UUID;
  v_cart_up BIGINT;
  v_cart_lt BIGINT;
  v_inv_up BIGINT;
  v_inv_lt BIGINT;
  v_core_up BIGINT;
  v_disc_up BIGINT;
  v_disc_lt BIGINT;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';

  IF v_main IS NULL OR v_uom IS NULL OR v_list IS NULL THEN
    RAISE EXCEPTION 'H4 cart dual-write smoke fail: MAIN/EA/RETAIL missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('H4-CART-MINOR', 'H4 cart invoice minor dual-write part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'H4-CART-MINOR';
  END IF;

  -- unit 12.50 → minor 1250; core 2.00 → 200; qty 2 → line_total 25.00 → 2500
  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 12.50, 2.00)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 12.50, core_charge = 2.00;

  PERFORM public.post_stock_receipt(
    v_main,
    'H4 cart minor seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 20,
        'unit_cost', 5,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  v_cart := public.create_pos_cart(
    v_main,
    NULL,
    'USD'::public.currency_code,
    'immediate'::public.fulfillment_mode
  );
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 2);

  SELECT unit_price_minor, line_total_minor
  INTO v_cart_up, v_cart_lt
  FROM public.pos_cart_lines
  WHERE cart_id = v_cart AND is_core_charge = false
  LIMIT 1;

  IF v_cart_up IS DISTINCT FROM 1250 OR v_cart_lt IS DISTINCT FROM 2500 THEN
    RAISE EXCEPTION
      'H4 smoke fail: cart product minors want 1250/2500 got %/%',
      v_cart_up, v_cart_lt;
  END IF;

  SELECT unit_price_minor INTO v_core_up
  FROM public.pos_cart_lines
  WHERE cart_id = v_cart AND is_core_charge = true
  LIMIT 1;

  IF v_core_up IS DISTINCT FROM 200 THEN
    RAISE EXCEPTION 'H4 smoke fail: core charge unit_price_minor want 200 got %', v_core_up;
  END IF;

  -- Discount path updates unit_price + line_total → trigger must re-sync minors
  -- 10% off 12.50 = 11.25 → 1125; line 11.25 * 2 = 22.50 → 2250
  UPDATE public.pos_cart_lines
  SET
    unit_price = round(unit_price * 0.9, 4),
    line_total = round(round(unit_price * 0.9, 4) * qty, 2)
  WHERE cart_id = v_cart AND is_core_charge = false;

  SELECT unit_price_minor, line_total_minor
  INTO v_disc_up, v_disc_lt
  FROM public.pos_cart_lines
  WHERE cart_id = v_cart AND is_core_charge = false
  LIMIT 1;

  IF v_disc_up IS DISTINCT FROM 1125 OR v_disc_lt IS DISTINCT FROM 2250 THEN
    RAISE EXCEPTION
      'H4 smoke fail: discounted cart minors want 1125/2250 got %/%',
      v_disc_up, v_disc_lt;
  END IF;

  -- Restore pre-discount majors so checkout JE math stays simple; minors re-sync
  UPDATE public.pos_cart_lines
  SET
    unit_price = 12.50,
    line_total = 25.00
  WHERE cart_id = v_cart AND is_core_charge = false;

  v_inv := public.checkout_pos_cart(v_cart);

  SELECT unit_price_minor, line_total_minor
  INTO v_inv_up, v_inv_lt
  FROM public.sales_invoice_lines
  WHERE invoice_id = v_inv AND is_core_charge = false
  LIMIT 1;

  IF v_inv_up IS DISTINCT FROM 1250 OR v_inv_lt IS DISTINCT FROM 2500 THEN
    RAISE EXCEPTION
      'H4 smoke fail: invoice line minors want 1250/2500 got %/%',
      v_inv_up, v_inv_lt;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.sales_invoice_lines
    WHERE invoice_id = v_inv
      AND (unit_price_minor IS NULL OR line_total_minor IS NULL)
  ) THEN
    RAISE EXCEPTION 'H4 smoke fail: invoice has null *_minor columns';
  END IF;

  RAISE NOTICE 'H4 cart/invoice amount_minor dual-write smoke OK invoice=%', v_inv;
END;
$$;
