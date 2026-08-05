-- Smoke: POS approver / quotations / staff login resolve (tablet plan p2–p4, p6).
-- Run after migrations 20260803250000–20260803252000. No ZIMRA.

DO $$
DECLARE
  v_has_fn BOOLEAN;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'is_pos_approver'
  ) INTO v_has_fn;
  IF NOT v_has_fn THEN
    RAISE EXCEPTION 'smoke fail: is_pos_approver missing';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'post_pos_refund'
  ) INTO v_has_fn;
  IF NOT v_has_fn THEN
    RAISE EXCEPTION 'smoke fail: post_pos_refund missing';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'create_pos_quotation_from_cart'
  ) INTO v_has_fn;
  IF NOT v_has_fn THEN
    RAISE EXCEPTION 'smoke fail: create_pos_quotation_from_cart missing';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'resolve_staff_login_email'
  ) INTO v_has_fn;
  IF NOT v_has_fn THEN
    RAISE EXCEPTION 'smoke fail: resolve_staff_login_email missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'pos_quotations'
  ) THEN
    RAISE EXCEPTION 'smoke fail: pos_quotations table missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'pos_action_audit'
  ) THEN
    RAISE EXCEPTION 'smoke fail: pos_action_audit table missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'hr_roles'
      AND column_name = 'approval_flags'
  ) THEN
    RAISE EXCEPTION 'smoke fail: hr_roles.approval_flags missing';
  END IF;

  RAISE NOTICE 'tablet_pos_p2_p6_smoke ok';
END;
$$;
