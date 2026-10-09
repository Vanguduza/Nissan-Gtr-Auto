-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905122612 enforce_rpc_only_core_commerce_writes).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0 defense in depth: core commercial ledgers are RPC/service-owned.
-- Keep existing SELECT grants/policies for legitimate projections, but remove all
-- client table-level mutation capability so RLS drift cannot bypass domain RPCs.
DO $do$
DECLARE
  v_table text;
BEGIN
  FOREACH v_table IN ARRAY ARRAY[
    'sales_invoices','sales_invoice_lines',
    'payment_entries','payment_allocations',
    'store_credit_accounts','store_credit_ledger',
    'journal_entries','journal_entry_lines',
    'stock_levels','stock_entries','stock_entry_lines','stock_batches','stock_serials',
    'consignment_stock_levels','customer_price_overrides',
    'ecocash_payment_intents','paynow_payment_intents','contipay_payment_intents',
    'staff_login_failures','staff_login_resolve_attempts'
  ]
  LOOP
    EXECUTE format(
      'REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON TABLE public.%I FROM anon, authenticated',
      v_table
    );
  END LOOP;
END
$do$;
