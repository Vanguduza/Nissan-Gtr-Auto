-- Canonical product-image authority for customer + operator apps.
-- Product photography is public merchandising data; writes remain service/staff controlled elsewhere.
CREATE TABLE IF NOT EXISTS public.stock_item_images (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stock_item_id UUID NOT NULL REFERENCES public.stock_items(id) ON DELETE CASCADE,
  storage_path TEXT NOT NULL,
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (stock_item_id, storage_path)
);

CREATE INDEX IF NOT EXISTS stock_item_images_item_order_idx
  ON public.stock_item_images (stock_item_id, is_primary DESC, sort_order, created_at);

ALTER TABLE public.stock_item_images ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS stock_item_images_public_read ON public.stock_item_images;
CREATE POLICY stock_item_images_public_read ON public.stock_item_images
FOR SELECT TO anon, authenticated USING (true);

INSERT INTO storage.buckets (id, name, public)
VALUES ('product-images', 'product-images', true)
ON CONFLICT (id) DO UPDATE SET public = true;

DROP FUNCTION IF EXISTS public.list_pos_popular_spares(integer, integer);
CREATE FUNCTION public.list_pos_popular_spares(
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
  currency public.currency_code,
  image_storage_path text
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
    sold.units_sold,
    COALESCE(stock.saleable_qty, 0)::numeric,
    price.unit_price,
    price.currency,
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
    SELECT pli.unit_price, pl.currency
    FROM public.price_lists pl
    JOIN public.price_list_items pli ON pli.price_list_id = pl.id
    WHERE pl.is_default AND pl.is_active AND pli.stock_item_id = si.id
    ORDER BY pl.created_at DESC
    LIMIT 1
  ) price ON true
  LEFT JOIN LATERAL (
    SELECT sii.storage_path
    FROM public.stock_item_images sii
    WHERE sii.stock_item_id = si.id
    ORDER BY sii.is_primary DESC, sii.sort_order, sii.created_at
    LIMIT 1
  ) image ON true
  ORDER BY sold.units_sold DESC, si.oem_part_number
  LIMIT GREATEST(1, LEAST(p_limit, 24));
END;
$$;

REVOKE ALL ON FUNCTION public.list_pos_popular_spares(integer, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_pos_popular_spares(integer, integer) TO authenticated, service_role;
COMMENT ON FUNCTION public.list_pos_popular_spares IS 'POS best-seller ranking with canonical stock_item_images/product-images primary thumbnail.';
