-- Staff SECURITY DEFINER mutator for B2B credit_limit / credit_hold.
-- Storefront MUST NOT self-set (role gate). Explicit currency on response.
-- NO ZIMRA / payroll tax.

CREATE OR REPLACE FUNCTION public.set_customer_credit(
  p_customer_id UUID,
  p_credit_limit NUMERIC DEFAULT NULL,
  p_credit_hold BOOLEAN DEFAULT NULL
)
RETURNS TABLE (
  customer_id UUID,
  credit_limit NUMERIC,
  credit_hold BOOLEAN,
  open_balance NUMERIC,
  currency public.currency_code
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_limit NUMERIC;
  v_hold BOOLEAN;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin, sales, or finance role required to set customer credit';
  END IF;
  IF p_customer_id IS NULL THEN
    RAISE EXCEPTION 'customer_id required';
  END IF;
  IF p_credit_limit IS NULL AND p_credit_hold IS NULL THEN
    RAISE EXCEPTION 'provide p_credit_limit and/or p_credit_hold';
  END IF;
  IF p_credit_limit IS NOT NULL AND p_credit_limit < 0 THEN
    RAISE EXCEPTION 'credit_limit must be >= 0';
  END IF;

  SELECT c.credit_limit, c.credit_hold
  INTO v_limit, v_hold
  FROM public.customers c
  WHERE c.id = p_customer_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'customer not found';
  END IF;

  v_limit := COALESCE(p_credit_limit, v_limit);
  v_hold := COALESCE(p_credit_hold, v_hold);

  UPDATE public.customers c
  SET
    credit_limit = v_limit,
    credit_hold = v_hold,
    updated_at = now()
  WHERE c.id = p_customer_id;

  RETURN QUERY
  SELECT
    c.id,
    c.credit_limit,
    c.credit_hold,
    c.open_balance,
    c.currency
  FROM public.customers c
  WHERE c.id = p_customer_id;
END;
$$;

COMMENT ON FUNCTION public.set_customer_credit(UUID, NUMERIC, BOOLEAN) IS
  'Staff-only credit_limit / credit_hold mutator. Returns limit, hold, open_balance with explicit currency. Storefront cannot call.';

REVOKE ALL ON FUNCTION public.set_customer_credit(UUID, NUMERIC, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_customer_credit(UUID, NUMERIC, BOOLEAN)
  TO authenticated, service_role;
