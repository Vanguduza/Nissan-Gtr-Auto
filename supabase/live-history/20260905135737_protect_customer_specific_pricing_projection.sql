-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905135737 protect_customer_specific_pricing_projection).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.resolve_item_price(
  p_customer_id uuid,
  p_stock_item_id uuid
)
RETURNS TABLE(unit_price numeric, core_charge numeric, currency public.currency_code)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_list uuid;
  v_currency public.currency_code := 'USD';
  v_unit_price numeric;
  v_core_charge numeric;
  v_row_currency public.currency_code;
  v_allowed boolean := false;
BEGIN
  IF p_stock_item_id IS NULL THEN
    RAISE EXCEPTION 'stock_item_id required';
  END IF;

  IF p_customer_id IS NOT NULL THEN
    v_allowed := (
      auth.role()='service_role'
      OR public.has_staff_role(ARRAY['admin','sales']::public.staff_role[])
      OR EXISTS (
        SELECT 1 FROM public.customers c
        WHERE c.id=p_customer_id AND c.profile_id=auth.uid()
      )
    );
    IF NOT v_allowed THEN
      RAISE EXCEPTION 'not authorized to resolve customer-specific pricing';
    END IF;

    SELECT o.unit_price,COALESCE(o.core_charge,0),c.currency
    INTO v_unit_price,v_core_charge,v_row_currency
    FROM public.customer_price_overrides o
    JOIN public.customers c ON c.id=o.customer_id
    WHERE o.customer_id=p_customer_id AND o.stock_item_id=p_stock_item_id;

    IF FOUND THEN
      unit_price:=v_unit_price;
      core_charge:=v_core_charge;
      currency:=v_row_currency;
      RETURN NEXT;
      RETURN;
    END IF;

    SELECT c.price_list_id,c.currency
    INTO v_list,v_currency
    FROM public.customers c
    WHERE c.id=p_customer_id;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'customer not found';
    END IF;
  END IF;

  IF v_list IS NULL THEN
    SELECT pl.id,pl.currency
    INTO v_list,v_currency
    FROM public.price_lists pl
    WHERE pl.is_default AND pl.is_active
    ORDER BY pl.created_at ASC
    LIMIT 1;
  END IF;

  IF v_list IS NULL THEN
    SELECT pl.id,pl.currency
    INTO v_list,v_currency
    FROM public.price_lists pl
    WHERE pl.code='RETAIL' AND pl.is_active
    ORDER BY pl.created_at ASC
    LIMIT 1;
  END IF;

  IF v_list IS NULL THEN
    RAISE EXCEPTION 'no active public/default price list configured';
  END IF;

  SELECT pli.unit_price,COALESCE(pli.core_charge,0),pl.currency
  INTO v_unit_price,v_core_charge,v_row_currency
  FROM public.price_list_items pli
  JOIN public.price_lists pl ON pl.id=pli.price_list_id
  WHERE pli.price_list_id=v_list AND pli.stock_item_id=p_stock_item_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'no price for stock item % on price list',p_stock_item_id;
  END IF;

  unit_price:=v_unit_price;
  core_charge:=v_core_charge;
  currency:=v_row_currency;
  RETURN NEXT;
END;
$function$;

-- Preserve public default-price lookup compatibility, but customer-specific branches are now authorized in-function.
GRANT EXECUTE ON FUNCTION public.resolve_item_price(uuid,uuid) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.catalog_commerce_stock_for_oems(p_oems text[])
RETURNS TABLE(
  normalized_oem text,
  stock_item_id uuid,
  oem_part_number text,
  description text,
  saleable_qty numeric,
  reorder_point numeric,
  unit_price numeric,
  currency text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $function$
WITH wanted AS (
  SELECT DISTINCT upper(regexp_replace(x,'[^A-Z0-9]','','g')) normalized_oem
  FROM unnest(coalesce(p_oems,array[]::text[])) u(x)
  WHERE coalesce(trim(x),'')<>''
),
stock AS (
  SELECT
    si.id,
    si.oem_part_number::text,
    si.description,
    si.reorder_point,
    upper(regexp_replace(si.oem_part_number::text,'[^A-Z0-9]','','g')) normalized_oem,
    coalesce(sum(CASE WHEN w.is_active AND NOT w.is_quarantine THEN sl.quantity ELSE 0 END),0) saleable_qty
  FROM public.stock_items si
  LEFT JOIN public.stock_levels sl ON sl.stock_item_id=si.id
  LEFT JOIN public.warehouses w ON w.id=sl.warehouse_id
  JOIN wanted x ON x.normalized_oem=upper(regexp_replace(si.oem_part_number::text,'[^A-Z0-9]','','g'))
  GROUP BY si.id,si.oem_part_number,si.description,si.reorder_point
)
SELECT
  s.normalized_oem,
  s.id,
  s.oem_part_number,
  s.description,
  s.saleable_qty,
  CASE
    WHEN auth.role()='service_role'
      OR public.has_staff_role(ARRAY['admin','warehouse','finance','sales']::public.staff_role[])
    THEN s.reorder_point
    ELSE NULL::numeric
  END AS reorder_point,
  pli.unit_price,
  pl.currency::text
FROM stock s
LEFT JOIN LATERAL (
  SELECT pli0.unit_price,pli0.price_list_id
  FROM public.price_list_items pli0
  JOIN public.price_lists pl0 ON pl0.id=pli0.price_list_id
  WHERE pli0.stock_item_id=s.id AND pl0.is_active=true
  ORDER BY pl0.is_default DESC,(pl0.code='RETAIL') DESC,pl0.created_at ASC
  LIMIT 1
) pli ON true
LEFT JOIN public.price_lists pl ON pl.id=pli.price_list_id;
$function$;

GRANT EXECUTE ON FUNCTION public.catalog_commerce_stock_for_oems(text[]) TO anon, authenticated, service_role;

-- Internal stable helpers expose operational structure, not public product data.
REVOKE EXECUTE ON FUNCTION public._driver_open_job_count(uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._finance_req_approver_on_reporting_line(uuid,uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._loyalty_money_value(numeric) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.gl_account_for_payment_tender(public.payment_tender) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.is_period_locked(date) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public._driver_open_job_count(uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._finance_req_approver_on_reporting_line(uuid,uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._loyalty_money_value(numeric) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.gl_account_for_payment_tender(public.payment_tender) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.is_period_locked(date) TO authenticated, service_role;
