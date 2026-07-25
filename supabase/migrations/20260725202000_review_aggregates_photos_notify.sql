-- Review aggregates, photo attachments (Storage + RLS), approve notify (fail-closed).
-- NO ZIMRA / payroll tax / secrets.

-- ---------------------------------------------------------------------------
-- Aggregate RPC (approved only)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_product_review_stats(
  p_stock_item_id UUID DEFAULT NULL,
  p_oem_part_number TEXT DEFAULT NULL
)
RETURNS TABLE (
  stock_item_id UUID,
  avg_rating NUMERIC,
  review_count BIGINT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_item UUID;
BEGIN
  v_item := public._resolve_customer_stock_item(p_stock_item_id, p_oem_part_number);

  RETURN QUERY
  SELECT
    v_item,
    COALESCE(
      (
        SELECT round(avg(r.rating)::numeric, 2)
        FROM public.customer_product_reviews r
        WHERE r.stock_item_id = v_item AND r.status = 'approved'
      ),
      0::numeric
    ) AS avg_rating,
    (
      SELECT count(*)::bigint
      FROM public.customer_product_reviews r
      WHERE r.stock_item_id = v_item AND r.status = 'approved'
    ) AS review_count;
END;
$$;

REVOKE ALL ON FUNCTION public.get_product_review_stats(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_product_review_stats(UUID, TEXT)
  TO authenticated, service_role, anon;

-- Convenience view (security_invoker so RLS still applies to underlying table).
CREATE OR REPLACE VIEW public.product_review_aggregates
WITH (security_invoker = true)
AS
SELECT
  stock_item_id,
  round(avg(rating)::numeric, 2) AS avg_rating,
  count(*)::bigint AS review_count
FROM public.customer_product_reviews
WHERE status = 'approved'
GROUP BY stock_item_id;

GRANT SELECT ON public.product_review_aggregates TO authenticated, service_role, anon;

COMMENT ON VIEW public.product_review_aggregates IS
  'Approved review avg + count per stock_item_id (security_invoker).';

-- ---------------------------------------------------------------------------
-- Photo child table
-- ---------------------------------------------------------------------------
CREATE TABLE public.customer_product_review_photos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  review_id UUID NOT NULL REFERENCES public.customer_product_reviews (id) ON DELETE CASCADE,
  storage_path TEXT NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 0 CHECK (sort_order >= 0 AND sort_order < 20),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT customer_product_review_photos_path_unique UNIQUE (review_id, storage_path)
);

CREATE INDEX customer_product_review_photos_review_idx
  ON public.customer_product_review_photos (review_id, sort_order);

COMMENT ON TABLE public.customer_product_review_photos IS
  'Review photo object keys in Storage bucket review-photos. Path: {review_id}/{file}.';

ALTER TABLE public.customer_product_review_photos ENABLE ROW LEVEL SECURITY;

-- Approved review photos: any authenticated reader; own pending; staff.
CREATE POLICY review_photos_select ON public.customer_product_review_photos
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.customer_product_reviews r
      WHERE r.id = review_id
        AND (
          r.status = 'approved'
          OR r.customer_id = public._current_customer_id()
          OR public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[])
        )
    )
  );

CREATE POLICY review_photos_insert_own_pending ON public.customer_product_review_photos
  FOR INSERT TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1
      FROM public.customer_product_reviews r
      WHERE r.id = review_id
        AND r.customer_id = public._current_customer_id()
        AND r.status = 'pending'
    )
    AND storage_path LIKE (review_id::text || '/%')
  );

CREATE POLICY review_photos_delete_own_pending ON public.customer_product_review_photos
  FOR DELETE TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.customer_product_reviews r
      WHERE r.id = review_id
        AND r.customer_id = public._current_customer_id()
        AND r.status = 'pending'
    )
  );

CREATE POLICY review_photos_staff_all ON public.customer_product_review_photos
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]));

GRANT SELECT ON TABLE public.customer_product_review_photos TO authenticated, service_role;
GRANT INSERT, DELETE ON TABLE public.customer_product_review_photos TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_product_review_photos TO service_role;

-- ---------------------------------------------------------------------------
-- Storage bucket review-photos + RLS
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._review_photo_review_id_from_path(p_name TEXT)
RETURNS UUID
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v_seg TEXT;
  v_id UUID;
BEGIN
  IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
    RETURN NULL;
  END IF;
  v_seg := trim(both '/' FROM p_name);
  IF split_part(v_seg, '/', 1) = 'review-photos' THEN
    v_seg := substr(v_seg, length('review-photos/') + 1);
  END IF;
  v_seg := split_part(v_seg, '/', 1);
  BEGIN
    v_id := v_seg::uuid;
  EXCEPTION
    WHEN invalid_text_representation THEN
      RETURN NULL;
  END;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public._can_write_review_photo_object(p_name TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[])
    OR EXISTS (
      SELECT 1
      FROM public.customer_product_reviews r
      WHERE r.id = public._review_photo_review_id_from_path(p_name)
        AND r.customer_id = public._current_customer_id()
        AND r.status = 'pending'
    );
$$;

CREATE OR REPLACE FUNCTION public._can_select_review_photo_object(p_name TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[])
    OR EXISTS (
      SELECT 1
      FROM public.customer_product_reviews r
      WHERE r.id = public._review_photo_review_id_from_path(p_name)
        AND (
          r.status = 'approved'
          OR r.customer_id = public._current_customer_id()
        )
    );
