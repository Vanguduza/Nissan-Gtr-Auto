-- Public home merch rails (featured / movers / newest) for anon storefront.
-- SECURITY DEFINER: returns only in-stock + priced retail rows (shop gate).
-- Does not open stock_levels (unit_cost) or raw inventory tables to anon.
-- NO ZIMRA / payroll tax / HTML5 QR.

CREATE OR REPLACE FUNCTION public.list_storefront_home_rails(p_limit INT DEFAULT 12)
RETURNS TABLE (
  rail TEXT,
  oem_part_number TEXT,
  catalog_title TEXT,
  qty_saleable NUMERIC,
  unit_price NUMERIC,
  currency public.currency_code,
  created_at TIMESTAMPTZ,
  reorder_point NUMERIC,
  discount_kind TEXT,
  discount_value NUMERIC,
  discount_description TEXT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_limit INT := LEAST(GREATEST(COALESCE(p_limit, 12), 1), 48);
BEGIN
  RETURN QUERY
  WITH saleable AS (
    SELECT
      sl.stock_item_id,
      SUM(sl.quantity)::numeric AS qty
    FROM public.stock_levels sl
    JOIN public.warehouses w ON w.id = sl.warehouse_id
    WHERE w.is_active AND NOT w.is_quarantine
    GROUP BY sl.stock_item_id
    HAVING SUM(sl.quantity) > 0
  ),
  retail AS (
    SELECT pli.stock_item_id, pli.unit_price, pl.currency
    FROM public.price_list_items pli
    JOIN public.price_lists pl ON pl.id = pli.price_list_id
    WHERE pl.is_active
      AND (pl.is_default OR pl.code = 'RETAIL')
      AND pli.unit_price > 0
  ),
  base AS (
    SELECT
      si.id,
      si.oem_part_number::text AS oem_part_number,
      COALESCE(NULLIF(trim(si.description), ''), si.oem_part_number)::text AS catalog_title,
      s.qty AS qty_saleable,
      r.unit_price,
      r.currency,
      si.created_at,
      si.reorder_point,
      COALESCE(m.discount_kind, 'none')::text AS discount_kind,
      COALESCE(m.discount_value, 0)::numeric AS discount_value,
      m.discount_description
    FROM public.stock_items si
    JOIN saleable s ON s.stock_item_id = si.id
    JOIN retail r ON r.stock_item_id = si.id
    LEFT JOIN public.stock_item_shop_merch m ON m.stock_item_id = si.id
  ),
  movers AS (
    SELECT * FROM base
    ORDER BY qty_saleable DESC, oem_part_number
    LIMIT v_limit
  ),
  newest AS (
    SELECT * FROM base
    ORDER BY created_at DESC NULLS LAST, oem_part_number
    LIMIT v_limit
  ),
  -- No curated featured flag yet: prefer discounted shop items, else movers proxy.
  featured AS (
    SELECT *
    FROM (
      SELECT
        b.*,
        CASE WHEN b.discount_kind <> 'none' THEN 0 ELSE 1 END AS feat_rank
      FROM base b
    ) x
    ORDER BY feat_rank, qty_saleable DESC, oem_part_number
    LIMIT v_limit
  )
  SELECT
    'featured'::text,
    f.oem_part_number,
    f.catalog_title,
    f.qty_saleable,
    f.unit_price,
    f.currency,
    f.created_at,
    f.reorder_point,
    f.discount_kind,
    f.discount_value,
    f.discount_description
  FROM featured f
  UNION ALL
  SELECT
    'movers'::text,
    m.oem_part_number,
    m.catalog_title,
    m.qty_saleable,
    m.unit_price,
    m.currency,
    m.created_at,
    m.reorder_point,
    m.discount_kind,
    m.discount_value,
    m.discount_description
  FROM movers m
  UNION ALL
  SELECT
    'newest'::text,
    n.oem_part_number,
    n.catalog_title,
    n.qty_saleable,
    n.unit_price,
    n.currency,
    n.created_at,
    n.reorder_point,
    n.discount_kind,
    n.discount_value,
    n.discount_description
  FROM newest n;
END;
$$;

REVOKE ALL ON FUNCTION public.list_storefront_home_rails(INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_storefront_home_rails(INT)
  TO anon, authenticated, service_role;

COMMENT ON FUNCTION public.list_storefront_home_rails(INT) IS
  'Anon-safe home rails: featured (discount-first else movers), movers, newest — shop gate only.';
