-- Harden customer_product_reviews (security-reviewer WARNINGs):
-- body length CHECK + submit guard; staff moderate via RPC only (no broad table UPDATE).

ALTER TABLE public.customer_product_reviews
  ADD CONSTRAINT customer_product_reviews_body_len
  CHECK (body IS NULL OR char_length(body) <= 4000);

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
  IF p_body IS NOT NULL AND char_length(p_body) > 4000 THEN
    RAISE EXCEPTION 'review body must be at most 4000 characters';
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

-- Staff must moderate via moderate_customer_product_review (SECURITY DEFINER),
-- not free-form UPDATE of customer_id / stock_item_id / rating / body.
DROP POLICY IF EXISTS reviews_staff_update ON public.customer_product_reviews;

-- Column privileges: authors may edit content fields only (identity FKs immutable).
REVOKE UPDATE ON TABLE public.customer_product_reviews FROM authenticated;
GRANT UPDATE (rating, body, status, updated_at)
  ON TABLE public.customer_product_reviews TO authenticated;
