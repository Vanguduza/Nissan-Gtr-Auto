-- Audit follow-ons (backend slice): customer wishlist + product reviews + Navara diagram seed.
-- Compare: NO table — session-only on web is enough; no half-built compare schema found.
-- Garage service reminders: SKIP — only customer_garage_vehicles exists; do not invent reminder tables.
-- NO ZIMRA / payroll tax / HTML5 QR. RLS in this same file for every new table.

-- ---------------------------------------------------------------------------
-- Wishlist (customer owns rows; staff SELECT optional, no staff write)
-- ---------------------------------------------------------------------------
CREATE TABLE public.customer_wishlist_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT customer_wishlist_items_unique UNIQUE (customer_id, stock_item_id)
);

CREATE INDEX customer_wishlist_customer_idx
  ON public.customer_wishlist_items (customer_id, created_at DESC);

CREATE INDEX customer_wishlist_stock_item_idx
  ON public.customer_wishlist_items (stock_item_id);

COMMENT ON TABLE public.customer_wishlist_items IS
  'Storefront wishlist. Own-row RLS via customers.profile_id; staff may SELECT only.';

ALTER TABLE public.customer_wishlist_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY wishlist_customer_all ON public.customer_wishlist_items
  FOR ALL TO authenticated
  USING (customer_id = public._current_customer_id())
  WITH CHECK (customer_id = public._current_customer_id());

CREATE POLICY wishlist_staff_select ON public.customer_wishlist_items
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]));

GRANT SELECT ON TABLE public.customer_wishlist_items TO authenticated, service_role;
GRANT INSERT, DELETE ON TABLE public.customer_wishlist_items TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_wishlist_items TO service_role;

