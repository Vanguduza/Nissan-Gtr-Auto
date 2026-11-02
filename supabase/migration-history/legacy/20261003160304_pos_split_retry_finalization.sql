-- POS split payments (phase 4): a fully paid split sale whose invoice could not be posted
-- (status finalization_failed, e.g. stock or costing problem) had no way back from the counter.
-- Let the operator who owns the sale, or an approver / finance, retry posting it once the cause is fixed.
-- It never takes money: it only re-runs private.finalize_pos_split_payment_session for funds already locked.

CREATE OR REPLACE FUNCTION public.retry_pos_split_finalization(p_session_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE s public.pos_split_payment_sessions%ROWTYPE; o public.commerce_orders%ROWTYPE;
BEGIN
  PERFORM public._require_payments_staff();
  SELECT * INTO s FROM public.pos_split_payment_sessions WHERE id = p_session_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'split payment session not found'; END IF;
  SELECT * INTO o FROM public.commerce_orders WHERE id = s.commerce_order_id;
  IF o.cart_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.pos_carts c WHERE c.id = o.cart_id
       AND (c.created_by = auth.uid() OR public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[]))) THEN
    RAISE EXCEPTION 'split payment access denied';
  END IF;
  IF s.status NOT IN ('finalization_failed', 'fully_committed') THEN
    RAISE EXCEPTION 'only a fully paid split sale that did not post can be retried (status %)', s.status;
  END IF;
  -- Back to fully_committed (or leg_pending if something is in flight) before posting again.
  UPDATE public.pos_split_payment_sessions SET status = 'fully_committed', updated_at = now() WHERE id = s.id;
  PERFORM private.refresh_pos_split_payment_session(s.id);
  IF (SELECT status FROM public.pos_split_payment_sessions WHERE id = s.id) <> 'fully_committed' THEN
    RETURN private.pos_split_payment_payload(s.id);
  END IF;
  RETURN private.finalize_pos_split_payment_session(s.id);
END $$;

REVOKE ALL ON FUNCTION public.retry_pos_split_finalization(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.retry_pos_split_finalization(uuid) TO authenticated;
