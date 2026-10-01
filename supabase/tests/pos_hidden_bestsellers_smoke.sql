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
