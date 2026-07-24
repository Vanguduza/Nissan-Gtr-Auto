-- Phase 16 slice 3: Consignment stock
-- Supplier-owned (held in our WH) and customer-held (our ownership at customer).
-- Revenue rule: recognize Parts Sales Revenue (4100) ONLY on purpose=recognize_sale
-- (customer consumes customer-held stock). Receive / place / take_ownership never hit 4100.
-- No ZIMRA / tax / loyalty.

-- ---------------------------------------------------------------------------
-- CoA: customer-held consignment inventory (our asset off-site)
-- ---------------------------------------------------------------------------
INSERT INTO public.chart_of_accounts (code, name, account_type) VALUES
  ('1320', 'Inventory (Customer Consignment)', 'asset')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Enums
-- ---------------------------------------------------------------------------
CREATE TYPE public.consignment_kind AS ENUM ('supplier_owned', 'customer_held');

CREATE TYPE public.consignment_entry_purpose AS ENUM (
  'receive',               -- supplier_owned inbound (qty memo only; no JE)
  'return_to_supplier',    -- supplier_owned outbound (qty memo only; no JE)
  'place_at_customer',     -- move our MAIN stock → customer-held (Dr 1320 / Cr 1300)
  'return_from_customer',  -- pull customer-held back to MAIN (Dr 1300 / Cr 1320)
  'take_ownership',        -- buy supplier-owned into MAIN (Dr 1300 / Cr 2100); no revenue
  'recognize_sale'         -- customer consumes held stock → AR + revenue + COGS
);

-- ---------------------------------------------------------------------------
-- Quantity balances (not owned inventory for supplier_owned until take_ownership)
-- ---------------------------------------------------------------------------
CREATE TABLE public.consignment_stock_levels (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  kind public.consignment_kind NOT NULL,
  supplier_id UUID REFERENCES public.suppliers (id) ON DELETE RESTRICT,
  customer_id UUID REFERENCES public.customers (id) ON DELETE RESTRICT,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id) ON DELETE RESTRICT,
  quantity NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  unit_cost NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT consignment_levels_party_ck CHECK (
    (kind = 'supplier_owned' AND supplier_id IS NOT NULL AND customer_id IS NULL)
    OR (kind = 'customer_held' AND customer_id IS NOT NULL AND supplier_id IS NULL)
  )
);

CREATE UNIQUE INDEX consignment_stock_levels_supplier_uidx
  ON public.consignment_stock_levels (supplier_id, stock_item_id, warehouse_id)
  WHERE kind = 'supplier_owned';

CREATE UNIQUE INDEX consignment_stock_levels_customer_uidx
  ON public.consignment_stock_levels (customer_id, stock_item_id, warehouse_id)
  WHERE kind = 'customer_held';

CREATE INDEX consignment_stock_levels_item_idx
  ON public.consignment_stock_levels (stock_item_id);

COMMENT ON TABLE public.consignment_stock_levels IS
  'Consignment qty. supplier_owned = not on BS until take_ownership; customer_held = valued in 1320.';

-- ---------------------------------------------------------------------------
-- Transactional docs (Draft → Submit → Cancel)
-- ---------------------------------------------------------------------------
CREATE TABLE public.consignment_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  status public.procurement_doc_status NOT NULL DEFAULT 'draft',
  kind public.consignment_kind NOT NULL,
  purpose public.consignment_entry_purpose NOT NULL,
  supplier_id UUID REFERENCES public.suppliers (id) ON DELETE RESTRICT,
  customer_id UUID REFERENCES public.customers (id) ON DELETE RESTRICT,
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id) ON DELETE RESTRICT,
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  notes TEXT,
  journal_entry_id UUID REFERENCES public.journal_entries (id),
  reversal_journal_entry_id UUID REFERENCES public.journal_entries (id),
  created_by UUID REFERENCES auth.users (id),
  submitted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT consignment_entries_party_ck CHECK (
    (kind = 'supplier_owned' AND supplier_id IS NOT NULL AND customer_id IS NULL)
    OR (kind = 'customer_held' AND customer_id IS NOT NULL AND supplier_id IS NULL)
  ),
  CONSTRAINT consignment_entries_purpose_kind_ck CHECK (
    (kind = 'supplier_owned' AND purpose IN (
      'receive', 'return_to_supplier', 'take_ownership'
    ))
    OR (kind = 'customer_held' AND purpose IN (
      'place_at_customer', 'return_from_customer', 'recognize_sale'
    ))
  ),
  CONSTRAINT consignment_entries_exchange_rate_ck CHECK (
    currency = 'USD'
    OR (exchange_rate_applied IS NOT NULL AND exchange_rate_applied > 0)
  )
);

