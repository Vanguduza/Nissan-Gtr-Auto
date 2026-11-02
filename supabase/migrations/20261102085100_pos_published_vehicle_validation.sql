-- POS selects vehicles from the published R2 master. The retired local hierarchy
-- is optional; requiring it rejected every published selection on a clean build.
CREATE OR REPLACE FUNCTION public._pos_catalog_vehicle_exists(
  p_model_slug text, p_chassis_code text, p_engine_code text
) RETURNS boolean LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  PERFORM public._require_sales_staff();
  RETURN EXISTS (
    SELECT 1 FROM public.catalog_models m
    JOIN public.catalog_variants v ON v.maker_slug=m.maker_slug AND v.model_slug=m.slug
    WHERE m.maker_slug='nissan' AND m.slug=btrim(p_model_slug)
      AND upper(btrim(v.chassis_code))=upper(btrim(p_chassis_code))
      AND upper(btrim(v.engine_code))=upper(btrim(p_engine_code))
  ) OR EXISTS (
    SELECT 1 FROM public.catalog_r2_vehicle_master v
    WHERE v.maker_slug='nissan'
      AND btrim(regexp_replace(lower(btrim(v.model)), '[^a-z0-9]+', '-', 'g'), '-')=btrim(p_model_slug)
      AND upper(btrim(v.chassis_code))=upper(btrim(p_chassis_code))
      AND upper(btrim(v.engine_code))=upper(btrim(p_engine_code))
  );
END;
$$;
REVOKE ALL ON FUNCTION public._pos_catalog_vehicle_exists(text,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public._pos_catalog_vehicle_exists(text,text,text) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.set_pos_cart_vehicle(p_cart_id uuid, p_model_slug text DEFAULT NULL::text, p_model_name text DEFAULT NULL::text, p_generation text DEFAULT NULL::text, p_chassis_code text DEFAULT NULL::text, p_engine_code text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE
  v_model_slug text := NULLIF(trim(p_model_slug), '');
  v_model_name text := NULLIF(trim(p_model_name), '');
  v_generation text := NULLIF(trim(p_generation), '');
  v_chassis text := NULLIF(trim(p_chassis_code), '');
  v_engine text := NULLIF(trim(p_engine_code), '');
  v_context jsonb;
BEGIN
  PERFORM public._require_sales_staff();
  IF NOT EXISTS (SELECT 1 FROM public.pos_carts WHERE id = p_cart_id AND status = 'open') THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;

  IF v_model_slug IS NOT NULL THEN
    IF v_chassis IS NULL OR v_engine IS NULL THEN
      RAISE EXCEPTION 'model, generation/chassis and engine are required together';
    END IF;
    IF NOT public._pos_catalog_vehicle_exists(v_model_slug, v_chassis, v_engine) THEN
      RAISE EXCEPTION 'selected Nissan model/generation/engine is not in the catalog';
    END IF;

    v_context := jsonb_build_object(
      'model_slug', v_model_slug,
      'model_name', v_model_name,
      'generation', v_generation,
      'chassis_code', v_chassis,
      'engine_code', v_engine
    );
  END IF;

  UPDATE public.pos_carts c
  SET vehicle_model_slug = v_model_slug,
      vehicle_model_name = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_model_name END,
      vehicle_generation = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_generation END,
      vehicle_chassis_code = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_chassis END,
      vehicle_engine_code = CASE WHEN v_model_slug IS NULL THEN NULL ELSE v_engine END,
      vehicle_contexts = CASE
        WHEN v_context IS NULL THEN c.vehicle_contexts
        WHEN EXISTS (
          SELECT 1 FROM jsonb_array_elements(COALESCE(c.vehicle_contexts, '[]'::jsonb)) e
          WHERE e->>'model_slug' = v_model_slug
            AND e->>'chassis_code' = v_chassis
            AND e->>'engine_code' = v_engine
        ) THEN c.vehicle_contexts
        ELSE COALESCE(c.vehicle_contexts, '[]'::jsonb) || jsonb_build_array(v_context)
      END,
      updated_at = now()
  WHERE c.id = p_cart_id;

  RETURN p_cart_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.search_pos_vehicle_spares(p_model_slug text, p_chassis_code text, p_engine_code text, p_query text, p_limit integer DEFAULT 50)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE
 SET search_path TO 'public'
AS $function$
DECLARE
  v_query text := trim(COALESCE(p_query, ''));
  v_limit integer := GREATEST(1, LEAST(COALESCE(p_limit, 50), 100));
  v_results jsonb;
BEGIN
  PERFORM public._require_sales_staff();
  IF NOT public._pos_catalog_vehicle_exists(p_model_slug, p_chassis_code, p_engine_code) THEN
    RAISE EXCEPTION 'vehicle selection is not present in Nissan catalog';
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.oem_part_number), '[]'::jsonb)
  INTO v_results
  FROM (
    SELECT DISTINCT ON (pf.oem_part_number)
      'part'::text AS type,
      pf.oem_part_number,
      si.description,
      pf.pnc_code,
      pc.category_name,
      pc.subcategory_name,
      pf.chassis_code,
      pf.engine_code
    FROM public.part_fitment pf
    LEFT JOIN public.stock_items si ON si.oem_part_number = pf.oem_part_number
    LEFT JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
    WHERE pf.chassis_code = trim(p_chassis_code)
      AND (pf.engine_code IS NULL OR pf.engine_code = trim(p_engine_code))
      AND (
        v_query = ''
        OR pf.oem_part_number ILIKE '%' || v_query || '%'
        OR COALESCE(si.description, '') ILIKE '%' || v_query || '%'
        OR COALESCE(pf.pnc_code, '') ILIKE '%' || v_query || '%'
        OR COALESCE(pc.category_name, '') ILIKE '%' || v_query || '%'
        OR COALESCE(pc.subcategory_name, '') ILIKE '%' || v_query || '%'
        OR COALESCE(pf.superseded_by, '') ILIKE '%' || v_query || '%'
      )
    ORDER BY pf.oem_part_number, (pf.engine_code = trim(p_engine_code)) DESC
    LIMIT v_limit
  ) t;

  RETURN jsonb_build_object(
    'mode', 'part',
    'query', v_query,
    'vehicle', jsonb_build_object(
      'model_slug', trim(p_model_slug),
      'chassis_code', trim(p_chassis_code),
      'engine_code', trim(p_engine_code)
    ),
    'results', v_results
  );
END;
$function$;