$$;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'review-photos',
  'review-photos',
  false,
  5242880, -- 5 MiB
  ARRAY[
    'image/jpeg',
    'image/jpg',
    'image/png',
    'image/webp'
  ]
)
ON CONFLICT (id) DO UPDATE
SET
  public = EXCLUDED.public,
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS review_photos_storage_select ON storage.objects;
CREATE POLICY review_photos_storage_select
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'review-photos'
    AND public._can_select_review_photo_object(name)
  );

DROP POLICY IF EXISTS review_photos_storage_insert ON storage.objects;
CREATE POLICY review_photos_storage_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'review-photos'
    AND public._can_write_review_photo_object(name)
  );

DROP POLICY IF EXISTS review_photos_storage_update ON storage.objects;
CREATE POLICY review_photos_storage_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'review-photos'
    AND public._can_write_review_photo_object(name)
  )
  WITH CHECK (
    bucket_id = 'review-photos'
    AND public._can_write_review_photo_object(name)
  );

DROP POLICY IF EXISTS review_photos_storage_delete ON storage.objects;
CREATE POLICY review_photos_storage_delete
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'review-photos'
    AND public._can_write_review_photo_object(name)
  );

-- ---------------------------------------------------------------------------
-- Attach photo metadata RPC (after Storage upload)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.add_customer_product_review_photo(
  p_review_id UUID,
  p_storage_path TEXT,
  p_sort_order SMALLINT DEFAULT 0
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_id UUID;
  v_path TEXT := NULLIF(trim(COALESCE(p_storage_path, '')), '');
  v_count INT;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  IF p_review_id IS NULL OR v_path IS NULL THEN
    RAISE EXCEPTION 'review_id and storage_path required';
  END IF;
  IF v_path NOT LIKE (p_review_id::text || '/%') THEN
    RAISE EXCEPTION 'storage_path must start with review_id/';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.customer_product_reviews
    WHERE id = p_review_id
      AND customer_id = v_cust
      AND status = 'pending'
  ) THEN
    RAISE EXCEPTION 'pending review not found for customer';
  END IF;

  SELECT count(*)::int INTO v_count
  FROM public.customer_product_review_photos
  WHERE review_id = p_review_id;
  IF v_count >= 5 THEN
    RAISE EXCEPTION 'max 5 photos per review';
  END IF;

  INSERT INTO public.customer_product_review_photos (review_id, storage_path, sort_order)
  VALUES (p_review_id, v_path, COALESCE(p_sort_order, 0))
  ON CONFLICT (review_id, storage_path) DO UPDATE
    SET sort_order = EXCLUDED.sort_order
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.add_customer_product_review_photo(UUID, TEXT, SMALLINT)
  FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.add_customer_product_review_photo(UUID, TEXT, SMALLINT)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- moderate_customer_product_review — optional notify on approve
-- ---------------------------------------------------------------------------
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
  v_prev public.product_review_status;
  v_cust UUID;
  v_profile UUID;
  v_phone TEXT;
  v_item UUID;
  v_oem TEXT;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin or sales role required to moderate reviews';
  END IF;
  IF p_status IS NULL OR p_status = 'pending' THEN
    RAISE EXCEPTION 'moderate status must be approved or rejected';
  END IF;

  SELECT status, customer_id, stock_item_id
  INTO v_prev, v_cust, v_item
  FROM public.customer_product_reviews
  WHERE id = p_review_id
  FOR UPDATE;

  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'review not found';
  END IF;

  UPDATE public.customer_product_reviews
  SET status = p_status, updated_at = now()
  WHERE id = p_review_id
  RETURNING id INTO v_id;

  IF p_status = 'approved' AND v_prev IS DISTINCT FROM 'approved' THEN
    SELECT c.profile_id,
           COALESCE(
             NULLIF(trim(COALESCE(c.phone_e164, '')), ''),
             NULLIF(trim(COALESCE(p.phone_e164, '')), ''),
             NULLIF(trim(COALESCE(c.whatsapp_e164, '')), '')
           ),
           si.oem_part_number
    INTO v_profile, v_phone, v_oem
    FROM public.customers c
    LEFT JOIN public.profiles p ON p.id = c.profile_id
    LEFT JOIN public.stock_items si ON si.id = v_item
    WHERE c.id = v_cust;

    IF v_profile IS NOT NULL THEN
      PERFORM public._enqueue_customer_sms(
        'review_approved',
        'review:approved:' || v_id::text,
        v_profile,
        v_phone,
        jsonb_build_object(
          'review_id', v_id,
          'customer_id', v_cust,
          'stock_item_id', v_item,
          'oem_part_number', v_oem
        ),
        format(
          'GTR Auto: your review for %s was approved.',
          COALESCE(v_oem, 'a product')
        )
      );
    END IF;

    BEGIN
      PERFORM public.emit_domain_event(
        'review_approved',
        'review:approved:staff:' || v_id::text,
        jsonb_build_object('review_id', v_id, 'stock_item_id', v_item)
      );
    EXCEPTION
      WHEN OTHERS THEN
        NULL;
    END;
  END IF;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.moderate_customer_product_review(UUID, public.product_review_status)
  FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.moderate_customer_product_review(UUID, public.product_review_status)
  TO authenticated, service_role;
