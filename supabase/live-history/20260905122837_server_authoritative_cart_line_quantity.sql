-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905122837 server_authoritative_cart_line_quantity).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0 cart integrity: quantity and removal are server-authoritative.
-- Keeps qty_base, core-charge children, exploded-kit components and totals coherent.
CREATE OR REPLACE FUNCTION public.set_pos_cart_line_qty(
  p_line_id uuid,
  p_qty numeric
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = 'public'
AS $function$
DECLARE
  v_line public.pos_cart_lines%ROWTYPE;
  v_cart public.pos_carts%ROWTYPE;
  v_child public.pos_cart_lines%ROWTYPE;
  v_child_qty numeric;
  v_component_per_kit numeric;
  v_before jsonb;
  v_after jsonb;
BEGIN
  IF p_line_id IS NULL THEN
    RAISE EXCEPTION 'line_id required';
  END IF;
  IF p_qty IS NULL OR p_qty < 0 THEN
    RAISE EXCEPTION 'qty must be >= 0';
  END IF;

  SELECT * INTO v_line
  FROM public.pos_cart_lines
  WHERE id = p_line_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'cart line not found';
  END IF;
  IF v_line.parent_line_id IS NOT NULL OR v_line.is_core_charge OR v_line.kit_line_kind = 'component' THEN
    RAISE EXCEPTION 'dependent cart lines cannot be edited directly';
  END IF;
  IF COALESCE(v_line.qty_fulfilled, 0) <> 0 THEN
    RAISE EXCEPTION 'fulfilled cart line cannot be edited';
  END IF;

  PERFORM public._require_cart_mutate(v_line.cart_id);

  SELECT * INTO v_cart
  FROM public.pos_carts
  WHERE id = v_line.cart_id
  FOR UPDATE;

  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'owned open cart required';
  END IF;
  IF v_cart.checkout_order_id IS NOT NULL THEN
    RAISE EXCEPTION 'cart is locked by checkout; cancel or expire the reservation before editing';
  END IF;

  SELECT jsonb_build_object(
    'line', to_jsonb(v_line),
    'children', COALESCE(jsonb_agg(to_jsonb(c) ORDER BY c.created_at) FILTER (WHERE c.id IS NOT NULL), '[]'::jsonb)
  )
  INTO v_before
  FROM public.pos_cart_lines c
  WHERE c.parent_line_id = v_line.id;

  IF p_qty = 0 THEN
    DELETE FROM public.pos_cart_lines WHERE parent_line_id = v_line.id;
    DELETE FROM public.pos_cart_lines WHERE id = v_line.id;
    UPDATE public.pos_carts SET updated_at = now() WHERE id = v_line.cart_id;

    PERFORM public._log_pos_action(
      'cart_line_removed',
      'pos_cart_lines',
      v_line.id,
      v_before,
      jsonb_build_object('removed', true),
      NULL
    );
    RETURN v_line.id;
  END IF;

  UPDATE public.pos_cart_lines
  SET qty = p_qty,
      qty_base = public.convert_to_base_uom(v_line.stock_item_id, v_line.uom_id, p_qty),
      line_total = round(v_line.unit_price * p_qty, 2)
  WHERE id = v_line.id;

  FOR v_child IN
    SELECT *
    FROM public.pos_cart_lines
    WHERE parent_line_id = v_line.id
    ORDER BY created_at
    FOR UPDATE
  LOOP
    IF v_child.is_core_charge THEN
      v_child_qty := p_qty;
    ELSIF v_child.kit_line_kind = 'component' THEN
      SELECT kc.qty
      INTO v_component_per_kit
      FROM public.item_kit_components kc
      WHERE kc.kit_id = v_line.kit_id
        AND kc.component_item_id = v_child.stock_item_id
        AND kc.uom_id = v_child.uom_id
      ORDER BY kc.created_at
      LIMIT 1;

      IF v_component_per_kit IS NULL OR v_component_per_kit <= 0 THEN
        RAISE EXCEPTION 'kit BOM mismatch for dependent component %', v_child.id;
      END IF;
      v_child_qty := v_component_per_kit * p_qty;
    ELSE
      RAISE EXCEPTION 'unexpected dependent cart line %', v_child.id;
    END IF;

    UPDATE public.pos_cart_lines
    SET qty = v_child_qty,
        qty_base = public.convert_to_base_uom(v_child.stock_item_id, v_child.uom_id, v_child_qty),
        line_total = round(v_child.unit_price * v_child_qty, 2)
    WHERE id = v_child.id;
  END LOOP;

  UPDATE public.pos_carts SET updated_at = now() WHERE id = v_line.cart_id;

  SELECT jsonb_build_object(
    'line', to_jsonb(l),
    'children', COALESCE(jsonb_agg(to_jsonb(c) ORDER BY c.created_at) FILTER (WHERE c.id IS NOT NULL), '[]'::jsonb)
  )
  INTO v_after
  FROM public.pos_cart_lines l
  LEFT JOIN public.pos_cart_lines c ON c.parent_line_id = l.id
  WHERE l.id = v_line.id
  GROUP BY l.id;

  PERFORM public._log_pos_action(
    'cart_line_qty_changed',
    'pos_cart_lines',
    v_line.id,
    v_before,
    v_after,
    NULL
  );

  RETURN v_line.id;
END;
$function$;

REVOKE ALL ON FUNCTION public.set_pos_cart_line_qty(uuid, numeric) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_pos_cart_line_qty(uuid, numeric) TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.set_customer_cart_line_qty(
  p_line_id uuid,
  p_qty numeric
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = 'public'
AS $function$
DECLARE
  v_cart_id uuid;
  v_result uuid;
BEGIN
  SELECT cart_id INTO v_cart_id
  FROM public.pos_cart_lines
  WHERE id = p_line_id;
  IF v_cart_id IS NULL THEN
    RAISE EXCEPTION 'cart line not found';
  END IF;

  PERFORM public._storefront_rpc_enter();
  BEGIN
    PERFORM public._assert_customer_owns_open_cart(v_cart_id);
    v_result := public.set_pos_cart_line_qty(p_line_id, p_qty);
  EXCEPTION
    WHEN OTHERS THEN
      PERFORM public._storefront_rpc_exit();
      RAISE;
  END;
  PERFORM public._storefront_rpc_exit();
  RETURN v_result;
END;
$function$;

REVOKE ALL ON FUNCTION public.set_customer_cart_line_qty(uuid, numeric) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_customer_cart_line_qty(uuid, numeric) TO authenticated, service_role;

-- Direct cart writes bypass pricing, kit/core coherence, audit and checkout locks.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON TABLE public.pos_carts FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON TABLE public.pos_cart_lines FROM anon, authenticated;
