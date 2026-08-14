-- Relationship procurement + dual-warehouse master stock + PO fund release
-- Founder 2026-08-12: preferred suppliers (not RFQ-win); WH1 receive / WH2 storefloor;
-- finance auto-release on PO approve under requesting official (created_by).
-- Pattern vocabulary: InvenTree (MIT) — Postgres remains SoR.

-- ---------------------------------------------------------------------------
-- Warehouse role codes (WH1 receiving, WH2 storefloor)
-- ---------------------------------------------------------------------------
ALTER TABLE public.warehouses
  ADD COLUMN IF NOT EXISTS role_code TEXT
    CHECK (role_code IS NULL OR role_code IN ('WH1', 'WH2', 'QUARANTINE', 'OTHER'));

COMMENT ON COLUMN public.warehouses.role_code IS
  'WH1 = all supplier receipts; WH2 = storefloor/POS; set by ops.';

-- Ensure WH1 / WH2 rows (alias MAIN → WH1 when present)
DO $$
DECLARE
  v_main UUID;
  v_wh1 UUID;
  v_wh2 UUID;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN' LIMIT 1;
  SELECT id INTO v_wh1 FROM public.warehouses WHERE code = 'WH1' LIMIT 1;
  SELECT id INTO v_wh2 FROM public.warehouses WHERE code = 'WH2' LIMIT 1;

  IF v_wh1 IS NULL AND v_main IS NOT NULL THEN
    UPDATE public.warehouses
    SET role_code = 'WH1', name = COALESCE(name, 'Warehouse 1 — Receiving')
    WHERE id = v_main;
  ELSIF v_wh1 IS NULL THEN
    INSERT INTO public.warehouses (code, name, is_quarantine, is_active, role_code)
    VALUES ('WH1', 'Warehouse 1 — Receiving', false, true, 'WH1');
  ELSE
    UPDATE public.warehouses SET role_code = 'WH1' WHERE id = v_wh1;
  END IF;

  IF v_wh2 IS NULL THEN
    INSERT INTO public.warehouses (code, name, is_quarantine, is_active, role_code)
    VALUES ('WH2', 'Warehouse 2 — Storefloor', false, true, 'WH2')
    ON CONFLICT (code) DO UPDATE
      SET role_code = 'WH2', is_active = true;
  ELSE
    UPDATE public.warehouses SET role_code = 'WH2' WHERE id = v_wh2;
  END IF;

  -- Prefer MAIN role_code if still unset
  UPDATE public.warehouses SET role_code = 'WH1' WHERE code = 'MAIN' AND role_code IS NULL;
END $$;

-- ---------------------------------------------------------------------------
-- Preferred supplier roster (relationship SoR — not quotation award)
-- ---------------------------------------------------------------------------
ALTER TABLE public.suppliers
  ADD COLUMN IF NOT EXISTS is_preferred BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS relationship_notes TEXT,
  ADD COLUMN IF NOT EXISTS address_text TEXT,
  ADD COLUMN IF NOT EXISTS tax_id TEXT,
  ADD COLUMN IF NOT EXISTS payment_terms TEXT,
  ADD COLUMN IF NOT EXISTS product_categories TEXT[] NOT NULL DEFAULT '{}';

COMMENT ON COLUMN public.suppliers.is_preferred IS
  'Long-standing relationship roster for procurement POs. RFQ award is optional/legacy.';

CREATE TABLE IF NOT EXISTS public.supplier_preferred_skus (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  supplier_id UUID NOT NULL REFERENCES public.suppliers (id) ON DELETE CASCADE,
  stock_item_id UUID REFERENCES public.stock_items (id) ON DELETE CASCADE,
  oem_part_number VARCHAR(32),
  typical_lead_days INT,
  last_quoted_unit_cost NUMERIC(18, 4),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  notes TEXT,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT supplier_preferred_skus_item_or_oem CHECK (
    stock_item_id IS NOT NULL OR oem_part_number IS NOT NULL
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS supplier_preferred_skus_supplier_item_uidx
  ON public.supplier_preferred_skus (supplier_id, stock_item_id)
  WHERE stock_item_id IS NOT NULL AND is_active;

CREATE INDEX IF NOT EXISTS supplier_preferred_skus_supplier_idx
  ON public.supplier_preferred_skus (supplier_id)
  WHERE is_active;

ALTER TABLE public.supplier_preferred_skus ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS supplier_preferred_skus_staff ON public.supplier_preferred_skus;
CREATE POLICY supplier_preferred_skus_staff
  ON public.supplier_preferred_skus FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]));

