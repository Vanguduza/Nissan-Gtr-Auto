-- Phase 16 slice 1: Bin / location within warehouse + pick-path hints
-- Exclusions: no ZIMRA / payroll tax; Quarantine warehouse rules unchanged
-- Bins are master data (CRUD RPCs); not transactional Draft/Submit docs

-- ---------------------------------------------------------------------------
-- warehouse_bins
-- ---------------------------------------------------------------------------
CREATE TABLE public.warehouse_bins (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id) ON DELETE RESTRICT,
  code VARCHAR(64) NOT NULL,
  name TEXT NOT NULL,
  -- Lower seq = earlier on pick path (NULLS LAST in hint queries)
  pick_path_seq INTEGER NOT NULL DEFAULT 100 CHECK (pick_path_seq >= 0),
  aisle TEXT,
  rack TEXT,
  shelf TEXT,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (warehouse_id, code)
);

CREATE INDEX warehouse_bins_warehouse_idx
  ON public.warehouse_bins (warehouse_id, pick_path_seq, code);
CREATE INDEX warehouse_bins_active_idx
  ON public.warehouse_bins (warehouse_id) WHERE is_active;

COMMENT ON TABLE public.warehouse_bins IS
  'Shelf/bin locations within a warehouse. Bins on Quarantine warehouses are allowed; '
  'they do not bypass Quarantine transfer/return protocols.';

