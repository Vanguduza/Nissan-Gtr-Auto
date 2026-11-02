-- Wishlist back-in-stock preference + stock-return notify (fail-closed SMS enqueue)
-- + thin wishlist_move_to_cart DEFINER for atomic add-line + optional wishlist remove.
-- NO ZIMRA / payroll tax / secrets. Prefer compose of create_customer_cart /
-- add_customer_cart_line when UI already holds an open cart; this RPC wraps that path.

-- ---------------------------------------------------------------------------
-- Preference column
-- ---------------------------------------------------------------------------
ALTER TABLE public.customer_wishlist_items
  ADD COLUMN IF NOT EXISTS notify_when_in_stock BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN public.customer_wishlist_items.notify_when_in_stock IS
  'When true, enqueue fail-closed SMS/domain event when SKU returns from zero stock.';

GRANT UPDATE (notify_when_in_stock) ON TABLE public.customer_wishlist_items TO authenticated;

-- ---------------------------------------------------------------------------
-- Event catalog (enqueue only; drain stays fail-closed without provider keys)
-- ---------------------------------------------------------------------------
INSERT INTO public.sms_event_catalog (code, description, category, priority) VALUES
  (
    'wishlist_back_in_stock',
    'Wishlist SKU returned to available stock',
    'sales',
    'normal'
  ),
  (
    'review_approved',
    'Customer product review approved',
    'sales',
    'low'
  )
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Customer SMS outbox helper (domain_events + sms_outbox; no provider send)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._enqueue_customer_sms(
  p_event_code TEXT,
  p_dedupe_key TEXT,
  p_profile_id UUID,
  p_phone_e164 TEXT,
  p_payload JSONB DEFAULT '{}'::jsonb,
  p_message_body TEXT DEFAULT NULL,
  p_actor_user_id UUID DEFAULT auth.uid()
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_event_id UUID;
  v_body TEXT;
  v_desc TEXT;
  v_phone TEXT := NULLIF(trim(COALESCE(p_phone_e164, '')), '');
BEGIN
  IF p_profile_id IS NULL THEN
    RETURN NULL;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.sms_event_catalog c
    WHERE c.code = p_event_code AND c.is_active
  ) THEN
    RAISE EXCEPTION 'Unknown or inactive event_code: %', p_event_code;
  END IF;

  INSERT INTO public.domain_events (event_code, dedupe_key, payload, actor_user_id)
  VALUES (
    p_event_code,
    p_dedupe_key,
    COALESCE(p_payload, '{}'::jsonb),
    p_actor_user_id
  )
  ON CONFLICT (event_code, dedupe_key) DO NOTHING
  RETURNING id INTO v_event_id;

  IF v_event_id IS NULL THEN
    SELECT id INTO v_event_id
    FROM public.domain_events
    WHERE event_code = p_event_code AND dedupe_key = p_dedupe_key;
  END IF;

  -- No phone → still record domain event; skip outbox (fail-closed, no send).
  IF v_phone IS NULL THEN
    RETURN v_event_id;
  END IF;

  SELECT description INTO v_desc FROM public.sms_event_catalog WHERE code = p_event_code;
  v_body := COALESCE(
    p_message_body,
    format('GTR Auto: %s (%s)', v_desc, p_event_code)
  );

  INSERT INTO public.sms_outbox (
    domain_event_id, event_code, recipient_user_id, phone_e164, body
  )
  VALUES (v_event_id, p_event_code, p_profile_id, v_phone, v_body)
  ON CONFLICT (domain_event_id, recipient_user_id) DO NOTHING;

  RETURN v_event_id;
END;
$$;

REVOKE ALL ON FUNCTION public._enqueue_customer_sms(TEXT, TEXT, UUID, TEXT, JSONB, TEXT, UUID)
  FROM PUBLIC;

