-- Scrub vendor name "megazip" from catalog SoR (columns + stored values).
-- Pipeline-local crawl dirs may still use the vendor name; Postgres must not.

-- ---------------------------------------------------------------------------
-- Rename vendor-prefixed columns
-- ---------------------------------------------------------------------------

ALTER TABLE public.catalog_variants
  RENAME COLUMN megazip_data_id TO external_data_id;

ALTER TABLE public.catalog_diagram_parts
  RENAME COLUMN megazip_item_id TO external_item_id;

-- ---------------------------------------------------------------------------
-- Scrub stored text / paths / URLs
-- ---------------------------------------------------------------------------

UPDATE public.catalog_makers
SET source = 'epc'
WHERE source ILIKE '%megazip%';

UPDATE public.catalog_models
SET source_url = NULL
WHERE source_url ILIKE '%megazip%';

UPDATE public.catalog_variants
SET source_url = NULL
WHERE source_url ILIKE '%megazip%';

UPDATE public.catalog_sections
SET source_url = NULL
WHERE source_url ILIKE '%megazip%';

UPDATE public.catalog_diagrams
SET
  storage_path = CASE
    WHEN storage_path ILIKE 'megazip/%'
      THEN 'epc/' || substr(storage_path, length('megazip/') + 1)
    ELSE storage_path
  END,
  source_url = CASE WHEN source_url ILIKE '%megazip%' THEN NULL ELSE source_url END,
  image_url = CASE WHEN image_url ILIKE '%megazip%' THEN NULL ELSE image_url END
WHERE storage_path ILIKE '%megazip%'
   OR source_url ILIKE '%megazip%'
   OR image_url ILIKE '%megazip%';

UPDATE public.catalog_diagram_parts
SET diagram_path = 'epc/' || substr(diagram_path, length('megazip/') + 1)
WHERE diagram_path ILIKE 'megazip/%';

UPDATE public.part_fitment
SET diagram_path = 'epc/' || substr(diagram_path, length('megazip/') + 1)
WHERE diagram_path ILIKE 'megazip/%';

-- ---------------------------------------------------------------------------
-- RPCs: stop returning vendor URLs; use renamed external_item_id
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.list_catalog_models(p_maker_slug text)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.sort_key), '[]'::jsonb)
  FROM (
    SELECT slug, display_name, body_type, sort_key, year_start, year_end
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
    SELECT slug, chassis_code, frame, grade, sales_region, year_label, engine_code
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
    SELECT slug, name, thumbnail_url, sort_order, assembly_group_id
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
    RETURN jsonb_build_object('diagram', null, 'hotspots', '[]'::jsonb, 'parts', '[]'::jsonb);
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
  INTO v_parts
  FROM (
    SELECT
      pf.oem_part_number,
      pf.pnc_code,
      pf.bbox_x,
      pf.bbox_y,
      pf.bbox_width,
      pf.bbox_height,
      si.qty_on_hand,
      si.unit_price,
      si.currency
    FROM part_fitment pf
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
        'bbox_x', COALESCE(cp.bbox_x, pf.bbox_x),
        'bbox_y', COALESCE(cp.bbox_y, pf.bbox_y),
        'bbox_width', COALESCE(cp.bbox_width, pf.bbox_width),
        'bbox_height', COALESCE(cp.bbox_height, pf.bbox_height)
      ) ORDER BY pf.oem_part_number), '[]'::jsonb)
      FROM part_fitment pf
      LEFT JOIN catalog_diagram_parts cp
        ON cp.maker_slug = p_maker_slug
       AND cp.model_slug = p_model_slug
       AND cp.variant_slug = p_variant_slug
       AND cp.section_slug = p_section_slug
       AND cp.oem_part_number = pf.oem_part_number
      WHERE pf.diagram_path = v_diagram.storage_path
      LIMIT 500
    ),
    'parts', COALESCE(v_companion, v_parts)
  );
END;
$$;

COMMENT ON COLUMN public.catalog_variants.external_data_id IS
  'Upstream EPC variant/data id (vendor-neutral).';
COMMENT ON COLUMN public.catalog_diagram_parts.external_item_id IS
  'Upstream EPC line/item id (vendor-neutral).';
