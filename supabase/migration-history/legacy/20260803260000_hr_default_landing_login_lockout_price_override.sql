-- Tablet kiosk Should/Must close-out:
-- 1) hr_roles.default_landing (pos|hub) for role routing without app rebuild
-- 2) staff_login_failures lockout (password fail tracking; non-enumerating)
-- 3) apply_pos_line_price_override — Admin|shop-manager same gate as discount
-- No ZIMRA. RLS on new tables in this file.

-- ---------------------------------------------------------------------------
-- 1. Configurable default landing on organogram roles
-- ---------------------------------------------------------------------------
ALTER TABLE public.hr_roles
  ADD COLUMN IF NOT EXISTS default_landing TEXT;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'hr_roles_default_landing_chk'
  ) THEN
    ALTER TABLE public.hr_roles
      ADD CONSTRAINT hr_roles_default_landing_chk
      CHECK (
        default_landing IS NULL
        OR default_landing IN ('pos', 'hub')
      );
  END IF;
END $$;

COMMENT ON COLUMN public.hr_roles.default_landing IS
  'Optional landing override: pos | hub. NULL → client falls back to staff_roles prefersPosHome.';

-- Seed sales-leaning roles to POS when module_access includes pos and no landing yet.
UPDATE public.hr_roles
SET default_landing = 'pos',
    updated_at = now()
WHERE default_landing IS NULL
  AND is_active
  AND module_access @> '["pos"]'::jsonb
  AND NOT (module_access ?| ARRAY['warehouse', 'finance', 'hr', 'admin']);

CREATE OR REPLACE FUNCTION public.my_default_landing()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT r.default_landing
  FROM public.employees e
  JOIN public.hr_roles r ON r.id = e.hr_role_id
  WHERE e.user_id = auth.uid()
    AND e.status = 'active'
    AND r.is_active
  LIMIT 1;
$$;

COMMENT ON FUNCTION public.my_default_landing() IS
  'Returns hr_roles.default_landing for signed-in active employee; NULL if unset.';

REVOKE ALL ON FUNCTION public.my_default_landing() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.my_default_landing() TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 2. Password login failure lockout (post-resolve)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.staff_login_failures (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  identifier_hash TEXT NOT NULL,
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  success BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS staff_login_failures_hash_idx
  ON public.staff_login_failures (identifier_hash, attempted_at DESC);

ALTER TABLE public.staff_login_failures ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS staff_login_failures_deny ON public.staff_login_failures;
CREATE POLICY staff_login_failures_deny ON public.staff_login_failures
  FOR ALL TO authenticated
  USING (false)
  WITH CHECK (false);

-- Returns true when identifier is currently locked out (too many recent failures).
CREATE OR REPLACE FUNCTION public.staff_login_is_locked(p_identifier TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_raw TEXT;
  v_hash TEXT;
  v_fails INT;
BEGIN
  v_raw := lower(trim(COALESCE(p_identifier, '')));
  IF v_raw = '' THEN
    RETURN false;
  END IF;
  v_hash := encode(extensions.digest(convert_to(v_raw, 'UTF8'), 'sha256'), 'hex');

  SELECT COUNT(*)::INT INTO v_fails
  FROM public.staff_login_failures
  WHERE identifier_hash = v_hash
    AND success = false
    AND attempted_at > now() - interval '15 minutes';

  RETURN v_fails >= 5;
END;
$$;

COMMENT ON FUNCTION public.staff_login_is_locked(TEXT) IS
  'True when ≥5 failed password attempts in 15 minutes for identifier hash.';

-- Record success/failure. Always returns void; callers must not branch on existence.
CREATE OR REPLACE FUNCTION public.record_staff_login_attempt(
  p_identifier TEXT,
  p_success BOOLEAN
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_raw TEXT;
  v_hash TEXT;
BEGIN
  v_raw := lower(trim(COALESCE(p_identifier, '')));
  IF v_raw = '' THEN
    RETURN;
  END IF;
  v_hash := encode(extensions.digest(convert_to(v_raw, 'UTF8'), 'sha256'), 'hex');

  INSERT INTO public.staff_login_failures (identifier_hash, success)
  VALUES (v_hash, COALESCE(p_success, false));

  -- On success, clear recent failures for this identifier (keep audit trail capped).
  IF p_success THEN
    DELETE FROM public.staff_login_failures
    WHERE identifier_hash = v_hash
      AND success = false
      AND attempted_at > now() - interval '15 minutes';
  END IF;
END;
$$;

COMMENT ON FUNCTION public.record_staff_login_attempt(TEXT, BOOLEAN) IS
  'Append staff password attempt; clears recent failures on success. Non-enumerating.';

REVOKE ALL ON FUNCTION public.staff_login_is_locked(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.record_staff_login_attempt(TEXT, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.staff_login_is_locked(TEXT)
  TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_staff_login_attempt(TEXT, BOOLEAN)
  TO anon, authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 3. Line price override (Admin | shop manager — same is_pos_approver gate)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.apply_pos_line_price_override(
  p_line_id UUID,
  p_unit_price NUMERIC,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_line public.pos_cart_lines%ROWTYPE;
  v_cart public.pos_carts%ROWTYPE;
  v_before JSONB;
  v_after JSONB;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;
  IF NOT public.is_pos_approver() THEN
    RAISE EXCEPTION 'admin or shop manager approval required';
  END IF;
  IF p_unit_price IS NULL OR p_unit_price < 0 THEN
    RAISE EXCEPTION 'unit price must be >= 0';
  END IF;

  SELECT * INTO v_line FROM public.pos_cart_lines WHERE id = p_line_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'line not found: %', p_line_id;
  END IF;
  IF v_line.is_core_charge THEN
    RAISE EXCEPTION 'cannot override core-charge lines';
  END IF;

  SELECT * INTO v_cart FROM public.pos_carts WHERE id = v_line.cart_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'cart not found';
  END IF;
  IF v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'only open carts support price override (got %)', v_cart.status;
  END IF;

  PERFORM public._require_cart_mutate(v_line.cart_id);

  v_before := jsonb_build_object(
    'id', v_line.id,
    'unit_price', v_line.unit_price,
    'line_total', v_line.line_total,
    'qty', v_line.qty
  );

  UPDATE public.pos_cart_lines
  SET
    unit_price = round(p_unit_price, 4),
    line_total = round(round(p_unit_price, 4) * qty, 2)
  WHERE id = p_line_id
  RETURNING * INTO v_line;

  v_after := jsonb_build_object(
    'id', v_line.id,
    'unit_price', v_line.unit_price,
    'line_total', v_line.line_total,
    'qty', v_line.qty
  );

  RETURN public._log_pos_action(
    'price_override',
    'pos_cart_line',
    p_line_id,
    v_before,
    v_after,
    p_notes
  );
END;
$$;

COMMENT ON FUNCTION public.apply_pos_line_price_override(UUID, NUMERIC, TEXT) IS
  'Admin|shop-manager unit price override on open non-core POS line; audited.';

REVOKE ALL ON FUNCTION public.apply_pos_line_price_override(UUID, NUMERIC, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.apply_pos_line_price_override(UUID, NUMERIC, TEXT)
  TO authenticated, service_role;
