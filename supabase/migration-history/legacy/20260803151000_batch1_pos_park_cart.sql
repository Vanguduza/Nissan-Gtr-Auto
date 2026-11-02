-- Batch 1 §1.6 park/hold + §1.8 fulfillment labels (pickup=immediate, delivery=dispatch).
-- Extend pos_carts.status CHECK to include parked; park/resume RPCs.

ALTER TABLE public.pos_carts DROP CONSTRAINT IF EXISTS pos_carts_status_check;
ALTER TABLE public.pos_carts
  ADD CONSTRAINT pos_carts_status_check
  CHECK (status IN ('open', 'parked', 'checked_out', 'abandoned'));

COMMENT ON COLUMN public.pos_carts.status IS
  'open | parked (hold) | checked_out | abandoned. Pickup UX = fulfillment immediate; delivery = dispatch.';

CREATE OR REPLACE FUNCTION public.park_pos_cart(p_cart_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
BEGIN
  PERFORM public._require_cart_mutate(p_cart_id);

  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'cart not found: %', p_cart_id;
  END IF;
  IF v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'only open carts can be parked (got %)', v_cart.status;
  END IF;

  UPDATE public.pos_carts
  SET status = 'parked', updated_at = now()
  WHERE id = p_cart_id;

  RETURN p_cart_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.resume_pos_cart(p_cart_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
BEGIN
  PERFORM public._require_cart_mutate(p_cart_id);

  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'cart not found: %', p_cart_id;
  END IF;
  IF v_cart.status <> 'parked' THEN
    RAISE EXCEPTION 'only parked carts can be resumed (got %)', v_cart.status;
  END IF;

  UPDATE public.pos_carts
  SET status = 'open', updated_at = now()
  WHERE id = p_cart_id;

  RETURN p_cart_id;
END;
$$;

REVOKE ALL ON FUNCTION public.park_pos_cart(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.resume_pos_cart(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.park_pos_cart(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.resume_pos_cart(UUID) TO authenticated, service_role;
