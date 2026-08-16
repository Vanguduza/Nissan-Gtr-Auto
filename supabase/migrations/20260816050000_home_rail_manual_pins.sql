-- Manual home-rail pins (backup control) alongside algorithmic rails.
-- Pins take priority within each rail; algorithm fills remaining slots.
-- Shop gate unchanged: in-stock + priced. NO ZIMRA / payroll tax / HTML5 QR.

ALTER TABLE public.stock_item_shop_merch
  ADD COLUMN IF NOT EXISTS pin_featured BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS pin_movers BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS pin_newest BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS pin_sort SMALLINT NOT NULL DEFAULT 0
    CHECK (pin_sort >= 0 AND pin_sort < 1000);

COMMENT ON COLUMN public.stock_item_shop_merch.pin_featured IS
  'Staff pin to Featured rail (before algorithmic discount/qty fill).';
COMMENT ON COLUMN public.stock_item_shop_merch.pin_movers IS
  'Staff pin to Fast movers rail (before algorithmic qty fill).';
COMMENT ON COLUMN public.stock_item_shop_merch.pin_newest IS
  'Staff pin to Newest additions rail (before algorithmic created_at fill).';
COMMENT ON COLUMN public.stock_item_shop_merch.pin_sort IS
  'Lower sorts first among pinned items on a rail (0 = top).';

CREATE INDEX IF NOT EXISTS stock_item_shop_merch_pin_featured_idx
  ON public.stock_item_shop_merch (pin_sort, stock_item_id)
  WHERE pin_featured;
CREATE INDEX IF NOT EXISTS stock_item_shop_merch_pin_movers_idx
  ON public.stock_item_shop_merch (pin_sort, stock_item_id)
  WHERE pin_movers;
CREATE INDEX IF NOT EXISTS stock_item_shop_merch_pin_newest_idx
  ON public.stock_item_shop_merch (pin_sort, stock_item_id)
  WHERE pin_newest;