-- ---------------------------------------------------------------------------
-- Finance fund release on PO approve
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.procurement_fund_releases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  purchase_order_id UUID NOT NULL REFERENCES public.purchase_orders (id) ON DELETE RESTRICT,
  requesting_official_id UUID REFERENCES auth.users (id),
  approved_by UUID REFERENCES auth.users (id),
  amount NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  status TEXT NOT NULL DEFAULT 'released'
    CHECK (status IN ('released', 'void', 'paid')),
  payment_entry_id UUID,
  notes TEXT,
  released_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (purchase_order_id)
);

CREATE INDEX IF NOT EXISTS procurement_fund_releases_official_idx
  ON public.procurement_fund_releases (requesting_official_id);

ALTER TABLE public.procurement_fund_releases ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS procurement_fund_releases_staff ON public.procurement_fund_releases;
CREATE POLICY procurement_fund_releases_staff
  ON public.procurement_fund_releases FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]));

ALTER TABLE public.purchase_orders
  ADD COLUMN IF NOT EXISTS funds_released_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS progress_step TEXT;

COMMENT ON COLUMN public.purchase_orders.funds_released_at IS
  'Set when approve_purchase_order creates procurement_fund_releases under created_by.';

-- PO line totals helper
CREATE OR REPLACE FUNCTION public._po_quoted_total(p_purchase_order_id UUID)
RETURNS NUMERIC
LANGUAGE sql
STABLE
SET search_path = public
AS $$
  SELECT COALESCE(SUM(qty_ordered * unit_price), 0)
  FROM public.purchase_order_lines
  WHERE purchase_order_id = p_purchase_order_id;
$$;

