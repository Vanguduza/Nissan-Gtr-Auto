-- Structural smoke for owner decision D1: operator-scoped hidden best sellers.
DO $$
BEGIN
  IF to_regclass('public.pos_operator_hidden_bestsellers') IS NULL THEN
    RAISE EXCEPTION 'pos_operator_hidden_bestsellers missing';
  END IF;
  IF NOT (SELECT relrowsecurity FROM pg_class WHERE oid = 'public.pos_operator_hidden_bestsellers'::regclass) THEN
    RAISE EXCEPTION 'pos_operator_hidden_bestsellers must have RLS enabled';
  END IF;
  IF to_regprocedure('public.list_pos_hidden_bestsellers()') IS NULL THEN
    RAISE EXCEPTION 'list_pos_hidden_bestsellers missing';
  END IF;
  IF to_regprocedure('public.hide_pos_bestseller(uuid)') IS NULL THEN
    RAISE EXCEPTION 'hide_pos_bestseller missing';
  END IF;
  IF to_regprocedure('public.unhide_pos_bestseller(uuid)') IS NULL THEN
    RAISE EXCEPTION 'unhide_pos_bestseller missing';
  END IF;
  IF has_function_privilege('anon', 'public.hide_pos_bestseller(uuid)', 'EXECUTE') THEN
    RAISE EXCEPTION 'anon must not execute hide_pos_bestseller';
  END IF;
END $$;

-- Behaviour: hide is per operator, list returns only the caller's rows, unhide restores.
DO $$
DECLARE
  v_a uuid := gen_random_uuid();
  v_b uuid := gen_random_uuid();
  v_item uuid;
  v_n int;
BEGIN
  SELECT id INTO v_item FROM public.stock_items LIMIT 1;
  IF v_item IS NULL THEN
    RAISE NOTICE 'pos_hidden_bestsellers_smoke: no stock_items; behaviour checks skipped';
    RETURN;
  END IF;
  INSERT INTO auth.users (id, email) VALUES (v_a, 'hide-a@smoke.test'), (v_b, 'hide-b@smoke.test');
  INSERT INTO public.profiles (id) VALUES (v_a), (v_b) ON CONFLICT (id) DO NOTHING;
  INSERT INTO public.staff_roles (user_id, role) VALUES (v_a, 'sales'), (v_b, 'sales');

  PERFORM set_config('request.jwt.claim.sub', v_a::text, true);
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
  PERFORM public.hide_pos_bestseller(v_item);
  PERFORM public.hide_pos_bestseller(v_item); -- idempotent
  SELECT count(*) INTO v_n FROM public.list_pos_hidden_bestsellers();
  IF v_n <> 1 THEN RAISE EXCEPTION 'operator A should see 1 hidden best seller, got %', v_n; END IF;

  PERFORM set_config('request.jwt.claim.sub', v_b::text, true);
  SELECT count(*) INTO v_n FROM public.list_pos_hidden_bestsellers();
  IF v_n <> 0 THEN RAISE EXCEPTION 'operator B must not see operator A hides, got %', v_n; END IF;

  PERFORM set_config('request.jwt.claim.sub', v_a::text, true);
  PERFORM public.unhide_pos_bestseller(v_item);
  SELECT count(*) INTO v_n FROM public.list_pos_hidden_bestsellers();
  IF v_n <> 0 THEN RAISE EXCEPTION 'unhide should restore the best seller, got %', v_n; END IF;

  DELETE FROM auth.users WHERE id IN (v_a, v_b);
END $$;
