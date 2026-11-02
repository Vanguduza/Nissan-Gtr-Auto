-- Complete EPC section sync: preserve every diagram per section for tablet offline bundle.

CREATE OR REPLACE FUNCTION public.list_catalog_diagrams(
  p_maker_slug text,
  p_model_slug text,
  p_variant_slug text,
  p_section_slug text
)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.slug), '[]'::jsonb)
  FROM (
    SELECT slug, title, storage_path, image_url
    FROM public.catalog_diagrams
    WHERE maker_slug = p_maker_slug
      AND model_slug = p_model_slug
      AND variant_slug = p_variant_slug
      AND section_slug = p_section_slug
  ) t;
$$;

CREATE OR REPLACE FUNCTION public.get_catalog_diagram_by_slug(
  p_maker_slug text,
  p_model_slug text,
  p_variant_slug text,
  p_section_slug text,
  p_diagram_slug text
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_diagram public.catalog_diagrams%ROWTYPE;
  v_parts jsonb := '[]'::jsonb;
BEGIN
  SELECT * INTO v_diagram
  FROM public.catalog_diagrams
  WHERE maker_slug = p_maker_slug
    AND model_slug = p_model_slug
    AND variant_slug = p_variant_slug
    AND section_slug = p_section_slug
    AND slug = p_diagram_slug
  LIMIT 1;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('diagram', null, 'hotspots', '[]'::jsonb, 'parts', '[]'::jsonb);
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.oem_part_number), '[]'::jsonb)
  INTO v_parts
  FROM (
    SELECT
      pf.oem_part_number, pf.pnc_code, pf.chassis_code, pf.engine_code,
      pc.category_name, pc.subcategory_name,
      si.id AS stock_item_id, si.description AS stock_description
    FROM public.part_fitment pf
    LEFT JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
    LEFT JOIN public.stock_items si ON si.oem_part_number = pf.oem_part_number
    WHERE pf.diagram_path = v_diagram.storage_path
    LIMIT 1000
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
        'bbox_x', pf.bbox_x, 'bbox_y', pf.bbox_y,
        'bbox_width', pf.bbox_width, 'bbox_height', pf.bbox_height
      ) ORDER BY pf.oem_part_number), '[]'::jsonb)
      FROM public.part_fitment pf
      WHERE pf.diagram_path = v_diagram.storage_path
        AND pf.bbox_x IS NOT NULL
    ),
    'parts', v_parts
  );
END;
$$;

REVOKE ALL ON FUNCTION public.list_catalog_diagrams(text,text,text,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_catalog_diagram_by_slug(text,text,text,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_catalog_diagrams(text,text,text,text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_catalog_diagram_by_slug(text,text,text,text,text) TO authenticated, service_role;