-- Wrap approve_purchase_order: after status=approved, release funds under requesting official
CREATE OR REPLACE FUNCTION public.approve_purchase_order(p_purchase_order_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_po public.purchase_orders%ROWTYPE;
  v_total NUMERIC;
  v_release UUID;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_finance();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_po
  FROM public.purchase_orders
  WHERE id = p_purchase_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'purchase order not found: %', p_purchase_order_id;
  END IF;
  IF v_po.status <> 'submitted' THEN
    RAISE EXCEPTION 'only submitted purchase orders can be approved (status=%)', v_po.status;
  END IF;

  UPDATE public.purchase_orders
  SET
    status = 'approved',
    approved_by = auth.uid(),
    approved_at = now(),
    rejected_by = NULL,
    rejected_at = NULL,
    rejection_reason = NULL,
    progress_step = 'approved',
    updated_at = now()
  WHERE id = p_purchase_order_id;

  v_total := public._po_quoted_total(p_purchase_order_id);

  INSERT INTO public.procurement_fund_releases (
    purchase_order_id,
    requesting_official_id,
    approved_by,
    amount,
    currency,
    status,
    notes
  )
  VALUES (
    p_purchase_order_id,
    v_po.created_by,
    auth.uid(),
    v_total,
    v_po.currency,
    'released',
    format('Auto fund release on PO approve for official %s', COALESCE(v_po.created_by::text, 'unknown'))
  )
  ON CONFLICT (purchase_order_id) DO NOTHING
  RETURNING id INTO v_release;

  UPDATE public.purchase_orders
  SET
    funds_released_at = now(),
    progress_step = 'funds_released',
    updated_at = now()
  WHERE id = p_purchase_order_id;

  PERFORM public.emit_domain_event(
    'po_approved',
    'purchase_order_approved:' || p_purchase_order_id::text,
    jsonb_build_object(
      'purchase_order_id', p_purchase_order_id,
      'document_number', v_po.document_number,
      'currency', v_po.currency,
      'supplier_id', v_po.supplier_id,
      'fund_release_id', v_release,
      'amount', v_total,
      'requesting_official_id', v_po.created_by
    ),
    auth.uid(),
    format(
      'GTR Auto: PO %s approved — funds released under requesting official',
      COALESCE(v_po.document_number, left(p_purchase_order_id::text, 8))
    )
  );

  PERFORM public.emit_domain_event(
    'procurement_funds_released',
    'procurement_fund_release:' || COALESCE(v_release::text, p_purchase_order_id::text),
    jsonb_build_object(
      'purchase_order_id', p_purchase_order_id,
      'fund_release_id', v_release,
      'requesting_official_id', v_po.created_by,
      'amount', v_total,
      'currency', v_po.currency
    ),
    auth.uid(),
    format('GTR Auto: procurement funds released for PO %s', COALESCE(v_po.document_number, left(p_purchase_order_id::text, 8)))
  );

  PERFORM public._procurement_end_rpc();
  RETURN p_purchase_order_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

REVOKE ALL ON FUNCTION public.approve_purchase_order(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.approve_purchase_order(UUID) TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Preferred supplier upsert / deactivate RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.upsert_preferred_supplier(
  p_code TEXT,
  p_name TEXT,
  p_email TEXT DEFAULT NULL,
  p_phone_e164 TEXT DEFAULT NULL,
  p_currency public.currency_code DEFAULT 'USD',
  p_notes TEXT DEFAULT NULL,
  p_address TEXT DEFAULT NULL,
  p_tax_id TEXT DEFAULT NULL,
  p_payment_terms TEXT DEFAULT NULL,
  p_categories TEXT[] DEFAULT '{}'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  INSERT INTO public.suppliers (
    code, name, email, phone_e164, default_currency, is_active, is_preferred,
    relationship_notes, address_text, tax_id, payment_terms, product_categories, updated_at
  )
  VALUES (
    upper(trim(p_code)), trim(p_name), p_email, p_phone_e164, p_currency, true, true,
    p_notes, p_address, p_tax_id, p_payment_terms, COALESCE(p_categories, '{}'), now()
  )
  ON CONFLICT (code) DO UPDATE
    SET
      name = EXCLUDED.name,
      email = EXCLUDED.email,
      phone_e164 = EXCLUDED.phone_e164,
      default_currency = EXCLUDED.default_currency,
      is_active = true,
      is_preferred = true,
      relationship_notes = EXCLUDED.relationship_notes,
      address_text = EXCLUDED.address_text,
      tax_id = EXCLUDED.tax_id,
      payment_terms = EXCLUDED.payment_terms,
      product_categories = EXCLUDED.product_categories,
      updated_at = now()
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.deactivate_preferred_supplier(p_supplier_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  UPDATE public.suppliers
  SET is_preferred = false, is_active = false, updated_at = now()
  WHERE id = p_supplier_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'supplier not found';
  END IF;
  RETURN p_supplier_id;
END;
$$;

REVOKE ALL ON FUNCTION public.upsert_preferred_supplier(TEXT, TEXT, TEXT, TEXT, public.currency_code, TEXT, TEXT, TEXT, TEXT, TEXT[]) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.deactivate_preferred_supplier(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.upsert_preferred_supplier(TEXT, TEXT, TEXT, TEXT, public.currency_code, TEXT, TEXT, TEXT, TEXT, TEXT[]) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.deactivate_preferred_supplier(UUID) TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Master stock: total + WH1 + WH2
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW public.v_master_stock AS
SELECT
  si.id AS stock_item_id,
  si.oem_part_number,
  si.description,
  COALESCE(SUM(sl.quantity), 0) AS qty_total,
  COALESCE(SUM(sl.quantity) FILTER (WHERE w.role_code = 'WH1' OR w.code IN ('WH1', 'MAIN')), 0) AS qty_wh1,
  COALESCE(SUM(sl.quantity) FILTER (WHERE w.role_code = 'WH2' OR w.code = 'WH2'), 0) AS qty_wh2
FROM public.stock_items si
LEFT JOIN public.stock_levels sl ON sl.stock_item_id = si.id
LEFT JOIN public.warehouses w ON w.id = sl.warehouse_id AND w.is_active
GROUP BY si.id, si.oem_part_number, si.description;

GRANT SELECT ON public.v_master_stock TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.list_master_stock(p_limit INT DEFAULT 200, p_query TEXT DEFAULT NULL)
RETURNS TABLE (
  stock_item_id UUID,
  oem_part_number VARCHAR,
  description TEXT,
  qty_total NUMERIC,
  qty_wh1 NUMERIC,
  qty_wh2 NUMERIC
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    m.stock_item_id,
    m.oem_part_number,
    m.description,
    m.qty_total,
    m.qty_wh1,
    m.qty_wh2
  FROM public.v_master_stock m
  WHERE
    public.has_staff_role(ARRAY['admin', 'warehouse', 'sales', 'finance']::public.staff_role[])
    AND (
      p_query IS NULL
      OR m.oem_part_number ILIKE '%' || p_query || '%'
      OR COALESCE(m.description, '') ILIKE '%' || p_query || '%'
    )
  ORDER BY m.oem_part_number
  LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 200), 1000));
$$;

REVOKE ALL ON FUNCTION public.list_master_stock(INT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_master_stock(INT, TEXT) TO authenticated, service_role;

-- Fast GRN helper: resolve OEM → stock_item_id
CREATE OR REPLACE FUNCTION public.resolve_stock_item_by_oem(p_oem TEXT)
RETURNS UUID
LANGUAGE sql
STABLE
SET search_path = public
AS $$
  SELECT id FROM public.stock_items
  WHERE oem_part_number = upper(trim(p_oem))
  LIMIT 1;
$$;

GRANT EXECUTE ON FUNCTION public.resolve_stock_item_by_oem(TEXT) TO authenticated, service_role;
