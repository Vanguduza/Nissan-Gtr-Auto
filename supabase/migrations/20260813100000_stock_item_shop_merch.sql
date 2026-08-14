-- Staff shop merch for PDP: price stays on price_list_items; discount + product images here.
-- Catalog title / fitment / OEM / EPC diagram remain pipeline-owned (not editable via these RPCs).
-- NO ZIMRA / payroll tax / HTML5 QR.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
CREATE TABLE public.stock_item_shop_merch (
  stock_item_id UUID PRIMARY KEY REFERENCES public.stock_items (id) ON DELETE CASCADE,
  discount_kind TEXT NOT NULL DEFAULT 'none'
    CHECK (discount_kind IN ('none', 'percent', 'amount')),
  discount_value NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (discount_value >= 0),
  discount_description TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by UUID REFERENCES auth.users (id),
  CONSTRAINT stock_item_shop_merch_percent_ok CHECK (
    discount_kind <> 'percent' OR discount_value <= 100
  ),
  CONSTRAINT stock_item_shop_merch_none_zero CHECK (
    discount_kind <> 'none' OR discount_value = 0
  )
);

COMMENT ON TABLE public.stock_item_shop_merch IS
  'Staff-owned PDP discount (kind/value/description). Price lives on price_list_items.';

CREATE TABLE public.stock_item_images (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE CASCADE,
  storage_path TEXT NOT NULL,
  is_primary BOOLEAN NOT NULL DEFAULT false,
  sort_order SMALLINT NOT NULL DEFAULT 0 CHECK (sort_order >= 0 AND sort_order < 20),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by UUID REFERENCES auth.users (id),
  CONSTRAINT stock_item_images_path_unique UNIQUE (stock_item_id, storage_path)
);

CREATE INDEX stock_item_images_item_idx
  ON public.stock_item_images (stock_item_id, sort_order);

CREATE UNIQUE INDEX stock_item_images_one_primary
  ON public.stock_item_images (stock_item_id)
  WHERE is_primary;

COMMENT ON TABLE public.stock_item_images IS
  'Staff product photos for PDP. Path relative to product-images bucket: {stock_item_id}/{file}.';

ALTER TABLE public.stock_item_shop_merch ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_item_images ENABLE ROW LEVEL SECURITY;

CREATE POLICY stock_item_shop_merch_select
  ON public.stock_item_shop_merch FOR SELECT TO authenticated
  USING (true);

CREATE POLICY stock_item_shop_merch_staff_write
  ON public.stock_item_shop_merch FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]));

CREATE POLICY stock_item_images_select
  ON public.stock_item_images FOR SELECT TO authenticated
  USING (true);

CREATE POLICY stock_item_images_staff_write
  ON public.stock_item_images FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]));

GRANT SELECT ON TABLE public.stock_item_shop_merch TO authenticated, service_role;
GRANT INSERT, UPDATE, DELETE ON TABLE public.stock_item_shop_merch TO authenticated, service_role;
GRANT SELECT ON TABLE public.stock_item_images TO authenticated, service_role;
GRANT INSERT, UPDATE, DELETE ON TABLE public.stock_item_images TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Storage bucket (public read for storefront PDP)
-- ---------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'product-images',
  'product-images',
  true,
  5242880,
  ARRAY['image/jpeg', 'image/jpg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE
SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

CREATE OR REPLACE FUNCTION public._stock_item_id_from_product_image_path(p_name TEXT)
RETURNS UUID
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_part TEXT;
BEGIN
  v_part := split_part(p_name, '/', 1);
  IF v_part IS NULL OR v_part = '' THEN
    RETURN NULL;
  END IF;
  BEGIN
    RETURN v_part::uuid;
  EXCEPTION WHEN invalid_text_representation THEN
    RETURN NULL;
  END;
END;
$$;

CREATE OR REPLACE FUNCTION public._can_write_product_image_object(p_name TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    auth.role() = 'service_role'
    OR (
      public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[])
      AND public._stock_item_id_from_product_image_path(p_name) IS NOT NULL
      AND EXISTS (
        SELECT 1 FROM public.stock_items si
        WHERE si.id = public._stock_item_id_from_product_image_path(p_name)
      )
    );
$$;

DROP POLICY IF EXISTS product_images_storage_select ON storage.objects;
CREATE POLICY product_images_storage_select
  ON storage.objects FOR SELECT
  TO public
  USING (bucket_id = 'product-images');

DROP POLICY IF EXISTS product_images_storage_insert ON storage.objects;
CREATE POLICY product_images_storage_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'product-images'
    AND public._can_write_product_image_object(name)
  );

DROP POLICY IF EXISTS product_images_storage_update ON storage.objects;
CREATE POLICY product_images_storage_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'product-images'
    AND public._can_write_product_image_object(name)
  )
  WITH CHECK (
    bucket_id = 'product-images'
    AND public._can_write_product_image_object(name)
  );

DROP POLICY IF EXISTS product_images_storage_delete ON storage.objects;
CREATE POLICY product_images_storage_delete
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'product-images'
    AND public._can_write_product_image_object(name)
  );

