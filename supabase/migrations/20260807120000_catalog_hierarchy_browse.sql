-- EPC hierarchy browse tables (Megazip-style drill-down) + browse RPCs
-- Complements search_catalog (search-first) with hierarchy-first navigation.

-- ---------------------------------------------------------------------------
-- Hierarchy tables
-- ---------------------------------------------------------------------------

CREATE TABLE public.catalog_makers (
  slug TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  source TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.catalog_models (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  maker_slug TEXT NOT NULL REFERENCES public.catalog_makers (slug) ON DELETE CASCADE,
  slug TEXT NOT NULL,
  display_name TEXT NOT NULL,
  body_type TEXT,
  sort_key TEXT NOT NULL,
  year_start INT,
  year_end INT,
  source_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (maker_slug, slug)
);

CREATE INDEX catalog_models_maker_sort_idx ON public.catalog_models (maker_slug, sort_key);

CREATE TABLE public.catalog_variants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  maker_slug TEXT NOT NULL REFERENCES public.catalog_makers (slug) ON DELETE CASCADE,
  model_slug TEXT NOT NULL,
  slug TEXT NOT NULL,
  chassis_code TEXT NOT NULL,
  frame TEXT,
  grade TEXT,
  sales_region TEXT,
  year_start INT,
  year_end INT,
  year_label TEXT,
  engine_code TEXT,
  megazip_data_id TEXT,
  source_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (maker_slug, model_slug, slug)
);

CREATE INDEX catalog_variants_chassis_idx ON public.catalog_variants (chassis_code);
CREATE INDEX catalog_variants_model_idx ON public.catalog_variants (maker_slug, model_slug);

CREATE TABLE public.catalog_sections (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  maker_slug TEXT NOT NULL,
  model_slug TEXT NOT NULL,
  variant_slug TEXT NOT NULL,
  slug TEXT NOT NULL,
  name TEXT NOT NULL,
  thumbnail_url TEXT,
  sort_order INT NOT NULL DEFAULT 0,
  assembly_group_id TEXT,
  source_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (maker_slug, model_slug, variant_slug, slug)
);

CREATE INDEX catalog_sections_variant_idx
  ON public.catalog_sections (maker_slug, model_slug, variant_slug, sort_order);

CREATE TABLE public.catalog_diagrams (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  maker_slug TEXT NOT NULL,
  model_slug TEXT NOT NULL,
  variant_slug TEXT NOT NULL,
  section_slug TEXT NOT NULL,
  slug TEXT NOT NULL,
  title TEXT NOT NULL,
  image_url TEXT,
  image_width INT,
  image_height INT,
  storage_path TEXT,
  source_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (maker_slug, model_slug, variant_slug, section_slug, slug)
);

CREATE INDEX catalog_diagrams_storage_idx ON public.catalog_diagrams (storage_path);

-- Additive PCdb / EPC path columns on existing taxonomy table
ALTER TABLE public.pnc_categories
  ADD COLUMN IF NOT EXISTS assembly_group_id TEXT,
  ADD COLUMN IF NOT EXISTS catalog_section_path TEXT,
  ADD COLUMN IF NOT EXISTS pcdb_part_type_id INT;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

ALTER TABLE public.catalog_makers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_models ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_variants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_diagrams ENABLE ROW LEVEL SECURITY;

CREATE POLICY catalog_makers_select_all
  ON public.catalog_makers FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_makers_staff_write
  ON public.catalog_makers FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY catalog_models_select_all
  ON public.catalog_models FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_models_staff_write
  ON public.catalog_models FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY catalog_variants_select_all
  ON public.catalog_variants FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_variants_staff_write
  ON public.catalog_variants FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY catalog_sections_select_all
  ON public.catalog_sections FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_sections_staff_write
  ON public.catalog_sections FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY catalog_diagrams_select_all
  ON public.catalog_diagrams FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_diagrams_staff_write
  ON public.catalog_diagrams FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

-- ---------------------------------------------------------------------------
-- Browse RPCs (hierarchy-first; search_catalog remains search-first)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.list_catalog_makers()
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.sort_order, t.name), '[]'::jsonb)
  FROM (
    SELECT m.slug, m.name, m.sort_order,
      (SELECT COUNT(*)::int FROM catalog_models cm WHERE cm.maker_slug = m.slug) AS model_count
    FROM catalog_makers m
  ) t;
