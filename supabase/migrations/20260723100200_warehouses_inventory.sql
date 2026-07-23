-- Warehouses + inventory (includes Quarantine)

CREATE TABLE public.warehouses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(32) NOT NULL UNIQUE,
  name TEXT NOT NULL,
  is_quarantine BOOLEAN NOT NULL DEFAULT false,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.stock_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  oem_part_number VARCHAR(32) NOT NULL,
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (oem_part_number)
);

CREATE TABLE public.stock_levels (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id) ON DELETE RESTRICT,
  quantity NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  valuation_method public.valuation_method NOT NULL DEFAULT 'FIFO',
  unit_cost NUMERIC(18, 4),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (stock_item_id, warehouse_id)
);

CREATE TABLE public.inventory_qr_codes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  oem_part_number VARCHAR(32) NOT NULL,
  batch_id VARCHAR(64) NOT NULL,
  valuation_method public.valuation_method NOT NULL DEFAULT 'FIFO',
  stock_item_id UUID REFERENCES public.stock_items (id),
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  printed_at TIMESTAMPTZ,
  UNIQUE (oem_part_number, batch_id)
);

CREATE INDEX stock_levels_warehouse_idx ON public.stock_levels (warehouse_id);
CREATE INDEX inventory_qr_oem_idx ON public.inventory_qr_codes (oem_part_number);

ALTER TABLE public.warehouses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_levels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_qr_codes ENABLE ROW LEVEL SECURITY;

-- Staff can manage warehouses/inventory; authenticated customers get read-only stock presence later via views
CREATE POLICY warehouses_staff_all
  ON public.warehouses FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]));

CREATE POLICY warehouses_select_authenticated
  ON public.warehouses FOR SELECT TO authenticated
  USING (true);

CREATE POLICY stock_items_staff_write
  ON public.stock_items FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY stock_items_select_authenticated
  ON public.stock_items FOR SELECT TO authenticated
  USING (true);

CREATE POLICY stock_levels_staff_write
  ON public.stock_levels FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]));

CREATE POLICY stock_levels_select_authenticated
  ON public.stock_levels FOR SELECT TO authenticated
  USING (true);

CREATE POLICY inventory_qr_staff_all
  ON public.inventory_qr_codes FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]));

CREATE POLICY inventory_qr_select_staff
  ON public.inventory_qr_codes FOR SELECT TO authenticated
  USING (public.is_staff());