-- Preferred putaway / pick location (warehouse qty remains UNIQUE per item+warehouse)
ALTER TABLE public.stock_levels
  ADD COLUMN IF NOT EXISTS bin_id UUID REFERENCES public.warehouse_bins (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS stock_levels_bin_idx ON public.stock_levels (bin_id)
  WHERE bin_id IS NOT NULL;

-- Receipt / transfer line putaway hint
ALTER TABLE public.stock_entry_lines
  ADD COLUMN IF NOT EXISTS bin_id UUID REFERENCES public.warehouse_bins (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS stock_entry_lines_bin_idx ON public.stock_entry_lines (bin_id)
  WHERE bin_id IS NOT NULL;

-- Pick-list suggested bin (from stock_levels.bin_id at create time)
ALTER TABLE public.pick_list_lines
  ADD COLUMN IF NOT EXISTS suggested_bin_id UUID
    REFERENCES public.warehouse_bins (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS pick_list_lines_suggested_bin_idx
  ON public.pick_list_lines (suggested_bin_id)
  WHERE suggested_bin_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Bin ↔ warehouse consistency
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._assert_bin_in_warehouse(
  p_bin_id UUID,
  p_warehouse_id UUID
)
RETURNS void
LANGUAGE plpgsql
STABLE
SET search_path = public
AS $$
DECLARE
  v_wh UUID;
  v_active BOOLEAN;
BEGIN
  IF p_bin_id IS NULL THEN
    RETURN;
  END IF;
  IF p_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'warehouse required when assigning bin';
  END IF;

  SELECT warehouse_id, is_active INTO v_wh, v_active
  FROM public.warehouse_bins
  WHERE id = p_bin_id;

  IF v_wh IS NULL THEN
    RAISE EXCEPTION 'bin not found: %', p_bin_id;
  END IF;
  IF NOT v_active THEN
    RAISE EXCEPTION 'bin % is inactive', p_bin_id;
  END IF;
  IF v_wh <> p_warehouse_id THEN
    RAISE EXCEPTION 'bin % belongs to warehouse %, not %', p_bin_id, v_wh, p_warehouse_id;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_stock_level_bin()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  PERFORM public._assert_bin_in_warehouse(NEW.bin_id, NEW.warehouse_id);
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS stock_levels_bin_guard ON public.stock_levels;
CREATE TRIGGER stock_levels_bin_guard
  BEFORE INSERT OR UPDATE OF bin_id, warehouse_id ON public.stock_levels
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_stock_level_bin();

CREATE OR REPLACE FUNCTION public.guard_stock_entry_line_bin()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
  v_entry public.stock_entries%ROWTYPE;
  v_wh UUID;
BEGIN
  IF NEW.bin_id IS NULL THEN
    RETURN NEW;
  END IF;

  SELECT * INTO v_entry FROM public.stock_entries WHERE id = NEW.stock_entry_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'stock entry % not found for line bin check', NEW.stock_entry_id;
  END IF;

  -- Receipt → dest; issue → source; transfer → dest putaway (to_warehouse)
  IF v_entry.entry_type = 'receipt' THEN
    v_wh := v_entry.to_warehouse_id;
  ELSIF v_entry.entry_type = 'issue' THEN
    v_wh := v_entry.from_warehouse_id;
  ELSE
    v_wh := COALESCE(v_entry.to_warehouse_id, v_entry.from_warehouse_id);
  END IF;

  PERFORM public._assert_bin_in_warehouse(NEW.bin_id, v_wh);
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS stock_entry_lines_bin_guard ON public.stock_entry_lines;
CREATE TRIGGER stock_entry_lines_bin_guard
  BEFORE INSERT OR UPDATE OF bin_id, stock_entry_id ON public.stock_entry_lines
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_stock_entry_line_bin();

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.warehouse_bins ENABLE ROW LEVEL SECURITY;

CREATE POLICY warehouse_bins_staff_write
  ON public.warehouse_bins FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY warehouse_bins_select_authenticated
  ON public.warehouse_bins FOR SELECT TO authenticated
  USING (true);

GRANT SELECT ON TABLE public.warehouse_bins TO authenticated, service_role;
GRANT INSERT, UPDATE, DELETE ON TABLE public.warehouse_bins TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.warehouse_bins TO service_role;

-- ---------------------------------------------------------------------------
-- CRUD RPCs (master data — no Draft/Submit)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_warehouse_bin(
  p_warehouse_id UUID,
  p_code TEXT,
  p_name TEXT,
  p_pick_path_seq INTEGER DEFAULT 100,
  p_aisle TEXT DEFAULT NULL,
  p_rack TEXT DEFAULT NULL,
  p_shelf TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_active BOOLEAN;
BEGIN
  PERFORM public._require_warehouse_staff();

  SELECT is_active INTO v_active FROM public.warehouses WHERE id = p_warehouse_id;
  IF v_active IS NULL THEN
    RAISE EXCEPTION 'warehouse not found';
  END IF;
  IF NOT v_active THEN
    RAISE EXCEPTION 'warehouse is inactive';
  END IF;
  -- Quarantine warehouses may have bins; returns still must use post_return_to_quarantine

  IF p_code IS NULL OR btrim(p_code) = '' THEN
    RAISE EXCEPTION 'bin code required';
  END IF;
  IF p_name IS NULL OR btrim(p_name) = '' THEN
    RAISE EXCEPTION 'bin name required';
  END IF;

  INSERT INTO public.warehouse_bins (
    warehouse_id, code, name, pick_path_seq, aisle, rack, shelf, created_by
  )
  VALUES (
    p_warehouse_id,
    upper(btrim(p_code)),
    btrim(p_name),
    COALESCE(p_pick_path_seq, 100),
    NULLIF(btrim(p_aisle), ''),
    NULLIF(btrim(p_rack), ''),
    NULLIF(btrim(p_shelf), ''),
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.update_warehouse_bin(
  p_bin_id UUID,
  p_name TEXT DEFAULT NULL,
  p_pick_path_seq INTEGER DEFAULT NULL,
  p_aisle TEXT DEFAULT NULL,
  p_rack TEXT DEFAULT NULL,
  p_shelf TEXT DEFAULT NULL,
  p_is_active BOOLEAN DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._require_warehouse_staff();

  IF NOT EXISTS (SELECT 1 FROM public.warehouse_bins WHERE id = p_bin_id) THEN
    RAISE EXCEPTION 'bin not found';
  END IF;

  UPDATE public.warehouse_bins
  SET
    name = COALESCE(NULLIF(btrim(p_name), ''), name),
    pick_path_seq = COALESCE(p_pick_path_seq, pick_path_seq),
    aisle = CASE WHEN p_aisle IS NULL THEN aisle ELSE NULLIF(btrim(p_aisle), '') END,
    rack = CASE WHEN p_rack IS NULL THEN rack ELSE NULLIF(btrim(p_rack), '') END,
    shelf = CASE WHEN p_shelf IS NULL THEN shelf ELSE NULLIF(btrim(p_shelf), '') END,
    is_active = COALESCE(p_is_active, is_active),
    updated_at = now()
  WHERE id = p_bin_id;

  RETURN p_bin_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.deactivate_warehouse_bin(p_bin_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  RETURN public.update_warehouse_bin(p_bin_id, NULL, NULL, NULL, NULL, NULL, false);
END;
$$;

-- Assign / clear preferred bin on stock_levels (must match warehouse)
CREATE OR REPLACE FUNCTION public.set_stock_level_bin(
  p_stock_item_id UUID,
  p_warehouse_id UUID,
  p_bin_id UUID DEFAULT NULL
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
  PERFORM public._assert_bin_in_warehouse(p_bin_id, p_warehouse_id);

  UPDATE public.stock_levels
  SET bin_id = p_bin_id, updated_at = now()
  WHERE stock_item_id = p_stock_item_id AND warehouse_id = p_warehouse_id
  RETURNING id INTO v_id;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'stock level not found for item % warehouse %',
      p_stock_item_id, p_warehouse_id;
  END IF;

  RETURN v_id;
END;
$$;

-- Pick-path hints: preferred bins ordered by pick_path_seq for a warehouse
CREATE OR REPLACE FUNCTION public.get_pick_path_hints(
  p_warehouse_id UUID,
  p_stock_item_ids UUID[] DEFAULT NULL
)
RETURNS TABLE (
  stock_item_id UUID,
  oem_part_number VARCHAR(32),
  quantity NUMERIC,
  bin_id UUID,
  bin_code VARCHAR(64),
  bin_name TEXT,
  pick_path_seq INTEGER,
  aisle TEXT,
  rack TEXT,
  shelf TEXT
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    sl.stock_item_id,
    si.oem_part_number,
    sl.quantity,
    wb.id AS bin_id,
    wb.code AS bin_code,
    wb.name AS bin_name,
    wb.pick_path_seq,
    wb.aisle,
    wb.rack,
    wb.shelf
  FROM public.stock_levels sl
  JOIN public.stock_items si ON si.id = sl.stock_item_id
  LEFT JOIN public.warehouse_bins wb
    ON wb.id = sl.bin_id AND wb.is_active
  WHERE sl.warehouse_id = p_warehouse_id
    AND sl.quantity > 0
    AND (
      p_stock_item_ids IS NULL
      OR cardinality(p_stock_item_ids) = 0
      OR sl.stock_item_id = ANY (p_stock_item_ids)
    )
  ORDER BY
    wb.pick_path_seq NULLS LAST,
    wb.code NULLS LAST,
    si.oem_part_number;
$$;

-- ---------------------------------------------------------------------------
-- post_stock_receipt: optional line.bin_id (putaway + preferred stock_levels.bin)
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
  v_bin UUID;
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
    v_bin := NULLIF(v_line ->> 'bin_id', '')::uuid;
    v_qty_base := public.convert_to_base_uom(v_item, v_uom, v_qty);

    PERFORM public._assert_bin_in_warehouse(v_bin, p_to_warehouse_id);

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
      unit_cost, currency, valuation_method, stock_batch_id, bin_id
    )
    VALUES (
      v_entry, v_item, v_uom, v_qty, v_qty_base,
      v_cost, v_currency, v_val, v_batch, v_bin
    )
    RETURNING id INTO v_line_id;

    PERFORM public._adjust_stock_level(
      v_item, p_to_warehouse_id, v_qty_base, v_val, v_cost, v_currency
    );

    IF v_bin IS NOT NULL THEN
      UPDATE public.stock_levels
      SET bin_id = v_bin, updated_at = now()
      WHERE stock_item_id = v_item AND warehouse_id = p_to_warehouse_id;
    END IF;

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
-- create_pick_list: stamp suggested_bin_id from stock_levels.bin_id
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
REVOKE ALL ON FUNCTION public._assert_bin_in_warehouse(UUID, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_warehouse_bin(UUID, TEXT, TEXT, INTEGER, TEXT, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.update_warehouse_bin(UUID, TEXT, INTEGER, TEXT, TEXT, TEXT, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.deactivate_warehouse_bin(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_stock_level_bin(UUID, UUID, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_pick_path_hints(UUID, UUID[]) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public._assert_bin_in_warehouse(UUID, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_warehouse_bin(UUID, TEXT, TEXT, INTEGER, TEXT, TEXT, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_warehouse_bin(UUID, TEXT, INTEGER, TEXT, TEXT, TEXT, BOOLEAN)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.deactivate_warehouse_bin(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_stock_level_bin(UUID, UUID, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_pick_path_hints(UUID, UUID[]) TO authenticated, service_role;

-- post_stock_receipt / create_pick_list already granted; re-assert after replace
GRANT EXECUTE ON FUNCTION public.post_stock_receipt(UUID, TEXT, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_pick_list(UUID, JSONB) TO authenticated, service_role;
