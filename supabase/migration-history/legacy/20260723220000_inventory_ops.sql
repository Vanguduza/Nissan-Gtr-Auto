-- Phase 4: UOM, batches, stock entries, dual-auth transfer, QR payload, serials, quarantine returns
-- Exclusions: no ZIMRA / payroll tax; Bridge-First (no HTML5 QR)

CREATE TYPE public.stock_entry_type AS ENUM ('receipt', 'transfer', 'issue');
CREATE TYPE public.stock_entry_status AS ENUM (
  'draft',
  'pending_approval',
  'posted',
  'cancelled',
  'rejected'
);

-- ---------------------------------------------------------------------------
-- UOM
-- ---------------------------------------------------------------------------
CREATE TABLE public.uoms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(16) NOT NULL UNIQUE,
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO public.uoms (code, name) VALUES
  ('EA', 'Each'),
  ('BOX', 'Box'),
  ('SET', 'Set')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE public.stock_items
  ADD COLUMN IF NOT EXISTS base_uom_id UUID REFERENCES public.uoms (id),
  ADD COLUMN IF NOT EXISTS requires_serial BOOLEAN NOT NULL DEFAULT false;

UPDATE public.stock_items si
SET base_uom_id = (SELECT id FROM public.uoms WHERE code = 'EA')
WHERE base_uom_id IS NULL;

CREATE TABLE public.item_uom_conversions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE CASCADE,
  from_uom_id UUID NOT NULL REFERENCES public.uoms (id),
  to_uom_id UUID NOT NULL REFERENCES public.uoms (id),
  factor NUMERIC(18, 6) NOT NULL CHECK (factor > 0),
  UNIQUE (stock_item_id, from_uom_id, to_uom_id),
  CONSTRAINT item_uom_distinct CHECK (from_uom_id <> to_uom_id)
);

CREATE OR REPLACE FUNCTION public.convert_to_base_uom(
  p_stock_item_id UUID,
  p_from_uom_id UUID,
  p_qty NUMERIC
)
RETURNS NUMERIC
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_base UUID;
  v_factor NUMERIC;
BEGIN
  SELECT base_uom_id INTO v_base FROM public.stock_items WHERE id = p_stock_item_id;
  IF v_base IS NULL THEN
    RAISE EXCEPTION 'stock item missing base_uom: %', p_stock_item_id;
  END IF;
  IF p_from_uom_id = v_base THEN
    RETURN p_qty;
  END IF;

  SELECT factor INTO v_factor
  FROM public.item_uom_conversions
  WHERE stock_item_id = p_stock_item_id
    AND from_uom_id = p_from_uom_id
    AND to_uom_id = v_base;

  IF v_factor IS NULL THEN
    -- try inverse: base → from means factor is base per from, so qty_base = qty / factor
    SELECT factor INTO v_factor
    FROM public.item_uom_conversions
    WHERE stock_item_id = p_stock_item_id
      AND from_uom_id = v_base
      AND to_uom_id = p_from_uom_id;
    IF v_factor IS NULL THEN
      RAISE EXCEPTION 'no UOM conversion for item % from %', p_stock_item_id, p_from_uom_id;
    END IF;
    RETURN round(p_qty / v_factor, 3);
  END IF;

  RETURN round(p_qty * v_factor, 3);
END;
$$;

-- ---------------------------------------------------------------------------
-- Batches (valuation layers)
-- ---------------------------------------------------------------------------
CREATE TABLE public.stock_batches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_code VARCHAR(64) NOT NULL UNIQUE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id) ON DELETE RESTRICT,
  valuation_method public.valuation_method NOT NULL DEFAULT 'FIFO',
  unit_cost NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  qty_on_hand NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (qty_on_hand >= 0),
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX stock_batches_item_wh_idx
  ON public.stock_batches (stock_item_id, warehouse_id, received_at);

