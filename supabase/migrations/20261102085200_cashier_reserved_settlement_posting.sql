-- Reserve-first finalization already enters the sale-accounting context, but
-- its subsequent tender receipts were outside it. Sales-only cashiers therefore
-- failed when post_payment_entry reached the finance-only journal functions.
-- Preserve the native settlement body and all its authorization/reservation/
-- idempotency guards; scope the existing POS posting context to this RPC only.
DO $$
BEGIN
  IF to_regprocedure('private.settle_pos_commerce_tenders_core(uuid,uuid,jsonb)') IS NULL THEN
    ALTER FUNCTION public.settle_pos_commerce_tenders(uuid,uuid,jsonb) SET SCHEMA private;
    ALTER FUNCTION private.settle_pos_commerce_tenders(uuid,uuid,jsonb)
      RENAME TO settle_pos_commerce_tenders_core;
  END IF;
END;
$$;
REVOKE ALL ON FUNCTION private.settle_pos_commerce_tenders_core(uuid,uuid,jsonb)
  FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.settle_pos_commerce_tenders(
  p_order_id uuid, p_payment_request_id uuid, p_tenders jsonb
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_previous text := COALESCE(current_setting('app.pos_posting',true),'');
  v_result jsonb;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM set_config('app.pos_posting','1',true);
  BEGIN
    v_result := private.settle_pos_commerce_tenders_core(p_order_id,p_payment_request_id,p_tenders);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting',v_previous,true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting',v_previous,true);
  RETURN v_result;
END;
$$;
REVOKE ALL ON FUNCTION public.settle_pos_commerce_tenders(uuid,uuid,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.settle_pos_commerce_tenders(uuid,uuid,jsonb)
  TO authenticated,service_role;
