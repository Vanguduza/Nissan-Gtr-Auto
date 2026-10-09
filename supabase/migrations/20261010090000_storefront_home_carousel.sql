-- Storefront home carousel curated from CRM → Product pages (owner decision 2026-10-10).
-- Replaces the "Find the right part" hero. Customer payload carries the stock id,
-- name, caption, photo and price only: never part numbers or catalogue data.

ALTER TABLE public.stock_item_shop_merch
  ADD COLUMN IF NOT EXISTS home_carousel_rank smallint,
  ADD COLUMN IF NOT EXISTS home_carousel_caption text;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'stock_item_shop_merch_home_carousel_rank_check'
  ) THEN
    ALTER TABLE public.stock_item_shop_merch
      ADD CONSTRAINT stock_item_shop_merch_home_carousel_rank_check
      CHECK (home_carousel_rank IS NULL OR home_carousel_rank BETWEEN 1 AND 12);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'stock_item_shop_merch_home_carousel_caption_check'
  ) THEN
    ALTER TABLE public.stock_item_shop_merch
      ADD CONSTRAINT stock_item_shop_merch_home_carousel_caption_check
      CHECK (home_carousel_caption IS NULL OR char_length(home_carousel_caption) <= 120);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS stock_item_shop_merch_home_carousel_idx
  ON public.stock_item_shop_merch (home_carousel_rank)
  WHERE home_carousel_rank IS NOT NULL;

-- CRM write: rank NULL removes the item from the carousel.
CREATE OR REPLACE FUNCTION public.set_storefront_home_carousel(
  p_stock_item_id uuid,
  p_rank smallint,
  p_caption text DEFAULT NULL
) RETURNS uuid
  LANGUAGE plpgsql SECURITY DEFINER
  SET search_path TO 'public'
AS $$
DECLARE
  v_caption text := NULLIF(trim(COALESCE(p_caption, '')), '');
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin or sales role required';
  END IF;
  IF p_stock_item_id IS NULL
     OR NOT EXISTS (SELECT 1 FROM public.stock_items WHERE id = p_stock_item_id) THEN
    RAISE EXCEPTION 'unknown stock_item_id';
  END IF;
  IF p_rank IS NOT NULL AND (p_rank < 1 OR p_rank > 12) THEN
    RAISE EXCEPTION 'carousel position must be 1..12';
  END IF;
  IF v_caption IS NOT NULL AND char_length(v_caption) > 120 THEN
    RAISE EXCEPTION 'caption must be 120 characters or fewer';
  END IF;

  INSERT INTO public.stock_item_shop_merch (
    stock_item_id, home_carousel_rank, home_carousel_caption, updated_at, updated_by
  ) VALUES (
    p_stock_item_id, p_rank, CASE WHEN p_rank IS NULL THEN NULL ELSE v_caption END, now(), auth.uid()
  )
  ON CONFLICT (stock_item_id) DO UPDATE
    SET home_carousel_rank = EXCLUDED.home_carousel_rank,
        home_carousel_caption = EXCLUDED.home_carousel_caption,
        updated_at = now(),
        updated_by = auth.uid();
  RETURN p_stock_item_id;
END;
$$;