-- ---------------------------------------------------------------------------
-- Stock entries (Receipt / Transfer / Issue)
-- ---------------------------------------------------------------------------
CREATE TABLE public.stock_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entry_type public.stock_entry_type NOT NULL,
  status public.stock_entry_status NOT NULL DEFAULT 'draft',
  document_number TEXT,
  from_warehouse_id UUID REFERENCES public.warehouses (id),
  to_warehouse_id UUID REFERENCES public.warehouses (id),
  notes TEXT,
  created_by UUID REFERENCES auth.users (id),
  first_approver_id UUID REFERENCES auth.users (id),
  second_approver_id UUID REFERENCES auth.users (id),
  first_approved_at TIMESTAMPTZ,
  second_approved_at TIMESTAMPTZ,
  posted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT stock_entry_transfer_warehouses CHECK (
    entry_type <> 'transfer'
    OR (from_warehouse_id IS NOT NULL AND to_warehouse_id IS NOT NULL
        AND from_warehouse_id <> to_warehouse_id)
  ),
  CONSTRAINT stock_entry_receipt_dest CHECK (
    entry_type <> 'receipt' OR to_warehouse_id IS NOT NULL
  ),
  CONSTRAINT stock_entry_issue_source CHECK (
    entry_type <> 'issue' OR from_warehouse_id IS NOT NULL
  ),
  CONSTRAINT stock_entry_dual_auth_distinct CHECK (
    second_approver_id IS NULL
    OR first_approver_id IS NULL
    OR first_approver_id <> second_approver_id
  )
);

