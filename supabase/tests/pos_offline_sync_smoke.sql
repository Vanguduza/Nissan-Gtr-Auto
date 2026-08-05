-- Smoke: offline POS pull + replay RPCs + receipts table (idempotency SoR).
-- No ZIMRA. Does not post live sales (existence + grant checks only).

DO $$
DECLARE
  v_has_fn BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'pull_pos_offline_snapshot'
  ) INTO v_has_fn;
  IF NOT v_has_fn THEN
    RAISE EXCEPTION 'smoke fail: pull_pos_offline_snapshot missing';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'replay_offline_pos_sale'
  ) INTO v_has_fn;
  IF NOT v_has_fn THEN
    RAISE EXCEPTION 'smoke fail: replay_offline_pos_sale missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'pos_offline_sale_receipts'
  ) THEN
    RAISE EXCEPTION 'smoke fail: pos_offline_sale_receipts table missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name = tc.constraint_name
     AND ccu.table_schema = tc.table_schema
    WHERE tc.table_schema = 'public'
      AND tc.table_name = 'pos_offline_sale_receipts'
      AND tc.constraint_type = 'UNIQUE'
      AND ccu.column_name = 'client_sale_id'
  ) THEN
    RAISE EXCEPTION 'smoke fail: client_sale_id UNIQUE missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename = 'pos_offline_sale_receipts'
      AND policyname = 'pos_offline_sale_receipts_select'
  ) THEN
    RAISE EXCEPTION 'smoke fail: RLS select policy missing';
  END IF;

  RAISE NOTICE 'pos_offline_sync_smoke ok';
END;
$$;
