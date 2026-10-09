-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905121707 harden_internal_rpc_capabilities).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0 privilege-surface hardening.
-- Internal capability setters and mutating helpers must never be client-callable.
DO $do$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS signature
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = ANY (ARRAY[
        '_finance_period_rpc_enter','_finance_req_rpc_enter','_fleet_begin_rpc',
        '_logistics_begin_rpc','_loyalty_rpc_enter','_payments_rpc_enter',
        '_payroll_begin_rpc','_procurement_begin_rpc','_recon_begin_rpc',
        '_storefront_rpc_enter','_warranty_begin_rpc',
        '_adjust_consignment_level','_append_loyalty','_append_store_credit',
        '_consume_fifo_batches','_ensure_loyalty_account','_ensure_store_credit_account',
        '_ensure_pick_list_for_invoice','_finalize_online_dispatch_order',
        '_auto_ship_online_dispatch_after_pick','_try_auto_assign_delivery_job',
        '_expire_stale_pos_scan_sessions','_log_pos_action',
        '_post_journal_entry_inventory','_post_stock_reconciliation',
        '_reverse_journal_inventory','_refresh_payroll_run_totals',
        '_recompute_delivery_eta_haversine','_enqueue_delivery_customer_sms',
        '_notify_out_for_delivery','_delivery_job_customer_contact',
        'next_series_value'
      ])
  LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon, authenticated', r.signature);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO service_role', r.signature);
  END LOOP;
END
$do$;

-- Provider settlement is an Edge/service boundary, never a staff/client mutation.
DO $do$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS signature
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = ANY (ARRAY['mark_ecocash_settled','mark_paynow_settled','mark_contipay_settled'])
  LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon, authenticated', r.signature);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO service_role', r.signature);
  END LOOP;
END
$do$;

-- Staff-facing finance RPCs: authenticated staff (guarded in-function) + service only; never anon.
DO $do$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS signature
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = ANY (ARRAY[
        'create_journal_draft','post_journal','post_journal_entry','reverse_journal',
        'create_payment_entry','allocate_payment','post_payment_entry','cancel_payment_entry',
        'issue_store_credit','redeem_store_credit','settle_invoice_tenders',
        'set_customer_credit','settle_commerce_manual_payment','emit_domain_event'
      ])
  LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon', r.signature);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated, service_role', r.signature);
  END LOOP;
END
$do$;

-- Customer order/checkout is authenticated-customer only; service role remains available to server orchestration.
DO $do$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT p.oid::regprocedure AS signature
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = ANY (ARRAY['prepare_customer_checkout','checkout_customer_cart','get_customer_order'])
  LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon', r.signature);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated, service_role', r.signature);
  END LOOP;
END
$do$;

-- Fix mutable search-path exposure on the payment guard/control helpers touched by this tranche.
ALTER FUNCTION public._require_payments_staff() SET search_path = '';
ALTER FUNCTION public._payments_rpc_enter() SET search_path = '';
ALTER FUNCTION public._payments_rpc_active() SET search_path = '';
ALTER FUNCTION public._storefront_rpc_enter() SET search_path = '';
ALTER FUNCTION public._storefront_rpc_active() SET search_path = '';
ALTER FUNCTION public._logistics_begin_rpc() SET search_path = '';
ALTER FUNCTION public._logistics_rpc_active() SET search_path = '';
