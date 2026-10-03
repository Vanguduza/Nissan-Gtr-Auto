-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908042118 pos_vehicle_context_reconcile).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Replacement-project reconciliation for the skipped 20260907090000 vehicle migration.
-- Restore prerequisites while preserving the newer multi-vehicle functions from 20260907130000.
ALTER TABLE public.pos_carts
  ADD COLUMN IF NOT EXISTS vehicle_model_slug TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_model_name TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_generation TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_chassis_code TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_engine_code TEXT;

ALTER TABLE public.pos_quotations
  ADD COLUMN IF NOT EXISTS vehicle_model_slug TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_model_name TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_generation TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_chassis_code TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_engine_code TEXT;

ALTER TABLE public.sales_invoices
  ADD COLUMN IF NOT EXISTS vehicle_model_slug TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_model_name TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_generation TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_chassis_code TEXT,
  ADD COLUMN IF NOT EXISTS vehicle_engine_code TEXT;

CREATE OR REPLACE FUNCTION public.search_pos_vehicle_spares(
  p_model_slug text,
  p_chassis_code text,
  p_engine_code text,
  p_query text,
  p_limit integer DEFAULT 50
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_query text := trim(COALESCE(p_query, ''));
  v_limit integer := GREATEST(1, LEAST(COALESCE(p_limit, 50), 100));
  v_results jsonb;
BEGIN
  PERFORM public._require_sales_staff();
  IF NOT EXISTS (
    SELECT 1 FROM public.catalog_variants v
    WHERE v.maker_slug = 'nissan'
      AND v.model_slug = trim(p_model_slug)
      AND v.chassis_code = trim(p_chassis_code)
      AND v.engine_code = trim(p_engine_code)
  ) THEN
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
$$;
REVOKE ALL ON FUNCTION public.search_pos_vehicle_spares(text,text,text,text,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.search_pos_vehicle_spares(text,text,text,text,integer) TO authenticated, service_role;
COMMENT ON FUNCTION public.search_pos_vehicle_spares IS
  'Fitment-scoped POS spare search for selected Nissan model/chassis/engine.';

DROP TRIGGER IF EXISTS pos_quotations_vehicle_snapshot ON public.pos_quotations;
CREATE TRIGGER pos_quotations_vehicle_snapshot
BEFORE INSERT OR UPDATE ON public.pos_quotations
FOR EACH ROW EXECUTE FUNCTION public._sync_pos_quotation_vehicle_from_cart();

DROP TRIGGER IF EXISTS pos_quotations_converted_cart_vehicle ON public.pos_quotations;
CREATE TRIGGER pos_quotations_converted_cart_vehicle
AFTER UPDATE OF converted_cart_id ON public.pos_quotations
FOR EACH ROW EXECUTE FUNCTION public._sync_converted_cart_vehicle_from_quote();

DROP TRIGGER IF EXISTS sales_invoices_vehicle_snapshot ON public.sales_invoices;
CREATE TRIGGER sales_invoices_vehicle_snapshot
BEFORE INSERT OR UPDATE ON public.sales_invoices
FOR EACH ROW EXECUTE FUNCTION public._sync_sales_invoice_vehicle_from_cart();

DROP FUNCTION IF EXISTS public.list_pos_recent_invoices(text, integer);
CREATE FUNCTION public.list_pos_recent_invoices(
  p_query text DEFAULT NULL,
  p_limit integer DEFAULT 50
)
RETURNS TABLE (
  id uuid,
  document_number text,
  customer_id uuid,
  customer_name text,
  total numeric,
  currency public.currency_code,
  posted_at timestamptz,
  vehicle_model_name text,
  vehicle_generation text,
  vehicle_chassis_code text,
  vehicle_engine_code text
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_query text := NULLIF(trim(p_query), '');
BEGIN
  PERFORM public._require_sales_staff();
  RETURN QUERY
  SELECT
    inv.id,
    inv.document_number,
    inv.customer_id,
    c.display_name,
    inv.total,
    inv.currency,
    COALESCE(inv.posted_at, inv.created_at),
    inv.vehicle_model_name,
    inv.vehicle_generation,
    inv.vehicle_chassis_code,
    inv.vehicle_engine_code
  FROM public.sales_invoices inv
  LEFT JOIN public.customers c ON c.id = inv.customer_id
  WHERE inv.doc_type = 'invoice'
    AND inv.status = 'posted'
    AND (
      v_query IS NULL
      OR inv.document_number ILIKE '%' || v_query || '%'
      OR inv.id::text = v_query
      OR c.display_name ILIKE '%' || v_query || '%'
      OR c.email ILIKE '%' || v_query || '%'
      OR c.phone_e164 ILIKE '%' || v_query || '%'
      OR inv.vehicle_model_name ILIKE '%' || v_query || '%'
      OR inv.vehicle_chassis_code ILIKE '%' || v_query || '%'
      OR inv.vehicle_engine_code ILIKE '%' || v_query || '%'
    )
  ORDER BY COALESCE(inv.posted_at, inv.created_at) DESC
  LIMIT GREATEST(1, LEAST(p_limit, 200));
END;
$$;

REVOKE ALL ON FUNCTION public.list_pos_recent_invoices(text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_pos_recent_invoices(text, integer) TO authenticated, service_role;
COMMENT ON FUNCTION public.list_pos_recent_invoices IS
  'Operator POS posted invoice search enriched with immutable vehicle snapshot context.';