-- ---------------------------------------------------------------------------
-- Home rails: pins first, then algorithm
-- ---------------------------------------------------------------------------
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
      m.discount_description,
      COALESCE(m.pin_featured, false) AS pin_featured,
      COALESCE(m.pin_movers, false) AS pin_movers,
      COALESCE(m.pin_newest, false) AS pin_newest,
      COALESCE(m.pin_sort, 0)::int AS pin_sort
    FROM public.stock_items si
    JOIN saleable s ON s.stock_item_id = si.id
    JOIN retail r ON r.stock_item_id = si.id
    LEFT JOIN public.stock_item_shop_merch m ON m.stock_item_id = si.id
  ),
  featured_pinned AS (
    SELECT * FROM base
    WHERE pin_featured
    ORDER BY pin_sort ASC, qty_saleable DESC, oem_part_number
    LIMIT v_limit
  ),
  featured_algo AS (
    SELECT b.*
    FROM base b
    WHERE NOT EXISTS (SELECT 1 FROM featured_pinned p WHERE p.id = b.id)
    ORDER BY
      CASE WHEN b.discount_kind <> 'none' THEN 0 ELSE 1 END,
      b.qty_saleable DESC,
      b.oem_part_number
    LIMIT v_limit
  ),
  featured AS (
    SELECT * FROM (
      SELECT *, 0 AS src FROM featured_pinned
      UNION ALL
      SELECT *, 1 AS src FROM featured_algo
    ) x
    ORDER BY src, pin_sort ASC, qty_saleable DESC, oem_part_number
    LIMIT v_limit
  ),
  movers_pinned AS (
    SELECT * FROM base
    WHERE pin_movers
    ORDER BY pin_sort ASC, qty_saleable DESC, oem_part_number
    LIMIT v_limit
  ),
  movers_algo AS (
    SELECT b.*
    FROM base b
    WHERE NOT EXISTS (SELECT 1 FROM movers_pinned p WHERE p.id = b.id)
    ORDER BY b.qty_saleable DESC, b.oem_part_number
    LIMIT v_limit
  ),
  movers AS (
    SELECT * FROM (
      SELECT *, 0 AS src FROM movers_pinned
      UNION ALL
      SELECT *, 1 AS src FROM movers_algo
    ) x
    ORDER BY src, pin_sort ASC, qty_saleable DESC, oem_part_number
    LIMIT v_limit
  ),
  newest_pinned AS (
    SELECT * FROM base
    WHERE pin_newest
    ORDER BY pin_sort ASC, created_at DESC NULLS LAST, oem_part_number
    LIMIT v_limit
  ),
  newest_algo AS (
    SELECT b.*
    FROM base b
    WHERE NOT EXISTS (SELECT 1 FROM newest_pinned p WHERE p.id = b.id)
    ORDER BY b.created_at DESC NULLS LAST, b.oem_part_number
    LIMIT v_limit
  ),
  newest AS (
    SELECT * FROM (
      SELECT *, 0 AS src FROM newest_pinned
      UNION ALL
      SELECT *, 1 AS src FROM newest_algo
    ) x
    ORDER BY src, pin_sort ASC, created_at DESC NULLS LAST, oem_part_number
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

COMMENT ON FUNCTION public.list_storefront_home_rails(INT) IS
  'Anon-safe home rails: manual pins first, then algorithm (featured discount/qty, movers qty, newest created_at). Shop gate only.';

-- ---------------------------------------------------------------------------
-- Staff product pages: expose + upsert pins
-- ---------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.list_staff_product_pages(TEXT, INT);

CREATE OR REPLACE FUNCTION public.list_staff_product_pages(
  p_query TEXT DEFAULT NULL,
  p_limit INT DEFAULT 50
)
RETURNS TABLE (
  stock_item_id UUID,
  oem_part_number TEXT,
  catalog_title TEXT,
  unit_price NUMERIC,
  currency public.currency_code,
  qty_saleable NUMERIC,
  discount_kind TEXT,
  discount_value NUMERIC,
  discount_description TEXT,
  primary_image_path TEXT,
  image_count INT,
  pin_featured BOOLEAN,
  pin_movers BOOLEAN,
  pin_newest BOOLEAN,
  pin_sort SMALLINT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_limit INT := LEAST(GREATEST(COALESCE(p_limit, 50), 1), 200);
  v_q TEXT := NULLIF(trim(COALESCE(p_query, '')), '');
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]) THEN
    RAISE EXCEPTION 'staff role required';
  END IF;

  RETURN QUERY
  WITH saleable AS (
    SELECT
      sl.stock_item_id,
      SUM(sl.quantity)::numeric AS qty
    FROM public.stock_levels sl
    JOIN public.warehouses w ON w.id = sl.warehouse_id
    WHERE w.is_active AND NOT w.is_quarantine
    GROUP BY sl.stock_item_id
  ),
  retail AS (
    SELECT pli.stock_item_id, pli.unit_price, pl.currency
    FROM public.price_list_items pli
    JOIN public.price_lists pl ON pl.id = pli.price_list_id
    WHERE pl.is_active
      AND (pl.is_default OR pl.code = 'RETAIL')
  ),
  imgs AS (
    SELECT
      i.stock_item_id,
      COUNT(*)::int AS image_count,
      MAX(i.storage_path) FILTER (WHERE i.is_primary) AS primary_image_path
    FROM public.stock_item_images i
    GROUP BY i.stock_item_id
  )
  SELECT
    si.id,
    si.oem_part_number::text,
    COALESCE(NULLIF(trim(si.description), ''), si.oem_part_number)::text AS catalog_title,
    r.unit_price,
    COALESCE(r.currency, 'USD'::public.currency_code),
    COALESCE(s.qty, 0),
    COALESCE(m.discount_kind, 'none'),
    COALESCE(m.discount_value, 0),
    m.discount_description,
    imgs.primary_image_path,
    COALESCE(imgs.image_count, 0),
    COALESCE(m.pin_featured, false),
    COALESCE(m.pin_movers, false),
    COALESCE(m.pin_newest, false),
    COALESCE(m.pin_sort, 0)::smallint
  FROM public.stock_items si
  LEFT JOIN saleable s ON s.stock_item_id = si.id
  LEFT JOIN retail r ON r.stock_item_id = si.id
  LEFT JOIN public.stock_item_shop_merch m ON m.stock_item_id = si.id
  LEFT JOIN imgs ON imgs.stock_item_id = si.id
  WHERE
    v_q IS NULL
    OR si.oem_part_number ILIKE '%' || v_q || '%'
    OR COALESCE(si.description, '') ILIKE '%' || v_q || '%'
  ORDER BY si.oem_part_number
  LIMIT v_limit;
END;
$$;

