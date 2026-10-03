-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908123000 pos_account_credit_state).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Reserve-first account-credit state for named POS customers.
ALTER TYPE public.commerce_order_state ADD VALUE IF NOT EXISTS 'account_invoiced';