REVOKE ALL ON FUNCTION public._stock_item_id_from_product_image_path(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._can_write_product_image_object(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public._stock_item_id_from_product_image_path(TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._can_write_product_image_object(TEXT)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- RPCs
-- ---------------------------------------------------------------------------
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
  image_count INT
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
    COALESCE(imgs.image_count, 0)
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

CREATE OR REPLACE FUNCTION public.upsert_staff_product_page(
  p_stock_item_id UUID,
  p_unit_price NUMERIC,
  p_discount_kind TEXT DEFAULT 'none',
  p_discount_value NUMERIC DEFAULT 0,
  p_discount_description TEXT DEFAULT NULL
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
    stock_item_id, discount_kind, discount_value, discount_description, updated_at, updated_by
  )
  VALUES (
    p_stock_item_id, v_kind, v_value, v_desc, now(), auth.uid()
  )
  ON CONFLICT (stock_item_id) DO UPDATE
  SET
    discount_kind = EXCLUDED.discount_kind,
    discount_value = EXCLUDED.discount_value,
    discount_description = EXCLUDED.discount_description,
    updated_at = now(),
    updated_by = auth.uid();

  RETURN p_stock_item_id;
END;
$$;

REVOKE ALL ON FUNCTION public.upsert_staff_product_page(UUID, NUMERIC, TEXT, NUMERIC, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.upsert_staff_product_page(UUID, NUMERIC, TEXT, NUMERIC, TEXT)
  TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.register_stock_item_image(
  p_stock_item_id UUID,
  p_storage_path TEXT,
  p_is_primary BOOLEAN DEFAULT false,
  p_sort_order SMALLINT DEFAULT 0
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_path TEXT := trim(COALESCE(p_storage_path, ''));
  v_primary BOOLEAN := COALESCE(p_is_primary, false);
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]) THEN
    RAISE EXCEPTION 'staff role required';
  END IF;
  IF p_stock_item_id IS NULL OR v_path = '' THEN
    RAISE EXCEPTION 'stock_item_id and storage_path required';
  END IF;
  IF public._stock_item_id_from_product_image_path(v_path) IS DISTINCT FROM p_stock_item_id THEN
    RAISE EXCEPTION 'storage_path must start with {stock_item_id}/';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.stock_items WHERE id = p_stock_item_id) THEN
    RAISE EXCEPTION 'unknown stock_item_id';
  END IF;

  IF v_primary THEN
    UPDATE public.stock_item_images
    SET is_primary = false
    WHERE stock_item_id = p_stock_item_id AND is_primary;
  ELSIF NOT EXISTS (
    SELECT 1 FROM public.stock_item_images WHERE stock_item_id = p_stock_item_id
  ) THEN
    v_primary := true;
  END IF;

  INSERT INTO public.stock_item_images (
    stock_item_id, storage_path, is_primary, sort_order, created_by
  )
  VALUES (
    p_stock_item_id, v_path, v_primary, COALESCE(p_sort_order, 0), auth.uid()
  )
  ON CONFLICT (stock_item_id, storage_path) DO UPDATE
  SET
    is_primary = EXCLUDED.is_primary,
    sort_order = EXCLUDED.sort_order
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.register_stock_item_image(UUID, TEXT, BOOLEAN, SMALLINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.register_stock_item_image(UUID, TEXT, BOOLEAN, SMALLINT)
  TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.set_stock_item_primary_image(p_image_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_item UUID;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]) THEN
    RAISE EXCEPTION 'staff role required';
  END IF;

  SELECT stock_item_id INTO v_item
  FROM public.stock_item_images
  WHERE id = p_image_id;

  IF v_item IS NULL THEN
    RAISE EXCEPTION 'image not found';
  END IF;

  UPDATE public.stock_item_images
  SET is_primary = false
  WHERE stock_item_id = v_item AND is_primary;

  UPDATE public.stock_item_images
  SET is_primary = true
  WHERE id = p_image_id;

  RETURN p_image_id;
END;
$$;

REVOKE ALL ON FUNCTION public.set_stock_item_primary_image(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_stock_item_primary_image(UUID)
  TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.delete_stock_item_image(p_image_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_item UUID;
  v_was_primary BOOLEAN;
  v_next UUID;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]) THEN
    RAISE EXCEPTION 'staff role required';
  END IF;

  SELECT stock_item_id, is_primary INTO v_item, v_was_primary
  FROM public.stock_item_images
  WHERE id = p_image_id;

  IF v_item IS NULL THEN
    RAISE EXCEPTION 'image not found';
  END IF;

  DELETE FROM public.stock_item_images WHERE id = p_image_id;

  IF v_was_primary THEN
    SELECT id INTO v_next
    FROM public.stock_item_images
    WHERE stock_item_id = v_item
    ORDER BY sort_order, created_at
    LIMIT 1;
    IF v_next IS NOT NULL THEN
      UPDATE public.stock_item_images SET is_primary = true WHERE id = v_next;
    END IF;
  END IF;

  RETURN p_image_id;
END;
$$;

REVOKE ALL ON FUNCTION public.delete_stock_item_image(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.delete_stock_item_image(UUID)
  TO authenticated, service_role;

COMMENT ON FUNCTION public.list_staff_product_pages(TEXT, INT) IS
  'Staff product-pages index: catalog title/OEM + editable price/discount/images.';
COMMENT ON FUNCTION public.upsert_staff_product_page(UUID, NUMERIC, TEXT, NUMERIC, TEXT) IS
  'Staff upsert retail price + discount; does not mutate catalog title/fitment/OEM/diagram.';