CREATE TABLE public.stock_entry_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stock_entry_id UUID NOT NULL REFERENCES public.stock_entries (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  qty_base NUMERIC(18, 3) NOT NULL CHECK (qty_base > 0),
  unit_cost NUMERIC(18, 4),
  currency public.currency_code DEFAULT 'USD',
  valuation_method public.valuation_method NOT NULL DEFAULT 'FIFO',
  stock_batch_id UUID REFERENCES public.stock_batches (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX stock_entry_lines_entry_idx ON public.stock_entry_lines (stock_entry_id);

-- ---------------------------------------------------------------------------
-- Serials
-- ---------------------------------------------------------------------------
CREATE TABLE public.stock_serials (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  serial_number TEXT NOT NULL,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  stock_batch_id UUID REFERENCES public.stock_batches (id),
  warehouse_id UUID REFERENCES public.warehouses (id),
  stock_entry_line_id UUID REFERENCES public.stock_entry_lines (id),
  status TEXT NOT NULL DEFAULT 'in_stock'
    CHECK (status IN ('in_stock', 'in_transit', 'sold', 'quarantine', 'scrapped')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (stock_item_id, serial_number)
);

-- ---------------------------------------------------------------------------
-- QR payload column + batch link
-- ---------------------------------------------------------------------------
ALTER TABLE public.inventory_qr_codes
  ADD COLUMN IF NOT EXISTS payload TEXT,
  ADD COLUMN IF NOT EXISTS stock_batch_id UUID REFERENCES public.stock_batches (id),
  ADD COLUMN IF NOT EXISTS stock_entry_line_id UUID REFERENCES public.stock_entry_lines (id);

CREATE OR REPLACE FUNCTION public.build_qr_payload(
  p_oem TEXT,
  p_batch_code TEXT,
  p_valuation public.valuation_method
)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT format(
    'gtr://part/%s?batch=%s&valuation=%s',
    p_oem,
    p_batch_code,
    p_valuation::text
  );
$$;

-- ---------------------------------------------------------------------------
-- Helpers: bump stock_levels
-- ---------------------------------------------------------------------------
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
  v_cost NUMERIC;
BEGIN
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
END;
$$;

CREATE OR REPLACE FUNCTION public._consume_fifo_batches(
  p_item UUID,
  p_warehouse UUID,
  p_qty_base NUMERIC
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_remaining NUMERIC := p_qty_base;
  v_batch RECORD;
  v_take NUMERIC;
BEGIN
  FOR v_batch IN
    SELECT id, qty_on_hand
    FROM public.stock_batches
    WHERE stock_item_id = p_item
      AND warehouse_id = p_warehouse
      AND qty_on_hand > 0
    ORDER BY received_at ASC, created_at ASC
    FOR UPDATE
  LOOP
    EXIT WHEN v_remaining <= 0;
    v_take := LEAST(v_batch.qty_on_hand, v_remaining);
    UPDATE public.stock_batches
    SET qty_on_hand = qty_on_hand - v_take
    WHERE id = v_batch.id;
    v_remaining := v_remaining - v_take;
  END LOOP;

  IF v_remaining > 0.0005 THEN
    RAISE EXCEPTION 'insufficient FIFO batch qty for item % (short %)', p_item, v_remaining;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._require_warehouse_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'warehouse or admin role required';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- post_stock_receipt
-- p_lines: [{stock_item_id, uom_id, qty, unit_cost, currency, valuation_method, serials?: string[]}]
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.post_stock_receipt(
  p_to_warehouse_id UUID,
  p_notes TEXT,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_entry UUID;
  v_line JSONB;
  v_line_id UUID;
  v_item UUID;
  v_uom UUID;
  v_qty NUMERIC;
  v_qty_base NUMERIC;
  v_cost NUMERIC;
  v_currency public.currency_code;
  v_val public.valuation_method;
  v_batch UUID;
  v_batch_code TEXT;
  v_oem TEXT;
  v_requires BOOLEAN;
  v_serial TEXT;
  v_wh_quar BOOLEAN;
BEGIN
  PERFORM public._require_warehouse_staff();

  SELECT is_quarantine INTO v_wh_quar FROM public.warehouses WHERE id = p_to_warehouse_id;
  IF v_wh_quar IS NULL THEN
    RAISE EXCEPTION 'warehouse not found';
  END IF;

  INSERT INTO public.stock_entries (
    entry_type, status, to_warehouse_id, notes, created_by, document_number
  )
  VALUES (
    'receipt',
    'draft',
    p_to_warehouse_id,
    p_notes,
    auth.uid(),
    public.next_series_value('RCV-')
  )
  RETURNING id INTO v_entry;

  IF p_lines IS NULL OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'receipt lines required';
  END IF;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_item := (v_line ->> 'stock_item_id')::uuid;
    v_uom := (v_line ->> 'uom_id')::uuid;
    v_qty := (v_line ->> 'qty')::numeric;
    v_cost := COALESCE((v_line ->> 'unit_cost')::numeric, 0);
    v_currency := COALESCE((v_line ->> 'currency')::public.currency_code, 'USD');
    v_val := COALESCE((v_line ->> 'valuation_method')::public.valuation_method, 'FIFO');
    v_qty_base := public.convert_to_base_uom(v_item, v_uom, v_qty);

    SELECT oem_part_number, requires_serial
    INTO v_oem, v_requires
    FROM public.stock_items WHERE id = v_item;

    v_batch_code := public.next_series_value('BATCH-');

    INSERT INTO public.stock_batches (
      batch_code, stock_item_id, warehouse_id, valuation_method,
      unit_cost, currency, qty_on_hand
    )
    VALUES (
      v_batch_code, v_item, p_to_warehouse_id, v_val, v_cost, v_currency, v_qty_base
    )
    RETURNING id INTO v_batch;

    INSERT INTO public.stock_entry_lines (
      stock_entry_id, stock_item_id, uom_id, qty, qty_base,
      unit_cost, currency, valuation_method, stock_batch_id
    )
    VALUES (
      v_entry, v_item, v_uom, v_qty, v_qty_base,
      v_cost, v_currency, v_val, v_batch
    )
    RETURNING id INTO v_line_id;

    PERFORM public._adjust_stock_level(
      v_item, p_to_warehouse_id, v_qty_base, v_val, v_cost, v_currency
    );

    INSERT INTO public.inventory_qr_codes (
      oem_part_number, batch_id, valuation_method, stock_item_id,
      stock_batch_id, stock_entry_line_id, payload
    )
    VALUES (
      v_oem,
      v_batch_code,
      v_val,
      v_item,
      v_batch,
      v_line_id,
      public.build_qr_payload(v_oem, v_batch_code, v_val)
    )
    ON CONFLICT (oem_part_number, batch_id) DO UPDATE
    SET payload = EXCLUDED.payload,
        stock_batch_id = EXCLUDED.stock_batch_id,
        stock_entry_line_id = EXCLUDED.stock_entry_line_id;

    IF v_requires THEN
      IF v_line -> 'serials' IS NULL OR jsonb_array_length(v_line -> 'serials') = 0 THEN
        RAISE EXCEPTION 'serials required for item %', v_oem;
      END IF;
      IF jsonb_array_length(v_line -> 'serials') <> round(v_qty_base)::int THEN
        RAISE EXCEPTION 'serial count must equal base qty for item %', v_oem;
      END IF;
      FOR v_serial IN SELECT jsonb_array_elements_text(v_line -> 'serials')
      LOOP
        INSERT INTO public.stock_serials (
          serial_number, stock_item_id, stock_batch_id, warehouse_id,
          stock_entry_line_id, status
        )
        VALUES (
          v_serial, v_item, v_batch, p_to_warehouse_id, v_line_id,
          CASE WHEN v_wh_quar THEN 'quarantine' ELSE 'in_stock' END
        );
      END LOOP;
    END IF;
  END LOOP;

  UPDATE public.stock_entries
  SET status = 'posted', posted_at = now()
  WHERE id = v_entry;

  PERFORM public.emit_domain_event(
    'stock_received',
    'stock_entry:' || v_entry::text,
    jsonb_build_object('stock_entry_id', v_entry, 'warehouse_id', p_to_warehouse_id)
  );

  RETURN v_entry;
END;
$$;

-- ---------------------------------------------------------------------------
-- Dual-auth transfer
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_stock_transfer(
  p_from_warehouse_id UUID,
  p_to_warehouse_id UUID,
  p_notes TEXT,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_entry UUID;
  v_line JSONB;
  v_item UUID;
  v_uom UUID;
  v_qty NUMERIC;
  v_qty_base NUMERIC;
BEGIN
  PERFORM public._require_warehouse_staff();

  IF p_from_warehouse_id = p_to_warehouse_id THEN
    RAISE EXCEPTION 'from and to warehouses must differ';
  END IF;

  INSERT INTO public.stock_entries (
    entry_type, status, from_warehouse_id, to_warehouse_id,
    notes, created_by, first_approver_id, first_approved_at, document_number
  )
  VALUES (
    'transfer',
    'pending_approval',
    p_from_warehouse_id,
    p_to_warehouse_id,
    p_notes,
    auth.uid(),
    auth.uid(),
    now(),
    public.next_series_value('TRF-')
  )
  RETURNING id INTO v_entry;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_item := (v_line ->> 'stock_item_id')::uuid;
    v_uom := (v_line ->> 'uom_id')::uuid;
    v_qty := (v_line ->> 'qty')::numeric;
    v_qty_base := public.convert_to_base_uom(v_item, v_uom, v_qty);

    INSERT INTO public.stock_entry_lines (
      stock_entry_id, stock_item_id, uom_id, qty, qty_base, valuation_method
    )
    VALUES (
      v_entry, v_item, v_uom, v_qty, v_qty_base,
      COALESCE((v_line ->> 'valuation_method')::public.valuation_method, 'FIFO')
    );
  END LOOP;

  PERFORM public.emit_domain_event(
    'transfer_pending_approval',
    'stock_entry:' || v_entry::text,
    jsonb_build_object('stock_entry_id', v_entry)
  );

  RETURN v_entry;
END;
$$;

CREATE OR REPLACE FUNCTION public.approve_stock_transfer(p_entry_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_entry public.stock_entries%ROWTYPE;
  v_line RECORD;
  v_cost NUMERIC;
  v_currency public.currency_code;
  v_val public.valuation_method;
  v_batch UUID;
  v_batch_code TEXT;
  v_dest_quar BOOLEAN;
BEGIN
  PERFORM public._require_warehouse_staff();

  SELECT * INTO v_entry FROM public.stock_entries WHERE id = p_entry_id FOR UPDATE;
  IF NOT FOUND OR v_entry.entry_type <> 'transfer' THEN
    RAISE EXCEPTION 'transfer not found';
  END IF;
  IF v_entry.status <> 'pending_approval' THEN
    RAISE EXCEPTION 'transfer not pending approval';
  END IF;
  IF v_entry.first_approver_id IS NULL THEN
    RAISE EXCEPTION 'first approver missing';
  END IF;
  IF auth.uid() IS NOT NULL AND auth.uid() = v_entry.first_approver_id THEN
    RAISE EXCEPTION 'second approver must be a different staff user';
  END IF;

  -- Mutate stock only after second signature
  FOR v_line IN
    SELECT * FROM public.stock_entry_lines WHERE stock_entry_id = p_entry_id
  LOOP
    v_val := v_line.valuation_method;
    SELECT unit_cost, currency INTO v_cost, v_currency
    FROM public.stock_levels
    WHERE stock_item_id = v_line.stock_item_id
      AND warehouse_id = v_entry.from_warehouse_id;

    IF v_val = 'FIFO' THEN
      PERFORM public._consume_fifo_batches(
        v_line.stock_item_id, v_entry.from_warehouse_id, v_line.qty_base
      );
    ELSE
      -- AVG: reduce oldest/any batch qty proportionally from available batches
      PERFORM public._consume_fifo_batches(
        v_line.stock_item_id, v_entry.from_warehouse_id, v_line.qty_base
      );
    END IF;

    PERFORM public._adjust_stock_level(
      v_line.stock_item_id, v_entry.from_warehouse_id, -v_line.qty_base,
      v_val, v_cost, COALESCE(v_currency, 'USD')
    );

    v_batch_code := public.next_series_value('BATCH-');
    INSERT INTO public.stock_batches (
      batch_code, stock_item_id, warehouse_id, valuation_method,
      unit_cost, currency, qty_on_hand
    )
    VALUES (
      v_batch_code, v_line.stock_item_id, v_entry.to_warehouse_id, v_val,
      COALESCE(v_cost, 0), COALESCE(v_currency, 'USD'), v_line.qty_base
    )
    RETURNING id INTO v_batch;

    UPDATE public.stock_entry_lines
    SET stock_batch_id = v_batch
    WHERE id = v_line.id;

    PERFORM public._adjust_stock_level(
      v_line.stock_item_id, v_entry.to_warehouse_id, v_line.qty_base,
      v_val, COALESCE(v_cost, 0), COALESCE(v_currency, 'USD')
    );
  END LOOP;

  UPDATE public.stock_entries
  SET
    status = 'posted',
    second_approver_id = auth.uid(),
    second_approved_at = now(),
    posted_at = now()
  WHERE id = p_entry_id;

  PERFORM public.emit_domain_event(
    'transfer_completed',
    'stock_entry:posted:' || p_entry_id::text,
    jsonb_build_object('stock_entry_id', p_entry_id)
  );

  SELECT is_quarantine INTO v_dest_quar
  FROM public.warehouses WHERE id = v_entry.to_warehouse_id;
  IF v_dest_quar THEN
    PERFORM public.emit_domain_event(
      'quarantine_received',
      'stock_entry:quar:' || p_entry_id::text,
      jsonb_build_object('stock_entry_id', p_entry_id)
    );
  END IF;

  RETURN p_entry_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.reject_stock_transfer(p_entry_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_first UUID;
BEGIN
  PERFORM public._require_warehouse_staff();
  SELECT first_approver_id INTO v_first
  FROM public.stock_entries
  WHERE id = p_entry_id AND entry_type = 'transfer' AND status = 'pending_approval'
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'pending transfer not found';
  END IF;
  IF auth.uid() IS NOT NULL AND auth.uid() = v_first THEN
    RAISE EXCEPTION 'second approver must be a different staff user';
  END IF;

  UPDATE public.stock_entries
  SET status = 'rejected',
      second_approver_id = auth.uid(),
      second_approved_at = now()
  WHERE id = p_entry_id;

  PERFORM public.emit_domain_event(
    'transfer_rejected',
    'stock_entry:rejected:' || p_entry_id::text,
    jsonb_build_object('stock_entry_id', p_entry_id)
  );

  RETURN p_entry_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Return → Quarantine only (never saleable MAIN without QUAR hop)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.post_return_to_quarantine(
  p_from_warehouse_id UUID,
  p_notes TEXT,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_quar UUID;
  v_is_quar BOOLEAN;
BEGIN
  PERFORM public._require_warehouse_staff();

  SELECT id INTO v_quar
  FROM public.warehouses
  WHERE is_quarantine = true AND is_active = true
  ORDER BY code
  LIMIT 1;

  IF v_quar IS NULL THEN
    RAISE EXCEPTION 'no quarantine warehouse configured';
  END IF;

  SELECT is_quarantine INTO v_is_quar
  FROM public.warehouses WHERE id = p_from_warehouse_id;

  -- If already in quarantine, still allow receipt-style adjust into QUAR via transfer path
  IF p_from_warehouse_id = v_quar THEN
    RAISE EXCEPTION 'source is already quarantine; use receipt/issue instead';
  END IF;

  -- Force destination quarantine — never MAIN/saleable
  RETURN public.create_stock_transfer(
    p_from_warehouse_id,
    v_quar,
    COALESCE(p_notes, 'Return to Quarantine'),
    p_lines
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- Naming prefixes for inventory
-- ---------------------------------------------------------------------------
INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('RCV-', 'Stock receipt', 5),
  ('TRF-', 'Stock transfer', 5),
  ('BATCH-', 'Stock batch', 6),
  ('ISS-', 'Stock issue', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.uoms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.item_uom_conversions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_entry_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_serials ENABLE ROW LEVEL SECURITY;

CREATE POLICY uoms_select_auth ON public.uoms FOR SELECT TO authenticated USING (true);
CREATE POLICY uoms_write_wh
  ON public.uoms FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY item_uom_select_auth
  ON public.item_uom_conversions FOR SELECT TO authenticated USING (true);
CREATE POLICY item_uom_write_wh
  ON public.item_uom_conversions FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY batches_staff
  ON public.stock_batches FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]));

CREATE POLICY stock_entries_staff
  ON public.stock_entries FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY stock_entry_lines_staff
  ON public.stock_entry_lines FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY serials_staff
  ON public.stock_serials FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

-- Grants
REVOKE ALL ON FUNCTION public.post_stock_receipt(UUID, TEXT, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_stock_transfer(UUID, UUID, TEXT, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.approve_stock_transfer(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.reject_stock_transfer(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_return_to_quarantine(UUID, TEXT, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.convert_to_base_uom(UUID, UUID, NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.build_qr_payload(TEXT, TEXT, public.valuation_method) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.post_stock_receipt(UUID, TEXT, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_stock_transfer(UUID, UUID, TEXT, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.approve_stock_transfer(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.reject_stock_transfer(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.post_return_to_quarantine(UUID, TEXT, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.convert_to_base_uom(UUID, UUID, NUMERIC) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.build_qr_payload(TEXT, TEXT, public.valuation_method) TO authenticated, service_role;
