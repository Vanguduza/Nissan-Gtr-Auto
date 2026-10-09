-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905133522 enforce_server_authoritative_cart_currency_fx).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0 commerce currency integrity: cart FX is server authoritative and cart lines may not cross currencies.

ALTER TABLE public.pos_carts
  DROP CONSTRAINT IF EXISTS pos_carts_usd_exchange_rate_exact_check;
ALTER TABLE public.pos_carts
  ADD CONSTRAINT pos_carts_usd_exchange_rate_exact_check
  CHECK (currency <> 'USD'::public.currency_code OR exchange_rate_applied = 1);

CREATE OR REPLACE FUNCTION public.create_customer_cart(
  p_warehouse_id uuid,
  p_currency public.currency_code DEFAULT 'USD'::public.currency_code,
  p_fulfillment_mode public.fulfillment_mode DEFAULT 'dispatch'::public.fulfillment_mode,
  p_exchange_rate numeric DEFAULT 1
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_cust uuid := public._current_customer_id();
  v_id uuid;
  v_rate numeric;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  IF p_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'warehouse_id required';
  END IF;
  IF NOT EXISTS (
    SELECT 1
    FROM public.warehouses w
    WHERE w.id = p_warehouse_id
      AND w.is_active = true
      AND COALESCE(w.is_quarantine, false) = false
  ) THEN
    RAISE EXCEPTION 'saleable warehouse not found or inactive';
  END IF;

  IF p_currency = 'USD'::public.currency_code THEN
    v_rate := 1;
  ELSIF p_currency = 'ZIG'::public.currency_code THEN
    v_rate := public.get_zig_exchange_rate(CURRENT_DATE);
    IF v_rate IS NULL OR v_rate <= 0 THEN
      RAISE EXCEPTION 'ZiG sales unavailable: no authoritative exchange rate configured';
    END IF;
  ELSE
    RAISE EXCEPTION 'unsupported cart currency: %', p_currency;
  END IF;

  -- p_exchange_rate is retained only for API compatibility; commercial FX is server authoritative.
  INSERT INTO public.pos_carts(
    document_number, customer_id, warehouse_id, currency,
    exchange_rate_applied, fulfillment_mode, channel, created_by
  )
  VALUES(
    public.next_series_value('CART-'), v_cust, p_warehouse_id, p_currency,
    v_rate, COALESCE(p_fulfillment_mode, 'dispatch'::public.fulfillment_mode),
    'storefront', auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.create_pos_cart(
  p_warehouse_id uuid,
  p_customer_id uuid DEFAULT NULL::uuid,
  p_currency public.currency_code DEFAULT 'USD'::public.currency_code,
  p_fulfillment_mode public.fulfillment_mode DEFAULT 'immediate'::public.fulfillment_mode
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_id uuid;
  v_rate numeric;
BEGIN
  PERFORM public._require_sales_staff();

  IF p_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'warehouse_id required';
  END IF;
  IF NOT EXISTS (
    SELECT 1
    FROM public.warehouses w
    WHERE w.id = p_warehouse_id
      AND w.is_active = true
      AND COALESCE(w.is_quarantine, false) = false
  ) THEN
    RAISE EXCEPTION 'saleable warehouse not found or inactive';
  END IF;

  IF p_customer_id IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM public.customers c WHERE c.id = p_customer_id) THEN
    RAISE EXCEPTION 'customer not found';
  END IF;

  IF p_currency = 'USD'::public.currency_code THEN
    v_rate := 1;
  ELSIF p_currency = 'ZIG'::public.currency_code THEN
    v_rate := public.get_zig_exchange_rate(CURRENT_DATE);
    IF v_rate IS NULL OR v_rate <= 0 THEN
      RAISE EXCEPTION 'ZiG sales unavailable: no authoritative exchange rate configured';
    END IF;
  ELSE
    RAISE EXCEPTION 'unsupported cart currency: %', p_currency;
  END IF;

  INSERT INTO public.pos_carts(
    document_number, customer_id, warehouse_id, currency,
    exchange_rate_applied, fulfillment_mode, created_by
  )
  VALUES(
    public.next_series_value('CART-'), p_customer_id, p_warehouse_id, p_currency,
    v_rate, COALESCE(p_fulfillment_mode, 'immediate'::public.fulfillment_mode), auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.create_pos_cart(
  p_warehouse_id uuid,
  p_customer_id uuid DEFAULT NULL::uuid,
  p_currency public.currency_code DEFAULT 'USD'::public.currency_code
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  RETURN public.create_pos_cart(
    p_warehouse_id,
    p_customer_id,
    p_currency,
    'immediate'::public.fulfillment_mode
  );
END;
$function$;

CREATE OR REPLACE FUNCTION public.add_cart_line(
  p_cart_id uuid,
  p_stock_item_id uuid,
  p_uom_id uuid,
  p_qty numeric
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_price numeric;
  v_core numeric;
  v_currency public.currency_code;
  v_qty_base numeric;
  v_part_id uuid;
  v_core_id uuid;
  v_kit public.item_kits%ROWTYPE;
  v_comp record;
  v_comp_qty numeric;
  v_comp_base numeric;
  v_comp_id uuid;
BEGIN
  PERFORM public._require_cart_mutate(p_cart_id);

  SELECT * INTO v_cart
  FROM public.pos_carts
  WHERE id = p_cart_id
  FOR UPDATE;

  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;
  IF p_qty IS NULL OR p_qty <= 0 THEN
    RAISE EXCEPTION 'qty must be > 0';
  END IF;

  SELECT * INTO v_kit
  FROM public.item_kits
  WHERE stock_item_id = p_stock_item_id
    AND is_active;

  SELECT r.unit_price, r.core_charge, r.currency
  INTO v_price, v_core, v_currency
  FROM public.resolve_item_price(v_cart.customer_id, p_stock_item_id) r;

  IF v_currency IS NULL OR v_currency IS DISTINCT FROM v_cart.currency THEN
    RAISE EXCEPTION 'price currency % does not match cart currency %; cross-currency cart pricing is not allowed',
      COALESCE(v_currency::text, 'NULL'), v_cart.currency;
  END IF;

  v_qty_base := public.convert_to_base_uom(p_stock_item_id, p_uom_id, p_qty);

  IF v_kit.id IS NOT NULL AND v_kit.sell_mode = 'explode' THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.item_kit_components WHERE kit_id = v_kit.id
    ) THEN
      RAISE EXCEPTION 'explode kit % has no BOM components', v_kit.id;
    END IF;

    INSERT INTO public.pos_cart_lines(
      cart_id, stock_item_id, uom_id, qty, qty_base, unit_price, line_total,
      is_core_charge, issues_stock, kit_id, kit_line_kind
    ) VALUES (
      p_cart_id, p_stock_item_id, p_uom_id, p_qty, v_qty_base, v_price,
      round(v_price * p_qty, 2), false, false, v_kit.id, 'header'
    ) RETURNING id INTO v_part_id;

    IF v_core IS NOT NULL AND v_core > 0 THEN
      INSERT INTO public.pos_cart_lines(
        cart_id, stock_item_id, parent_line_id, uom_id, qty, qty_base,
        unit_price, line_total, is_core_charge, issues_stock, kit_id, kit_line_kind
      ) VALUES (
        p_cart_id, p_stock_item_id, v_part_id, p_uom_id, p_qty, v_qty_base,
        v_core, round(v_core * p_qty, 2), true, false, v_kit.id, NULL
      ) RETURNING id INTO v_core_id;
    END IF;

    FOR v_comp IN
      SELECT * FROM public.item_kit_components WHERE kit_id = v_kit.id ORDER BY created_at
    LOOP
      v_comp_qty := v_comp.qty * p_qty;
      v_comp_base := public.convert_to_base_uom(v_comp.component_item_id, v_comp.uom_id, v_comp_qty);
      INSERT INTO public.pos_cart_lines(
        cart_id, stock_item_id, parent_line_id, uom_id, qty, qty_base,
        unit_price, line_total, is_core_charge, issues_stock, kit_id, kit_line_kind
      ) VALUES (
        p_cart_id, v_comp.component_item_id, v_part_id, v_comp.uom_id,
        v_comp_qty, v_comp_base, 0, 0, false, true, v_kit.id, 'component'
      ) RETURNING id INTO v_comp_id;
    END LOOP;

    UPDATE public.pos_carts SET updated_at = now() WHERE id = p_cart_id;
    RETURN v_part_id;
  END IF;

  INSERT INTO public.pos_cart_lines(
    cart_id, stock_item_id, uom_id, qty, qty_base, unit_price, line_total,
    is_core_charge, issues_stock, kit_id, kit_line_kind
  ) VALUES (
    p_cart_id, p_stock_item_id, p_uom_id, p_qty, v_qty_base, v_price,
    round(v_price * p_qty, 2), false, true, v_kit.id,
    CASE WHEN v_kit.id IS NOT NULL THEN 'header'::public.kit_line_kind ELSE NULL END
  ) RETURNING id INTO v_part_id;

  IF v_core IS NOT NULL AND v_core > 0 THEN
    INSERT INTO public.pos_cart_lines(
      cart_id, stock_item_id, parent_line_id, uom_id, qty, qty_base,
      unit_price, line_total, is_core_charge, issues_stock, kit_id, kit_line_kind
    ) VALUES (
      p_cart_id, p_stock_item_id, v_part_id, p_uom_id, p_qty, v_qty_base,
      v_core, round(v_core * p_qty, 2), true, false, v_kit.id, NULL
    ) RETURNING id INTO v_core_id;
  END IF;

  UPDATE public.pos_carts SET updated_at = now() WHERE id = p_cart_id;
  RETURN v_part_id;
END;
$function$;
