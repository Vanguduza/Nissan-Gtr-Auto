-- Reconcile retained sales/delivery workflows with the October POS posting
-- guard. Hosted 20260905132947 and later returns/deposits/driver-cash functions
-- enter their own accounting context, while 20261001110000 changed the ledger
-- guard to app.pos_posting. The native full-day/COD/card/back-order simulations
-- caught legitimate non-finance actors failing at that nested journal step.
--
-- Keep the ledger guard and every original workflow body. Only the validated
-- entry point enters the existing internal posting scope; its core is owner-only
-- in private. Restore all affected caller contexts on success and exception,
-- including nested calls and recovery_required card responses. No client receives
-- permission to call a context setter or the private cores directly.

DO $move_core$
BEGIN
  IF to_regprocedure('private.post_pos_return_case_core(uuid)') IS NULL THEN
    ALTER FUNCTION public.post_pos_return_case(uuid) SET SCHEMA private;
    ALTER FUNCTION private.post_pos_return_case(uuid) RENAME TO post_pos_return_case_core;
  END IF;
END;
$move_core$;
REVOKE ALL ON FUNCTION private.post_pos_return_case_core(uuid) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.post_pos_return_case(p_return_case_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $scope$
DECLARE
  v_pos text := COALESCE(current_setting('app.pos_posting', true), '');
  v_sales text := COALESCE(current_setting('app.sales_checkout_accounting', true), '');
  v_payments text := COALESCE(current_setting('app.payments_rpc', true), '');
  v_delivery text := COALESCE(current_setting('app.delivery_payment_rpc', true), '');
  v_result uuid;
BEGIN
  IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
  PERFORM set_config('app.pos_posting', '1', true);
  BEGIN
    v_result := private.post_pos_return_case_core(p_return_case_id);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting', v_pos, true);
    PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
    PERFORM set_config('app.payments_rpc', v_payments, true);
    PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting', v_pos, true);
  PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
  PERFORM set_config('app.payments_rpc', v_payments, true);
  PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
  RETURN v_result;
END;
$scope$;
REVOKE ALL ON FUNCTION public.post_pos_return_case(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.post_pos_return_case(uuid) TO authenticated, service_role;

DO $move_core$
BEGIN
  IF to_regprocedure('private.take_pos_fulfillment_deposit_core(uuid, numeric, public.currency_code, public.payment_tender, uuid, text)') IS NULL THEN
    ALTER FUNCTION public.take_pos_fulfillment_deposit(uuid, numeric, public.currency_code, public.payment_tender, uuid, text) SET SCHEMA private;
    ALTER FUNCTION private.take_pos_fulfillment_deposit(uuid, numeric, public.currency_code, public.payment_tender, uuid, text) RENAME TO take_pos_fulfillment_deposit_core;
  END IF;
END;
$move_core$;
REVOKE ALL ON FUNCTION private.take_pos_fulfillment_deposit_core(uuid, numeric, public.currency_code, public.payment_tender, uuid, text) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.take_pos_fulfillment_deposit(p_request_id uuid, p_amount numeric, p_currency public.currency_code, p_tender public.payment_tender, p_till_session_id uuid DEFAULT NULL::uuid, p_reference text DEFAULT NULL::text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $scope$
DECLARE
  v_pos text := COALESCE(current_setting('app.pos_posting', true), '');
  v_sales text := COALESCE(current_setting('app.sales_checkout_accounting', true), '');
  v_payments text := COALESCE(current_setting('app.payments_rpc', true), '');
  v_delivery text := COALESCE(current_setting('app.delivery_payment_rpc', true), '');
  v_result jsonb;
BEGIN
  PERFORM public._require_sales_staff();
  PERFORM set_config('app.pos_posting', '1', true);
  BEGIN
    v_result := private.take_pos_fulfillment_deposit_core(p_request_id, p_amount, p_currency, p_tender, p_till_session_id, p_reference);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting', v_pos, true);
    PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
    PERFORM set_config('app.payments_rpc', v_payments, true);
    PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting', v_pos, true);
  PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
  PERFORM set_config('app.payments_rpc', v_payments, true);
  PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
  RETURN v_result;
END;
$scope$;
REVOKE ALL ON FUNCTION public.take_pos_fulfillment_deposit(uuid, numeric, public.currency_code, public.payment_tender, uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.take_pos_fulfillment_deposit(uuid, numeric, public.currency_code, public.payment_tender, uuid, text) TO authenticated, service_role;

DO $move_core$
BEGIN
  IF to_regprocedure('private.refund_pos_fulfillment_deposit_core(uuid, uuid, text)') IS NULL THEN
    ALTER FUNCTION public.refund_pos_fulfillment_deposit(uuid, uuid, text) SET SCHEMA private;
    ALTER FUNCTION private.refund_pos_fulfillment_deposit(uuid, uuid, text) RENAME TO refund_pos_fulfillment_deposit_core;
  END IF;
END;
$move_core$;
REVOKE ALL ON FUNCTION private.refund_pos_fulfillment_deposit_core(uuid, uuid, text) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.refund_pos_fulfillment_deposit(p_deposit_id uuid, p_till_session_id uuid DEFAULT NULL::uuid, p_notes text DEFAULT NULL::text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $scope$
DECLARE
  v_pos text := COALESCE(current_setting('app.pos_posting', true), '');
  v_sales text := COALESCE(current_setting('app.sales_checkout_accounting', true), '');
  v_payments text := COALESCE(current_setting('app.payments_rpc', true), '');
  v_delivery text := COALESCE(current_setting('app.delivery_payment_rpc', true), '');
  v_result jsonb;
BEGIN
  IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
  PERFORM set_config('app.pos_posting', '1', true);
  BEGIN
    v_result := private.refund_pos_fulfillment_deposit_core(p_deposit_id, p_till_session_id, p_notes);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting', v_pos, true);
    PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
    PERFORM set_config('app.payments_rpc', v_payments, true);
    PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting', v_pos, true);
  PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
  PERFORM set_config('app.payments_rpc', v_payments, true);
  PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
  RETURN v_result;
END;
$scope$;
REVOKE ALL ON FUNCTION public.refund_pos_fulfillment_deposit(uuid, uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.refund_pos_fulfillment_deposit(uuid, uuid, text) TO authenticated, service_role;

DO $move_core$
BEGIN
  IF to_regprocedure('private.finalize_pos_card_terminal_purchase_core(uuid)') IS NULL THEN
    ALTER FUNCTION public.finalize_pos_card_terminal_purchase(uuid) SET SCHEMA private;
    ALTER FUNCTION private.finalize_pos_card_terminal_purchase(uuid) RENAME TO finalize_pos_card_terminal_purchase_core;
  END IF;
END;
$move_core$;
REVOKE ALL ON FUNCTION private.finalize_pos_card_terminal_purchase_core(uuid) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.finalize_pos_card_terminal_purchase(p_attempt_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $scope$
DECLARE
  v_pos text := COALESCE(current_setting('app.pos_posting', true), '');
  v_sales text := COALESCE(current_setting('app.sales_checkout_accounting', true), '');
  v_payments text := COALESCE(current_setting('app.payments_rpc', true), '');
  v_delivery text := COALESCE(current_setting('app.delivery_payment_rpc', true), '');
  v_result jsonb;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM set_config('app.pos_posting', '1', true);
  BEGIN
    v_result := private.finalize_pos_card_terminal_purchase_core(p_attempt_id);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting', v_pos, true);
    PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
    PERFORM set_config('app.payments_rpc', v_payments, true);
    PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting', v_pos, true);
  PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
  PERFORM set_config('app.payments_rpc', v_payments, true);
  PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
  RETURN v_result;
END;
$scope$;
REVOKE ALL ON FUNCTION public.finalize_pos_card_terminal_purchase(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_pos_card_terminal_purchase(uuid) TO authenticated, service_role;

DO $move_core$
BEGIN
  IF to_regprocedure('private.finalize_pos_card_terminal_purchase_full_legacy_core(uuid)') IS NULL THEN
    ALTER FUNCTION public.finalize_pos_card_terminal_purchase_full_legacy(uuid) SET SCHEMA private;
    ALTER FUNCTION private.finalize_pos_card_terminal_purchase_full_legacy(uuid) RENAME TO finalize_pos_card_terminal_purchase_full_legacy_core;
  END IF;
END;
$move_core$;
REVOKE ALL ON FUNCTION private.finalize_pos_card_terminal_purchase_full_legacy_core(uuid) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.finalize_pos_card_terminal_purchase_full_legacy(p_attempt_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $scope$
DECLARE
  v_pos text := COALESCE(current_setting('app.pos_posting', true), '');
  v_sales text := COALESCE(current_setting('app.sales_checkout_accounting', true), '');
  v_payments text := COALESCE(current_setting('app.payments_rpc', true), '');
  v_delivery text := COALESCE(current_setting('app.delivery_payment_rpc', true), '');
  v_result jsonb;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM set_config('app.pos_posting', '1', true);
  BEGIN
    v_result := private.finalize_pos_card_terminal_purchase_full_legacy_core(p_attempt_id);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting', v_pos, true);
    PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
    PERFORM set_config('app.payments_rpc', v_payments, true);
    PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting', v_pos, true);
  PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
  PERFORM set_config('app.payments_rpc', v_payments, true);
  PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
  RETURN v_result;
END;
$scope$;
REVOKE ALL ON FUNCTION public.finalize_pos_card_terminal_purchase_full_legacy(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_pos_card_terminal_purchase_full_legacy(uuid) TO authenticated, service_role;

DO $move_core$
BEGIN
  IF to_regprocedure('private.finalize_pos_card_terminal_refund_core(uuid, text)') IS NULL THEN
    ALTER FUNCTION public.finalize_pos_card_terminal_refund(uuid, text) SET SCHEMA private;
    ALTER FUNCTION private.finalize_pos_card_terminal_refund(uuid, text) RENAME TO finalize_pos_card_terminal_refund_core;
  END IF;
END;
$move_core$;
REVOKE ALL ON FUNCTION private.finalize_pos_card_terminal_refund_core(uuid, text) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.finalize_pos_card_terminal_refund(p_attempt_id uuid, p_notes text DEFAULT NULL::text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $scope$
DECLARE
  v_pos text := COALESCE(current_setting('app.pos_posting', true), '');
  v_sales text := COALESCE(current_setting('app.sales_checkout_accounting', true), '');
  v_payments text := COALESCE(current_setting('app.payments_rpc', true), '');
  v_delivery text := COALESCE(current_setting('app.delivery_payment_rpc', true), '');
  v_result jsonb;
BEGIN
  IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'POS manager or finance approval required'; END IF;
  PERFORM set_config('app.pos_posting', '1', true);
  BEGIN
    v_result := private.finalize_pos_card_terminal_refund_core(p_attempt_id, p_notes);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting', v_pos, true);
    PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
    PERFORM set_config('app.payments_rpc', v_payments, true);
    PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting', v_pos, true);
  PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
  PERFORM set_config('app.payments_rpc', v_payments, true);
  PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
  RETURN v_result;
END;
$scope$;
REVOKE ALL ON FUNCTION public.finalize_pos_card_terminal_refund(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_pos_card_terminal_refund(uuid, text) TO authenticated, service_role;

DO $move_core$
BEGIN
  IF to_regprocedure('private.finalize_delivery_card_terminal_payment_core(uuid)') IS NULL THEN
    ALTER FUNCTION public.finalize_delivery_card_terminal_payment(uuid) SET SCHEMA private;
    ALTER FUNCTION private.finalize_delivery_card_terminal_payment(uuid) RENAME TO finalize_delivery_card_terminal_payment_core;
  END IF;
END;
$move_core$;
REVOKE ALL ON FUNCTION private.finalize_delivery_card_terminal_payment_core(uuid) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.finalize_delivery_card_terminal_payment(p_attempt_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $scope$
DECLARE
  v_pos text := COALESCE(current_setting('app.pos_posting', true), '');
  v_sales text := COALESCE(current_setting('app.sales_checkout_accounting', true), '');
  v_payments text := COALESCE(current_setting('app.payments_rpc', true), '');
  v_delivery text := COALESCE(current_setting('app.delivery_payment_rpc', true), '');
  v_result jsonb;
BEGIN
  IF NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN RAISE EXCEPTION 'delivery card payment belongs to another driver'; END IF;
  PERFORM set_config('app.pos_posting', '1', true);
  BEGIN
    v_result := private.finalize_delivery_card_terminal_payment_core(p_attempt_id);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting', v_pos, true);
    PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
    PERFORM set_config('app.payments_rpc', v_payments, true);
    PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting', v_pos, true);
  PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
  PERFORM set_config('app.payments_rpc', v_payments, true);
  PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
  RETURN v_result;
END;
$scope$;
REVOKE ALL ON FUNCTION public.finalize_delivery_card_terminal_payment(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_delivery_card_terminal_payment(uuid) TO authenticated, service_role;

DO $move_core$
BEGIN
  IF to_regprocedure('private.collect_delivery_cash_core(uuid, numeric, uuid, text)') IS NULL THEN
    ALTER FUNCTION public.collect_delivery_cash(uuid, numeric, uuid, text) SET SCHEMA private;
    ALTER FUNCTION private.collect_delivery_cash(uuid, numeric, uuid, text) RENAME TO collect_delivery_cash_core;
  END IF;
END;
$move_core$;
REVOKE ALL ON FUNCTION private.collect_delivery_cash_core(uuid, numeric, uuid, text) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.collect_delivery_cash(p_delivery_job_id uuid, p_amount numeric, p_request_id uuid, p_notes text DEFAULT NULL::text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $scope$
DECLARE
  v_pos text := COALESCE(current_setting('app.pos_posting', true), '');
  v_sales text := COALESCE(current_setting('app.sales_checkout_accounting', true), '');
  v_payments text := COALESCE(current_setting('app.payments_rpc', true), '');
  v_delivery text := COALESCE(current_setting('app.delivery_payment_rpc', true), '');
  v_result jsonb;
BEGIN
  IF NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN
    RAISE EXCEPTION 'assigned dispatched driver job required';
  END IF;
  IF p_request_id IS NULL THEN RAISE EXCEPTION 'cash collection request id required'; END IF;
  -- Serialize first calls and retries before checking cached ownership. Otherwise
  -- another job/driver could commit this request ID between the check and the
  -- retained core's cached-result SELECT under READ COMMITTED.
  PERFORM pg_advisory_xact_lock(hashtextextended('gtr:delivery-cash:' || p_request_id::text, 0));
  -- A request-ID retry must retain the same driver and delivery job. The old
  -- body returned its cached result before checking either ownership field.
  IF EXISTS (
    SELECT 1 FROM public.delivery_cash_collections c WHERE c.request_id = p_request_id
      AND (c.collected_by IS DISTINCT FROM auth.uid() OR
           c.delivery_job_id IS DISTINCT FROM p_delivery_job_id)
  ) THEN RAISE EXCEPTION 'delivery cash collection request belongs to another driver or job'; END IF;
  PERFORM set_config('app.pos_posting', '1', true);
  BEGIN
    v_result := private.collect_delivery_cash_core(p_delivery_job_id, p_amount, p_request_id, p_notes);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting', v_pos, true);
    PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
    PERFORM set_config('app.payments_rpc', v_payments, true);
    PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting', v_pos, true);
  PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
  PERFORM set_config('app.payments_rpc', v_payments, true);
  PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
  RETURN v_result;
END;
$scope$;
REVOKE ALL ON FUNCTION public.collect_delivery_cash(uuid, numeric, uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.collect_delivery_cash(uuid, numeric, uuid, text) TO authenticated, service_role;

DO $move_core$
BEGIN
  IF to_regprocedure('private._driver_cash_journal_core(public.driver_cash_handins, text, text, numeric, text)') IS NULL THEN
    ALTER FUNCTION private._driver_cash_journal(public.driver_cash_handins, text, text, numeric, text) RENAME TO _driver_cash_journal_core;
  END IF;
END;
$move_core$;
REVOKE ALL ON FUNCTION private._driver_cash_journal_core(public.driver_cash_handins, text, text, numeric, text) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION private._driver_cash_journal(h public.driver_cash_handins, p_debit text, p_credit text, p_amount numeric, p_what text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $scope$
DECLARE
  v_pos text := COALESCE(current_setting('app.pos_posting', true), '');
  v_sales text := COALESCE(current_setting('app.sales_checkout_accounting', true), '');
  v_payments text := COALESCE(current_setting('app.payments_rpc', true), '');
  v_delivery text := COALESCE(current_setting('app.delivery_payment_rpc', true), '');
  v_result uuid;
BEGIN
  PERFORM set_config('app.pos_posting', '1', true);
  BEGIN
    v_result := private._driver_cash_journal_core(h, p_debit, p_credit, p_amount, p_what);
  EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.pos_posting', v_pos, true);
    PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
    PERFORM set_config('app.payments_rpc', v_payments, true);
    PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
    RAISE;
  END;
  PERFORM set_config('app.pos_posting', v_pos, true);
  PERFORM set_config('app.sales_checkout_accounting', v_sales, true);
  PERFORM set_config('app.payments_rpc', v_payments, true);
  PERFORM set_config('app.delivery_payment_rpc', v_delivery, true);
  RETURN v_result;
END;
$scope$;
REVOKE ALL ON FUNCTION private._driver_cash_journal(public.driver_cash_handins, text, text, numeric, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION private._driver_cash_journal(public.driver_cash_handins, text, text, numeric, text) FROM authenticated, service_role;
