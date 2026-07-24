-- Phase 16 slice 2: Kits / BOM sell
-- sell_mode stocked → issue kit SKU only; explode → issue components only (header revenue)
-- Core-charge parent-child cart rules unchanged. No ZIMRA / tax / consignment / loyalty.

-- ---------------------------------------------------------------------------
-- Enums + tables
-- ---------------------------------------------------------------------------
CREATE TYPE public.kit_sell_mode AS ENUM ('stocked', 'explode');
CREATE TYPE public.kit_line_kind AS ENUM ('header', 'component');

CREATE TABLE public.item_kits (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stock_item_id UUID NOT NULL UNIQUE REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  sell_mode public.kit_sell_mode NOT NULL DEFAULT 'explode',
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.item_kit_components (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  kit_id UUID NOT NULL REFERENCES public.item_kits (id) ON DELETE CASCADE,
  component_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (kit_id, component_item_id)
);

CREATE INDEX item_kit_components_kit_idx ON public.item_kit_components (kit_id);
CREATE INDEX item_kit_components_item_idx ON public.item_kit_components (component_item_id);

COMMENT ON TABLE public.item_kits IS
  'Sellable kit SKU. stocked = deplete kit inventory; explode = deplete BOM components only.';
COMMENT ON TABLE public.item_kit_components IS
  'BOM lines for a kit. Used at sale when sell_mode=explode; informational for stocked.';

-- Cart / invoice line stock-issue flags (default true preserves non-kit behaviour)
ALTER TABLE public.pos_cart_lines
  ADD COLUMN IF NOT EXISTS issues_stock BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS kit_id UUID REFERENCES public.item_kits (id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS kit_line_kind public.kit_line_kind;

ALTER TABLE public.sales_invoice_lines
  ADD COLUMN IF NOT EXISTS issues_stock BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS kit_id UUID REFERENCES public.item_kits (id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS kit_line_kind public.kit_line_kind;

CREATE INDEX IF NOT EXISTS pos_cart_lines_kit_idx ON public.pos_cart_lines (kit_id)
  WHERE kit_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS sales_invoice_lines_kit_idx ON public.sales_invoice_lines (kit_id)
  WHERE kit_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Guards: no self-component; no nested kits as components (keeps explode simple)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.guard_item_kit_component()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
  v_kit_item UUID;
BEGIN
  SELECT stock_item_id INTO v_kit_item FROM public.item_kits WHERE id = NEW.kit_id;
  IF v_kit_item IS NULL THEN
    RAISE EXCEPTION 'kit not found: %', NEW.kit_id;
  END IF;
  IF NEW.component_item_id = v_kit_item THEN
    RAISE EXCEPTION 'kit cannot include itself as a component';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.item_kits k
    WHERE k.stock_item_id = NEW.component_item_id AND k.is_active
  ) THEN
    RAISE EXCEPTION 'nested kits as components are not supported';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS item_kit_components_guard ON public.item_kit_components;
CREATE TRIGGER item_kit_components_guard
  BEFORE INSERT OR UPDATE OF kit_id, component_item_id ON public.item_kit_components
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_item_kit_component();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.item_kits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.item_kit_components ENABLE ROW LEVEL SECURITY;

CREATE POLICY item_kits_select_authenticated
  ON public.item_kits FOR SELECT TO authenticated
  USING (true);

CREATE POLICY item_kits_staff_write
  ON public.item_kits FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY item_kit_components_select_authenticated
  ON public.item_kit_components FOR SELECT TO authenticated
  USING (true);

CREATE POLICY item_kit_components_staff_write
  ON public.item_kit_components FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

GRANT SELECT ON TABLE public.item_kits TO authenticated, service_role;
GRANT SELECT ON TABLE public.item_kit_components TO authenticated, service_role;
GRANT INSERT, UPDATE, DELETE ON TABLE public.item_kits TO authenticated, service_role;
GRANT INSERT, UPDATE, DELETE ON TABLE public.item_kit_components TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Kit master-data RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_item_kit(
  p_stock_item_id UUID,
  p_sell_mode public.kit_sell_mode DEFAULT 'explode'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._require_warehouse_staff();

  IF NOT EXISTS (SELECT 1 FROM public.stock_items WHERE id = p_stock_item_id) THEN
    RAISE EXCEPTION 'stock item not found: %', p_stock_item_id;
  END IF;

  INSERT INTO public.item_kits (stock_item_id, sell_mode, created_by)
  VALUES (p_stock_item_id, COALESCE(p_sell_mode, 'explode'), auth.uid())
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.update_item_kit(
  p_kit_id UUID,
  p_sell_mode public.kit_sell_mode DEFAULT NULL,
  p_is_active BOOLEAN DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._require_warehouse_staff();

  UPDATE public.item_kits
  SET
    sell_mode = COALESCE(p_sell_mode, sell_mode),
    is_active = COALESCE(p_is_active, is_active),
    updated_at = now()
  WHERE id = p_kit_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'kit not found: %', p_kit_id;
  END IF;

  RETURN p_kit_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.add_kit_component(
  p_kit_id UUID,
  p_component_item_id UUID,
  p_qty NUMERIC,
  p_uom_id UUID
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._require_warehouse_staff();

  IF p_qty IS NULL OR p_qty <= 0 THEN
    RAISE EXCEPTION 'component qty must be > 0';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.item_kits WHERE id = p_kit_id) THEN
    RAISE EXCEPTION 'kit not found: %', p_kit_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.stock_items WHERE id = p_component_item_id) THEN
    RAISE EXCEPTION 'component stock item not found: %', p_component_item_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.uoms WHERE id = p_uom_id) THEN
    RAISE EXCEPTION 'uom not found: %', p_uom_id;
  END IF;

  INSERT INTO public.item_kit_components (kit_id, component_item_id, qty, uom_id)
  VALUES (p_kit_id, p_component_item_id, p_qty, p_uom_id)
  ON CONFLICT (kit_id, component_item_id) DO UPDATE
  SET qty = EXCLUDED.qty, uom_id = EXCLUDED.uom_id
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.remove_kit_component(
  p_kit_id UUID,
  p_component_item_id UUID
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._require_warehouse_staff();

  DELETE FROM public.item_kit_components
  WHERE kit_id = p_kit_id AND component_item_id = p_component_item_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'kit component not found';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- add_cart_line: detect kit → stocked (SKU only) or explode (header + components)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.add_cart_line(
  p_cart_id UUID,
  p_stock_item_id UUID,
  p_uom_id UUID,
  p_qty NUMERIC
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_price NUMERIC;
  v_core NUMERIC;
  v_currency public.currency_code;
  v_qty_base NUMERIC;
  v_part_id UUID;
  v_core_id UUID;
  v_kit public.item_kits%ROWTYPE;
  v_comp RECORD;
  v_comp_qty NUMERIC;
  v_comp_base NUMERIC;
  v_comp_id UUID;
BEGIN
  PERFORM public._require_sales_staff();
  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;

  IF p_qty IS NULL OR p_qty <= 0 THEN
    RAISE EXCEPTION 'qty must be > 0';
  END IF;

  SELECT * INTO v_kit
  FROM public.item_kits
  WHERE stock_item_id = p_stock_item_id AND is_active;

  SELECT r.unit_price, r.core_charge, r.currency
  INTO v_price, v_core, v_currency
  FROM public.resolve_item_price(v_cart.customer_id, p_stock_item_id) r;

  v_qty_base := public.convert_to_base_uom(p_stock_item_id, p_uom_id, p_qty);

  -- ---- Explode kit: header revenue (no stock) + zero-price component stock lines ----
  IF v_kit.id IS NOT NULL AND v_kit.sell_mode = 'explode' THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.item_kit_components WHERE kit_id = v_kit.id
    ) THEN
      RAISE EXCEPTION 'explode kit % has no BOM components', v_kit.id;
    END IF;

    INSERT INTO public.pos_cart_lines (
      cart_id, stock_item_id, uom_id, qty, qty_base, unit_price, line_total,
      is_core_charge, issues_stock, kit_id, kit_line_kind
    )
    VALUES (
      p_cart_id, p_stock_item_id, p_uom_id, p_qty, v_qty_base, v_price,
      round(v_price * p_qty, 2),
      false, false, v_kit.id, 'header'
    )
    RETURNING id INTO v_part_id;

    -- Core charge still attaches to kit header (unchanged parent-child rule)
    IF v_core IS NOT NULL AND v_core > 0 THEN
      INSERT INTO public.pos_cart_lines (
        cart_id, stock_item_id, parent_line_id, uom_id, qty, qty_base,
        unit_price, line_total, is_core_charge, issues_stock, kit_id, kit_line_kind
      )
      VALUES (
        p_cart_id, p_stock_item_id, v_part_id, p_uom_id, p_qty, v_qty_base,
        v_core, round(v_core * p_qty, 2), true, false, v_kit.id, NULL
      )
      RETURNING id INTO v_core_id;
    END IF;

    FOR v_comp IN
      SELECT * FROM public.item_kit_components WHERE kit_id = v_kit.id
      ORDER BY created_at
    LOOP
      v_comp_qty := v_comp.qty * p_qty;
      v_comp_base := public.convert_to_base_uom(
        v_comp.component_item_id, v_comp.uom_id, v_comp_qty
      );

      INSERT INTO public.pos_cart_lines (
        cart_id, stock_item_id, parent_line_id, uom_id, qty, qty_base,
        unit_price, line_total, is_core_charge, issues_stock, kit_id, kit_line_kind
      )
      VALUES (
        p_cart_id, v_comp.component_item_id, v_part_id, v_comp.uom_id,
        v_comp_qty, v_comp_base,
        0, 0, false, true, v_kit.id, 'component'
      )
      RETURNING id INTO v_comp_id;
    END LOOP;

    UPDATE public.pos_carts SET updated_at = now() WHERE id = p_cart_id;
    RETURN v_part_id;
  END IF;

  -- ---- Stocked kit or plain SKU: issue this line's stock only ----
  INSERT INTO public.pos_cart_lines (
    cart_id, stock_item_id, uom_id, qty, qty_base, unit_price, line_total,
    is_core_charge, issues_stock, kit_id, kit_line_kind
  )
  VALUES (
    p_cart_id, p_stock_item_id, p_uom_id, p_qty, v_qty_base, v_price,
    round(v_price * p_qty, 2),
    false, true,
    v_kit.id,
    CASE WHEN v_kit.id IS NOT NULL THEN 'header'::public.kit_line_kind ELSE NULL END
  )
  RETURNING id INTO v_part_id;

  IF v_core IS NOT NULL AND v_core > 0 THEN
    INSERT INTO public.pos_cart_lines (
      cart_id, stock_item_id, parent_line_id, uom_id, qty, qty_base,
      unit_price, line_total, is_core_charge, issues_stock, kit_id, kit_line_kind
    )
    VALUES (
      p_cart_id, p_stock_item_id, v_part_id, p_uom_id, p_qty, v_qty_base,
      v_core, round(v_core * p_qty, 2), true, false, v_kit.id, NULL
    )
    RETURNING id INTO v_core_id;
  END IF;

  UPDATE public.pos_carts SET updated_at = now() WHERE id = p_cart_id;
  RETURN v_part_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- checkout_pos_cart: honour issues_stock; map parent_line_id; no double COGS
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.checkout_pos_cart(p_cart_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_cust public.customers%ROWTYPE;
  v_inv UUID;
  v_line RECORD;
  v_subtotal NUMERIC := 0;
  v_total NUMERIC := 0;
  v_journal UUID;
  v_lines JSONB := '[]'::jsonb;
  v_rev NUMERIC := 0;
  v_core NUMERIC := 0;
  v_cogs NUMERIC := 0;
  v_unit_cost NUMERIC;
  v_large NUMERIC := 1000;
  v_fulfill public.fulfillment_mode;
  v_qty_fulfilled NUMERIC;
  v_inv_line_id UUID;
  v_parent_inv UUID;
  v_cart_to_inv JSONB := '{}'::jsonb;
  v_issues BOOLEAN;
BEGIN
  PERFORM public._require_sales_staff();
  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;

  v_fulfill := COALESCE(v_cart.fulfillment_mode, 'immediate');

  IF v_cart.customer_id IS NOT NULL THEN
    SELECT * INTO v_cust FROM public.customers WHERE id = v_cart.customer_id;
    IF v_cust.credit_hold THEN
      INSERT INTO public.sales_invoices (
        doc_type, status, customer_id, warehouse_id, currency,
        exchange_rate_applied, cart_id, fulfillment_mode,
        customer_phone_e164, customer_email, customer_whatsapp_e164
      )
      VALUES (
        'invoice', 'on_hold', v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
        v_cart.exchange_rate_applied, p_cart_id, v_fulfill,
        v_cust.phone_e164, v_cust.email, v_cust.whatsapp_e164
      )
      RETURNING id INTO v_inv;

      PERFORM public.emit_domain_event(
        'order_on_hold',
        'invoice:hold:' || v_inv::text,
        jsonb_build_object('invoice_id', v_inv, 'reason', 'credit_hold')
      );
      RETURN v_inv;
    END IF;
  END IF;

  SELECT COALESCE(SUM(line_total), 0) INTO v_subtotal
  FROM public.pos_cart_lines WHERE cart_id = p_cart_id;
  v_total := v_subtotal;

  IF v_cart.customer_id IS NOT NULL
     AND v_cust.credit_limit > 0
     AND (v_cust.open_balance + v_total) > v_cust.credit_limit THEN
    INSERT INTO public.sales_invoices (
      doc_type, status, customer_id, warehouse_id, currency,
      exchange_rate_applied, cart_id, subtotal, total, fulfillment_mode,
      customer_phone_e164, customer_email, customer_whatsapp_e164
    )
    VALUES (
      'invoice', 'on_hold', v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
      v_cart.exchange_rate_applied, p_cart_id, v_subtotal, v_total, v_fulfill,
      v_cust.phone_e164, v_cust.email, v_cust.whatsapp_e164
    )
    RETURNING id INTO v_inv;

    PERFORM public.emit_domain_event(
      'order_on_hold',
      'invoice:limit:' || v_inv::text,
      jsonb_build_object('invoice_id', v_inv, 'reason', 'credit_limit')
    );
    RETURN v_inv;
  END IF;

  INSERT INTO public.sales_invoices (
    doc_type, status, document_number, customer_id, warehouse_id, currency,
    exchange_rate_applied, subtotal, total, cart_id, fulfillment_mode,
    customer_phone_e164, customer_email, customer_whatsapp_e164,
    posted_by, posted_at
  )
  VALUES (
    'invoice', 'draft', public.next_series_value('SINV-'),
    v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
    v_cart.exchange_rate_applied, v_subtotal, v_total, p_cart_id, v_fulfill,
    v_cust.phone_e164, v_cust.email, v_cust.whatsapp_e164,
    auth.uid(), now()
  )
  RETURNING id INTO v_inv;

  PERFORM public.emit_domain_event(
    'order_received',
    'invoice:received:' || v_inv::text,
    jsonb_build_object(
      'invoice_id', v_inv,
      'fulfillment_mode', v_fulfill::text
    )
  );

  -- Parent lines first (created_at), then children / core
  FOR v_line IN
    SELECT * FROM public.pos_cart_lines
    WHERE cart_id = p_cart_id
    ORDER BY created_at
  LOOP
    v_issues := COALESCE(v_line.issues_stock, true);

    IF v_line.is_core_charge THEN
      v_qty_fulfilled := 0;
    ELSIF NOT v_issues THEN
      -- Explode kit header (revenue-only): nothing to pick / issue
      v_qty_fulfilled := v_line.qty_base;
    ELSIF v_fulfill = 'immediate' THEN
      v_qty_fulfilled := v_line.qty_base;
    ELSE
      v_qty_fulfilled := 0;
    END IF;

    v_parent_inv := NULL;
    IF v_line.parent_line_id IS NOT NULL THEN
      v_parent_inv := (v_cart_to_inv ->> v_line.parent_line_id::text)::uuid;
    END IF;

    INSERT INTO public.sales_invoice_lines (
      invoice_id, stock_item_id, parent_line_id, is_core_charge, uom_id,
      qty, qty_base, unit_price, line_total, qty_fulfilled,
      issues_stock, kit_id, kit_line_kind
    )
    VALUES (
      v_inv, v_line.stock_item_id, v_parent_inv, v_line.is_core_charge, v_line.uom_id,
      v_line.qty, v_line.qty_base, v_line.unit_price, v_line.line_total, v_qty_fulfilled,
      v_issues, v_line.kit_id, v_line.kit_line_kind
    )
    RETURNING id INTO v_inv_line_id;

    v_cart_to_inv := v_cart_to_inv || jsonb_build_object(v_line.id::text, v_inv_line_id);

    IF v_line.is_core_charge THEN
      v_core := v_core + v_line.line_total;
    ELSE
      v_rev := v_rev + v_line.line_total;

      -- Stock + COGS only when issues_stock and immediate (never kit+components together)
      IF v_issues AND v_fulfill = 'immediate' THEN
        SELECT unit_cost INTO v_unit_cost
        FROM public.stock_levels
        WHERE stock_item_id = v_line.stock_item_id AND warehouse_id = v_cart.warehouse_id;

        PERFORM public._consume_fifo_batches(
          v_line.stock_item_id, v_cart.warehouse_id, v_line.qty_base
        );
        PERFORM public._adjust_stock_level(
          v_line.stock_item_id, v_cart.warehouse_id, -v_line.qty_base,
          'FIFO', COALESCE(v_unit_cost, 0), v_cart.currency
        );
        v_cogs := v_cogs + COALESCE(v_unit_cost, 0) * v_line.qty_base;
      END IF;
    END IF;
  END LOOP;

  v_lines := jsonb_build_array(
    jsonb_build_object(
      'account_code', CASE WHEN v_cart.customer_id IS NULL THEN '1100' ELSE '1200' END,
      'debit', v_total, 'credit', 0, 'currency', v_cart.currency
    ),
    jsonb_build_object(
      'account_code', '4100',
      'debit', 0, 'credit', v_rev, 'currency', v_cart.currency
    )
  );
  IF v_core > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', '4200',
        'debit', 0, 'credit', v_core, 'currency', v_cart.currency
      )
    );
  END IF;
  IF v_cogs > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', '5100',
        'debit', round(v_cogs, 2), 'credit', 0, 'currency', v_cart.currency
      ),
      jsonb_build_object(
        'account_code', '1300',
        'debit', 0, 'credit', round(v_cogs, 2), 'currency', v_cart.currency
      )
    );
  END IF;

  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    format('Sale %s', (SELECT document_number FROM public.sales_invoices WHERE id = v_inv)),
    v_cart.currency,
    v_cart.exchange_rate_applied,
    v_lines
  );

  UPDATE public.sales_invoices
  SET status = 'posted', journal_entry_id = v_journal, posted_at = now()
  WHERE id = v_inv;

  UPDATE public.pos_carts SET status = 'checked_out', updated_at = now() WHERE id = p_cart_id;

  IF v_cart.customer_id IS NOT NULL THEN
    UPDATE public.customers
    SET open_balance = open_balance + v_total, updated_at = now()
    WHERE id = v_cart.customer_id;
  END IF;

  PERFORM public.enqueue_customer_receipts(v_inv);

  IF v_total >= v_large THEN
    PERFORM public.emit_domain_event(
      'large_order',
      'invoice:large:' || v_inv::text,
      jsonb_build_object('invoice_id', v_inv, 'total', v_total)
    );
  END IF;

  RETURN v_inv;
