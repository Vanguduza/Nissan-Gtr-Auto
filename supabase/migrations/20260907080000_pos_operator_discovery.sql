-- POS operator discovery/read models for the locked 2026-09-07 tablet screen.
-- Real business data only: popular spares come from posted sales; returns search posted invoices.

CREATE OR REPLACE FUNCTION public.list_pos_popular_spares(
  p_days integer DEFAULT 90,
  p_limit integer DEFAULT 8
)
RETURNS TABLE (
  stock_item_id uuid,
  oem_part_number text,
  description text,
  units_sold numeric,
  saleable_qty numeric,
  unit_price numeric,
  currency public.currency_code
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
BEGIN
  PERFORM public._require_sales_staff();

  RETURN QUERY
  WITH sold AS (
    SELECT
      sil.stock_item_id,
      SUM(sil.qty) AS units_sold
    FROM public.sales_invoice_lines sil
    JOIN public.sales_invoices inv ON inv.id = sil.invoice_id
    WHERE inv.doc_type = 'invoice'
      AND inv.status = 'posted'
      AND COALESCE(inv.posted_at, inv.created_at) >= now() - make_interval(days => GREATEST(1, LEAST(p_days, 365)))
      AND NOT sil.is_core_charge
    GROUP BY sil.stock_item_id
  )
  SELECT
    si.id,
    si.oem_part_number::text,
    si.description,
    sold.units_sold,
    COALESCE(stock.saleable_qty, 0)::numeric,
    price.unit_price,
    price.currency
  FROM sold
  JOIN public.stock_items si ON si.id = sold.stock_item_id
  LEFT JOIN LATERAL (
    SELECT SUM(sl.quantity)::numeric AS saleable_qty
    FROM public.stock_levels sl
    JOIN public.warehouses w ON w.id = sl.warehouse_id
    WHERE sl.stock_item_id = si.id
      AND w.is_active
      AND NOT w.is_quarantine
  ) stock ON true
  LEFT JOIN LATERAL (
    SELECT pli.unit_price, pl.currency
    FROM public.price_lists pl
    JOIN public.price_list_items pli ON pli.price_list_id = pl.id
    WHERE pl.is_default
      AND pl.is_active
      AND pli.stock_item_id = si.id
    ORDER BY pl.created_at DESC
    LIMIT 1
  ) price ON true
  ORDER BY sold.units_sold DESC, si.oem_part_number
  LIMIT GREATEST(1, LEAST(p_limit, 24));
END;
$$;

CREATE OR REPLACE FUNCTION public.list_pos_recent_invoices(
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
  posted_at timestamptz
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
    COALESCE(inv.posted_at, inv.created_at)
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
    )
  ORDER BY COALESCE(inv.posted_at, inv.created_at) DESC
  LIMIT GREATEST(1, LEAST(p_limit, 200));
END;
$$;

REVOKE ALL ON FUNCTION public.list_pos_popular_spares(integer, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_pos_recent_invoices(text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_pos_popular_spares(integer, integer) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_pos_recent_invoices(text, integer) TO authenticated, service_role;

COMMENT ON FUNCTION public.list_pos_popular_spares IS
  'Operator POS popular spares ranked by posted invoice quantity; returns current retail price and saleable stock.';
COMMENT ON FUNCTION public.list_pos_recent_invoices IS
  'Operator POS recent posted invoice search for order history and manager-approved refund selection.';
