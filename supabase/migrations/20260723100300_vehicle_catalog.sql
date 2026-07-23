-- Vehicle master + PNC + part fitment (catalog foundation)

CREATE TABLE public.vehicle_master (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vin_prefix VARCHAR(12),
  chassis_code VARCHAR(32) NOT NULL,
  engine_code VARCHAR(32),
  production_year INT,
  model_variant TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX vehicle_master_chassis_idx ON public.vehicle_master (chassis_code);
CREATE INDEX vehicle_master_vin_prefix_idx ON public.vehicle_master (vin_prefix);

CREATE TABLE public.pnc_categories (
  pnc_code VARCHAR(8) PRIMARY KEY,
  category_name TEXT NOT NULL,
  subcategory_name TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.part_fitment (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  oem_part_number VARCHAR(32) NOT NULL,
  pnc_code VARCHAR(8) REFERENCES public.pnc_categories (pnc_code),
  chassis_code VARCHAR(32),
  engine_code VARCHAR(32),
  superseded_by VARCHAR(32),
  bbox_x NUMERIC(10, 4),
  bbox_y NUMERIC(10, 4),
  bbox_width NUMERIC(10, 4),
  bbox_height NUMERIC(10, 4),
  diagram_path TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX part_fitment_oem_idx ON public.part_fitment (oem_part_number);
CREATE INDEX part_fitment_pnc_idx ON public.part_fitment (pnc_code);
CREATE INDEX part_fitment_chassis_idx ON public.part_fitment (chassis_code);

ALTER TABLE public.vehicle_master ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pnc_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.part_fitment ENABLE ROW LEVEL SECURITY;

CREATE POLICY vehicle_master_select_all
  ON public.vehicle_master FOR SELECT TO authenticated
  USING (true);

CREATE POLICY vehicle_master_staff_write
  ON public.vehicle_master FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY pnc_select_all
  ON public.pnc_categories FOR SELECT TO authenticated
  USING (true);

CREATE POLICY pnc_staff_write
  ON public.pnc_categories FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY part_fitment_select_all
  ON public.part_fitment FOR SELECT TO authenticated
  USING (true);

CREATE POLICY part_fitment_staff_write
  ON public.part_fitment FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));
