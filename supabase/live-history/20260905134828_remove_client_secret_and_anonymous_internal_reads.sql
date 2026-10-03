-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905134828 remove_client_secret_and_anonymous_internal_reads).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Secret challenge storage is service-side only.
REVOKE SELECT ON TABLE
  public.auth_otp_challenges,
  public.auth_otp_proofs,
  public.password_reset_challenges
FROM anon, authenticated;

-- Anonymous clients never need operational/internal queue or audit visibility.
REVOKE SELECT ON TABLE
  public.paynow_webhook_events,
  public.ecocash_webhook_events,
  public.contipay_webhook_events,
  public.customer_receipt_outbox,
  public.sms_outbox,
  public.hr_credential_outbox,
  public.domain_events,
  public.finance_audit_log,
  public.pos_action_audit
FROM anon;