-- ---------------------------------------------------------------------------
-- RPC: set notify flag
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.set_wishlist_notify_when_in_stock(
  p_notify BOOLEAN,
  p_stock_item_id UUID DEFAULT NULL,
  p_oem_part_number TEXT DEFAULT NULL,
  p_wishlist_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_item UUID;
  v_id UUID;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  IF p_notify IS NULL THEN
    RAISE EXCEPTION 'notify flag required';
  END IF;

  IF p_wishlist_id IS NOT NULL THEN
    UPDATE public.customer_wishlist_items
    SET notify_when_in_stock = p_notify
    WHERE id = p_wishlist_id AND customer_id = v_cust
    RETURNING id INTO v_id;
    IF v_id IS NULL THEN
      RAISE EXCEPTION 'wishlist item not found';
    END IF;
    RETURN v_id;
  END IF;

  v_item := public._resolve_customer_stock_item(p_stock_item_id, p_oem_part_number);

  UPDATE public.customer_wishlist_items
  SET notify_when_in_stock = p_notify
  WHERE customer_id = v_cust AND stock_item_id = v_item
  RETURNING id INTO v_id;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'wishlist item not found';
  END IF;
  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.set_wishlist_notify_when_in_stock(BOOLEAN, UUID, TEXT, UUID)
  FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_wishlist_notify_when_in_stock(BOOLEAN, UUID, TEXT, UUID)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Notify watchers when aggregate on-hand crosses 0 → >0
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._notify_wishlist_back_in_stock(p_stock_item_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row RECORD;
  v_phone TEXT;
  v_oem TEXT;
  v_day TEXT := to_char(timezone('utc', now()), 'YYYY-MM-DD');
BEGIN
  SELECT oem_part_number INTO v_oem
  FROM public.stock_items
  WHERE id = p_stock_item_id;

  FOR v_row IN
    SELECT
      w.id AS wishlist_id,
      w.customer_id,
      c.profile_id,
      c.phone_e164 AS cust_phone,
      c.whatsapp_e164 AS cust_wa,
      p.phone_e164 AS profile_phone
    FROM public.customer_wishlist_items w
    JOIN public.customers c ON c.id = w.customer_id
    LEFT JOIN public.profiles p ON p.id = c.profile_id
    WHERE w.stock_item_id = p_stock_item_id
      AND w.notify_when_in_stock = true
      AND c.profile_id IS NOT NULL
  LOOP
    v_phone := COALESCE(
      NULLIF(trim(COALESCE(v_row.cust_phone, '')), ''),
      NULLIF(trim(COALESCE(v_row.profile_phone, '')), ''),
      NULLIF(trim(COALESCE(v_row.cust_wa, '')), '')
    );

    PERFORM public._enqueue_customer_sms(
      'wishlist_back_in_stock',
      format('wishlist:%s:%s:%s', v_row.wishlist_id::text, p_stock_item_id::text, v_day),
      v_row.profile_id,
      v_phone,
      jsonb_build_object(
        'wishlist_id', v_row.wishlist_id,
        'customer_id', v_row.customer_id,
        'stock_item_id', p_stock_item_id,
        'oem_part_number', v_oem
      ),
      format(
        'GTR Auto: %s is back in stock.',
        COALESCE(v_oem, 'A wishlist part')
      )
    );
  END LOOP;

  -- Optional staff fan-out via existing emit (manager prefs); ignore if no staff auth.
  BEGIN
    PERFORM public.emit_domain_event(
      'wishlist_back_in_stock',
      format('stock:back:%s:%s', p_stock_item_id::text, v_day),
      jsonb_build_object('stock_item_id', p_stock_item_id, 'oem_part_number', v_oem)
    );
  EXCEPTION
    WHEN OTHERS THEN
      NULL; -- fail-closed: customer enqueue already attempted
  END;
END;
$$;

REVOKE ALL ON FUNCTION public._notify_wishlist_back_in_stock(UUID) FROM PUBLIC;

-- Patch stock adjuster: detect 0 → >0 across warehouses
CREATE OR REPLACE FUNCTION public._adjust_stock_level(
  p_item UUID,
  p_warehouse UUID,
  p_delta NUMERIC,
  p_valuation public.valuation_method,
  p_unit_cost NUMERIC,
  p_currency public.currency_code
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_qty NUMERIC;
  v_before NUMERIC;
  v_after NUMERIC;
BEGIN
  SELECT COALESCE(SUM(quantity), 0) INTO v_before
  FROM public.stock_levels
  WHERE stock_item_id = p_item;

  INSERT INTO public.stock_levels (
    stock_item_id, warehouse_id, quantity, valuation_method, unit_cost, currency
  )
  VALUES (
    p_item, p_warehouse, GREATEST(p_delta, 0), p_valuation, p_unit_cost, p_currency
  )
  ON CONFLICT (stock_item_id, warehouse_id) DO UPDATE
  SET
    quantity = public.stock_levels.quantity + p_delta,
    updated_at = now(),
    unit_cost = CASE
      WHEN p_delta > 0 AND public.stock_levels.valuation_method = 'AVG' THEN
        CASE
          WHEN public.stock_levels.quantity + p_delta = 0 THEN p_unit_cost
          ELSE round(
            (
              COALESCE(public.stock_levels.unit_cost, 0) * public.stock_levels.quantity
              + COALESCE(p_unit_cost, 0) * p_delta
            ) / (public.stock_levels.quantity + p_delta),
            4
          )
        END
      WHEN p_delta > 0 THEN COALESCE(p_unit_cost, public.stock_levels.unit_cost)
      ELSE public.stock_levels.unit_cost
    END
  RETURNING quantity INTO v_qty;

  IF v_qty < 0 THEN
    RAISE EXCEPTION 'insufficient stock for item % in warehouse %', p_item, p_warehouse;
  END IF;

  IF p_delta > 0 THEN
    SELECT COALESCE(SUM(quantity), 0) INTO v_after
    FROM public.stock_levels
    WHERE stock_item_id = p_item;

    IF v_before <= 0 AND v_after > 0 THEN
      PERFORM public._notify_wishlist_back_in_stock(p_item);
    END IF;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- wishlist_move_to_cart — compose add_customer_cart_line + optional remove
-- Clients may instead call create_customer_cart / add_customer_cart_line
-- directly; this RPC is for atomic remove-after-add.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.wishlist_move_to_cart(
  p_cart_id UUID,
  p_qty NUMERIC DEFAULT 1,
  p_remove_from_wishlist BOOLEAN DEFAULT true,
  p_stock_item_id UUID DEFAULT NULL,
  p_oem_part_number TEXT DEFAULT NULL,
  p_wishlist_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_item UUID;
  v_wish UUID;
  v_uom UUID;
  v_line UUID;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  IF p_cart_id IS NULL THEN
    RAISE EXCEPTION 'cart_id required (create_customer_cart first)';
  END IF;
  IF p_qty IS NULL OR p_qty <= 0 THEN
    RAISE EXCEPTION 'qty must be > 0';
  END IF;

  IF p_wishlist_id IS NOT NULL THEN
    SELECT id, stock_item_id INTO v_wish, v_item
    FROM public.customer_wishlist_items
    WHERE id = p_wishlist_id AND customer_id = v_cust;
    IF v_wish IS NULL THEN
      RAISE EXCEPTION 'wishlist item not found';
    END IF;
  ELSE
    v_item := public._resolve_customer_stock_item(p_stock_item_id, p_oem_part_number);
    SELECT id INTO v_wish
    FROM public.customer_wishlist_items
    WHERE customer_id = v_cust AND stock_item_id = v_item;
  END IF;

  SELECT base_uom_id INTO v_uom
  FROM public.stock_items
  WHERE id = v_item;
  IF v_uom IS NULL THEN
    RAISE EXCEPTION 'stock item missing base_uom_id';
  END IF;

  v_line := public.add_customer_cart_line(p_cart_id, v_item, v_uom, p_qty);

  IF p_remove_from_wishlist AND v_wish IS NOT NULL THEN
    DELETE FROM public.customer_wishlist_items
    WHERE id = v_wish AND customer_id = v_cust;
  END IF;

  RETURN v_line;
END;
$$;

REVOKE ALL ON FUNCTION public.wishlist_move_to_cart(UUID, NUMERIC, BOOLEAN, UUID, TEXT, UUID)
  FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.wishlist_move_to_cart(UUID, NUMERIC, BOOLEAN, UUID, TEXT, UUID)
  TO authenticated, service_role;

COMMENT ON FUNCTION public.wishlist_move_to_cart(UUID, NUMERIC, BOOLEAN, UUID, TEXT, UUID) IS
  'Thin atomic helper: add_customer_cart_line then optional wishlist delete. Prefer compose when no remove needed.';
