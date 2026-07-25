-- Shop-floor POS Phase 2 smoke: standalone checkout+contacts+bind (no session);
-- optional pairing create/claim/revoke; OTP stub gate documented in Edge smoke_test.
-- Run as postgres (local supabase). Needs MAIN warehouse, EA uom.

DO $$
DECLARE
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_list_retail UUID;
  v_list_b2b UUID;
  v_cart UUID;
  v_cart2 UUID;
  v_inv UUID;
  v_cust UUID;
  v_bound UUID;
  v_profile UUID;
  v_session UUID;
  v_code TEXT;
  v_exp TIMESTAMPTZ;
  v_outbox INT;
  v_lines INT;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list_retail FROM public.price_lists WHERE code = 'RETAIL';
  SELECT id INTO v_list_b2b FROM public.price_lists WHERE code = 'B2B';

  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN warehouse or EA uom missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P2-POS-SMOKE-001', 'POS companion smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P2-POS-SMOKE-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list_retail, v_item, 40, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 40, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'P2 POS smoke seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 30,
        'unit_cost', 15,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  -- Registered trade customer for bind (B2B + profile)
  v_profile := gen_random_uuid();
  INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
  VALUES (
    v_profile, 'authenticated', 'authenticated',
    'pos-smoke-bind@example.com', crypt('smoke-not-login', gen_salt('bf')),
    now(), now(), now()
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff, phone_e164)
  VALUES (v_profile, 'POS Smoke Bound', false, '+263771900001')
  ON CONFLICT (id) DO UPDATE
  SET phone_e164 = EXCLUDED.phone_e164, full_name = EXCLUDED.full_name;

  INSERT INTO public.customers (
    display_name, email, phone_e164, whatsapp_e164, price_list_id, profile_id, currency
  )
  VALUES (
    'POS Smoke B2B Co',
    'pos-smoke-bind@example.com',
    '+263771900001',
    '+263771900001',
    v_list_b2b,
    v_profile,
    'USD'
  )
  RETURNING id INTO v_cust;

  -- -----------------------------------------------------------------------
  -- Standalone: add line WITHOUT scan session → checkout with contacts → bind
  -- -----------------------------------------------------------------------
  v_cart := public.create_pos_cart(v_main, NULL, 'USD'::public.currency_code, 'immediate'::public.fulfillment_mode);

  IF EXISTS (
    SELECT 1 FROM public.pos_scan_sessions
    WHERE cart_id = v_cart AND status IN ('open', 'claimed')
  ) THEN
    RAISE EXCEPTION 'smoke fail: standalone cart must start with zero active sessions';
  END IF;

  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 1);

  SELECT count(*)::int INTO v_lines FROM public.pos_cart_lines WHERE cart_id = v_cart;
  IF v_lines < 1 THEN
    RAISE EXCEPTION 'smoke fail: standalone line-add without session failed';
  END IF;

  v_bound := public.resolve_customer_for_receipt_contacts(
    'pos-smoke-bind@example.com',
    '+263771900001',
    NULL
  );
  IF v_bound IS DISTINCT FROM v_cust THEN
    RAISE EXCEPTION 'smoke fail: bind helper expected unique customer % got %', v_cust, v_bound;
  END IF;

  v_inv := public.checkout_pos_cart(
    v_cart,
    'pos-smoke-bind@example.com',
    '+263771900001',
    '+263771900001'
  );

  IF NOT EXISTS (
    SELECT 1 FROM public.sales_invoices
    WHERE id = v_inv
      AND status = 'posted'
      AND customer_id = v_cust
      AND customer_email = 'pos-smoke-bind@example.com'
      AND customer_whatsapp_e164 = '+263771900001'
  ) THEN
    RAISE EXCEPTION 'smoke fail: standalone checkout bind/contacts missing';
  END IF;

  SELECT count(*)::int INTO v_outbox
  FROM public.customer_receipt_outbox WHERE document_id = v_inv;
  IF v_outbox < 1 THEN
    RAISE EXCEPTION 'smoke fail: receipt outbox not enqueued';
  END IF;

  -- Ambiguous / retail-only walk-in must not bind
  IF public.resolve_customer_for_receipt_contacts('nobody@example.com', NULL, NULL) IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: unbound contact must return null';
  END IF;

  -- -----------------------------------------------------------------------
  -- Optional companion: create → claim (same owner) → revoke
  -- -----------------------------------------------------------------------
  -- Need a staff profile as auth.uid(); smokes run as postgres — set JWT claim via
  -- request.jwt.claim.sub is unavailable; use service path: set local role simulation
  -- by inserting session rows through RPCs after setting auth.uid via set_config.
  -- For SECURITY DEFINER RPCs that call auth.uid(), use a test staff user + GUC.
  PERFORM set_config('request.jwt.claim.sub', v_profile::text, true);
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
  PERFORM set_config('role', 'authenticated', true);

  -- Elevate smoke profile to sales for pairing RPCs
  INSERT INTO public.staff_roles (user_id, role)
  VALUES (v_profile, 'sales')
  ON CONFLICT DO NOTHING;
  UPDATE public.profiles SET is_staff = true WHERE id = v_profile;

  v_cart2 := public.create_pos_cart(v_main, NULL, 'USD'::public.currency_code, 'immediate'::public.fulfillment_mode);

  SELECT s.session_id, s.pairing_code, s.expires_at
  INTO v_session, v_code, v_exp
  FROM public.create_pos_scan_session(v_cart2) AS s;

  IF v_session IS NULL OR v_code !~ '^[0-9]{6}$' THEN
    RAISE EXCEPTION 'smoke fail: create_pos_scan_session';
  END IF;

  -- Standalone line-add still works WITH an open session present
  PERFORM public.add_cart_line(v_cart2, v_item, v_uom, 1);

  v_session := public.claim_pos_scan_session(v_code);
  IF NOT EXISTS (
    SELECT 1 FROM public.pos_scan_sessions
    WHERE id = v_session AND status = 'claimed' AND scanner_user_id = v_profile
  ) THEN
    RAISE EXCEPTION 'smoke fail: claim_pos_scan_session';
  END IF;

  PERFORM public.add_cart_line_from_qr(
    v_cart2,
    format('gtr://part/%s?batch=%s&valuation=FIFO', 'P2-POS-SMOKE-001', gen_random_uuid()::text),
    1
  );

  PERFORM public.revoke_pos_scan_session(v_session);
  IF NOT EXISTS (
    SELECT 1 FROM public.pos_scan_sessions WHERE id = v_session AND status = 'revoked'
  ) THEN
    RAISE EXCEPTION 'smoke fail: revoke_pos_scan_session';
  END IF;

  -- Checkout still works after revoke (no session required)
  v_inv := public.checkout_pos_cart(v_cart2, NULL, NULL, NULL);
  IF NOT EXISTS (
    SELECT 1 FROM public.sales_invoices WHERE id = v_inv AND status = 'posted'
  ) THEN
    RAISE EXCEPTION 'smoke fail: checkout after revoke';
  END IF;

  -- OTP stub gate is Edge-only: see supabase/functions/auth-otp/smoke_test.ts
  -- AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1 + keys unset → stub code 000000
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'profiles' AND column_name = 'phone_e164'
  ) THEN
    RAISE EXCEPTION 'smoke fail: profiles.phone_e164 missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_tables
    WHERE schemaname = 'public' AND tablename = 'auth_otp_challenges'
  ) THEN
    RAISE EXCEPTION 'smoke fail: auth_otp_challenges missing';
  END IF;

  IF NOT (
    SELECT relrowsecurity FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'pos_scan_sessions'
  ) THEN
    RAISE EXCEPTION 'smoke fail: pos_scan_sessions RLS not enabled';
  END IF;

  RAISE NOTICE 'pos_scan_companion_otp_smoke: PASS inv=% outbox=% session_revoked=%',
    v_inv, v_outbox, v_session;
END;
$$;
