-- Wishlist + product reviews RLS smoke (postgres via docker exec).
-- Own vs peer denial; approved readable; compare/garage-reminder tables absent.
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
  v_review UUID;
  v_seen INT;
  v_diag INT;
BEGIN
  -- Garage reminders must stay absent (plan item 10 skip)
  IF to_regclass('public.garage_service_reminders') IS NOT NULL
     OR to_regclass('public.customer_garage_reminders') IS NOT NULL
     OR to_regclass('public.service_reminders') IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: garage reminder table must not exist';
  END IF;

  -- Compare: no DB table (session-only)
  IF to_regclass('public.customer_compare_items') IS NOT NULL
     OR to_regclass('public.product_compare') IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: compare table must not exist (session-only)';
  END IF;

  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA' LIMIT 1;
  IF v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: UOM EA missing';
  END IF;

  -- Prefer Navara seeded OEM; else create a smoke SKU
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

  -- Customer A: add wishlist (SECURITY DEFINER RPC)
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  IF EXISTS (SELECT 1 FROM public.stock_items WHERE id = v_item AND oem_part_number = '15208-65F0C') THEN
    v_wish := public.add_customer_wishlist_item(NULL, '15208-65F0C');
  ELSE
    v_wish := public.add_customer_wishlist_item(v_item, NULL);
  END IF;

  PERFORM public._test_set_auth_uid(v_cust_a_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_wishlist_items
  WHERE customer_id = v_cust_a;
  RESET ROLE;
  IF v_seen < 1 THEN
    RAISE EXCEPTION 'smoke fail: wishlist insert not visible to owner';
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

  -- Peer remove of A's SKU fails (own-row only)
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

  -- Staff approves
  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM public.moderate_customer_product_review(v_review, 'approved');

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

  -- Owner removes wishlist
  PERFORM public._test_set_auth_uid(v_cust_a_user);
  PERFORM public.remove_customer_wishlist_item(v_item, NULL, NULL);

  PERFORM public._test_set_auth_uid(v_cust_a_user);
  SET LOCAL ROLE authenticated;
  SELECT count(*)::int INTO v_seen
  FROM public.customer_wishlist_items
  WHERE customer_id = v_cust_a AND stock_item_id = v_item;
  RESET ROLE;
  IF v_seen <> 0 THEN
    RAISE EXCEPTION 'smoke fail: wishlist remove failed';
  END IF;

  -- Diagram paths seeded for Navara demo OEMs
  SELECT count(*)::int INTO v_diag
  FROM public.part_fitment
  WHERE oem_part_number IN ('15208-65F0C', '40206-EA00A', '21410-JF00A', '16546-00Q0A')
    AND diagram_path IS NOT NULL
    AND diagram_path LIKE 'navara-d40/%';
  IF v_diag < 4 THEN
    RAISE EXCEPTION 'smoke fail: expected Navara diagram_path rows, got %', v_diag;
  END IF;

  RAISE NOTICE 'wishlist_reviews_navara_diagrams_smoke OK';
END;
$$;