CREATE TABLE public.consignment_entry_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  consignment_entry_id UUID NOT NULL
    REFERENCES public.consignment_entries (id) ON DELETE CASCADE,
  line_no INT NOT NULL,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  unit_cost NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
  unit_price NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (consignment_entry_id, line_no)
);

CREATE INDEX consignment_entries_status_idx
  ON public.consignment_entries (status, kind);
CREATE INDEX consignment_entry_lines_entry_idx
  ON public.consignment_entry_lines (consignment_entry_id);

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('CNS-', 'Consignment entry', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.consignment_stock_levels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.consignment_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.consignment_entry_lines ENABLE ROW LEVEL SECURITY;

CREATE POLICY consignment_stock_levels_staff
  ON public.consignment_stock_levels FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]));

CREATE POLICY consignment_entries_staff
  ON public.consignment_entries FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]));

CREATE POLICY consignment_entry_lines_staff
  ON public.consignment_entry_lines FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.consignment_stock_levels TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.consignment_entries TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.consignment_entry_lines TO authenticated;

-- ---------------------------------------------------------------------------
-- Mutation guards (Draft-only line edits; headers via RPCs)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.guard_consignment_entry_immutable()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status IS DISTINCT FROM 'draft' THEN
      RAISE EXCEPTION 'consignment_entries: new rows must be draft; use RPCs';
    END IF;
    IF current_setting('gtr.consignment_rpc', true) IS DISTINCT FROM 'on' THEN
      RAISE EXCEPTION 'consignment_entries: use create_consignment_entry_draft RPC';
    END IF;
  ELSIF TG_OP = 'UPDATE' THEN
    IF current_setting('gtr.consignment_rpc', true) IS DISTINCT FROM 'on' THEN
      RAISE EXCEPTION 'consignment_entries: use consignment RPCs';
    END IF;
  ELSIF TG_OP = 'DELETE' THEN
    IF current_setting('gtr.consignment_rpc', true) IS DISTINCT FROM 'on' THEN
      RAISE EXCEPTION 'consignment_entries: direct delete forbidden';
    END IF;
  END IF;
  RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS consignment_entries_guard ON public.consignment_entries;
CREATE TRIGGER consignment_entries_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.consignment_entries
  FOR EACH ROW EXECUTE PROCEDURE public.guard_consignment_entry_immutable();

CREATE OR REPLACE FUNCTION public.guard_consignment_entry_lines_draft()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
  v_status public.procurement_doc_status;
BEGIN
  IF current_setting('gtr.consignment_rpc', true) IS DISTINCT FROM 'on' THEN
    RAISE EXCEPTION 'consignment_entry_lines: use consignment RPCs';
  END IF;
  SELECT status INTO v_status
  FROM public.consignment_entries
  WHERE id = COALESCE(NEW.consignment_entry_id, OLD.consignment_entry_id);
  IF v_status IS DISTINCT FROM 'draft' THEN
    RAISE EXCEPTION 'consignment_entry_lines: parent must be draft (status=%)', v_status;
  END IF;
  RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS consignment_entry_lines_guard ON public.consignment_entry_lines;
