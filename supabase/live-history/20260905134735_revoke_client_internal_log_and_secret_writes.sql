-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905134735 revoke_client_internal_log_and_secret_writes).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0: internal secret, webhook, outbox and audit tables are never client-authored.
REVOKE INSERT, UPDATE, DELETE ON TABLE
  public.auth_otp_challenges,
  public.auth_otp_proofs,
  public.password_reset_challenges,
  public.paynow_webhook_events,
  public.ecocash_webhook_events,
  public.contipay_webhook_events,
  public.customer_receipt_outbox,
  public.sms_outbox,
  public.hr_credential_outbox,
  public.domain_events,
  public.finance_audit_log,
  public.pos_action_audit
FROM anon, authenticated;

-- Receipt enqueueing is an internal consequence of a posted commercial document.
REVOKE EXECUTE ON FUNCTION public.enqueue_customer_receipts(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.enqueue_customer_receipts(uuid) TO service_role;

-- Internal audit appenders should not be anonymous entrypoints.
REVOKE EXECUTE ON FUNCTION public.log_finance_audit(text,text,uuid,jsonb,jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.log_finance_audit(text,text,uuid,jsonb,jsonb) TO authenticated, service_role;

-- Helper trigger functions are not RPC surfaces.
REVOKE EXECUTE ON FUNCTION public.guard_finance_audit_immutable() FROM PUBLIC, anon, authenticated;