-- Resolve stock_item by id or OEM (storefront PDP uses either).
CREATE OR REPLACE FUNCTION public._resolve_customer_stock_item(
  p_stock_item_id UUID,
  p_oem_part_number TEXT
)
RETURNS UUID
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_oem TEXT := NULLIF(trim(COALESCE(p_oem_part_number, '')), '');
BEGIN
  IF p_stock_item_id IS NOT NULL THEN
    SELECT si.id INTO v_id
    FROM public.stock_items si
    WHERE si.id = p_stock_item_id;
    IF v_id IS NULL THEN
      RAISE EXCEPTION 'stock item not found';
    END IF;
    RETURN v_id;
  END IF;

  IF v_oem IS NULL THEN
    RAISE EXCEPTION 'stock_item_id or oem_part_number required';
  END IF;

  SELECT si.id INTO v_id
  FROM public.stock_items si
  WHERE lower(si.oem_part_number) = lower(v_oem)
  ORDER BY si.created_at ASC
  LIMIT 1;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'stock item not found for OEM %', v_oem;
  END IF;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.add_customer_wishlist_item(
  p_stock_item_id UUID DEFAULT NULL,
  p_oem_part_number TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_item UUID;
  v_id UUID;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;

  v_item := public._resolve_customer_stock_item(p_stock_item_id, p_oem_part_number);

  INSERT INTO public.customer_wishlist_items (customer_id, stock_item_id)
  VALUES (v_cust, v_item)
  ON CONFLICT (customer_id, stock_item_id) DO UPDATE
    SET created_at = public.customer_wishlist_items.created_at
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.remove_customer_wishlist_item(
  p_stock_item_id UUID DEFAULT NULL,
  p_oem_part_number TEXT DEFAULT NULL,
  p_wishlist_id UUID DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_item UUID;
  v_deleted INT := 0;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;

  IF p_wishlist_id IS NOT NULL THEN
    DELETE FROM public.customer_wishlist_items
    WHERE id = p_wishlist_id AND customer_id = v_cust;
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    IF v_deleted = 0 THEN
      RAISE EXCEPTION 'wishlist item not found';
    END IF;
    RETURN;
  END IF;

  v_item := public._resolve_customer_stock_item(p_stock_item_id, p_oem_part_number);

  DELETE FROM public.customer_wishlist_items
  WHERE customer_id = v_cust AND stock_item_id = v_item;
  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  IF v_deleted = 0 THEN
    RAISE EXCEPTION 'wishlist item not found';
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public._resolve_customer_stock_item(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_customer_wishlist_item(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.remove_customer_wishlist_item(UUID, TEXT, UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.add_customer_wishlist_item(UUID, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.remove_customer_wishlist_item(UUID, TEXT, UUID)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Product reviews (author owns; authenticated read approved or own pending)
-- ---------------------------------------------------------------------------
CREATE TYPE public.product_review_status AS ENUM ('pending', 'approved', 'rejected');

CREATE TABLE public.customer_product_reviews (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE CASCADE,
  rating SMALLINT NOT NULL CHECK (rating >= 1 AND rating <= 5),
  body TEXT NOT NULL DEFAULT '',
  status public.product_review_status NOT NULL DEFAULT 'pending',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT customer_product_reviews_unique UNIQUE (customer_id, stock_item_id)
);

CREATE INDEX customer_product_reviews_item_status_idx
  ON public.customer_product_reviews (stock_item_id, status, created_at DESC);

CREATE INDEX customer_product_reviews_customer_idx
  ON public.customer_product_reviews (customer_id, created_at DESC);

COMMENT ON TABLE public.customer_product_reviews IS
  'Customer product reviews. Authors manage own pending rows; approved readable by authenticated; staff moderate.';

ALTER TABLE public.customer_product_reviews ENABLE ROW LEVEL SECURITY;

-- Approved reviews: any authenticated user (matches catalog SELECT-to-authenticated convention).
CREATE POLICY reviews_select_approved ON public.customer_product_reviews
  FOR SELECT TO authenticated
  USING (
    status = 'approved'
    OR customer_id = public._current_customer_id()
    OR public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[])
  );

CREATE POLICY reviews_customer_insert ON public.customer_product_reviews
  FOR INSERT TO authenticated
  WITH CHECK (
    customer_id = public._current_customer_id()
    AND status = 'pending'
  );

-- Customers may edit/delete only their own pending reviews (resubmit path).
CREATE POLICY reviews_customer_update_pending ON public.customer_product_reviews
  FOR UPDATE TO authenticated
  USING (
    customer_id = public._current_customer_id()
    AND status = 'pending'
  )
  WITH CHECK (
    customer_id = public._current_customer_id()
    AND status = 'pending'
  );

CREATE POLICY reviews_customer_delete_pending ON public.customer_product_reviews
  FOR DELETE TO authenticated
  USING (
    customer_id = public._current_customer_id()
    AND status = 'pending'
  );

-- Staff may update status (moderate) or delete.
CREATE POLICY reviews_staff_update ON public.customer_product_reviews
  FOR UPDATE TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]));

CREATE POLICY reviews_staff_delete ON public.customer_product_reviews
  FOR DELETE TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]));

GRANT SELECT ON TABLE public.customer_product_reviews TO authenticated, service_role;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_product_reviews TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_product_reviews TO service_role;

