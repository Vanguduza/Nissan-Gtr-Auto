-- Customer-safe popular-parts read model derived from real posted sales.
-- This intentionally does NOT expose staff/operator pins, customer identity, invoice ids, or staff controls.
CREATE OR REPLACE FUNCTION public.list_customer_popular_spares(
  p_days integer DEFAULT 90,
  p_limit integer DEFAULT 8
)
RETURNS TABLE (
  stock_item_id uuid,
  oem_part_number text,
  description text,
  saleable_qty numeric,
  unit_price numeric,
  image_path text
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  WITH sold AS (
    SELECT sil.stock_item_id, SUM(sil.qty) AS units_sold
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
    COALESCE(stock.saleable_qty, 0)::numeric,
    price.unit_price,
    image.storage_path
  FROM sold
  JOIN public.stock_items si ON si.id = sold.stock_item_id
  LEFT JOIN LATERAL (
    SELECT SUM(sl.quantity)::numeric AS saleable_qty
    FROM public.stock_levels sl
    JOIN public.warehouses w ON w.id = sl.warehouse_id
    WHERE sl.stock_item_id = si.id AND w.is_active AND NOT w.is_quarantine
  ) stock ON true
  LEFT JOIN LATERAL (
    SELECT pli.unit_price
    FROM public.price_lists pl
    JOIN public.price_list_items pli ON pli.price_list_id = pl.id
    WHERE pl.is_default AND pl.is_active AND pl.currency = 'USD'::public.currency_code
      AND pli.stock_item_id = si.id
    ORDER BY pl.created_at DESC LIMIT 1
  ) price ON true
  LEFT JOIN LATERAL (
    SELECT sii.storage_path
    FROM public.stock_item_images sii
    WHERE sii.stock_item_id = si.id
    ORDER BY sii.is_primary DESC, sii.sort_order ASC, sii.created_at ASC
    LIMIT 1
  ) image ON true
  WHERE COALESCE(stock.saleable_qty, 0) > 0 AND price.unit_price IS NOT NULL
  ORDER BY sold.units_sold DESC, si.oem_part_number
  LIMIT GREATEST(1, LEAST(p_limit, 24));
$$;
REVOKE ALL ON FUNCTION public.list_customer_popular_spares(integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_customer_popular_spares(integer,integer) TO anon, authenticated, service_role;
COMMENT ON FUNCTION public.list_customer_popular_spares IS
  'Public retail popular-parts rail ranked by aggregate posted sales; returns only saleable stock, USD retail price, and product image path.';