REVOKE ALL ON FUNCTION public.set_storefront_home_carousel(uuid, smallint, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_storefront_home_carousel(uuid, smallint, text) TO authenticated;

-- Public read for the home page (signed out too). No part numbers in the payload.
CREATE OR REPLACE FUNCTION public.list_storefront_home_carousel(p_limit integer DEFAULT 8)
RETURNS TABLE (
  stock_item_id uuid,
  title text,
  caption text,
  image_path text,
  unit_price numeric,
  currency public.currency_code,
  qty_saleable numeric,
  discount_kind text,
  discount_value numeric,
  discount_description text
)
  LANGUAGE plpgsql STABLE SECURITY DEFINER
  SET search_path TO 'public'
AS $$
DECLARE
  v_limit int := LEAST(GREATEST(COALESCE(p_limit, 8), 1), 12);
BEGIN
  RETURN QUERY
  WITH saleable AS (
    SELECT sl.stock_item_id, SUM(sl.quantity)::numeric AS qty
    FROM public.stock_levels sl
    JOIN public.warehouses w ON w.id = sl.warehouse_id
    WHERE w.is_active AND NOT w.is_quarantine
    GROUP BY sl.stock_item_id
  ),
  retail AS (
    SELECT DISTINCT ON (pli.stock_item_id) pli.stock_item_id, pli.unit_price, pl.currency
    FROM public.price_list_items pli
    JOIN public.price_lists pl ON pl.id = pli.price_list_id
    WHERE pl.is_active AND (pl.is_default OR pl.code = 'RETAIL') AND pli.unit_price > 0
    ORDER BY pli.stock_item_id, pl.is_default DESC, pl.code
  ),
  primary_image AS (
    SELECT DISTINCT ON (i.stock_item_id) i.stock_item_id, i.storage_path
    FROM public.stock_item_images i
    ORDER BY i.stock_item_id, i.is_primary DESC, i.sort_order, i.created_at
  )
  SELECT
    si.id,
    COALESCE(NULLIF(trim(si.description), ''), 'Nissan part')::text,
    m.home_carousel_caption,
    img.storage_path,
    r.unit_price,
    r.currency,
    COALESCE(s.qty, 0),
    COALESCE(m.discount_kind, 'none')::text,
    COALESCE(m.discount_value, 0)::numeric,
    m.discount_description
  FROM public.stock_item_shop_merch m
  JOIN public.stock_items si ON si.id = m.stock_item_id
  JOIN retail r ON r.stock_item_id = si.id
  LEFT JOIN saleable s ON s.stock_item_id = si.id
  LEFT JOIN primary_image img ON img.stock_item_id = si.id
  WHERE m.home_carousel_rank IS NOT NULL
  ORDER BY m.home_carousel_rank, si.description
  LIMIT v_limit;
END;
$$;

REVOKE ALL ON FUNCTION public.list_storefront_home_carousel(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_storefront_home_carousel(integer) TO anon, authenticated;

-- Home rails: add the stock id so customer links never carry the part number,
-- and stop falling back to the part number as the display title.
DROP FUNCTION IF EXISTS public.list_storefront_home_rails(integer);
CREATE FUNCTION public.list_storefront_home_rails(p_limit integer DEFAULT 12)
RETURNS TABLE (
  rail text,
  oem_part_number text,
  catalog_title text,
  qty_saleable numeric,
  unit_price numeric,
  currency public.currency_code,
  created_at timestamp with time zone,
  reorder_point numeric,
  discount_kind text,
  discount_value numeric,
  discount_description text,
  stock_item_id uuid
)
  LANGUAGE plpgsql STABLE SECURITY DEFINER
  SET search_path TO 'public'
AS $$
DECLARE v_limit INT := LEAST(GREATEST(COALESCE(p_limit, 12), 1), 48);
BEGIN
  RETURN QUERY
  WITH saleable AS (
    SELECT sl.stock_item_id, SUM(sl.quantity)::numeric qty
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
    WHERE pl.is_active AND (pl.is_default OR pl.code = 'RETAIL') AND pli.unit_price > 0
  ),
  base AS (
    SELECT si.id, si.oem_part_number::text oem_part_number,
      COALESCE(NULLIF(trim(si.description), ''), 'Nissan part')::text catalog_title,
      s.qty qty_saleable, r.unit_price, r.currency, si.created_at, si.reorder_point,
      COALESCE(m.discount_kind, 'none')::text discount_kind,
      COALESCE(m.discount_value, 0)::numeric discount_value,
      m.discount_description
    FROM public.stock_items si
    JOIN saleable s ON s.stock_item_id = si.id
    JOIN retail r ON r.stock_item_id = si.id
    LEFT JOIN public.stock_item_shop_merch m ON m.stock_item_id = si.id
  ),
  movers AS (SELECT * FROM base ORDER BY qty_saleable DESC, oem_part_number LIMIT v_limit),
  newest AS (SELECT * FROM base ORDER BY created_at DESC NULLS LAST, oem_part_number LIMIT v_limit),
  featured AS (
    SELECT * FROM (
      SELECT b.*, CASE WHEN b.discount_kind <> 'none' THEN 0 ELSE 1 END feat_rank FROM base b
    ) x
    ORDER BY feat_rank, qty_saleable DESC, oem_part_number LIMIT v_limit
  )
  SELECT 'featured'::text, f.oem_part_number, f.catalog_title, f.qty_saleable, f.unit_price, f.currency, f.created_at, f.reorder_point, f.discount_kind, f.discount_value, f.discount_description, f.id FROM featured f
  UNION ALL SELECT 'movers'::text, m.oem_part_number, m.catalog_title, m.qty_saleable, m.unit_price, m.currency, m.created_at, m.reorder_point, m.discount_kind, m.discount_value, m.discount_description, m.id FROM movers m
  UNION ALL SELECT 'newest'::text, n.oem_part_number, n.catalog_title, n.qty_saleable, n.unit_price, n.currency, n.created_at, n.reorder_point, n.discount_kind, n.discount_value, n.discount_description, n.id FROM newest n;
END;
$$;

REVOKE ALL ON FUNCTION public.list_storefront_home_rails(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_storefront_home_rails(integer) TO anon, authenticated, service_role;