$$;

CREATE OR REPLACE FUNCTION public.list_catalog_models(p_maker_slug text)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.sort_key), '[]'::jsonb)
  FROM (
    SELECT slug, display_name, body_type, sort_key, year_start, year_end, source_url
    FROM catalog_models
    WHERE maker_slug = p_maker_slug
  ) t;
$$;

CREATE OR REPLACE FUNCTION public.list_catalog_variants(p_maker_slug text, p_model_slug text)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.chassis_code, t.slug), '[]'::jsonb)
  FROM (
    SELECT slug, chassis_code, frame, grade, sales_region, year_label, engine_code, source_url
    FROM catalog_variants
    WHERE maker_slug = p_maker_slug AND model_slug = p_model_slug
  ) t;
$$;

CREATE OR REPLACE FUNCTION public.list_catalog_sections(
  p_maker_slug text,
  p_model_slug text,
  p_variant_slug text
)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.sort_order, t.name), '[]'::jsonb)
  FROM (
    SELECT slug, name, thumbnail_url, sort_order, assembly_group_id, source_url
    FROM catalog_sections
    WHERE maker_slug = p_maker_slug
      AND model_slug = p_model_slug
      AND variant_slug = p_variant_slug
  ) t;
$$;

CREATE OR REPLACE FUNCTION public.get_catalog_diagram(
  p_maker_slug text,
  p_model_slug text,
  p_variant_slug text,
  p_section_slug text
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_diagram catalog_diagrams%ROWTYPE;
  v_parts jsonb := '[]'::jsonb;
BEGIN
  SELECT * INTO v_diagram
  FROM catalog_diagrams
  WHERE maker_slug = p_maker_slug
    AND model_slug = p_model_slug
    AND variant_slug = p_variant_slug
    AND section_slug = p_section_slug
  ORDER BY slug
  LIMIT 1;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('diagram', null, 'hotspots', '[]'::jsonb, 'parts', '[]'::jsonb);
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.oem_part_number), '[]'::jsonb)
  INTO v_parts
  FROM (
    SELECT
      pf.oem_part_number,
      pf.pnc_code,
      pf.chassis_code,
      pf.engine_code,
      pf.bbox_x,
      pf.bbox_y,
      pf.bbox_width,
      pf.bbox_height,
      pf.diagram_path,
      pc.category_name,
      pc.subcategory_name,
      pc.pcdb_part_type_id,
      si.id AS stock_item_id,
      si.description AS stock_description
    FROM part_fitment pf
    LEFT JOIN pnc_categories pc ON pc.pnc_code = pf.pnc_code
    LEFT JOIN stock_items si ON si.oem_part_number = pf.oem_part_number
    WHERE pf.diagram_path = v_diagram.storage_path
    LIMIT 500
  ) t;

  RETURN jsonb_build_object(
    'diagram', jsonb_build_object(
      'slug', v_diagram.slug,
      'title', v_diagram.title,
      'storage_path', v_diagram.storage_path,
      'image_url', v_diagram.image_url,
      'width', v_diagram.image_width,
      'height', v_diagram.image_height
    ),
    'hotspots', (
      SELECT COALESCE(jsonb_agg(jsonb_build_object(
        'oem', pf.oem_part_number,
        'pnc_code', pf.pnc_code,
        'bbox_x', pf.bbox_x,
        'bbox_y', pf.bbox_y,
        'bbox_width', pf.bbox_width,
        'bbox_height', pf.bbox_height
      ) ORDER BY pf.oem_part_number), '[]'::jsonb)
      FROM part_fitment pf
      WHERE pf.diagram_path = v_diagram.storage_path
        AND pf.bbox_x IS NOT NULL
    ),
    'parts', v_parts
  );
END;
$$;

REVOKE ALL ON FUNCTION public.list_catalog_makers() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_catalog_models(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_catalog_variants(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_catalog_sections(text, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_catalog_diagram(text, text, text, text) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.list_catalog_makers() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_catalog_models(text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_catalog_variants(text, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_catalog_sections(text, text, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_catalog_diagram(text, text, text, text) TO authenticated, service_role;

COMMENT ON FUNCTION public.get_catalog_diagram IS
  'Hierarchy-first diagram + hotspots + parts; joins stock_items when stocked (search-second via search_catalog).';
