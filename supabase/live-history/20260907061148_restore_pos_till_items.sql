-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907061148 restore_pos_till_items).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.list_pos_till_items(
  p_warehouse_id UUID,
  p_source TEXT,
  p_in_stock_only BOOLEAN DEFAULT true,
  p_chassis_code TEXT DEFAULT NULL,
  p_engine_code TEXT DEFAULT NULL,
  p_category TEXT DEFAULT NULL,
  p_oems TEXT[] DEFAULT NULL,
  p_section_key TEXT DEFAULT NULL,
  p_limit INT DEFAULT 80,
  p_offset INT DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public AS $$
DECLARE
  v_list UUID; v_currency public.currency_code; v_source TEXT; v_limit INT; v_offset INT;
  v_chassis TEXT; v_engine TEXT; v_category TEXT; v_section TEXT; v_items JSONB;
BEGIN
  PERFORM public._require_payments_staff();
  IF p_warehouse_id IS NULL THEN RAISE EXCEPTION 'warehouse_id required'; END IF;
  IF NOT EXISTS(SELECT 1 FROM public.warehouses w WHERE w.id=p_warehouse_id AND w.is_active AND NOT w.is_quarantine) THEN RAISE EXCEPTION 'warehouse not found or not saleable'; END IF;
  v_source:=lower(btrim(COALESCE(p_source,'')));
  IF v_source NOT IN('shop_stock','oems','section') THEN RAISE EXCEPTION 'p_source must be shop_stock, oems, or section'; END IF;
  v_limit:=GREATEST(1,LEAST(COALESCE(p_limit,80),500)); v_offset:=GREATEST(0,COALESCE(p_offset,0));
  v_chassis:=NULLIF(upper(btrim(COALESCE(p_chassis_code,''))),''); v_engine:=NULLIF(upper(btrim(COALESCE(p_engine_code,''))),'');
  v_category:=NULLIF(btrim(COALESCE(p_category,'')),''); v_section:=NULLIF(btrim(COALESCE(p_section_key,'')),'');
  SELECT pl.id,pl.currency INTO v_list,v_currency FROM public.price_lists pl WHERE pl.is_default AND pl.is_active LIMIT 1;
  IF v_list IS NULL THEN SELECT pl.id,pl.currency INTO v_list,v_currency FROM public.price_lists pl WHERE pl.code='RETAIL' AND pl.is_active LIMIT 1; END IF;
  IF v_list IS NULL THEN RAISE EXCEPTION 'no active default/RETAIL price list'; END IF;
  IF v_source='oems' AND (p_oems IS NULL OR cardinality(p_oems)=0) THEN RAISE EXCEPTION 'p_oems required when p_source = oems'; END IF;
  IF v_source='section' AND v_section IS NULL THEN RAISE EXCEPTION 'p_section_key required when p_source = section'; END IF;

  WITH base AS (
    SELECT
      si.id AS stock_item_id, si.oem_part_number, si.description, si.base_uom_id AS uom_id,
      COALESCE(pli.unit_price,0)::numeric AS unit_price, COALESCE(pli.core_charge,0)::numeric AS core_charge,
      COALESCE(sl.quantity,0)::numeric AS saleable_qty, COALESCE(sl.currency,v_currency) AS currency, wb.code AS bin_code,
      (SELECT pf.pnc_code FROM public.part_fitment pf WHERE pf.oem_part_number=si.oem_part_number AND pf.pnc_code IS NOT NULL AND btrim(pf.pnc_code)<>'' ORDER BY pf.pnc_code LIMIT 1) AS pnc_code,
      (SELECT pc.category_name FROM public.part_fitment pf JOIN public.pnc_categories pc ON pc.pnc_code=pf.pnc_code WHERE pf.oem_part_number=si.oem_part_number ORDER BY pc.category_name NULLS LAST LIMIT 1) AS category_name,
      (SELECT pf.superseded_by FROM public.part_fitment pf WHERE pf.oem_part_number=si.oem_part_number AND pf.superseded_by IS NOT NULL AND btrim(pf.superseded_by)<>'' ORDER BY pf.superseded_by LIMIT 1) AS superseded_by,
      COALESCE((SELECT array_agg(DISTINCT c ORDER BY c) FROM (SELECT btrim(pf.chassis_code) c FROM public.part_fitment pf WHERE pf.oem_part_number=si.oem_part_number AND pf.chassis_code IS NOT NULL AND btrim(pf.chassis_code)<>'') s),'{}'::text[]) AS chassis_codes,
      COALESCE((SELECT array_agg(DISTINCT e ORDER BY e) FROM (SELECT btrim(pf.engine_code) e FROM public.part_fitment pf WHERE pf.oem_part_number=si.oem_part_number AND pf.engine_code IS NOT NULL AND btrim(pf.engine_code)<>'') s),'{}'::text[]) AS engine_codes,
      CASE WHEN v_source='oems' THEN array_position(ARRAY(SELECT upper(btrim(x)) FROM unnest(p_oems) x),upper(btrim(si.oem_part_number))) ELSE NULL END AS requested_order
    FROM public.stock_items si
    LEFT JOIN public.price_list_items pli ON pli.stock_item_id=si.id AND pli.price_list_id=v_list
    LEFT JOIN public.stock_levels sl ON sl.stock_item_id=si.id AND sl.warehouse_id=p_warehouse_id
    LEFT JOIN public.warehouse_bins wb ON wb.id=sl.bin_id AND wb.is_active
    WHERE si.base_uom_id IS NOT NULL
      AND (v_source<>'shop_stock' OR COALESCE(pli.unit_price,0)>=0)
      AND (NOT COALESCE(p_in_stock_only,true) OR COALESCE(sl.quantity,0)>0)
      AND (v_source<>'oems' OR upper(btrim(si.oem_part_number))=ANY(ARRAY(SELECT upper(btrim(x)) FROM unnest(p_oems) x)))
      AND (v_source<>'section' OR EXISTS(
        SELECT 1 FROM (
          SELECT pf.oem_part_number oem FROM public.part_fitment pf WHERE pf.pnc_code=v_section
          UNION SELECT cdp.oem_part_number FROM public.catalog_diagram_parts cdp WHERE cdp.section_slug=v_section
          UNION SELECT pf.oem_part_number FROM public.catalog_diagrams cd JOIN public.part_fitment pf ON pf.diagram_path=cd.storage_path WHERE cd.section_slug=v_section
        ) sec WHERE upper(btrim(sec.oem))=upper(btrim(si.oem_part_number))
      ))
      AND (v_category IS NULL OR EXISTS(SELECT 1 FROM public.part_fitment pf JOIN public.pnc_categories pc ON pc.pnc_code=pf.pnc_code WHERE pf.oem_part_number=si.oem_part_number AND lower(COALESCE(pc.category_name,'')) LIKE '%'||lower(v_category)||'%'))
      AND (v_chassis IS NULL OR NOT EXISTS(SELECT 1 FROM public.part_fitment pf WHERE pf.oem_part_number=si.oem_part_number AND pf.chassis_code IS NOT NULL AND btrim(pf.chassis_code)<>'') OR EXISTS(SELECT 1 FROM public.part_fitment pf WHERE pf.oem_part_number=si.oem_part_number AND upper(btrim(pf.chassis_code))=v_chassis AND (v_engine IS NULL OR pf.engine_code IS NULL OR btrim(pf.engine_code)='' OR upper(btrim(pf.engine_code))=v_engine)))
  ), page AS (
    SELECT * FROM base ORDER BY requested_order NULLS LAST,oem_part_number LIMIT v_limit OFFSET v_offset
  )
  SELECT COALESCE(jsonb_agg(to_jsonb(page)-'requested_order' ORDER BY requested_order NULLS LAST,oem_part_number),'[]'::jsonb) INTO v_items FROM page;

  RETURN jsonb_build_object('warehouse_id',p_warehouse_id,'source',v_source,'currency',v_currency,'price_list_id',v_list,'limit',v_limit,'offset',v_offset,'items',COALESCE(v_items,'[]'::jsonb));
END;$$;
COMMENT ON FUNCTION public.list_pos_till_items(UUID,TEXT,BOOLEAN,TEXT,TEXT,TEXT,TEXT[],TEXT,INT,INT) IS 'Staff TillItem grid SoR. p_source: shop_stock | oems | section. Saleable quantity comes only from stock_levels; returns pricing, bins and fitment metadata.';
REVOKE ALL ON FUNCTION public.list_pos_till_items(UUID,TEXT,BOOLEAN,TEXT,TEXT,TEXT,TEXT[],TEXT,INT,INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_pos_till_items(UUID,TEXT,BOOLEAN,TEXT,TEXT,TEXT,TEXT[],TEXT,INT,INT) TO authenticated,service_role;