CREATE TRIGGER consignment_entry_lines_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.consignment_entry_lines
  FOR EACH ROW EXECUTE PROCEDURE public.guard_consignment_entry_lines_draft();

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._require_consignment_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'warehouse, finance, or admin role required for consignment';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._adjust_consignment_level(
  p_kind public.consignment_kind,
  p_supplier_id UUID,
  p_customer_id UUID,
  p_item UUID,
  p_warehouse UUID,
  p_delta NUMERIC,
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
BEGIN
  IF p_kind = 'supplier_owned' THEN
    INSERT INTO public.consignment_stock_levels (
      kind, supplier_id, customer_id, stock_item_id, warehouse_id,
      quantity, unit_cost, currency
    )
    VALUES (
      'supplier_owned', p_supplier_id, NULL, p_item, p_warehouse,
      GREATEST(p_delta, 0), COALESCE(p_unit_cost, 0), p_currency
    )
    ON CONFLICT (supplier_id, stock_item_id, warehouse_id)
      WHERE kind = 'supplier_owned'
    DO UPDATE SET
      quantity = public.consignment_stock_levels.quantity + p_delta,
      unit_cost = CASE
        WHEN p_delta > 0 THEN COALESCE(p_unit_cost, public.consignment_stock_levels.unit_cost)
        ELSE public.consignment_stock_levels.unit_cost
      END,
      updated_at = now()
    RETURNING quantity INTO v_qty;
  ELSE
    INSERT INTO public.consignment_stock_levels (
      kind, supplier_id, customer_id, stock_item_id, warehouse_id,
      quantity, unit_cost, currency
    )
    VALUES (
      'customer_held', NULL, p_customer_id, p_item, p_warehouse,
      GREATEST(p_delta, 0), COALESCE(p_unit_cost, 0), p_currency
    )
    ON CONFLICT (customer_id, stock_item_id, warehouse_id)
      WHERE kind = 'customer_held'
    DO UPDATE SET
      quantity = public.consignment_stock_levels.quantity + p_delta,
      unit_cost = CASE
        WHEN p_delta > 0 THEN COALESCE(p_unit_cost, public.consignment_stock_levels.unit_cost)
        ELSE public.consignment_stock_levels.unit_cost
      END,
      updated_at = now()
    RETURNING quantity INTO v_qty;
  END IF;

  IF v_qty < 0 THEN
    RAISE EXCEPTION 'insufficient consignment qty for item %', p_item;
  END IF;
END;
$$;

-- Partial unique indexes need explicit conflict targets via inference; Postgres
-- ON CONFLICT requires matching unique index. Wrap with upsert helpers instead
-- when inference fails on older PG — use SELECT FOR UPDATE path below.

CREATE OR REPLACE FUNCTION public._adjust_consignment_level(
  p_kind public.consignment_kind,
  p_supplier_id UUID,
  p_customer_id UUID,
  p_item UUID,
  p_warehouse UUID,
  p_delta NUMERIC,
  p_unit_cost NUMERIC,
  p_currency public.currency_code
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_qty NUMERIC;
BEGIN
  IF p_kind = 'supplier_owned' THEN
    SELECT id, quantity INTO v_id, v_qty
    FROM public.consignment_stock_levels
    WHERE kind = 'supplier_owned'
      AND supplier_id = p_supplier_id
      AND stock_item_id = p_item
      AND warehouse_id = p_warehouse
    FOR UPDATE;

    IF v_id IS NULL THEN
      IF p_delta < 0 THEN
        RAISE EXCEPTION 'insufficient consignment qty for item %', p_item;
      END IF;
      INSERT INTO public.consignment_stock_levels (
        kind, supplier_id, stock_item_id, warehouse_id, quantity, unit_cost, currency
      ) VALUES (
        'supplier_owned', p_supplier_id, p_item, p_warehouse,
        p_delta, COALESCE(p_unit_cost, 0), p_currency
      );
    ELSE
      IF v_qty + p_delta < 0 THEN
        RAISE EXCEPTION 'insufficient consignment qty for item %', p_item;
      END IF;
      UPDATE public.consignment_stock_levels
      SET
        quantity = quantity + p_delta,
        unit_cost = CASE
          WHEN p_delta > 0 THEN COALESCE(p_unit_cost, unit_cost)
          ELSE unit_cost
        END,
        updated_at = now()
      WHERE id = v_id;
    END IF;
  ELSE
    SELECT id, quantity INTO v_id, v_qty
    FROM public.consignment_stock_levels
    WHERE kind = 'customer_held'
      AND customer_id = p_customer_id
      AND stock_item_id = p_item
      AND warehouse_id = p_warehouse
    FOR UPDATE;

    IF v_id IS NULL THEN
      IF p_delta < 0 THEN
        RAISE EXCEPTION 'insufficient consignment qty for item %', p_item;
      END IF;
      INSERT INTO public.consignment_stock_levels (
        kind, customer_id, stock_item_id, warehouse_id, quantity, unit_cost, currency
      ) VALUES (
        'customer_held', p_customer_id, p_item, p_warehouse,
        p_delta, COALESCE(p_unit_cost, 0), p_currency
      );
    ELSE
      IF v_qty + p_delta < 0 THEN
        RAISE EXCEPTION 'insufficient consignment qty for item %', p_item;
      END IF;
      UPDATE public.consignment_stock_levels
      SET
        quantity = quantity + p_delta,
        unit_cost = CASE
          WHEN p_delta > 0 THEN COALESCE(p_unit_cost, unit_cost)
          ELSE unit_cost
        END,
        updated_at = now()
      WHERE id = v_id;
    END IF;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- RPCs: draft / lines / submit / cancel
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_consignment_entry_draft(
  p_kind public.consignment_kind,
  p_purpose public.consignment_entry_purpose,
  p_warehouse_id UUID,
  p_supplier_id UUID DEFAULT NULL,
  p_customer_id UUID DEFAULT NULL,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT 1,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_rate NUMERIC;
BEGIN
  PERFORM public._require_consignment_staff();

  IF p_kind = 'supplier_owned' THEN
    IF p_supplier_id IS NULL OR p_customer_id IS NOT NULL THEN
      RAISE EXCEPTION 'supplier_owned requires supplier_id and no customer_id';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.suppliers WHERE id = p_supplier_id AND is_active) THEN
      RAISE EXCEPTION 'supplier not found or inactive';
    END IF;
  ELSE
    IF p_customer_id IS NULL OR p_supplier_id IS NOT NULL THEN
      RAISE EXCEPTION 'customer_held requires customer_id and no supplier_id';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.customers WHERE id = p_customer_id) THEN
      RAISE EXCEPTION 'customer not found';
    END IF;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.warehouses WHERE id = p_warehouse_id AND is_active AND NOT is_quarantine
  ) THEN
    RAISE EXCEPTION 'warehouse must be active non-quarantine';
  END IF;

  v_rate := CASE
    WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
    ELSE p_exchange_rate
  END;
  IF p_currency <> 'USD' AND (v_rate IS NULL OR v_rate <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for non-USD';
  END IF;

  PERFORM set_config('gtr.consignment_rpc', 'on', true);

  INSERT INTO public.consignment_entries (
    document_number, status, kind, purpose, supplier_id, customer_id,
    warehouse_id, currency, exchange_rate_applied, notes, created_by
  )
  VALUES (
    public.next_series_value('CNS-'),
    'draft',
    p_kind,
    p_purpose,
    p_supplier_id,
    p_customer_id,
    p_warehouse_id,
    p_currency,
    v_rate,
    p_notes,
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.add_consignment_entry_line(
  p_entry_id UUID,
  p_stock_item_id UUID,
  p_uom_id UUID,
  p_qty NUMERIC,
  p_unit_cost NUMERIC DEFAULT 0,
  p_unit_price NUMERIC DEFAULT 0,
  p_currency public.currency_code DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_hdr public.consignment_entries%ROWTYPE;
  v_line_no INT;
  v_id UUID;
  v_cur public.currency_code;
BEGIN
  PERFORM public._require_consignment_staff();

  SELECT * INTO v_hdr FROM public.consignment_entries WHERE id = p_entry_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'consignment entry not found: %', p_entry_id;
  END IF;
  IF v_hdr.status <> 'draft' THEN
    RAISE EXCEPTION 'lines editable only while draft';
  END IF;
  IF p_qty IS NULL OR p_qty <= 0 THEN
    RAISE EXCEPTION 'qty must be > 0';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.stock_items WHERE id = p_stock_item_id) THEN
    RAISE EXCEPTION 'stock item not found';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.uoms WHERE id = p_uom_id) THEN
    RAISE EXCEPTION 'uom not found';
  END IF;

  IF v_hdr.purpose = 'recognize_sale' AND COALESCE(p_unit_price, 0) <= 0 THEN
    RAISE EXCEPTION 'recognize_sale requires unit_price > 0';
  END IF;

  v_cur := COALESCE(p_currency, v_hdr.currency);

  SELECT COALESCE(MAX(line_no), 0) + 1 INTO v_line_no
  FROM public.consignment_entry_lines
  WHERE consignment_entry_id = p_entry_id;

  PERFORM set_config('gtr.consignment_rpc', 'on', true);

  INSERT INTO public.consignment_entry_lines (
    consignment_entry_id, line_no, stock_item_id, uom_id, qty,
    unit_cost, unit_price, currency
  )
  VALUES (
    p_entry_id, v_line_no, p_stock_item_id, p_uom_id, p_qty,
    COALESCE(p_unit_cost, 0), COALESCE(p_unit_price, 0), v_cur
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_consignment_entry(p_entry_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_hdr public.consignment_entries%ROWTYPE;
  v_line RECORD;
  v_qty_base NUMERIC;
  v_cost NUMERIC;
  v_price NUMERIC;
  v_inv_total NUMERIC := 0;
  v_cogs_total NUMERIC := 0;
  v_rev_total NUMERIC := 0;
  v_journal UUID;
  v_lines JSONB := '[]'::jsonb;
  v_batch_code TEXT;
  v_level_cost NUMERIC;
BEGIN
  PERFORM public._require_consignment_staff();

  SELECT * INTO v_hdr FROM public.consignment_entries WHERE id = p_entry_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'consignment entry not found: %', p_entry_id;
  END IF;
  IF v_hdr.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft consignment entries can be submitted (status=%)', v_hdr.status;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.consignment_entry_lines WHERE consignment_entry_id = p_entry_id
  ) THEN
    RAISE EXCEPTION 'consignment entry has no lines';
  END IF;

  IF public.is_period_locked(CURRENT_DATE) THEN
    RAISE EXCEPTION 'accounting period is locked for today';
  END IF;

  FOR v_line IN
    SELECT * FROM public.consignment_entry_lines
    WHERE consignment_entry_id = p_entry_id
    ORDER BY line_no
  LOOP
    v_qty_base := public.convert_to_base_uom(v_line.stock_item_id, v_line.uom_id, v_line.qty);
    v_cost := v_line.unit_cost;
    v_price := v_line.unit_price;

    CASE v_hdr.purpose
      WHEN 'receive' THEN
        -- Supplier-owned memo qty only — no stock_levels, no journal, no revenue
        PERFORM public._adjust_consignment_level(
          'supplier_owned', v_hdr.supplier_id, NULL,
          v_line.stock_item_id, v_hdr.warehouse_id,
          v_qty_base, v_cost, v_line.currency
        );

      WHEN 'return_to_supplier' THEN
        PERFORM public._adjust_consignment_level(
          'supplier_owned', v_hdr.supplier_id, NULL,
          v_line.stock_item_id, v_hdr.warehouse_id,
          -v_qty_base, v_cost, v_line.currency
        );

      WHEN 'place_at_customer' THEN
        -- Reclass owned inventory MAIN → 1320; still our asset; no revenue
        SELECT unit_cost INTO v_level_cost
        FROM public.stock_levels
        WHERE stock_item_id = v_line.stock_item_id AND warehouse_id = v_hdr.warehouse_id;
        v_cost := COALESCE(NULLIF(v_cost, 0), v_level_cost, 0);

        PERFORM public._consume_fifo_batches(
          v_line.stock_item_id, v_hdr.warehouse_id, v_qty_base
        );
        PERFORM public._adjust_stock_level(
          v_line.stock_item_id, v_hdr.warehouse_id, -v_qty_base,
          'FIFO', v_cost, v_line.currency
        );
        PERFORM public._adjust_consignment_level(
          'customer_held', NULL, v_hdr.customer_id,
          v_line.stock_item_id, v_hdr.warehouse_id,
          v_qty_base, v_cost, v_line.currency
        );
        v_inv_total := v_inv_total + round(v_qty_base * v_cost, 2);

      WHEN 'return_from_customer' THEN
        SELECT unit_cost INTO v_level_cost
        FROM public.consignment_stock_levels
        WHERE kind = 'customer_held'
          AND customer_id = v_hdr.customer_id
          AND stock_item_id = v_line.stock_item_id
          AND warehouse_id = v_hdr.warehouse_id;
        v_cost := COALESCE(NULLIF(v_cost, 0), v_level_cost, 0);

        PERFORM public._adjust_consignment_level(
          'customer_held', NULL, v_hdr.customer_id,
          v_line.stock_item_id, v_hdr.warehouse_id,
          -v_qty_base, v_cost, v_line.currency
        );
        v_batch_code := public.next_series_value('BATCH-');
        INSERT INTO public.stock_batches (
          batch_code, stock_item_id, warehouse_id, valuation_method,
          unit_cost, currency, qty_on_hand
        ) VALUES (
          v_batch_code, v_line.stock_item_id, v_hdr.warehouse_id, 'FIFO',
          v_cost, v_line.currency, v_qty_base
        );
        PERFORM public._adjust_stock_level(
          v_line.stock_item_id, v_hdr.warehouse_id, v_qty_base,
          'FIFO', v_cost, v_line.currency
        );
        v_inv_total := v_inv_total + round(v_qty_base * v_cost, 2);

      WHEN 'take_ownership' THEN
        -- Purchase into owned inventory — AP only; NEVER revenue
        PERFORM public._adjust_consignment_level(
          'supplier_owned', v_hdr.supplier_id, NULL,
          v_line.stock_item_id, v_hdr.warehouse_id,
          -v_qty_base, v_cost, v_line.currency
        );
        v_batch_code := public.next_series_value('BATCH-');
        INSERT INTO public.stock_batches (
          batch_code, stock_item_id, warehouse_id, valuation_method,
          unit_cost, currency, qty_on_hand
        ) VALUES (
          v_batch_code, v_line.stock_item_id, v_hdr.warehouse_id, 'FIFO',
          v_cost, v_line.currency, v_qty_base
        );
        PERFORM public._adjust_stock_level(
          v_line.stock_item_id, v_hdr.warehouse_id, v_qty_base,
          'FIFO', v_cost, v_line.currency
        );
        v_inv_total := v_inv_total + round(v_qty_base * v_cost, 2);

      WHEN 'recognize_sale' THEN
        -- ONLY path that credits 4100 Parts Sales Revenue
        SELECT unit_cost INTO v_level_cost
        FROM public.consignment_stock_levels
        WHERE kind = 'customer_held'
          AND customer_id = v_hdr.customer_id
          AND stock_item_id = v_line.stock_item_id
          AND warehouse_id = v_hdr.warehouse_id;
        v_cost := COALESCE(NULLIF(v_cost, 0), v_level_cost, 0);

        PERFORM public._adjust_consignment_level(
          'customer_held', NULL, v_hdr.customer_id,
          v_line.stock_item_id, v_hdr.warehouse_id,
          -v_qty_base, v_cost, v_line.currency
        );
        v_cogs_total := v_cogs_total + round(v_qty_base * v_cost, 2);
        v_rev_total := v_rev_total + round(v_qty_base * v_price, 2);

      ELSE
        RAISE EXCEPTION 'unsupported consignment purpose: %', v_hdr.purpose;
    END CASE;
  END LOOP;

  -- Journals (append-only via _post_journal_entry_inventory)
  IF v_hdr.purpose = 'place_at_customer' AND v_inv_total > 0 THEN
    v_journal := public._post_journal_entry_inventory(
      CURRENT_DATE,
      format('Consignment place %s', v_hdr.document_number),
      v_hdr.currency,
      v_hdr.exchange_rate_applied,
      jsonb_build_array(
        jsonb_build_object('account_code', '1320', 'debit', v_inv_total, 'credit', 0, 'currency', v_hdr.currency),
        jsonb_build_object('account_code', '1300', 'debit', 0, 'credit', v_inv_total, 'currency', v_hdr.currency)
      )
    );
  ELSIF v_hdr.purpose = 'return_from_customer' AND v_inv_total > 0 THEN
    v_journal := public._post_journal_entry_inventory(
      CURRENT_DATE,
      format('Consignment return %s', v_hdr.document_number),
      v_hdr.currency,
      v_hdr.exchange_rate_applied,
      jsonb_build_array(
        jsonb_build_object('account_code', '1300', 'debit', v_inv_total, 'credit', 0, 'currency', v_hdr.currency),
        jsonb_build_object('account_code', '1320', 'debit', 0, 'credit', v_inv_total, 'currency', v_hdr.currency)
      )
    );
  ELSIF v_hdr.purpose = 'take_ownership' AND v_inv_total > 0 THEN
    v_journal := public._post_journal_entry_inventory(
      CURRENT_DATE,
      format('Consignment take ownership %s', v_hdr.document_number),
      v_hdr.currency,
      v_hdr.exchange_rate_applied,
      jsonb_build_array(
        jsonb_build_object('account_code', '1300', 'debit', v_inv_total, 'credit', 0, 'currency', v_hdr.currency),
        jsonb_build_object('account_code', '2100', 'debit', 0, 'credit', v_inv_total, 'currency', v_hdr.currency)
      )
    );
  ELSIF v_hdr.purpose = 'recognize_sale' THEN
    IF v_rev_total <= 0 THEN
      RAISE EXCEPTION 'recognize_sale requires positive revenue total';
    END IF;
    v_lines := jsonb_build_array(
      jsonb_build_object('account_code', '1200', 'debit', v_rev_total, 'credit', 0, 'currency', v_hdr.currency),
      jsonb_build_object('account_code', '4100', 'debit', 0, 'credit', v_rev_total, 'currency', v_hdr.currency)
    );
    IF v_cogs_total > 0 THEN
      v_lines := v_lines || jsonb_build_array(
        jsonb_build_object('account_code', '5100', 'debit', v_cogs_total, 'credit', 0, 'currency', v_hdr.currency),
        jsonb_build_object('account_code', '1320', 'debit', 0, 'credit', v_cogs_total, 'currency', v_hdr.currency)
      );
    END IF;
    v_journal := public._post_journal_entry_inventory(
      CURRENT_DATE,
      format('Consignment sale %s', v_hdr.document_number),
      v_hdr.currency,
      v_hdr.exchange_rate_applied,
      v_lines
    );

    UPDATE public.customers
    SET open_balance = open_balance + v_rev_total, updated_at = now()
    WHERE id = v_hdr.customer_id;
  END IF;
  -- receive / return_to_supplier: intentionally no journal (not our inventory)

  PERFORM set_config('gtr.consignment_rpc', 'on', true);

  UPDATE public.consignment_entries
  SET
    status = 'submitted',
    submitted_at = now(),
    journal_entry_id = v_journal,
    updated_at = now()
  WHERE id = p_entry_id;

  RETURN p_entry_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_consignment_entry(p_entry_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_hdr public.consignment_entries%ROWTYPE;
  v_line RECORD;
  v_qty_base NUMERIC;
  v_cost NUMERIC;
  v_price NUMERIC;
  v_rev_total NUMERIC := 0;
  v_rev_je UUID;
  v_batch_code TEXT;
  v_level_cost NUMERIC;
BEGIN
  PERFORM public._require_consignment_staff();

  SELECT * INTO v_hdr FROM public.consignment_entries WHERE id = p_entry_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'consignment entry not found: %', p_entry_id;
  END IF;
  IF v_hdr.status = 'cancelled' THEN
    RETURN p_entry_id;
  END IF;
  IF v_hdr.status NOT IN ('draft', 'submitted') THEN
    RAISE EXCEPTION 'only draft/submitted consignment entries can be cancelled';
  END IF;

  IF v_hdr.status = 'draft' THEN
    PERFORM set_config('gtr.consignment_rpc', 'on', true);
    UPDATE public.consignment_entries
    SET status = 'cancelled', cancelled_at = now(), updated_at = now()
    WHERE id = p_entry_id;
    RETURN p_entry_id;
  END IF;

  IF public.is_period_locked(CURRENT_DATE) THEN
    RAISE EXCEPTION 'accounting period is locked for today';
  END IF;

  -- Reverse stock / consignment qty (mirror of submit)
  FOR v_line IN
    SELECT * FROM public.consignment_entry_lines
    WHERE consignment_entry_id = p_entry_id
    ORDER BY line_no
  LOOP
    v_qty_base := public.convert_to_base_uom(v_line.stock_item_id, v_line.uom_id, v_line.qty);
    v_cost := v_line.unit_cost;
    v_price := v_line.unit_price;

    CASE v_hdr.purpose
      WHEN 'receive' THEN
        PERFORM public._adjust_consignment_level(
          'supplier_owned', v_hdr.supplier_id, NULL,
          v_line.stock_item_id, v_hdr.warehouse_id,
          -v_qty_base, v_cost, v_line.currency
        );
      WHEN 'return_to_supplier' THEN
        PERFORM public._adjust_consignment_level(
          'supplier_owned', v_hdr.supplier_id, NULL,
          v_line.stock_item_id, v_hdr.warehouse_id,
          v_qty_base, v_cost, v_line.currency
        );
      WHEN 'place_at_customer' THEN
        SELECT unit_cost INTO v_level_cost
        FROM public.consignment_stock_levels
        WHERE kind = 'customer_held'
          AND customer_id = v_hdr.customer_id
          AND stock_item_id = v_line.stock_item_id
          AND warehouse_id = v_hdr.warehouse_id;
        v_cost := COALESCE(NULLIF(v_cost, 0), v_level_cost, 0);
        PERFORM public._adjust_consignment_level(
          'customer_held', NULL, v_hdr.customer_id,
          v_line.stock_item_id, v_hdr.warehouse_id,
          -v_qty_base, v_cost, v_line.currency
        );
        v_batch_code := public.next_series_value('BATCH-');
        INSERT INTO public.stock_batches (
          batch_code, stock_item_id, warehouse_id, valuation_method,
          unit_cost, currency, qty_on_hand
        ) VALUES (
          v_batch_code, v_line.stock_item_id, v_hdr.warehouse_id, 'FIFO',
          v_cost, v_line.currency, v_qty_base
        );
        PERFORM public._adjust_stock_level(
          v_line.stock_item_id, v_hdr.warehouse_id, v_qty_base,
          'FIFO', v_cost, v_line.currency
        );
      WHEN 'return_from_customer' THEN
        SELECT unit_cost INTO v_level_cost
        FROM public.stock_levels
        WHERE stock_item_id = v_line.stock_item_id AND warehouse_id = v_hdr.warehouse_id;
        v_cost := COALESCE(NULLIF(v_cost, 0), v_level_cost, 0);
        PERFORM public._consume_fifo_batches(
          v_line.stock_item_id, v_hdr.warehouse_id, v_qty_base
        );
        PERFORM public._adjust_stock_level(
          v_line.stock_item_id, v_hdr.warehouse_id, -v_qty_base,
          'FIFO', v_cost, v_line.currency
        );
        PERFORM public._adjust_consignment_level(
          'customer_held', NULL, v_hdr.customer_id,
          v_line.stock_item_id, v_hdr.warehouse_id,
          v_qty_base, v_cost, v_line.currency
        );
      WHEN 'take_ownership' THEN
        SELECT unit_cost INTO v_level_cost
        FROM public.stock_levels
        WHERE stock_item_id = v_line.stock_item_id AND warehouse_id = v_hdr.warehouse_id;
        v_cost := COALESCE(NULLIF(v_cost, 0), v_level_cost, 0);
        PERFORM public._consume_fifo_batches(
          v_line.stock_item_id, v_hdr.warehouse_id, v_qty_base
        );
        PERFORM public._adjust_stock_level(
          v_line.stock_item_id, v_hdr.warehouse_id, -v_qty_base,
          'FIFO', v_cost, v_line.currency
        );
        PERFORM public._adjust_consignment_level(
          'supplier_owned', v_hdr.supplier_id, NULL,
          v_line.stock_item_id, v_hdr.warehouse_id,
          v_qty_base, v_cost, v_line.currency
        );
      WHEN 'recognize_sale' THEN
        SELECT unit_cost INTO v_level_cost
        FROM public.consignment_stock_levels
        WHERE kind = 'customer_held'
          AND customer_id = v_hdr.customer_id
          AND stock_item_id = v_line.stock_item_id
          AND warehouse_id = v_hdr.warehouse_id;
        v_cost := COALESCE(NULLIF(v_cost, 0), v_level_cost, v_line.unit_cost, 0);
        PERFORM public._adjust_consignment_level(
          'customer_held', NULL, v_hdr.customer_id,
          v_line.stock_item_id, v_hdr.warehouse_id,
          v_qty_base, v_cost, v_line.currency
        );
        v_rev_total := v_rev_total + round(v_qty_base * v_price, 2);
      ELSE
        RAISE EXCEPTION 'unsupported consignment purpose: %', v_hdr.purpose;
    END CASE;
  END LOOP;

  IF v_hdr.journal_entry_id IS NOT NULL THEN
    v_rev_je := public._reverse_journal_inventory(
      v_hdr.journal_entry_id,
      format('Cancel consignment %s', v_hdr.document_number)
    );
  END IF;

  IF v_hdr.purpose = 'recognize_sale' AND v_rev_total > 0 THEN
    UPDATE public.customers
    SET open_balance = open_balance - v_rev_total, updated_at = now()
    WHERE id = v_hdr.customer_id;
  END IF;

  PERFORM set_config('gtr.consignment_rpc', 'on', true);

  UPDATE public.consignment_entries
  SET
    status = 'cancelled',
    cancelled_at = now(),
    reversal_journal_entry_id = v_rev_je,
    updated_at = now()
  WHERE id = p_entry_id;

  RETURN p_entry_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.create_consignment_entry_draft(
  public.consignment_kind, public.consignment_entry_purpose, UUID, UUID, UUID,
  public.currency_code, NUMERIC, TEXT
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_consignment_entry_line(
  UUID, UUID, UUID, NUMERIC, NUMERIC, NUMERIC, public.currency_code
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_consignment_entry(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_consignment_entry(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._adjust_consignment_level(
  public.consignment_kind, UUID, UUID, UUID, UUID, NUMERIC, NUMERIC, public.currency_code
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._require_consignment_staff() FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_consignment_entry_draft(
  public.consignment_kind, public.consignment_entry_purpose, UUID, UUID, UUID,
  public.currency_code, NUMERIC, TEXT
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.add_consignment_entry_line(
  UUID, UUID, UUID, NUMERIC, NUMERIC, NUMERIC, public.currency_code
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_consignment_entry(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_consignment_entry(UUID) TO authenticated, service_role;

-- UI follow-on: @management_app_agent — consignment receive / place / take_ownership / sale
-- Types regen: supabase gen types typescript --local > packages/supabase-client/src/database.types.ts