REVOKE ALL ON FUNCTION public.list_staff_product_pages(TEXT, INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_staff_product_pages(TEXT, INT)
  TO authenticated, service_role;

DROP FUNCTION IF EXISTS public.upsert_staff_product_page(UUID, NUMERIC, TEXT, NUMERIC, TEXT);

CREATE OR REPLACE FUNCTION public.upsert_staff_product_page(
  p_stock_item_id UUID,
  p_unit_price NUMERIC,
  p_discount_kind TEXT DEFAULT 'none',
  p_discount_value NUMERIC DEFAULT 0,
  p_discount_description TEXT DEFAULT NULL,
  p_pin_featured BOOLEAN DEFAULT false,
  p_pin_movers BOOLEAN DEFAULT false,
  p_pin_newest BOOLEAN DEFAULT false,
  p_pin_sort SMALLINT DEFAULT 0
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_list UUID;
  v_kind TEXT := lower(trim(COALESCE(p_discount_kind, 'none')));
  v_value NUMERIC := COALESCE(p_discount_value, 0);
  v_desc TEXT := NULLIF(trim(COALESCE(p_discount_description, '')), '');
  v_sort SMALLINT := LEAST(GREATEST(COALESCE(p_pin_sort, 0), 0), 999);
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]) THEN
    RAISE EXCEPTION 'staff role required';
  END IF;
  IF p_stock_item_id IS NULL THEN
    RAISE EXCEPTION 'stock_item_id required';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.stock_items WHERE id = p_stock_item_id) THEN
    RAISE EXCEPTION 'unknown stock_item_id';
  END IF;
  IF p_unit_price IS NULL OR p_unit_price < 0 THEN
    RAISE EXCEPTION 'unit_price must be >= 0';
  END IF;
  IF v_kind NOT IN ('none', 'percent', 'amount') THEN
    RAISE EXCEPTION 'discount_kind must be none|percent|amount';
  END IF;
  IF v_kind = 'none' THEN
    v_value := 0;
    v_desc := NULL;
  ELSIF v_kind = 'percent' AND (v_value < 0 OR v_value > 100) THEN
    RAISE EXCEPTION 'percent discount must be 0..100';
  ELSIF v_kind = 'amount' AND v_value < 0 THEN
    RAISE EXCEPTION 'amount discount must be >= 0';
  END IF;
  IF v_kind <> 'none' AND v_desc IS NULL THEN
    RAISE EXCEPTION 'discount_description required when discount is set';
  END IF;

  SELECT id INTO v_list
  FROM public.price_lists
  WHERE is_active AND (is_default OR code = 'RETAIL')
  ORDER BY is_default DESC, code
  LIMIT 1;

  IF v_list IS NULL THEN
    RAISE EXCEPTION 'no active default/retail price list';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, p_stock_item_id, p_unit_price, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = EXCLUDED.unit_price;

  INSERT INTO public.stock_item_shop_merch (
    stock_item_id,
    discount_kind,
    discount_value,
    discount_description,
    pin_featured,
    pin_movers,
    pin_newest,
    pin_sort,
    updated_at,
    updated_by
  )
  VALUES (
    p_stock_item_id,
    v_kind,
    v_value,
    v_desc,
    COALESCE(p_pin_featured, false),
    COALESCE(p_pin_movers, false),
    COALESCE(p_pin_newest, false),
    v_sort,
    now(),
    auth.uid()
  )
  ON CONFLICT (stock_item_id) DO UPDATE
  SET
    discount_kind = EXCLUDED.discount_kind,
    discount_value = EXCLUDED.discount_value,
    discount_description = EXCLUDED.discount_description,
    pin_featured = EXCLUDED.pin_featured,
    pin_movers = EXCLUDED.pin_movers,
    pin_newest = EXCLUDED.pin_newest,
    pin_sort = EXCLUDED.pin_sort,
    updated_at = now(),
    updated_by = auth.uid();

  RETURN p_stock_item_id;
END;
$$;

REVOKE ALL ON FUNCTION public.upsert_staff_product_page(
  UUID, NUMERIC, TEXT, NUMERIC, TEXT, BOOLEAN, BOOLEAN, BOOLEAN, SMALLINT
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.upsert_staff_product_page(
  UUID, NUMERIC, TEXT, NUMERIC, TEXT, BOOLEAN, BOOLEAN, BOOLEAN, SMALLINT
) TO authenticated, service_role;

COMMENT ON FUNCTION public.list_staff_product_pages(TEXT, INT) IS
  'Staff product-pages index: catalog + price/discount/images + home-rail pins.';
COMMENT ON FUNCTION public.upsert_staff_product_page(
  UUID, NUMERIC, TEXT, NUMERIC, TEXT, BOOLEAN, BOOLEAN, BOOLEAN, SMALLINT
) IS
  'Staff upsert retail price, discount, and optional home-rail pins; does not mutate catalog title/fitment/OEM/diagram.';
