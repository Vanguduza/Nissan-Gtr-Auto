-- Megazip diagram companion parts + diagram_kind on catalog_diagrams

ALTER TABLE public.catalog_diagrams
  ADD COLUMN IF NOT EXISTS diagram_kind TEXT,
  ADD COLUMN IF NOT EXISTS hotspot_count INT;

CREATE TABLE public.catalog_diagram_parts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  maker_slug TEXT NOT NULL,
  model_slug TEXT NOT NULL,
  variant_slug TEXT NOT NULL,
  section_slug TEXT NOT NULL,
  diagram_slug TEXT NOT NULL,
  diagram_path TEXT,
  itemslist_id TEXT NOT NULL,
  callout_ref TEXT,
  oem_part_number TEXT NOT NULL,
  description TEXT,
  quantity TEXT,
  external_item_id TEXT,
  bbox_x DOUBLE PRECISION,
  bbox_y DOUBLE PRECISION,
  bbox_width DOUBLE PRECISION,
  bbox_height DOUBLE PRECISION,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (maker_slug, model_slug, variant_slug, section_slug, itemslist_id)
);

CREATE INDEX catalog_diagram_parts_diagram_idx
  ON public.catalog_diagram_parts (maker_slug, model_slug, variant_slug, section_slug);

CREATE INDEX catalog_diagram_parts_oem_idx
  ON public.catalog_diagram_parts (oem_part_number);

ALTER TABLE public.catalog_diagram_parts ENABLE ROW LEVEL SECURITY;

CREATE POLICY catalog_diagram_parts_select_all
  ON public.catalog_diagram_parts FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_diagram_parts_staff_write
  ON public.catalog_diagram_parts FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

-- Extend get_catalog_diagram with diagram_kind + companion table rows
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
  v_companion jsonb := '[]'::jsonb;
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
    RETURN jsonb_build_object(
      'diagram', null,
      'hotspots', '[]'::jsonb,
      'parts', '[]'::jsonb,
      'companion_parts', '[]'::jsonb
    );
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

  SELECT COALESCE(jsonb_agg(row_to_json(c)::jsonb ORDER BY c.callout_ref, c.oem_part_number), '[]'::jsonb)
  INTO v_companion
  FROM (
    SELECT
      itemslist_id,
      callout_ref,
      oem_part_number,
      description,
      quantity,
      external_item_id,
      bbox_x,
      bbox_y,
      bbox_width,
      bbox_height
    FROM catalog_diagram_parts
    WHERE maker_slug = p_maker_slug
      AND model_slug = p_model_slug
      AND variant_slug = p_variant_slug
      AND section_slug = p_section_slug
    LIMIT 500
  ) c;

  RETURN jsonb_build_object(
    'diagram', jsonb_build_object(
      'slug', v_diagram.slug,
      'title', v_diagram.title,
      'storage_path', v_diagram.storage_path,
      'image_url', v_diagram.image_url,
      'width', v_diagram.image_width,
      'height', v_diagram.image_height,
      'diagram_kind', v_diagram.diagram_kind,
      'hotspot_count', v_diagram.hotspot_count
    ),
    'hotspots', (
      SELECT COALESCE(jsonb_agg(jsonb_build_object(
        'oem', pf.oem_part_number,
        'pnc_code', pf.pnc_code,
        'itemslist_id', cp.itemslist_id,
        'callout_ref', cp.callout_ref,
        'bbox_x', pf.bbox_x,
        'bbox_y', pf.bbox_y,
        'bbox_width', pf.bbox_width,
        'bbox_height', pf.bbox_height
      ) ORDER BY pf.oem_part_number), '[]'::jsonb)
      FROM part_fitment pf
      LEFT JOIN catalog_diagram_parts cp
        ON cp.diagram_path = pf.diagram_path
        AND cp.oem_part_number = pf.oem_part_number
      WHERE pf.diagram_path = v_diagram.storage_path
        AND pf.bbox_x IS NOT NULL
    ),
    'parts', v_parts,
    'companion_parts', v_companion
  );
END;
$$;

COMMENT ON TABLE public.catalog_diagram_parts IS
  'Megazip HTML items-list companion rows (ref, OEM, qty) linked to diagram hotspots via itemslist_id.';
COMMENT ON FUNCTION public.get_catalog_diagram IS
  'Hierarchy-first diagram + hotspots + fitment parts + HTML companion table; joins stock_items when stocked.';