END;
$$;

-- ---------------------------------------------------------------------------
-- create_pick_list: skip non-stock lines (explode headers, cores)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_pick_list(
  p_sales_invoice_id UUID,
  p_lines JSONB DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_pick UUID;
  v_line RECORD;
  v_elem JSONB;
  v_inv_line UUID;
  v_qty NUMERIC;
  v_qty_base NUMERIC;
  v_open NUMERIC;
  v_any BOOLEAN := false;
  v_bin UUID;
BEGIN
  PERFORM public._logistics_begin_rpc();
  PERFORM public._require_logistics_staff();

  SELECT * INTO v_inv
  FROM public.sales_invoices
  WHERE id = p_sales_invoice_id AND doc_type = 'invoice' AND status = 'posted'
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'posted sales invoice required';
  END IF;
  IF v_inv.fulfillment_mode <> 'dispatch' THEN
    RAISE EXCEPTION 'pick lists only for dispatch fulfillment invoices';
  END IF;

  INSERT INTO public.pick_lists (
    document_number, sales_invoice_id, warehouse_id, status, created_by
  )
  VALUES (
    public.next_series_value('PL-'),
    p_sales_invoice_id,
    v_inv.warehouse_id,
    'draft',
    auth.uid()
  )
  RETURNING id INTO v_pick;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    FOR v_line IN
      SELECT *
      FROM public.sales_invoice_lines
      WHERE invoice_id = p_sales_invoice_id
        AND NOT is_core_charge
        AND COALESCE(issues_stock, true)
        AND qty_base > qty_fulfilled
    LOOP
      v_open := public._invoice_line_open_qty_base(v_line.id);
      IF v_open <= 0 THEN
        CONTINUE;
      END IF;
      v_any := true;

      SELECT sl.bin_id INTO v_bin
      FROM public.stock_levels sl
      WHERE sl.stock_item_id = v_line.stock_item_id
        AND sl.warehouse_id = v_inv.warehouse_id;

      INSERT INTO public.pick_list_lines (
        pick_list_id, sales_invoice_line_id, stock_item_id, uom_id,
        qty_requested, qty_base_requested, suggested_bin_id
      )
      VALUES (
        v_pick, v_line.id, v_line.stock_item_id, v_line.uom_id,
        v_open, v_open, v_bin
      );
    END LOOP;
  ELSE
    FOR v_elem IN SELECT * FROM jsonb_array_elements(p_lines)
    LOOP
      v_inv_line := (v_elem ->> 'sales_invoice_line_id')::uuid;
      v_qty := (v_elem ->> 'qty')::numeric;
      IF v_inv_line IS NULL OR v_qty IS NULL OR v_qty <= 0 THEN
        RAISE EXCEPTION 'pick line requires sales_invoice_line_id and qty > 0';
      END IF;

      SELECT * INTO v_line
      FROM public.sales_invoice_lines
      WHERE id = v_inv_line AND invoice_id = p_sales_invoice_id;
      IF NOT FOUND THEN
        RAISE EXCEPTION 'invoice line % not on invoice', v_inv_line;
      END IF;
      IF v_line.is_core_charge THEN
        RAISE EXCEPTION 'core-charge lines cannot be picked';
      END IF;
      IF NOT COALESCE(v_line.issues_stock, true) THEN
        RAISE EXCEPTION 'non-stock kit header lines cannot be picked';
      END IF;

      v_qty_base := public.convert_to_base_uom(v_line.stock_item_id, v_line.uom_id, v_qty);
      v_open := public._invoice_line_open_qty_base(v_inv_line);
      IF v_qty_base > v_open THEN
        RAISE EXCEPTION 'cannot pick more than open qty (open=%, requested=%)', v_open, v_qty_base;
      END IF;

      v_any := true;

      SELECT sl.bin_id INTO v_bin
      FROM public.stock_levels sl
      WHERE sl.stock_item_id = v_line.stock_item_id
        AND sl.warehouse_id = v_inv.warehouse_id;

      INSERT INTO public.pick_list_lines (
        pick_list_id, sales_invoice_line_id, stock_item_id, uom_id,
        qty_requested, qty_base_requested, suggested_bin_id
      )
      VALUES (
        v_pick, v_inv_line, v_line.stock_item_id, v_line.uom_id,
        v_qty, v_qty_base, v_bin
      );
    END LOOP;
  END IF;

  IF NOT v_any THEN
    RAISE EXCEPTION 'no open qty to pick on invoice %', p_sales_invoice_id;
  END IF;

  RETURN v_pick;
END;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.create_item_kit(UUID, public.kit_sell_mode) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.update_item_kit(UUID, public.kit_sell_mode, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_kit_component(UUID, UUID, NUMERIC, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.remove_kit_component(UUID, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_cart_line(UUID, UUID, UUID, NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.checkout_pos_cart(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_pick_list(UUID, JSONB) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_item_kit(UUID, public.kit_sell_mode)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_item_kit(UUID, public.kit_sell_mode, BOOLEAN)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.add_kit_component(UUID, UUID, NUMERIC, UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.remove_kit_component(UUID, UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.add_cart_line(UUID, UUID, UUID, NUMERIC)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.checkout_pos_cart(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_pick_list(UUID, JSONB) TO authenticated, service_role;
