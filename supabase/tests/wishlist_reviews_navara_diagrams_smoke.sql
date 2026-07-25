-- Wishlist + product reviews + compare RLS smoke (postgres via docker exec).
-- Own vs peer denial; approved readable; garage reminders stay absent.
-- Direct-table SELECT checks use SET LOCAL ROLE authenticated (superuser bypasses RLS).

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', p_uid::text, 'role', 'authenticated')::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
END;
$$;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_cust_a_user UUID := 'c0000000-0000-4000-8000-0000000000a1';
  v_cust_b_user UUID := 'c0000000-0000-4000-8000-0000000000b2';
  v_cust_a UUID := 'c1000000-0000-4000-8000-0000000000a1';
  v_cust_b UUID := 'c1000000-0000-4000-8000-0000000000b2';
  v_uom UUID;
  v_item UUID;
  v_wish UUID;
  v_compare UUID;
  v_review UUID;
  v_seen INT;
  v_diag INT;
  v_avg NUMERIC;
  v_rcnt BIGINT;
  v_credit_limit NUMERIC;
  v_currency public.currency_code;
BEGIN
  -- Idempotent cleanup
  DELETE FROM public.customer_product_review_photos
  WHERE review_id IN (
    SELECT id FROM public.customer_product_reviews
    WHERE customer_id IN (v_cust_a, v_cust_b)
  );
  DELETE FROM public.customer_product_reviews
  WHERE customer_id IN (v_cust_a, v_cust_b);
  DELETE FROM public.customer_wishlist_items
  WHERE customer_id IN (v_cust_a, v_cust_b);
  DELETE FROM public.customer_compare_items
  WHERE customer_id IN (v_cust_a, v_cust_b);

  -- Garage reminders must stay absent (plan skip)
  IF to_regclass('public.garage_service_reminders') IS NOT NULL
     OR to_regclass('public.customer_garage_reminders') IS NOT NULL
     OR to_regclass('public.service_reminders') IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: garage reminder table must not exist';
  END IF;

  -- Internals: authenticated must not EXECUTE stock adjuster (DEFINER)
  IF EXISTS (
    SELECT 1
    FROM information_schema.role_routine_grants
    WHERE routine_schema = 'public'
      AND routine_name = '_adjust_stock_level'
      AND grantee IN ('PUBLIC', 'anon', 'authenticated')
      AND privilege_type = 'EXECUTE'
  ) THEN
    RAISE EXCEPTION 'smoke fail: PUBLIC/anon/authenticated must not EXECUTE _adjust_stock_level';
  END IF;

  -- Compare table must exist with RLS
  IF to_regclass('public.customer_compare_items') IS NULL THEN
    RAISE EXCEPTION 'smoke fail: customer_compare_items missing';
  END IF;

  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA' LIMIT 1;
  IF v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: UOM EA missing';
  END IF;

  SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = '15208-65F0C' LIMIT 1;
  IF v_item IS NULL THEN
    INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
    VALUES ('P-WISH-REVIEW-001', 'Wishlist/review smoke part', v_uom)
    ON CONFLICT (oem_part_number) DO UPDATE
    SET description = EXCLUDED.description
    RETURNING id INTO v_item;
    IF v_item IS NULL THEN
      SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P-WISH-REVIEW-001';
    END IF;
  END IF;

  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES
    (
      '00000000-0000-0000-0000-000000000000', v_cust_a_user,
      'authenticated', 'authenticated', 'storefront-a@gtr.local',
      crypt('local-dev-customer', gen_salt('bf')), now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"full_name":"Storefront A"}'::jsonb, now(), now(), '', '', '', ''
    ),
    (
      '00000000-0000-0000-0000-000000000000', v_cust_b_user,
      'authenticated', 'authenticated', 'storefront-b@gtr.local',
      crypt('local-dev-customer', gen_salt('bf')), now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"full_name":"Storefront B"}'::jsonb, now(), now(), '', '', '', ''
    )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES
    (v_cust_a_user, 'Storefront A', false),
    (v_cust_b_user, 'Storefront B', false)
  ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, is_staff = false;

  INSERT INTO public.customers (id, display_name, email, currency, profile_id)
  VALUES
    (v_cust_a, 'Storefront Customer A', 'storefront-a@gtr.local', 'USD', v_cust_a_user),
    (v_cust_b, 'Storefront Customer B', 'storefront-b@gtr.local', 'USD', v_cust_b_user)
  ON CONFLICT (id) DO UPDATE
  SET profile_id = EXCLUDED.profile_id, display_name = EXCLUDED.display_name;

  -- Customer A: wishlist
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  IF EXISTS (SELECT 1 FROM public.stock_items WHERE id = v_item AND oem_part_number = '15208-65F0C') THEN
    v_wish := public.add_customer_wishlist_item(NULL, '15208-65F0C');
  ELSE
    v_wish := public.add_customer_wishlist_item(v_item, NULL);
  END IF;

  PERFORM public._test_set_auth_uid(v_cust_a_user);
  PERFORM public.set_wishlist_notify_when_in_stock(true, NULL, NULL, v_wish);

  PERFORM public._test_set_auth_uid(v_cust_a_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_wishlist_items
  WHERE customer_id = v_cust_a AND notify_when_in_stock = true;
  RESET ROLE;
  IF v_seen < 1 THEN
    RAISE EXCEPTION 'smoke fail: notify_when_in_stock not set';
  END IF;

  -- Peer cannot see A's wishlist
  PERFORM public._test_set_auth_uid(v_cust_b_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_wishlist_items
  WHERE customer_id = v_cust_a;
  RESET ROLE;
  IF v_seen <> 0 THEN
    RAISE EXCEPTION 'smoke fail: peer can read foreign wishlist';
  END IF;

  -- Compare: A adds; peer denied
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  v_compare := public.add_customer_compare_item(v_item, NULL);

  PERFORM public._test_set_auth_uid(v_cust_a_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_compare_items
  WHERE customer_id = v_cust_a;
  RESET ROLE;
  IF v_seen < 1 THEN
    RAISE EXCEPTION 'smoke fail: compare insert not visible to owner';
  END IF;

  PERFORM public._test_set_auth_uid(v_cust_b_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_compare_items
  WHERE customer_id = v_cust_a;
  RESET ROLE;
  IF v_seen <> 0 THEN
    RAISE EXCEPTION 'smoke fail: peer can read foreign compare list';
  END IF;

  PERFORM public._test_set_auth_uid(v_cust_b_user);
  BEGIN
    PERFORM public.remove_customer_compare_item(v_item, NULL, NULL);
    RAISE EXCEPTION 'smoke fail: peer compare remove should fail';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE '%smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%compare item not found%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected peer compare remove error: %', SQLERRM;
      END IF;
  END;

  -- Peer remove of A's wishlist SKU fails
  PERFORM public._test_set_auth_uid(v_cust_b_user);
  BEGIN
    PERFORM public.remove_customer_wishlist_item(v_item, NULL, NULL);
    RAISE EXCEPTION 'smoke fail: peer remove should fail';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE '%smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%wishlist item not found%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected peer remove error: %', SQLERRM;
      END IF;
  END;

  -- Reviews: oversized body rejected
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  BEGIN
    PERFORM public.submit_customer_product_review(
      4::smallint, repeat('x', 4001), v_item, NULL
    );
    RAISE EXCEPTION 'smoke fail: oversized body should fail';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE '%smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%4000%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected oversized body error: %', SQLERRM;
      END IF;
  END;

  -- Reviews: A submits pending
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  v_review := public.submit_customer_product_review(
    5::smallint, 'Great oil filter', v_item, NULL
  );

  PERFORM public._test_set_auth_uid(v_cust_a_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_product_reviews
  WHERE id = v_review AND status = 'pending';
  RESET ROLE;
  IF v_seen <> 1 THEN
    RAISE EXCEPTION 'smoke fail: own pending review not visible';
  END IF;

  -- Peer cannot see pending
  PERFORM public._test_set_auth_uid(v_cust_b_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_product_reviews
  WHERE id = v_review;
  RESET ROLE;
  IF v_seen <> 0 THEN
    RAISE EXCEPTION 'smoke fail: peer can read pending review';
  END IF;

  -- Staff cannot rewrite body via table UPDATE
  PERFORM public._test_set_auth_uid(v_admin);
  SET LOCAL ROLE authenticated;
  BEGIN
    UPDATE public.customer_product_reviews
    SET body = 'staff rewrite'
    WHERE id = v_review;
    IF FOUND THEN
      RAISE EXCEPTION 'smoke fail: staff table UPDATE on body should be denied';
    END IF;
  EXCEPTION
    WHEN insufficient_privilege THEN
      NULL;
    WHEN OTHERS THEN
      IF SQLERRM LIKE '%smoke fail:%' THEN
        RAISE;
      END IF;
  END;
  RESET ROLE;

  -- Staff approves (optional notify enqueue fail-closed)
  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM public.moderate_customer_product_review(v_review, 'approved');

  -- Aggregates
  SELECT avg_rating, review_count INTO v_avg, v_rcnt
  FROM public.get_product_review_stats(v_item, NULL);
  IF v_rcnt < 1 OR v_avg < 5 THEN
    RAISE EXCEPTION 'smoke fail: review aggregates expected avg=5 count>=1 got % / %', v_avg, v_rcnt;
  END IF;

  -- Peer can read approved
  PERFORM public._test_set_auth_uid(v_cust_b_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_product_reviews
  WHERE id = v_review AND status = 'approved';
  RESET ROLE;
  IF v_seen <> 1 THEN
    RAISE EXCEPTION 'smoke fail: approved review not readable';
  END IF;

  -- Storefront must not set credit
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  BEGIN
    PERFORM public.set_customer_credit(v_cust_a, 5000, false);
    RAISE EXCEPTION 'smoke fail: storefront set_customer_credit should fail';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE '%smoke fail:%' THEN
        RAISE;
      END IF;
      IF SQLERRM NOT LIKE '%role required%' THEN
        RAISE EXCEPTION 'smoke fail: unexpected credit deny: %', SQLERRM;
      END IF;
  END;

  -- Staff credit mutator returns explicit currency
  PERFORM public._test_set_auth_uid(v_admin);
  SELECT sc.credit_limit, sc.currency
  INTO v_credit_limit, v_currency
  FROM public.set_customer_credit(v_cust_a, 2500.00, false) sc;
  IF v_credit_limit <> 2500.00 OR v_currency IS NULL THEN
    RAISE EXCEPTION 'smoke fail: set_customer_credit bad response % %', v_credit_limit, v_currency;
  END IF;

  -- Owner removes wishlist + compare
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  PERFORM public.remove_customer_wishlist_item(v_item, NULL, NULL);
  PERFORM public.remove_customer_compare_item(NULL, NULL, v_compare);

  PERFORM public._test_set_auth_uid(v_cust_a_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_wishlist_items
  WHERE customer_id = v_cust_a AND stock_item_id = v_item;
  RESET ROLE;
  IF v_seen <> 0 THEN
    RAISE EXCEPTION 'smoke fail: wishlist remove failed';
  END IF;

  -- Diagram paths: Navara + X-Trail
  SELECT count(*)::int INTO v_diag
  FROM public.part_fitment
  WHERE oem_part_number IN ('15208-65F0C', '40206-EA00A', '21410-JF00A', '16546-00Q0A')
    AND diagram_path IS NOT NULL
    AND diagram_path LIKE 'navara-d40/%';
  IF v_diag < 4 THEN
    RAISE EXCEPTION 'smoke fail: expected Navara diagram_path rows, got %', v_diag;
  END IF;

  SELECT count(*)::int INTO v_diag
  FROM public.part_fitment
  WHERE oem_part_number IN ('15208-9N00A', '16546-JA00A', '62022-JG00A')
    AND diagram_path LIKE 'xtrail-t31/%';
  IF v_diag < 3 THEN
    RAISE EXCEPTION 'smoke fail: expected X-Trail diagram_path rows, got %', v_diag;
  END IF;

  RAISE NOTICE 'wishlist_reviews_navara_diagrams_smoke OK';
END;
$$;