CREATE OR REPLACE FUNCTION public.submit_customer_product_review(
  p_rating SMALLINT,
  p_body TEXT DEFAULT '',
  p_stock_item_id UUID DEFAULT NULL,
  p_oem_part_number TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_item UUID;
  v_id UUID;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  IF p_rating IS NULL OR p_rating < 1 OR p_rating > 5 THEN
    RAISE EXCEPTION 'rating must be 1..5';
  END IF;

  v_item := public._resolve_customer_stock_item(p_stock_item_id, p_oem_part_number);

  INSERT INTO public.customer_product_reviews (
    customer_id, stock_item_id, rating, body, status
  )
  VALUES (
    v_cust,
    v_item,
    p_rating,
    COALESCE(p_body, ''),
    'pending'
  )
  ON CONFLICT (customer_id, stock_item_id) DO UPDATE
  SET
    rating = EXCLUDED.rating,
    body = EXCLUDED.body,
    status = 'pending',
    updated_at = now()
  WHERE public.customer_product_reviews.status IN ('pending', 'rejected')
  RETURNING id INTO v_id;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'cannot replace an approved review; contact support';
  END IF;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.moderate_customer_product_review(
  p_review_id UUID,
  p_status public.product_review_status
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin or sales role required to moderate reviews';
  END IF;
  IF p_status IS NULL OR p_status = 'pending' THEN
    RAISE EXCEPTION 'moderate status must be approved or rejected';
  END IF;

  UPDATE public.customer_product_reviews
  SET status = p_status, updated_at = now()
  WHERE id = p_review_id
  RETURNING id INTO v_id;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'review not found';
  END IF;
  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.submit_customer_product_review(SMALLINT, TEXT, UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.moderate_customer_product_review(UUID, public.product_review_status) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.submit_customer_product_review(SMALLINT, TEXT, UUID, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.moderate_customer_product_review(UUID, public.product_review_status)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Navara D40 / YD25 catalog + diagram_path seed (fixtures; no live scrape)
-- Binary PNGs live under data-pipeline/fixtures/navara_d40_yd25/diagrams/;
-- upload into Storage via supabase/seed_catalog_diagrams.mjs after db reset.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  v_uom UUID;
  v_list UUID;
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  r RECORD;
BEGIN
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA' LIMIT 1;
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL' LIMIT 1;

  -- vehicle_master natural key is an expression unique index — upsert via NOT EXISTS
  IF NOT EXISTS (
    SELECT 1 FROM public.vehicle_master
    WHERE COALESCE(vin_prefix, '') = 'MNTCCND40'
      AND chassis_code = 'D40'
      AND COALESCE(engine_code, '') = 'YD25'
      AND COALESCE(production_year, 0) = 2010
      AND model_variant = 'Nissan Navara D40 · YD25'
  ) THEN
    INSERT INTO public.vehicle_master (
      vin_prefix, chassis_code, engine_code, production_year, model_variant
    )
    VALUES ('MNTCCND40', 'D40', 'YD25', 2010, 'Nissan Navara D40 · YD25');
  END IF;

  INSERT INTO public.pnc_categories (pnc_code, category_name, subcategory_name)
  VALUES
    ('15208', 'Filters', 'Oil filter'),
    ('40206', 'Brakes', 'Front brake disc'),
    ('21410', 'Cooling', 'Water pump'),
    ('16546', 'Filters', 'Air filter')
  ON CONFLICT (pnc_code) DO UPDATE
  SET
    category_name = EXCLUDED.category_name,
    subcategory_name = EXCLUDED.subcategory_name;

  -- part_fitment rows (natural key upsert)
  FOR r IN
    SELECT * FROM (VALUES
      ('15208-65F0C', '15208', 'D40', 'YD25', NULL::text,
       120.5::numeric, 84.0::numeric, 48.0::numeric, 62.0::numeric,
       'navara-d40/15208-oil-filter.png'),
      ('40206-EA00A', '40206', 'D40', 'YD25', NULL::text,
       200.0, 150.0, 90.0, 90.0,
       'navara-d40/40206-brake-disc.png'),
      ('21410-JF00A', '21410', 'D40', 'YD25', NULL::text,
       310.0, 220.0, 70.0, 55.0,
       'navara-d40/21410-water-pump.png'),
      ('21010-JF00A', '21410', 'D40', 'YD25', '21410-JF00A',
       305.0, 215.0, 72.0, 58.0,
       'navara-d40/21410-water-pump.png'),
      ('16546-00Q0A', '16546', 'D40', 'YD25', NULL::text,
       88.0, 44.0, 120.0, 80.0,
       'navara-d40/16546-air-filter.png')
    ) AS t(
      oem_part_number, pnc_code, chassis_code, engine_code, superseded_by,
      bbox_x, bbox_y, bbox_width, bbox_height, diagram_path
    )
  LOOP
    UPDATE public.part_fitment
    SET
      superseded_by = r.superseded_by,
      bbox_x = r.bbox_x,
      bbox_y = r.bbox_y,
      bbox_width = r.bbox_width,
      bbox_height = r.bbox_height,
      diagram_path = r.diagram_path
    WHERE oem_part_number = r.oem_part_number
      AND COALESCE(chassis_code, '') = COALESCE(r.chassis_code, '')
      AND COALESCE(engine_code, '') = COALESCE(r.engine_code, '')
      AND COALESCE(pnc_code, '') = COALESCE(r.pnc_code, '');

    IF NOT FOUND THEN
      INSERT INTO public.part_fitment (
        oem_part_number, pnc_code, chassis_code, engine_code, superseded_by,
        bbox_x, bbox_y, bbox_width, bbox_height, diagram_path
      )
      VALUES (
        r.oem_part_number, r.pnc_code, r.chassis_code, r.engine_code, r.superseded_by,
        r.bbox_x, r.bbox_y, r.bbox_width, r.bbox_height, r.diagram_path
      );
    END IF;
  END LOOP;

  INSERT INTO public.oe_cross_refs (oem_part_number, oe_number, brand)
  VALUES
    ('15208-65F0C', '15208-65F0A', 'Nissan OE'),
    ('15208-65F0C', 'AY100-NS004', 'Aftermarket'),
    ('40206-EA00A', '40206-EB300', 'Nissan OE'),
    ('21410-JF00A', '21010-JF00A', 'Nissan OE')
  ON CONFLICT (oem_part_number, oe_number, brand) DO NOTHING;

  -- Demo stock_items + retail prices so PDP binds without scrape
  IF v_uom IS NOT NULL THEN
    FOR r IN
      SELECT * FROM (VALUES
        ('15208-65F0C', 'Oil filter — Navara D40 YD25', 18.50::numeric, 0::numeric),
        ('40206-EA00A', 'Front brake disc — Navara D40', 65.00, 25.00),
        ('21410-JF00A', 'Water pump — Navara D40 YD25', 42.00, 15.00),
        ('21010-JF00A', 'Water pump (superseded) — Navara D40', 38.00, 15.00),
        ('16546-00Q0A', 'Air filter — Navara D40', 22.00, 0)
      ) AS t(oem, descr, price, core)
    LOOP
      INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
      VALUES (r.oem, r.descr, v_uom)
      ON CONFLICT (oem_part_number) DO UPDATE
      SET
        description = EXCLUDED.description,
        base_uom_id = COALESCE(public.stock_items.base_uom_id, EXCLUDED.base_uom_id);

      IF v_list IS NOT NULL THEN
        INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
        SELECT v_list, si.id, r.price, r.core
        FROM public.stock_items si
        WHERE si.oem_part_number = r.oem
        ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
        SET unit_price = EXCLUDED.unit_price, core_charge = EXCLUDED.core_charge;
      END IF;
    END LOOP;
  END IF;

  -- Register Storage object metadata (bytes uploaded by seed_catalog_diagrams.mjs)
  INSERT INTO storage.objects (bucket_id, name, owner, owner_id, metadata)
  VALUES
    (
      'catalog-diagrams', 'navara-d40/15208-oil-filter.png', v_admin, v_admin::text,
      jsonb_build_object('mimetype', 'image/png', 'size', 137, 'cacheControl', '3600')
    ),
    (
      'catalog-diagrams', 'navara-d40/40206-brake-disc.png', v_admin, v_admin::text,
      jsonb_build_object('mimetype', 'image/png', 'size', 136, 'cacheControl', '3600')
    ),
    (
      'catalog-diagrams', 'navara-d40/21410-water-pump.png', v_admin, v_admin::text,
      jsonb_build_object('mimetype', 'image/png', 'size', 137, 'cacheControl', '3600')
    ),
    (
      'catalog-diagrams', 'navara-d40/16546-air-filter.png', v_admin, v_admin::text,
      jsonb_build_object('mimetype', 'image/png', 'size', 136, 'cacheControl', '3600')
    )
  ON CONFLICT (bucket_id, name) DO UPDATE
  SET metadata = EXCLUDED.metadata;
END;
$$;
