-- Harden shop-ops internals after wishlist/review migrations (rls-auditor + security-reviewer).
-- Follow-up to 20260725201000 / 20260725202000 — do not rewrite applied history.
-- Exclusions: no ZIMRA / payroll tax.

-- ---------------------------------------------------------------------------
-- BLOCKING: deny client EXECUTE on stock adjuster (SECURITY DEFINER)
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public._adjust_stock_level(
  UUID,
  UUID,
  NUMERIC,
  public.valuation_method,
  NUMERIC,
  public.currency_code
) FROM PUBLIC, anon, authenticated;
-- Intentionally no GRANT EXECUTE to clients; invokers remain postgres/service DEFINER callers.

-- Defense in depth: SMS enqueue + wishlist notify helpers
REVOKE ALL ON FUNCTION public._enqueue_customer_sms(TEXT, TEXT, UUID, TEXT, JSONB, TEXT, UUID)
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public._notify_wishlist_back_in_stock(UUID)
  FROM PUBLIC, anon, authenticated;

-- ---------------------------------------------------------------------------
-- WARNING: review-photo path helpers — mirror delivery-pods storage grants
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public._review_photo_review_id_from_path(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._can_write_review_photo_object(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._can_select_review_photo_object(TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public._review_photo_review_id_from_path(TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._can_write_review_photo_object(TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._can_select_review_photo_object(TEXT)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Recommended: bind review photo metadata to existing storage.objects row
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

  IF NOT EXISTS (
    SELECT 1
    FROM storage.objects o
    WHERE o.bucket_id = 'review-photos'
      AND o.name = v_path
  ) THEN
    RAISE EXCEPTION
      'review photo object missing in review-photos bucket at %', v_path;
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

COMMENT ON FUNCTION public.add_customer_product_review_photo(UUID, TEXT, SMALLINT) IS
  'Attaches review photo metadata after Storage upload; requires storage.objects row '
  'in private review-photos bucket at storage_path.';

REVOKE ALL ON FUNCTION public.add_customer_product_review_photo(UUID, TEXT, SMALLINT)
  FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.add_customer_product_review_photo(UUID, TEXT, SMALLINT)
  TO authenticated, service_role;
